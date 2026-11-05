from app.graph.model import ScriptedModel
from tests.harness import envelope, events, graph_for, speak


def test_resume_does_not_route_again_and_revokes_once():
    def handler(request):
        return envelope({"authUrl": "https://auth.example/taobao"})

    model = ScriptedModel({
        "取消淘宝授权": {"intent": "auth.revoke", "confidence": 0.95, "slots": {"platform": "taobao"}, "reply": ""},
        "我要买纸巾": {"intent": "shop.search", "confidence": 0.9, "slots": {"keywords": ["纸巾"]}, "reply": "好"},
    })
    graph, java = graph_for(model, handler)
    first = events(speak(graph, "取消淘宝授权", "auth"))
    assert len([item for item in first if item["event"] == "confirm_card"]) == 1
    assert not any(path.endswith("/revoke") for _method, path in java.calls)

    second = events(speak(graph, "", "auth", resume={"action": "confirm", "platform": "taobao"}))
    assert not [item for item in second if item["event"] == "intent"]
    assert not [item for item in second if item["event"] in {"confirm_card", "auth_card"}]
    assert sum(1 for _method, path in java.calls if path.endswith("/revoke")) == 1

    graph2, java2 = graph_for(model, handler)
    speak(graph2, "取消淘宝授权", "hold")
    speak(graph2, "", "hold", resume={"action": "cancel", "platform": "taobao"})
    assert not any(path.endswith("/revoke") for _method, path in java2.calls)

    bought = events(speak(graph, "我要买纸巾", "buy"))
    assert not [item for item in bought if item["event"] in {"auth_card", "confirm_card"}]
