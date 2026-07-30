# 提交 3084：Core: Unlink table metadata's last-updated timestamp from snapshot timestamp (#14504)

## 提交信息

- **序号**：3084 / 4088
- **哈希**：615b5a097cf27a65fec2a9f613150ab83ea8bc7a
- **短哈希**：615b5a097
- **日期**：2026-01-08
- **作者**：Hongyue/Steve Zhang
- **提交说明**：Core: Unlink table metadata's last-updated timestamp from snapshot timestamp (#14504)
- **PR/Issue**：#14504

## 总体目的

Iceberg 的 `TableMetadata` 中有两个容易混淆的时间戳概念：一个是表元数据的 `lastUpdatedMillis`（记录表元数据文件最后一次被更新的时间），另一个是快照（Snapshot）自身的 `timestampMillis`（记录快照被创建的时间）。在此前的实现中，当向表添加新快照（`addSnapshot`）或将一个快照设为当前快照（通过 `setRef` 设置 main 分支引用）时，代码会将 `lastUpdatedMillis` 直接设置为快照的 `timestampMillis`。

这种"绑定"设计存在语义问题：快照时间戳是在提交开始时生成的（记录的是提交动作发起的时刻），而元数据文件的最后更新时间应该是元数据文件实际被写入的时刻。两者在正常提交路径中可能接近，但在以下场景中会产生不正确的结果：

1. **快照回滚（rollback）**：当用户执行 `rollbackTo` 将表回滚到一个已存在的旧快照时，该快照的 `timestampMillis` 是很久以前的值，但表元数据文件是"现在"刚被更新的。如果将 `lastUpdatedMillis` 设为旧快照时间戳，元数据日志（metadata log）中会记录一个过去的、不准确的更新时间，导致时间旅行查询、审计日志等依赖 `lastUpdatedMillis` 的功能产生误导。

2. **并发提交重试**：当提交因冲突而重试时，快照时间戳可能在重试开始时生成，但元数据文件实际写入发生在重试成功后，两者之间存在时间差。

本提交的核心目的是"解绑"这两个时间戳：在 `addSnapshot` 时不再用快照时间戳覆盖 `lastUpdatedMillis`（保留 `lastUpdatedMillis` 为元数据构建时已设定的值，通常为 `System.currentTimeMillis()`）；在 `setRef` 设置 main 分支回滚到已存在快照时，使用当前时间戳而非快照时间戳作为变更时间记录到快照日志中。

## 如何达成设计目的

在 `TableMetadata` 的 `addSnapshot` 方法中移除 `this.lastUpdatedMillis = snapshot.timestampMillis()` 这一行，使 `lastUpdatedMillis` 保持为构建 `TableMetadata` 时已赋的值（通常在 `TableMetadataParser` 读入或 `TableMetadata.Builder` 构建时通过 `System.currentTimeMillis()` 设定）。在 `setRef` 方法中，针对 main 分支引用设置时的快照日志记录，引入 `timeOfChange` 变量：若快照是新添加的则用快照时间戳（保持与添加快照时刻一致），否则（回滚到已存在快照）使用 `this.lastUpdatedMillis`（即元数据实际更新时间）。同时更新 Flink 和 Spark 各版本的元数据表测试，将原先断言元数据日志时间戳等于快照时间戳的断言，改为断言等于 `tableMetadata.lastUpdatedMillis()`，并在回滚测试中新增断言验证回滚时间戳大于被回滚到的快照时间戳。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableMetadata.java` (+6/-5 lines)

**修改目的**：解绑 `lastUpdatedMillis` 与快照时间戳的关联。

**工作逻辑**：
1. 在 `addSnapshot` 方法中，移除 `this.lastUpdatedMillis = snapshot.timestampMillis();` 这一行。此前每次添加快照都会把元数据最后更新时间覆盖为快照创建时间；移除后，`lastUpdatedMillis` 保持为 `TableMetadata` 构建时设定的值（通常为提交完成写入元数据时的 `System.currentTimeMillis()`），更准确地反映元数据文件的实际更新时刻。`this.lastSequenceNumber` 和快照列表的更新逻辑不变。

2. 在 `setRef` 方法中，原逻辑为：若设置的是已添加的快照（`isAddedSnapshot(snapshotId)` 为 true），则将 `lastUpdatedMillis` 设为快照时间戳。新逻辑移除了对 `lastUpdatedMillis` 的这一赋值。取而代之的是，在添加快照日志条目时引入局部变量 `long timeOfChange`：若 `isAddedSnapshot(snapshotId)` 为 true（即新提交的快照被设为当前），`timeOfChange = snapshot.timestampMillis()`（与快照创建时刻一致）；否则（回滚到已存在的旧快照），`timeOfChange = this.lastUpdatedMillis()`（使用元数据实际更新时间）。然后 `snapshotLog.add(new SnapshotLogEntry(timeOfChange, ref.snapshotId()))`。注释说明 "rollback to an existing snapshot will use current timestamp as the time of the change"。

3. 对于 main 分支设置时 `lastUpdatedMillis` 的处理：原有逻辑中，若 `ref.minSnapshotsToKeep() == 0`（即该快照可能被快速清理），则将 `lastUpdatedMillis` 设为 `System.currentTimeMillis()`。这部分逻辑保留不变。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkMetaDataTable.java` (+5/-3 lines)

