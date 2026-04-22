# V1.1 客户商用版规划

## 1. 版本定位

V1.1 定位为“客户可试点商用版”，目标不是继续做 Demo，而是让客户在受控生产环境中完成真实的成员接入、资产发布、授权协商、数据交付、审计追踪和按次计费。

当前 V1.0 已经跑通核心链路，但仍属于演示和验证基线。V1.1 必须补齐安全、权限、部署、数据源、运维、计费结算和验收体系，才能作为客户交付版本。

## 2. 目标客户场景

- 行业数据空间运营方：维护成员、身份、策略、审计、计费与节点健康。
- 数据供应方：发布真实数据资产，配置策略套餐，接受协商，追踪交付。
- 数据消费方：发现资产，完成资格凭证，发起协商，签约后拉取数据。
- 审计/监管角色：查看全链路审计、合同、传输、计费与异常记录。

## 3. 当前 V1.0 可复用能力

- Maven 多模块工程已成型：Java 21、Spring Boot 3.4.7、MyBatis Plus、MySQL、Kafka、Vue3。
- 双 Data Plane、资产目录、Offer、合同协商、Agreement、传输、EDR 拉取已经串通。
- Identity Hub、Issuer Service、Federated Catalog、Operator Services 已落库。
- 前端已按供应方、消费方、运营方拆分角色页面。
- Docker Compose 已改成单入口网关模式，后端不再直接暴露宿主机端口。
- 运营审计、按次计费、DCP 资格校验已有基础实现。

## 4. V1.1 必须补齐的商用能力

### 4.1 账号、权限与组织

- 新增登录认证，建议支持 OIDC/OAuth2，也保留本地账号模式。
- 新增 RBAC：平台管理员、运营员、供应方管理员、供应方操作员、消费方管理员、消费方操作员、审计员。
- `participantId` 与会员、组织、用户建立明确绑定关系。
- 所有写操作必须带操作者身份，并写入审计事件。
- 前端页面根据角色控制可见菜单和按钮，不再只靠页面入口区分。

### 4.2 成员与资格治理

- 会员表升级为企业成员主数据，补充企业名称、统一社会信用代码、联系人、状态、准入时间、退出时间。
- 凭证签发流程升级为“申请 -> 审批 -> 签发 -> 写入 -> 展示 -> 校验”。
- DCP 展示校验从演示按钮改为可追踪任务，记录校验方、校验时间、失败原因。
- 协商和传输门禁统一调用资格校验服务，不在前端重复拼逻辑。

### 4.3 资产、策略与 Offer

- 资产支持完整生命周期：草稿、待审核、已发布、已下架、已归档。
- 一资产多 Offer 保留，Offer 必须绑定策略、价格、有效期、调用额度、交付方式。
- 策略中心补充条件：地域、用途、行业、有效期、调用频次、脱敏级别、审批要求。
- Federated Catalog 与资产发布建立状态可视化：待同步、已同步、同步失败、重试中。
- 资产详情补充数据样例、字段字典、更新频率、服务 SLA、数据责任人。

### 4.4 合同协商与协议

- 协商状态机显式化：REQUESTED、QUALIFIED、OFFER_ACCEPTED、AGREEMENT_CREATED、FINALIZED、REJECTED、EXPIRED。
- 协商失败必须落库失败码、失败原因、失败步骤和操作者。
- Agreement 补充合同编号、合同版本、签约双方、协议期限、计费规则、终止条件。
- 支持协议撤销、续期、暂停、恢复。
- 前端增加合同详情页，展示从资产、Offer、策略、资格、协商到协议的完整链路。

### 4.5 数据交付与 Data Plane

- Data Plane 支持真实数据源连接器：
  - HTTP API
  - MySQL/JDBC 查询
  - S3/MinIO 对象文件
  - 本地文件目录（仅测试环境）
- EDR Token 支持过期、撤销、单次使用、限流、绑定协议和资产。
- 数据拉取必须校验 Agreement 状态、Token、额度、调用频次和 IP/来源约束。
- 传输流程补齐失败重试、超时、终止、审计和消息事件。
- 双 Data Plane 从演示分流升级为可配置路由：按资产、区域、负载、健康状态选择节点。

### 4.6 计费与结算

- 计费从简单 quota 扣减升级为三层模型：
  - 计费计划：成员维度，定义套餐、默认额度、价格。
  - Offer 价格：资产套餐维度，定义单价、包月、阶梯价或免费试用。
  - 用量流水：每次协商、每次数据调用、每次传输落独立流水。
- 协商创建可以收费，但默认建议“签约不收费，实际数据调用收费”。
- 账单按 agreementId、assetId、offerId、participantId 聚合。
- 补充账期、对账、账单状态、人工调整、导出 CSV/Excel。
- 前端运营方增加计费配置、用量流水、账单列表、账单详情和额度调整页面。

### 4.7 审计与合规

