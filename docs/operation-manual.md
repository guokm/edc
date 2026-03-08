# 操作手册（Maven 核心流程版）

## 1. 目标

本手册用于本地 Docker 环境验证：

- Maven 模块中的控制面/数据面核心流程
- 双 Data Plane 节点注册与分流
- MySQL 持久化与 EDR 拉取

## 2. 一键执行

```bash
./scripts/verify.sh
```

## 3. 手工调试步骤

### 3.1 构建

```bash
mvn -q -DskipTests package
```

### 3.2 启动

```bash
docker compose up -d --build
```

### 3.3 健康检查

```bash
for p in 8181 8182 8183 8184 8185 8186 8187; do
  curl -s -o /tmp/h_$p.json -w "port $p => %{http_code}\n" http://localhost:$p/actuator/health
done
```

前端统一巡检接口（推荐）：

```bash
curl -s http://localhost:8181/api/monitor/health
curl -s http://localhost:8181/api/monitor/governance
curl -s http://localhost:8181/api/monitor/dataplanes
```

### 3.4 运行核心场景

```bash
curl -s -H 'Content-Type: application/json' -X POST \
  http://localhost:8181/api/scenario/run \
  -d '{"assetCount":3,"consumerId":"participant-b"}'
```

### 3.5 查看 Data Plane 注册

```bash
curl -s http://localhost:8181/api/dataplanes
```

### 3.6 按传输 ID 查询 EDR

```bash
curl -s http://localhost:8181/api/transfers/<transferId>/edr
```

### 3.7 数据拉取

```bash
curl -s -H "Authorization: <authToken>" "<endpoint>?message=hello"
```

### 3.8 按次计费校验（运营服务）

```bash
curl -s -H 'Content-Type: application/json' -X POST \
  http://localhost:8186/api/billing/usage/check \
  -d '{"participantId":"participant-a","serviceCode":"ISSUER_CREDENTIAL_ISSUE"}'
```

## 4. MySQL 建表脚本

- 建表文件：`docs/sql/mysql-schema.sql`
- 初始化命令：

```bash
docker compose exec -T mysql env MYSQL_PWD=edc mysql -u edc -D edc < docs/sql/mysql-schema.sql
```

- 变更规则：若修改任意表结构，必须同步更新：
  - `docs/sql/mysql-schema.sql`
  - `edc-control-plane/src/main/java/com/example/edc/controlplane/service/ControlPlaneSchemaService.java`
  - `edc-data-plane/src/main/java/com/example/edc/dataplane/service/DataPlaneSchemaService.java`

## 5. 持久化检查（MySQL）

```bash
docker compose exec -T mysql env MYSQL_PWD=edc mysql -u edc -D edc -e "select count(*) from edc_cp_asset;"
docker compose exec -T mysql env MYSQL_PWD=edc mysql -u edc -D edc -e "select count(*) from edc_cp_transfer_process;"
docker compose exec -T mysql env MYSQL_PWD=edc mysql -u edc -D edc -e "select count(*) from edc_cp_data_plane_instance;"
docker compose exec -T mysql env MYSQL_PWD=edc mysql -u edc -D edc -e "select count(distinct data_plane_id) from edc_cp_transfer_process;"
docker compose exec -T mysql env MYSQL_PWD=edc mysql -u edc -D edc -e "select count(distinct data_plane_id) from edc_dp_transfer_process;"
```

## 6. 停止环境

```bash
docker compose down
```

## 7. 前端按角色操作说明

访问：`http://localhost:8080`

页面列表统一规则：时间列 + 时间倒序 + 默认每页 10 条。

### 7.1 供应方（华东车联）

- 入口：`供应方入口`
- 页面：`供应方工作台`
- 操作顺序：
  1. 在“供应方资产发布”先刷新/创建策略（策略来源：运营服务 `GET/POST /api/policies`）
  2. 选择策略ID并发布资产
  3. 查看发布轨迹：创建资产 -> 写入 Offer -> 刷新目录
  4. 在“认证人与目录”确认资产已可见
  5. 在“协商列表/协商合同/数据传输状态”观察消费侧流转结果

### 7.2 消费方（保险风控中心）

