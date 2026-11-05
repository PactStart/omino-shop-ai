"""只绑定本轮槽位或检查点里已经出现的商品。绑不上就空着，不猜编号。"""


def bind(state: dict) -> list[str]:
    slots = state.get("slots") or {}
    referents = [item for item in (slots.get("referents") or []) if item]
    if referents:
        return referents
    return [item for item in (state.get("goods_ids") or []) if item]
