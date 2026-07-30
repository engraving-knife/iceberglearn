# 提交 3048：INFRA: Skip running CI for doap.rdf file (#14919)

## 提交信息

- **序号**：3048 / 4088
- **哈希**：0069c5e09617d99a3ddce33a4a093fe288243eeb
- **短哈希**：0069c5e09
- **日期**：2025-12-23
- **作者**：Prashant Singh
- **提交说明**：INFRA: Skip running CI for doap.rdf file (#14919)
- **PR/Issue**：#14919

## 总体目的

前一个提交（3047，更新 `doap.rdf` 登记 1.10.1 发布）触发了全量 CI 流水线运行，而 `doap.rdf` 只是一个纯元数据文件（RDF 描述文档），它的变更不会影响任何 Java/Scala 代码、构建产物或运行时行为。为这类纯文档/元数据变更跑完整 CI 既浪费算力，又会拖慢真正需要验证的 PR 排队。此提交的动机就是把 `doap.rdf` 加入所有 GitHub Actions 工作流的 `paths-ignore`（忽略路径）列表，使今后仅修改 `doap.rdf` 时不再触发 CI。

这与仓库已有的惯例一致：`CONTRIBUTING.md`、`LICENSE`、`NOTICE` 等非代码文件早已在忽略列表中，`doap.rdf` 性质相同，理应一并加入。该提交紧随 3047 之后由同一作者 Prashant Singh 完成，可视为对 3047 引入的 CI 噪声的直接补救。

## 如何达成设计目的**

遍历 `.github/workflows/` 下所有 CI 工作流文件，在各自的 `push`/`pull_request` 触发条件的 `paths-ignore` 数组中追加 `'doap.rdf'` 一项，使其与已有的忽略文件并列。共覆盖 6 个工作流：delta-conversion-ci、flink-ci、hive-ci、java-ci、kafka-connect-ci、spark-ci。

## 修改详情

### `.github/workflows/delta-conversion-ci.yml` (+1/-0 lines)

**修改目的**：在该工作流的路径忽略列表中加入 `doap.rdf`。

**工作逻辑**：在 `paths-ignore` 中 `**/NOTICE` 之后新增 `- 'doap.rdf'`，使得仅改动 `doap.rdf` 时该工作流不被触发。

### `.github/workflows/flink-ci.yml` (+1/-0 lines)

**修改目的**：同上，Flink CI 忽略 `doap.rdf`。

**工作逻辑**：`paths-ignore` 数组追加 `doap.rdf`。

### `.github/workflows/hive-ci.yml` (+1/-0 lines)

**修改目的**：同上，Hive CI 忽略 `doap.rdf`。

**工作逻辑**：`paths-ignore` 数组追加 `doap.rdf`。

### `.github/workflows/java-ci.yml` (+1/-0 lines)

**修改目的**：同上，核心 Java CI 忽略 `doap.rdf`。

**工作逻辑**：`paths-ignore` 数组追加 `doap.rdf`。

### `.github/workflows/kafka-connect-ci.yml` (+1/-0 lines)

**修改目的**：同上，Kafka Connect CI 忽略 `doap.rdf`。

**工作逻辑**：`paths-ignore` 数组追加 `doap.rdf`。

### `.github/workflows/spark-ci.yml` (+1/-0 lines)

**修改目的**：同上，Spark CI 忽略 `doap.rdf`。

**工作逻辑**：`paths-ignore` 数组追加 `doap.rdf`。注意该文件在 3046 提交中刚被改过（加入 Spark 4.1 矩阵），此处叠加一行忽略配置，二者无冲突。

## 总结

此提交是一个 CI 优化（INFRA 类）改动，把纯元数据文件 `doap.rdf` 纳入所有 GitHub Actions 工作流的忽略路径，避免发布流程相关的 DOAP 更新触发无意义的全量构建，节省 CI 资源并加快 PR 反馈。改动低风险且与既有忽略策略一致。
