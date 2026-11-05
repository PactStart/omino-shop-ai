"""购物服务的工具白名单。每次请求都带当前用户，调用方不能改成别人。

正式环境共用一个连接池。每轮新建客户端会把到购物服务的连接拆掉再建。
测试传入自己的客户端，避免打到真的服务。
"""

import time
from concurrent.futures import ThreadPoolExecutor

import httpx

from app.observability.log import tool_failed

# 读超时 5 秒。连接保持给后面的轮次用，单轮不再新建。
_POOL = httpx.Client(
    timeout=5.0,
    limits=httpx.Limits(max_keepalive_connections=20, max_connections=50),
)


class ToolFailed(Exception):
    def __init__(self, status: int) -> None:
        super().__init__(f"tool {status}")
        self.status = status


class JavaClient:
    def __init__(self, base_url: str, token: str, user_id: str, client: httpx.Client | None = None) -> None:
        self.base_url = base_url.rstrip("/")
        self.token = token
        self.user_id = user_id
        self.client = client or _POOL
        self.trace_id = ""
        self.calls: list[tuple[str, str]] = []
        self.timings: list[tuple[str, str, int, int]] = []

    def search_goods(self, body: dict) -> dict:
        return self._post("/internal/v1/goods/search", body)

    def parse_link(self, source_url: str) -> dict:
        return self._post("/internal/v1/goods/parse-link", {"sourceUrl": source_url})

    def parse_image(self, image_object_key: str) -> dict:
        return self._post("/internal/v1/goods/parse-image", {"imageObjectKey": image_object_key})

    def goods_detail(self, goods_id: str) -> dict:
        return self._post("/internal/v1/goods/detail", {"goodsId": goods_id})

    def search_memory(self, limit: int = 20) -> list:
        data = self._post("/internal/v1/memory/search", {"limit": limit})
        return data if isinstance(data, list) else []

    def write_memory(self, body: dict) -> dict:
        return self._post("/internal/v1/memory", body)

    def get_me(self) -> dict:
        return self._get("/internal/v1/me")

    def list_authorizations(self) -> list:
        data = self._get("/internal/v1/me/authorizations")
        return data if isinstance(data, list) else []

    def start_authorization(self, platform: str) -> dict:
        return self._post("/internal/v1/me/authorizations/start", {"platform": platform})

    def revoke_authorization(self, platform: str) -> dict:
        return self._post("/internal/v1/me/authorizations/revoke", {"platform": platform})

    def list_orders(self) -> list:
        data = self._get("/internal/v1/orders")
        return data if isinstance(data, list) else []

    def get_order(self, order_id: str) -> dict | None:
        try:
            return self._get(f"/internal/v1/orders/{order_id}")
        except ToolFailed:
            return None

    def get_order_stats(self) -> dict:
        return self._get("/internal/v1/orders/stats")

    def get_wallet(self) -> dict:
        return self._get("/internal/v1/wallet")

    def get_credits(self) -> dict:
        return self._get("/internal/v1/credits")

    def get_affiliate_stats(self) -> dict:
        return self._get("/internal/v1/affiliate/stats")

    def search_kb(self, question: str) -> dict:
        return self._post("/internal/v1/kb/search", {"question": question})

    def create_ticket(self, body: dict) -> dict:
        return self._post("/internal/v1/tickets", body)

    def _post(self, path: str, body: dict) -> dict | list:
        return self._send("POST", path, body)

    def _get(self, path: str) -> dict | list:
        return self._send("GET", path, None)

    def _send(self, method: str, path: str, body: dict | None) -> dict | list:
        """发出一次工具调用并记下耗时。失败只记状态码，不记正文。"""
        self.calls.append((method, path))
        started = time.perf_counter()
        response = self.client.request(method, self.base_url + path, json=body, headers=self._headers())
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        try:
            payload = self._unwrap(response)
        except ToolFailed as failed:
            self.timings.append((method, path, failed.status, elapsed_ms))
            tool_failed(self.trace_id, method, path, failed.status, elapsed_ms)
            raise
        self.timings.append((method, path, response.status_code, elapsed_ms))
        return payload

    def _headers(self) -> dict[str, str]:
        return {"X-Internal-Token": self.token, "X-User-Id": self.user_id}

    def _unwrap(self, response: httpx.Response) -> dict | list:
        if response.status_code >= 400:
            raise ToolFailed(response.status_code)
        payload = response.json()
        if isinstance(payload, dict) and payload.get("code") not in (None, 0):
            raise ToolFailed(int(payload.get("code") or 500))
        if isinstance(payload, dict) and "data" in payload:
            return payload["data"]
        return payload


def run_together(calls: list) -> list:
    """同时发出几笔互不依赖的工具调用。任一失败就抛出，调用方决定话术。

    @param calls 无参可调用对象，每个对应一笔请求
    @return 与 calls 相同顺序的结果
    """
    if len(calls) <= 1:
        return [call() for call in calls]
    with ThreadPoolExecutor(max_workers=len(calls)) as pool:
        futures = [pool.submit(call) for call in calls]
        return [future.result() for future in futures]
