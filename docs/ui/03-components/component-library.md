# 第 3 层：APP 组件库

## 1. 组件组织

Figma 组件按 `Foundation/`、`Navigation/`、`Input/`、`Feedback/`、`Chat/`、`Goods/`、`Order/`、`Asset/`、`Account/`、`Overlay/` 命名。组件必须使用 Auto Layout 和语义 Variables，并用 Properties 表达状态，禁止为每个页面复制一套。

通用状态属性统一命名：

- `state`: `default | pressed | focused | disabled | loading | error`
- `size`: `sm | md | lg`
- `selected`: boolean
- `leadingIcon` / `trailingIcon`: boolean
- `theme`: 由 Variables Mode 控制，不做组件属性

## 2. 基础与导航组件

| 组件 | 关键属性 | 规则 |
| --- | --- | --- |
| `Navigation/TopBar` | title、back、action、largeTitle | action 最多一个主要动作；对话生成中的停止不放在顶栏 |
| `Navigation/TabBar` | current、items | chat/orders/me 三等分；安全区计入容器。商城本期不进底栏 |
| `Navigation/GodViewBar` | userId、nickname | H5 `gv=1` 常驻，不可关闭 |
| `Input/TextField` | label、value、error、secure | 错误就地显示；输入法和清除按钮不改变高度 |
| `Input/Otp` | length、filled、error | 6 格或接口实际长度；倒计时不挤压输入区 |
| `Input/Chip` | selected、disabled、icon | 横滑时保持 44px 可点高度 |
| `Action/Button` | hierarchy、state、icon | primary/secondary/ghost/danger；danger 只用线框红字 |
| `Feedback/Toast` | tone、text | 一句话；不承载必须确认的动作 |
| `Feedback/StatePanel` | loading/empty/error | 支持重试；空态不编造业务结果 |

## 3. 对话组件

| 组件 | 输入 | 必须设计的变体 | 硬约束 |
| --- | --- | --- | --- |
| `Chat/TopBar` | title、credits、generating | idle / generating | 右侧是积分入口；生成中也不把停止放在这里 |
| `Chat/Composer` | text、generating、attachment、mode | voice / text / recording / generating / disabled | 左相机，中为按住说话或输入框，可切换；有文字才发送，否则 `+`。生成中：语音态中间变停止，文字态右侧变停止。断线不变成 cancel |
| `Chat/MessageBubble` | role、text、status | user / assistant / system / streaming / cancelled / error | 半截取消内容需可识别，不伪装完整回答 |
| `Chat/SuggestionChips` | items | populated / hidden | 仅消息列表为空时显示，点击立即发问 |
| `Chat/ClarifyCard` | question、missingSlots | default | Chip 只填输入框，不自动提交 |
| `Chat/AuthCard` | platform、interruptId | default / completed | 打开授权 Sheet；出现后本 run 已结束 |
| `Chat/ConfirmCard` | prompt、interruptId | default / completed | 打开确认 Sheet |
| `Chat/TicketReceipt` | ticketId、status | default | 只表达“已建单” |
| `Chat/CreditCard` | reason、balance、packages | start-insufficient / exhausted-mid | 不是 HITL；关闭后保留已有文字 |

## 4. 商品与订单组件

`Goods/Card` 是对话、历史、收藏、合集共用的商品卡。属性包括图片、平台、标题、价格、券后价、销量、评价数、店铺和推荐理由。商城启用后继续用这一张卡。

必须有 `list`、`compact`、`grid`、`loading`、`image-error` 变体，但数据语义一致。规则：

- 整卡点击进入详情；卡片内没有“购买”“转链”。
- `salesCount`、`reviewCount` 为 null 时整项隐藏，不能显示 0。
- 价格使用 `color/price`；优惠信息不能比最终价格更突出。
- 超长标题最多两行；价格区稳定，不因推荐理由有无而跳动。

其他组件：

| 组件 | 规则 |
| --- | --- |
| `Goods/CollectionCard` | 最多 4 张封面拼贴，显示标题和总件数；点击进合集 |
| `Goods/Gallery` | 稳定宽高比，支持缺图与页码，不使用装饰性裁切遮住商品 |
| `Goods/PriceBlock` | 明确最终价、原价/优惠的层级；积分数字不可混入 |
| `Order/Row` | 平台订单号、商品、订单状态、结算状态、实付和用户返佣分层；有 goodsId 时整卡进入商品详情，否则不可点 |
| `Order/StatusBadge` | 右上角只表示结算或失效，商品下方另写订单状态，两套语义不能合成一个词 |

## 5. 资产与账号组件

| 组件 | 规则 |
| --- | --- |
| `Asset/CreditHero` | 数字无 `¥`，显示 tokensPerCredit 说明 |
| `Asset/MoneyHero` | 金额必须带 `¥`，available/frozen/total 分层 |
| `Asset/PackageCard` | name/credits/price 三列或三层；支持 selected；不展示成本价 |
| `Asset/LedgerRow` | 金额正负、类型、时间与余额层级明确；缺接口的页面标 API pending |
| `Account/MeHeader` | 头像、昵称、脱敏手机；不得显示完整身份信息 |
| `Account/InboxBadge` | count=0 隐藏；大数采用稳定上限显示 |
| `Account/AuthRow` | 平台、required、authorized、动作；京东不提供授权/取消按钮 |
| `Invite/PosterCarousel` | 模板全幅横滑、Dots；二维码贴入服务端矩形并保留静区 |

## 6. Overlay 组件

| 组件 | 高度与动作 | 约束 |
| --- | --- | --- |
| `Overlay/AttachSheet` | 三行动作，内容自适应 | 图片先上传，链接作为消息输入 |
| `Overlay/AuthSheet` | 打开授权 / 取消 | 取消不 resume，不是支付 |
| `Overlay/ConfirmSheet` | 确认 / 取消 | HITL action 必须明确 |
| `Overlay/CreditSheet` | 套餐列表和支付入口 | H5 只显示钱包；支付后不自动续跑 |
| `Overlay/OptionalUpdate` | 稍后 / 更新 | force 更新不能复用可关闭变体 |
| `Overlay/Announcement` | 单条或多条滑动 | plain 与白名单 HTML 分开渲染 |

Sheet 最大高度为视口 92%，内容溢出时内部滚动，主动作保持可见。Modal/Drawer 上不显示 TabBar。

## 7. 组件验收

每个组件检查：主题 V1–V5、字号 sm/md/lg、长中文、空字段、loading/error/disabled、44px 点击区、对比度、键盘和安全区。组件只有视觉但没有状态属性，不能标记为 `Ready for dev`。

