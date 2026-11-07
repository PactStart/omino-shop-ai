# 第 5 层：商城页面

本期不做。底栏没有商城，正式信息架构见 [`consumer-app.md`](../01-product/consumer-app.md)。下面只保留以后启用时的页面结构，当前不生成画板。

页面：`mall`、`mallSearch`。内部容器：`mallWeb`。接口以 [`app-api-catalog.md`](../../app-api-catalog.md) 的「商城与商品」为准。

## mall `/mall`

页面可包含搜索入口、运营 Banner、分类导航和猜你喜欢，但它仍是工作型消费页面，不能变成卡片堆叠的营销落地页。

- `GET /mall` 提供 banner 与 nav；搜索条和猜你喜欢不是该响应的一部分。
- Banner 自动播放由 `intervalMs` 控制，0 时不自动播放。
- Action 可能去搜索、详情或 Web；未知 action 必须安全忽略。
- 猜你喜欢由 `POST /goods/guess` 分页提供；未授权可能返回 NEED_AUTH。
- 状态：loading、content、no-banner、no-nav、guess-empty、need-auth、partial-error。

首页顺序固定为搜索、轮播、金刚分类、猜你喜欢。不渲染资源位和专题，不为它们预留可见空卡片。`page` action 只打开当时契约列出的页面。

## mallSearch `/mall/search`

结构：固定搜索栏 → 联想/热词层 → 平台与筛选 → 排序 → 商品结果 → 分页加载。筛选支持价格区间、品牌、有券；品牌只有拼多多真正支持，不能向用户承诺所有平台生效。

接口：suggestions、hot-words、goods/search。分页同时容纳 page 和可选 nextId；UI 只表达“加载更多”，不展示游标。状态：initial、suggesting、hot-words、results、filter-open、loading-more、empty、need-auth、upstream-error。

## mallWeb `/mall/web`（商城内部容器）

`webview` action 在 App 内嵌打开。它是商城导航栈里的受控容器，不是顶层页面。不得假设站内支付、登录共享或任意 URL 浏览。启用商城后，搜索页和这个容器保留底栏。
