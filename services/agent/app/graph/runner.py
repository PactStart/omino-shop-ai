"""跑一轮。停止用任务取消，不用授权那种暂停。

图的 interrupt 只表示用户还没点授权卡。用户点「停止」时，调用方取消
正在 astream 的任务；已经写完的节点留在检查点里，半截节点不会再补。
路由一结束就把 intent 交出去，不等话术和工具全部完成。
"""

import asyncio
import time

from langgraph.errors import GraphInterrupt
from langgraph.types import Command

from app.graph.state import event
from app.graph.turn import bound, make
from app.observability.langfuse_setup import record_trace
from app.observability.log import turn_done


def run_turn(graph, state: dict, thread_id: str, resume=None) -> dict:
    """同步跑完一轮。测试走这里。暂停被接住后返回检查点里的状态。

    @param graph 已经编译的图，上面绑了模型和工具
    @param state 这一轮的输入；恢复时忽略正文，改用 resume
    @param thread_id 会话线程，授权恢复必须和上一轮相同
    @param resume 用户在卡片上的决定；空表示新的一轮
    @return 检查点里的最终状态
    """
    model, java = bound(graph)
    turn, state = make(state, model, java, thread_id)
    config = _config(thread_id, turn)
    try:
        if resume is not None:
            graph.invoke(Command(resume=resume), config)
        else:
            graph.invoke(state, config)
    except GraphInterrupt:
        pass
    snapshot = graph.get_state(config)
    return snapshot.values or {}


async def stream_events(graph, state: dict, thread_id: str, turn, resume=None):
    """边跑边交出事件。intent 在路由结束后立刻交出，收口事件接在后面。

    已经交过的 intent 不再从收口列表里重复交一次。授权暂停被接住后，
    仍然把暂停前组装好的卡片交出去。

    @param graph 已编译的图
    @param state 这一轮输入，提示词会被拿掉
    @param thread_id 会话线程
    @param turn 这一轮的模型和工具
    @param resume 授权恢复载荷
    @return 逐条事件
    """
    started = time.perf_counter()
    outcome = "ok"
    values: dict = {}
    sent_intent = False
    _, state = make(state, turn.model, turn.java, thread_id)
    # make 会再包一层 Turn。调用方已经准备好的计时对象要留在配置里。
    config = _config(thread_id, turn)
    incoming = Command(resume=resume) if resume is not None else state
    try:
        try:
            async for update in graph.astream(incoming, config, stream_mode="updates"):
                routed = update.get("intent_router") if isinstance(update, dict) else None
                if routed and resume is None and not sent_intent:
                    sent_intent = True
                    yield event("intent", {"intent": routed.get("intent")})
        except GraphInterrupt:
            pass
        values = graph.get_state(config).values or {}
        for item in values.get("events") or []:
            if sent_intent and item.get("event") == "intent":
                continue
            yield item
    except asyncio.CancelledError:
        outcome = "cancel"
        raise
    except Exception:
        outcome = "error"
        raise
    finally:
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        turn_done(turn, str(values.get("intent") or ""), elapsed_ms, outcome)
        record_trace(turn, str(values.get("intent") or ""), elapsed_ms, outcome)


async def run_cancellable(graph, state: dict, thread_id: str, cancel: asyncio.Event, resume=None) -> dict:
    """边跑边看取消标记。标记在进入图之前就已经置上时，不写检查点。

    @param graph 已经编译的图
    @param state 这一轮的输入
    @param thread_id 会话线程
    @param cancel 置上之后，最迟在下一个节点边界停下
    @param resume 授权恢复载荷
    @return 停下时检查点里的状态
    """
    if cancel.is_set():
        raise asyncio.CancelledError
    model, java = bound(graph)
    turn, state = make(state, model, java, thread_id)
    config = _config(thread_id, turn)
    incoming = Command(resume=resume) if resume is not None else state

    async def drive() -> None:
        try:
            async for _chunk in graph.astream(incoming, config):
                if cancel.is_set():
                    raise asyncio.CancelledError
        except GraphInterrupt:
            return

    task = asyncio.create_task(drive())
    try:
        await task
    except asyncio.CancelledError:
        task.cancel()
        raise
    snapshot = graph.get_state(config)
    return snapshot.values or {}


def _config(thread_id: str, turn) -> dict:
    return {"configurable": {"thread_id": thread_id, "turn": turn}}