- 入口：`消费方入口`
- 页面：`消费方工作台`
- 前置条件：
  - 运营方侧 `participant-b` 存在当前有效 `ACTIVE` 会员（否则协商/传输返回 `403`）
  - 已完成“签发并签约”前四步中的身份材料准备（Issuer 签发 + Identity 凭证写入 + DCP 展示/校验），否则协商/传输返回 `403`
- 操作顺序：
  1. 刷新目录并选择目标资产
  2. 执行“签发并签约”流程（Issuer -> Identity -> DCP -> 协商）
  3. 选择协议发起传输
  4. 通过 EDR 拉取数据
  5. 查看页面内“协商治理校验 / 传输治理校验”提示，确认会员记录与计费剩余额度

- 成功/失败判定：
  - 成功：协商列表状态为 `FINALIZED`，且存在 `agreementId`
  - 失败：offer 不存在/不匹配时返回 `HTTP 409`，协商列表状态为 `REJECTED_OFFER_NOT_FOUND` 或 `REJECTED_OFFER_ASSET_MISMATCH`

### 7.3 运营方（数据空间运营平台）

- 入口：`运营方入口`
- 页面：`治理模块接口`、`节点与健康`
- 操作顺序：
  1. 一键巡检治理接口
  2. 执行额度校验（按次计费）
  3. 查看双数据平面与健康摘要

完整讲解脚本见：`docs/one-page-demo.md`

## 8. 运营方从 0 到 1 全流程演示（详细示例）

场景：`participant-b（保险风控中心）` 购买 `participant-a（华东车联）` 的车辆风控资产。  
主线：**准入 -> 身份 -> 合同 -> 交付 -> 计费**。

### 8.1 目标

- 让运营人员看到：会员治理、身份凭证、合同协商、数据交付、计费校验是一条连续链路。
- 每一步都有可观测状态和可追踪 ID，可用于对外宣讲与内部验收。

### 8.2 演示步骤（按页面操作）

1. `运营方入口 -> 治理模块接口`，先点击 `一键巡检`。  
   预期：Identity/Issuer/Federated/Operator 检查项返回 200。
2. 在同页点击 `创建会员`，再点 `会员列表`。  
   预期：出现 `mem-xxx`，`participantId=participant-b`，状态 `ACTIVE`。
3. 在同页点击 `执行签发`（Issuer）。  
   预期：返回 `issuanceId`，状态 `ISSUED`。
4. 在同页点击 `写入凭证`（Identity）。  
   预期：返回 `credentialId`，凭证写入成功。
5. 在同页点击 `创建DCP展示` 和 `校验DCP展示`。  
   预期：返回 `presentationId`，并看到 `verified=true`。
6. 切到 `供应方入口 -> 供应方工作台`，刷新/创建策略后发布资产。  
   预期：发布轨迹出现 `创建资产 -> 写入 Offer -> 刷新目录`。
7. 切到 `消费方入口 -> 消费方工作台`，刷新目录并选择资产与 Offer，执行 `签发并签约`。  
   预期：协商状态 `FINALIZED`，生成 `agreementId`。
8. 在消费方页面选择协议，点击 `发起传输`，再 `拉取数据`。  
   预期：出现 `transferProcessId`，链路状态推进并拿到 payload。
9. 回到 `运营方入口`，点击 `额度校验`，再到 `节点与健康` 刷新。  
   预期：看到 `usedCount/remainingCount` 变化，且双 Data Plane 健康可用。

### 8.3 现场讲解的“证据链 ID”

建议按以下顺序串联讲解，页面都能看到对应值：

`participantId -> membershipId -> issuanceId -> credentialId -> presentationId -> negotiationId -> agreementId -> transferProcessId`

### 8.4 成功与失败判定

- 协商成功：`FINALIZED` 且存在 `agreementId`。
- 协商失败：`HTTP 409`，状态为 `REJECTED_OFFER_NOT_FOUND` 或 `REJECTED_OFFER_ASSET_MISMATCH`。
- 计费成功：`allowed=true`，并看到 `remainingCount` 递减。

### 8.5 agreementId 后的“编排门禁预演”演示

