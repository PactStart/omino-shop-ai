# 官方包安装与上传

本功能不新增 HTTP。消费者与内部工具的路径仍以 [`docs/api-app.md`](../../../docs/api-app.md) 和 [`docs/api-internal.md`](../../../docs/api-internal.md) 为准。

## 文件

`services/java/sdks` 内必须有这五份坐标的 jar。淘宝另有 sources jar。旁边已有的 pom 一并在。没有 Java 源码，没有参考仓库的脚本。

| 坐标 | 附加文件 |
| --- | --- |
| `com.taobao:taobao-sdk-java:20250925` | sources jar，`classifier=sources` |
| `com.jd:open-api-sdk:20250925` | 随包 pom（参考目录里有） |
| `com.pdd:pop-sdk:1.10.16` | 随包 pom（参考目录里有） |
| `com.vip:vop-sdk:20250214094310` | 无随包 pom，用坐标参数发布 |
| `com.vip:osp-sdk:20250214094310` | 无随包 pom，用坐标参数发布 |

`osp-sdk` 只作为唯品会调用的客户端运行时，与 `vop-sdk` 一起安装。

## 命令

两条命令都在 `services/java/sdks`：

1. 安装：对上表每一份执行 `mvn install:install-file`，装进本机 Maven 仓库。
2. 上传：对上表每一份执行 `mvn deploy:deploy-file`。

上传时：

- 地址来自操作者 `settings.xml` 已激活属性 `omino.sdk.repo.url`。
- 认证使用同一文件里的 server id `omino-shop-sdk`。
- 属性或 server 不存在：命令非零退出。
- 脚本、POM、日志不包含地址和口令。

依赖进入 `shop-api-app` 之前，`AGENTS.md` §2.1 先写上坐标、版本，以及 [research.md](../research.md) 里「用哪一部分」。
