# 提交 2304：CP 12988 to Spark version (#13412)

## 提交信息

- **序号**：2304 / 4088
- **哈希**：dfecabc76c5b82ecdde7d61abdf540d43ee14f59
- **短哈希**：dfecabc76
- **日期**：2025-07-01 16:55:14 -0700
- **作者**：Prashant Singh
- **提交说明**：CP 12988 to Spark version (#13412)
- **PR/Issue**：#13412

## 总体目的

本提交将 PR #12988 的修改回移植到 Spark 3.4 和 Spark 4.0 版本。PR #12988 修改了 Spark 结构化流读取中 `SparkMicroBatchStream` 的行数限制（`maxRows`）处理逻辑，将其从硬限制改为软限制。

此前，当设置了 `STREAMING_MAX_ROWS_PER_MICRO_BATCH` 限制时，系统会在添加文件前检查是否会超过行数限制。如果当前文件的记录数加上已累计的记录数会超过限制，则不添加该文件并停止读取。这导致一个严重问题：如果单个文件的记录数就超过了 `maxRows` 限制，流会永远卡住，无法前进，因为没有任何文件能满足限制条件。

修改后，`maxRows` 变为软限制：系统会先添加文件（确保流能继续前进），然后再检查是否超过了行数限制。这意味着单个 micro-batch 可能会略微超过 `maxRows` 限制（最多多一个文件的记录数），但保证了流不会卡住。

## 如何达成设计目的

核心修改在 `SparkMicroBatchStream` 的文件遍历循环中：

1. **先添加文件，再检查行数限制**：将行数检查从"添加前检查"改为"添加后检查"
2. **文件数限制仍为硬限制**：`maxFiles` 仍然是硬限制，在添加文件前检查
3. **添加后检查行数**：添加文件后，如果累计记录数达到或超过 `maxRows`，则停止读取当前快照

这种设计确保了：
- 流始终能前进（至少处理一个文件）
- `maxFiles` 是精确的硬限制
- `maxRows` 是近似的软限制，可能略微超出但不会阻塞流

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkMicroBatchStream.java` (+14/-3 lines)

**修改目的**：将 `maxRows` 从硬限制改为软限制。

**工作逻辑**：

原逻辑（添加前检查）：
```java
if ((curFilesAdded + 1) > getMaxFiles(limit)
    || (curRecordCount + task.file().recordCount()) > getMaxRows(limit)) {
    shouldContinueReading = false;
    break;
}
curFilesAdded += 1;
curRecordCount += task.file().recordCount();
```

新逻辑（添加后检查）：
```java
if ((curFilesAdded + 1) > getMaxFiles(limit)) {
    shouldContinueReading = false;
    break;
}
curFilesAdded += 1;
curRecordCount += task.file().recordCount();
if (curRecordCount >= getMaxRows(limit)) {
    ++curPos;  // 文件已被包含，需要前进位置
    shouldContinueReading = false;
    break;
}
```

关键点：当因 `maxRows` 停止时，需要额外 `++curPos`，因为文件已经被处理但位置计数器还没递增。此外，`curPos` 从 `int` 改为 `long`，防止大文件集场景下的整数溢出。还修复了注释中的拼写错误。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/SparkMicroBatchStream.java` (+12/-3 lines)

**修改目的**：同样将 `maxRows` 从硬限制改为软限制（Spark 4.0 版本）。

**工作逻辑**：与 Spark 3.4 版本的修改相同，只是缺少 `curPos` 类型变更（Spark 4.0 可能已经是 `long`）。也修复了注释中的拼写错误。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestStructuredStreamingRead3.java` (+45/-64 lines)

**修改目的**：更新测试以验证新的软限制行为。

**工作逻辑**：
- 引入 `assertMicroBatchRecordSizes` 辅助方法，验证每个 micro-batch 的记录数而非仅验证 micro-batch 数量
- `testReadStreamWithMaxRows1`：原期望只有 1 个 micro-batch（流卡住），新期望 6 个 micro-batch（记录数分别为 1, 2, 1, 1, 1, 1），验证流能正常前进
- `testReadStreamWithMaxRows2`：原期望 4 个 micro-batch，新期望 3 个 micro-batch（记录数 3, 2, 2），验证软限制下 batch 大小的变化
- 组合限制测试也相应更新

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/source/TestStructuredStreamingRead3.java` (+42/-60 lines)

**修改目的**：更新 Spark 4.0 的测试（与 3.4 类似的变更）。

## 总结

本提交将 `maxRows` 从硬限制改为软限制，解决了结构化流读取中当单个文件记录数超过 `maxRows` 时流会卡住的严重问题。修改确保流始终能向前推进，代价是单个 micro-batch 可能略微超出行数限制。这是流处理正确性与精确限制之间的合理权衡，因为流不前进比略微超限的问题更严重。
