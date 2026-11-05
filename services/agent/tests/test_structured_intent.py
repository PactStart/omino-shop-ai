from app.graph.intent_schema import IntentDecision, IntentName, IntentSlots, decision_from
from app.graph.model import model_from_request


class _Raw:
    usage_metadata = {"input_tokens": 5, "output_tokens": 6}
    content = '{"intent":"shop.search","confidence":0.99,"slots":{"keywords":["纸巾"]}}'


def test_router_uses_function_calling_and_ignores_free_text(monkeypatch):
    captured = {}

    class FakeRunnable:
        def invoke(self, messages):
            captured["messages"] = messages
            return {"raw": _Raw(), "parsed": None, "parsing_error": RuntimeError("no tool call")}

    class FakeChat:
        def __init__(self, **kwargs):
            captured["client"] = kwargs

        def with_structured_output(self, schema, **kwargs):
            captured["schema"] = schema
            captured["method"] = kwargs.get("method")
            captured["include_raw"] = kwargs.get("include_raw")
            return FakeRunnable()

        def invoke(self, messages):
            raise AssertionError("intent must not use free text")

    monkeypatch.setattr("langchain_openai.ChatOpenAI", FakeChat)
    model = model_from_request({"model": "deepseek-chat", "baseUrl": "https://api.deepseek.com", "apiKey": "k"})
    decision = model.generate("买纸巾", {"intent.router": "只输出 JSON"}, purpose="intent", prompt_key="intent.router")
    assert captured["method"] == "function_calling"
    assert captured["include_raw"] is True
    assert captured["schema"] is IntentDecision
    assert decision["intent"] == "handoff"
    assert decision["slots"] == {}
    assert decision["prompt_tokens"] == 5


def test_structured_decision_keeps_only_filled_slots():
    parsed = IntentDecision(
        intent=IntentName.SHOP_SEARCH,
        confidence=0.94,
        slots=IntentSlots(keywords=["耳机"], platforms=["jd"], budgetMax=200),
        secondary=[IntentName.CHITCHAT],
        reply="",
    )
    decision = decision_from(parsed)
    assert decision == {
        "intent": "shop.search",
        "confidence": 0.94,
        "slots": {"keywords": ["耳机"], "platforms": ["jd"], "budgetMax": 200},
        "secondary": ["chitchat"],
        "reply": "",
    }
