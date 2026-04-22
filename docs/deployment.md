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
- Docker 内置网关只绑定宿主机本机：`127.0.0.1:18080`
- 服务器对外建议使用已有宿主机 Nginx 的 80/443，反向代理到 `127.0.0.1:18080`
- 后端服务端口仅在 Docker 网络内互通，不映射宿主机端口，避免与服务器已有服务冲突。

## 2.1 宿主机 Nginx 接入示例

如果服务器已有 Nginx，对外只开放 80/443，推荐把域名反代到 Docker 内置网关：

```nginx
server {
    listen 80;
    server_name your.domain.com;

    location / {
        proxy_pass http://127.0.0.1:18080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

生产域名部署时同步设置：

```bash
cp .env.example .env
sed -i 's#http://localhost:18080#https://your.domain.com#' .env
docker compose up -d --build
```

`EDC_PUBLIC_BASE_URL` 会影响 Data Plane 返回的 EDR endpoint，必须是浏览器或消费方能够访问的外部地址。

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

- Docker 内置入口（前端+网关）：`127.0.0.1:18080`
- 推荐对外入口：宿主机 Nginx `80/443`
- Zookeeper（宿主机映射）：`12181`
- 控制面/数据面/身份/发行/联邦/运营：仅 Docker 网络内端口，不映射宿主机
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
