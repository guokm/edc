# AGENT.md - 会话接手总览（Maven 主路径）

> ⚠️ **【强制要求 (CRITICAL RULE FOR AI AGENTS)】**
> 1. 本文件 (`AGENT.md`) 是此 EDC 系统的**项目全局根信息库**。
> 2. **多个智能体 (Agents) 强依赖此文件来快速了解系统总体架构、表结构和各种接口的分布情况。**
> 3. **每次你（作为 AI Agent）修改了项目结构、新增/修改/删除了任何数据库表 (Entity)、新增/修改/删除了任何接口 (Controller/API) 后，都必须同步更新此文件！确保其时刻反映最新的工程状态。**

## 1. 项目定位

- 主技术栈：**Java 21 + Spring Boot 3.4.7 + MyBatis Plus + MySQL + Kafka + Vue3**
- 构建体系：Maven 多模块（根 `pom.xml`）
- 运行方式：Docker Compose 本地一体化
- 上游 `upstream/connector`：仅做能力对照与设计指引，不作为当前主运行时

## 2. 关键规范（必须遵守）

### 2.1 包结构规范

后端模块按以下分层组织：

- `controller`
- `service`
- `mapper`
- `entity`

可补充 `dto` 包承载请求/响应对象。

### 2.2 注释约束

- 所有 **Controller 接口方法** 必须在方法上方提供标准 JavaDoc，说明：
  - 功能用途
  - 关键入参含义
  - 返回值说明
- 所有 **Service 业务逻辑方法** 必须在方法上方提供标准 JavaDoc（同上）。

### 2.3 MyBatis Plus 与数据库约束

- 实体统一继承 `Model<T>`。
- Mapper 统一继承 `BaseMapper<T>`。
- 查询统一使用 `LambdaQueryWrapper`，避免字符串 SQL 字段错误。

### 2.4 DTO 时间字段约束

- DTO 中所有 `LocalDateTime` 字段必须标注：
  - `@JsonFormat`
  - `@JsonSerialize`
  - `@JsonDeserialize`

用于避免 Web 输出与反序列化异常。

### 2.5 表结构变更同步规则（强制）

- MySQL 建表基准文件：`docs/sql/mysql-schema.sql`。
- 任何涉及表结构的修改（新增列、改列类型、加表、删表、索引调整）必须同步更新：
  - `docs/sql/mysql-schema.sql`
  - `edc-control-plane/src/main/java/com/example/edc/controlplane/service/ControlPlaneSchemaService.java`
  - `edc-data-plane/src/main/java/com/example/edc/dataplane/service/DataPlaneSchemaService.java`
  - `edc-identity-hub/src/main/java/com/example/edc/identityhub/service/IdentitySchemaService.java`
  - `edc-issuer-service/src/main/java/com/example/edc/issuer/service/IssuerSchemaService.java`
  - `edc-federated-catalog/src/main/java/com/example/edc/federated/service/FederatedSchemaService.java`
  - `edc-operator-services/src/main/java/com/example/edc/operator/service/OperatorSchemaService.java`
- 若变更影响运维检查 SQL，还需同步更新 `scripts/verify.sh` 与 `docs/operation-manual.md`。

## 3. 模块结构（当前）

- `edc-control-plane`：核心控制面（目录、协商、传输编排、Data Plane 注册、场景执行）
- `edc-data-plane`：核心数据面（传输状态、EDR、数据拉取、自动注册）
- `edc-identity-hub`：身份中心
- `edc-issuer-service`：凭证发行
- `edc-federated-catalog`：联邦目录
- `edc-operator-services`：运营治理（会员/策略/审计/按次计费校验）
- `edc-common`：共享模型
- `frontend`：Vue3 控制台

### 3.1 前端页面结构（当前）

- 顶部改为**角色入口 + 角色内导航**：
  - `供应方入口`：
    - `供应方工作台`：策略管理（`/api/policies`）+ 资产发布 + 目录同步轨迹 + 协商与传输追踪 + 双平面演示 +（可选）7 步演示向导
    - `节点与健康`：服务健康、Data Plane 注册、双平面运行摘要
    - `角色演示文档`：供应方 3 步脚本与验收点
  - `消费方入口`：
    - `消费方工作台`：目录发现、签发并签约流程（Issuer -> Identity -> DCP -> 协商）、协议选择、传输发起、EDR 拉取
    - `角色演示文档`：消费方 3 步脚本与验收点
  - `运营方入口`：
    - `治理模块接口`：Identity / Issuer / Federated / Operator 四模块接口联调与按次计费校验
    - `节点与健康`：全服务健康检查与双节点摘要
    - `角色演示文档`：运营方 3 步脚本与验收点
- 固定角色：
  - 供应方：华东车联（`participant-a`）
  - 消费方：保险风控中心（`participant-b`）
  - 运营方：数据空间运营平台（`operator`）
