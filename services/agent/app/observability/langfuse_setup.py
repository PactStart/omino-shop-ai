"""生产必须开追踪。送出前去掉模型密钥。

追踪只记意图、耗时和工具路径，不记提示词、密钥和请求体。
上报失败不影响已经生成的回复。
"""

import os

from app.observability.log import trace_failed

_client = None


def require_prod_tracing() -> None:
    if os.environ.get("APP_ENV") == "prod" and os.environ.get("LANGFUSE_ENABLED") != "true":
        raise RuntimeError("生产必须打开追踪")


def redact(payload: dict) -> dict:
    cleaned = dict(payload)
    llm = cleaned.get("llm")
    if isinstance(llm, dict):
        hidden = dict(llm)
        hidden.pop("apiKey", None)
        cleaned["llm"] = hidden
    return cleaned


def trace_id(payload: dict) -> str | None:
    return payload.get("traceId")


def record_trace(turn, intent: str, elapsed_ms: int, outcome: str) -> None:
    """有追踪开关时写一条 trace。本地没打开就直接返回。

    @param turn 这一轮的依赖
    @param intent 最终意图
    @param elapsed_ms 整轮毫秒
    @param outcome ok、cancel 或 error
    """
    if os.environ.get("LANGFUSE_ENABLED") != "true":
        return
    try:
        client = _langfuse()
        trace = client.trace(
            id=turn.trace_id or None,
            name="invoke",
            user_id=turn.user_id or None,
            session_id=turn.conversation_id or None,
            metadata={"intent": intent, "outcome": outcome, "elapsedMs": elapsed_ms},
        )
        for purpose, lap_ms, prompt_tokens, completion_tokens in getattr(turn.model, "laps", []):
            trace.span(name=purpose, metadata={
                "elapsedMs": lap_ms,
                "promptTokens": prompt_tokens,
                "completionTokens": completion_tokens,
            })
        for method, path, status, lap_ms in turn.java.timings:
            trace.span(name=f"{method} {path}", metadata={"status": status, "elapsedMs": lap_ms})
        client.flush()
    except Exception:
        trace_failed(turn.trace_id)


def _langfuse():
    global _client
    if _client is None:
        from langfuse import Langfuse

        _client = Langfuse()
    return _client
