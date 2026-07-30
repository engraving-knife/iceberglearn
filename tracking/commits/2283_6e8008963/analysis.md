# 提交 2283：Spark: Make maxRecordPerMicrobatch a soft limit (#12988)

## 提交信息

- **序号**：2283 / 4088
- **哈希**：6e8008963f5ec0c8de67d4deea693720a3f5675d
- **短哈希**：6e8008963
- **日期**：2025-06-27 09:44:38 -0700
- **作者**：Prashant Singh
- **提交说明**：Spark: Make maxRecordPerMicrobatch a soft limit (#12988)
- **PR/Issue**：#12988

## 总体目的

本提交将 Spark 结构化流式读取中 `streaming-max-rows-per-micro-batch`（即 `maxRecordPerMicrobatch`）从硬限制改为软限制。此前的实现存在一个严重问题：流式读取的最小处理单元是单个数据文件，如果某个数据文件的记录数超过配置的行数限制，该文件永远不会被包含在任何微批次中（因为包含它就会超限），导致流处理"卡死"在该文件处无法推进。

文档中的警告明确说明了这个问题："streaming-max-rows-per-micro-batch should always be greater than the number of records in any data file in the table. The smallest unit that will be streamed is a single file, so if a data file contains more records than this limit, the stream will get stuck at this file."

改为软限制后，每个微批次始终至少包含一个完整的未处理数据文件（即使该文件记录数超过软限制），软限制仅用于在达到限制后阻止继续添加更多文件。这样既保证了流处理不会卡死，又能在大多数情况下控制批次大小。同时将记录计数变量从 `int` 改为 `long` 以避免大表场景下的整数溢出。

## 如何达成设计目的

- 调整 `SparkMicroBatchStream` 中微批次构建逻辑的判断顺序：原先在添加文件前检查"添加后是否超限"，超限则不添加并停止；新逻辑先添加文件（除非文件数超限），添加后再检查是否达到软限制，达到则停止添加更多文件。
- 将 `curRecordCount` 从 `int` 改为 `long`，避免大文件记录数累加导致的整数溢出。
- 更新文档，将硬限制警告替换为软限制说明。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkMicroBatchStream.java` (+14/-5 lines)

**修改目的**：将 maxRecordPerMicrobatch 从硬限制改为软限制，避免流处理卡死。

**工作逻辑**：原逻辑（硬限制）：
```java
if ((curFilesAdded + 1) > getMaxFiles(limit)
    || (curRecordCount + task.file().recordCount()) > getMaxRows(limit)) {
  shouldContinueReading = false;
  break;
}
curFilesAdded += 1;
curRecordCount += task.file().recordCount();
```
添加文件前，如果"当前记录数 + 文件记录数"超过最大行数限制，则不添加该文件并停止。问题：若某文件自身记录数就超过限制，它永远不会被添加，流卡死。

新逻辑（软限制）：
```java
if ((curFilesAdded + 1) > getMaxFiles(limit)) {
  // 文件数超限才停止，记录数是软限制可接受超出
  shouldContinueReading = false;
  break;
}
curFilesAdded += 1;
curRecordCount += task.file().recordCount();
if (curRecordCount >= getMaxRows(limit)) {
  // 已添加文件，若达到软限制则停止添加更多文件
  ++curPos;
  shouldContinueReading = false;
  break;
}
```
关键变化：1) 文件数限制仍为硬限制（添加前检查）；2) 行数限制改为软限制——先添加文件再检查，保证至少添加一个文件；3) 达到软限制后 `++curPos` 确保游标推进到已处理位置，避免重复处理。此外 `curRecordCount` 从 `int` 改为 `long` 防溢出，并修正两处注释拼写。

### `docs/docs/spark-configuration.md` (+1/-2 lines)

**修改目的**：更新文档说明，将硬限制警告改为软限制描述。

**工作逻辑**：原警告"should always be greater than the number of records in any data file...stream will get stuck"替换为"`streaming-max-rows-per-micro-batch` option sets a 'soft max', a batch will always include all the rows in the next unprocessed data file but additional files will not be included if doing so would exceed the soft max limit."

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestStructuredStreamingRead3.java` (+39/-76 lines)

**修改目的**：更新测试以验证软限制行为。

**工作逻辑**：调整测试用例，验证即使文件记录数超过软限制，批次仍会包含该文件并推进流处理，不再卡死。测试代码精简（净减 37 行）。

## 总结

本提交将 Spark 流式读取的 `maxRecordPerMicrobatch` 从硬限制改为软限制，解决了单个大文件导致流处理卡死的问题。核心思路是"先包含再检查"，保证每个批次至少处理一个文件。同时修复了潜在的 int 溢出问题。这是一个实用性很高的健壮性修复，降低了用户配置该参数时踩坑的风险。
