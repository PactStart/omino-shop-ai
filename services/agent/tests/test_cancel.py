import asyncio

from app.graph.model import ScriptedModel
from app.graph.runner import run_cancellable
from tests.harness import envelope, graph_for


def test_cancel_does_not_write_a_partial_checkpoint():
    def handler(_request):
        return envelope({})

    model = ScriptedModel({"你好": {"intent": "chitchat", "confidence": 0.9, "slots": {}, "reply": "在"}})
    graph, _java = graph_for(model, handler)
    cancel = asyncio.Event()
    cancel.set()

    async def run():
        await run_cancellable(graph, {"text": "你好", "prompts": {}, "conversation_id": "c1", "model": "scripted"}, "c1", cancel)

    try:
        asyncio.run(run())
        raised = False
    except asyncio.CancelledError:
        raised = True
    assert raised
    assert not graph.get_state({"configurable": {"thread_id": "c1"}}).values
