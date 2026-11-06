"""一轮只用一个模型对象。测试注入脚本，正式环境才连模型服务。

意图走工具调用上的结构化输出，不把自由文本解析成意图。话术只能改写节点已经准备好的事实：
价格、销量和政策句必须能在事实里找到，否则退回节点原文。这样模型写得出
更顺的一句，但编不出购物服务没返回的数字。
"""

import json
import os
import re
import time
from typing import Any, Callable

from app.graph.intent_schema import HANDOFF, IntentDecision, decision_from
from app.graph.prompts import DEFAULTS
from app.observability.log import intent_rejected, reply_reverted

_NUMBER = re.compile(r"\d+(?:\.\d+)?")


def grounded_reply(draft: str, prepared: str, facts: dict[str, Any]) -> str:
    """收下模型写的句子。句子里多出来的数字一律丢掉，改回节点原文。

    @param draft 模型根据提示词写出的那一句
    @param prepared 节点根据工具结果写好的原文
    @param facts 这一轮允许出现的事实
    @return 可以发给用户的一句
    """
    if not draft or not draft.strip():
        return prepared
    allowed = set(_NUMBER.findall(prepared))
    allowed.update(_NUMBER.findall(json.dumps(facts, ensure_ascii=False)))
    invented = set(_NUMBER.findall(draft)) - allowed
    if invented:
        return prepared
    return draft.strip()


class ScriptedModel:
    """测试双生。意图按台词表返回；话术原样交还节点准备好的句子。"""

    def __init__(self, table: dict[str, dict[str, Any]], name: str = "scripted") -> None:
        self.table = table
        self.name = name
        self.used: list[str] = []
        self.laps: list[tuple[str, int, int, int]] = []

    def generate(
        self,
        text: str,
        prompts: dict[str, str] | None = None,
        *,
        purpose: str = "intent",
        facts: dict[str, Any] | None = None,
        prompt_key: str | None = None,
    ) -> dict[str, Any]:
        """按用途返回一次决策。prompts 和 prompt_key 只为了和正式模型同一签名。

        @param text 用户原文，用来查台词表
        @param prompts 当次注入的八条提示词，脚本不拿它覆盖台词
        @param purpose intent 识别意图，reply 只复述事实
        @param facts 节点准备好的回复和追问
        @param prompt_key 这一次该用的提示词键
        @return 意图字段，或一句没有新数字的回复
        """
        del prompts, prompt_key
        started = time.perf_counter()
        self.used.append(self.name)
        row = dict(self.table.get(text) or {})
        if purpose == "reply":
            prepared = ""
            if facts:
                prepared = str(facts.get("clarify") or facts.get("reply") or "")
            prompt_tokens = int(row.get("prompt_tokens") or 0)
            completion_tokens = int(row.get("completion_tokens") or 0)
            self.laps.append((purpose, _elapsed_ms(started), prompt_tokens, completion_tokens))
            return {
                "reply": prepared or row.get("reply") or "",
                "prompt_tokens": prompt_tokens,
                "completion_tokens": completion_tokens,
            }
        row.setdefault("intent", "chitchat")
        row.setdefault("confidence", 0.9)
        row.setdefault("slots", {})
        row.setdefault("reply", "")
        row.setdefault("secondary", [])
        row.setdefault("prompt_tokens", 0)
        row.setdefault("completion_tokens", 0)
        self.laps.append((
            purpose,
            _elapsed_ms(started),
            int(row.get("prompt_tokens") or 0),
            int(row.get("completion_tokens") or 0),
        ))
        return row