- 所有关键动作统一写入 `edc_op_audit_event`：
  - 登录/登出
  - 成员创建/审批/禁用
  - 资产发布/下架
  - 策略和 Offer 变更
  - 凭证签发/校验
  - 协商成功/失败
  - 协议创建/撤销
  - 数据传输/拉取/失败
  - 计费扣减/账单生成
- 审计事件补充 requestId、traceId、operatorUserId、participantId、ip、userAgent、签名摘要。
- 前端增加审计检索：时间、角色、动作、对象 ID、结果、失败原因。
- 关键审计建议做不可篡改增强：哈希链或外部日志归档。

### 4.8 运维、监控与部署

- Docker Compose 保留为单机交付和客户试点环境。
- 生产建议补充 Kubernetes/Helm 部署包。
- 接入 Prometheus 指标：
  - HTTP 请求耗时和状态码
  - 协商成功率
  - 传输成功率
  - Data Plane 健康
  - Kafka 消息堆积
  - MySQL 连接池
- 接入集中日志，至少提供 JSON 日志格式和 traceId。
- 补充备份恢复手册：MySQL 备份、恢复演练、配置备份。
- 补充启动前检查脚本：端口、Docker、磁盘、内存、域名、证书、数据库连通性。

## 5. 技术改造清单

### 5.1 后端

- 引入统一认证鉴权模块，所有 Controller 写接口接入权限校验。
- 引入全局异常码规范，错误响应包含 code、message、traceId、detail。
- 引入 Flyway 或 Liquibase，替代仅靠 SchemaService 自动建表的生产模式。
- 补充索引和唯一约束：
  - member participantId 唯一约束
  - offer assetId + policyId + version 约束
  - agreementId、negotiationId、transferProcessId 索引
  - usage participantId + serviceCode + periodMonth 唯一约束
- 关键接口增加幂等键，避免重复扣费、重复签约、重复传输。
- Kafka 事件定义版本化，补充事件消费失败重试和死信队列。

### 5.2 前端

- 增加登录页和用户态。
- 角色入口改为真实权限菜单。
- 供应方补齐资产生命周期页面。
- 消费方补齐合同详情、调用记录和额度余额。
- 运营方补齐成员审批、策略配置、计费配置、审计检索。
- 节点健康页补齐真实运行指标，不只展示接口通断。
- 所有操作按钮提供确认、处理中、成功、失败、重试状态。

### 5.3 数据库

- 保留 `docs/sql/mysql-schema.sql` 作为基准 SQL。
- 新增迁移脚本目录：`docs/sql/migrations` 或接入 Flyway 目录。
- 表字段补充中文注释、索引、唯一约束。
- 审计、用量、账单类表按时间字段设计归档策略。

### 5.4 部署

- Docker Compose 商用试点模式：
  - 宿主机 Nginx 对外 80/443
  - Docker 内置网关 `127.0.0.1:18080`
  - 后端服务不映射宿主机端口
  - MySQL/Kafka/Zookeeper 默认仅本机绑定
- Kubernetes 生产模式：
  - Ingress 暴露统一域名
  - Secret 管理数据库密码、Token、证书
  - ConfigMap 管理非敏感配置
  - StatefulSet 或外部托管数据库
  - 滚动升级和健康探针

## 6. 建议版本拆分

### 6.1 V1.1.0 客户试点版

目标：能给一个客户部署并跑真实试点。

必须完成：

- 登录认证和基础 RBAC。
- 成员/参与方/用户关系打通。
- 资产生命周期和一资产多 Offer。
- 协商、Agreement、传输状态机显式化。
- 数据拉取绑定 Agreement、EDR、额度。
- 运营审计全链路落库。
- 计费用量流水与账单查询。
- Docker Compose 单入口部署稳定。
- 操作手册、部署手册、验收手册完整。

### 6.2 V1.2.0 生产增强版

目标：支持客户生产环境长时间运行。

必须完成：

- Flyway/Liquibase 数据库迁移。
- Prometheus/日志/告警。
- Kubernetes/Helm 部署。
- 备份恢复与升级回滚。
- 性能压测和安全扫描。
- 审计防篡改增强。
- 多数据源连接器稳定化。

### 6.3 V1.3.0 多客户运营版

目标：支持平台多客户、多行业、多空间运营。

必须完成：

- 多租户/多数据空间隔离。
- 跨节点 Federated Catalog 策略化爬取。
- 复杂计费：阶梯价、包月、预付费、账期结算。
- 合同模板和在线审批。
- 数据产品市场和上架审核。
- 外部 IAM、CA、KMS、对象存储集成。

## 7. V1.1 验收标准

### 7.1 功能验收

- 运营方创建成员、审批成员、签发资格。
- 供应方创建策略、发布资产、配置多个 Offer。
- 目录同步成功后消费方可发现资产。
- 消费方完成资格校验后发起协商。
- 协商成功生成 Agreement。
- 消费方基于 Agreement 发起传输并拉取数据。
- 每次拉取产生用量流水。
- 运营方可查看审计、账单、节点健康和传输状态。
- 错误策略、无资格、额度不足、协议失效均能明确失败并落库。

