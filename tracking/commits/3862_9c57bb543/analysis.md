# 提交 3862：Core: Add adapters from TrackedFile to DataFile, DeleteFile (#16100)

## 提交信息

- **序号**：3862 / 4088
- **哈希**：9c57bb543567ce9b97aab13f21d927df2bbdac82
- **短哈希**：9c57bb543
- **日期**：2026-06-11 14:34:32 -0700
- **作者**：Anoop Johnson
- **提交说明**：Core: Add adapters from TrackedFile to DataFile, DeleteFile (#16100)
- **PR/Issue**：#16100

## 总体目的

本提交新增了从 `TrackedFile` 到 `DataFile` 和 `DeleteFile` 的适配器（adapter），使 Iceberg V4 的追踪文件（TrackedFile）能被现有的读取和写入流程消费。这是 Iceberg V4 行级追踪（tracking）功能的关键集成步骤。

Iceberg V4 引入了 `TrackedFile` 概念，一种统一的文件表示，携带 `Tracking` 元数据（包括状态、序列号、DV 快照、行 ID 等）。但现有的大量代码（读取器、写入器、引擎集成等）依赖 `DataFile` 和 `DeleteFile` 接口。如果不提供适配器，需要重写所有消费文件的代码，工作量巨大且风险高。

本提交通过适配器模式解决这一问题：`TrackedFileAdapters` 提供工厂方法将 `TrackedFile` 包装为 `DataFile`、`DeleteFile`（DV 删除文件）和 `DeleteFile`（等值删除文件），委托给底层 `TrackedFile` 的数据，同时从 `Tracking` 元数据提取序列号、位置等信息。

## 如何达成设计目的

整体设计分两部分：

1. **`MetricsUtil` 新增 ContentStats 转换方法**：从 `ContentStats`（V4 统计结构）提取 `valueCounts`、`nullValueCounts`、`nanValueCounts`、`lowerBounds`、`upperBounds` 等 Map，供适配器使用。这些方法将 V4 的字段级统计转换为现有 `DataFile`/`DeleteFile` 期望的 `Map<Integer, ...>` 格式。

2. **`TrackedFileAdapters` 类**：提供三个工厂方法 `asDataFile()`、`asDVDeleteFile()`、`asEqualityDeleteFile()`，返回实现了 `ContentFile` 接口的适配器。适配器基类 `TrackedFileAdapter` 提供共享实现，子类 `TrackedDataFile`、`TrackedDVDeleteFile`、`TrackedEqualityDeleteFile` 提供特定实现。

## 修改详情

### `core/src/main/java/org/apache/iceberg/MetricsUtil.java` (+76/-0 lines)

**修改目的**：新增 ContentStats 到 Map 的转换方法。

**工作逻辑**：
新增 5 个 package-private 静态方法，从 `ContentStats` 提取各统计指标：
- `valueCounts(ContentStats)`：提取每字段的值计数，返回 `Map<Integer, Long>`。
- `nullValueCounts(ContentStats)`：提取每字段的 null 值计数。
- `nanValueCounts(ContentStats)`：提取每字段的 NaN 值计数。
- `lowerBounds(ContentStats)`：提取每字段的下界，使用 `Conversions.toByteBuffer()` 转换为 ByteBuffer。
- `upperBounds(ContentStats)`：提取每字段的上界。

所有方法在 stats 为 null 时返回 null，结果为空时也返回 null，否则返回不可修改的 Map。

### `core/src/main/java/org/apache/iceberg/TrackedFileAdapters.java` (+428/-0 lines, new file)

**修改目的**：新增 TrackedFile 到 DataFile/DeleteFile 的适配器。

**工作逻辑**：

1. **工厂方法**：
```java
static DataFile asDataFile(TrackedFile file, Map<Integer, PartitionSpec> specsById)
static DeleteFile asDVDeleteFile(TrackedFile file, Map<Integer, PartitionSpec> specsById)
static DeleteFile asEqualityDeleteFile(TrackedFile file, Map<Integer, PartitionSpec> specsById)
```
每个方法校验 `file.contentType()` 匹配预期类型，然后创建对应适配器。

2. **`TrackedFileAdapter<F>` 抽象基类**：实现 `ContentFile<F>` 接口的共享方法：
   - `pos()` / `manifestLocation()`：从 `tracking()` 获取。
   - `specId()` / `partition()`：从文件或 spec 获取。
   - `dataSequenceNumber()` / `fileSequenceNumber()`：从 `tracking()` 获取。

3. **`TrackedContentFile<F>` 中间基类**：扩展 `TrackedFileAdapter`，提供文件本身就是内容文件的适配器共享方法（`path()`、`location()`、`format()` 等）。

4. **`TrackedDataFile`**：实现 `DataFile` 接口，从 `TrackedFile` 的 `ContentStats` 通过 `MetricsUtil` 提取 recordCount、fileSize、valueCounts、lowerBounds 等指标。

5. **`TrackedDVDeleteFile`**：实现 `DeleteFile` 接口，表示 DV 删除文件。从 `Tracking` 的 `dvSnapshotId`、`deletedPositions` 等获取 DV 相关信息。

6. **`TrackedEqualityDeleteFile`**：实现 `DeleteFile` 接口，表示等值删除文件。

### `core/src/test/java/org/apache/iceberg/TestTrackedFileAdapters.java` (+482/-0 lines, new file)

**修改目的**：全面测试适配器行为。

**工作逻辑**：
测试覆盖：
- 工厂方法创建正确的适配器类型。
- 内容类型不匹配时抛出异常。
- 适配器正确委托 `TrackedFile` 的数据（路径、格式、分区等）。
- 从 `Tracking` 提取序列号、位置等信息。
- 从 `ContentStats` 提取统计指标（recordCount、valueCounts、bounds 等）。
- DV 删除文件的 `dvSnapshotId`、`cardinality` 等字段。
- spec ID 不匹配时的异常。

## 总结

本提交是 Iceberg V4 追踪功能集成的关键一步，通过适配器模式让 `TrackedFile` 能被现有 `DataFile`/`DeleteFile` 消费者使用，避免了大规模代码重写。适配器从 `TrackedFile` 的数据和 `Tracking` 元数据中提取所有必要信息，包括通过 `MetricsUtil` 从 V4 `ContentStats` 转换统计指标。这使 V4 追踪功能能逐步集成到现有流程中，是 V4 演进的重要基础设施工件。
