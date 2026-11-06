"""把意图和把握收成下一站。没有网络。"""

INTENTS = (
    "shop.search",
    "shop.gift",
    "shop.scene",
    "shop.link",
    "shop.image",
    "shop.compare",
    "me.profile",
    "me.auth_list",
    "me.stats",
    "me.credits",
    "order.query",
    "auth.grant",
    "auth.revoke",
    "earn.invite",
    "kb.faq",
    "ticket.create",
    "chitchat",
    "handoff",
)

_SHOP = {name for name in INTENTS if name.startswith("shop.")}
_ACCOUNT = {"me.profile", "me.auth_list", "me.stats", "me.credits"}
_SUPPORT = {"kb.faq", "ticket.create", "handoff"}
_HITL = {"auth.grant", "auth.revoke"}


def route_intent(intent: str, confidence: float) -> str:
    """返回下一站的名字。把握低于 0.6 一律追问，未知意图去加急工单。"""
    if intent not in INTENTS:
        return "handoff"
    if confidence < 0.6:
        return "clarify"
    if intent in _SHOP:
        return "subgraph_shop"
    if intent in _ACCOUNT:
        return "subgraph_account"
    if intent == "order.query":
        return "subgraph_order"
    if intent == "earn.invite":
        return "subgraph_earn"
    if intent in _HITL:
        return "hitl_prepare"
    if intent in _SUPPORT:
        return "subgraph_support"
    if intent == "chitchat":
        return "chitchat"
    return "handoff"
