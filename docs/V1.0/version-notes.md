# V1.0 版本重要信息

## 1. 版本定位

- 版本号：`V1.0`
- 技术基线：`Java 21 + Spring Boot 3.4.7 + MyBatis Plus + MySQL + Kafka + Vue3`
- 构建方式：Maven 多模块
- 运行方式：Docker Compose 本地一体化

## 1.1 会话上下文关键结论（本次聊天沉淀）

- 上游 Eclipse EDC 源码（Gradle）定位为能力参考，不作为当前运行时；主交付以 Maven 工程为准。
- 后端统一规范为 `controller/service/mapper/entity`（可补充 `dto`），并落实到四个配套模块。
- 数据源已从 PostgreSQL 全量切换为 MySQL，并统一到建表基准文件 `docs/sql/mysql-schema.sql`。
- 前端已补齐多页面控制台（运行总览、场景演练、数据节点、传输追踪、治理接口）。
- 前端进一步升级为顶部导航控制台，新增“全链路流转”大屏：认证人信息、目录列表、资产详情、创建合同、协商列表、协商合同、传输状态、链路追踪、EDR 拉取、双数据平面演示全部可视化。
- 前端新增“演示向导（公司宣讲版）”：7 步自动串联全流程，并按步骤高亮当前讲解区域。
- 前端新增“演示角色分区”卡片：供应方/消费方/运营方职责在页面直接可见。
- 前端进一步按角色拆分入口：供应方/消费方/运营方进入后只显示本角色可操作页面，避免宣讲时功能混淆。
- 新增 `docs/one-page-demo.md`，用于公司演示“一眼看懂”角色职责与操作顺序。
- 角色入口布局调整为“顶部角色切换 + 下方功能 Tab”，并修复选中态色块展示。
- 全流程一键演示新增确认弹框，弹框内提供每一步骤目标与讲解信息。
- 供应方新增“策略来源与创建”与“发布后目录同步轨迹”可视化。
- 消费方新增“签发并签约”链路（Issuer -> Identity -> DCP -> 协商）可视化。
- 协商新增 Offer 强校验：offer 不存在或与资产不匹配时返回 `409`，并记录 `REJECTED_OFFER_*` 失败状态。
- 前端列表统一展示“时间”列，并按时间倒序分页展示（默认每页 10 条）。
- 目录与协商模型升级：`CatalogEntry` 支持 `offers[]`，协商入参改为 `offerId`，协商与协议落库新增 `offer_id`。
- 协商失败状态升级为 Offer 维度：`REJECTED_OFFER_NOT_FOUND` / `REJECTED_OFFER_ASSET_MISMATCH`。
- 运营计费采用“按调用次数校验并扣减一次”的简化模型，业务模块先校验后执行业务。
- 协商与传输新增“ACTIVE 会员强校验 + 按次计费”：
  - 协商前强制校验消费方 ACTIVE 会员（失败 `403`）。
  - 传输前强制校验协议消费者 ACTIVE 会员（失败 `403`）。
  - 协商与传输新增“签发资格”强校验（DCP 展示已校验且凭证有效，失败 `403`）。
  - 协商与传输分别接入 `CONTRACT_NEGOTIATION_CREATE` / `TRANSFER_START` 计费编码（额度不足 `402`）。
- 运营服务新增只读计费接口：`GET /api/billing/usage/status`，用于页面展示计费状态且不产生扣次。
- 前端新增“协商治理校验 / 传输治理校验”可视化区块，直接展示会员 `mem-xxx` 与计费 `used/remaining`。
- 身份中心新增资格查询接口：`GET /api/dcp/qualification`，供控制面与前端显示资格判定结果。
- 新增 agreement 级“编排门禁预演”接口：`GET /api/transfers/orchestration/preview`，展示会员/资格/计费只读/Data Plane 选择。
- 编排门禁预演接口新增权限头校验：`X-Participant-Id=operator` + `X-Operator-Token`（由 `EDC_ORCHESTRATION_DEMO_TOKEN` 控制）。
- Federated Catalog 与控制面目录建立镜像同步（按 `offer_id` 幂等 upsert），关系可通过 `cp_offer.id = fc_catalog_item.offer_id` 追踪。
- 控制面关键动作自动写入运营审计表 `edc_op_audit_event`，前端治理页补充审计与账单列表展示。
- 协商/传输计费编码升级为业务维度：`CONTRACT_NEGOTIATION_CREATE:<offerId>`、`TRANSFER_START:<assetId>`。
- 协商/传输成功后自动写入 `edc_op_billing_record`（按 `agreement_id` 挂账）。
- 身份资格校验强化：Identity 凭证需携带并通过 `issuanceId` 校验（Issuer 新增 `GET /api/issuer/credentials/{issuanceId}`）。
- 部署模型升级为单入口网关：`frontend` 容器内置 Nginx，统一代理 `/cp` `/ih` `/is` `/fc` `/op` `/dp1` `/dp2`，宿主机只需把已有 Nginx 反代到 `127.0.0.1:18080`。
- Zookeeper 宿主机端口调整为 `12181`，避免与服务器已有组件冲突。
- 新增 `.env.example`，用于服务器部署时配置 `EDC_PUBLIC_BASE_URL`，保证 EDR endpoint 返回对外可访问地址。
- 根目录 `AGENT.md` 为全项目总说明；`docs/<版本号>/` 记录每个版本的重要信息（本文件即 V1.0）。

## 2. 本版本关键能力

