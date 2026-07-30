# 提交 3338：Core: Don't fail when bulk deleting metadata in CatalogUtil (#15464)

## 提交信息

- **序号**：3338 / 4088
- **哈希**：6b2e8045398918d3392518d005a04d0e9c9219ab
- **短哈希**：6b2e80453
- **日期**：2026-03-02 19:30:33 +0100
- **作者**：Ayson Chang
- **提交说明**：Core: Don't fail when bulk deleting metadata in CatalogUtil (#15464)
- **PR/Issue**：#15464

## 总体目的

本提交修复了 `CatalogUtil.deleteRemovedMetadataFiles` 在使用支持批量删除（`SupportsBulkOperations`）的 FileIO 时，批量删除失败会导致整个操作抛异常的问题，使其改为"尽力删除、失败仅告警"的行为，与删除 manifest、manifest list 等其他元数据文件的处理方式保持一致。

背景是：Iceberg 在表提交后会做元数据日志清理——当 `write.metadata.delete-after-commit.enabled` 开启时，`deleteRemovedMetadataFiles` 负责删除已不在 `metadata.previousFiles()` 中的旧元数据 JSON 文件。原实现区分两条路径：若 FileIO 支持批量操作，直接调用 `((SupportsBulkOperations) io).deleteFiles(...)`；否则用 `Tasks.foreach` 逐个删除，并配置了 `suppressFailureWhenFinished()` 与 `onFailure` 日志告警。问题在于批量路径没有做任何异常捕获——一旦底层批量删除抛出 `RuntimeException`（例如对象存储上的部分文件已不存在、瞬时网络错误等），异常会直接向上传播，可能导致本应成功的提交操作被误判为失败，或在使用该清理逻辑的调用方处引发非预期中断。

相比之下，删除 manifest、manifest list 等文件早已统一走 `CatalogUtil.deleteFiles(io, files, type, concurrent)` 辅助方法，该方法在批量路径中用 try-catch 包裹 `bulkIO.deleteFiles(files)`，失败时仅 `LOG.warn` 不抛出；在非批量路径中同样抑制失败。本提交把元数据文件删除也改为复用该辅助方法，消除行为不一致。

## 如何达成设计目的

整体思路是复用已有的 `deleteFiles(FileIO, Iterable<String>, String, boolean)` 辅助方法替换 `deleteRemovedMetadataFiles` 中手写的双分支逻辑。改动集中在 `CatalogUtil.java` 的该 方法内：把原先的 `if (io instanceof SupportsBulkOperations) { 直接批量删除 } else { Tasks 逐个删除并抑制失败 }` 替换为一次 `deleteFiles(io, 文件路径集合, "metadata", true)` 调用。该辅助方法内部已统一处理批量/非批量两种 FileIO，且两条路径都抑制删除失败（批量路径 try-catch 后告警，非批量路径 `suppressFailureWhenFinished` + 告警）。同时新增测试用 Mockito 模拟批量删除抛异常，验证调用不再传播异常。

## 修改详情

### `core/src/main/java/org/apache/iceberg/CatalogUtil.java` (+8/-15 lines)

**修改目的**：让元数据文件批量删除失败时不传播异常，仅记录告警。

**工作逻辑**：
`deleteRemovedMetadataFiles` 方法在计算出 `removedPreviousMetadataFiles`（需删除的旧元数据日志条目集合，已排除 `metadata.previousFiles()` 中仍需保留的）后，原先内联了两条删除路径：

- 批量路径：`((SupportsBulkOperations) io).deleteFiles(Iterables.transform(removedPreviousMetadataFiles, MetadataLogEntry::file))`——无异常捕获，失败即抛出。
- 非批量路径：`Tasks.foreach(...).noRetry().suppressFailureWhenFinished().onFailure(...告警...).run(...)`——抑制失败。

修改后统一改为：
```java
deleteFiles(
    io,
    removedPreviousMetadataFiles.stream()
        .map(TableMetadata.MetadataLogEntry::file)
        .collect(Collectors.toSet()),
    "metadata",
    true);
```
复用的 `deleteFiles(io, files, type, concurrent)` 辅助方法行为为：若 `io instanceof SupportsBulkOperations`，则 `try { bulkIO.deleteFiles(files); } catch (RuntimeException e) { LOG.warn("Failed to bulk delete {} files", type, e); }`（不抛出）；否则在 `concurrent=true` 时用线程池并发逐个删除并 `suppressFailureWhenFinished`。因此元数据文件删除从此变为"尽力而为"，与 manifest 等清理一致。另外把原先 `Iterables.transform` 的惰性视图改为 `collect(Collectors.toSet())`，顺带去重。新增了 `java.util.stream.Collectors` 的 import。

### `core/src/test/java/org/apache/iceberg/TestCatalogUtil.java` (+38 lines)

**修改目的**：验证批量删除元数据文件失败时不再抛异常。

**工作逻辑**：
新增测试 `noFailureWhenBulkDeletingMetadataFiles`。用 `mock(FileIO.class, withSettings().extraInterfaces(SupportsBulkOperations.class))` 构造一个支持批量操作的 FileIO mock，并通过 `doThrow(new RuntimeException("Simulated bulk delete failure")).when((SupportsBulkOperations) io).deleteFiles(any())` 让批量删除抛异常。构造两个 `MetadataLogEntry`（v1.json、v2.json）放入 `base.previousFiles()`，令 `metadata.previousFiles()` 为空（这样两个文件都应被删除），并开启 `METADATA_DELETE_AFTER_COMMIT_ENABLED`。最后用 `assertThatCode(() -> CatalogUtil.deleteRemovedMetadataFiles(io, base, metadata)).doesNotThrowAnyException()` 断言调用不抛任何异常。该测试直接覆盖了修复前的 bug 路径——批量删除抛异常会被传播——确认修复后异常被辅助方法内部捕获并吞掉。

## 总结

本提交将 `CatalogUtil.deleteRemovedMetadataFiles` 中手写的批量/非批量双分支删除逻辑替换为复用已有的 `deleteFiles` 辅助方法，使批量删除元数据文件失败时由"抛异常中断操作"变为"仅告警并继续"，与 manifest 等其他元数据清理行为一致，避免因对象存储瞬时错误或文件不存在等非致命问题导致表提交或清理流程被误判失败。
