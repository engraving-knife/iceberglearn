# 提交 1898：Core: Bulk deletions in RemoveSnapshots (#11837)

## 提交信息

- **序号**：1898 / 4088
- **哈希**：845ef51498bd2fce5f7e7de71feca60db939c8b2
- **短哈希**：845ef5149
- **日期**：2025-03-21 14:21:22 +0100
- **作者**：gaborkaszab
- **提交说明**：Core: Bulk deletions in RemoveSnapshots (#11837)
- **PR/Issue**：#11837

## 总体目的

这个提交为 Iceberg 的快照过期清理（`RemoveSnapshots`/expire snapshots）操作添加了批量删除文件的能力。

在快照过期时，Iceberg 需要删除不再被任何快照引用的数据文件、manifest 文件和 manifest list 文件。此前，这些文件是逐个删除的——对每个文件单独调用 `FileIO.deleteFile(path)`。当有过期文件数量较多时，逐个删除会产生大量的文件系统/RPC 调用，效率低下。

一些 `FileIO` 实现（如 S3、HDFS 等）支持批量删除操作（通过 `SupportsBulkOperations` 接口），可以一次调用删除多个文件。本提交让 `RemoveSnapshots` 在检测到 `FileIO` 支持批量操作时，自动使用批量删除而非逐个删除，从而显著提升清理效率，特别是在云存储环境中。

## 如何达成设计目的

整体设计思路是在 `FileCleanupStrategy.deleteFiles()` 方法中添加分支逻辑：

1. 如果 `FileIO` 实现了 `SupportsBulkOperations` 接口且没有自定义的 `deleteFunc`，则调用 `deleteFiles(pathsToDelete)` 进行批量删除。
2. 否则，回退到原有的逐个删除路径（使用 `Tasks.foreach` 并行执行）。

同时重构了 `RemoveSnapshots` 中的默认删除函数：将原来在 `RemoveSnapshots` 中定义的 `defaultDelete` Consumer 移到 `FileCleanupStrategy` 中作为 `defaultDeleteFunc`，并将 `deleteFunc` 的默认值从 `defaultDelete` 改为 `null`，以便 `deleteFiles()` 方法能区分"使用默认逐个删除"和"使用自定义删除函数"两种情况。

## 修改详情

### `core/src/main/java/org/apache/iceberg/FileCleanupStrategy.java` (修改, +44/-8 lines)

**修改目的**：添加批量删除逻辑，并引入默认删除函数。

**工作逻辑**：

1. 新增 `defaultDeleteFunc` 字段，是一个调用 `fileIO.deleteFile(file)` 的 Consumer。

2. 重写 `deleteFiles` 方法：
   - 当 `deleteFunc == null` 且 `fileIO instanceof SupportsBulkOperations` 时，调用 `((SupportsBulkOperations) fileIO).deleteFiles(pathsToDelete)` 进行批量删除。捕获 `BulkDeletionFailureException` 和 `RuntimeException`，记录警告日志但不中断流程。
   - 否则，使用原有逻辑：选择 `defaultDeleteFunc`（当 deleteFunc 为 null）或自定义 `deleteFunc`，通过 `Tasks.foreach(pathsToDelete).executeWith(deleteExecutorService).retry(3)...` 逐个删除。新增 `.stopOnFailure()` 调用。

### `core/src/main/java/org/apache/iceberg/RemoveSnapshots.java` (修改, +10/-2 lines)

**修改目的**：移除默认删除函数定义，改为使用 null 作为默认值。

**工作逻辑**：移除了 `defaultDelete` Consumer 字段及其定义。将 `deleteFunc` 的默认值从 `defaultDelete` 改为 `null`。这样 `FileCleanupStrategy.deleteFiles()` 可以通过判断 `deleteFunc == null` 来决定是否使用批量删除。

### `core/src/test/java/org/apache/iceberg/TestRemoveSnapshots.java` (修改, +94 lines)

**修改目的**：添加批量删除的测试用例。

**工作逻辑**：

1. 新增 `TestBulkLocalFileIO` 内部类，实现 `SupportsBulkOperations` 接口。其 `deleteFile()` 方法抛出异常（确保不会被逐个删除调用），`deleteFiles()` 方法抛出异常（用于被 mock）。

2. 新增三个测试方法：
   - `testRemoveFromTableWithBulkIO`：验证使用批量 IO 时文件被批量删除，`deleteFiles` 被调用 3 次（分别对应数据文件、manifest lists、manifests）。
   - `testBulkDeletionWithBulkDeletionFailureException`：验证批量删除抛出 `BulkDeletionFailureException` 时不会中断流程。
   - `testBulkDeletionWithRuntimeException`：验证批量删除抛出 `RuntimeException` 时也不会中断流程。

3. `runBulkDeleteTest` 辅助方法：创建表、提交文件、删除文件、过期快照，验证 `deleteFiles` 被正确调用且快照被正确清理。

## 总结

本提交为快照过期清理操作添加了批量删除文件的能力。当 `FileIO` 支持 `SupportsBulkOperations` 时，自动使用批量删除替代逐个删除，显著提升云存储场景下的清理效率。批量删除失败时会被优雅处理（记录日志但继续执行），不影响整体快照过期流程。
