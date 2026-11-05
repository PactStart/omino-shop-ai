"""图里传递的字段。检查点不保存模型密钥。

events 由收口整段写上，不使用追加合并。授权恢复要换掉暂停那一轮的卡片，
追加会把上一张确认卡再带出来。
"""

from typing import Any, TypedDict


class AgentState(TypedDict, total=False):
    text: str
    input_type: str
    source_url: str | None
    image_object_key: str | None
    prompts: dict
    conversation_id: str
    user_id: str
    model: str
    trace_id: str
    memories: list
    intent: str
    confidence: float
    slots: dict
    reply: str
    clarify: str | None
    cards: list
    events: list
    pending_intents: list
    goods_ids: list
    prompt_tokens: int
    completion_tokens: int
    platform: str
    resumed: bool


def event(name: str, data: dict[str, Any]) -> dict[str, Any]:
    return {"event": name, "data": data}
