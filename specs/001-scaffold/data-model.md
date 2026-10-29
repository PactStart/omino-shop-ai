# Data Model: 001-scaffold

本功能没有业务实体，不创建 `docs/data-model.md` 中的业务表。

## 运行时数据

### 商品主库

- 服务名：`postgres`
- 镜像：`pgvector/pgvector:pg16`
- 库名：来自环境变量，默认 `omino_shop_ai`
- 唯一结构变化：扩展 `vector`
- 验证：扩展存在；不存在用户、订单、商品等业务表

### 缓存

- 服务名：`redis`
- 镜像：`redis:7.4-alpine`
- 本功能不写入业务键

### 配置中心

- 服务名：`nacos`
- 镜像：`nacos/nacos-server:v2.4.3`
- 模式：单机，嵌入式存储
- 鉴权：关闭
- 本功能不写入 DataId，Java 不读取

### 观测库

- 服务名：`langfuse-db`
- 镜像：`postgres:16-alpine`
- 只供 `langfuse-web` 使用
- 与商品主库没有外键或共享表空间
- 表由 Langfuse v2 容器自行创建，本仓库不写这些迁移

## 明确不做

- 不建管理员账号表
- 不把 Langfuse 的表放进商品主库
- 不使用 MySQL
