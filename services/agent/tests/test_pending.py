from app.graph.model import ScriptedModel
from tests.harness import envelope, events, graph_for, speak


def test_only_the_primary_intent_runs():
    def handler(request):
        if request.url.path.endswith("/memory/search"):
            return envelope([])
        if request.url.path.endswith("/goods/search"):
            return envelope({"goods": []})
        return envelope({})

    model = ScriptedModel({
        "买纸巾顺便查订单": {
            "intent": "shop.search",
            "confidence": 0.9,
            "slots": {"keywords": ["纸巾"]},
            "secondary": ["order.query"],
            "reply": "先看纸巾",
        }
    })
    graph, java = graph_for(model, handler)
    done = events(speak(graph, "买纸巾顺便查订单"))
    assert done[-1]["data"]["pendingIntents"] == ["order.query"]
    assert any(path.endswith("/goods/search") for _method, path in java.calls)
    assert not any("/orders" in path for _method, path in java.calls)
