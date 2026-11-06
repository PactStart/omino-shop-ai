"""邀请子图。人数和贡献只来自联盟统计，不在这里计算。"""

from langgraph.graph import END, StateGraph

from app.graph.state import AgentState
from app.graph.turn import require
from app.tools.java_client import ToolFailed


def compile_earn():
    """编译邀请子图。工具从这一轮的配置里取。

    @return 已编译子图
    """
    graph = StateGraph(AgentState)
    graph.add_node("earn_read", lambda _state, config: _read(require(config).java))
    graph.set_entry_point("earn_read")
    graph.add_edge("earn_read", END)
    return graph.compile()


def _read(java) -> dict:
    try:
        stats = java.get_affiliate_stats()
    except ToolFailed:
        return {"reply": "暂时查不到", "clarify": None, "cards": []}
    return {
        "reply": f"邀请{stats.get('inviteCount')} 有效{stats.get('activeUsers')} 贡献{stats.get('contribution')}",
        "clarify": None,
        "cards": [],
    }
