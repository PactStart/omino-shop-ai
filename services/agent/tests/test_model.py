from pathlib import Path

import pytest

from app.graph.model import ScriptedModel, grounded_reply
from app.observability.langfuse_setup import redact, require_prod_tracing
from tests.harness import envelope, events, graph_for, speak


def test_one_model_and_tracing_gate(monkeypatch):
    model = ScriptedModel({"你好": {"intent": "chitchat", "confidence": 0.9, "slots": {}, "reply": "在的", "prompt_tokens": 3, "completion_tokens": 2}})
    graph, _java = graph_for(model, lambda _request: envelope({}))
    done = events(speak(graph, "你好"))
    usage = [item for item in done if item["event"] == "usage"][0]["data"]
    assert usage["model"] == "scripted"
    assert model.used == ["scripted", "scripted"]
    assert not Path("app/prompts").exists()

    monkeypatch.setenv("APP_ENV", "prod")
    monkeypatch.setenv("LANGFUSE_ENABLED", "false")
    with pytest.raises(RuntimeError):
        require_prod_tracing()

    monkeypatch.setenv("APP_ENV", "local")
    monkeypatch.setenv("LANGFUSE_ENABLED", "false")
    require_prod_tracing()
    assert events(speak(graph, "你好", "local"))[-1]["event"] == "done"

    hidden = redact({"llm": {"model": "scripted", "apiKey": "secret"}})
    assert "apiKey" not in hidden["llm"]


def test_reply_drops_numbers_the_service_did_not_return():
    prepared = "物流未知；1.20"
    assert grounded_reply("物流未知，返佣 1.20", prepared, {"reply": prepared}) == "物流未知，返佣 1.20"
    assert grounded_reply("找到了，只要 9.99", "没找到", {"reply": "没找到"}) == "没找到"