在消费方工作台「数据传输状态与链路追踪」区域，先选中 `agreementId`，再执行“演示编排门禁（Agreement）”：

1. 填写权限头：  
   - `X-Participant-Id=operator`  
   - `X-Operator-Token=operator-demo-key`（默认示例值，可在环境变量 `EDC_ORCHESTRATION_DEMO_TOKEN` 改）
2. 调用接口：  
   `GET /api/transfers/orchestration/preview?agreementId=<agreementId>&dataPlaneId=<可选>`
3. 页面将按步骤展示：
   - ACTIVE 会员校验
   - 签发资格校验
   - 计费只读校验（不扣次）
   - Data Plane 选择（指定或轮询）
4. 若 `readyToTransfer=true`，可继续调用真实传输接口：  
   `POST /api/transfers`（此接口会执行计费扣次）。

## 9. 关键关系澄清（会员 / 协商 / 计费）

### 9.1 创建会员 与 消费方协商签约的关系

- 当前版本中，会员数据（`edc_op_membership`）既用于治理档案，也作为协商/传输准入门禁之一。  
- 协商签约主链路在控制面：`asset + offer -> negotiation -> agreement`。  
- 协商接口会**强制**校验“consumerId 是否存在当前有效 ACTIVE 会员”：  
  - 不满足时返回 `HTTP 403`（拒绝协商）
- 传输发起同样会按协议中的 `consumerId` 执行 ACTIVE 会员校验：  
  - 不满足时返回 `HTTP 403`（拒绝传输）
- 协商与传输还会校验“签发资格”（DCP 展示已校验且凭证在有效期）：  
  - 不满足时返回 `HTTP 403`（拒绝协商/传输）

对应字段参考：

- 会员：`edc_op_membership.participant_id`
- 协商：`edc_cp_contract_negotiation.consumer_id`
- 协议：`edc_cp_contract_agreement.consumer_id`

### 9.2 计费按什么计算

当前是“**按调用次数**”模型，计费键为：

- `participantId + serviceCode + periodMonth(yyyyMM)`

计算逻辑（`POST /api/billing/usage/check`）：

1. 按 `participantId + serviceCode` 找计费方案 `edc_op_billing_plan`（默认自动创建：`quota_limit=200`，`unit_price=0.0500`）。  
2. 读取本月计数 `edc_op_usage_counter`。  
3. 若 `used_count < quota_limit`，则 `allowed=true` 且 `used_count + 1`；否则 `allowed=false` 不再增加。  
4. 返回：
   - `usedCount`：本次后已用次数
   - `remainingCount = quotaLimit - usedCount`
   - `estimatedAmount = unitPrice * usedCount`

### 9.3 哪些操作会触发计费扣次

- Issuer：`POST /api/issuer/credentials` -> `ISSUER_CREDENTIAL_ISSUE`
- Identity：
  - `POST /api/identity/credentials` -> `IDENTITY_CREDENTIAL_WRITE`
  - `POST /api/identity/presentations` -> `IDENTITY_PRESENTATION_CREATE`
  - `POST /api/dcp/presentations` -> `DCP_PRESENTATION_CREATE`
  - `POST /api/dcp/verification` -> `DCP_PRESENTATION_VERIFY`
- Federated Catalog：
  - `GET /api/federated/catalog` -> `FEDERATED_CATALOG_QUERY`
  - `POST /api/federated/crawl` -> `FEDERATED_CRAWL_TRIGGER`
- Control Plane：
  - `POST /api/contracts/negotiations` -> `CONTRACT_NEGOTIATION_CREATE`
  - `POST /api/transfers` -> `TRANSFER_START`

注意：`治理模块接口`里的`一键巡检`包含“计费校验”检查项，会消耗一次对应服务编码额度。

### 9.4 协商与传输的失败码补充

- 会员不满足：`HTTP 403`（ACTIVE 会员校验失败）。  
- 签发资格不满足：`HTTP 403`（缺少已校验的 DCP 展示或凭证过期）。  
- 额度不足：`HTTP 402`（按次计费额度耗尽）。  
- Offer 不存在/不匹配：`HTTP 409`（并写入 `REJECTED_OFFER_*`）。
