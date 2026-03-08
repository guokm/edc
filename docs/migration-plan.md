# Eclipse EDC 到 Maven 迁移计划（执行版）

本文档说明如何把 EDC 关键能力持续落入 Maven 模块，`upstream/connector` 仅作为能力对照。

## 0. 当前基线（Maven 主路径）

- 核心流程已落在 Maven 模块：
  - `edc-control-plane`
  - `edc-data-plane`
- 已实现并验收：
  - 双 Data Plane 节点注册与调度
  - MySQL 持久化（`edc_cp_*`、`edc_dp_*`）
  - 测试数据自动生成 + 协商 + 传输 + EDR 拉取全流程
- 一键验收入口：`./scripts/verify.sh`

## 1. 迁移目标

- 在不破坏上游 Gradle 构建的前提下，建立并完善 Maven 构建链路。
- 先完成 BOM（依赖矩阵）迁移，再分阶段迁移核心模块与扩展模块。
- 最终形成可用于企业发布、运维和二次开发的 Maven 多模块结构。

## 2. 当前完成情况

- 已创建 Maven 聚合工程：`upstream/connector/maven/pom.xml`
- 已完成以下 BOM 的 Maven 化（与 Gradle BOM 对齐）：
  - `dist/bom/controlplane-base-bom/pom.xml`
  - `dist/bom/controlplane-dcp-bom/pom.xml`
  - `dist/bom/controlplane-feature-sql-bom/pom.xml`
  - `dist/bom/dataplane-base-bom/pom.xml`
  - `dist/bom/dataplane-feature-sql-bom/pom.xml`

## 3. 分阶段迁移策略

### 阶段一：BOM 与依赖矩阵（已完成）

- 目标：在 Maven 中表达 EDC 能力组合与版本约束。
- 输出：控制面与数据面 BOM 已可用于依赖管理。

### 阶段二：核心库迁移（SPI + core/common/lib）

- 目标：迁移 `spi` 与 `core/common/lib`。
- 规则：
  - 每个 Gradle 子模块补充 `pom.xml`。
  - 依赖声明与 Gradle `dependencies {}` 保持一致。
  - 版本统一交由父 POM/BOM 管理。

### 阶段三：控制面与数据面核心迁移

- 目标：迁移 `core/control-plane`、`core/data-plane`。
- 输出：形成可组装的控制面/数据面 Maven 运行时模块。

### 阶段四：扩展与协议模块迁移

- 目标：迁移 `extensions` 与 `data-protocols`。
- 要求：确保 DSP / DCP / ODRL 相关能力在 Maven 下可构建、可测试、可运行。

### 阶段五：发布与 CI 收敛

- 目标：统一 Maven 发布流程、制品命名和 CI 流水线。
- 输出：可重复、可追踪的企业级构建发布链路。

## 4. Gradle 到 Maven 映射规则（简化）

- `project(":core:common:boot")` 对应 `org.eclipse.edc:boot`
- `project(":extensions:control-plane:api:control-plane-api")` 对应 `org.eclipse.edc:control-plane-api`
- 统一规则：默认使用模块路径最后一段作为 `artifactId`，特殊模块按实际命名修正。

## 5. 风险与注意事项

- 上游 `edc-build` Gradle 插件提供了构建约定与发布逻辑，迁移到 Maven 后需手工补齐。
- 模块数量大、依赖关系复杂，必须按 BOM 驱动逐层推进，避免一次性迁移导致失控。
- 部分测试基建与插件行为在 Maven 下可能不存在等价实现，需要逐项替代。

## 6. 落地执行清单（下一步）

1. 迁移 `spi/common` 第一批模块（建议 10 个以内，优先低耦合模块）。
2. 迁移 `core/common/lib` 关键模块（优先被控制面和数据面共同依赖的库）。
3. 确保 `dist/bom` 中声明的依赖在 Maven 下全部可解析。
4. 在 CI 中新增 Maven 构建校验（编译 + 单测 + 依赖收敛检查）。
5. 完成一轮“Gradle 与 Maven 构建结果对比”，确认功能一致性。

## 7. 验收标准

- Maven 全量构建可重复执行且通过。
- 关键运行时模块（控制面、数据面、身份、发行）在 Maven 下可启动。
- BOM 约束生效，不出现版本漂移。
- CI 能稳定产出制品并支撑后续版本发布。
