# 本地任务。在仓库根目录执行。Java 进程会读 deploy/env/local.env。
# yml 默认端口仍是 5432 / 6379；本机冲突只改 local.env。

SHELL := /bin/bash
.DEFAULT_GOAL := help

ROOT := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
ENV_FILE := $(ROOT)/deploy/env/local.env
ENV_EXAMPLE := $(ROOT)/deploy/env/local.env.example
COMPOSE := docker compose -f $(ROOT)/deploy/docker-compose.local.yml --env-file $(ENV_FILE)
MVN ?= mvn
INFRA := postgres redis nacos langfuse-web langfuse-db

.PHONY: help env require-env up down logs app admin agent test java-test agent-test admin-web www land h5 android ios

help:
	@printf '%s\n' \
		'make env         没有 local.env 时从 example 复制' \
		'make up          启动 Postgres、Redis、Nacos、Langfuse' \
		'make down        停掉上面这些容器' \
		'make logs        看基础设施日志' \
		'make app         APP API，默认 :8081，会读 local.env' \
		'make admin       Admin API，默认 :8082，会读 local.env' \
		'make agent       Agent，默认 :8090，只监听本机' \
		'make test        Java 与 Agent 测试' \
		'make java-test   只跑 Java 测试' \
		'make agent-test  只跑 Agent 测试' \
		'make admin-web   管理后台空壳，:5173' \
		'make h5          消费者 H5 空壳，:5177' \
		'make www         官网空壳，:5175' \
		'make land        邀请落地空壳，:5176' \
		'make android     打 Android debug 包' \
		'make ios         用 Xcode 打开 iOS 工程'

env:
	@if [ ! -f "$(ENV_FILE)" ]; then \
		cp "$(ENV_EXAMPLE)" "$(ENV_FILE)"; \
		echo "已复制 deploy/env/local.env"; \
	else \
		echo "已有 deploy/env/local.env"; \
	fi

require-env:
	@test -f "$(ENV_FILE)" || { echo "缺少 deploy/env/local.env，先执行 make env"; exit 1; }

up: require-env
	$(COMPOSE) up -d $(INFRA)

down: require-env
	$(COMPOSE) down

logs: require-env
	$(COMPOSE) logs -f $(INFRA)

app: require-env
	set -a && . "$(ENV_FILE)" && set +a && \
	cd "$(ROOT)/services/java" && \
	$(MVN) -pl shop-api-app -am spring-boot:run -Dspring-boot.run.profiles=local

admin: require-env
	set -a && . "$(ENV_FILE)" && set +a && \
	cd "$(ROOT)/services/java" && \
	$(MVN) -pl shop-api-admin -am spring-boot:run -Dspring-boot.run.profiles=local

agent:
	cd "$(ROOT)/services/agent" && uv sync && \
	uv run uvicorn app.main:app --reload --port 8090 --host 127.0.0.1

java-test:
	cd "$(ROOT)/services/java" && \
	$(MVN) -pl shop-api-app,shop-api-admin -am test

agent-test:
	cd "$(ROOT)/services/agent" && uv run pytest

test: java-test agent-test

admin-web:
	cd "$(ROOT)/apps/admin-web" && npm ci && npm run dev

www:
	cd "$(ROOT)/apps/www" && npm ci && npm run dev

land:
	cd "$(ROOT)/apps/land" && npm ci && npm run dev

h5:
	cd "$(ROOT)/apps/h5" && npm ci && npm run dev

android:
	cd "$(ROOT)/apps/android" && ./gradlew :app:assembleDebug

ios:
	open "$(ROOT)/apps/ios/App.xcodeproj"
