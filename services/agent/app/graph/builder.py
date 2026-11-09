"""装配一张图。五个业务子图各自编译，外层只做分流、暂停和收口。

检查点只挂在外层，只用 AsyncPostgresSaver，不用 Store。
子图不自己暂停：授权确认留在外层，停止也不走 interrupt。
节点名避开状态字段名。状态里已有 clarify，追问节点就叫 ask_user。
"""

from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import END, StateGraph

from app.graph.nodes.chitchat import chat
from app.graph.nodes.clarify import ask
from app.graph.nodes.hitl import prepare, wait
from app.graph.nodes.ingest import ingest
from app.graph.nodes.intent import recognize
from app.graph.nodes.memory_load import load
from app.graph.nodes.memory_write import remember
from app.graph.nodes.reply_sse import finish
from app.graph.route import route_intent
from app.graph.state import AgentState
from app.graph.subgraphs.account import compile_account
from app.graph.subgraphs.earn import compile_earn
from app.graph.subgraphs.order import compile_order
from app.graph.subgraphs.shop import compile_shop
from app.graph.subgraphs.support import compile_support
from app.graph.turn import require

# 同一份检查点只编译一次。模型和工具每轮从配置里取，不绑死在图上。
_ready: dict[int, object] = {}


def build_graph(checkpointer=None):
    """把子图和收口串起来。没传入检查点时用内存，好让单测彼此隔离。

    子图在这里编译，是为了让搜索、账户、订单各自的分支留在自己的文件里。
    外层新增一种意图时，只加一条分流和一张子图，不必把判断写进同一个函数。

    @param checkpointer 外层检查点；空则本次图使用内存
    @return 已编译的整张图
    """
    graph = StateGraph(AgentState)
    graph.add_node("ingest", ingest)
    graph.add_node("memory_load", load)
    graph.add_node("intent_router", _recognize)
    graph.add_node("ask_user", ask)
    graph.add_node("subgraph_shop", compile_shop())
    graph.add_node("subgraph_account", compile_account())
    graph.add_node("subgraph_order", compile_order())
    graph.add_node("subgraph_earn", compile_earn())
    graph.add_node("subgraph_support", compile_support())
    graph.add_node("chitchat", chat)
    graph.add_node("hitl_prepare", _prepare)
    graph.add_node("hitl_wait", _wait)
    graph.add_node("memory_write", _remember)
    graph.add_node("reply_sse", _finish)

    graph.set_entry_point("ingest")
    graph.add_edge("ingest", "memory_load")
    graph.add_edge("memory_load", "intent_router")
    # 分流的返回值仍是 clarify，节点名用 ask_user，避免和状态字段撞车。
    graph.add_conditional_edges("intent_router", _route, {
        "clarify": "ask_user",
        "subgraph_shop": "subgraph_shop",
        "subgraph_account": "subgraph_account",
        "subgraph_order": "subgraph_order",
        "subgraph_earn": "subgraph_earn",
        "subgraph_support": "subgraph_support",
        "chitchat": "chitchat",
        "hitl_prepare": "hitl_prepare",
        "handoff": "subgraph_support",
    })
    for name in ("ask_user", "subgraph_shop", "subgraph_account", "subgraph_order", "subgraph_earn", "subgraph_support", "chitchat"):
        graph.add_edge(name, "memory_write")
    # 授权先发卡再暂停。恢复后才写记忆和收口，收口看到 resumed 就不再发卡。
    graph.add_edge("hitl_prepare", "hitl_wait")
    graph.add_edge("hitl_wait", "memory_write")
    graph.add_edge("memory_write", "reply_sse")
    graph.add_edge("reply_sse", END)
    return graph.compile(checkpointer=checkpointer or MemorySaver())


def graph_for_saver(checkpointer):
    """有共享检查点时复用已经编译的图。没有时每轮单独一张内存图。

    单测每次自己编译，避免线程之间串状态。数据库没连上时也不能复用，
    否则所有会话会挤在同一张内存图里。

    @param checkpointer 进程级检查点；空表示这一轮自备内存
    @return 已编译的图
    """
    if checkpointer is None:
        return build_graph(None)
    found = _ready.get(id(checkpointer))
    if found is None:
        found = build_graph(checkpointer)
        _ready[id(checkpointer)] = found
    return found


def _recognize(state, config):
    turn = require(config)
    return recognize(state, turn.model, turn.prompts)


def _prepare(state, config):
    return prepare(state, require(config).java)


def _wait(state, config):
    return wait(state, require(config).java)


def _remember(state, config):
    return remember(state, require(config).java)


def _finish(state, config):
    turn = require(config)
    return finish(state, turn.model, turn.prompts)


def _route(state: dict) -> str:
    """把意图和把握交给纯函数。这个函数本身不调用模型和工具。"""
    return route_intent(state.get("intent") or "", float(state.get("confidence") or 0))


async def open_saver(database_url: str):
    """启动时建一次检查点表，并打开一个会换连接的池。

    表由检查点库自己建，不进 Flyway。池里的连接自动提交，行按字典读。
    每次取出前探活，断了就丢掉并另开一条，所以库重启后下一轮还能接着用。
    连不上时调用方把检查点留空，进程仍能回答健康检查；
    没有共享检查点时，授权恢复只在单测的同一张图里有效。

    @param database_url 主库连接串
    @return 已建表的异步检查点
    """
    from psycopg.rows import dict_row
    from psycopg_pool import AsyncConnectionPool
    from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver

    pool = AsyncConnectionPool(
        database_url,
        min_size=1,
        max_size=4,
        timeout=10,
        open=False,
        kwargs={"autocommit": True, "prepare_threshold": 0, "row_factory": dict_row},
        check=AsyncConnectionPool.check_connection,
    )
    try:
        await pool.open(wait=True, timeout=10)
        saver = AsyncPostgresSaver(pool)
        await saver.setup()
    except Exception:
        await pool.close()
        raise
    return saver
