"""闲聊只回短文本，不调用业务工具。"""


def chat(state: dict) -> dict:
    return {"reply": state.get("reply") or "在的", "clarify": None, "cards": []}
