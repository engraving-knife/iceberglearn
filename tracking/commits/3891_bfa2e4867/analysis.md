# 提交 3891：Flink: Add data model and key serialization for equality delete conversion (#16831)

## 提交信息

- **序号**：3891 / 4088
- **哈希**：bfa2e48675d8126b208abec128121374c8b80803
- **短哈希**：bfa2e4867
- **日期**：2026-06-16 16:43:08 -0700
- **作者**：Maximilian Michels
- **提交说明**：Flink: Add data model and key serialization for equality delete conversion (#16831)
- **PR/Issue**：#16831

## 总体目的

为 Flink 的 equality delete 转换（ConvertEqualityDeletes）维护任务实现基础数据模型和键序列化。这是从大型 PR #15996 拆分出的六个提交中的第一个，实现了将暂存分支（staging branch）上的 equality-delete 文件转换为目标分支上的 deletion vectors（DV）的维护任务。

该任务在一个 Flink 作业中运行，包含五个算子：Planner（规划器）、Reader（读取器）、PK index（主键索引）、DV writer（DV 写入器）、Committer（提交器）。本提交只添加这些算子之间交换消息所需的共享数据模型和键序列化，算子实现和公共 API 在后续 PR 中添加。

equality delete 转换的意义在于：equality delete 文件在查询时需要与数据文件进行合并（merge-on-read），性能较差；将其转换为 deletion vectors 后，可以通过文件级的位置位图快速过滤被删除的行，显著提升查询性能。

## 如何达成设计目的

定义了一系列 Java record 作为算子间消息类型，确保 Flink 能使用原生序列化器而非回退到 Kryo。同时实现了 `StructLikeSerializer` 用于编码 equality 键和分区元组，通过在键前缀中包含分区 spec id 和 equality 字段 id，避免不同 spec 或不同 equality 字段集的行发生键碰撞。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/DVPosition.java` (+54 lines, new file)

**修改目的**：定义 PK index 到 DV writer 的消息类型。

**工作逻辑**：
```java
public record DVPosition(
    String dataFilePath, long position, int specId, byte[] partition, long dataSequenceNumber)
    implements Serializable
```
标识要标记删除的特定行（通过文件路径和位置），携带 data file 的 specId 和编码后的分区信息，使下游写入器无需重新读取 manifest。`dataSequenceNumber` 用于确保 equality delete 只删除比自身序列号更早的数据行。包含 `ABORT` 哨兵值用于中止信号。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/DVWriteResult.java` (+46 lines, new file)

**修改目的**：定义 DV writer 到 Committer 的结果消息。

**工作逻辑**：
```java
public record DVWriteResult(
    List<DeleteFile> dvFiles, List<DeleteFile> rewrittenDvFiles, boolean hasError)
    implements Serializable
```
包含新写入和重写的 DV 文件列表，`hasError` 标志指示写入器是否失败，失败时 Committer 不应提交数据文件。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/EqualityConvertPlan.java` (+76 lines, new file)

**修改目的**：定义 Planner 到 DV writer 和 Committer 的周期元数据。

**工作逻辑**：
包含周期内的 staging 数据文件、staging DV 文件、staging 和 main 的 snapshot id 等。通过 `NO_OP_STAGING_SNAPSHOT_ID` 哨兵值表示空操作周期。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/EqualityDeleteFileScanTask.java` (+51 lines, new file)

**修改目的**：封装 equality delete 文件扫描任务。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/FlinkAddedRowsScanTask.java` (+72 lines, new file)

**修改目的**：封装新增数据文件扫描任务，包装原生 FileScanTask。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/IndexCommand.java` (+93 lines, new file)

**修改目的**：定义 Reader 到 PK index 的消息类型，按主键分区。

**工作逻辑**：
包含四种命令类型：`ADD_DATA_ROW`（添加数据行）、`ADD_STAGING_DATA_ROW`（添加暂存数据行）、`RESOLVE_DELETE`（解析删除）、`CLEAR_INDEX`（广播清除过期键）。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/ReadCommand.java` (+86 lines, new file)

**修改目的**：定义 Planner 到 Reader 的消息类型。

**工作逻辑**：
包装 ContentScanTask（原生 FileScanTask、FlinkAddedRowsScanTask 或 EqualityDeleteFileScanTask），附带序列号和 staging 标志。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/SerializedEqualityValues.java` (+51 lines, new file)

**修改目的**：定义序列化的 equality 值类型，用于 keyed stream。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/StructLikeSerializer.java` (+140 lines, new file)

**修改目的**：实现 equality 键和分区元组的序列化。

**工作逻辑**：
- `serializeKey`：在每个键前缀中包含分区 spec id 和 equality 字段 id，确保不同 spec 或不同 equality 字段集的行不会发生键碰撞
- `encodePartition`：将分区元组编码为 `byte[]`，使 Flink 原生序列化而不回退到 Kryo
- 使用 `Conversions.toByteBuffer` 进行底层编码

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestFlinkPojoTypes.java` (+47 lines, new file)

**修改目的**：验证所有消息类型能被 Flink 原生序列化。

**工作逻辑**：
断言 `IndexCommand`、`DVPosition`、`SerializedEqualityValues` 等 keyed-stream 类型不会回退到 Kryo 序列化。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestStructLikeSerializer.java` (+176 lines, new file)

**修改目的**：测试 `StructLikeSerializer` 的键序列化和分区编码。

## 总结

为 Flink equality delete 转换维护任务奠定了数据模型基础，定义了 8 个 Java record 消息类型和 1 个序列化器，覆盖了从 Planner 到 Committer 的完整数据流。通过精心设计的键序列化方案（前缀包含 spec id 和字段 id）避免了键碰撞，并使用 Java record 确保 Flink 原生序列化。这是将 equality delete 转换为 deletion vectors 这一重要性能优化功能的第一步。
