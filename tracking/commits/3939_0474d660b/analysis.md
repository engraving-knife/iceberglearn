# 提交 3939：Flink: Fix file offset mismatch in DataIterator.seek() when files are skipped (#16929)

## 提交信息

- **序号**：3939 / 4088
- **哈希**：0474d660bf5594a7f87e4e8745f56c83a985ad03
- **短哈希**：0474d660b
- **日期**：2026-06-24 13:12:24 +0200
- **作者**：Yujiang Zhong
- **提交说明**：Flink: Fix file offset mismatch in DataIterator.seek() when files are skipped (#16929)
- **PR/Issue**：#16929

## 总体目的

这次提交修复了 Flink Iceberg 源读取器中 `DataIterator.seek()` 方法的一个 file offset 计算错误。该 bug 在使用 residual filter（残余过滤器，用于在分区裁剪后对记录进行行级过滤）时尤其严重。

问题描述：`DataIterator.seek(startingFileOffset, startingRecordOffset)` 用于从检查点恢复读取位置。在 seek 过程中，前 `startingFileOffset` 个文件会被跳过（通过 `tasks.next()` 迭代）。原本的代码在跳过文件后直接将 `fileOffset` 设置为 `startingFileOffset`，但实际上由于 residual filter 的存在，被跳过的文件数量可能少于 `startingFileOffset`（因为某些文件的所有记录都被过滤掉了，不产生有效文件偏移）。

更准确地说，bug 在于 `fileOffset` 的赋值时机：原代码在跳过文件的循环后才设置 `fileOffset = startingFileOffset`，但没有在跳过循环中递增 `fileOffset`。当 `updateCurrentIterator()` 因 residual filter 跳过空文件时，`fileOffset` 不会被正确更新，导致后续 `fileOffset()` 返回的值与实际读取位置不一致。

这个不一致会导致 Flink checkpoint 恢复时读取位置错误：如果 checkpoint 时 `fileOffset` 被错误记录，恢复后 seek 到错误位置，可能产生重复读取或漏读。

修复方式：在跳过文件的循环中逐个递增 `fileOffset`，并移除循环后的批量赋值。同时移除了 `recordOffset = startingRecordOffset` 的赋值（该值由 `updateCurrentIterator` 正确管理）。

## 如何达成设计目的

修改 `DataIterator.seek()` 方法的 file offset 跟踪逻辑：
1. 在跳过文件的 `for` 循环中添加 `fileOffset += 1`，使每跳过一个文件就递增偏移量。
2. 移除循环后的 `fileOffset = startingFileOffset` 和 `recordOffset = startingRecordOffset` 批量赋值，因为这两个值现在由跳过循环和 `updateCurrentIterator` 正确维护。

同时重构测试工具 `ReaderUtil.createFileTask`，增加接收 `ResidualEvaluator` 参数的重载，使测试能够构造带 residual filter 的 FileScanTask。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/source/DataIterator.java` (+2/-2 lines)

**修改目的**：修复 seek 中 file offset 的计算。

**修改内容**：
```java
for (long i = 0L; i < startingFileOffset; ++i) {
  tasks.next();
  fileOffset += 1;  // 新增：每跳过一个文件递增
}
```
移除了循环后的 `fileOffset = startingFileOffset;` 和 `recordOffset = startingRecordOffset;`。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/source/reader/ReaderUtil.java` (+13/-3 lines)

**修改目的**：支持测试中传入自定义 ResidualEvaluator。

**修改内容**：新增 `createFileTask` 重载方法接收 `ResidualEvaluator residuals` 参数，原方法委托给新方法并传入 `ResidualEvaluator.unpartitioned(Expressions.alwaysTrue())`。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/source/reader/TestArrayPoolDataIteratorBatcherRowData.java` (+184/-0 lines)

**修改目的**：验证 file offset 在文件被跳过时的正确性。

**修改内容**：新增三个测试：
1. `testDataIteratorWithResidualFilter`：验证带 residual filter 时 fileOffset 与记录的 id 字段一致（每条记录的 id 设为其所在文件的 offset）。
2. `testInitializationWithHeadFilesSkipped`：构造 [F0(被过滤), F1(1记录)] 的场景，验证 seek(0,0) 后 fileOffset 为 1（F0 被跳过）。
3. `testSeekResumesCorrectlyAfterHeadFilesSkipped`：模拟完整的 checkpoint-restore 场景，构造 [F0(过滤), F1(1记录), F2(2记录)]，读取 F1 和 F2 的第一条记录后记录 checkpoint 位置，然后创建新 iterator seek 到该位置，验证恢复后只读取 F2 的第二条记录（不重复、不漏读）。

## 总结

这次提交修复了 Flink DataIterator.seek() 中 file offset 计算错误，该错误在 residual filter 过滤掉文件时导致 checkpoint 恢复后读取位置不一致，可能产生重复或漏读。通过在跳过文件时逐个递增 fileOffset 并移除错误的批量赋值，修复了该问题。测试中通过模拟完整的 checkpoint-restore 场景验证了修复的正确性。该修复随后在 #16950 中被 backport。
