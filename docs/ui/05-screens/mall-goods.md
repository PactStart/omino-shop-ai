# 第 5 层：商品页面

## 生成范围

正式页面：`goodsDetail`、`goodsCollection`、`goodsHistory`、`goodsFavorites`。商城首页、搜索和内嵌页在 [`mall.md`](./mall.md)，本期不做。

接口以 [`app-api-catalog.md`](../../app-api-catalog.md) 的「商城与商品」为准。商品从对话、合集、浏览、收藏或订单卡片进入。

## goodsDetail `/goods/:goodsId`

只排现有接口能填的区块。数据来自 `POST /goods/detail`、`POST /goods/similar`、`POST /goods/command`、收藏和 `POST /goods/convert-link`。没有榜单、抢购播报、领券、达人推荐、店铺评分、评价正文，也不做「分享赚 / 下单返」两个购买按钮。

页面分三层：滚动内容、滚动后出现的顶部分栏、固定底栏。详情页没有底栏。

### 滚动内容

三个锚点区块，空的整段不渲染。

1. **宝贝。** 主图横滑，图来自 `imageUrls`，右下角是当前位置。没有视频。其下依次是价格、返利、标题、规格、店铺和数量。有 `couponPrice` 时它是大号价格，`price` 作对照；只有 `price` 时大号显示它。`commissionHint` 有值时在价格下写一行「下单返」，金额只用这一个字段。标题前是平台标，标题下是 `subtitle`。`skuSummary`、`shopName` 有值才出现，店铺名不可点。`salesCount`、`reviewCount` 为整数才显示，空则隐藏该项，不写 0。
2. **详情。** 标题「商品详情」，正文是 `detailHtml`。过长时收成「展开」。`detailHtml` 为空则没有这一段。
3. **推荐。** 标题「为你推荐」，两列商品卡，数据来自 `POST /goods/similar`。卡片用对话里的商品卡字段：图、平台、标题、价格、券后价、销量、评价数、店铺名。点卡片进另一件商品的详情。没有结果则没有这一段。往下滚动按页加载，不展示游标。

### 顶部分栏

进入页面时顶栏只有返回。宝贝主图滚出视口后，顶栏中间出现三个锚点：宝贝、详情、推荐。点哪一项就滚到对应区块，滚动时高亮当前所在区块。某一段没有内容，对应锚点也不出现。主图回到视口后分栏收起，顶栏回到只有返回。

### 底部操作栏

底栏固定在页面底部，为系统手势留出安全区。从左到右三项：

| 项 | 行为 |
| --- | --- |
| 复制口令 | 调 `POST /goods/command`，成功后把 `command` 放进剪贴板并轻提示。拼多多不显示这项。其它平台返回 `40001` 时提示没有口令，不编造口令 |
| 收藏 | 调收藏或取消收藏。详情响应里没有是否已收藏，进入时按未收藏显示；本次点过之后以这次结果为准 |
| 去购买 | 唯一购买按钮。调 `POST /goods/convert-link`。`canPromote` 为 false 时不可点。`40301` 打开授权层。成功后按端打开第三方：已安装走 deeplink，其次 universal link，最后网页。`goodsUrl` 不绑这个按钮 |

`commissionHint` 只在宝贝区出现一次，不写进底栏，也不拆成佣金和额外补贴。

### 状态

loading 用稳定骨架，底栏位置不变。收藏、口令、转链各自有进行中和失败，失败不改已经展示的商品内容。画板：loading、default、scrolled-tabs、favorited、missing-metrics、no-detail、no-similar、image-error、need-auth、convert-error、command-unsupported、disabled-platform。

## goodsCollection `/goods/collections/:id`

- TopBar 标题 + 商品网格/列表 + 每项独立移除动作。
- 点击卡片进入详情；点击移除不能触发进详情。
- 空合集提供“返回对话”，不提供新建合集。
- 接口：GET 合集、DELETE 单项；设计 loading、default、removing、empty、error、forbidden。

## goodsHistory 与 goodsFavorites

复用 GoodsCard 列表。浏览记录只进详情；收藏列表允许取消收藏。分别设计 loading、default、empty、pagination-loading、error；不要新增批量删除或购物车。

## Figma 输出清单

先建立 GoodsCard、Gallery、PriceBlock、CollectionCard，再生成页面。对话、历史、收藏和合集引用同一个 GoodsCard 组件集。不生成商城首页、搜索和内嵌页。

