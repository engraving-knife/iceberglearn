# 提交 2834：Spark: enable stream-results option for remove orphan files (#14278)

## 提交信息

- **序号**：2834 / 4088
- **哈希**：99ccfc4b7bba2cd53eac8b8519cc81f7e418c8de
- **短哈希**：99ccfc4b7
- **日期**：2025-11-06 12:19:44 +0100
- **作者**：Arif Azmi
- **提交说明**：Spark: enable stream-results option for remove orphan files (#14278)
- **PR/Issue**：#14278

## 总体目的

Iceberg Spark 的 `remove_orphan_files` 存储过程用于清理未被任何 Iceberg 元数据引用的孤立文件。原实现把所有孤立文件路径通过 `collectAsList()` 一次性拉到 Spark driver，然后批量删除。当孤立文件数量极大时（例如几十万、上百万），driver 内存会被撑爆导致 OOM。

该提交为 `DeleteOrphanFilesSparkAction` 引入 `stream-results` 选项。启用后，孤立文件通过 `Dataset.toLocalIterator()` 按 RDD 分区流式拉取到 driver，分批删除（每批 `DELETE_GROUP_SIZE=100000`），driver 内存中只保留一份"采样"输出（默认最多 20000 条路径），避免 driver OOM。同时为了让 procedure 也能用上该能力，在 `RemoveOrphanFilesProcedure` 中新增 `stream_results` 参数，并在文档中说明其行为与限制。

## 如何达成设计目的

1. **新增配置常量**：在 `DeleteOrphanFilesSparkAction` 中加入 `STREAM_RESULTS`、`STREAM_RESULTS_DEFAULT`、`MAX_ORPHAN_FILE_SAMPLE_SIZE`（仅测试可见）、`MAX_ORPHAN_FILE_SAMPLE_SIZE_DEFAULT=20000`、`DELETE_GROUP_SIZE=100000`。
2. **重构删除流程**：把原本内联在 `doExecute` 中的删除逻辑抽到 `deleteFiles(Dataset<String> orphanFileDS)`，根据 `streamResults()` 选择 `toLocalIterator()`（流式）或 `collectAsList().iterator()`（一次性），再用 `Iterators.partition` 按 `DELETE_GROUP_SIZE` 分组，循环对每组执行 `collectPathsForOutput` + 删除（bulk 或 non-bulk），并累加 `filesCount`。
3. **拆分删除方法**：新增 `deleteBulk`（沿用原 `deleteFiles(SupportsBulkOperations, List)` 逻辑）与 `deleteNonBulk`（封装原 non-bulk `Tasks.foreach` 逻辑），让 `deleteFiles` 主流程更清晰。
4. **改造 `findOrphanFiles` 返回 Dataset**：原 `findOrphanFiles` 直接 `collectAsList()` 返回 `List<String>`；改为返回缓存的 `Dataset<String>`（先 `cache()` 再 `count()` 触发 conflicts accumulator 计算），由调用方负责 `unpersist`。这样流式模式可以基于该 Dataset 迭代而不必先全量 collect。
5. **采样输出**：`collectPathsForOutput` 在流式模式下只把每组中不超过剩余采样配额的路径加入结果列表，非流式模式则加入全部。
6. **Procedure 暴露参数**：`RemoveOrphanFilesProcedure` 增加 `stream_results` 可选 boolean 参数，当为 true 时通过 `action.option("stream-results", "true")` 透传。
7. **文档与测试**：在 `spark-procedures.md` 中描述 `stream_results` 参数；新增 `testStreamResultsDeletion` 测试，验证流式模式下采样数量受限（用 `max-orphan-file-sample-size=5`）且所有孤立文件仍被删除；并更新 `findOrphanFiles` 的调用方式（返回 Dataset 后 `collectAsList` + `unpersist`）。

## 修改详情

### `docs/docs/spark-procedures.md` (+1/-0 lines)

**修改目的**：在 `remove_orphan_files` 过程的参数表中记录 `stream_results` 选项。

**工作逻辑**：新增一行说明 `stream_results` 为 boolean，启用后孤立文件按 RDD 分区流式发送到 driver，输出最多包含 20000 条采样路径，建议在大文件量场景启用以防 driver OOM。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/DeleteOrphanFilesSparkAction.java` (+167/-58 lines)

**修改目的**：核心实现——重构删除流程以支持流式拉取与采样输出。

**工作逻辑**：
- 类注释补充流式模式说明：启用后结果只含最多 `MAX_ORPHAN_FILE_SAMPLE_SIZE_DEFAULT` 条采样路径，总删除数仅记日志。
- 新增常量 `STREAM_RESULTS`、`STREAM_RESULTS_DEFAULT=false`、`MAX_ORPHAN_FILE_SAMPLE_SIZE`（`@VisibleForTesting`）、`MAX_ORPHAN_FILE_SAMPLE_SIZE_DEFAULT=20000`、`DELETE_GROUP_SIZE=100000`。
- `streamResults()` 通过 `PropertyUtil.propertyAsBoolean(options(), STREAM_RESULTS, STREAM_RESULTS_DEFAULT)` 读取开关。
- 新 `doExecute()`：构建 actual/valid Dataset，调用 `findOrphanFiles(...)` 返回缓存的 `orphanFileDS`，在 `try/finally` 中调用 `deleteFiles(orphanFileDS)` 并 `unpersist`。
- `deleteFiles(Dataset<String>)`：根据是否流式选择迭代器；用 `Iterators.partition(orphanFiles, DELETE_GROUP_SIZE)` 分组；每组先 `collectPathsForOutput` 采样，再按 bulk/non-bulk 删除；累加 `filesCount` 并 `LOG.info`，最后用 `ImmutableDeleteOrphanFiles.Result.builder().orphanFileLocations(orphanFileList).build()` 返回。
- `collectPathsForOutput`：流式模式按 `maxSampleSize` 限制添加；非流式全量添加。
- `deleteBulk` 沿用原 bulk 删除逻辑；`deleteNonBulk` 封装 `Tasks.foreach` 逻辑，按 `deleteFunc` 是否存在选择 `table.io()::deleteFile` 或 `deleteFunc::accept`。
- `findOrphanFiles` 改为 `static Dataset<String>`，去掉 `SparkSession` 参数，对返回的 Dataset `cache()` 后 `count()` 触发 conflicts 累加器；若 `PrefixMismatchMode.ERROR` 且有冲突则抛 `ValidationException`；异常时 `unpersist`。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/RemoveOrphanFilesProcedure.java` (+10/-3 lines)

**修改目的**：在存储过程层面暴露 `stream_results` 参数。

**工作逻辑**：
- 新增 `STREAM_RESULTS_PARAM = optionalInParameter("stream_results", DataTypes.BooleanType)`，加入 `PARAMETERS` 数组。
- 在 `call` 方法中读取 `streamResults = input.asBoolean(STREAM_RESULTS_PARAM, false)`，当为 true 时 `action.option("stream-results", "true")`。
- 顺带修正一处 warn 日志末尾缺空格的小问题（`"This"` → `"This "`）。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRemoveOrphanFilesAction.java` (+97/-4 lines)

**修改目的**：验证流式模式行为并适配 `findOrphanFiles` 新签名。

**工作逻辑**：
- 导入 `STREAM_RESULTS`、`MAX_ORPHAN_FILE_SAMPLE_SIZE`。
- 在已有 dry-run 测试中插入一段：用 `.option(STREAM_RESULTS, "true").deleteWith(s -> {})` 做流式 dry run，断言仍能找到 1 个孤立文件且文件未被删除。
- 新增 `testStreamResultsDeletion`：写入 1 条有效记录 + 10 个孤儿 parquet 文件；先做非流式 dry run 断言返回全部 10 个；再做流式 dry run（`max-orphan-file-sample-size=5`）断言只返回 5 个采样；最后断言所有 10 个孤儿文件都被实际删除，表数据完好。
- 适配 `findOrphanFiles` 新签名：返回 `Dataset<String>` 后 `collectAsList()` + `unpersist`。

## 总结

该提交为 Spark 的 `remove_orphan_files` 引入了 `stream-results` 流式删除模式，通过 `toLocalIterator` 分区流式拉取、按 10 万一批分组删除，并将结果输出限制为最多 2 万条采样，有效防止 driver 在清理海量孤立文件时 OOM。同时把能力暴露为 procedure 参数并补充文档与测试。该功能后续在 2839 中被 backport 到 Spark 3.4 与 4.0。
