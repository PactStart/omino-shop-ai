# omino-shop-ai

本阶段是空壳：基础设施能起，两个 Java 进程报健康，Agent 本机健康，四个前端能打开空页。没有登录、对话、转链。

首次把 `deploy/env/local.env.example` 复制为 `deploy/env/local.env`。

```bash
docker compose -f deploy/docker-compose.local.yml --env-file deploy/env/local.env up -d postgres redis nacos langfuse-web langfuse-db

cd services/java
mvn -pl shop-api-app -am spring-boot:run -Dspring-boot.run.profiles=local
mvn -pl shop-api-admin -am spring-boot:run -Dspring-boot.run.profiles=local

cd services/agent
uv sync
uv run uvicorn app.main:app --reload --port 8090 --host 127.0.0.1

cd apps/admin-web && npm install && npm run dev
cd apps/www && npm install && npm run dev
cd apps/land && npm install && npm run dev
cd apps/h5 && npm install && npm run dev

cd apps/android && ./gradlew :app:assembleDebug
open apps/ios/App.xcodeproj
```

健康检查：`GET /api/app/actuator/health`（8081）、`GET /api/admin/actuator/health`（8082）、`GET http://127.0.0.1:8090/health`。
