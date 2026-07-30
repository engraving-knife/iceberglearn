# 提交 3404：Core: Fix BinPackRewriteFilePlanner producing incorrect output file count with max-files-to-rewrite (#15576)

## 提交信息

- **序号**：3404 / 4088
- **哈希**：06f9a1d1d383fc29c82dfba099e78a65a6a3188a
- **短哈希**：06f9a1d1d3
- **日期**：2026-03-16 20:17:53 -0700
- **作者**：hemanthboyina
- **提交说明**：Core: Fix BinPackRewriteFilePlanner producing incorrect output file count with max-files-to-rewrite (#15576)
- **PR/Issue**：#15576

## 总体目的

修复 `BinPackRewriteFilePlanner` 在使用 `max-files-to-rewrite` 限制时，计算预期输出文件数使用了错误的输入大小。当文件组被 `max-files-to-rewrite` 截断时，原有代码使用整个文件组的输入大小（`inputSize`）来计算 `inputSplitSize` 和 `expectedOutputFiles`，而非使用实际被选中的截断文件列表的大小。这导致预期输出文件数被高估，可能产生过多的小输出文件。

## 如何达成设计目的

1. 将截断后的文件列表提取为独立变量 `tasksToRewrite`
2. 基于 `tasksToRewrite` 计算实际的 `rewriteInputSize`
3. 使用 `rewriteInputSize` 而非整个文件组的 `inputSize` 来计算 `inputSplitSize` 和 `expectedOutputFiles`
4. 新增测试验证截断场景下预期输出文件数的正确性

## 修改详情

### `core/src/main/java/org/apache/iceberg/actions/BinPackRewriteFilePlanner.java` (+9/-3 lines)

**修改目的**：修复使用截断后文件列表的实际大小计算输出文件数。

**工作逻辑**：
- 原有代码：
  ```java
  selectedFileGroups.add(newRewriteGroup(ctx, partition,
      fileScanTasks.subList(0, scanTasksToRewrite),
      inputSplitSize(inputSize), expectedOutputFiles(inputSize)));
  ```
  其中 `inputSize` 是整个文件组的大小，而非截断后的大小
- 修复后：
  ```java
  List<FileScanTask> tasksToRewrite = fileScanTasks.subList(0, scanTasksToRewrite);
  long rewriteInputSize = inputSize(tasksToRewrite);
  selectedFileGroups.add(newRewriteGroup(ctx, partition,
      tasksToRewrite,
      inputSplitSize(rewriteInputSize), expectedOutputFiles(rewriteInputSize)));
  ```
  使用截断后文件列表的实际大小 `rewriteInputSize` 计算拆分和预期输出

### `core/src/test/java/org/apache/iceberg/actions/TestBinPackRewriteFilePlanner.java` (+35 lines)

**修改目的**：新增测试验证截断场景下预期输出文件数的正确性。

**工作逻辑**：
- 创建 3 个各 200 字节的文件（总 600 字节）
- 设置 `max-files-to-rewrite=1`、`target-file-size=250`
- 修复前：使用完整 600 字节计算 `expectedOutputFiles = ceil(600/250) = 3`
- 修复后：使用截断后的 200 字节计算 `expectedOutputFiles = ceil(200/250) = 1`
- 验证 `group.inputFileNum()` 为 1，`group.expectedOutputFiles()` 为 1

## 总结

本提交修复了 `BinPackRewriteFilePlanner` 在 `max-files-to-rewrite` 截断文件组时，使用完整文件组大小而非截断后实际大小计算预期输出文件数的 bug。修复后，输出文件数的预估基于实际被重写的文件，避免了过多小文件的生成。
