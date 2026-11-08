"""生产必须开追踪。送出前去掉模型密钥。

追踪只记意图、耗时和工具路径，不记提示词、密钥和请求体。
上报失败不影响已经生成的回复。
"""

import hashlib
import os
import re

from app.observability.log import trace_failed

_client = None
_HEX_TRACE = re.compile(r"^[0-9a-f]{32}$")


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
        from langfuse import propagate_attributes

        client = _langfuse()
        original = turn.trace_id or ""
        metadata = {"intent": intent, "outcome": outcome, "elapsedMs": elapsed_ms}
        if original:
            metadata["traceId"] = original
        trace_context = None
        otel_id = _otel_trace_id(original)
        if otel_id:
            trace_context = {"trace_id": otel_id}
        attributes = {
            "metadata": {key: str(value) for key, value in metadata.items()},
            "trace_name": "invoke",
        }
        if turn.user_id:
            attributes["user_id"] = turn.user_id
        if turn.conversation_id:
            attributes["session_id"] = turn.conversation_id
        with propagate_attributes(**attributes):
            with client.start_as_current_observation(
                name="invoke",
                as_type="span",
                trace_context=trace_context,
                metadata=metadata,
            ):
                for purpose, lap_ms, prompt_tokens, completion_tokens in getattr(turn.model, "laps", []):
                    usage = {}
                    if prompt_tokens:
                        usage["input"] = int(prompt_tokens)
                    if completion_tokens:
                        usage["output"] = int(completion_tokens)
                    with client.start_as_current_observation(
                        name=purpose or "model",
                        as_type="generation",
                        metadata={
                            "elapsedMs": lap_ms,
                            "promptTokens": prompt_tokens,
                            "completionTokens": completion_tokens,
                        },
                        usage_details=usage or None,
                    ):
                        pass
                for method, path, status, lap_ms in getattr(turn.java, "timings", []):
                    with client.start_as_current_observation(
                        name=f"{method} {path}",
                        as_type="span",
                        metadata={"status": status, "elapsedMs": lap_ms},
                    ):
                        pass
        client.flush()
    except Exception:
        trace_failed(getattr(turn, "trace_id", ""))


def _otel_trace_id(value: str) -> str | None:
    """Langfuse 只接受 32 位小写十六进制。其它追踪号用哈希稳住同一条。

    @param value Java 传来的 traceId
    @return 可写入 trace_context 的编号；空字符串返回 None
    """
    if not value:
        return None
    if _HEX_TRACE.fullmatch(value):
        return value
    return hashlib.sha256(value.encode()).hexdigest()[:32]


def _langfuse():
    global _client
    if _client is None:
        from langfuse import Langfuse

        base = os.environ.get("LANGFUSE_BASE_URL") or os.environ.get("LANGFUSE_HOST")
        _client = Langfuse(base_url=base) if base else Langfuse()
    return _client
