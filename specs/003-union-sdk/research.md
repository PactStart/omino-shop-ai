# Research: 003-union-sdk

未知项是：官方包用哪一部分、怎样安装和上传且口令不进 Git、出站落在哪一层、正向样本怎样验收。HTTP 路径、错误码、游标表和入账规则不在这里重选。

## 五份官方包

- Decision: 使用规格点名的五份坐标，不改版本：`com.taobao:taobao-sdk-java:20250925`（含 sources jar）、`com.jd:open-api-sdk:20250925`、`com.pdd:pop-sdk:1.10.16`、`com.vip:vop-sdk:20250214094310`、`com.vip:osp-sdk:20250214094310`。文件从参考仓库 `sdks` 复制 jar、随包 pom 和淘宝 sources jar 到 `services/java/sdks`。安装与上传脚本在本仓库新写。
- Rationale: 这些包不在 Maven Central。规格已经指定坐标。参考仓库里京东、拼多多带 pom，淘宝是二进制加 sources jar，唯品会两份只有 jar。没有随包 pom 的坐标，用 `install-file` / `deploy-file` 的坐标参数发布，不补写业务封装。
- Alternatives considered:
  - 手写各平台 HTTP 签名：宪章禁止不熟悉的协议从零手写。
  - 换一个社区封装：规格禁止改坐标，也会多一层依赖。
  - 把参考仓库本身当依赖：宪章禁止。复制范围限于上述二进制和随包 pom。
  - 复制参考仓库的安装脚本：脚本里夹着仓库地址和调用方式，规格要求命令在本仓库新写，口令只来自操作者的 `settings.xml`。

这五份是厂商 SDK，许可证不是 MIT / Apache-2.0 / BSD。规格已指定它们，计划在 Complexity Tracking 里记录这个例外。引入前先把坐标、版本和「用哪一部分」写入 `AGENTS.md` §2.1。依赖只进 `shop-api-app`。Admin 不依赖它们。写入 POM 后核对依赖树，不得抬高 Boot `3.4.11`、Jackson、MyBatis-Plus 或其它已锁定版本。厂商包若传递带入 Fastjson，业务代码不得调用。

## 每个包用哪一部分

只调用搜品、详情、转链、订单增量所需的官方请求。推荐、榜单、活动、券、处罚单、退款单、推广位生成不在本功能。

| 平台 | 使用 | 不使用 |
| --- | --- | --- |
| 淘宝 | `TbkDgMaterialOptionalUpgradeRequest` 搜品；`TbkItemInfoUpgradeGetRequest` 详情；`TbkDgGeneralLinkConvertRequest` 转链；`TbkOrderDetailsGetRequest` 按创建时间、更新时间两条增量拉单 | 物料推荐、退款单、处罚单、优惠券、活动、淘口令、渠道备案 |
| 京东 | `UnionOpenGoodsQueryRequest` 搜品和按商品号查详情；`UnionOpenPromotionBysubunionidGetRequest` 转链；`UnionOpenOrderRowQueryRequest` 订单增量 | 京粉、大字段、物料、榜单、活动 |
| 拼多多 | `PddDdkGoodsSearchRequest` 搜品；`PddDdkGoodsDetailRequest` 详情；`PddDdkGoodsPromotionUrlGenerateRequest` 转链；`PddDdkOrderListIncrementGetRequest` 订单增量 | 推荐、推广位生成与绑定、备案链接、招商转链 |
| 唯品会 | `vop-sdk` 的商品查询与按商品号取详情、按商品号生成链接、`orderList` 拉单。`osp-sdk` 只作这两份调用的客户端运行时 | 相似推荐、用户推荐、退款单、推广位生成、频道链接 |

阅读参考仓库只为确认上述官方请求的名字。适配层在本仓库重写，不复制其服务类、配置类和脚本。

## 安装与上传

- Decision: `services/java/sdks` 提供两条命令。安装命令对五份坐标执行 `mvn install:install-file`，淘宝 sources jar 使用 `classifier=sources`。上传命令对同样五份执行 `mvn deploy:deploy-file`。仓库地址用 Maven 从操作者 `settings.xml` 里已激活的属性 `omino.sdk.repo.url` 读出；认证用其中的 `<server>` id `omino-shop-sdk`。任一缺失则命令非零退出。脚本和仓库文件不写地址、口令、联盟密钥。
- Rationale: `deploy:deploy-file` 必须带 URL，但 URL 不能进 Git。`settings.xml` 的 profile 属性是操作者本机已有的位置，口令由 Maven 按 server id 附带，脚本不打印。
- Alternatives considered:
  - 把私服 URL 写进脚本或 POM：违反规格。
  - 只提供安装、不提供上传：规格要求两条命令。
  - 复制参考仓库的 `deploy-*.sh`：会把别人的仓库地址带进来。

## 出站放在平台适配层

- Decision: 官方调用只留在 `shop-api-app` 的 `adapter.platform`。`PlatformGoodsClient` 增加转链。订单拉取实现 `PlatformOrderSource`，类放在同一适配包，替换现在始终返回空列表的来源。密钥有空缺时在创建官方客户端之前抛出平台失败，不发出站。密钥齐全后才调用上表的请求。官方拒绝、超时、白名单拒绝都变成 `50010`，`message` 与日志使用固定失败文案，不拼接官方原文。官方正常返回的空列表保持空结果，水位按一次成功的拉取前进，但这不算正向验收样本。单次调用超时 10 秒。
- Rationale: 现有 `Upstream.call` 在密钥齐全时也会抛失败，这是 feat 2 的壳。转链门闩已经在 `GoodsConvertLinkService`：未授权先 `40301`，然后才签发链接。签发今天用商品页拼 deeplink，本功能改为调用适配层的官方转链。点击归因仍在签发成功之后写入。订单事务和入账留在 `OrderSyncService`：拉取在事务外，拉取失败则游标不动；拉取成功后，写入与游标前进仍在同一事务。一个平台失败不得挡住其余平台。失败时该平台 `last_error` 只写固定文案，不写入密钥。
- Alternatives considered:
  - 在 service 里直接 new 官方客户端：分层禁止，Admin 与测试也会碰到 SDK。
  - 继续用空订单来源：密钥为空时也会把游标推进到当前时间，违反「密钥为空不推进游标」。
  - 把官方报错原文返回给调用方：原文可能含请求参数里的密钥或推广位。

销量、评价数：官方字段缺失或无法解析时保持空；官方给出的数字（含 0）按给出的值保存。

## 正向样本与唯品会白名单

- Decision: 自动化测试覆盖密钥为空时的 `50010`、零出站、响应和日志不含密钥与 PID，以及既有的重复入账。淘宝、京东、拼多多的成功样本，和唯品会在白名单 IP 上的成功样本，按 [quickstart.md](quickstart.md) 用测试密钥打官方。开发机不在唯品会白名单时，本地失败记为 `50010`，不算 SC-003 的通过样本。
- Rationale: 规格要求成功结果，并且联盟账号已有数据。把真实出站放进每次 CI 会依赖外网、账号和白名单。空密钥路径可以在 CI 里稳定证明。
- Alternatives considered:
  - 录制响应代替真实出站：规格澄清已经要求成功样本来自官方。
  - 唯品会本地失败也算通过：澄清已经否定。
