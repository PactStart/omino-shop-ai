"""客服子图。规则没命中就说明可以建单，不改去搜商品。

投诉和未知意图走加急工单。用户自己要反馈则是普通优先级。
"""

from langgraph.graph import END, StateGraph

from app.graph.state import AgentState
from app.graph.turn import require
from app.tools.java_client import ToolFailed


def compile_support():
    """编译客服子图。规则和工单分开，避免没命中时顺手建单。

    @return 已编译子图
    """
    graph = StateGraph(AgentState)
    graph.add_node("support_route", lambda _state: {})
    graph.add_node("support_kb", lambda state, config: _kb(state, require(config).java))
    graph.add_node("support_ticket", lambda state, config: _ticket(state, require(config).java))
    graph.set_entry_point("support_route")
    graph.add_conditional_edges("support_route", _next, {
        "support_kb": "support_kb",
        "support_ticket": "support_ticket",
    })
    graph.add_edge("support_kb", END)
    graph.add_edge("support_ticket", END)
    return graph.compile()


def _next(state: dict) -> str:
    if state.get("intent") == "kb.faq":
        return "support_kb"
    return "support_ticket"


def _kb(state: dict, java) -> dict:
    """命中只复述条目正文。未命中给一句可以建单的说明，这一轮不建。"""
    try:
        found = java.search_kb(state.get("text") or "")
    except ToolFailed:
        return {"reply": "暂时没建成", "clarify": None, "cards": []}
    if not found or found.get("hit") is False or found.get("miss") is True:
        return {"reply": "没有找到这条规则，可以建一张工单", "clarify": None, "cards": []}
    return {"reply": str(found.get("content") or ""), "clarify": None, "cards": []}


def _ticket(state: dict, java) -> dict:
    """加急只给 handoff。用户明确要反馈时保持普通优先级。"""
    intent = state.get("intent")
    priority = "normal" if intent == "ticket.create" else "urgent"
    try:
        ticket = java.create_ticket({
            "category": (state.get("slots") or {}).get("category") or "other",
            "content": (state.get("slots") or {}).get("content") or state.get("text") or "",
            "priority": priority,
        })
    except ToolFailed:
        return {"reply": "暂时没建成", "clarify": None, "cards": []}
    return {"reply": f"工单{ticket.get('ticketId')}", "clarify": None, "cards": []}
