# 提交 3149：Spark 4.1: Add Merge WriteSummary to Snapshot Summary (#15014)

## 提交信息

- **序号**：3149 / 4088
- **哈希**：829b6414659718de5d8acf35deee58f3cf2ed351
- **短哈希**：829b64146
- **日期**：2026-01-23 16:12:19 -0800
- **作者**：Szehon Ho
- **提交说明**：Spark 4.1: Add Merge WriteSummary to Snapshot Summary (#15014)
- **PR/Issue**：#15014

## 总体目的

Spark 4.1 在 DataSource V2 写入接口中引入了 `WriteSummary` 机制，其中针对 `MERGE INTO` 操作提供了 `MergeSummary`，它汇总了 merge 操作的各类行级指标，例如被复制、删除、更新、插入的行数，以及按 matched/not-matched-by-source 细分的更新与删除行数。然而在 Iceberg 的 Spark 4.1 写入实现中，这些由 Spark 统计出来的 merge 指标此前并没有被写入到 Iceberg 快照（Snapshot）的 summary 中，导致用户无法通过 Iceberg 快照元数据观察 MERGE 操作的行级统计信息，也无法在下游工具（如查询引擎、监控、审计）中消费这些指标。

本提交的目的就是把 Spark `MergeSummary` 提供的指标透传到 Iceberg 的 `SnapshotUpdate` 操作属性中，使其最终出现在快照 summary 的键值对里（以 `spark.merge-into.` 前缀标识）。这样 MERGE 操作的行级指标就与 Iceberg 自身的快照统计（如新增/删除数据文件数等）一同持久化，提升了可观测性。同时配套新增了针对 copy-on-write 与 merge-on-read 两种模式的指标测试，确保各 merge 子句（UPDATE/DELETE/INSERT、NOT MATCHED BY SOURCE 等）下指标值的正确性。

设计上需要兼顾两种写入路径：`SparkWrite`（用于 copy-on-write 的 overwrite 路径）与 `SparkPositionDeltaWrite`（用于 merge-on-read 的 row delta 路径），两条路径都需要接收并透传 `WriteSummary`。由于两处透传逻辑完全一致，本提交抽取了公共基类 `BaseSparkWrite` 来承载指标映射逻辑，避免重复。

## 如何达成设计目的

整体思路分三步：第一，新建抽象基类 `BaseSparkWrite`，提供 `setMergeSummaryProperties(SnapshotUpdate, MergeSummary)` 将 `MergeSummary` 的 8 项指标映射为以 `spark.merge-into.` 为前缀的快照属性；第二，让 `SparkWrite` 与 `SparkPositionDeltaWrite` 继承该基类，并在各自提交链路中接收 `WriteSummary` 参数、判断其是否为 `MergeSummary` 后调用基类方法写入属性；第三，新增 `TestMergeMetrics` 抽象测试基类及其 CoW/MoR 两个子类，覆盖各类 MERGE 子句组合下的指标断言。涉及 6 个文件：3 个新增生产/测试基类与 2 个新增测试子类、2 个现有写入类修改。

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/BaseSparkWrite.java` (+61/-0 lines)

**修改目的**：新增写入实现公共基类，集中承载 MergeSummary 指标到快照属性的映射。

**工作逻辑**：
该抽象类提供 `setMergeSummaryProperties(SnapshotUpdate<?> operation, MergeSummary mergeSummary)`，将 `MergeSummary` 的 8 个指标方法逐一映射为快照属性键：
- `numTargetRowsCopied()` → `spark.merge-into.num-target-rows-copied`
- `numTargetRowsDeleted()` → `spark.merge-into.num-target-rows-deleted`
- `numTargetRowsUpdated()` → `spark.merge-into.num-target-rows-updated`
- `numTargetRowsInserted()` → `spark.merge-into.num-target-rows-inserted`
- `numTargetRowsMatchedUpdated()` → `spark.merge-into.num-target-rows-matched-updated`
- `numTargetRowsMatchedDeleted()` → `spark.merge-into.num-target-rows-matched-deleted`
- `numTargetRowsNotMatchedBySourceUpdated()` → `spark.merge-into.num-target-rows-not-matched-by-source-updated`
- `numTargetRowsNotMatchedBySourceDeleted()` → `spark.merge-into.num-target-rows-not-matched-by-source-deleted`

每个值通过私有 `setIfPositive` 写入，仅当 `value >= 0` 时才调用 `operation.set(key, String.valueOf(value))`，避免写入无意义的负值（Spark 在某些未统计场景下可能返回负数）。`SnapshotUpdate` 是 Iceberg 所有快照更新操作（`OverwriteFiles`、`RowDelta` 等）的基类，其 `set(key, value)` 方法用于设置随快照持久化的属性。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkWrite.java` (+41/-4 lines)

**修改目的**：让 copy-on-write 写入路径（overwrite）接收并透传 `WriteSummary`，将其中的 merge 指标写入快照属性。

**工作逻辑**：
- `SparkWrite` 改为 `extends BaseSparkWrite`，引入 `MergeSummary`、`WriteSummary` 导入。
- `commitOperation` 方法新增重载 `commitOperation(SnapshotUpdate, String, WriteSummary summary)`；原单参数版本委托新版本并传 `null`。在写入应用属性与 WAP 暂存逻辑之间，插入判断：`if (summary instanceof MergeSummary) { setMergeSummaryProperties(operation, (MergeSummary) summary); }`，复用基类映射逻辑。
- 内部 `Writer` 的 `commit(WriterCommitMessage[])` 改为委托新增的 `commit(messages, null)`，并新增 `commit(WriterCommitMessage[], WriteSummary summary)` 重载以接收 Spark 4.1 传入的 summary。该重载在 overwrite 提交链路的各隔离级别分支（`commitWithSerializableIsolation`、`commitWithSnapshotIsolation`）及无校验分支中，把 `summary` 一路传递到 `commitOperation`。相应地，两个 `commitWith*Isolation` 方法的签名都新增 `WriteSummary summary` 参数。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeltaWrite.java` (+21/-6 lines)

**修改目的**：让 merge-on-read 的 position delta 写入路径接收并透传 `WriteSummary`，将 merge 指标写入快照属性。

**工作逻辑**：
- `SparkPositionDeltaWrite` 改为 `extends BaseSparkWrite`，引入 `MergeSummary`、`WriteSummary` 导入。
- 内部 `Writer` 新增 `commit(WriterCommitMessage[], WriteSummary summary)` 重载，原 `commit(WriterCommitMessage[])` 委托新方法传 `null`。
- `commitOperation` 方法签名新增 `WriteSummary summary` 参数，并在提交前同样用 `if (summary instanceof MergeSummary) setMergeSummaryProperties(...)` 透传指标，与 `SparkWrite` 保持一致。两条提交分支（带校验与无校验）调用 `commitOperation` 时都传入 `summary`。

### `spark/v4.1/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMergeMetrics.java` (+304/-0 lines)

**修改目的**：新增 MERGE 行级指标断言的抽象测试基类，覆盖各类 MERGE 子句组合。

**工作逻辑**：
该抽象类继承 `SparkRowLevelOperationsTestBase`，定义抽象方法 `expectedRowsCopied(long unchangedRowsInModifiedFiles)`，用于区分 CoW 与 MoR 下"被复制行数"的差异。类注释明确说明：copy-on-write 模式下未变更行会被重写（numTargetRowsCopied > 0），而 merge-on-read 模式使用 delete file，未变更行不会被复制（numTargetRowsCopied = 0）。

测试用例覆盖：
- `testMergeMetricsWithMatchedUpdate`：仅 WHEN MATCHED THEN UPDATE，验证 updated/copied 指标。
- `testMergeMetricsWithMatchedDelete`：仅 WHEN MATCHED THEN DELETE，验证 deleted 指标。
- `testMergeMetricsWithAllClauses`：同时含 UPDATE/DELETE/INSERT，验证多指标综合。
- `testMergeMetricsWithNotMatchedBySourceUpdate`：含 WHEN NOT MATCHED BY SOURCE THEN UPDATE，验证 not-matched-by-source-updated 指标。
- `testMergeMetricsWithNotMatchedBySourceDelete`：含 WHEN NOT MATCHED BY SOURCE THEN DELETE。
- `testMergeMetricsWithMultipleUpdatesAndDeletes`：复杂场景，混合 matched 条件删除/更新、insert、not-matched-by-source 更新/删除，全面验证 8 项指标。

每个用例执行 MERGE 后，通过 `SnapshotUtil.latestSnapshot(table, branch).summary()` 获取快照 summary，并用私有 `assertMergeMetric` 断言 `spark.merge-into.*` 各键值符合预期。

### `spark/v4.1/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestCopyOnWriteMergeMetrics.java` (+42/-0 lines)

**修改目的**：copy-on-write 模式的 merge 指标测试子类。

**工作逻辑**：
继承 `TestMergeMetrics`，通过 `extraTableProperties()` 设置 `TableProperties.MERGE_MODE` 为 `RowLevelOperationMode.COPY_ON_WRITE`；覆盖 `expectedRowsCopied` 直接返回传入的 `unchangedRowsInModifiedFiles`，因为 CoW 模式下被修改文件中的未变更行会被整体重写，故复制行数等于未变更行数。

### `spark/v4.1/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMergeOnReadMergeMetrics.java` (+42/-0 lines)

**修改目的**：merge-on-read 模式的 merge 指标测试子类。

**工作逻辑**：
继承 `TestMergeMetrics`，通过 `extraTableProperties()` 设置 `MERGE_MODE` 为 `MERGE_ON_READ`；覆盖 `expectedRowsCopied` 恒返回 `0`，因为 MoR 模式使用 delete file 标记删除，未变更行不会被复制重写。两个子类通过同一抽象基类共享所有用例，仅以 `expectedRowsCopied` 与表属性差异化，体现 CoW/MoR 在"复制行数"这一指标上的本质区别。

## 总结

本提交为 Spark 4.1 模块打通了 MERGE 操作行级指标到 Iceberg 快照 summary 的透传链路：通过新增 `BaseSparkWrite` 公共基类统一将 Spark `MergeSummary` 的 8 项指标映射为 `spark.merge-into.*` 快照属性，并在 `SparkWrite`（CoW overwrite）与 `SparkPositionDeltaWrite`（MoR row delta）两条写入路径中接收 `WriteSummary` 并透传；同时新增覆盖 CoW/MoR 两种模式、各类 MERGE 子句组合的指标测试，确保指标正确性，显著提升了 MERGE 操作在 Iceberg 中的可观测性。
