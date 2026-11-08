from pathlib import Path

import pytest

from app.graph.model import ScriptedModel, grounded_reply
from app.graph.turn import Turn
from app.observability.langfuse_setup import record_trace, redact, require_prod_tracing
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


def test_record_trace_sends_intent_and_skips_prompts(monkeypatch):
    names = []

    class Box:
        def __enter__(self):
            return self

        def __exit__(self, *_args):
            return False

    class Client:
        def start_as_current_observation(self, **kwargs):
            names.append(kwargs["name"])
            assert "prompts" not in kwargs
            blob = str(kwargs.get("metadata"))
            assert "不要写出" not in blob
            return Box()

        def flush(self):
            names.append("flush")

    monkeypatch.setenv("LANGFUSE_ENABLED", "true")
    monkeypatch.setattr("app.observability.langfuse_setup._langfuse", lambda: Client())
    turn = Turn(
        java=type("Java", (), {"timings": [("GET", "/internal/v1/orders", 200, 4)]})(),
        model=type("Model", (), {"laps": [("intent", 3, 1, 2)]})(),
        prompts={"system": "不要写出"},
        trace_id="run-1",
        conversation_id="c1",
        user_id="u1",
    )
    record_trace(turn, "chitchat", 12, "ok")
    assert names == ["invoke", "intent", "GET /internal/v1/orders", "flush"]

    names.clear()
    monkeypatch.setenv("LANGFUSE_ENABLED", "false")
    record_trace(turn, "chitchat", 12, "ok")
    assert names == []


def test_reply_drops_numbers_the_service_did_not_return():
    prepared = "物流未知；1.20"
    assert grounded_reply("物流未知，返佣 1.20", prepared, {"reply": prepared}) == "物流未知，返佣 1.20"
    assert grounded_reply("找到了，只要 9.99", "没找到", {"reply": "没找到"}) == "没找到"
