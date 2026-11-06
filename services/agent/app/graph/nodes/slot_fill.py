"""预算和平台以这句话为准。空着时才用未过期的明确记忆。"""


def fill(slots: dict, memories: list) -> dict:
    filled = dict(slots or {})
    explicit = [row for row in memories or [] if row.get("source") == "explicit"]
    has_budget = any(filled.get(key) not in (None, "") for key in ("budget", "budgetMin", "budgetMax"))
    if not has_budget:
        for row in explicit:
            if row.get("kind") == "budget" and row.get("text"):
                filled["budgetMax"] = row["text"]
                break
    if not filled.get("platforms"):
        platforms = [row["text"] for row in explicit if row.get("kind") == "platform" and row.get("text")]
        if platforms:
            filled["platforms"] = platforms
    return filled
