import asyncio

from app.graph.model import ScriptedModel
from app.graph.runner import stream_events
from app.graph.turn import Turn
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
