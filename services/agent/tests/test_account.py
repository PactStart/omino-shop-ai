from app.graph.model import ScriptedModel
from tests.harness import envelope, events, graph_for, speak


def test_numbers_come_from_the_service():
    def handler(request):
        path = request.url.path
        if path.endswith("/orders") and request.method == "GET":
            return envelope([{"id": "o1", "logistics": None, "userCommission": "1.20", "mediaCommission": "9.99"}])
        if path.endswith("/credits"):
            return envelope({"available": "3.50", "tokensPerCredit": "1000"})
        if path.endswith("/me") and request.method == "GET":
            return envelope({"nickname": "小林"})
        if path.endswith("/memory/search"):
            return envelope([{"text": "纸巾", "source": "explicit", "kind": "free_text"}])
        if path.endswith("/wallet"):
            return envelope({"monthEstimate": "2.00"})
        if path.endswith("/orders/stats"):
            return envelope({"count": 1, "payAmount": "10.00"})
        if path.endswith("/affiliate/stats"):
            return envelope({"inviteCount": 2, "activeUsers": 1, "contribution": "0.30"})
        return envelope({})

    model = ScriptedModel({
        "查订单": {"intent": "order.query", "confidence": 0.9, "slots": {}, "reply": ""},
        "没有的订单": {"intent": "order.query", "confidence": 0.9, "slots": {}, "reply": ""},
        "积分": {"intent": "me.credits", "confidence": 0.9, "slots": {}, "reply": ""},
        "你了解我多少": {"intent": "me.profile", "confidence": 0.9, "slots": {}, "reply": ""},
        "邀请": {"intent": "earn.invite", "confidence": 0.9, "slots": {}, "reply": ""},
    })
    graph, java = graph_for(model, handler)
    order = events(speak(graph, "查订单", "o"))
    text = order[1]["data"]["text"]
    assert "未知" in text
    assert text.count("1.20") == 1
    assert "mediaCommission" not in text
    assert "9.99" not in text

    def empty_orders(request):
        if request.url.path.endswith("/orders"):
            return envelope([])
        return handler(request)

    graph2, _java2 = graph_for(model, empty_orders)
    missing = events(speak(graph2, "没有的订单", "missing"))
    assert missing[1]["data"]["text"] == "不知道"

    credits = events(speak(graph, "积分", "c"))
    assert "3.50" in credits[1]["data"]["text"]
    assert not any("pay" in path or "purchase" in path for _method, path in java.calls)

    profile = events(speak(graph, "你了解我多少", "p"))
    assert "小林" in profile[1]["data"]["text"]
    assert "纸巾" in profile[1]["data"]["text"]

    invite = events(speak(graph, "邀请", "i"))
    assert "2" in invite[1]["data"]["text"]
