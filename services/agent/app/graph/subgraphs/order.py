"""订单子图。只有一个读取节点：有订单号读一笔，没有就读列表。

物流空着写成「未知」。用户佣金可以出现，媒体佣金不能出现。
"""

from langgraph.graph import END, StateGraph

from app.graph.state import AgentState
from app.graph.turn import require
from app.tools.java_client import ToolFailed


def compile_order():
    """编译订单子图。工具从这一轮的配置里取。

    @return 已编译子图
    """
    graph = StateGraph(AgentState)
    graph.add_node("order_read", lambda state, config: _read(state, require(config).java))
    graph.set_entry_point("order_read")
    graph.add_edge("order_read", END)
    return graph.compile()


def _read(state: dict, java) -> dict:
    """列表和单笔都只复述服务返回的字段。没有记录就说不知道。"""
    order_id = (state.get("slots") or {}).get("orderId")
    try:
        if order_id:
            order = java.get_order(order_id)
            orders = [order] if order else []
        else:
            orders = java.list_orders()
    except ToolFailed:
        return {"reply": "暂时查不到", "clarify": None, "cards": []}
    if not orders:
        return {"reply": "不知道", "clarify": None, "cards": []}
    parts = []
    for order in orders:
        if not order:
            continue
        logistics = order.get("logistics")
        parts.append("物流未知" if logistics is None else str(logistics))
        if order.get("userCommission") is not None:
            parts.append(str(order["userCommission"]))
    return {"reply": "；".join(parts), "clarify": None, "cards": []}
