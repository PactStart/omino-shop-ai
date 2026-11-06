import json

import httpx

from app.graph.builder import build_graph
from app.graph.model import ScriptedModel
from app.graph.runner import run_turn
from app.graph.turn import attach
from app.tools.java_client import JavaClient

PROMPTS = {
    "intent.router": "路由",
    "clarify": "追问",
    "reply.shop": "导购",
    "reply.account": "账户",
    "reply.order": "订单",
    "reply.earn": "邀请",
    "reply.support": "客服",
    "reply.chitchat": "闲聊",
}


def envelope(data):
    return httpx.Response(200, json={"code": 0, "data": data})


def graph_for(model: ScriptedModel, handler):
    client = httpx.Client(transport=httpx.MockTransport(handler))
    java = JavaClient("http://java", "token", "user-1", client)
    graph = build_graph()
    attach(graph, model, java)
    return graph, java


def speak(graph, text: str, thread: str = "c1", resume=None):
    state = {
        "text": text,
        "prompts": PROMPTS,
        "conversation_id": thread,
        "model": "scripted",
    }
    return run_turn(graph, state, thread, resume)


def events(values: dict) -> list[dict]:
    return list(values.get("events") or [])


def body_json(request: httpx.Request) -> dict:
    return json.loads(request.content.decode() or "{}")
