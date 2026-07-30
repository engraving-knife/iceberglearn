# 提交 3767：Core, Orc: Remove deprecated partition stats read functionality (#14998)

## 提交信息

- **序号**：3767 / 4088
- **哈希**：f37a04b532595354fdc85e127491b032f528e2e0
- **短哈希**：f37a04b53
- **日期**：2026-05-22 10:03:42 +0200
- **作者**：gaborkaszab
- **提交说明**：Core, Orc: Remove deprecated partition stats read functionality (#14998)
- **PR/Issue**：#14998

## 总体目的

这个提交移除了 Iceberg 核心模块中已废弃的分区统计（partition stats）读取功能。这些功能在之前的版本中已被标记为 `@Deprecated`（计划在 1.12.0 移除），现在被新的实现所替代：

1. **`PartitionStats` 类**：旧的分区统计数据持有类，已被 `PartitionStatistics`（基于 `BasePartitionStatistics`）替代。
2. **`PartitionStatsHandler` 中的废弃方法和字段**：包括 `schema()` 方法、各种 `NestedField` 常量（`SPEC_ID`、`DATA_RECORD_COUNT` 等）、`readPartitionStatsFile()` 方法和 `recordToPartitionStats()` 方法，这些已被 `PartitionStatistics` 接口和 `PartitionStatisticsScan` 替代。

这是 Iceberg 1.11.0 版本清理废弃代码的一部分，通过移除旧的 API 降低维护负担并引导用户使用新 API。

## 如何达成设计目的

1. 完全删除 `PartitionStats.java` 文件。
2. 从 `PartitionStatsHandler.java` 中移除所有废弃的字段常量、`schema()` 方法、`readPartitionStatsFile()` 方法和 `recordToPartitionStats()` 方法。
3. 更新测试代码，移除对已删除 API 的测试。
4. 在 `revapi.yml` 中添加 1.11.0 版本的 accepted breaks 记录，记录这些 API 的移除。

## 修改详情

### `.palantir/revapi.yml` (+56/-0 lines)

**修改目的**：记录 1.11.0 版本中移除的废弃 API。

**工作逻辑**：新增 `1.11.0` 版本条目，记录被移除的 `PartitionStats` 类、`PartitionStatsHandler` 中的多个字段和方法，justification 为 "Removed deprecated functionality for partition stats"。

### `core/src/main/java/org/apache/iceberg/PartitionStats.java` (删除 317 lines)

**修改目的**：移除已废弃的 `PartitionStats` 类。

**工作逻辑**：该类实现了 `StructLike` 接口，持有分区统计的 13 个字段（partition、specId、dataRecordCount、dataFileCount 等），并提供 `liveEntry()`、`deletedEntry()`、`appendStats()` 等方法。这些功能已被 `PartitionStatistics` 接口及其实现替代。

### `core/src/main/java/org/apache/iceberg/PartitionStatsHandler.java` (+4/-214 lines)

**修改目的**：移除废弃的字段、方法和导入。

**工作逻辑**：
- 移除所有 `@Deprecated` 标记的字段常量（`PARTITION_FIELD_ID`、`PARTITION_FIELD_NAME`、`SPEC_ID`、`DATA_RECORD_COUNT`、`DATA_FILE_COUNT` 等 13 个常量）。
- 移除废弃的 `schema(StructType, int)` 方法和 `v2Schema()`、`v3Schema()` 辅助方法（这些已被 `PartitionStatistics.schema()` 替代）。
- 移除废弃的 `readPartitionStatsFile(Schema, InputFile)` 方法（已被 `PartitionStatisticsScan` 替代）。
- 移除 `recordToPartitionStats()` 私有方法。
- 移除不再需要的导入（`Literal`、`InputFile`、`Types`、`IntegerType`、`LongType`、`NestedField`）。
- 将 `appendStats` 方法的可见性从 `private` 改为 `@VisibleForTesting` 包级，以便测试使用。

### `core/src/test/java/org/apache/iceberg/PartitionStatsHandlerTestBase.java` (+12/-386 lines)

**修改目的**：移除对已删除 API 的测试，适配新 API。

**工作逻辑**：大幅精简测试代码，移除所有使用 `PartitionStats` 类和废弃方法的测试用例，保留使用新 `PartitionStatistics` API 的测试。将使用 `appendStats` 的测试调整为使用包级可见的方法。

### `core/src/test/java/org/apache/iceberg/TestPartitionStats.java` (删除 135 lines)

**修改目的**：移除 `PartitionStats` 类的单元测试。

**工作逻辑**：整个文件被删除，因为 `PartitionStats` 类已被移除。

### `core/src/test/java/org/apache/iceberg/orc/TestOrcPartitionStatsHandler.java` (+1/-20 lines)

**修改目的**：移除 ORC 分区统计处理器测试中对废弃 API 的引用。

**工作逻辑**：移除使用废弃 `PartitionStatsHandler.schema()` 方法和 `PartitionStats` 类的测试代码。

## 总结

这个提交是 Iceberg 1.11.0 版本废弃代码清理的一部分，移除了旧的分区统计读取功能（`PartitionStats` 类和 `PartitionStatsHandler` 中的废弃方法/字段），这些已被新的 `PartitionStatistics` 接口和 `PartitionStatisticsScan` 替代。总共删除了约 991 行代码，同时添加了 154 行（主要是 revapi.yml 中的 accepted breaks 记录和少量测试调整）。这降低了代码维护负担，使 API 更加清晰。
