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

.PHONY: help env require-infra require-app require-admin require-agent up down logs \
	app admin agent test java-test agent-test accept admin-web www land h5 android ios \
	stop-app stop-admin stop-agent stop-admin-web stop-www stop-land stop-h5 \
	restart-app restart-admin restart-agent restart-admin-web restart-www restart-land restart-h5

help:
	@printf '%s\n' \
		'make env         缺哪份 local.*.env 就从 example 复制' \
		'make up          启动 Postgres、Redis、Nacos、Langfuse' \
		'make down        停掉上面这些容器' \
		'make logs        看基础设施日志' \
		'make app         APP API，默认 :8181，先读 infra 再读 app env' \
		'make admin       Admin API，默认 :8182，先读 infra 再读 admin env' \
		'make agent       Agent，默认 :8190，只监听本机，先读 infra 再读 agent env' \
		'make test        Java 与 Agent 测试，不出网' \
		'make java-test   只跑 Java 测试' \
		'make agent-test  只跑 Agent 测试' \
		'make accept      四个平台的授权状态、搜品、详情、转链。PLATFORM=jd 只跑一个，不进 make test' \
		'make admin-web   管理后台空壳，:5183，读 apps/admin-web/.env' \
		'make h5          消费者 H5 空壳，:5177，读 apps/h5/.env' \
		'make www         官网空壳，:5175，读 apps/www/.env' \
		'make land        邀请落地空壳，:5176，读 apps/land/.env' \
		'make android     打 Android debug 包' \
		'make ios         用 Xcode 打开 iOS 工程' \
		'make stop-app         停掉 APP API（:8181）。admin :8182、agent :8190 同样有 stop-*' \
		'make stop-admin-web   停掉管理后台（:5183）。www :5175、land :5176、h5 :5177 同样有 stop-*' \
		'make restart-app      停掉后再启动，仍占当前终端。其余应用把 app 换成自己的名字'

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

accept: require-app
	set -a && . "$(INFRA_ENV)" && . "$(APP_ENV)" && set +a && \
	bash "$(ROOT)/scripts/union-accept.sh" $(PLATFORM)

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

# 按默认端口停掉这次启动的进程。命令行对不上该应用时不杀。
stop-app:
	bash "$(ROOT)/scripts/dev-stop.sh" 8181 'AppApiApplication|shop-api-app' 'APP API'

stop-admin:
	bash "$(ROOT)/scripts/dev-stop.sh" 8182 'AdminApiApplication|shop-api-admin' 'Admin API'

stop-agent:
	bash "$(ROOT)/scripts/dev-stop.sh" 8190 'app\.main:app' 'Agent'

stop-admin-web:
	bash "$(ROOT)/scripts/dev-stop.sh" 5183 'apps/admin-web' '管理后台'

stop-www:
	bash "$(ROOT)/scripts/dev-stop.sh" 5175 'apps/www' '官网'

stop-land:
	bash "$(ROOT)/scripts/dev-stop.sh" 5176 'apps/land' '落地页'

stop-h5:
	bash "$(ROOT)/scripts/dev-stop.sh" 5177 'apps/h5' '消费者 H5'

# 先停再起。recipe 里再调 make，避免和 stop 并行。
restart-app: stop-app
	$(MAKE) app

restart-admin: stop-admin
	$(MAKE) admin

restart-agent: stop-agent
	$(MAKE) agent

restart-admin-web: stop-admin-web
	$(MAKE) admin-web

restart-www: stop-www
	$(MAKE) www

restart-land: stop-land
	$(MAKE) land

restart-h5: stop-h5
	$(MAKE) h5
