"""用本轮这一个模型识别主意图。其余意图留到下一句。"""


def recognize(state: dict, model, prompts: dict) -> dict:
    # 只用路由提示词做结构化分类。话术提示词留到收口，避免这一步就把回复写死。
    decision = model.generate(
        state.get("text") or "",
        prompts,
        purpose="intent",
        prompt_key="intent.router",
    )
    secondary = list(decision.get("secondary") or [])
    return {
        "intent": decision["intent"],
        "confidence": float(decision["confidence"]),
        "slots": dict(decision.get("slots") or {}),
        "reply": decision.get("reply") or "",
        "pending_intents": secondary,
        "prompt_tokens": int(state.get("prompt_tokens") or 0) + int(decision.get("prompt_tokens") or 0),
        "completion_tokens": int(state.get("completion_tokens") or 0) + int(decision.get("completion_tokens") or 0),
        "model": state.get("model") or model.name,
    }
