# 第 5 层：订单页面

## 生成范围

页面：`orders`。默认 H5 `390 x 844`、V1。字段与枚举以 [`app-api-catalog.md`](../../app-api-catalog.md) 的订单一节为准。消费者没有订单详情页。

## orders `/orders`

### 布局

TopBar 标题「我的订单」→ 吸顶的搜索、结算分段和平台筛选 → OrderRow 列表 → TabBar。不要画结算规则页，也不要画导购/自营切换。

搜索框占位「搜索订单」，匹配商品标题或平台订单号。结算分段四个字：全部、即将到账、已到账、已失效。全部不带结算和订单状态；即将到账是 `settleStatus=pending`；已到账是 `settleStatus=settled`；已失效是 `orderStatus=cancelled`。平台是四枚可再点取消的胶囊：淘宝、京东、拼多多、唯品会。没选平台就是全部平台。筛选区吸顶，不换行挤掉列表。

OrderRow 自上而下：平台色块和平台订单号，可复制；右上角单独表示结算或失效。中间是商品图、标题、订单状态、实付和下单时间。底栏只写用户返佣，用结算说法带上金额。订单状态和结算状态仍然分开，不能合成一个词。不画件数、绿钻或夺宝次数。

列表滚到末尾自动请求下一页。正在加载时给出轻量状态；没有更多数据就停止，不再请求。换筛选或搜索词时回到第一页。

### 数据与动作

- `GET /orders` 支持 platform、orderStatus、settleStatus、keyword、page、size。
- platform：taobao/jd/pdd/vip；订单状态六种；结算状态三种。`keyword` 最长 64，空表示不搜。
- 有 `goodsId` 时，整张订单卡片进入 `goodsDetail`，卡片上不放转链，也不再单放「再次购买」。复制订单号不进入详情。
- 没有 `goodsId` 时卡片不可点。
- 不展示媒体返佣、上级、钱包流水关联、extras 或未下发时间。

### 状态画板

loading、default、filtered、empty-all、empty-filtered、pagination-loading、error、row-opens-goods、row-no-goods、long-title、missing-amount。

空筛选结果应保留筛选条件并提供清除筛选；全局无订单只提供与产品一致的轻量引导，不能编造订单或物流。

## Figma 输出清单

1. `Order/Row` 全状态组件集。
2. orders 的默认、筛选空态、加载与错误。
3. 原型：筛选后点有商品的订单卡片，进入商品详情。

