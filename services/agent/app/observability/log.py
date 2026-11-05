"""助手日志。一行一轮，失败另起一行。

不写密钥、连接串、提示词全文和请求体。traceId 与购物服务传来的相同。
"""

import logging

logger = logging.getLogger("omino.agent")


def turn_done(turn, intent: str, elapsed_ms: int, outcome: str) -> None:
    """一轮结束时打一行汇总。

    @param turn 这一轮的依赖，上面有模型和工具的耗时
    @param intent 最终意图，取消时可能还没有
    @param elapsed_ms 从进入图到收口的毫秒
    @param outcome ok、cancel 或 error
    """
    intent_ms, reply_ms, prompt_tokens, completion_tokens = _model_laps(turn.model)
    tools = " ".join(
        f"{method} {path} {status} {ms}ms" for method, path, status, ms in turn.java.timings
    )
    logger.info(
        "turn traceId=%s conversationId=%s intent=%s model=%s outcome=%s elapsedMs=%s intentMs=%s replyMs=%s promptTokens=%s completionTokens=%s tools=%s",
        turn.trace_id or "-",
        turn.conversation_id or "-",
        intent or "-",
        getattr(turn.model, "name", "-"),
        outcome,
        elapsed_ms,
        intent_ms,
        reply_ms,
        prompt_tokens,
        completion_tokens,
        tools or "-",
    )


def tool_failed(trace_id: str, method: str, path: str, status: int, elapsed_ms: int) -> None:
    """工具没有成功。只记路径和状态，不记请求体。"""
    logger.warning(
        "tool failed traceId=%s %s %s status=%s elapsedMs=%s",
        trace_id or "-",
        method,
        path,
        status,
        elapsed_ms,
    )


def intent_rejected(trace_id: str) -> None:
    """意图没有按结构返回。不记模型原文。"""
    logger.warning("意图结构无效 traceId=%s", trace_id or "-")


def reply_reverted(trace_id: str) -> None:
    """话术里出现了事实中没有的数字，已退回节点原文。"""
    logger.warning("话术退回原文 traceId=%s", trace_id or "-")


def checkpoint_down() -> None:
    """检查点连不上。不写连接串，里面有数据库口令。"""
    logger.warning("检查点不可用")


def trace_failed(trace_id: str) -> None:
    """追踪上报失败。不影响这一轮已经生成的回复。"""
    logger.warning("trace failed traceId=%s", trace_id or "-")


def _model_laps(model) -> tuple[int, int, int, int]:
    intent_ms = 0
    reply_ms = 0
    prompt = 0
    completion = 0
    for purpose, elapsed_ms, prompt_tokens, completion_tokens in getattr(model, "laps", []):
        prompt += prompt_tokens
        completion += completion_tokens
        if purpose == "intent":
            intent_ms = elapsed_ms
        elif purpose == "reply":
            reply_ms = elapsed_ms
    return intent_ms, reply_ms, prompt, completion
