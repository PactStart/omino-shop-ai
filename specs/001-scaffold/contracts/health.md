# Contract: 健康检查

本功能的三个进程健康检查，以及两个基础设施的 HTTP 探针。业务信封不套在这些响应上。

## APP

- Method: `GET`
- URL: `http://127.0.0.1:8181/api/app/actuator/health`
- Auth: 无
- Success: HTTP 200，正文表明进程为 UP
- Failure: 未启动或端口不是 8181，视为未通过

## Admin

- Method: `GET`
- URL: `http://127.0.0.1:8082/api/admin/actuator/health`
- Auth: 无
- Success: HTTP 200，正文表明进程为 UP
- 两个进程不得监听同一端口

## Agent

- Method: `GET`
- URL: `http://127.0.0.1:8190/health`
- Bind: 只允许 `127.0.0.1`
- Auth: 无
- Success: HTTP 200，正文为 `{"status":"ok"}`
- 不提供对话、工具或对公网监听

## Nacos

- Method: `GET`
- URL: `http://127.0.0.1:8848/nacos/v1/console/health/liveness`
- Auth: 无。本地关闭鉴权
- Success: HTTP 200
- 不要求控制台登录

## Langfuse

- Method: `GET`
- URL: `http://127.0.0.1:3100/`
- Auth: 无。不要求登录成功
- Success: HTTP 2xx 或 3xx。连接失败不算通过
- 不要求 Java 或 Agent 已连接
