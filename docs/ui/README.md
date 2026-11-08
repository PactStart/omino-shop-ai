# APP UI 设计文档入口

本目录是消费者端 Android、iOS、H5 的页面权威，也是交给 Figma 或 UI 生成工具的五层设计输入。接口字段以 [`app-api-catalog.md`](../app-api-catalog.md) 为准，对话事件以 [`conversation.md`](../conversation.md) 为准，技术栈与约束以 [`AGENTS.md`](../../AGENTS.md) 为准。

## 五层结构

| 层 | 目录 | 回答的问题 | 适合何时引用 |
| --- | --- | --- | --- |
| 1 | [`01-product`](./01-product/consumer-app.md) | 做什么、给谁用、有哪些页面、哪些边界不能越过 | 新建 Figma 文件、梳理全局 IA |
| 2 | [`02-foundations`](./02-foundations/design-system.md) | 颜色、字号、间距、栅格和多端适配怎么统一 | 建 Variables、Styles、基础画板 |
| 3 | [`03-components`](./03-components/component-library.md) | 组件有哪些变体、状态和约束 | 建组件库、补组件状态 |
| 4 | [`04-flows`](./04-flows/core-flows.md) | 页面怎样连接、正常与异常路径如何流转 | 做用户流程和可点击原型 |
| 5 | [`05-screens`](./05-screens/) | 某个页面具体怎么排、显示什么状态、绑定什么接口 | 直接生成页面设计稿 |

## 推荐用法

只生成某个业务域时，直接艾特对应的第五层文档，例如：

> 根据 `@docs/ui/05-screens/chat.md`，在 Figma 中先生成该文档指定的低保真和状态矩阵。只用 V1，不新增产品能力；先给出 frame/section/component 清单，确认后再生成高保真。

第五层文档已经包含本域需要的上游摘要与链接。生成整个 APP 时，按以下顺序分批执行，不要一次生成全部页面：

1. `01-product`：确认范围、导航和待决策项。
2. `02-foundations`：建立 V1 Variables、排版、间距和画板规则。
3. `03-components`：建立基础组件及业务组件。
4. `04-flows`：先做核心流程的低保真原型。
5. `05-screens`：逐业务域生成页面及全部状态。

每轮只改变一个层次：先结构，再状态，再视觉，最后原型。没有通过上一轮验收，不进入下一轮。

## 设计交付约定

- 默认画板：H5 `390 x 844`；Android 与 iOS 在布局稳定后适配，不把 H5 放进 WebView 充当原生页面。
- 默认视觉：V1 晴空绿。V2–V5 只切换颜色 Token，不改变信息架构。
- Figma 使用 Auto Layout、Variables、Component Properties；页面不得写死方案色和圆角。
- 每个页面至少交付 `default`、`loading`、`empty`、`error`、`long-content`；交互页面再补文档指定状态。
- 所有画板用 `PageID/State/Platform` 命名，例如 `chat/generating/h5`。
- 设计稿不能自行创造接口、字段、Tab、支付渠道、授权平台或购买入口。

## 当前待决策项

以下差异不能由设计工具自行裁决，相关页面必须标注 `Decision pending`：

1. 会话列表已有 `GET /chat/conversations`。消息回放、`profile` 编辑、工单列表/详情、钱包流水仍没有完整接口。可以做结构稿，数据区域必须标 `API pending`，不能虚构字段。
2. 商城本期不做。页面结构在 [`mall.md`](./05-screens/mall.md)，不生成商城画板。

