"""收下这一句，并记下本轮唯一的模型名。不把密钥放进检查点。

新的一句一定经过这里。授权恢复用 Command 从暂停点接着跑，不会再进这里，
所以在这里把 resumed 和上一轮追问清掉，避免下一句被当成还在恢复。
"""


def ingest(state: dict) -> dict:
    """规范化本轮输入，并清掉只属于上一轮的标记。

    @param state 调用方放进这一轮的字段，检查点里可能还留着旧值
    @return 本轮要从头计的那些字段
    """
    return {
        "text": state.get("text") or "",
        "input_type": state.get("input_type") or "text",
        "source_url": state.get("source_url"),
        "image_object_key": state.get("image_object_key"),
        "model": state.get("model"),
        "prompt_tokens": 0,
        "completion_tokens": 0,
        "cards": [],
        "pending_intents": [],
        "clarify": None,
        "resumed": False,
    }
