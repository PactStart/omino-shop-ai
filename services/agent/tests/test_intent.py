from app.graph.route import INTENTS, route_intent


def test_every_intent_has_a_destination():
    seen = {route_intent(name, 0.9) for name in INTENTS}
    assert "subgraph_shop" in seen
    assert "clarify" not in seen
    assert route_intent("shop.search", 0.9) == "subgraph_shop"
    assert route_intent("order.query", 0.9) == "subgraph_order"
    assert route_intent("auth.grant", 0.9) == "hitl_prepare"
    assert route_intent("chitchat", 0.9) == "chitchat"


def test_low_confidence_asks_and_does_not_search():
    assert route_intent("shop.search", 0.59) == "clarify"


def test_unknown_is_not_search():
    assert route_intent("shopping", 0.99) == "handoff"
