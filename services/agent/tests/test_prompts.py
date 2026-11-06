from app.graph.model import OpenAiModel
from app.graph.prompts import DEFAULTS, fill


def test_default_prompts_include_the_required_sections():
    for text in DEFAULTS.values():
        for title in ("角色：", "背景：", "上下文：", "目标：", "输出格式：", "约束：", "示例："):
            assert title in text
    assert "shop.search" in DEFAULTS["intent.router"]
    assert "confidence" in DEFAULTS["intent.router"]
    assert "不要新增数字" in DEFAULTS["reply.shop"]


def test_blank_prompt_keeps_stored_text_and_fills_the_rest():
    filled = fill({"intent.router": "  运营写的路由  ", "reply.shop": "  "})
    assert filled["intent.router"] == "运营写的路由"
    assert filled["reply.shop"] == DEFAULTS["reply.shop"]
    assert filled["clarify"] == DEFAULTS["clarify"]
    assert len(filled) == 8


def test_model_uses_default_when_the_injected_prompt_is_blank():
    seen = {}

    def complete(prompt, text, facts):
        seen["prompt"] = prompt
        return ("在的", 1, 1)

    OpenAiModel("fake", complete).generate("你好", {}, purpose="reply", prompt_key="reply.chitchat")
    assert seen["prompt"] == DEFAULTS["reply.chitchat"]


def test_model_keeps_a_stored_prompt():
    seen = {}

    def complete(prompt, text, facts):
        seen["prompt"] = prompt
        return ("在的", 1, 1)

    OpenAiModel("fake", complete).generate(
        "你好",
        {"reply.chitchat": "运营话术"},
        purpose="reply",
        prompt_key="reply.chitchat",
    )
    assert seen["prompt"] == "运营话术"
