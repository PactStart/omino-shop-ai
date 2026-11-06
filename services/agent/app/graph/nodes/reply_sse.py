"""事件只在这里组装。分支节点只留下回复、追问和卡片。

正常一轮的顺序是：意图、追问或正文、卡片、用量、结束。
授权暂停到不了这个节点，暂停前改调 assemble(..., pause=True)，
那样只会发出意图、卡片和结束，避免在用户还没点卡片时先写用量。
"""

from typing import Any

from app.graph.state import event

# 这些句子已经由节点按工具结果写死。再打一次模型只会拖长首字时间。
_FIXED_REPLY = {
    "暂时没搜到",
    "没找到",
    "打不开",
    "暂时没打开这几款",
    "暂时查不到",
    "不知道",
    "没有找到这条规则，可以建一张工单",
    "暂时没建成",
}


def prompt_key(intent: str) -> str:
    """按主意图选话术提示词。追问另走 clarify，不在这里判断。

    @param intent 这一轮的主意图
    @return agent_prompt 的键
    """
    if intent.startswith("shop."):
        return "reply.shop"
    if intent.startswith("me."):
        return "reply.account"
    if intent == "order.query":
        return "reply.order"
    if intent == "earn.invite":
        return "reply.earn"
    if intent.startswith("kb.") or intent.startswith("ticket.") or intent == "handoff":
        return "reply.support"
    return "reply.chitchat"


def assemble(state: dict, *, pause: bool = False) -> list[dict[str, Any]]:
    """把这一轮已经决定的内容收成事件列表。调用方负责先写好回复和卡片。

    恢复之后不再发意图，也不再发卡片：那些在暂停那一轮已经发过。
    事件列表整段替换，不用追加。追加会把上一轮的授权卡留在这一轮里。

    @param state 含意图、回复、追问、卡片和累计 token 的状态
    @param pause 为真时只发暂停所需的意图、卡片和结束
    @return 按约定顺序排好的事件
    """
    events: list[dict[str, Any]] = []
    if not state.get("resumed"):
        events.append(event("intent", {"intent": state.get("intent")}))
    if pause:
        events.extend(state.get("cards") or [])
        events.append(_done(state))
        return events
    if state.get("clarify"):
        events.append(event("clarify", {"text": state["clarify"]}))
    elif state.get("reply"):
        events.append(event("text_delta", {"text": state["reply"]}))
    events.extend(state.get("cards") or [])
    prompt = int(state.get("prompt_tokens") or 0)
    completion = int(state.get("completion_tokens") or 0)
    total = prompt + completion
    events.append(event("usage", {
        "promptTokens": prompt,
        "completionTokens": completion,
        "totalTokens": total,
        "credits": f"{total / 1000:.2f}",
        "model": state.get("model"),
    }))
    events.append(_done(state))
    return events


def finish(state: dict, model, prompts: dict | None = None) -> dict:
    """先让模型按提示词改写一句，再组装事件。恢复轮不再叫模型。

    追问和固定失败句已经定稿，不再打话术模型。其余用当前意图对应的 reply.*。
    模型若写出事实里没有的数字，模型对象会退回节点原文。

    @param state 分支已经写好回复、追问和卡片
    @param model 这一轮的同一个模型
    @param prompts 当次注入的提示词，不从检查点里读
    @return 事件列表，以及改写后的回复和累计 token
    """
    if state.get("resumed") or _reply_is_final(state):
        extra = {"prompt_tokens": 0, "completion_tokens": 0}
        phrased = state
    else:
        key = "clarify" if state.get("clarify") else prompt_key(state.get("intent") or "")
        facts = {"reply": state.get("reply") or "", "clarify": state.get("clarify") or ""}
        extra = model.generate(
            state.get("text") or "",
            prompts or {},
            purpose="reply",
            facts=facts,
            prompt_key=key,
        )
        sentence = str(extra.get("reply") or "")
        phrased = dict(state)
        if state.get("clarify"):
            phrased["clarify"] = sentence or state.get("clarify")
        elif sentence:
            phrased["reply"] = sentence
    prompt = int(state.get("prompt_tokens") or 0) + int(extra.get("prompt_tokens") or 0)
    completion = int(state.get("completion_tokens") or 0) + int(extra.get("completion_tokens") or 0)
    model_name = state.get("model") or model.name
    phrased = dict(phrased)
    phrased["prompt_tokens"] = prompt
    phrased["completion_tokens"] = completion
    phrased["model"] = model_name
    return {
        "events": assemble(phrased),
        "prompt_tokens": prompt,
        "completion_tokens": completion,
        "model": model_name,
        "reply": phrased.get("reply") or "",
        "clarify": phrased.get("clarify"),
    }


def _reply_is_final(state: dict) -> bool:
    """追问句和固定失败句不再送给模型改写。"""
    if state.get("clarify"):
        return True
    return (state.get("reply") or "") in _FIXED_REPLY


def _done(state: dict) -> dict[str, Any]:
    """结束事件带上没在这一轮执行的其余意图。"""
    return event("done", {
        "conversationId": state.get("conversation_id"),
        "intent": state.get("intent"),
        "pendingIntents": state.get("pending_intents") or [],
    })
