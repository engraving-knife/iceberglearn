# 提交 2154：Spark 4.0: Structured Streaming read limit support follow-up (#13095)

## 提交信息

- **序号**：2154 / 4088
- **哈希**：1911c94ea605a3d3f10a1994b046f00a5e9fdceb
- **短哈希**：1911c94ea
- **日期**：2025-05-21 09:39:48 -0700
- **作者**：Wing Yew Poon
- **提交说明**：Spark 4.0: Structured Streaming read limit support follow-up (#13095)
- **PR/Issue**：#13095

## 总体目的

这是针对 Spark 4.0 中 Iceberg Structured Streaming 读取限制（read limit）支持的后续修复。此前 `SparkMicroBatchStream` 的 `latestOffset` 方法虽然接收 Spark 传入的 `ReadLimit` 参数，但在实际逻辑中并未真正使用它，而是依赖 `maxFilesPerMicroBatch` 和 `maxRecordsPerMicroBatch` 这两个硬编码属性来判断批次的终止条件。此外，`getDefaultReadLimit` 方法存在一个明显 bug：当同时配置了最大文件数和最大行数时，`ReadLimit.maxRows()` 错误地使用了 `maxFilesPerMicroBatch` 而非 `maxRecordsPerMicroBatch`。该提交修复了这些问题，使 Iceberg 的流式读取能正确响应 Spark 框架传入的读取限制（包括 `ReadMaxFiles`、`ReadMaxRows` 以及 `CompositeReadLimit`），确保微批处理的大小控制更加准确和灵活。

## 如何达成设计目的

- 新增两个私有静态方法 `getMaxFiles(ReadLimit)` 和 `getMaxRows(ReadLimit)`，用于从 Spark 传入的 `ReadLimit` 对象中正确提取最大文件数和最大行数限制，支持单一限制类型和复合限制类型（`CompositeReadLimit`）。
- 在 `latestOffset` 方法中，将原来使用硬编码属性的判断逻辑替换为调用 `getMaxFiles(limit)` 和 `getMaxRows(limit)`，真正使用 Spark 框架传递的 `ReadLimit` 参数。
- 修复 `getDefaultReadLimit` 中的 bug，将 `ReadLimit.maxRows(maxFilesPerMicroBatch)` 改为 `ReadLimit.maxRows(maxRecordsPerMicroBatch)`。
- 重命名和新增测试用例以覆盖更多场景，包括最大行数为 2 的场景和复合读取限制（同时限制文件数和行数）的场景。

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/SparkMicroBatchStream.java` (修改, +52/-8 lines)

**修改目的**：修复 Structured Streaming 中读取限制未生效以及 maxRows 参数错误使用 maxFilesPerMicroBatch 的 bug。

**工作逻辑**：
- 新增 `getMaxFiles` 方法：判断传入的 `ReadLimit` 是否为 `ReadMaxFiles` 类型，若是则返回其 `maxFiles()` 值；若是 `CompositeReadLimit`，则遍历其包含的子限制，找到 `ReadMaxFiles` 类型并返回其值；否则返回 `Integer.MAX_VALUE`（表示无限制）。
- 新增 `getMaxRows` 方法：逻辑类似，但针对 `ReadMaxRows` 类型，将其 `long` 类型的 `maxRows()` 通过 `Math.toIntExact` 转换为 `int`。
- 在 `latestOffset` 的循环中，将原来的 `curFilesAdded + 1 > maxFilesPerMicroBatch` 和 `curRecordCount + task.file().recordCount() > maxRecordsPerMicroBatch` 替换为使用 `getMaxFiles(limit)` 和 `getMaxRows(limit)`，移除了原有的 TODO 注释。
- 修复 `getDefaultReadLimit` 中 `readLimits[1] = ReadLimit.maxRows(maxFilesPerMicroBatch)` 为 `ReadLimit.maxRows(maxRecordsPerMicroBatch)`。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/source/TestStructuredStreamingRead3.java` (修改, +44/-14 lines)

**修改目的**：重命名和扩展测试用例，覆盖修复后的读取限制逻辑。

**工作逻辑**：
- 将测试方法名简化重命名（如 `testReadStreamOnIcebergTableWithMultipleSnapshots_WithNumberOfFiles_1` 改为 `testReadStreamWithMaxFiles1`）。
- 将部分测试中 `startStream` 的调用改为使用 `ImmutableMap.of(...)` 形式，保持一致性。
- 新增 `testReadStreamWithMaxRows2` 测试用例，验证最大行数为 2 时的微批次数和数据正确性。
- 新增 `testReadStreamWithCompositeReadLimit` 测试用例，同时设置 `STREAMING_MAX_FILES_PER_MICRO_BATCH=1` 和 `STREAMING_MAX_ROWS_PER_MICRO_BATCH=2`，验证复合读取限制场景下微批次数量为 6。

## 总结

该提交修复了 Spark 4.0 Structured Streaming 中两个重要问题：读取限制参数未被实际使用，以及 maxRows 默认限制错误使用了文件数而非行数。修复后，Iceberg 流式读取能正确响应 Spark 框架的流量控制机制，提升了流处理的数据吞吐控制精度和正确性。