**修改目的**：更新 Flink v1.20 元数据表测试，适配 `lastUpdatedMillis` 与快照时间戳解绑后的行为。

**工作逻辑**：
在元数据日志断言中，原先断言第三条日志的 `timestamp` 等于 `Instant.ofEpochMilli(currentSnapshot.timestampMillis())`。修改为：断言 `timestamp` 大于或等于快照时间戳（`isAfterOrEqualTo(Instant.ofEpochMilli(currentSnapshot.timestampMillis()))`），且等于 `Instant.ofEpochMilli(tableMetadata.lastUpdatedMillis())`。使用 `getFieldAs("timestamp")` 强转为 `Instant` 类型。在过滤查询的断言中，将原先 `Instant.ofEpochMilli(tableMetadata.currentSnapshot().timestampMillis())` 改为 `Instant.ofEpochMilli(tableMetadata.lastUpdatedMillis())`。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkMetaDataTable.java` (+5/-3 lines)

**修改目的**：同 v1.20，为 Flink v2.0 更新元数据表测试断言。逻辑与 v1.20 完全一致。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkMetaDataTable.java` (+5/-3 lines)

**修改目的**：同 v1.20，为 Flink v2.1 更新元数据表测试断言。逻辑与 v1.20 完全一致。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMetadataTables.java` (+3/-3 lines)

**修改目的**：更新 Spark v3.4 元数据表测试，将时间戳断言从快照时间戳改为 `lastUpdatedMillis`。

**工作逻辑**：
在期望的元数据日志行中，将 `DateTimeUtils.toJavaTimestamp(currentSnapshot.timestampMillis() * 1000)` 改为 `DateTimeUtils.toJavaTimestamp(tableMetadata.lastUpdatedMillis() * 1000)`。在过滤查询结果的断言中，将 `tableMetadata.currentSnapshot().timestampMillis()` 改为 `tableMetadata.lastUpdatedMillis()`。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestIcebergSourceTablesBase.java` (+5/-0 lines)

**修改目的**：在 Spark v3.4 的回滚测试中新增对回滚时间戳的断言。

**工作逻辑**：
在 `rollbackTo` 后获取 `rollbackTimestamp` 之后，新增断言：`assertThat(rollbackTimestamp).as("Rollback history timestamp should be greater than first snapshot timestamp").isEqualTo(((HasTableOperations) table).operations().current().lastUpdatedMillis()).isGreaterThan(firstSnapshotTimestamp)`。这验证了回滚后的历史时间戳等于元数据的 `lastUpdatedMillis`（而非旧快照时间戳），且大于被回滚到的第一个快照的时间戳。新增导入 `org.apache.iceberg.HasTableOperations`。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMetadataTables.java` (+3/-3 lines)

**修改目的**：同 v3.4，为 Spark v3.5 更新元数据表测试断言。逻辑与 v3.4 完全一致。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestIcebergSourceTablesBase.java` (+5/-0 lines)

**修改目的**：同 v3.4，为 Spark v3.5 新增回滚时间戳断言。逻辑与 v3.4 完全一致。

### `spark/v4.0/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMetadataTables.java` (+3/-3 lines)

**修改目的**：同 v3.4，为 Spark v4.0 更新元数据表测试断言。逻辑与 v3.4 完全一致。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/source/TestIcebergSourceTablesBase.java` (+5/-0 lines)

**修改目的**：同 v3.4，为 Spark v4.0 新增回滚时间戳断言。逻辑与 v3.4 完全一致。

## 总结

本提交修正了 Iceberg `TableMetadata` 中 `lastUpdatedMillis` 与快照 `timestampMillis` 的不当绑定关系。核心改动是在 `addSnapshot` 时不再用快照时间戳覆盖元数据更新时间，在 `setRef` 回滚到已存在快照时使用元数据实际更新时间而非旧快照时间戳记录快照日志。这使得 `lastUpdatedMillis` 更准确地反映元数据文件的实际写入时刻，对快照回滚场景下的元数据日志、审计和时间旅行查询的正确性有重要意义。配套更新了 Flink（v1.20/v2.0/v2.1）和 Spark（v3.4/v3.5/v4.0）各版本的元数据表测试。
