# 出站行为

不新增路径，不改信封。搜品、详情、转链仍走 [`docs/api-app.md`](../../../docs/api-app.md)。内部搜品仍走 [`docs/api-internal.md`](../../../docs/api-internal.md) 的既有工具，和消费者共用平台适配层。

## 调用前

| 条件 | 结果 | 出站 |
| --- | --- | --- |
| 平台码不是 `taobao` / `jd` / `pdd` / `vip` | 拒绝 | 无 |
| 转链时该用户未授权该平台 | `40301`，`error.reason=NEED_AUTH` | 无。先于密钥检查 |
| 搜品、按商品号拉详情或转链时，当前用户没有可用的 `relation_id` | `40301`，`error.reason=NEED_AUTH` | 无。先于密钥检查。拼多多读该用户京东那条，其余平台读本平台那条 |
| 搜品、详情、订单增量，或已授权且已有推广身份后的转链，任一必需密钥为空 | `50010` | 无 |
| 密钥齐全且推广身份齐全 | 调用 research.md 中该平台的那一个官方请求 | 有 |

必需密钥：淘宝 `UNION_TAOBAO_APP_KEY` / `UNION_TAOBAO_APP_SECRET`，京东 `UNION_JD_APP_KEY` / `UNION_JD_APP_SECRET`。不读取 `UNION_TAOBAO_PID` 和 `UNION_JD_SITE_ID`。淘宝搜品和转链仍用固定广告位，并带上该用户淘宝 `relation_id`。京东转链不带站点号，`pid` 用该用户京东 `relation_id`。拼多多 `UNION_PDD_CLIENT_ID` / `UNION_PDD_CLIENT_SECRET`；`pid` 用该用户京东 `relation_id`，`custom_parameters` 为 `{"uid":"<用户号>"}`。唯品会 `UNION_VIP_APP_KEY` / `UNION_VIP_APP_SECRET`。有当前用户时 `openId` 用该用户唯品会 `relation_id`，`chanTag` 仍是 `default_pid`。订单增量没有当前用户，唯品会订单列表仍只用 `default_pid`。推广身份先读 Redis（10 分钟），没有再查 `platform_authorization` 并写入 Redis。

## 调用后

| 官方结果 | 调用方 | 落库 |
| --- | --- | --- |
| 搜品至少一条，详情一条，字段能映射 | 现有商品契约。销量、评价数按 data-model.md | 瘦快照 |
| 已授权转链得到可购买链接 | 现有转链契约。未安装 APP 时的链接形态不变 | 成功后写 `click_event` |
| 订单增量至少一笔能映射的订单 | 不直接给消费者返回官方报文 | 统一订单；符合入账条件时仍只写一条返佣 |
| 官方正常返回的空列表 | 搜品为空列表。订单增量写 0 笔 | 不写点击。该次算成功拉取，水位按 feat 2 前进。不算正向验收 |
| 超时（10 秒）、官方拒绝、唯品会白名单拒绝 | `50010` | 不写点击。该平台水位不前进。`last_error` 为固定文案 |
| 响应、`message`、日志、`last_error` | 不含密钥、PID、relationId、堆栈、SQL、官方原文 |  |

一个平台的订单拉取失败不影响另外三个平台。

正向验收样本见 [quickstart.md](../quickstart.md)。唯品会的四类成功样本只在来源 IP 已加入白名单时采集。
