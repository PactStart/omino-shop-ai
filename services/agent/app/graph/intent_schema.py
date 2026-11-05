"""意图路由的结构。字段由接口校验，不靠提示词里的示例。"""

from enum import Enum
from typing import Any

from pydantic import BaseModel, Field


class IntentName(str, Enum):
    """与对话契约里的点分意图一一对应。"""

    SHOP_SEARCH = "shop.search"
    SHOP_GIFT = "shop.gift"
    SHOP_SCENE = "shop.scene"
    SHOP_LINK = "shop.link"
    SHOP_IMAGE = "shop.image"
    SHOP_COMPARE = "shop.compare"
    ME_PROFILE = "me.profile"
    ME_AUTH_LIST = "me.auth_list"
    ME_STATS = "me.stats"
    ME_CREDITS = "me.credits"
    ORDER_QUERY = "order.query"
    AUTH_GRANT = "auth.grant"
    AUTH_REVOKE = "auth.revoke"
    EARN_INVITE = "earn.invite"
    KB_FAQ = "kb.faq"
    TICKET_CREATE = "ticket.create"
    CHITCHAT = "chitchat"
    HANDOFF = "handoff"


class IntentSlots(BaseModel):
    """这句话里明确出现的槽位。没说的留空。"""

    keywords: list[str] = Field(default_factory=list, description="原话里的商品词")
    itemList: list[str] = Field(default_factory=list, description="场景清单")
    budget: float | None = Field(default=None, description="预算，单位元")
    budgetMin: float | None = Field(default=None, description="预算下限，单位元")
    budgetMax: float | None = Field(default=None, description="预算上限，单位元")
    platforms: list[str] = Field(default_factory=list, description="原话点名的平台码")
    platform: str | None = Field(default=None, description="授权或取消授权的一个平台码")
    scene: str | None = Field(default=None, description="场景")
    sourceUrl: str | None = Field(default=None, description="商品链接")
    referents: list[str] = Field(default_factory=list, description="原话里的商品编号")
    orderId: str | None = Field(default=None, description="原话里的订单号")
    question: str | None = Field(default=None, description="规则问题原文")
    category: str | None = Field(default=None, description="工单类别")
    content: str | None = Field(default=None, description="工单内容")


class IntentDecision(BaseModel):
    """一次路由的结构。intent 只能是约定枚举。"""

    intent: IntentName
    confidence: float = Field(ge=0, le=1)
    slots: IntentSlots = Field(default_factory=IntentSlots)
    secondary: list[IntentName] = Field(default_factory=list)
    reply: str = ""


HANDOFF: dict[str, Any] = {
    "intent": "handoff",
    "confidence": 1,
    "slots": {},
    "secondary": [],
    "reply": "我帮你记下了",
}


def decision_from(parsed: IntentDecision) -> dict[str, Any]:
    """收成图节点用的普通字典。空槽位不写进去。

    @param parsed 已经通过结构校验的决定
    @return intent、confidence、slots、secondary、reply
    """
    slots = {
        key: value
        for key, value in parsed.slots.model_dump(exclude_none=True).items()
        if value not in ([], "")
    }
    return {
        "intent": parsed.intent.value,
        "confidence": float(parsed.confidence),
        "slots": slots,
        "secondary": [item.value for item in parsed.secondary],
        "reply": parsed.reply or "",
    }