- 列表展示规范（前端）：
  - 所有业务列表统一提供“时间”列
  - 默认按时间倒序
  - 默认每页 10 条（分页）

## 4. 双数据节点部署

`docker-compose.yml` 中数据面节点：

- `data-plane-1`（端口 8182，ID `dp-1`）
- `data-plane-2`（端口 8187，ID `dp-2`）
- Data Plane 公网地址统一经前端网关 Nginx 暴露：
  - `http://<host>:18080/dp1`
  - `http://<host>:18080/dp2`

网关与端口规则：

- Docker 内置入口：`frontend`（Nginx）`127.0.0.1:18080`
- 服务器对外入口由宿主机 Nginx 的 80/443 反代到 `127.0.0.1:18080`
- 后端服务端口只绑定本机 `127.0.0.1`（8181/8182/8183/8184/8185/8186/8187）
- Zookeeper 宿主机映射：`12181`
- 服务器部署时复制 `.env.example` 为 `.env`，把 `EDC_PUBLIC_BASE_URL` 改成对外域名；该值会进入 Data Plane EDR endpoint。

## 5. 核心持久化表

控制面：

- `edc_cp_asset`
- `edc_cp_contract_offer`
- `edc_cp_contract_negotiation`
- `edc_cp_contract_agreement`
- `edc_cp_transfer_process`
- `edc_cp_data_plane_instance`

数据面：

- `edc_dp_transfer_process`
- `edc_dp_edr`

身份中心：

- `edc_ih_credential`
- `edc_ih_presentation`

发行服务：

- `edc_is_issuance`

联邦目录：

- `edc_fc_catalog_item`
- `edc_fc_crawl_job`

运营治理：

- `edc_op_membership`
- `edc_op_policy`
- `edc_op_audit_event`
- `edc_op_billing_record`
- `edc_op_billing_plan`
- `edc_op_usage_counter`

## 6. 常用命令

```bash
# 编译（JDK21）
mvn -q -DskipTests package

# 启动全栈
docker compose up -d --build

# 一键验收（前后端+双节点+持久化+全流程）
./scripts/verify.sh

# 查看关键日志
docker compose logs --tail=200 control-plane
docker compose logs --tail=200 data-plane-1
docker compose logs --tail=200 data-plane-2
```

## 7. 高权限指令白名单（建议预授权）

以下命令在受限环境中通常需要提升权限。新会话建议按前缀一次性授权，后续执行可免逐条确认：

- `docker compose up -d --build`
- `docker compose down --remove-orphans`
- `docker compose ps`
- `docker compose logs --tail=200 <service>`
- `docker compose exec -T mysql env MYSQL_PWD=edc mysql -u edc -D edc -e "<sql>"`
- `./scripts/verify.sh`
- `mvn -q -DskipTests package`
- `npm run build`
- `ps -ef`
- `pkill -f <pattern>`

详细清单见：`docs/authorized-commands.md`

## 8. 已验证结果（当前基线）

- `./scripts/verify.sh` 可通过
- 双 Data Plane 已注册并参与传输分流
- 传输流程、EDR、核心业务数据均落 MySQL
- 前端已支持“全链路流转可视化”与“双数据平面传输演示”

## 8.1 本轮新增接口（2026-03-08）

Control Plane 新增/补齐：

- `GET /api/catalog/{assetId}`
- `POST /api/catalog/assets`
- `GET /api/contracts/negotiations`
- `GET /api/contracts/agreements`
- `GET /api/transfers/status`
- `GET /api/transfers/{transferProcessId}/trace`
- `GET /api/transfers/orchestration/preview?agreementId=...&dataPlaneId=...`（agreement 级编排门禁预演）
- `POST /api/scenario/dual-plane-demo`
- `GET /api/monitor/health`
- `GET /api/monitor/governance`
- `GET /api/monitor/dataplanes`

协商校验规则（2026-03-08 新增）：

- 目录模型已升级为“一资产多 Offer（多策略套餐）”，`GET /api/catalog*` 返回 `offers[]`。
- `POST /api/contracts/negotiations` 入参改为 `offerId`（而非 `policyId`）。
- 协商会强校验 `offerId` 必须存在且属于目标资产：
  - offer 不存在：`409` + `REJECTED_OFFER_NOT_FOUND`
  - offer 与资产不匹配：`409` + `REJECTED_OFFER_ASSET_MISMATCH`
