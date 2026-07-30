# 提交 2414：Core: Use Bulk deletion for cleaning up uncommitted files in BaseTransaction (#13653)

## 提交信息

- **序号**：2414 / 4088
- **哈希**：c7e367494d1646505b7c162886ca1b1c45a33306
- **短哈希**：c7e367494
- **日期**：2025-07-25 09:34:46 +0200
- **作者**：Hongyue/Steve Zhang
- **提交说明**：Core: Use Bulk deletion for cleaning up uncommitted files in BaseTransaction (#13653)
- **PR/Issue**：#13653

## 总体目的

本提交优化了 `BaseTransaction` 中清理未提交文件（uncommitted files）的方式，当 FileIO 支持批量删除操作时，优先使用批量删除（bulk deletion）而非逐个删除。

在 Iceberg 的事务处理过程中，当事务提交失败或被回滚时，需要清理在事务过程中生成但未被提交的文件（如 manifest 文件、manifest list 等）。此前，`BaseTransaction` 中有 4 处地方使用 `Tasks.foreach(...).run(ops.io()::deleteFile)` 逐个删除这些文件。对于支持批量删除的存储系统（如 S3），逐个删除会产生大量独立的删除请求，效率低下且可能触发限流。

本提交将这些清理逻辑统一提取为一个 `deleteUncommittedFiles` 方法，当 FileIO 实现了 `SupportsBulkOperations` 接口时，使用 `deleteFiles` 批量删除；否则回退到原有的逐个删除方式（并额外使用线程池并行执行以提高效率）。

## 如何达成设计目的

1. 提取统一的 `deleteUncommittedFiles(Iterable<String> paths)` 方法，封装所有未提交文件清理逻辑
2. 在该方法中，首先检查 FileIO 是否为 `SupportsBulkOperations` 实例：
   - 如果是，调用 `deleteFiles(paths)` 进行批量删除，并捕获 `BulkDeletionFailureException` 和其他 `RuntimeException`，记录警告日志但不抛出异常（清理失败不应影响主流程）
   - 如果不是，使用 `Tasks.foreach(paths).executeWith(ThreadPools.getWorkerPool())` 并行逐个删除，同样抑制失败
3. 将原有 4 处内联的清理逻辑替换为调用 `deleteUncommittedFiles`
4. 对于需要过滤已提交文件的场景，先使用 stream 过滤出未提交的文件集合，再调用统一方法

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseTransaction.java` (+39/-18 lines)

**修改目的**：统一未提交文件清理逻辑，支持批量删除。

**工作逻辑**：
- 新增 `deleteUncommittedFiles` 私有方法：检查 FileIO 是否支持批量操作，支持则批量删除，不支持则并行逐个删除。两种路径都抑制失败并记录警告日志。
- `createTableTransaction` 的 finally 块：将原来的 `Tasks.foreach(deletedFiles)...run(ops.io()::deleteFile)` 替换为 `deleteUncommittedFiles(deletedFiles)`
- `replaceTableTransaction` 的 finally 块：同上替换
- `commitTransaction` 中处理已提交文件过滤的逻辑：原来在 `Tasks.foreach` 的 lambda 中判断文件是否在 `committedFiles` 集合中，现在改为先用 stream 过滤出 `uncommittedFiles`，再调用 `deleteUncommittedFiles(uncommittedFiles)`
- `close` 方法中的清理逻辑：同上替换

### `core/src/test/java/org/apache/iceberg/TestTables.java` (+16/-0 lines)

**修改目的**：将测试用的 `TestBulkLocalFileIO` 类从 `TestRemoveSnapshots` 移到 `TestTables` 中，以便多个测试类共享。

**工作逻辑**：新增 `TestBulkLocalFileIO` 内部类，继承 `TestTables.LocalFileIO` 并实现 `SupportsBulkOperations` 接口。其 `deleteFile` 方法抛出异常（确保测试中不会误调用单文件删除），`deleteFiles` 方法也抛出异常（预期会被 mock）。

### `core/src/test/java/org/apache/iceberg/TestRemoveSnapshots.java` (+2/-21 lines)

**修改目的**：将 `TestBulkLocalFileIO` 类移到 `TestTables` 中，更新引用。

**工作逻辑**：删除原有的 `TestBulkLocalFileIO` 私有内部类，将所有引用从 `TestBulkLocalFileIO` 改为 `TestTables.TestBulkLocalFileIO`。

### `core/src/test/java/org/apache/iceberg/TestTransaction.java` (+39/-0 lines)

**修改目的**：新增测试验证事务失败时使用批量删除清理未提交文件。

**工作逻辑**：新增 `testTransactionFailureBulkDeletionCleanup` 测试：
- 创建 `TestBulkLocalFileIO` 的 spy，mock `deleteFiles` 方法为空操作
- 创建使用该 BulkIO 的表
- 在事务中执行 append 操作生成 manifest 和 manifest list
- 通过 `failCommits(1)` 使事务提交失败
- 验证提交抛出 `CommitFailedException`
- 通过 Mockito verify 确认 `deleteFiles` 被调用，且参数包含生成的 manifest 和 manifest list 路径

## 总结

本提交优化了事务未提交文件的清理效率，通过统一提取清理方法并在支持时使用批量删除，减少了存储系统（特别是 S3 等）的删除请求数量。同时为非批量 FileIO 路径增加了线程池并行执行以提高效率。代码结构也更清晰，消除了 4 处重复的清理逻辑。
