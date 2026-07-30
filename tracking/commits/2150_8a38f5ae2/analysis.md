# 提交 2150：Spark 3.5: Structured Streaming read limit support follow-up

## 提交信息

- **序号**：2150 / 4088
- **哈希**：8a38f5ae2b5167277785494b9a48d9abd38330fe
- **短哈希**：8a38f5ae2
- **日期**：2025-05-20 13:44:06 -0700
- **作者**：Wing Yew Poon
- **提交说明**：Spark 3.5: Structured Streaming read limit support follow-up (#12260)
- **PR/Issue**：#12260

## 总体目的

这个提交是 Spark 结构化流（Structured Streaming）读取限制支持的后续改进，修复了两个问题。第一，`SparkMicroBatchStream.latestOffset` 方法原来没有使用传入的 ReadLimit 参数，而是使用从表属性中读取的 maxFilesPerMicroBatch 和 maxRecordsPerMicroBatch 值，这导致 Spark 传入的 ReadLimit（可能由用户通过 Spark 配置如 maxFilesPerTrigger、maxRowsPerTrigger 设置）被忽略。第二，在构造 CompositeReadLimit 时存在一个 bug，`ReadLimit.maxRows()` 错误地使用了 maxFilesPerMicroBatch 而非 maxRecordsPerMicroBatch，导致行数限制不正确。

## 如何达成设计目的

1. 新增 `getMaxFiles(ReadLimit)` 和 `getMaxRows(ReadLimit)` 两个辅助方法，从传入的 ReadLimit 参数中解析最大文件数和最大行数，支持 ReadMaxFiles、ReadMaxRows 和 CompositeReadLimit 三种类型。
2. 在 `latestOffset` 方法的文件遍历循环中，使用 `getMaxFiles(limit)` 和 `getMaxRows(limit)` 替代原来使用的 maxFilesPerMicroBatch 和 maxRecordsPerMicroBatch，确保使用 Spark 传入的 ReadLimit。
3. 修复 `constructReadLimit` 方法中的 bug，将 `ReadLimit.maxRows(maxFilesPerMicroBatch)` 改为 `ReadLimit.maxRows(maxRecordsPerMicroBatch)`。
4. 更新测试类，重命名测试方法使其更清晰，并新增测试用例覆盖行数限制场景。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkMicroBatchStream.java` (修改, +49/-3 lines)

**修改目的**：使用传入的 ReadLimit 并修复 maxRows bug。

**工作逻辑**：
- 新增导入 CompositeReadLimit、ReadMaxFiles、ReadMaxRows。
- 新增 `getMaxFiles(ReadLimit readLimit)` 方法：检查 readLimit 是否为 ReadMaxFiles 或 CompositeReadLimit（从中提取 ReadMaxFiles），返回最大文件数；如果没有则返回 Integer.MAX_VALUE。
- 新增 `getMaxRows(ReadLimit readLimit)` 方法：类似逻辑，从 ReadMaxRows 或 CompositeReadLimit 中提取最大行数；将 long 转为 int。
- 修改 `latestOffset` 方法中的判断条件：将 `curFilesAdded + 1 > maxFilesPerMicroBatch` 改为 `> getMaxFiles(limit)`，将 `curRecordCount + task.file().recordCount() > maxRecordsPerMicroBatch` 改为 `> getMaxRows(limit)`。
- 修复 `constructReadLimit` 方法：将 `readLimits[1] = ReadLimit.maxRows(maxFilesPerMicroBatch)` 改为 `ReadLimit.maxRows(maxRecordsPerMicroBatch)`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestStructuredStreamingRead3.java` (修改, +33/-11 lines)

**修改目的**：重命名和新增测试用例，覆盖 ReadLimit 功能。

**工作逻辑**：
- 将原有测试方法重命名为更清晰的名称（如 testReadStreamWithMaxFiles1、testReadStreamWithMaxRows1 等）。
- 新增 testReadStreamWithMaxRows2 测试用例，验证每批 2 行的限制。
- 调整 startStream 方法的调用方式，使用 ImmutableMap 传参。

## 总结

这个提交修复了 Spark 结构化流读取中的两个问题：ReadLimit 参数未被使用和 maxRows 构造 bug。修复后，Spark 传入的读取限制（如 maxFilesPerTrigger、maxRowsPerTrigger）能被正确应用，提高了结构化流的灵活性和正确性。该修复后续被 backport 到 Spark 3.4。
