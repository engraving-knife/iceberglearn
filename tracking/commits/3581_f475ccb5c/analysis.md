# 提交 3581：ORC: Fix connection leak in OrcIterable (#16086)

## 提交信息

- **序号**：3581 / 4088
- **哈希**：f475ccb5c579b8a1d4842507b12e854161dea7af
- **短哈希**：f475ccb5c
- **日期**：2026-04-24 15:49:05 +0200
- **作者**：Denys Kuzmenko
- **提交说明**：ORC: Fix connection leak in OrcIterable (#16086)
- **PR/Issue**：#16086

## 总体目的

该提交修复了 `OrcIterable` 中的资源/连接泄漏问题。`OrcIterable` 继承自 `CloseableGroup`，用于管理 ORC 文件读取过程中的可关闭资源。在创建 `VectorizedRowBatchIterator` 后，代码未将其注册到 `CloseableGroup` 中，导致当 `OrcIterable` 被关闭时，`rowBatchIterator` 及其内部的 ORC `RecordReader` 不会被关闭，从而泄漏底层的输入流和文件句柄。

这在频繁读取 ORC 文件的场景下（如查询扫描大量文件）会导致文件句柄耗尽，最终引发 "Too many open files" 错误。该修复通过在创建 `rowBatchIterator` 后立即调用 `addCloseable(rowBatchIterator)` 将其注册到 `CloseableGroup`，确保它在 `OrcIterable` 关闭时也被正确关闭。

## 如何达成设计目的

在 `OrcIterable` 构造函数中，创建 `VectorizedRowBatchIterator` 后立即调用 `addCloseable(rowBatchIterator)` 将其注册到父类 `CloseableGroup` 的资源管理列表中。这样当 `OrcIterable.close()` 被调用时，`CloseableGroup` 会自动关闭所有注册的可关闭资源，包括 `rowBatchIterator`。

## 修改详情

### `orc/src/main/java/org/apache/iceberg/orc/OrcIterable.java` (+2/-0 lines)

**修改目的**：将 rowBatchIterator 注册到 CloseableGroup。

**工作逻辑**：
```java
VectorizedRowBatchIterator rowBatchIterator =
    newOrcIterator(file, readOrcSchema, start, length, orcFileReader, sarg, recordsPerBatch);
addCloseable(rowBatchIterator);  // 新增：注册到 CloseableGroup
```
`addCloseable` 是 `CloseableGroup` 的方法，将 `rowBatchIterator` 添加到待关闭资源列表。当 `OrcIterable.close()` 被调用时，所有注册的资源都会被关闭。

### `orc/src/test/java/org/apache/iceberg/orc/TestOrcIterableResourceCleanup.java` (+133/-0 lines, new file)

**修改目的**：验证资源清理的正确性。

**工作逻辑**：
- `testClosingIterableClosesAllStreams`：写入测试 ORC 文件，通过 Mockito spy 监控所有 `SeekableInputStream`，创建 OrcIterable 读取数据后关闭，验证所有流都被关闭一次。
- `testClosingIterableClosesIteratorResources`：模拟 5 轮读取，每轮创建 OrcIterable 并 drain iterator（不显式关闭 iterator），然后关闭 iterable。验证所有流都被正确关闭。注释指出如果没有 `addCloseable(rowBatchIterator)`，`VectorizedRowBatchIterator` 及其 `RecordReader` 不会被关闭，导致 ORC 输入流/文件句柄泄漏。

## 总结

该提交修复了 ORC 读取器中的资源泄漏 bug，通过一行代码的修复（`addCloseable(rowBatchIterator)`）确保 `VectorizedRowBatchIterator` 在 `OrcIterable` 关闭时被正确关闭。这是一个重要的 bug 修复，防止了文件句柄泄漏和潜在的 "Too many open files" 问题。测试通过 Mockito spy 验证了所有输入流被正确关闭。
