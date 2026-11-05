"""授权和取消授权才暂停。恢复后不再识别意图，也不再发一张卡。

第一轮在 interrupt 之前就要把卡片交给用户，所以这里直接调用组装函数。
真正的收口节点在暂停之后才走得到，不能靠它发出这张卡。
"""

from langgraph.types import interrupt

from app.graph.nodes.reply_sse import assemble
from app.graph.state import event
from app.tools.java_client import JavaClient


def prepare(state: dict, java: JavaClient) -> dict:
    """准备授权卡或确认卡，并写成这一轮的事件。取消授权先不调工具。

    授权链接只在这里要一次。恢复时如果再要一次，用户会看到两张卡，
    也可能换成另一条链接。

    @param state 已经识别出 auth.grant 或 auth.revoke
    @param java 购物服务客户端
    @return 卡片、平台，以及暂停前要发出的事件
    """
    intent = state.get("intent")
    platform = (state.get("slots") or {}).get("platform") or "taobao"
    if intent == "auth.grant":
        started = java.start_authorization(platform)
        card = event("auth_card", {"platform": platform, "authUrl": started.get("authUrl")})
    else:
        card = event("confirm_card", {"platform": platform})
    partial = {"cards": [card], "platform": platform, "clarify": None, "reply": ""}
    return {**partial, "events": assemble({**state, **partial}, pause=True)}


def wait(state: dict, java: JavaClient) -> dict:
    """停在这里等用户点卡片。只有确认取消授权才调用解除工具。

    interrupt 抛出的暂停由运行器接住。下一次带同一线程和 hitl 进来时，
    从这里继续，不会回到意图识别。

    @param state 暂停前记下的意图和平台
    @param java 购物服务客户端
    @return 恢复标记和一句结果；卡片清空，避免收口再发一张
    """
    decision = interrupt({"intent": state.get("intent"), "platform": state.get("platform")})
    platform = state.get("platform") or "taobao"
    if isinstance(decision, dict) and decision.get("platform"):
        platform = decision["platform"]
    reply = "好的"
    if state.get("intent") == "auth.revoke":
        if isinstance(decision, dict) and decision.get("action") == "confirm":
            java.revoke_authorization(platform)
            reply = "已取消授权"
        else:
            reply = "未取消授权"
    elif state.get("intent") == "auth.grant":
        reply = "授权已继续"
    return {"resumed": True, "reply": reply, "cards": [], "clarify": None, "platform": platform}
