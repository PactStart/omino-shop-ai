# Quickstart: 003-union-sdk

验收者是仓库研发。页面和对话图不在本次。命令在仓库根目录。APP 已按 `make app` 跑在 `:8181`，数据库里已有 feat 2 的表。测试密钥只放在环境变量，不写入 Git。

## 1. 官方包

```bash
test -f services/java/sdks/*taobao*20250925*.jar
test -f services/java/sdks/*source*.jar
test -f services/java/sdks/*jd*20250925*.jar
test -f services/java/sdks/*pop-sdk-1.10.16*.jar
test -f services/java/sdks/*vop-sdk*.jar
test -f services/java/sdks/*osp-sdk*.jar
```

安装命令把五份坐标装进本机 Maven 后，`shop-api-app` 能编译。上传命令在 `settings.xml` 没有 `omino.sdk.repo.url` 或 server `omino-shop-sdk` 时非零退出。`git grep` 在 `services/java/sdks` 与 `AGENTS.md` 中找不到私服口令和联盟密钥。

## 2. 密钥为空

清空一个平台的必需密钥后重启 APP。对该平台搜品、详情、已授权转链、订单增量：

- 响应 `code` 为 `50010`
- 该次没有出站
- 该平台 `order_sync_cursor` 两列水位不变
- 响应体、日志、`last_error` 里没有密钥和 PID

未授权用户转链仍是 `40301`，`error.reason` 为 `NEED_AUTH`，即使密钥已经配齐也不出站。

重复同步同一笔已完成且待结算的订单：返佣流水仍为 1 条。这条沿用 feat 2 的测试即可。

## 3. 成功样本

APP 已在 `:8181` 运行。测试密钥在 `deploy/env/local.app.env`。这条不进 `make test`，也不拉订单。

```bash
make accept                 # 淘宝、京东、拼多多、唯品会
make accept PLATFORM=jd     # 只跑一个平台
KEYWORD=耳机 make accept PLATFORM=jd
```

脚本用 mock 短信登录 `13800138000`，然后对每个平台做：

1. 查询授权状态。此时还不改授权表。京东必须是不需要授权、已授权、没有链接。其它三个 `required` 为真：已授权则没有链接，未授权则必须有一条 `authUrl`。响应不出现推广身份字段。
2. 搜品至少返回一条商品。
3. 用该商品读详情。官方没给销量或评价数时，这两项为 `null`，不是 `0`。
4. 在本地库把该用户标成已授权后转链，得到可购买链接，并留下一条点击。响应无 PID、无密钥。

官方拒绝、超时、本机唯品会白名单失败都是 `50010`，不能代替上面的成功样本。官方正常返回的空列表也不是成功样本。四个平台的搜品、详情、转链都成功，这一步才算通过。