### 7.2 安全验收

- 未登录不能访问业务接口。
- 无权限角色不能执行越权操作。
- EDR Token 过期后不可拉取数据。
- 被暂停的 Agreement 不可继续传输。
- 所有写操作都有审计事件。
- 敏感配置不写死在代码和镜像中。

### 7.3 部署验收

- 服务器只开放 80/443。
- Docker 后端服务不暴露宿主机端口。
- `EDC_PUBLIC_BASE_URL` 配置为客户域名后，EDR endpoint 返回客户域名。
- `docker compose up -d --build` 可重复执行。
- `./scripts/verify.sh` 可通过网关完成验证。
- 停机、启动、备份、恢复流程可按文档执行。

### 7.4 性能验收

- 资产目录 1 万条可分页查询。
- 协商创建 P95 小于 1 秒。
- EDR 查询 P95 小于 500 毫秒。
- 单 Data Plane 每分钟至少支持 1000 次小报文拉取。
- 审计和用量写入不阻塞核心链路超过 200 毫秒。

## 8. 当前优先级建议

第一优先级：

- 登录/RBAC。
- 成员与 participantId 关系打通。
- 计费流水和账单模型重构。
- 后端服务不映射宿主机端口后的脚本和文档收敛。
- 资产、Offer、Agreement、Transfer 状态机完善。

第二优先级：

- Flyway/Liquibase。
- 真实数据源连接器。
- 审计检索页面。
- EDR Token 过期/撤销/限流。
- Prometheus 指标。

## 9. 已开始落地的 V1.1 基座（2026-04-22）

本轮先落地“客户试点必须有的运营账号与主数据地基”，后续资产生命周期、账单流水、前端登录态都基于这层继续扩展。

### 9.1 数据模型

- 新增 `edc_op_organization`：企业/平台组织主数据。
- 新增 `edc_op_participant`：数据空间参与方主数据，`participant_id` 与控制面、身份、计费链路贯通。
- 新增 `edc_op_user_account`：运营登录账号，绑定 `organization_id + participant_id + role_code`。
- 新增 `edc_op_login_session`：登录会话，业务接口通过 `X-Operator-Token` 识别操作者。
- 默认种子：
  - 运营方：`operator_admin / ChangeMe@123`，角色 `PLATFORM_ADMIN`，参与方 `operator`
  - 供应方：`provider_admin / ChangeMe@123`，角色 `PROVIDER_ADMIN`，参与方 `participant-a`
  - 消费方：`consumer_admin / ChangeMe@123`，角色 `CONSUMER_ADMIN`，参与方 `participant-b`

### 9.2 新增接口

- `POST /api/auth/login`
- `GET /api/auth/me`
- `POST /api/auth/logout`
- `GET/POST /api/organizations`
- `GET/POST /api/participants`
- `GET/POST /api/users`

### 9.3 权限策略

- `POST /api/organizations`、`POST /api/participants`、`POST /api/users` 仅允许 `PLATFORM_ADMIN`。
- 列表接口要求登录：
  - `PLATFORM_ADMIN` / `AUDITOR` 可看全量。
  - 供应方/消费方账号只能看自己组织或参与方范围内的数据。
- 写操作会落 `edc_op_audit_event`，用于后续审计检索页面展示。
- 新增运营侧审计 payload 约定：`operatorUserId`、`operatorUsername`、`participantId`、`roleCode`、`traceId`。

### 9.4 验证

- 已通过 Maven 编译验证：`mvn -q -DskipTests -pl edc-operator-services -am package`。
- `scripts/verify.sh` 已补充运营登录、当前用户、组织、参与方、账号列表和新表数量检查。

第三优先级：

- Kubernetes/Helm。
- 多租户隔离。
- 防篡改审计。
- 外部 IAM/KMS/对象存储集成。

## 9. 不建议直接进入客户商用的点

- 当前权限模型仍偏演示，没有真实登录和 RBAC。
- 当前计费还是简化 quota 校验，缺少完整用量流水和账单生命周期。
- 当前 SchemaService 自动建表适合演示，生产应使用迁移脚本。
- 当前 DCP/凭证流程偏流程展示，需补审批、撤销、过期、失败原因。
- 当前真实数据源连接器不足，Data Plane 仍需要从演示传输升级为生产交付。

## 10. 推荐交付物

- `docs/V1.1/version-notes.md`：本规划文档。
- `docs/deployment.md`：客户部署手册。
- `docs/operation-manual.md`：运维操作手册。
- `docs/api-reference.md`：网关路径下的 API 参考。
- `docs/sql/mysql-schema.sql`：建表基准。
- `scripts/verify.sh`：客户环境验收脚本。
- `.env.example`：客户环境变量模板。
