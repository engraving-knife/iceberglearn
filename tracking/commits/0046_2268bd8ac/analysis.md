# 提交 0046：Replace `.size() > 0` with `.isNotEmpty()` (#8819)

## 提交信息

- **序号**：0046 / 4088
- **哈希**：2268bd8acaaee2748b20ad93430ca7073ac53009
- **短哈希**：2268bd8ac
- **日期**：2023-10-13 12:59:05 +0200
- **作者**：Kirill Saied
- **提交说明**：Replace `.size() > 0` with `.isNotEmpty()` (#8819)
- **PR/Issue**：#8819（关联 ISSUE #8810）

## 总体目的

这是一次跨多个模块的代码清理提交，属于 ISSUE #8810 系列清理工作的一部分。提交将代码库中散落的 `collection.size() > 0`（以及 `> 0` 在布尔判断、断言中的等价形式）替换为语义更清晰的 `!collection.isEmpty()` 或 `collection.isEmpty()` 取反形式。这种写法不仅可读性更好，还能在某些集合实现中避免不必要的 O(n) 大小计算（例如对某些 `List`/`Set` 实现，`size()` 可能需要遍历，而 `isEmpty()` 通常只需 O(1) 检查第一个元素）。

之所以需要 `isNotEmpty()` 标题但实际写法是 `!isEmpty()`，是因为 Java 标准集合 API 没有 `isNotEmpty()` 方法（Guava 的 `Iterables`/`Sets` 等也未必都提供），所以实际清理统一落地为 `!xxx.isEmpty()` 形式；提交标题用 `isNotEmpty()` 是表述"非空判断"这一意图。

本提交覆盖 aliyun、api、aws（集成测试）、data（测试）、delta-lake、flink（v1.15/v1.16/v1.17 三个版本）、hive3、mr、parquet 等模块，共 13 个文件 16 处改动。与同一作者紧随其后的 0047（Spark 模块）、0050（Core 模块）属于同一清理批次（PR #8819、#8814、#8813 共同对应 ISSUE #8810），按模块拆分提交以便审阅。这类清理虽不改变运行时行为，但统一了代码风格，对 Iceberg 这种大型多模块项目的长期可维护性有积极意义。

## 如何达成设计目的

整体思路是机械式替换：找到所有形如 `xxx.size() > 0` 的表达式，按上下文替换为 `!xxx.isEmpty()`；对于 `Assert.assertTrue(coll.size() > 0)` 这类断言，进一步改写为更贴切的 `Assert.assertFalse(coll.isEmpty())`，让断言语义更直接。改动分布在生产代码（如 `ResidualEvaluator`、`FlinkSink`、`HiveIcebergOutputCommitter`、`BaseSnapshotDeltaLakeTableAction`、`ParquetBloomRowGroupFilter`、`OrcSplit`、`DataStatisticsCoordinator`）和测试代码（如 `AliyunOSSMockLocalStore`、`TestGlueCatalogNamespace`、`TestMetricsRowGroupFilter`、`TestMetricsRowGroupFilterTypes`）中。

## 修改详情

### `aliyun/src/test/java/org/apache/iceberg/aliyun/oss/mock/AliyunOSSMockLocalStore.java`

**修改目的**：将"按 bucket 名查找目录后用 size 判断是否存在"改为 `isEmpty` 判断。

**工作逻辑**：`findBucketsByFilter(...)` 返回的 `buckets` 列表，原写法 `buckets.size() > 0 ? buckets.get(0) : null` 改为 `!buckets.isEmpty() ? buckets.get(0) : null`。语义不变，仅表达方式更清晰。

### `api/src/main/java/org/apache/iceberg/expressions/ResidualEvaluator.java`

**修改目的**：在判定分区 spec 是否有字段时改用 `isEmpty`。

**工作逻辑**：`ResidualEvaluator.of(...)` 中 `spec.fields().size() > 0` 改为 `!spec.fields().isEmpty()`，用于决定是否构造真正的 `ResidualEvaluator` 还是返回 `unpartitioned(expr)`。这是 Iceberg 表达式残留计算的核心入口之一。

### `aws/src/integration/java/org/apache/iceberg/aws/glue/TestGlueCatalogNamespace.java`

**修改目的**：改进集成测试断言可读性。

**工作逻辑**：`Assert.assertTrue(namespaceList.size() > 0)` 改为 `Assert.assertFalse(namespaceList.isEmpty())`，语义更直接，避免"size 大于 0"这种迂回表达。

### `data/src/test/java/org/apache/iceberg/data/TestMetricsRowGroupFilter.java` 和 `TestMetricsRowGroupFilterTypes.java`

**修改目的**：测试辅助方法中"读取所有记录后判断是否有数据"改用 `isEmpty`。

**工作逻辑**：两处均为 `Lists.newArrayList(reader).size() > 0` 改为 `!Lists.newArrayList(reader).isEmpty()`。这里 `Lists.newArrayList(reader)` 会把 reader 中的记录物化为 `List`，再判空。语义不变。

### `delta-lake/src/main/java/org/apache/iceberg/delta/BaseSnapshotDeltaLakeTableAction.java`

**修改目的**：在 Delta Lake 表快照迁移逻辑中，把"添加/删除文件列表是否非空"的判断统一改为 `isEmpty`。

**工作逻辑**：原本 `filesToAdd.size() > 0 && filesToRemove.size() > 0`、`filesToAdd.size() > 0`、`filesToRemove.size() > 0` 三处分支判断（分别对应 OverwriteFiles、AppendFiles、DeleteFiles 三种提交路径）统一改为 `!filesToAdd.isEmpty() && !filesToRemove.isEmpty()`、`!filesToAdd.isEmpty()`、`!filesToRemove.isEmpty()`。这是本提交中改动逻辑密度最高的一处，三处分支语义不变，但可读性提升明显。

### `flink/v1.15/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkSink.java`、`flink/v1.16/.../FlinkSink.java`、`flink/v1.17/.../FlinkSink.java`

**修改目的**：三个 Flink 版本分支的 `FlinkSink.checkAndGetEqualityFieldIds()` 中等值字段列判断改用 `isEmpty`。

**工作逻辑**：`equalityFieldColumns != null && equalityFieldColumns.size() > 0` 改为 `equalityFieldColumns != null && !equalityFieldColumns.isEmpty()`。三个 Flink 版本（1.15/1.16/1.17）的同一文件同步修改，保证版本间一致性。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsCoordinator.java`

**修改目的**：Flink 1.17 数据统计协调器中子任务网关就绪检查改用 `isEmpty`。

**工作逻辑**：`Preconditions.checkState(gateways[subtaskIndex].size() > 0, ...)` 改为 `!gateways[subtaskIndex].isEmpty()`。这是 Flink sink shuffle 功能（v1.17 引入）的协调器逻辑，校验某个 subtask 的网关集合已就绪。

### `hive3/src/main/java/org/apache/hadoop/hive/ql/io/orc/OrcSplit.java`

**修改目的**：Hive3 ORC split 的 ACID 判定改用 `isEmpty`。

**工作逻辑**：`isAcid()` 中 `hasBase || deltas.size() > 0` 改为 `hasBase || !deltas.isEmpty()`，判断 split 是否带有 delta 文件（ACID 增量）。语义不变。

### `mr/src/main/java/org/apache/iceberg/mr/hive/HiveIcebergOutputCommitter.java`

**修改目的**：Hive MR OutputCommitter 中两处"是否有已提交数据文件"的判断改用 `isEmpty`。

**工作逻辑**：
- `abortJob` 路径中 `if (dataFiles.size() > 0)` 改为 `if (!dataFiles.isEmpty())`，控制是否清理已写入的数据文件；
- `commitJob` 路径中 `if (dataFiles.size() > 0)` 改为 `if (!dataFiles.isEmpty())`，控制是否创建 `AppendFiles` 提交。两处都是 committer 关键路径上的非空判断。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetBloomRowGroupFilter.java`

**修改目的**：Parquet Bloom Filter 行组过滤中"过滤器引用列集合非空"判断改用 `isEmpty`。

**工作逻辑**：`filterRefs.size() > 0 && Sets.intersection(...).isEmpty()` 改为 `!filterRefs.isEmpty() && Sets.intersection(...).isEmpty()`。这是布隆过滤器行组裁剪的提前退出逻辑：当过滤条件引用的列与布隆过滤器列无交集时，直接返回 `ROWS_MIGHT_MATCH`。

## 小结

本提交作为 ISSUE #8810 跨模块清理批次的一部分，将 aliyun/api/aws/data/delta-lake/flink/hive3/mr/parquet 等模块中 16 处 `.size() > 0` 统一替换为 `!isEmpty()` 形式，在不改变运行时行为的前提下显著提升了 Iceberg 多模块代码风格的一致性与可读性。
