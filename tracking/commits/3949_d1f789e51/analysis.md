# 提交 3949：Flink: Backport fix file offset mismatch in DataIterator.seek() when files are skipped (#16950)

## 提交信息

- **序号**：3949 / 4088
- **哈希**：d1f789e517cd93589968325e60fab86306982cce
- **短哈希**：d1f789e51
- **日期**：2026-06-25 14:09:01 +0200
- **作者**：Yujiang Zhong
- **提交说明**：Flink: Backport fix file offset mismatch in DataIterator.seek() when files are skipped (#16950)
- **PR/Issue**：#16950（backport #16929，即提交 3939）

## 总体目的

这次提交是 #16929（提交 3939）的 backport，将 `DataIterator.seek()` 中 file offset 计算修复同步到 Flink 1.20 和 2.0 分支。

原 bug：当 `DataIterator.seek(startingFileOffset, startingRecordOffset)` 用于从检查点恢复读取位置时，跳过文件的循环中没有递增 `fileOffset`，而是在循环后直接赋值 `fileOffset = startingFileOffset`。当 residual filter 过滤掉文件时，`fileOffset` 与实际读取位置不一致，导致 checkpoint 恢复时产生重复读取或漏读。

修复方式：在跳过文件的循环中逐个递增 `fileOffset`，移除循环后的批量赋值和 `recordOffset` 赋值。

## 如何达成设计目的

与 #16929 相同的修复方式，将修改应用到 Flink 1.20 和 2.0 的 `DataIterator.java`、`ReaderUtil.java` 和 `TestArrayPoolDataIteratorBatcherRowData.java`。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/source/DataIterator.java` (+2/-2 lines)

**修改目的**：修复 Flink 1.20 的 seek file offset 计算。

**工作逻辑**：在跳过文件的 `for` 循环中添加 `fileOffset += 1`，移除循环后的 `fileOffset = startingFileOffset` 和 `recordOffset = startingRecordOffset`。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/source/reader/ReaderUtil.java` (+13/-3 lines)

**修改目的**：支持 Flink 1.20 测试中传入自定义 ResidualEvaluator。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/source/reader/TestArrayPoolDataIteratorBatcherRowData.java` (+170/-0 lines)

**修改目的**：将 Flink 1.20 的测试同步到包含 residual filter 场景的测试。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/source/DataIterator.java` (+2/-2 lines)

**修改目的**：修复 Flink 2.0 的 seek file offset 计算。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/source/reader/ReaderUtil.java` (+13/-3 lines)

**修改目的**：支持 Flink 2.0 测试中传入自定义 ResidualEvaluator。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/source/reader/TestArrayPoolDataIteratorBatcherRowData.java` (+170/-0 lines)

**修改目的**：将 Flink 2.0 的测试同步到包含 residual filter 场景的测试。

## 总结

这次提交将 #16929 的 `DataIterator.seek()` file offset 修复 backport 到 Flink 1.20 和 2.0 分支，确保所有受支持的 Flink 版本都不再出现 residual filter 场景下的 checkpoint 恢复读取位置不一致问题。
