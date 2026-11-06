"""用户明确说记住时写入。客服、查账和工单不写。"""

import re

from app.tools.java_client import JavaClient, ToolFailed

_BUDGET = re.compile(r"记住预算\s*([0-9]+(?:\.[0-9]+)?)")
_PLATFORM = re.compile(r"记住平台\s*(\S+)")
_NAMES = {"京东": "jd", "淘宝": "taobao", "拼多多": "pdd", "唯品会": "vip", "jd": "jd", "taobao": "taobao", "pdd": "pdd", "vip": "vip"}
_SKIP = ("me.", "order.", "kb.", "ticket.", "auth.", "handoff")


def remember(state: dict, java: JavaClient) -> dict:
    intent = state.get("intent") or ""
    if intent == "handoff" or any(intent.startswith(prefix) for prefix in _SKIP):
        return {}
    text = state.get("text") or ""
    bodies = []
    budget = _BUDGET.search(text)
    if budget:
        bodies.append({"kind": "budget", "text": budget.group(1), "confidence": 1, "source": "explicit"})
    platform = _PLATFORM.search(text)
    if platform:
        name = _NAMES.get(platform.group(1), platform.group(1))
        bodies.append({"kind": "platform", "text": name, "confidence": 1, "source": "explicit"})
    for body in bodies:
        try:
            java.write_memory(body)
        except ToolFailed:
            return {}
    return {}
