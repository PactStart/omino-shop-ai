"""这一轮的模型和工具。图可以按进程复用，这两样每轮更换。

提示词放在这里，不放进图状态。放进状态会被检查点整段写进数据库。
"""

from dataclasses import dataclass, field
from typing import Any
from weakref import WeakKeyDictionary


@dataclass
class Turn:
    """一轮调用的依赖和计时。工具耗时记在 java 上，模型耗时记在 model 上。"""

    java: Any
    model: Any
    prompts: dict
    trace_id: str = ""
    conversation_id: str = ""
    user_id: str = ""
    started: float = field(default=0.0)


_bound: WeakKeyDictionary = WeakKeyDictionary()


def attach(graph, model, java) -> None:
    """单测把模型和工具绑在这张图上，同步入口才找得到。"""
    _bound[graph] = (model, java)


def bound(graph):
    """取出这张图对应的模型和工具。"""
    return _bound[graph]


def require(config) -> Turn:
    """从节点收到的配置里取出这一轮。

    @param config LangGraph 传入的配置，turn 放在 configurable 里
    @return 这一轮的依赖
    """
    return config["configurable"]["turn"]


def make(state: dict, model, java, thread_id: str) -> tuple[Turn, dict]:
    """把提示词从状态里拿出来。返回的状态可以进检查点。

    @param state 调用方准备的输入，可能含提示词
    @param model 这一轮的模型
    @param java 这一轮的购物服务客户端
    @param thread_id 会话线程
    @return 轮次对象，以及去掉提示词之后的状态
    """
    prompts = dict(state.get("prompts") or {})
    cleaned = {key: value for key, value in state.items() if key != "prompts"}
    turn = Turn(
        java=java,
        model=model,
        prompts=prompts,
        trace_id=str(state.get("trace_id") or ""),
        conversation_id=str(state.get("conversation_id") or thread_id),
        user_id=str(state.get("user_id") or ""),
    )
    java.trace_id = turn.trace_id
    if hasattr(model, "trace_id"):
        model.trace_id = turn.trace_id
    return turn, cleaned