- 控制面 / 数据面核心流程可运行：目录、协商、传输、EDR 拉取。
- 双 Data Plane 节点分流生效：`dp-1`（8182）与 `dp-2`（8187）。
- MySQL 持久化落地，核心表与扩展表均已建模并初始化。
- 前端控制台覆盖运行总览、场景演练、数据节点、传输追踪、治理接口。
- 前端已升级为三导航页：全链路流转、治理模块接口、节点与健康。
- 身份/发行/联邦/运营四模块已从内存 Demo 改为数据库持久化实现。
- Docker 部署已收敛到网关单入口，后端服务端口仅在 Docker 网络内互通，不映射宿主机。

## 2.1 本轮补齐接口（用于前端全链路展示）

- `GET /api/catalog/{assetId}`：资产目录详情。
- `POST /api/catalog/assets`：供应方发布资产并创建 Offer。
- `GET /api/contracts/negotiations`：协商列表。
- `GET /api/contracts/agreements`：协商合同（协议）列表。
- `GET /api/transfers/status`：传输状态（控制面 + 数据面）。
- `GET /api/transfers/{transferProcessId}/trace`：单传输全链路轨迹。
- `GET /api/transfers/orchestration/preview`：agreement 级编排门禁预演（只读，不扣费）。
- `POST /api/scenario/dual-plane-demo`：强制 dp-1 / dp-2 的双数据平面演示流程。
- `GET /api/issuer/credentials/{issuanceId}`：查询签发单明细。
- `POST /api/federated/internal/sync`：控制面向联邦目录的内部镜像同步接口（带令牌）。
- `GET /api/monitor/health`：控制面汇总各模块健康状态。
- `GET /api/monitor/governance`：控制面汇总治理接口状态。
- `GET /api/monitor/dataplanes`：控制面汇总双数据平面运行摘要。

## 2.2 本轮中文演示数据增强

- 控制面自动场景生成资产改为中文业务语义（车辆运行数据集、智慧交通标签、中文元数据键值）。
- 双数据平面演示资产改为中文语义并标注目标数据平面。
- 联邦目录默认资产与爬取资产改为中文业务语义（智慧城市流量与运营指标）。

## 3. 计费模式（按调用次数）

- 运营服务提供按次校验接口：`POST /api/billing/usage/check`。
- Identity Hub / Issuer Service / Federated Catalog / Control Plane 在关键接口执行前调用该接口。
- 校验通过则扣减 1 次额度；超过额度则拒绝请求。
- 相关表：
  - `edc_op_billing_plan`（配额与单价）
  - `edc_op_usage_counter`（按月使用次数）

## 4. 关键数据表（摘要）

- 控制面：`edc_cp_*`
- 数据面：`edc_dp_*`
- 身份中心：`edc_ih_credential`、`edc_ih_presentation`
- 发行服务：`edc_is_issuance`
- 联邦目录：`edc_fc_catalog_item`、`edc_fc_crawl_job`
- 运营治理：`edc_op_membership`、`edc_op_policy`、`edc_op_audit_event`、`edc_op_billing_record`、`edc_op_billing_plan`、`edc_op_usage_counter`

完整建表与中文注释见：`docs/sql/mysql-schema.sql`

## 4.1 本次实现中的关键问题与修复

- 问题：本机 `3306` 端口被占用导致 MySQL 容器启动冲突。  
  修复：宿主机映射改为 `13306:3306`，容器内服务仍使用 `3306`。
- 问题：旧容器残留（orphan）干扰新编排。  
  修复：统一使用 `docker compose down --remove-orphans` 清理后重启。
- 问题：Data Plane 启动后注册有延迟，过早执行场景会出现 `scenario-run 400`。  
  修复：`scripts/verify.sh` 增加 `dataplanes-ready` 等待逻辑（注册到 2 个节点后再跑场景）。
- 问题：四个配套模块原先使用内存数据，不满足商用持久化要求。  
  修复：全部改为 MyBatis Plus + MySQL 落库，并补齐 Schema 初始化。

## 5. 验证基线

- 构建命令：`mvn -q -DskipTests package`
- 全链路验证：`./scripts/verify.sh`
- 验证覆盖：
  - 后端健康检查
  - 场景跑通（资产/协商/传输）
  - EDR 拉取
  - 双 Data Plane 注册与分流
  - 运营计费按次校验（billing-check）

## 5.1 当前实现边界说明

- 当前表关系主要由应用层逻辑维护（ID 链路），数据库暂未全面加外键约束。
- 计费为简化版“按次”模型，默认参与方 `participant-a` 与默认配额策略已内置种子。
- 若扩展为正式商用计费，建议后续补充：套餐版本化、账期结算、对账与发票链路、幂等扣费。

## 6. 端口基线

- Docker 内置入口（前端+网关）：`127.0.0.1:18080`
- 推荐对外入口：宿主机 Nginx `80/443` 反代到 `127.0.0.1:18080`
- 控制面/数据面/身份/发行/联邦/运营：仅 Docker 网络内端口，不映射宿主机
- MySQL（宿主机映射）：`127.0.0.1:13306`
- Kafka：`127.0.0.1:9092` / `127.0.0.1:29092`
- Zookeeper：`127.0.0.1:12181`

## 6.1 网关路径基线

- `/cp` -> Control Plane
- `/ih` -> Identity Hub
- `/is` -> Issuer Service
- `/fc` -> Federated Catalog
- `/op` -> Operator Services
- `/dp1` -> Data Plane 1
- `/dp2` -> Data Plane 2

## 7. 相关文档

- 项目总览：`AGENT.md`
- 公司演示剧本：`docs/demo-playbook.md`
- API：`docs/api-reference.md`
- 表流转：`docs/table-data-flow.md`
- 操作手册：`docs/operation-manual.md`
- 技术蓝图：`docs/technical-blueprint.md`
