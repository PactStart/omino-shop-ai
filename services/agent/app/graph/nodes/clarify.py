"""把握不够或槽位空着时只追问，不搜品。"""


def ask(state: dict) -> dict:
    return {"clarify": state.get("reply") or "请再说具体一点", "cards": []}
