# 提交 2140：Core: Add max files rewrite option for RewriteAction

## 提交信息

- **序号**：2140 / 4088
- **哈希**：c2478968e65368c61799d8ca4b89506a61ca3e7c
- **短哈希**：c2478968e
- **日期**：2025-05-16 12:06:21 -0700
- **作者**：B Vadlamani
- **提交说明**：Core: Add max files rewrite option for RewriteAction (#12824)
- **PR/Issue**：#12824

## 总体目的

这个提交为 RewriteAction（数据文件重写/压缩操作）新增了 `max-files-to-rewrite` 选项，允许用户限制单次重写操作处理的文件数量。在大规模数据场景下，一次重写操作可能涉及大量数据文件，导致操作耗时过长、资源占用过大。通过提供最大文件数限制，用户可以分批执行重写操作，更好地控制资源使用和执行时间。此前 BinPackRewriteFilePlanner 会重写所有符合条件的文件，没有提供限制单次重写文件数量的能力。

## 如何达成设计目的

1. 在 BinPackRewriteFilePlanner 中新增 `MAX_FILES_TO_REWRITE` 常量和 `maxFilesToRewrite` 字段，支持通过配置属性指定最大重写文件数。
2. 修改 `plan()` 方法，在规划重写文件组时根据 maxFilesToRewrite 限制选取的文件数量，使用 AtomicInteger 跟踪已选文件数，并在达到限制时截断文件组。
3. 在 Flink 的 RewriteDataFiles 中暴露该配置选项。
4. 在 Spark 的 RewriteDataFilesSparkAction 中支持该选项。
5. 添加测试验证最大文件数限制的功能。

## 修改详情

### `core/src/main/java/org/apache/iceberg/actions/BinPackRewriteFilePlanner.java` (修改, +53/-27 lines)

**修改目的**：实现最大文件数重写限制的核心逻辑。

**工作逻辑**：
- 新增常量 `MAX_FILES_TO_REWRITE = "max-files-to-rewrite"` 和字段 `maxFilesToRewrite`。
- 在 `options()` 方法中将 MAX_FILES_TO_REWRITE 加入可配置属性列表。
- 新增 `maxFilesToRewrite()` 方法解析配置值，验证值为正整数或 null（null 表示不限制）。
- 重构 `plan()` 方法：原来使用 Stream API 流式处理文件组，改为使用 List 和 AtomicInteger 跟踪。当 maxFilesToRewrite 为 null 时，行为与原来一致（选取所有文件组）。当指定了限制时，使用 `fileCountRunner` 跟踪已选文件数，对每个文件扫描任务组计算可选取的数量（`scanTasksToRewrite = Math.min(fileScanTasks.size(), remainingSize)`），使用 `subList` 截取并更新计数器，达到限制后跳过后续文件组。

### `core/src/test/java/org/apache/iceberg/actions/TestBinPackRewriteFilePlanner.java` (修改, +90/-3 lines)

**修改目的**：验证 max-files-to-rewrite 选项的功能。

**工作逻辑**：新增多个测试用例，验证在不同 maxFilesToRewrite 设置下的文件选择行为，包括不限制、限制小于总数、限制等于总数等场景。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/RewriteDataFiles.java` (修改, +6 lines)

**修改目的**：在 Flink 维护 API 中暴露 max-files-to-rewrite 选项。

**工作逻辑**：添加 MAX_FILES_TO_REWRITE 属性常量和相关配置支持。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteDataFilesSparkAction.java`（及 v3.4、v4.0 对应文件）(修改, +2/-1 lines each)

**修改目的**：在 Spark 的 RewriteDataFilesSparkAction 中支持新选项。

**工作逻辑**：将 MAX_FILES_TO_REWRITE 添加到支持的属性集合中。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestSparkShufflingDataRewritePlanner.java`（及 v3.4、v4.0 对应文件）(修改, +2/-1 lines each)

**修改目的**：更新测试以适配新增的属性。

**工作逻辑**：在测试中添加对 MAX_FILES_TO_REWRITE 属性的处理。

## 总结

这个提交为数据文件重写操作新增了最大文件数限制能力，使用户能够更好地控制重写操作的规模和资源消耗。核心实现通过 AtomicInteger 计数器和文件组截断机制实现，设计简洁有效。该功能在 Core、Flink 和 Spark 三个模块中同步提供支持，后续被 backport 到更多版本。
