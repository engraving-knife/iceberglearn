# 提交 2312：Core, Spark: Propagate orphaned delete files when rewriting data files (#13245)

## 提交信息

- **序号**：2312 / 4088
- **哈希**：8033b32bc5b9c105f09ba1c7179eb7f3128466b9
- **短哈希**：8033b32bc
- **日期**：2025-07-03 09:05:51 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core, Spark: Propagate orphaned delete files when rewriting data files (#13245)
- **PR/Issue**：#13245

## 总体目的

这个提交解决了一个关于"孤立删除文件"（orphaned delete files）的重要问题。在 Iceberg 中，当数据文件被删除时，引用该数据文件的删除文件（特别是 DV，即 Deletion Vector 删除向量）会变成"孤立的"——它们引用的数据文件已不存在。

在此提交之前，当执行数据文件重写（RewriteDataFiles）操作时，旧数据文件被新数据文件替换，但引用旧数据文件的 DV 并不会被同时删除。这些孤立的 DV 会残留在表中，需要通过单独的 `RemoveDanglingDeletes` 操作来清理。这不仅增加了操作复杂度，还可能导致读取性能下降和存储空间浪费。

此提交的核心改进是：在数据文件重写过程中，直接将引用被重写数据文件的 DV 一起传播（删除），从而避免产生孤立 DV。这涉及修改 API 接口、Core 实现以及 Spark 3.4/3.5/4.0 三个版本的集成代码。

## 如何达成设计目的

整体设计思路如下：

1. **API 层扩展**：在 `OverwriteFiles` 接口中新增 `deleteFiles(DataFileSet, DeleteFileSet)` 方法，允许在删除数据文件时同时删除对应的删除文件。在 `RewriteDataFiles.FileGroupRewriteResult` 中新增 `removedDeleteFilesCount()` 指标。

2. **Core 层实现**：
   - `BaseOverwriteFiles` 实现新的 `deleteFiles` 方法，遍历删除数据文件和删除文件
   - `RewriteFileGroup` 新增 `danglingDVs()` 方法，通过过滤 `ContentFileUtil::isDV` 从扫描任务中提取 DV
   - `RewriteDataFilesCommitManager` 在提交时收集所有 dangling DVs 并通过 `rewrite.deleteFile()` 删除

3. **Spark 层集成**：
   - `SparkWrite` 中的 overwrite 操作现在也会收集并删除 dangling DVs
   - `RewriteDataFilesSparkAction` 调整结果统计，将重写时删除的 DV 数量与后续 `RemoveDanglingDeletes` 删除的数量合并统计

4. **测试验证**：新增 `removeDanglingDVsFromDeleteManifest` 测试，验证重写后删除清单中没有孤立 DV，并调整了多个现有测试的预期快照数量。

## 修改详情

### `api/src/main/java/org/apache/iceberg/OverwriteFiles.java` (+15/-0 lines)

**修改目的**：扩展 OverwriteFiles API，支持批量删除数据文件及其关联的删除文件。

**工作逻辑**：新增 `deleteFiles(DataFileSet dataFilesToDelete, DeleteFileSet deleteFilesToDelete)` default 方法，默认抛出 `UnsupportedOperationException`。这是向后兼容的接口扩展方式。

### `api/src/main/java/org/apache/iceberg/actions/RewriteDataFiles.java` (+8/-2 lines)

**修改目的**：在结果接口中新增已移除删除文件数量的统计指标。

**工作逻辑**：
- `Result` 接口的 `removedDeleteFilesCount()` 从硬编码返回 0 改为聚合所有 `FileGroupRewriteResult` 的 `removedDeleteFilesCount` 之和
- `FileGroupRewriteResult` 接口新增 `default int removedDeleteFilesCount()` 返回 0

### `core/src/main/java/org/apache/iceberg/BaseOverwriteFiles.java` (+15/-0 lines)

**修改目的**：实现新的 `deleteFiles` 批量删除方法。

**工作逻辑**：遍历 `dataFilesToDelete` 调用 `deleteFile(DataFile)`，遍历 `deleteFilesToDelete` 调用 `delete(DeleteFile)`。

### `core/src/main/java/org/apache/iceberg/actions/BaseRewriteDataFiles.java` (+6/-0 lines)

**修改目的**：在 `FileGroupRewriteResult` 的 Immutable 实现中添加 `removedDeleteFilesCount` 默认值。

### `core/src/main/java/org/apache/iceberg/actions/RewriteDataFilesCommitManager.java` (+11/-6 lines)

**修改目的**：在重写提交时收集并删除 dangling DVs。

**工作逻辑**：创建 `DeleteFileSet danglingDVs`，从每个 `RewriteFileGroup` 收集 `danglingDVs()`，然后通过 `rewrite.deleteFile()` 逐个删除。重构了提交逻辑，不再使用 `rewriteFiles(rewrittenDataFiles, addedDataFiles)` 一次性方法，而是分别调用 `deleteFile` 和 `addFile`。

### `core/src/main/java/org/apache/iceberg/actions/RewriteFileGroup.java` (+10/-0 lines)

**修改目的**：新增提取 dangling DVs 的能力。

**工作逻辑**：`danglingDVs()` 方法从 `fileScanTasks()` 中提取所有 deletes，通过 `ContentFileUtil::isDV` 过滤出 DV 类型（而非 equality delete），收集到 `DeleteFileSet` 中。同时在 `rewriteResult()` 中设置 `removedDeleteFilesCount`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkWrite.java` (+40/-16 lines, 各版本相同)

**修改目的**：在 Spark 的 overwrite 写入路径中传播 dangling DVs 的删除。

**工作逻辑**：
- `overwrittenFiles()` 返回类型从 `List<DataFile>` 改为 `DataFileSet`
- 新增 `danglingDVs()` 方法，从扫描任务中提取 DV
- overwrite 提交时调用 `overwriteFiles.deleteFiles(overwrittenFiles, danglingDVs)` 同时删除数据和删除文件
- `files()` 方法返回类型从 `List<DataFile>` 改为 `DataFileSet`
- abort 清理路径也相应调整

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteDataFilesSparkAction.java` (+9/-3 lines, 各版本相同)

**修改目的**：调整结果统计以合并重写期间和后续 RemoveDanglingDeletes 删除的 DV 数量。

**工作逻辑**：先构建基础结果，如果启用 `removeDanglingDeletes`，则将后续删除的 DV 数量加到结果中已有的 `removedDeleteFilesCount` 上。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (+82/-3 lines, 各版本相同)

**修改目的**：新增测试验证重写后无孤立 DV，并调整现有测试的预期值。

**工作逻辑**：`removeDanglingDVsFromDeleteManifest` 测试创建带有 DV 的表，执行重写后验证：重写的数据文件数和删除的 DV 数匹配、表中不再有删除文件、删除清单中不存在孤立 DV（即每个 DV 引用的数据文件路径仍然有效）。多个现有测试调整了预期快照数（v3 少一个快照因为重写时已删除 DV）。

## 总结

这是一个重要的功能改进提交，解决了数据文件重写时孤立 DV 残留的问题。通过在重写过程中直接传播删除 dangling DVs，避免了需要单独执行 RemoveDanglingDeletes 操作的复杂度。变更涉及 API 接口扩展、Core 层实现和 Spark 3.4/3.5/4.0 三个版本的集成代码，共修改 15 个文件。该改进特别有利于使用 DV（format version >= 3）的表的维护效率。
