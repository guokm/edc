# EDC 商业数据空间（Maven + JDK21 + Spring Boot 3.4.7 + MyBatis Plus + Vue3）

本项目当前主路径是 **Maven 多模块**，核心流程已经落在现有模块中（不再依赖上游源码做运行时 Demo）。

## 当前核心能力

- 后端：JDK 21 + Spring Boot 3.4.7 + MyBatis Plus（Maven）
- 核心流程落地模块：
  - `edc-control-plane`：资产目录、合同协商、传输编排、数据面注册、场景编排
  - `edc-data-plane`：传输状态、EDR 生成、数据拉取、自动注册到控制面
- 持久化：MySQL（真实表，不用内存 Demo）
- 消息总线：Kafka（传输事件发布）
- 双数据节点：`data-plane-1` + `data-plane-2`
- 身份/发行/联邦/运营四模块已落库
- 计费模式：按调用次数校验（运营服务 `usage/check`）

## 项目结构

- `edc-common`：共享模型
- `edc-control-plane`：控制面核心流程
- `edc-data-plane`：数据面核心流程
- `edc-identity-hub` / `edc-issuer-service` / `edc-federated-catalog` / `edc-operator-services`：配套模块
- `frontend`：Vue3 控制台
- `scripts/verify.sh`：一键启动+全链路验证
- `upstream/connector`：上游 EDC 源码（仅作功能对照和迁移指引）

## 前端页面（已补充）

- 供应方入口：发布资产、查看协商、追踪传输、双平面演示
- 消费方入口：目录发现、签发并签约（Issuer->Identity->DCP->协商）、发起传输、EDR 拉取
- 运营方入口：治理接口巡检、按次计费校验、节点与健康监控
- 每个角色都提供 `角色演示文档` 页面，支持现场讲解
- 一键全流程演示提供确认弹框，先展示步骤说明再执行
- 一页速览文档：`docs/one-page-demo.md`

## 一键验证

```bash
./scripts/verify.sh
```

脚本会自动：
1. Docker 启动全栈（含双 Data Plane）
2. 调用控制面场景接口生成测试数据并跑完整流程
3. 校验 EDR 拉取
4. 校验 MySQL 持久化结果（含双节点分流）

## 本地构建

```bash
mvn -q -DskipTests package
```

## MySQL 建表脚本

- 建表 SQL 文件：`docs/sql/mysql-schema.sql`
- 手工初始化命令：

```bash
docker compose exec -T mysql env MYSQL_PWD=edc mysql -u edc -D edc < docs/sql/mysql-schema.sql
```

- 规则：任何表结构调整必须同步修改 `docs/sql/mysql-schema.sql` 与对应 SchemaService。

## 关键端口（单入口）

- 前端+网关 Nginx：18080（对外）
- Zookeeper：12181（宿主机本地）
- 控制面：8181（仅 `127.0.0.1`）
- 数据面1：8182（仅 `127.0.0.1`）
- 数据面2：8187（仅 `127.0.0.1`）
- 身份中心：8183（仅 `127.0.0.1`）
- 发行服务：8184（仅 `127.0.0.1`）
- 联邦目录：8185（仅 `127.0.0.1`）
- 运营服务：8186（仅 `127.0.0.1`）
- MySQL：13306（仅 `127.0.0.1`）
- Kafka：9092 / 29092（仅 `127.0.0.1`）
