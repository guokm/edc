# 部署指南（Maven/SpringBoot 运行时）

## 1. 编排拓扑

- `control-plane`
- `data-plane-1`
- `data-plane-2`
- `identity-hub`
- `issuer-service`
- `federated-catalog`
- `operator-services`
- `frontend`
- `mysql`
- `kafka`
- `zookeeper`

## 2. 单入口网关说明

- `frontend` 容器内置 Nginx（`docker/nginx/default.conf`），承担两件事：
  - 静态页面托管（Vue3）
  - 统一反向代理后端接口（`/cp` `/ih` `/is` `/fc` `/op` `/dp1` `/dp2`）
- 浏览器只访问一个入口：`http://<服务器IP>:18080`
- 后端端口均绑定 `127.0.0.1`，不对外暴露。

## 3. 启动命令

```bash
docker compose up -d --build
```

## 4. 关键环境变量（Data Plane）

- `EDC_DATAPLANE_ID`
- `EDC_DATAPLANE_PUBLIC_API_BASE_URL`（建议指向网关地址）
  - `dp-1`: `${EDC_PUBLIC_BASE_URL:-http://localhost:18080}/dp1`
  - `dp-2`: `${EDC_PUBLIC_BASE_URL:-http://localhost:18080}/dp2`
- `EDC_DATAPLANE_CONTROL_API_BASE_URL`
- `EDC_CONTROL_PLANE_BASE_URL`

## 5. 关键环境变量（Identity/Issuer/Federated）

- `EDC_OPERATOR_BASE_URL`（按次计费校验接口地址，容器内默认 `http://operator-services:8186`）

## 6. 验收命令

```bash
./scripts/verify.sh
```

## 7. 数据库初始化

```bash
docker compose exec -T mysql env MYSQL_PWD=edc mysql -u edc -D edc < docs/sql/mysql-schema.sql
```

- 表结构单一基准文件：`docs/sql/mysql-schema.sql`
- 调整表结构时必须同步更新该文件和对应 SchemaService。

## 8. 关键端口（当前编排）

- 对外入口（前端+网关）：`18080`
- Zookeeper（宿主机映射）：`12181`
- 控制面：`127.0.0.1:8181`
- 数据面1：`127.0.0.1:8182`
- 数据面2：`127.0.0.1:8187`
- 身份中心：`127.0.0.1:8183`
- 发行服务：`127.0.0.1:8184`
- 联邦目录：`127.0.0.1:8185`
- 运营服务：`127.0.0.1:8186`
- MySQL：`127.0.0.1:13306`
- Kafka：`127.0.0.1:9092` / `127.0.0.1:29092`

## 9. 网关路径映射

- `/cp` -> `control-plane:8181`
- `/ih` -> `identity-hub:8183`
- `/is` -> `issuer-service:8184`
- `/fc` -> `federated-catalog:8185`
- `/op` -> `operator-services:8186`
- `/dp1` -> `data-plane-1:8182`
- `/dp2` -> `data-plane-2:8182`