- 协商与协议记录均落库 `offer_id` 字段，便于全链路追踪套餐来源。
- 协商与传输新增治理强校验：
  - 协商前必须校验消费者存在当前有效 `ACTIVE` 会员（否则 `403`）。
  - 传输前必须校验协议消费者存在当前有效 `ACTIVE` 会员（否则 `403`）。
  - 协商与传输都要求“签发资格”通过（存在已校验 DCP 展示且凭证未过期，否则 `403`）。
  - 协商与传输均接入按次计费校验：
    - `POST /api/contracts/negotiations` -> `CONTRACT_NEGOTIATION_CREATE`
    - `POST /api/transfers` -> `TRANSFER_START`
    - 额度不足返回 `402`。
- 身份中心新增资格查询接口：
  - `GET /api/dcp/qualification?participantId=...&audience=...`（用于判断参与方是否具备可用资格）。
- 身份资格校验进一步强化（2026-03-09）：
  - Identity 凭证写入支持 `issuanceId`（`POST /api/identity/credentials`）。
  - 资格校验除 DCP/过期外，还会校验 `claims.issuanceId` 在 Issuer 中存在且参与方一致。
  - Issuer 新增查询接口：`GET /api/issuer/credentials/{issuanceId}`。
- 运营服务补充只读计费查询接口：
  - `GET /api/billing/usage/status?participantId=...&serviceCode=...`（查询当前次数，不扣减）。
- 协商与传输的计费编码改为业务维度（2026-03-09）：
  - 协商：`CONTRACT_NEGOTIATION_CREATE:<offerId>`
  - 传输：`TRANSFER_START:<assetId>`
  - 协商/传输成功后会自动写入 `edc_op_billing_record`（`agreement_id` 反查资产与 Offer）。
- 新增“编排门禁预演”权限校验（2026-03-09）：
  - 访问 `GET /api/transfers/orchestration/preview` 必须携带：
    - `X-Participant-Id: operator`
    - `X-Operator-Token: <令牌>`
  - 控制面通过配置项 `edc.security.orchestration-demo-token`（环境变量 `EDC_ORCHESTRATION_DEMO_TOKEN`）进行令牌校验。
  - 该接口仅做门禁预演，不扣减计费额度；真实扣费发生在 `POST /api/transfers`。
- 前端消费方页面新增治理可视化：
  - “协商治理校验”与“传输治理校验”直接展示会员记录（`mem-xxx`）与计费 `used/remaining`。
- Federated Catalog 与控制面目录关系（2026-03-09）：
  - 控制面资产/Offer 会镜像同步到 `edc_fc_catalog_item`（通过 `offer_id` 幂等 upsert）。
  - 控制面新增内部同步调用：`POST /api/federated/internal/sync`（需 `X-Sync-Token`）。
  - 关系主键：`edc_cp_contract_offer.id` -> `edc_fc_catalog_item.offer_id`。
- 运营审计落地（2026-03-09）：
  - 控制面关键动作会自动写入 `edc_op_audit_event`：
    - `ASSET_CREATED` / `ASSET_PUBLISHED`
    - `NEGOTIATION_FINALIZED` / `NEGOTIATION_REJECTED`
    - `TRANSFER_STARTED`
  - 前端治理页已补充“审计列表/写入审计事件/账单列表”操作按钮。

## 8.2 本轮演示增强（2026-03-08）

- 控制面场景资产与双平面演示资产改为中文业务语义（资产名称、描述、元数据、所有者）。
- 联邦目录默认种子与爬取生成资产改为中文业务语义。
- 前端新增“演示向导（公司宣讲版）”：
  - 7 步串联：身份 -> 资产 -> 协商 -> 协议 -> 传输 -> 双平面 -> 计费
  - 支持“执行当前步骤”“一键演示全流程”
  - 支持按步骤自动高亮当前讲解区域
- “一键演示全流程”新增确认弹框，弹框展示每一步目标与讲解文案后再执行。
- 供应方发布资产新增“策略来源说明 + 发布同步轨迹”（创建资产 -> 写入 Offer -> 刷新目录）。
- 消费方新增“签发并签约流程”轨迹面板，展示 Issuer/Identity/DCP/协商全过程状态。
- 消费方传输区新增“Control Plane 编排门禁预演”面板：基于 agreementId 展示会员/资格/计费只读/Data Plane 选择，并显示建议 `POST /api/transfers` 请求体。

## 9. 文档组织规则（必须遵守）

- 项目根目录 `AGENT.md`：用于记录**整个项目**的总体说明、架构理解、关键规范、模块边界与长期约束。
- `docs/<版本号>/`：用于记录**每个版本**的重要信息（能力范围、关键变更、数据模型变更、验证结果、风险与回滚点）。
- 新版本发布时，必须新增对应版本目录与版本文档（例如 `docs/V1.0/version-notes.md`），并同步更新根 `AGENT.md` 的基线信息。
- 公司宣讲与现场演示统一参考：`docs/demo-playbook.md`（优先使用一键演示向导）。
- 角色化一眼速览文档：`docs/one-page-demo.md`（讲清每个角色做什么）。
