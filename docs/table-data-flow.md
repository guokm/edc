# 表数据流转说明（控制面 / 数据面）

## 1. 目的

本文档说明 EDC 商业数据空间在 MySQL 中的核心表关系、数据流转路径和关键关联键，便于开发、排障和新会话快速接手。

## 2. 核心关联键（扭住全链路）

- `asset_id`：资产主线
- `offer_id`：资产套餐（Offer）主线
- `negotiation_id` / `agreement_id`：协商与协议主线
- `transfer_process_id`：传输主线（跨控制面与数据面）
- `data_plane_id`：数据面分流主线

## 3. 表关系

### 3.1 控制面（CP）

- `edc_cp_asset.id` -> `edc_cp_contract_offer.asset_id`
- `edc_cp_asset.id` -> `edc_cp_contract_negotiation.asset_id`
- `edc_cp_contract_offer.id` -> `edc_cp_contract_negotiation.offer_id`
- `edc_cp_contract_negotiation.id` -> `edc_cp_contract_agreement.negotiation_id`
- `edc_cp_contract_offer.id` -> `edc_cp_contract_agreement.offer_id`
- `edc_cp_contract_agreement.id` -> `edc_cp_transfer_process.agreement_id`
- `edc_cp_data_plane_instance.id` -> `edc_cp_transfer_process.data_plane_id`

### 3.2 数据面（DP）

- `edc_cp_transfer_process.id` == `edc_dp_transfer_process.id`
- `edc_dp_transfer_process.id` -> `edc_dp_edr.transfer_process_id`

### 3.3 身份 / 发行 / 联邦 / 运营

- 身份中心：
  - `edc_ih_credential.id` -> `edc_ih_presentation.credential_id`
- 发行服务：
  - `edc_is_issuance.issuance_id`（签发单主键）
  - Identity 凭证 `claims.issuanceId` -> `edc_is_issuance.issuance_id`（签发资格校验）
- 联邦目录：
  - `edc_cp_contract_offer.id` -> `edc_fc_catalog_item.offer_id`（控制面 Offer 镜像）
  - `edc_cp_asset.id` -> `edc_fc_catalog_item.asset_id`（资产镜像）
  - `edc_fc_crawl_job.id`（爬取任务）与 `edc_fc_catalog_item.*`（目录结果）按任务结果关联
- 运营会员：
  - `edc_op_membership.participant_id` 与业务侧 `consumer_id/participantId` 关联，并在协商/传输前执行 ACTIVE 校验
- 运营计费：
  - `edc_op_billing_plan(participant_id, service_code)` 定义可用次数上限
  - `edc_op_usage_counter(participant_id, service_code, period_month)` 记录每月已用次数
  - 业务模块调用 `/api/billing/usage/check` 先校验后扣减（协商按 `offerId`，传输按 `assetId`）
  - `edc_op_billing_record.agreement_id` 与协议关联（可反查 `asset_id/offer_id`）
- 运营审计：
  - 控制面关键动作写入 `edc_op_audit_event`，用于运营追踪与追责

## 4. 业务流转（端到端）

1. 资产与要约创建：写入 `edc_cp_asset`、`edc_cp_contract_offer`（一资产可多 Offer），并同步镜像到 `edc_fc_catalog_item`。  
2. 合同协商：按 `offer_id` 发起并写入 `edc_cp_contract_negotiation`。  
3. 协议生成：写入 `edc_cp_contract_agreement`，保留 `offer_id` 追踪套餐来源，并写入协商账单 `edc_op_billing_record`。  
4. 发起传输：控制面选定 `data_plane_id`，写入 `edc_cp_transfer_process`（`REQUESTED`），并写入传输账单 `edc_op_billing_record`。  
5. 数据面执行传输：以同一 `transfer_process_id` 写入/更新 `edc_dp_transfer_process`（`STARTED`）。  
6. 生成 EDR：写入 `edc_dp_edr`，并回填控制面 `edc_cp_transfer_process.edr_endpoint/edr_auth_token`。  
7. 消费端拉取数据：通过 EDR endpoint + token 访问数据面接口。  
8. 全链路审计：`ASSET_* / NEGOTIATION_* / TRANSFER_*` 事件写入 `edc_op_audit_event`。  

## 5. 一致性约束（当前实现）

- 当前是**应用层约束**（服务逻辑维护关联键），数据库暂未加外键。  
- 关键一致性要求：`cp_transfer_process.id` 必须与 `dp_transfer_process.id` 一致。  
- 双节点分流要求：`count(distinct data_plane_id)` 在传输表中应 >= 2（场景验证）。  
- 计费一致性要求：业务接口执行前必须调用运营计费校验接口，`allowed=true` 才可继续业务写入。  
- 会员一致性要求：协商与传输前必须存在当前有效 `ACTIVE` 会员（基于 `participant_id` 校验）。  

## 6. 排障查询（MySQL）

```sql
-- 1) 资产、协商、协议、传输数量
select count(*) as cp_asset from edc_cp_asset;
select count(*) as cp_neg from edc_cp_contract_negotiation;
select count(*) as cp_agr from edc_cp_contract_agreement;
select count(*) as cp_tp from edc_cp_transfer_process;

-- 2) 控制面与数据面传输ID对齐情况
select cp.id
from edc_cp_transfer_process cp
left join edc_dp_transfer_process dp on dp.id = cp.id
where dp.id is null;

-- 3) EDR 缺失检查
select dp.id
from edc_dp_transfer_process dp
left join edc_dp_edr e on e.transfer_process_id = dp.id
where e.transfer_process_id is null;

-- 4) 双数据面分流检查
select count(distinct data_plane_id) as cp_dp_distinct from edc_cp_transfer_process;
select count(distinct data_plane_id) as dp_dp_distinct from edc_dp_transfer_process;
```

## 7. 相关代码与DDL

- 建表定义：`docs/sql/mysql-schema.sql`
- 控制面主流程：`edc-control-plane/src/main/java/com/example/edc/controlplane/service/ControlPlaneService.java`
- 数据面传输与EDR：`edc-data-plane/src/main/java/com/example/edc/dataplane/service/DataPlaneTransferService.java`
- 数据面注册心跳：`edc-data-plane/src/main/java/com/example/edc/dataplane/service/DataPlaneRegistrationService.java`
