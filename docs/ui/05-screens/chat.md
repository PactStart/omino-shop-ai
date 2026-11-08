# 第 5 层：对话页面

## 生成范围

页面：`chat`、`chatHistory`；组件/Overlay：ChatTopBar、MessageList、Composer、SuggestionChips、GoodsCard、CollectionCard、ClarifyCard、AuthCard、ConfirmCard、TicketReceipt、attachSheet、authSheet、confirmSheet、creditSheet。

默认 H5 `390 x 844`、V1、字号 md。事件协议以 [`conversation.md`](../../conversation.md) §4.1 为准；接口目录见 [`app-api-catalog.md`](../../app-api-catalog.md)“对话”；商品字段不在本文复制。

## chat `/chat`

### 布局

```text
Safe area / optional GodViewBar
ChatTopBar (fixed)
MessageList (fills, scrolls, bottom anchored)
SuggestionChips (only empty conversation)
Composer (fixed above TabBar/keyboard)
TabBar (root state only)
chatHistory (left drawer, over this page)
```

顶栏左侧是菜单和「新对话」。菜单向左滑出历史抽屉。新对话清空当前会话并回到空对话，顶栏图标和抽屉里的第一项都是这一个动作。标题仍在顶栏，右侧在 idle 只留积分入口。生成中顶栏不放停止。

消息流、推荐问题、商品卡、合集卡、澄清、授权、确认、工单和积分卡保持原样。消息流偏紧凑，商品卡可读但不能压过对话上下文。

底栏输入分成两态，键盘出现时整条跟随，消息列表保持最后一条可见：

- 语音态：左相机，中间「按住说话」，再一个切到文字的按钮，右 `+`。按住说话期间松手发送语音，上滑取消。
- 文字态：左相机，中间输入框，再一个切回按住说话的按钮；有文字时右侧是发送，空文字时右侧是 `+`。

相机直接进入拍照。`+` 仍打开 `attachSheet`（拍照、相册、粘贴链接）。

生成中，停止留在底部拇指刚用过的位置，不放到顶栏：

- 文字态：右侧按钮改成停止。这一格在有文字时是发送，清空后才是 `+`，生成中优先显示停止。
- 语音态：中间的「按住说话」改成停止。拇指还在中间，比再去够右侧的 `+` 更近，点击面也更大。

生成中不能再拍照、发文字或打开 `+`。断线和返回都不是停止。

### 页面状态矩阵

| State | 可见内容 | 主动作 | 禁止误解 |
| --- | --- | --- | --- |
| empty | 还没有会话时显示推荐问题、空 Composer | 选问题或输入 | 推荐问题点击会直接开 run |
| resumed | 已选历史会话但本地没有气泡，只显示标题 | 继续输入 | 不回放历史，也不当成新对话 |
| idle | 已完成消息 | 发新消息 | 商品卡无购买按钮 |
| generating | 流式光标；底部主按钮变为停止 | 停止 | 停止不在顶栏；返回/断线不是停止 |
| reconnecting | 保留已有内容、轻量重连状态 | 等待或重试连接 | 不调用 cancel |
| clarify | ClarifyCard | 填入输入框再发送 | Chip 不自动搜索 |
| auth-interrupted | AuthCard | 打开 AuthSheet | 本 run 已结束 |
| confirm-interrupted | ConfirmCard | 打开 ConfirmSheet | 恢复会产生新 runId |
| credit-exhausted | 已有文字 + CreditCard | 购买积分 | 支付后不自动续跑 |
| cancelled | 保留半截输出并标识已停止 | 发新消息 | 半截不伪装正式回答 |
| error | 错误消息/重试策略 | 重新发起 | 不泄露内部错误 |

### 数据与动作

- 空会话：还没有 `conversationId` 时 `GET /chat/suggestions`。
- 历史会话：`GET /chat/conversations`。切到已有会话后，本地没有气泡时不显示推荐问题，只显示该会话标题；下一句仍发到这个会话。
- 发送：`POST /chat/turns`，先立即得到 runId。
- 流式读取：`GET /chat/runs/{runId}/events`；断线用 `Last-Event-ID`。
- 图片：先 `POST /files/image`，再以 image 输入开 run。
- 停止：只有显式点击停止才 `POST /chat/runs/{runId}/cancel`。
- `product_cards` 最多展示 6 张；更多使用 `collection_card`。
- `card_impression` 可批量上报，但埋点失败不阻断浏览。

### 消息事件对应 UI

`text_delta` 追加到当前助手消息；`product_card/product_cards` 插入商品卡；`collection_card` 插入合集卡；`clarify` 插入澄清卡；`auth_card/confirm_card` 插入中断卡且退出 generating；`ticket_created` 插入工单回执；`credit_card` 打开积分入口；终态事件停止流式反馈。

## chatHistory

从对话顶栏菜单滑出，盖在对话左侧，右侧仍能看到当前对话。点遮罩或向左滑关闭。它不是全屏新页。

抽屉自上而下只有两块：

1. **新对话。** 图标加「新对话」。点后关闭抽屉，清空会话 id，回到空的 `chat`。
2. **最近。** 会话列表来自 `GET /chat/conversations`。每一行是彩色对话图标和标题，点一行切到该会话并关闭抽屉。切换只更换当前 `conversationId`。这个接口没有消息，端上不回放历史气泡；下一句仍发到这个会话。

不要画搜索、置顶、删除、任务或技能入口。

## Overlay

- `attachSheet`：拍照、相册、粘贴链接三行；系统权限失败有反馈。
- `authSheet`：平台、授权说明、打开官方页、取消；取消不 resume。
- `confirmSheet`：清楚展示 prompt，确认/取消；动作结果形成新 run。
- `creditSheet`：余额、原因、套餐和渠道；H5 只显示钱包；关闭保留流式文字。

## Figma 输出清单

先生成 10 个 chat 状态低保真，再生成对话组件 variants，最后做 V1 高保真和三条原型：普通搜品、授权中断、积分耗尽。不要在同一轮生成其他业务域。

