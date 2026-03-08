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

## 2. 启动命令

```bash
docker compose up -d --build
```

## 3. 关键环境变量（Data Plane）

- `EDC_DATAPLANE_ID`
- `EDC_DATAPLANE_PUBLIC_API_BASE_URL`
- `EDC_DATAPLANE_CONTROL_API_BASE_URL`
- `EDC_CONTROL_PLANE_BASE_URL`

## 4. 关键环境变量（Identity/Issuer/Federated）

- `EDC_OPERATOR_BASE_URL`（按次计费校验接口地址，默认 `http://localhost:8186`）

## 5. 验收命令

```bash
./scripts/verify.sh
```

## 6. 数据库初始化

```bash
docker compose exec -T mysql env MYSQL_PWD=edc mysql -u edc -D edc < docs/sql/mysql-schema.sql
```

- 表结构单一基准文件：`docs/sql/mysql-schema.sql`
- 调整表结构时必须同步更新该文件和对应 SchemaService。

## 7. 关键端口

- 控制面：8181
- 数据面1：8182
- 数据面2：8187
- 身份中心：8183
- 发行服务：8184
- 联邦目录：8185
- 运营服务：8186
- 前端：8080
- MySQL（宿主机映射）：13306
