import asyncio

import httpx
from langgraph.checkpoint.memory import MemorySaver

from app.graph.builder import build_graph
from app.graph.model import ScriptedModel
from app.graph.runner import stream_events
from app.graph.turn import Turn, attach
from app.tools.java_client import JavaClient
from tests.harness import PROMPTS, envelope, graph_for


def test_intent_is_sent_once_and_before_the_rest():
    def handler(request):
        if request.url.path.endswith("/memory/search"):
            return envelope([])
        if request.url.path.endswith("/goods/search"):
            return envelope({"goods": [{
                "goodsId": "g1",
                "title": "纸巾",
                "price": "9.90",
                "salesCount": None,
                "reviewCount": None,
                "goodsUrl": "https://item.example/1",
            }]})
        return envelope({})

    model = ScriptedModel({
        "我要买纸巾": {"intent": "shop.search", "confidence": 0.9, "slots": {"keywords": ["纸巾"]}, "reply": "好"},
    })
    graph, java = graph_for(model, handler)
    state = {"text": "我要买纸巾", "prompts": PROMPTS, "conversation_id": "stream", "model": "scripted"}
    turn = Turn(java=java, model=model, prompts=PROMPTS, conversation_id="stream")

    async def collect():
        return [item async for item in stream_events(graph, state, "stream", turn)]

    names = [item["event"] for item in asyncio.run(collect())]
    assert names[0] == "intent"
    assert names.count("intent") == 1
    assert "product_cards" in names
    assert names[-1] == "done"


class _AsyncOnlySaver(MemorySaver):
    def get_tuple(self, config):
        try:
            asyncio.get_running_loop()
        except RuntimeError:
            return super().get_tuple(config)
        raise asyncio.InvalidStateError("sync")

    async def aget_tuple(self, config):
        return MemorySaver.get_tuple(self, config)


def test_stream_reads_checkpoint_without_blocking_the_loop():
    model = ScriptedModel({
        "你好": {"intent": "chitchat", "confidence": 0.9, "slots": {}, "reply": "在的"},
    })
    java = JavaClient(
        "http://java",
        "token",
        "user-1",
        httpx.Client(transport=httpx.MockTransport(lambda _request: envelope([]))),
    )
    graph = build_graph(_AsyncOnlySaver())
    attach(graph, model, java)
    state = {"text": "你好", "prompts": PROMPTS, "conversation_id": "async", "model": "scripted"}
    turn = Turn(java=java, model=model, prompts=PROMPTS, conversation_id="async")

    async def collect():
        return [item async for item in stream_events(graph, state, "async", turn)]

    names = [item["event"] for item in asyncio.run(collect())]
    assert names[0] == "intent"
    assert names[-1] == "done"
