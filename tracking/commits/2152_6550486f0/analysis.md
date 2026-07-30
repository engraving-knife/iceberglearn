# 提交 2152：Spark 3.4: Structured Streaming read limit support follow-up

## 提交信息

- **序号**：2152 / 4088
- **哈希**：6550486f0c640e15bb7c4d6c16b087397656213e
- **短哈希**：6550486f0
- **日期**：2025-05-21 08:27:38 -0700
- **作者**：Wing Yew Poon
- **提交说明**：Spark 3.4: Structured Streaming read limit support follow-up (#13099)
- **PR/Issue**：#13099（backport #12260）

## 总体目的

这个提交是将 #12260（提交 2150）中修复的 Spark 结构化流读取限制问题 backport 到 Spark 3.4 版本。原始修复解决了两个问题：SparkMicroBatchStream.latestOffset 方法未使用传入的 ReadLimit 参数，以及 constructReadLimit 方法中 maxRows 错误使用 maxFilesPerMicroBatch 的 bug。Spark 3.4 作为仍然受支持的版本，同样需要这些修复，确保用户通过 Spark 配置设置的读取限制能被正确应用。

## 如何达成设计目的

1. 将 Spark 3.5 中修复后的 SparkMicroBatchStream.java 和 TestStructuredStreamingRead3.java 复制到 Spark 3.4 对应的目录中。
2. 代码内容与原始修复完全一致，包括新增 getMaxFiles/getMaxRows 辅助方法、修改 latestOffset 使用传入的 ReadLimit、修复 constructReadLimit 中的 maxRows bug。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkMicroBatchStream.java` (修改, +49/-3 lines)

**修改目的**：使用传入的 ReadLimit 并修复 maxRows bug。

**工作逻辑**：与提交 2150 中 Spark 3.5 的修改完全相同。
- 新增 getMaxFiles(ReadLimit) 和 getMaxRows(ReadLimit) 辅助方法，从 ReadLimit 中解析最大文件数和最大行数，支持 ReadMaxFiles、ReadMaxRows 和 CompositeReadLimit。
- 修改 latestOffset 方法，使用 getMaxFiles(limit) 和 getMaxRows(limit) 替代 maxFilesPerMicroBatch 和 maxRecordsPerMicroBatch。
- 修复 constructReadLimit 方法中 `ReadLimit.maxRows(maxFilesPerMicroBatch)` 改为 `ReadLimit.maxRows(maxRecordsPerMicroBatch)` 的 bug。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestStructuredStreamingRead3.java` (修改, +33/-11 lines)

**修改目的**：重命名和新增测试用例，覆盖 ReadLimit 功能。

**工作逻辑**：与 Spark 3.5 版本的测试修改完全相同，包括重命名测试方法和新增 testReadStreamWithMaxRows2 测试用例。

## 总结

这个提交是 #12260 的 backport，将 Spark 结构化流读取限制的修复扩展到 Spark 3.4 版本。代码与 Spark 3.5 的修复完全一致，确保了所有受支持的 Spark 版本都能正确处理 Spark 传入的 ReadLimit 参数和 maxRows 限制。
