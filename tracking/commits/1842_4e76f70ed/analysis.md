# 提交 1842：Spark: Rewrite V2 deletes to V3 DVs (#12250)

## 提交信息

- **序号**：1842 / 4088
- **哈希**：4e76f70edbe32540d2724faa9545cd7914b807de
- **短哈希**：4e76f70ed
- **日期**：2025-03-11 15:28:57 -0700
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark: Rewrite V2 deletes to V3 DVs (#12250)
- **PR/Issue**：#12250

## 总体目的

本提交实现了将 Iceberg V2 格式的 position delete 文件重写为 V3 格式的 DV（Deletion Vector）文件的能力。Iceberg V3 引入了 Deletion Vector（DV）作为一种新的 position delete 存储格式，DV 使用 Puffin 文件格式存储紧凑的位图（Roaring Bitmap），相比 V2 的 Parquet/Avro 格式 position delete 文件，DV 在存储效率和读取性能上有显著优势。

在此之前，`RewritePositionDeleteFilesSparkAction` 明确禁止对 V3 表执行重写（`Preconditions.checkArgument(TableUtil.formatVersion(table) <= 2, "Cannot rewrite position deletes for V3 table")`），因为 V3 表可能已经使用 DV 格式。本提交移除了这一限制，并实现了以下能力：对于 V3 表，如果仍存在 V2 格式的 position delete 文件（非 Puffin 格式），则将它们重写为 DV 格式的 Puffin 文件；如果所有 position delete 文件已经是 DV 格式，则跳过重写。

核心改动包括：在 `RewritePositionDeleteFilesSparkAction` 中新增 `requiresRewriteToDVs()` 方法检测是否存在非 Puffin 格式的 position delete 文件；在 `SparkPositionDeletesRewrite` 中新增 `DVWriter` 内部类，使用 `PartitioningDVWriter` 将 position delete 写为 DV 格式；修改 `createWriter` 方法根据表的 format version 选择使用 `DVWriter`（V3）还是原有的 `DeleteWriter`（V2）。

## 如何达成设计目的

整体设计思路是"按 format version 分流 writer + 提前检测跳过"。在 action 层面，`RewritePositionDeleteFilesSparkAction` 在执行前调用 `requiresRewriteToDVs()` 扫描 position deletes 元数据表，过滤出格式非 PUFFIN 的文件，如果不存在则说明所有 delete 已是 DV 格式，直接返回空结果。在 writer 层面，`SparkPositionDeletesRewrite.Write.createWriter` 通过 `TableUtil.formatVersion(table)` 获取格式版本，V3 表使用 `FileFormat.PUFFIN` 作为输出格式并创建 `DVWriter`，V2 表沿用原有 `DeleteWriter`。`DVWriter` 内部使用 `PartitioningDVWriter` 将 `(file_path, position)` 对写入 Puffin 格式的 DV 文件。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeletesRewrite.java` (修改, 144 lines)

**修改目的**：新增 DVWriter，按 format version 选择 writer 类型。

**工作逻辑**：

1. `createWriter` 方法：通过 `TableUtil.formatVersion(table)` 获取格式版本。`OutputFileFactory` 的格式从固定 `format` 改为 `formatVersion >= 3 ? FileFormat.PUFFIN : format`。然后按 format version 分流：V3 创建 `new DVWriter(table, deleteFileFactory, dsSchema, specId, partition)`；V2 沿用原有逻辑创建 `DeleteWriter`。

2. 新增 `DVWriter` 内部类（实现 `DataWriter<InternalRow>`）：
   - 持有 `PositionDelete<InternalRow> positionDelete`、`FileIO io`、`PartitionSpec spec`、`partition`、`PartitioningDVWriter<InternalRow> dvWriter` 等字段。
   - 构造函数：从 `dsSchema` 取得 `DELETE_FILE_PATH` 和 `DELETE_FILE_POS` 字段的序号；创建 `PartitioningDVWriter<>(deleteFileFactory, p -> null)`。
   - `write(InternalRow record)`：从 record 读取 file path 和 position，设置到 `positionDelete`，然后调用 `dvWriter.write(positionDelete, spec, partition)`。
   - `commit()`：close 后返回 `DeleteTaskCommit(allDeleteFiles())`。
   - `abort()`：close 后用 `SparkCleanupUtil.deleteTaskFiles` 清理已写文件。
   - `close()`：关闭 dvWriter 并设 closed 标志。
   - `allDeleteFiles()`：返回 `dvWriter.result().deleteFiles()`。

3. `DeleteWriter.write` 方法签名移除 `throws IOException`（与 DVWriter 的 `write` 签名统一）。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewritePositionDeleteFilesSparkAction.java` (修改, 34 lines)

**修改目的**：移除 V3 表限制，新增 DV 重写需求检测。

**工作逻辑**：

1. `execute` 方法：在 `validateAndInitOptions()` 之后新增检查：如果 `TableUtil.formatVersion(table) >= 3 && !requiresRewriteToDVs()`，则记录日志 "v2 deletes in {} have already been rewritten to v3 DVs" 并返回 `EMPTY_RESULT`。

2. 新增 `requiresRewriteToDVs()` 方法：创建 position deletes 元数据表的 `PositionDeletesBatchScan`，设置 baseTableFilter、caseSensitive、select `PositionDeletesTable.DELETE_FILE_PATH`、ignoreResiduals，然后 planFiles。通过 `CloseableIterable.transform` 将 task 转为 `PositionDeletesScanTask`，再通过 `CloseableIterable.filter` 过滤出 `file().format() != FileFormat.PUFFIN` 的 task。如果迭代器有下一个元素则返回 true（存在需要重写的 V2 delete 文件）。

3. `validateAndInitOptions` 方法：移除了 `Preconditions.checkArgument(TableUtil.formatVersion(table) <= 2, "Cannot rewrite position deletes for V3 table")` 这一限制。

### `core/src/main/java/org/apache/iceberg/actions/RewritePositionDeletesGroup.java` (修改, 3 lines)

**修改目的**：修复 `addedBytes()` 方法对 DV 文件大小的计算。

**工作逻辑**：将 `addedDeleteFiles.stream().mapToLong(DeleteFile::fileSizeInBytes).sum()` 改为 `addedDeleteFiles.stream().mapToLong(ScanTaskUtil::contentSizeInBytes).sum()`。DV 文件（Puffin 格式）的 `fileSizeInBytes` 可能不反映实际内容大小，`ScanTaskUtil.contentSizeInBytes` 能正确计算 DV 文件的内容大小。新增 `import org.apache.iceberg.util.ScanTaskUtil`。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/PositionDeletesRowReader.java` (修改, 1 line)

**修改目的**：添加 `@SuppressWarnings("resource")` 注解。

**工作逻辑**：在 `open` 方法上添加 `@SuppressWarnings("resource")` 注解并注释 "handled by BaseReader"，消除静态分析工具的资源泄漏告警。该方法创建的资源由基类 `BaseReader` 管理生命周期。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewritePositionDeleteFilesAction.java` (修改, 51 lines)

**修改目的**：新增 V3 表的 DV 重写测试。

**工作逻辑**：新增测试用例验证 V3 表上执行 rewrite position deletes 时，V2 格式的 delete 文件被正确重写为 DV 格式（Puffin 文件），并验证重写后的文件格式和内容正确。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestPositionDeletesTable.java` (修改, 313 lines)

**修改目的**：扩展 position deletes 表测试以覆盖 DV 格式。

**工作逻辑**：新增/修改大量测试用例，验证 V3 表上 position delete 的读写、DV 文件格式、以及与 V2 的兼容性。

## 小结

本提交实现了 Spark 3.5 中将 V2 position delete 重写为 V3 DV 的能力，是 Iceberg V3 格式演进的重要一步。改动涉及 core 和 spark/v3.5 两个模块、6 个文件（423 行新增、123 行删除），影响面较大。核心设计是按 format version 分流 writer，并通过提前检测避免不必要的重写。回迁到 1.4.x 时需注意：1.4.x 可能不支持 V3 格式和 DV（`PartitioningDVWriter`、`ScanTaskUtil.contentSizeInBytes` 等类需存在）；此改动依赖 V3 格式基础设施，若 1.4.x 不支持 V3 则无法回迁。
