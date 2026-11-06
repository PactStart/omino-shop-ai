"""导购子图。数字和商品只来自购物服务，模型不在这张图里选工具。

外层只把 shop.* 送进来。搜索、链接、图片、对比是这张图上的四个节点，
缺关键词则停在追问节点，不会落到搜索。记忆只在进入这张图时读一次，
闲聊进不来，所以闲聊不会打到购物服务。
"""

from langgraph.graph import END, StateGraph

from app.graph.nodes.coref import bind
from app.graph.nodes.slot_fill import fill
from app.graph.state import AgentState, event
from app.graph.turn import require
from app.tools.java_client import JavaClient, ToolFailed, run_together


def compile_shop():
    """编译导购子图。工具从这一轮的配置里取，图本身可以复用。

    @return 可以挂到外层节点上的已编译子图
    """
    graph = StateGraph(AgentState)
    graph.add_node("shop_ground", lambda state, config: _ground(state, require(config).java))
    graph.add_node("shop_missing", _missing)
    graph.add_node("shop_search", lambda state, config: _search(state, require(config).java))
    graph.add_node("shop_link", lambda state, config: _link(state, require(config).java))
    graph.add_node("shop_image", lambda state, config: _image(state, require(config).java))
    graph.add_node("shop_compare", lambda state, config: _compare(state, require(config).java))
    graph.set_entry_point("shop_ground")
    graph.add_conditional_edges("shop_ground", _next, {
        "shop_missing": "shop_missing",
        "shop_search": "shop_search",
        "shop_link": "shop_link",
        "shop_image": "shop_image",
        "shop_compare": "shop_compare",
    })
    for name in ("shop_missing", "shop_search", "shop_link", "shop_image", "shop_compare"):
        graph.add_edge(name, END)
    return graph.compile()


def _ground(state: dict, java: JavaClient) -> dict:
    """读明确记忆并补上这句话没说的预算和平台。工具失败就当没有记忆。"""
    try:
        memories = java.search_memory(20)
    except ToolFailed:
        memories = []
    return {"slots": fill(state.get("slots") or {}, memories), "memories": memories}


def _next(state: dict) -> str:
    """关键词还空着的搜索、送礼和场景只去追问。其余意图各进自己的节点。"""
    intent = state.get("intent")
    slots = state.get("slots") or {}
    if intent in {"shop.search", "shop.gift", "shop.scene"} and not _keywords(slots):
        return "shop_missing"
    if intent == "shop.link":
        return "shop_link"
    if intent == "shop.image":
        return "shop_image"
    if intent == "shop.compare":
        return "shop_compare"
    return "shop_search"


def _missing(state: dict) -> dict:
    """缺关键词时不编一个词去搜。收口会把这句追问发出去。"""
    return {"clarify": "还缺关键词", "reply": "", "cards": []}


def _search(state: dict, java: JavaClient) -> dict:
    """按补全后的槽位搜一次。失败和没结果都只留下一句，不留卡片。"""
    slots = state.get("slots") or {}
    try:
        found = java.search_goods(_search_body(slots))
    except ToolFailed:
        return {"reply": "暂时没搜到", "clarify": None, "cards": [], "slots": slots}
    return _from_search(found, slots)


def _keywords(slots: dict) -> list:
    words = [item for item in (slots.get("keywords") or []) if item]
    if words:
        return words
    return [item for item in (slots.get("itemList") or []) if item]


def _search_body(slots: dict) -> dict:
    """空预算和空平台不写进请求，好让购物服务使用自己的默认。"""
    body = {"keywords": _keywords(slots)}
    if slots.get("budgetMin") not in (None, ""):
        body["budgetMin"] = slots["budgetMin"]
    if slots.get("budgetMax") not in (None, ""):
        body["budgetMax"] = slots["budgetMax"]
    elif slots.get("budget") not in (None, ""):
        body["budgetMax"] = slots["budget"]
    if slots.get("platforms"):
        body["platforms"] = slots["platforms"]
    if slots.get("scene"):
        body["scene"] = slots["scene"]
    return body


def _from_search(found: dict, slots: dict) -> dict:
    """六条及以下发商品列表，超过六条只发合集。销量为空就保持空，不用 0 顶上。"""
    goods = list((found or {}).get("goods") or [])
    if found and found.get("collectionId"):
        covers = [_card(item) for item in goods[:4]]
        card = event("collection_card", {
            "collectionId": found["collectionId"],
            "total": found.get("total"),
            "covers": covers,
        })
        return {"reply": "结果比较多，放在这个合集里", "clarify": None, "cards": [card], "slots": slots, "goods_ids": []}
    if not goods:
        return {"reply": "没找到", "clarify": None, "cards": [], "slots": slots}
    cards = [event("product_cards", {"list": [_card(item) for item in goods]})]
    return {
        "reply": "找到这些",
        "clarify": None,
        "cards": cards,
        "slots": slots,
        "goods_ids": [item.get("goodsId") for item in goods if item.get("goodsId")],
    }


def _card(item: dict) -> dict:
    return {
        "goodsId": item.get("goodsId"),
        "title": item.get("title"),
        "price": item.get("price"),
        "salesCount": item.get("salesCount"),
        "reviewCount": item.get("reviewCount"),
        "goodsUrl": item.get("goodsUrl"),
    }


def _link(state: dict, java: JavaClient, slots: dict | None = None) -> dict:
    """链接解析恰好一张卡。打不开就一句失败，不再改去搜索。"""
    slots = state.get("slots") if slots is None else slots
    slots = slots or {}
    url = slots.get("sourceUrl") or state.get("source_url")
    if not url:
        return {"clarify": "把商品链接发我", "cards": [], "slots": slots}
    try:
        found = java.parse_link(url)
    except ToolFailed:
        return {"reply": "打不开", "cards": [], "slots": slots}
    goods = (found or {}).get("goods") or found
    if not isinstance(goods, dict) or not goods.get("goodsId"):
        return {"reply": "打不开", "cards": [], "slots": slots}
    return {"reply": goods.get("title") or "找到了", "cards": [event("product_card", _card(goods))], "slots": slots}


def _image(state: dict, java: JavaClient) -> dict:
    """没图或认不出都追问。认出之后才允许走和搜索一样的出卡规则。"""
    key = state.get("image_object_key")
    if not key:
        return {"clarify": "没看到图片", "cards": []}
    try:
        found = java.parse_image(key)
    except ToolFailed:
        return {"clarify": "这张图我没认出来", "cards": []}
    if not found or found.get("recognized") is False:
        return {"clarify": "这张图我没认出来", "cards": []}
    if found.get("collectionId") or found.get("goods"):
        return _from_search(found, state.get("slots") or {})
    return {"clarify": "这张图我没认出来", "cards": []}


def _compare(state: dict, java: JavaClient) -> dict:
    """只对比已经绑定的商品。绑不上就追问，不拿关键词再搜一轮。"""
    referents = bind(state)
    if len(referents) < 1:
        return {"clarify": "要对比哪几款", "cards": []}
    try:
        # 几款详情互不依赖，一起查，避免按件数把等待时间叠起来。
        details = run_together([lambda goods_id=goods_id: java.goods_detail(goods_id) for goods_id in referents])
    except ToolFailed:
        return {"reply": "暂时没打开这几款", "cards": []}
    titles = [str((item or {}).get("title") or "") for item in details]
    return {"reply": "对比：" + "、".join(titles), "cards": []}
