from app.graph.model import ScriptedModel
from tests.harness import body_json, envelope, events, graph_for, speak


def test_support_and_other_shop_paths():
    seen = {}

    def handler(request):
        path = request.url.path
        seen[path] = body_json(request) if request.content else {}
        if path.endswith("/kb/search"):
            if "退货" in (seen[path].get("question") or ""):
                return envelope({"hit": False})
            return envelope({"hit": True, "content": "七天无理由"})
        if path.endswith("/tickets"):
            return envelope({"ticketId": "T100"})
        if path.endswith("/goods/parse-link"):
            return envelope({"goodsId": "g1", "title": "纸巾", "price": "1.00", "salesCount": None, "reviewCount": None, "goodsUrl": "https://item.example/1"})
        if path.endswith("/goods/parse-image"):
            return envelope({"recognized": False})
        if path.endswith("/memory/search"):
            return envelope([])
        return envelope({})

    model = ScriptedModel({
        "退货规则": {"intent": "kb.faq", "confidence": 0.9, "slots": {}, "reply": ""},
        "运费规则": {"intent": "kb.faq", "confidence": 0.9, "slots": {}, "reply": ""},
        "我要反馈": {"intent": "ticket.create", "confidence": 0.9, "slots": {"category": "feedback", "content": "页面打不开"}, "reply": ""},
        "你这个骗子": {"intent": "handoff", "confidence": 0.9, "slots": {}, "reply": ""},
        "你好": {"intent": "chitchat", "confidence": 0.9, "slots": {}, "reply": "在的"},
        "送女朋友": {"intent": "shop.gift", "confidence": 0.9, "slots": {"keywords": []}, "reply": ""},
        "开学要准备什么": {"intent": "shop.scene", "confidence": 0.9, "slots": {"keywords": []}, "reply": ""},
        "https://item.jd.com/1.html": {"intent": "shop.link", "confidence": 0.9, "slots": {"sourceUrl": "https://item.jd.com/1.html"}, "reply": ""},
        "看图": {"intent": "shop.image", "confidence": 0.9, "slots": {}, "reply": ""},
        "这两款哪个好": {"intent": "shop.compare", "confidence": 0.9, "slots": {"referents": []}, "reply": ""},
    })
    graph, java = graph_for(model, handler)

    miss = events(speak(graph, "退货规则", "kb"))
    assert "没有找到" in miss[1]["data"]["text"]
    assert not any(path.endswith("/goods/search") for _method, path in java.calls)

    hit = events(speak(graph, "运费规则", "kb2"))
    assert hit[1]["data"]["text"] == "七天无理由"

    ticket = events(speak(graph, "我要反馈", "t"))
    assert "T100" in ticket[1]["data"]["text"]

    speak(graph, "你这个骗子", "h")
    assert seen["/internal/v1/tickets"]["priority"] == "urgent"

    before = len(java.calls)
    speak(graph, "你好", "chat")
    assert java.calls[before:] == []

    speak(graph, "送女朋友", "gift")
    speak(graph, "开学要准备什么", "scene")
    assert not any(path.endswith("/goods/search") for _method, path in java.calls)

    link = events(speak(graph, "https://item.jd.com/1.html", "link"))
    assert len([item for item in link if item["event"] == "product_card"]) == 1

    image = events(speak(graph, "看图", "img", ))
    # image key is absent, so it asks. Put the key on a fresh state via speak override below.
    assert any(item["event"] == "clarify" for item in image)

    compared = events(speak(graph, "这两款哪个好", "cmp"))
    assert any(item["event"] == "clarify" for item in compared)
    assert not any(path.endswith("/goods/search") or path.endswith("/goods/detail") for _method, path in java.calls)
