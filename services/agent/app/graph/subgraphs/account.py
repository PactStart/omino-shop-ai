"""账户子图。每个意图一个节点，话术只拼接工具返回的字段。

授权不在这张图里。授权要暂停等用户点卡片，那是外层的 hitl 节点，
避免账户查询和授权确认共用一条会误暂停的边。
"""

from langgraph.graph import END, StateGraph

from app.graph.state import AgentState
from app.graph.turn import require
from app.tools.java_client import ToolFailed, run_together


def compile_account():
    """编译账户子图。入口只负责按意图挑节点。工具从这一轮的配置里取。

    @return 已编译子图
    """
    graph = StateGraph(AgentState)
    graph.add_node("account_route", lambda _state: {})
    graph.add_node("account_profile", lambda state, config: _profile(state, require(config).java))
    graph.add_node("account_auth", lambda _state, config: _auth(require(config).java))
    graph.add_node("account_stats", lambda _state, config: _stats(require(config).java))
    graph.add_node("account_credits", lambda _state, config: _credits(require(config).java))
    graph.set_entry_point("account_route")
    graph.add_conditional_edges("account_route", _next, {
        "account_profile": "account_profile",
        "account_auth": "account_auth",
        "account_stats": "account_stats",
        "account_credits": "account_credits",
    })
    for name in ("account_profile", "account_auth", "account_stats", "account_credits"):
        graph.add_edge(name, END)
    return graph.compile()


def _next(state: dict) -> str:
    """积分是账户里剩下的那一种，单独走默认节点。"""
    return {
        "me.profile": "account_profile",
        "me.auth_list": "account_auth",
        "me.stats": "account_stats",
    }.get(state.get("intent") or "", "account_credits")


def _profile(state: dict, java) -> dict:
    """昵称和明确记下的句子都来自工具。两笔请求一起发。"""
    del state
    try:
        me, memories = run_together([java.get_me, lambda: java.search_memory(20)])
    except ToolFailed:
        return _unavailable()
    texts = [row.get("text") for row in memories if row.get("text")]
    return {"reply": f"{me.get('nickname') or ''} {' '.join(texts)}".strip(), "clarify": None, "cards": []}


def _auth(java) -> dict:
    try:
        rows = java.list_authorizations()
    except ToolFailed:
        return _unavailable()
    return {"reply": " ".join(str(row.get("platform")) for row in rows), "clarify": None, "cards": []}


def _stats(java) -> dict:
    """单量、实付和本月预估只复述接口。订单统计和钱包一起查。"""
    try:
        stats, wallet = run_together([java.get_order_stats, java.get_wallet])
    except ToolFailed:
        return _unavailable()
    return {"reply": f"单量{stats.get('count')} 实付{stats.get('payAmount')} 返佣{wallet.get('monthEstimate')}", "clarify": None, "cards": []}


def _credits(java) -> dict:
    """只报告余额。不创建套餐订单，也不读取支付地址。"""
    try:
        credits = java.get_credits()
    except ToolFailed:
        return _unavailable()
    return {"reply": f"积分{credits.get('available')}", "clarify": None, "cards": []}


def _unavailable() -> dict:
    return {"reply": "暂时查不到", "clarify": None, "cards": []}
