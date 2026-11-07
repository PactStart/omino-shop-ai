# 第 5 层：订单页面

## 生成范围

页面：`orders`。默认 H5 `390 x 844`、V1。字段与枚举以 [`app-api-catalog.md`](../../app-api-catalog.md) 的订单一节为准。消费者没有订单详情页。

## orders `/orders`

### 布局

TopBar 标题 → 平台 Chips 横滑 → 订单状态 Chips 横滑 → 可选结算状态筛选 → OrderRow 列表 → TabBar。筛选区域可以吸顶，但不能占用过多首屏。

OrderRow 信息优先级：商品标题/图片 → 订单状态 → 实付 → 用户返佣 → 结算状态 → 下单时间。订单状态和结算状态分别显示，不能压成一个标签。

### 数据与动作

- `GET /orders` 支持 platform、orderStatus、settleStatus、page、size。
- platform：taobao/jd/pdd/vip；订单状态六种；结算状态三种。
- 有 `goodsId` 时，整张订单卡片进入 `goodsDetail`，卡片上不放转链，也不再单放「再次购买」。
- 没有 `goodsId` 时卡片不可点。
- 不展示媒体返佣、上级、钱包流水关联、extras 或未下发时间。

### 状态画板

loading、default、filtered、empty-all、empty-filtered、pagination-loading、error、row-opens-goods、row-no-goods、long-title、missing-amount。

空筛选结果应保留筛选条件并提供清除筛选；全局无订单只提供与产品一致的轻量引导，不能编造订单或物流。

## Figma 输出清单

1. `Order/Row` 全状态组件集。
2. orders 的默认、筛选空态、加载与错误。
3. 原型：筛选后点有商品的订单卡片，进入商品详情。

