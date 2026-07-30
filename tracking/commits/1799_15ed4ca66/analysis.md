# 提交 1799：Build: Ignore docker folder in CI (#12417)

## 提交信息

- **序号**：1799 / 4088
- **哈希**：15ed4ca6607a195ffc7a94d2ec7826326c233994
- **短哈希**：15ed4ca66
- **日期**：2025-02-28 09:00:42 +0100
- **作者**：Manu Zhang
- **提交说明**：Build: Ignore docker folder in CI (#12417)
- **PR/Issue**：#12417

## 总体目的

Iceberg 的多个 CI 工作流（delta-conversion、flink、hive、java、kafka-connect、spark）配置了 `paths-ignore`，用于在仅修改某些与该工作流无关的路径时跳过 CI 运行，以节省资源和加速开发反馈。此前 `paths-ignore` 列表已包含 `.gitignore`、`.asf.yaml`、`dev/**`、`docs/**`、`site/**`、`format/**` 等无关路径，但缺少 `docker/**`。

当仓库中新增了 `docker` 目录（用于 Docker 化测试环境或镜像构建）后，仅修改 docker 配置不应触发这些引擎 CI。本提交将 `docker/**` 加入各 CI 工作流的 `paths-ignore` 列表，避免仅 docker 变更时无谓地触发全量 CI。

## 如何达成设计目的

通过在 6 个 CI 工作流 YAML 文件的 `on.push.paths-ignore`（或 `paths-ignore`）列表中，在 `dev/**` 之后统一插入 `- 'docker/**'` 一行，使 docker 目录变更不再触发这些工作流。

## 修改详情

### `.github/workflows/delta-conversion-ci.yml`（修改, +1 lines）

**修改目的**：让 delta-conversion CI 忽略 docker 目录变更。

**工作逻辑**：在 `paths-ignore` 列表的 `- 'dev/**'` 之后新增 `- 'docker/**'`。

### `.github/workflows/flink-ci.yml`（修改, +1 lines）

**修改目的**：让 Flink CI 忽略 docker 目录变更。

**工作逻辑**：同上，在 `paths-ignore` 中新增 `- 'docker/**'`。

### `.github/workflows/hive-ci.yml`（修改, +1 lines）

**修改目的**：让 Hive CI 忽略 docker 目录变更。

**工作逻辑**：同上，在 `paths-ignore` 中新增 `- 'docker/**'`。

### `.github/workflows/java-ci.yml`（修改, +1 lines）

**修改目的**：让 Java CI 忽略 docker 目录变更。

**工作逻辑**：同上，在 `paths-ignore` 中新增 `- 'docker/**'`。

### `.github/workflows/kafka-connect-ci.yml`（修改, +1 lines）

**修改目的**：让 Kafka Connect CI 忽略 docker 目录变更。

**工作逻辑**：同上，在 `paths-ignore` 中新增 `- 'docker/**'`。

### `.github/workflows/spark-ci.yml`（修改, +1 lines）

**修改目的**：让 Spark CI 忽略 docker 目录变更。

**工作逻辑**：同上，在 `paths-ignore` 中新增 `- 'docker/**'`。

## 小结

- **成效**：docker 目录的变更不再触发 6 个引擎/模块的 CI 工作流，减少不必要的 CI 运行，节省资源。
- **影响范围**：仅影响 CI 工作流配置，不涉及代码改动。
- **回迁到 1.4.x 的注意事项**：纯 CI 配置改动，无风险，无前置依赖。但需确认 1.4.x 分支是否已存在 `docker` 目录及对应的 CI 工作流；若 1.4.x 上尚无 docker 目录，则回迁无实际意义，可暂不回迁。