class OpenAiModel:
    """正式模型。同一个客户端服务这一轮的意图和话术。"""

    def __init__(self, name: str, complete: Callable[..., Any], route: Callable[..., Any] | None = None) -> None:
        self.name = name
        self.complete = complete
        self.route = route
        self.used: list[str] = []
        self.laps: list[tuple[str, int, int, int]] = []
        self.trace_id = ""

    def generate(
        self,
        text: str,
        prompts: dict[str, str] | None = None,
        *,
        purpose: str = "intent",
        facts: dict[str, Any] | None = None,
        prompt_key: str | None = None,
    ) -> dict[str, Any]:
        """把当次提示词和事实发给模型。话术结果过一遍数字检查。

        @param text 用户原文
        @param prompts 当次注入的提示词
        @param purpose intent 或 reply
        @param facts 节点已经查到的事实；意图调用不用
        @param prompt_key 空则按用途取默认键
        @return 解析后的意图，或接地后的一句回复
        """
        started = time.perf_counter()
        self.used.append(self.name)
        prompts = prompts or {}
        key = prompt_key or ("intent.router" if purpose == "intent" else "reply.chitchat")
        # 这一键没注入正文时用内置默认，避免系统侧是空的。已有正文不替换。
        prompt = str(prompts.get(key) or "").strip() or DEFAULTS.get(key, "")
        if purpose == "reply":
            message, prompt_tokens, completion_tokens = self.complete(prompt, text, facts)
            prepared = ""
            if facts:
                prepared = str(facts.get("clarify") or facts.get("reply") or "")
            sentence = grounded_reply(str(message or ""), prepared, facts or {})
            if sentence != (str(message or "").strip() or prepared):
                reply_reverted(self.trace_id)
            self.laps.append((purpose, _elapsed_ms(started), prompt_tokens, completion_tokens))
            return {
                "reply": sentence,
                "prompt_tokens": prompt_tokens,
                "completion_tokens": completion_tokens,
            }
        # 意图只接受结构化结果。自由文本里即使有 JSON，也不当成路由成功。
        if self.route is None:
            parsed, prompt_tokens, completion_tokens = dict(HANDOFF), 0, 0
        else:
            parsed, prompt_tokens, completion_tokens = self.route(prompt, text)
        parsed = dict(parsed)
        parsed.setdefault("slots", {})
        parsed.setdefault("secondary", [])
        parsed.setdefault("reply", "")
        parsed["prompt_tokens"] = prompt_tokens
        parsed["completion_tokens"] = completion_tokens
        self.laps.append((purpose, _elapsed_ms(started), prompt_tokens, completion_tokens))
        return parsed


def model_from_request(llm: dict[str, Any] | None) -> OpenAiModel:
    """按当次请求建一个客户端。本地没有模型时才读环境变量。

    意图用工具调用上的结构，不靠提示词约定 JSON。
    话术仍是一句自然语言，并附上节点已经查到的事实。

    @param llm 购物服务抽中的模型；本地可以是 None
    @return 这一轮共用的模型对象
    """
    if llm is None:
        name = os.environ.get("OPENAI_MODEL", "deepseek-chat")
        base = os.environ.get("OPENAI_API_BASE", "https://api.deepseek.com")
        key = os.environ.get("OPENAI_API_KEY", "")
    else:
        name = llm["model"]
        base = llm["baseUrl"]
        key = llm["apiKey"]

    from langchain_openai import ChatOpenAI

    # 同一轮的意图和话术共用这一个客户端，避免两次调用各建一次连接。
    client = ChatOpenAI(model=name, base_url=base, api_key=key, temperature=0)
    # DeepSeek 的 Chat Completions 不接受 json_schema。结构放在工具参数里。
    router = client.with_structured_output(IntentDecision, method="function_calling", include_raw=True)
    holder: dict[str, OpenAiModel] = {}

    def complete(prompt: str, text: str, facts: dict[str, Any] | None) -> tuple[str, int, int]:
        user = text
        if facts is not None:
            user = json.dumps(
                {"text": text, "facts": facts, "rule": "只根据这些事实写一句给用户看的话，不要新增数字。"},
                ensure_ascii=False,
            )
        message = client.invoke([
            {"role": "system", "content": prompt},
            {"role": "user", "content": user},
        ])
        usage = getattr(message, "usage_metadata", None) or {}
        content = message.content
        if isinstance(content, list):
            content = "".join(str(part.get("text") or "") if isinstance(part, dict) else str(part) for part in content)
        return str(content or ""), int(usage.get("input_tokens") or 0), int(usage.get("output_tokens") or 0)

    def route(prompt: str, text: str) -> tuple[dict[str, Any], int, int]:
        """只接受通过结构校验的决定。校验失败不读取自由文本。"""
        try:
            result = router.invoke([
                {"role": "system", "content": prompt},
                {"role": "user", "content": text},
            ])
        except Exception:
            intent_rejected(holder["model"].trace_id)
            return dict(HANDOFF), 0, 0
        if isinstance(result, IntentDecision):
            return decision_from(result), 0, 0
        if not isinstance(result, dict):
            intent_rejected(holder["model"].trace_id)
            return dict(HANDOFF), 0, 0
        raw = result.get("raw")
        usage = getattr(raw, "usage_metadata", None) or {}
        prompt_tokens = int(usage.get("input_tokens") or 0)
        completion_tokens = int(usage.get("output_tokens") or 0)
        parsed = result.get("parsed") if isinstance(result, dict) else None
        if not isinstance(parsed, IntentDecision):
            intent_rejected(holder["model"].trace_id)
            return dict(HANDOFF), prompt_tokens, completion_tokens
        return decision_from(parsed), prompt_tokens, completion_tokens

    model = OpenAiModel(name, complete, route)
    holder["model"] = model
    return model


def _elapsed_ms(started: float) -> int:
    return int((time.perf_counter() - started) * 1000)
