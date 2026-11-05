from app.graph.model import ScriptedModel
from tests.harness import body_json, envelope, events, graph_for, speak


def goods(count, sales=None):
    return [
        {
            "goodsId": f"g{i}",
            "title": f"纸巾{i}",
            "price": "9.90",
            "salesCount": sales,
            "reviewCount": sales,
            "goodsUrl": f"https://item.example/{i}",
        }
        for i in range(count)
    ]


def test_cards_follow_the_count_and_do_not_invent():
    store = {"rows": []}

    def handler(request):
        path = request.url.path
        if path.endswith("/memory/search"):
            return envelope(store["rows"])
        if path.endswith("/memory"):
            store["rows"].append(body_json(request))
            return envelope({"id": "m1"})
        if path.endswith("/goods/search"):
            payload = body_json(request)
            store["search"] = payload
            count = store.get("count", 0)
            if count > 6:
                return envelope({"collectionId": "col-1", "total": count, "goods": goods(4)})
            return envelope({"goods": goods(count, sales=None)})
        return envelope({})

    model = ScriptedModel({
        "我要买纸巾": {"intent": "shop.search", "confidence": 0.9, "slots": {"keywords": ["纸巾"]}, "reply": "好"},
        "买": {"intent": "shop.search", "confidence": 0.9, "slots": {"keywords": []}, "reply": "买什么"},
        "没把握": {"intent": "shop.search", "confidence": 0.4, "slots": {"keywords": ["纸巾"]}, "reply": "哪一种"},
        "又买又查": {"intent": "shop.search", "confidence": 0.9, "slots": {"keywords": ["纸巾"]}, "secondary": ["order.query"], "reply": "先买"},
        "记住预算 200": {"intent": "chitchat", "confidence": 0.9, "slots": {}, "reply": "记下了"},
        "买纸巾": {"intent": "shop.search", "confidence": 0.9, "slots": {"keywords": ["纸巾"]}, "reply": "好"},
        "预算 50 的纸巾": {"intent": "shop.search", "confidence": 0.9, "slots": {"keywords": ["纸巾"], "budgetMax": "50"}, "reply": "好"},
    })
    graph, java = graph_for(model, handler)

    store["count"] = 6
    six = events(speak(graph, "我要买纸巾", "six"))
    assert [item["event"] for item in six if item["event"] == "product_cards"]
    assert six[-3]["data"]["list"][0]["salesCount"] is None
    assert not [item for item in six if item["event"] == "collection_card"]
    assert len([item for item in six if item["event"] == "product_cards"][0]["data"]["list"]) == 6

    store["count"] = 7
    seven = events(speak(graph, "我要买纸巾", "seven"))
    assert not [item for item in seven if item["event"] == "product_cards"]
    covers = [item for item in seven if item["event"] == "collection_card"][0]["data"]["covers"]
    assert len(covers) <= 4

    store["count"] = 0
    empty = events(speak(graph, "我要买纸巾", "empty"))
    assert "没找到" in empty[1]["data"]["text"]
    assert not [item for item in empty if item["event"] in {"product_cards", "product_card", "collection_card"}]

    before = len(java.calls)
    speak(graph, "买", "ask")
    speak(graph, "没把握", "low")
    assert not any(path.endswith("/goods/search") for _method, path in java.calls[before:])

    speak(graph, "又买又查", "both")
    assert any(path.endswith("/goods/search") for _method, path in java.calls)
    assert not any(path.endswith("/orders") for _method, path in java.calls)
    both = events(speak(graph, "又买又查", "both-2"))
    assert "order.query" in both[-1]["data"]["pendingIntents"]

    speak(graph, "记住预算 200", "mem")
    speak(graph, "买纸巾", "mem")
    assert store["search"]["budgetMax"] == "200"
    speak(graph, "预算 50 的纸巾", "mem")
    assert store["search"]["budgetMax"] == "50"
