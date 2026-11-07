# Quickstart: 002-java-api

验收按用户故事 1 到 11 的顺序做。前一个故事的检查没过，不开始下一个。字段和已有路径以 `docs/data-model.md`、`docs/app-api-catalog.md`、`docs/api-admin.md`、`docs/api-internal.md`、`docs/conversation.md` 为准。本功能新加的会话和提现路径以 [contracts/](contracts/) 为准，而且必须已经写回上述权威文档。

## 准备

feat 1 的本地编排已启动：PostgreSQL + pgvector、Redis。本地 Java 可以 `NACOS_CONFIG_ENABLED=false`。短信 `SMS_PROVIDER=mock`，验证码与 omino-sso 的模拟通道一样，固定 `888888`。生产不得使用 mock。

```bash
cd services/java
mvn -pl shop-api-app,shop-api-admin -am test
```

两个模块都要测过。APP 与 Admin 的测试互相不引用对方模块。

## 故事 1–11 各看什么

1. 空库迁移后表与权威数据模型一致。`POST /api/app/v1/auth/sms/send` 后用 `888888` 登录得到凭证。第二个用户带第一个用户的邀请码登录后，`inviter_id` 已写入且邀请关系为有效。自己邀请自己被拒绝。运行生成器后，一张权威表有对应 XML，XML 里没有联盟分支。无凭证访问需登录接口为 HTTP `401`、`code` `40100`。
2. `POST /api/app/v1/chat/turns` 立刻返回 `runId`。断开 SSE 后再订阅，已有事件还在。`POST .../cancel` 之后事件名为 `cancelled`。事件名都能在 `docs/conversation.md` 里找到。`test` 与 `prod` 在 Agent 不可用时终态为 `error` 且 `code` 为 `50020`，不是样例 `done`。终态后 Stream 再保留 30 分钟。样例事件只在 `local` 或测试双写。
3. 未授权 `POST /api/app/v1/goods/convert-link` 得到 `40301`，`error.reason` 为 `NEED_AUTH`，响应无推广位标识。销量未知的详情里该项为 null。收藏两次只有一条。
4. 四个平台客户端都在 `adapter.platform`。搜品或详情失败为 `50010`。请求美团平台码被拒绝。
5. 一笔已完成且待结算的订单同步两次，只有一条返佣流水。消费者订单响应没有媒体佣金。提现：申请后可用余额减少、冻结额增加；两笔并发申请后余额不为负；打款失败后冻结额不变、状态仍是 `pending`、`message` 为「打款未成功，冻结额未动」，且能按 `pending` 查到；打款成功才有一条 `withdraw` 流水；拒绝会解冻。见 [contracts/withdrawal.md](contracts/withdrawal.md)。
6. `wallet` 购积分当场扣余额并加积分，并与提现一样先锁钱包行。微信和支付宝用测试密钥：验签失败不加积分，合法回调只加一次。不连接真实商户。没有把积分转回钱包的路径。
7. `GET /api/app/v1/invite/posters` 只有模板和矩形。公开查询邀请码不返回昵称或手机号。
8. 知识库未命中就是未命中。建单同时有一条 `created` 日志。再改状态会追加日志，旧日志不变。内部工具缺少 `X-Internal-Token` 时为 `401` / `40100`。
9. 缺少客户端头为 `40001`。站内信只出现在指定用户的收件箱。
10. 提示词只有八个键。模型读取响应没有密钥明文。上架推荐问题第 13 条为 `40001`。开一轮时注入八条提示词和抽中的一个模型。
11. 不带 `sid_admin` 访问任一运营业务接口为 `401` / `40100`。生产默认 `POST /api/admin/v1/god-view/tokens` 为 `40303`。开启后票据带 `gv=1`，有效期不超过 1 小时。仓库没有管理员密码表。

## 不在本次验收里证明的事

- 真实微信或支付宝商户扣款，以及真实支付宝出款。
- 对话图、页面、Flutter。
- 给上级的第二笔分成。
