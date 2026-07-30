# 提交 2779：Flink: Support writing DVs in IcebergSink (#14197)

## 提交信息

- **序号**：2779 / 4088
- **哈希**：b6747f8cf6313fa4c53c5596bf75b675d721c8d2
- **短哈希**：b6747f8cf
- **日期**：2025-10-21 16:25:45 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Support writing DVs in IcebergSink (#14197)
- **PR/Issue**：#14197

## 总体目的

本提交为 Flink IcebergSink 添加 Deletion Vector（DV）写入支持。

Iceberg 在格式版本 V3 中引入了 Deletion Vector（DV）作为位置删除（position delete）的更高效替代方案。DV 以 PUFFIN 文件格式存储，使用位图（bitmap）表示被删除的行位置，相比传统的位置删除文件（逐行记录 file_path + pos）具有更好的压缩率和读取性能。

在此之前，Flink IcebergSink 的 `BaseTaskWriter` 及其子类只支持传统的位置删除文件写入方式。当表的格式版本为 V3+ 时，sink 应该能自动切换到 DV 写入模式，生成 PUFFIN 格式的 DV 文件而非传统的位置删除文件。

本提交的核心是在 `BaseTaskWriter` 中引入 `PartitioningDVWriter`，当 `useDv` 标志为 true 时，位置删除通过 DV writer 写入。同时适配 Flink sink 的各层 writer 和 manifest 工具，确保 DV 文件能正确地通过 manifest 提交。

## 如何达成设计目的

1. **`BaseTaskWriter` 核心改造**：新增带 `useDv` 参数的构造器，当 `useDv` 为 true 时创建 `PartitioningDVWriter`。`BaseEqualityDeltaWriter` 新增接受 `PartitioningDVWriter` 的构造器——当传入 DV writer 时，位置删除直接写入 DV writer 而非传统的 `SortingPositionOnlyDeleteWriter`。新增 `WrappedPositionDeleteWriter` 适配器类将 `SortingPositionOnlyDeleteWriter` 包装为 `PartitioningWriter` 接口。重写 `close()` 方法，在关闭时将 DV writer 的结果（deleteFiles 和 referencedDataFiles）收集到已完成列表中。

2. **Flink writer 适配**：`BaseDeltaTaskWriter`、`PartitionedDeltaWriter`、`UnpartitionedDeltaWriter` 的构造器新增 `useDv` 参数，透传给 `BaseTaskWriter`。`RowDataTaskWriterFactory` 根据表的格式版本（`TableUtil.formatVersion(table) > 2`）决定是否启用 DV。各 writer 的 `close()` 方法调用 `super.close()` 确保 DV writer 被正确关闭。

3. **Manifest 适配**：`FlinkManifestUtil.writeCompletedFiles` 新增 `formatVersion` 参数，用于在写删除 manifest 时指定格式版本（V3 表的删除 manifest 需要用 V3 格式）。`IcebergFilesCommitter`、`IcebergWriteAggregator` 调用时传入 `TableUtil.formatVersion(table)`。

4. **Dynamic Sink 限制**：`DynamicWriter` 显式检查 V3+ 表不允许 upsert 模式（因为 dynamic sink 暂不支持 DV），`DynamicWriteResultAggregator` 传 `formatVersion=2`（dynamic sink 硬编码为 V2）。

5. **测试扩展**：`TestTaskEqualityDeltaWriter`、`TestDeltaTaskWriter`、`TestFlinkUpsert` 等测试参数化扩展到 V2 和 V3+，验证 V2 生成传统位置删除文件、V3+ 生成 PUFFIN 格式 DV 文件。

## 修改详情

### `core/src/main/java/org/apache/iceberg/io/BaseTaskWriter.java` (+106/-8 lines)

**修改目的**：在核心 task writer 中支持 DV 写入。

**工作逻辑**：
- 新增带 `useDv` 参数的构造器，`useDv` 为 true 时创建 `PartitioningDVWriter` 实例。
- 新增 `dvFileWriter()` 方法返回 DV writer。
- 重写 `close()` 方法：若 DV writer 非空，关闭并收集其 `DeleteWriteResult` 中的 deleteFiles 和 referencedDataFiles。
- `BaseEqualityDeltaWriter` 新增 `partitionKey` 字段和接受 `PartitioningDVWriter` 的构造器。当传入 DV writer 时，`posDeleteWriter` 直接使用该 DV writer；否则通过 `createPosDeleteWriter()` 创建传统的 `WrappedPositionDeleteWriter`。`writePosDelete()` 调用 `posDeleteWriter.write(positionDelete, spec, partitionKey)` 传入分区信息。
- 新增 `closePosDeleteWriter` 标志：只有当 posDeleteWriter 是本地创建的（非共享 DV writer）时才在 `close()` 中关闭它，避免重复关闭共享的 DV writer。
- 新增 `WrappedPositionDeleteWriter` 内部类：继承 `SortingPositionOnlyDeleteWriter` 并实现 `PartitioningWriter` 接口，将无分区参数的 `write()` 适配为带分区参数的 `write(positionDelete, spec, partition)`。

### `data/src/test/java/org/apache/iceberg/io/TestTaskEqualityDeltaWriter.java` (+123/-10 lines)

**修改目的**：扩展测试覆盖 V3+ 格式版本，验证 DV 文件生成。

**工作逻辑**：参数化扩展为遍历 `TestHelpers.V2_AND_ABOVE` 的格式版本。在验证位置删除文件时，V2 检查传统 path+pos 格式记录，V3+ 检查 PUFFIN 格式 DV 文件——验证 `posDeleteFile.format() == FileFormat.PUFFIN`，并通过 `readDVFile()` 方法读取 DV 文件验证 `PositionDeleteIndex` 的 cardinality 和删除的行位置。`GenericTaskDeltaWriter` 构造器新增 `useDv` 参数（`formatVersion > 2`），`close()` 调用 `super.close()` 确保 DV writer 被关闭。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/BaseDeltaTaskWriter.java` (+10/-3 lines)

**修改目的**：传递 `useDv` 参数并适配 DV writer。

**工作逻辑**：构造器新增 `useDv` 参数传给 `BaseTaskWriter`。`RowDataDeltaWriter` 构造器新增 `PartitioningDVWriter<RowData>` 参数，传给 `BaseEqualityDeltaWriter`。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkManifestUtil.java` (+8/-2 lines)

**修改目的**：`writeCompletedFiles` 新增 `formatVersion` 参数。

**工作逻辑**：删除 manifest 的写入从硬编码 `FORMAT_V2` 改为使用传入的 `formatVersion`，确保 V3 表的删除 manifest 使用正确的格式版本。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergFilesCommitter.java` (+6/-1 lines)

**修改目的**：传递表格式版本给 `writeCompletedFiles`。

**工作逻辑**：调用 `FlinkManifestUtil.writeCompletedFiles` 时传入 `TableUtil.formatVersion(table)`。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergWriteAggregator.java` (+6/-1 lines)

**修改目的**：同 IcebergFilesCommitter，传递格式版本。

**工作逻辑**：调用 `FlinkManifestUtil.writeCompletedFiles` 时传入 `TableUtil.formatVersion(table)`。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/PartitionedDeltaWriter.java` (+9/-3 lines)

**修改目的**：适配 `useDv` 参数并确保 DV writer 正确关闭。

**工作逻辑**：构造器新增 `useDv` 参数。创建 `RowDataDeltaWriter` 时传入 `dvFileWriter()`。`close()` 方法新增 `super.close()` 调用，确保父类的 DV writer 被关闭。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/RowDataTaskWriterFactory.java` (+9/-2 lines)

**修改目的**：根据表格式版本决定是否启用 DV。

**工作逻辑**：新增 `useDv` 字段，在 `initialize()` 中通过 `TableUtil.formatVersion(table) > 2` 设置。创建 `UnpartitionedDeltaWriter` 和 `PartitionedDeltaWriter` 时传入 `useDv`。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/UnpartitionedDeltaWriter.java` (+9/-3 lines)

**修改目的**：适配 `useDv` 参数并确保 DV writer 正确关闭。

**工作逻辑**：构造器新增 `useDv` 参数。创建 `RowDataDeltaWriter` 时传入 `dvFileWriter()`。`close()` 新增 `super.close()` 调用。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicWriteResultAggregator.java` (+3/-1 lines)

**修改目的**：为 dynamic sink 硬编码 formatVersion=2（暂不支持 DV）。

**工作逻辑**：调用 `FlinkManifestUtil.writeCompletedFiles` 时传入 `2`。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicWriter.java` (+6/-0 lines)

**修改目的**：禁止 V3+ 表在 dynamic sink 中使用 upsert 模式。

**工作逻辑**：新增 `Preconditions.checkArgument(!(TableUtil.formatVersion(table) > 2), "Dynamic Sink writer does not support upsert mode in tables (V3+)")` 校验。

### 测试文件

- `TestFlinkUpsert.java` (+24/-14)：参数化扩展到 V2 和 V3+，formatVersion 通过参数传入。
- `TestCommittableToTableChangeConverter.java` (+9/-2)：`writeCompletedFiles` 调用传入 `TableUtil.formatVersion(table)`。
- `TestDeltaTaskWriter.java` (+16/-7)：参数化扩展到 V2 和 V3+。
- `TestFlinkManifest.java` (+10/-3)：`writeCompletedFiles` 调用传入 `TableUtil.formatVersion(table)`。

## 总结

本提交为 Flink IcebergSink 引入了 Deletion Vector（DV）写入支持，使 V3+ 格式的表能自动使用 PUFFIN 格式的 DV 文件替代传统的位置删除文件。核心设计是在 `BaseTaskWriter` 中引入可插拔的 `PartitioningDVWriter`，通过 `useDv` 标志控制是否启用 DV 模式。Flink sink 根据 `TableUtil.formatVersion(table) > 2` 自动判断是否启用 DV。Dynamic Sink 暂不支持 DV，通过显式校验禁止 V3+ 表的 upsert 模式。测试全面覆盖了 V2（传统位置删除）和 V3+（DV）两种模式。注意此提交针对 Flink 2.1，2779 是向 Flink 1.20/2.0 的 backport。
