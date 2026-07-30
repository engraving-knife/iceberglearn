# 提交 2840：Spark: Backport stream-results for remove orphan files to 3.4 and 4.0 (#14522)

## 提交信息

- **序号**：2840 / 4088
- **哈希**：17e76f8f80d41ecc10ad47e631846f1a29d41561
- **短哈希**：17e76f8f8
- **日期**：2025-11-06 13:14:31 -0800
- **作者**：Arif Azmi
- **提交说明**：Spark: Backport stream-results for remove orphan files to 3.4 and 4.0 (#14522)
- **PR/Issue**：#14522

## 总体目的

提交 2833（PR #14278）为 Spark 3.5 的 `remove_orphan_files` 引入了 `stream-results` 流式删除模式，防止清理海量孤立文件时 driver OOM。但 Iceberg 同时维护 Spark 3.4 与 4.0 两个版本分支，这两个分支的 `DeleteOrphanFilesSparkAction` 与 `RemoveOrphanFilesProcedure` 实现结构与 3.5 基本一致，同样存在"一次性 collect 全部孤立文件路径导致 driver OOM"的风险。

本提交把 2833 的修改原样 backport 到 Spark 3.4 与 4.0 两个分支，使三个 Spark 版本（3.4、3.5、4.0）在 `remove_orphan_files` 的流式删除能力上保持一致。修改内容、设计思路与 2833 完全相同，只是路径前缀不同（`spark/v3.4/...` 与 `spark/v4.0/...`）。

## 如何达成设计目的

对 Spark 3.4 与 4.0 各自复制 2833 的全部修改：

1. `DeleteOrphanFilesSparkAction`：新增 `STREAM_RESULTS`/`STREAM_RESULTS_DEFAULT`/`MAX_ORPHAN_FILE_SAMPLE_SIZE`/`MAX_ORPHAN_FILE_SAMPLE_SIZE_DEFAULT=20000`/`DELETE_GROUP_SIZE=100000` 常量；新增 `streamResults()`；重构 `doExecute()` 与 `deleteFiles(Dataset<String>)` 支持流式迭代与采样输出；拆分 `deleteBulk`/`deleteNonBulk`；`findOrphanFiles` 改为返回缓存的 `Dataset<String>`。
2. `RemoveOrphanFilesProcedure`：新增 `stream_results` 参数，true 时 `action.option("stream-results", "true")`；顺带修正 warn 日志末尾空格。
3. `TestRemoveOrphanFilesAction`：新增流式 dry run 断言与 `testStreamResultsDeletion` 测试，适配 `findOrphanFiles` 新签名。

注：本次 backport 不包含对 `docs/docs/spark-procedures.md` 的修改（2833 修改的是 `docs/...`，而 3.4/4.0 backport 只动 spark 模块代码）。文档侧的 `stream_results` 说明已在 2833 中统一加入，对所有版本生效。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/DeleteOrphanFilesSparkAction.java` (+136/-58 lines)

**修改目的**：核心实现——支持流式拉取与采样输出。与 2833 中 Spark 3.5 改动完全一致。

**工作逻辑**：类注释补充流式模式说明；新增配置常量；`streamResults()` 读取开关；新 `doExecute()` 调用返回 Dataset 的 `findOrphanFiles` 并在 `try/finally` 中 `deleteFiles` + `unpersist`；`deleteFiles(Dataset<String>)` 按流式/非流式选择迭代器，`Iterators.partition` 按 10 万分组，每组采样 + 删除，累加 `filesCount`；`collectPathsForOutput` 流式模式按 `maxSampleSize` 限制；`deleteBulk`/`deleteNonBulk` 拆分；`findOrphanFiles` 改为 `static Dataset<String>`，`cache()` 后 `count()` 触发 conflicts 累加器。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/procedures/RemoveOrphanFilesProcedure.java` (+10/-3 lines)

**修改目的**：暴露 `stream_results` 参数。与 2833 一致。

**工作逻辑**：新增 `STREAM_RESULTS_PARAM`，加入 `PARAMETERS`；`call` 中读取并透传 `action.option("stream-results", "true")`；修正 warn 日志空格。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRemoveOrphanFilesAction.java` (+97/-4 lines)

**修改目的**：验证流式模式并适配 `findOrphanFiles` 新签名。与 2833 一致。

**工作逻辑**：导入 `STREAM_RESULTS`/`MAX_ORPHAN_FILE_SAMPLE_SIZE`；在 dry-run 测试中插入流式 dry run 段；新增 `testStreamResultsDeletion`；适配 `findOrphanFiles` 返回 Dataset。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/actions/DeleteOrphanFilesSparkAction.java` (+136/-58 lines)

**修改目的**：与 3.4 相同的改动应用到 Spark 4.0 分支。逻辑完全一致。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/procedures/RemoveOrphanFilesProcedure.java` (+10/-3 lines)

**修改目的**：与 3.4 相同的 procedure 参数改动应用到 4.0。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/actions/TestRemoveOrphanFilesAction.java` (+97/-4 lines)

**修改目的**：与 3.4 相同的测试改动应用到 4.0。

## 总结

该提交是 2833 的 backport，把 Spark 3.5 上 `remove_orphan_files` 的 `stream-results` 流式删除能力同步到 Spark 3.4 与 4.0 两个分支，实现三个 Spark 版本行为一致。所有代码与测试改动与 2833 一一对应，差异仅在路径前缀。这是一个跨版本一致性维护的机械 backport，未触及文档（文档已在 2833 中统一更新）。
