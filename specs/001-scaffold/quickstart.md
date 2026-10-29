# Quickstart: 001-scaffold

验证空跑骨架。命令以仓库根为起点。不要在本功能里做登录、对话或联盟调用。

## 1. 基础设施

```bash
cp deploy/env/local.env.example deploy/env/local.env
docker compose -f deploy/docker-compose.local.yml --env-file deploy/env/local.env up -d postgres redis nacos langfuse-web langfuse-db
```

期望：

- `postgres` 中能查到扩展 `vector`，且没有业务表
- `redis` 可连接
- `curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8848/nacos/v1/console/health/liveness` 为 `200`
- `curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:3100/` 为 2xx 或 3xx
- 编排文件中没有 MySQL

细节见 [contracts/health.md](contracts/health.md) 与 [data-model.md](data-model.md)。

## 2. Java

```bash
cd services/java
mvn -pl shop-api-app,shop-api-admin -am test
mvn -pl shop-api-app -am spring-boot:run -Dspring-boot.run.profiles=local
```

另开终端启动 Admin，端口 `8082`。期望：

- 测试通过，其中包含信封字段测试
- `curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8081/api/app/actuator/health` 为 `200`
- Admin 的 `http://127.0.0.1:8082/api/admin/actuator/health` 为 `200`
- 两个模块的依赖里没有彼此

## 3. Agent

```bash
cd services/agent
uv sync
uv run uvicorn app.main:app --port 8090 --host 127.0.0.1
```

期望：`curl -s http://127.0.0.1:8090/health` 返回 `{"status":"ok"}`。

## 4. Web 空壳

分别在 `apps/admin-web`、`apps/h5`、`apps/www`、`apps/land` 执行 `npm ci` 与 `npm run dev`。页面为空，没有登录、对话或运营流程。端口为 5173、5177、5175、5176。

## 5. 原生工程

```bash
cd apps/android && ./gradlew :app:assembleDebug
```

iOS 在 macOS 上用 Xcode 16.2 打开 `apps/ios` 工程。没有该环境时，只检查工程存在且 Bundle ID 为 `com.omino.shopai`，并在交付说明里写明未打开。
