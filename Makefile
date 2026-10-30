# 本地任务。在仓库根目录执行。
# make up 只读 deploy/env/local.infra.env。
# make app / admin / agent 先读 infra，再读自己那份。
# yml 默认端口仍是 5432 / 6381；本机冲突只改 local.infra.env。
# 前端不读 deploy/env，各自目录的 .env 由 .env.example 复制。

SHELL := /bin/bash
.DEFAULT_GOAL := help

ROOT := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
ENV_DIR := $(ROOT)/deploy/env
INFRA_ENV := $(ENV_DIR)/local.infra.env
APP_ENV := $(ENV_DIR)/local.app.env
ADMIN_ENV := $(ENV_DIR)/local.admin.env
AGENT_ENV := $(ENV_DIR)/local.agent.env
COMPOSE := docker compose -f $(ROOT)/deploy/docker-compose.local.yml --env-file $(INFRA_ENV)
MVN ?= mvn
INFRA := postgres redis nacos langfuse-web langfuse-db

.PHONY: help env require-infra require-app require-admin require-agent up down logs app admin agent test java-test agent-test admin-web www land h5 android ios

help:
	@printf '%s\n' \
		'make env         缺哪份 local.*.env 就从 example 复制' \
		'make up          启动 Postgres、Redis、Nacos、Langfuse' \
		'make down        停掉上面这些容器' \
		'make logs        看基础设施日志' \
		'make app         APP API，默认 :8181，先读 infra 再读 app env' \
		'make admin       Admin API，默认 :8082，先读 infra 再读 admin env' \
		'make agent       Agent，默认 :8190，只监听本机，先读 infra 再读 agent env' \
		'make test        Java 与 Agent 测试' \
		'make java-test   只跑 Java 测试' \
		'make agent-test  只跑 Agent 测试' \
		'make admin-web   管理后台空壳，:5183，读 apps/admin-web/.env' \
		'make h5          消费者 H5 空壳，:5177，读 apps/h5/.env' \
		'make www         官网空壳，:5175，读 apps/www/.env' \
		'make land        邀请落地空壳，:5176，读 apps/land/.env' \
		'make android     打 Android debug 包' \
		'make ios         用 Xcode 打开 iOS 工程'

define copy_if_missing
	@if [ ! -f "$(2)" ]; then cp "$(1)" "$(2)"; echo "已复制 $(2)"; else echo "已有 $(2)"; fi
endef

env:
	$(call copy_if_missing,$(ENV_DIR)/local.infra.env.example,$(INFRA_ENV))
	$(call copy_if_missing,$(ENV_DIR)/local.app.env.example,$(APP_ENV))
	$(call copy_if_missing,$(ENV_DIR)/local.admin.env.example,$(ADMIN_ENV))
	$(call copy_if_missing,$(ENV_DIR)/local.agent.env.example,$(AGENT_ENV))

require-infra:
	@test -f "$(INFRA_ENV)" || { echo "缺少 deploy/env/local.infra.env，先执行 make env"; exit 1; }

require-app: require-infra
	@test -f "$(APP_ENV)" || { echo "缺少 deploy/env/local.app.env，先执行 make env"; exit 1; }

require-admin: require-infra
	@test -f "$(ADMIN_ENV)" || { echo "缺少 deploy/env/local.admin.env，先执行 make env"; exit 1; }

require-agent: require-infra
	@test -f "$(AGENT_ENV)" || { echo "缺少 deploy/env/local.agent.env，先执行 make env"; exit 1; }

up: require-infra
	$(COMPOSE) up -d $(INFRA)

down: require-infra
	$(COMPOSE) down

logs: require-infra
	$(COMPOSE) logs -f $(INFRA)

app: require-app
	set -a && . "$(INFRA_ENV)" && . "$(APP_ENV)" && set +a && \
	cd "$(ROOT)/services/java" && \
	$(MVN) -pl shop-api-app -am spring-boot:run -Dspring-boot.run.profiles=local

admin: require-admin
	set -a && . "$(INFRA_ENV)" && . "$(ADMIN_ENV)" && set +a && \
	cd "$(ROOT)/services/java" && \
	$(MVN) -pl shop-api-admin -am spring-boot:run -Dspring-boot.run.profiles=local

agent: require-agent
	set -a && . "$(INFRA_ENV)" && . "$(AGENT_ENV)" && set +a && \
	cd "$(ROOT)/services/agent" && uv sync && \
	uv run uvicorn app.main:app --reload --port 8190 --host 127.0.0.1

java-test:
	cd "$(ROOT)/services/java" && \
	$(MVN) -pl shop-api-app,shop-api-admin -am test

agent-test:
	cd "$(ROOT)/services/agent" && uv run pytest

test: java-test agent-test

define ensure_dotenv
	@test -f "$(1)/.env" || cp "$(1)/.env.example" "$(1)/.env"
endef

admin-web:
	$(call ensure_dotenv,$(ROOT)/apps/admin-web)
	cd "$(ROOT)/apps/admin-web" && npm ci && npm run dev

www:
	$(call ensure_dotenv,$(ROOT)/apps/www)
	cd "$(ROOT)/apps/www" && npm ci && npm run dev

land:
	$(call ensure_dotenv,$(ROOT)/apps/land)
	cd "$(ROOT)/apps/land" && npm ci && npm run dev

h5:
	$(call ensure_dotenv,$(ROOT)/apps/h5)
	cd "$(ROOT)/apps/h5" && npm ci && npm run dev

android:
	$(call ensure_dotenv,$(ROOT)/apps/android)
	cd "$(ROOT)/apps/android" && ./gradlew :app:assembleDebug

ios:
	$(call ensure_dotenv,$(ROOT)/apps/ios)
	open "$(ROOT)/apps/ios/App.xcodeproj"
