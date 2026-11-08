"""内网入口。错误令牌不进图。消费者令牌也不接受。

图在响应开始之后才跑。客户端断开时取消正在 astream 的任务。
路由结束就先把 intent 写出去，卡片和正文仍等工具结果齐了再写。
"""

import asyncio
import json
import os
from collections.abc import AsyncIterator

from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import StreamingResponse

from app.graph.builder import graph_for_saver
from app.graph.model import model_from_request
from app.graph.prompts import fill
from app.graph.runner import stream_events
from app.graph.turn import Turn
from app.observability.langfuse_setup import redact, require_prod_tracing
from app.tools.java_client import JavaClient

router = APIRouter()

PROMPT_KEYS = (
    "intent.router",
    "clarify",
    "reply.shop",
    "reply.account",
    "reply.order",
    "reply.earn",
    "reply.support",
    "reply.chitchat",
)


def require_body(body: dict) -> None:
    """补完默认后八条提示词都要有正文。测试和生产还不允许这一轮没有模型。

    @param body 开一轮的请求体
    """
    prompts = body.get("prompts") or {}
    if any(not str(prompts.get(key) or "").strip() for key in PROMPT_KEYS):
        raise HTTPException(status_code=400, detail="提示词不完整")
    app_env = os.environ.get("APP_ENV", "local")
    if body.get("llm") is None and app_env in {"test", "prod"}:
        raise HTTPException(status_code=400, detail="没有可抽模型")


@router.post("/internal/v1/graph/invoke")
async def invoke(request: Request) -> StreamingResponse:
    """跑一轮并把收口事件按 SSE 写出。令牌不对时图还没建。

    同一会话的授权恢复只在进程持有共享检查点时接得上。
    没有检查点时每次都是一张新的内存图，恢复会被当成新的一轮。

    @param request 内网调用，正文含提示词、模型和用户输入
    @return text/event-stream
    """
    expected = os.environ.get("AGENT_INTERNAL_TOKEN", "")
    if request.headers.get("X-Internal-Token") != expected or not expected:
        raise HTTPException(status_code=401, detail="未授权")
    require_prod_tracing()
    body = await request.json()
    body["prompts"] = fill(body.get("prompts"))
    require_body(body)
    safe = redact(body)
    model = model_from_request(body.get("llm"))
    java = JavaClient(
        os.environ.get("JAVA_APP_INTERNAL_URL", "http://127.0.0.1:8181/api/app"),
        expected,
        str(body.get("userId") or ""),
    )
    checkpointer = getattr(request.app.state, "checkpointer", None)
    graph = graph_for_saver(checkpointer)
    state = {
        "text": body.get("text") or "",
        "input_type": body.get("inputType") or "text",
        "source_url": body.get("sourceUrl"),
        "image_object_key": body.get("imageObjectKey"),
        "prompts": body["prompts"],
        "conversation_id": body.get("conversationId"),
        "user_id": body.get("userId"),
        "model": model.name,
        "trace_id": safe.get("traceId"),
    }
    thread_id = str(body.get("conversationId") or body.get("clientMsgId") or "run")
    # 没有授权恢复载荷时不读检查点。大多数句子都不是从卡片上继续的。
    resume = body.get("hitl") if body.get("hitl") and await _paused(graph, thread_id) else None
    turn = Turn(
        java=java,
        model=model,
        prompts=state.get("prompts") or {},
        trace_id=str(state.get("trace_id") or ""),
        conversation_id=str(state.get("conversation_id") or thread_id),
        user_id=str(state.get("user_id") or ""),
    )

    async def events() -> AsyncIterator[str]:
        task = asyncio.current_task()
        watcher = asyncio.create_task(_stop_when_disconnected(request, task))
        try:
            async for item in stream_events(graph, state, thread_id, turn, resume):
                if await request.is_disconnected():
                    raise HTTPException(status_code=499, detail="cancelled")
                payload = json.dumps(item["data"], ensure_ascii=False)
                yield f"event: {item['event']}\ndata: {payload}\n\n"
        except asyncio.CancelledError:
            raise HTTPException(status_code=499, detail="cancelled") from None
        finally:
            watcher.cancel()

    return StreamingResponse(events(), media_type="text/event-stream")


async def _stop_when_disconnected(request: Request, task: asyncio.Task) -> None:
    """客户端一断开就取消图任务。刷新页面不会走到这里，那是购物服务不取消。"""
    while not task.done():
        if await request.is_disconnected():
            task.cancel()
            return
        await asyncio.sleep(0.05)


async def _paused(graph, thread_id: str) -> bool:
    """这一线程是否还停在授权节点。读不到检查点就当没有暂停。

    @param graph 已编译的图
    @param thread_id 会话线程
    @return 检查点还停在下一个节点时为真
    """
    try:
        snapshot = await graph.aget_state({"configurable": {"thread_id": thread_id}})
    except Exception:
        return False
    return bool(getattr(snapshot, "next", None))
