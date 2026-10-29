# Research: 002-java-api

未知项来自规格里尚未锁定的工具：DAO XML 生成器、微信/支付宝验签、返佣打款通道、管理端 BFF。已有契约（表、APP/Admin/Tool/对话）不在这里重选。

## DAO XML 生成器

- Decision: 使用 `com.baomidou:mybatis-plus-generator` `3.5.9`，只生成 Mapper 接口和 Mapper XML。不生成 Entity、Service、Controller。不使用 `mybatisplus-maven-plugin` `1.0.0`。生成器不进入两个可运行 jar 的运行时依赖，放在仅构建期能调用的测试或 `codegen` 范围。产物提交进 `shop-api-common` 的 `mapper` 包与 `resources/mapper`。手写查询放在生成器不会覆盖的 `*.custom.xml`。
- Rationale: 权威文档已经锁定 MyBatis-Plus `3.5.9`。生成器与它同版本，避免为了新生成器去升级 ORM。官方新生成器从 `3.5.1` 起可用，Maven Central 有 `3.5.9`（2024-10-23）。MyBatis-Plus 本体是 Apache-2.0。`3.5.18` 一代生成器会把 ORM 一起抬高，违反版本锁定。`mybatisplus-maven-plugin` `1.0.0` 面向旧版生成器和 MySQL，不采用。Entity 必须按 `docs/data-model.md` 手写，生成器默认实体对不上 `CHAR(26)`、`NUMERIC`、`JSONB`。
- Alternatives considered:
  - MyBatis Generator（`org.mybatis.generator`）：Apache-2.0，但不认识 MyBatis-Plus 的 `BaseMapper`，会另起一套 XML 风格。
  - 手写全部 XML：规格明确要求集成生成器。
  - 升级到当前最新生成器：会被动升级已锁定的 `3.5.9`。

实现引入该依赖前，先把坐标写入 `AGENTS.md` §2.1，并确认依赖树没有抬高 Boot、MyBatis-Plus、Jackson 或其它已锁定版本。用它的生成 API 与 XML 模板；不用它的 Service 层模板。

## 微信支付回调验签

- Decision: 使用 `com.github.wechatpay-apiv3:wechatpay-java` `0.2.17`（Apache-2.0，仓库 `wechatpay-apiv3/wechatpay-java`，约 1.5k star，2025-04 发布）。只用 APIv3 的下单与回调验签。验收用测试密钥构造合法与非法回调，不连接真实商户。
- Rationale: 规格要求验签失败不加积分、同一合法回调只入账一次。官方库负责验签，避免手写微信支付报文。Java 8+，可在 JDK 21 上使用。
- Alternatives considered:
  - 自己拼 AES-GCM 和平台证书：宪章禁止不熟悉的协议从零手写。
  - 更早的 `0.2.16`：能用，但 `0.2.17` 修了 HTTP 响应未关闭的问题，仍是 Apache-2.0。

只用通知解析和 JSAPI/Native 下单所需的那部分。不把 SDK 的示例商户站点带进仓库。引入前写入 `AGENTS.md` §2.1。若依赖树抬高已锁定版本，停止并改记一个不抬版本的 `0.2.x`。

## 支付宝回调与返佣打款

- Decision: 使用 `com.alipay.sdk:alipay-sdk-java` `4.40.1044.ALL`（Apache-2.0，仓库 `alipay/alipay-sdk-java-all`）。两处用途、两个进程：积分订单的支付宝回调验签放在 `shop-api-app`；返佣提现的单笔转账放在 `shop-api-admin`。同一坐标由 parent 管理，微信验签库不进 Admin。提现通道本功能只锁定 `alipay`。微信只用于用户买积分，不用于打款给用户。2026-10-29 依赖树已通过：Boot 仍是 `3.4.11`，Jackson 仍是 `2.18.4`，Flyway 仍是 `11.3.1`，PostgreSQL 驱动仍是 `42.7.5`。该包传递引入 `fastjson`，业务代码不得调用它。
- Rationale: 数据模型把收款账号举例为支付宝账号、手机号或邮箱，并写明渠道上线前再锁。规格要求打款成功才扣冻结额。支付宝同一 SDK 能覆盖验签和转账。微信商家转账要 openid，和当前收款账号字段不是同一种标识。
- Alternatives considered:
  - 再引入第二个支付宝库：与「已有官方 SDK 就不再引第二个」一致，拒绝。
  - 微信打款：字段对不上，本功能不打开。
  - 不打款、只改状态：规格澄清已经否定。

本地和自动化测试用可替换的打款端口制造成功或失败，生产必须走支付宝通道。测试端口实现同一套状态机，不是跳过打款。2026-10-29 的依赖树没有抬高已锁定版本，微信库只出现在 `shop-api-app`。

## 管理端登录

- Decision: 继续用已经锁定的 Spring Security OAuth2 Client，不引入第二个 OIDC 库。Java 作 BFF：浏览器只访问 `/api/admin`，授权码在服务端交换。会话 cookie 名用 `sid_admin`，不要用 `sid`。令牌只放服务端会话。路径在实现前写入 `docs/api-admin.md`，形状见 [contracts/admin-session.md](contracts/admin-session.md)。
- Rationale: `AGENTS.md` §2.1 已锁定 OAuth2 Client。omino-sso 接入指南要求 Authorization Code + PKCE S256，并提醒与 IdP 同机时 cookie 名不能撞 `sid`。指南里的 Java 示例是 Boot `3.5.3`，本仓库必须留在 Boot `3.4.11`，只借鉴端点形状，不升级框架，不复制示例工程。
- Alternatives considered:
  - 按示例把 Boot 升到 `3.5.3`：违反版本锁定。
  - 浏览器持有 access_token：宪章禁止。
  - 自建管理员密码：宪章禁止。

## 对话事件与平台适配器

- Decision: `test` 与 `prod` 的 Worker 调用 Agent，把 `docs/conversation.md` 的事件写入 Redis Stream。Agent 不可用或没有可分流模型时写事件 `error`，`code` 为 `50020`，不把样例商品写成 `done`。样例事件只允许在 `local` 或自动化测试的测试双写中注册。终态后 Stream 再保留 30 分钟。四个平台客户端只放在 `adapter.platform`。没有密钥或上游失败时返回 `50010`。订单同步测试用可控的平台样本，不把真实联盟账号当作验收。
- Rationale: 宪章要求 Worker 调用 Agent。本功能仍不实现 LangGraph。`50020` 与 `50010` 已是权威错误码。
- Alternatives considered: 在 `test`/`prod` 继续投递样例事件直到对话功能落地。那会把样例商品当成推荐，并且必须先修订宪章。本次不采用。

## 冻结额与流水

- Decision: 提现申请只在 `available_amount` 与 `frozen_amount` 之间搬移，合计不变，不写 `wallet_ledger`。拒绝把冻结额搬回可用余额，仍不写流水。打款成功才减少冻结额，并写一条 `type=withdraw` 的流水。打款失败保持冻结，不写成功流水。
- Rationale: 现有流水类型只有 `commission`、`withdraw`、`credit_buy`。再加冻结类型会改枚举。数据模型要求余额变更同时写流水，指的是用户资产合计发生变化的入账和出账。冻结是同一钱包内的占用。改可用余额或冻结额时先锁定该用户钱包行，避免并发扣成负数。
- Alternatives considered: 为冻结和失败各加流水类型。审计更细，但权威枚举没有这些值，本功能不加。
