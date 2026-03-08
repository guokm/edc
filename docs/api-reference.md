# API 参考（Maven 核心版）

## 1. Control Plane（8181）

- `GET /api/catalog`：查询目录（每个资产返回 `offers[]`）
- `GET /api/catalog/{assetId}`：查询资产目录详情（包含该资产全部 `offers[]`）
- `POST /api/catalog/assets`：供应方发布资产并生成 offer
- `POST /api/contracts/negotiations`：发起协商（入参使用 `offerId`；先校验消费方 ACTIVE 会员 + 签发资格（DCP 已校验），再执行按次计费；offer 不存在或不属于资产时返回 409，并落库 `REJECTED_OFFER_*`）
- `GET /api/contracts/negotiations`：查询协商列表
- `GET /api/contracts/agreements`：查询协商合同（协议）列表
- `POST /api/transfers`：发起传输（先校验协议消费者 ACTIVE 会员 + 签发资格（DCP 已校验），再执行按次计费；支持自动选 Data Plane）
- `GET /api/transfers/orchestration/preview?agreementId=...&dataPlaneId=...`：按协议预演传输编排门禁（会员/签发资格/计费只读/Data Plane 选择，不扣费；需权限头：`X-Participant-Id: operator` + `X-Operator-Token`）
- `GET /api/transfers/{transferProcessId}/edr`：查询 EDR
- `GET /api/transfers/status`：查询传输状态（控制面 + 数据面）
- `GET /api/transfers/{transferProcessId}/trace`：查询全链路轨迹（资产/协商/协议/传输/EDR）
- `GET /api/monitor/health`：汇总全模块健康状态（前端统一巡检）
- `GET /api/monitor/governance`：汇总治理接口状态（前端治理页巡检）
- `GET /api/monitor/dataplanes`：汇总双 Data Plane 运行摘要
- `POST /api/dsp/catalog/request`：DSP 目录请求
- `POST /api/dsp/negotiations`：DSP 协商
- `POST /api/dsp/transfers`：DSP 传输
- `POST /api/dataplanes/register`：Data Plane 注册
- `GET /api/dataplanes`：查询注册节点
- `POST /api/scenario/run`：生成测试数据并跑完整流程
- `POST /api/scenario/dual-plane-demo`：强制 dp-1 / dp-2 双平面传输演示

## 2. Data Plane（8182 / 8187）

- `POST /api/transfer/start`
- `POST /api/transfer/suspend`
- `POST /api/transfer/resume`
- `POST /api/transfer/terminate`
- `GET /api/transfer/edr/{transferProcessId}`
- `GET /api/data/{transferProcessId}?message=...`（需 `Authorization`）
- `GET /api/dataplane/info`

## 3. 其他模块

- Identity Hub（8183）
  - `GET /api/identity/did`
  - `POST /api/identity/credentials`
  - `GET /api/identity/credentials/{id}`
  - `POST /api/identity/presentations`
  - `POST /api/dcp/presentations`
  - `POST /api/dcp/verification`
  - `GET /api/dcp/qualification?participantId=...&audience=...`
- Issuer Service（8184）
  - `POST /api/issuer/credentials`
- Federated Catalog（8185）
  - `GET /api/federated/catalog`
  - `POST /api/federated/crawl`
- Operator Services（8186）
  - `POST /api/memberships`
  - `GET /api/memberships/active?participantId=...`
  - `POST /api/policies`
  - `GET /api/policies`
  - `POST /api/audit/events`
  - `POST /api/billing/records`
  - `POST /api/billing/usage/check`（按次计费校验并扣减次数）
  - `GET /api/billing/usage/status?participantId=...&serviceCode=...`（查询当前使用状态，不扣减）

这些接口保持原路径，可通过 `./scripts/verify.sh` 一并验证可用性。
