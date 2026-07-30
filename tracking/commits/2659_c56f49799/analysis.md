# 提交 2659：Flink: Code changes for Flink 2.1

## 提交信息

- **序号**：2659 / 4088
- **哈希**：c56f497992702d75a26853fdac09932cc309f90a
- **短哈希**：c56f49799
- **日期**：2025-09-19 07:36:48 -0700
- **作者**：Maximilian Michels
- **提交说明**：Flink: Code changes for Flink 2.1
- **PR/Issue**：无（此提交是 Flink 2.1 迁移系列的一部分）

## 总体目的

本提交是 Flink 2.1 版本支持迁移工作的第四步，对 `flink/v2.1/` 目录中的代码进行适配修改，使其兼容 Flink 2.1 的 API。由于 v2.1 目录是从 v2.0 复制而来的，其中大部分代码可以直接复用，但 Flink 2.1 相比 2.0 有一些 API 变更和新特性，需要针对性地调整。

本提交主要处理两类变更：
1. **Variant 类型支持**：Flink 2.1 在 RowData 接口中新增了 `getVariant(int)` 方法用于读取 Variant 类型数据。Iceberg 的 Flink 数据读取器（FlinkParquetReaders、RowDataProjection、StructRowData）需要实现这些新方法以支持 Variant 类型。
2. **Checkpoint 处理改进**：Flink 2.1 对 `prepareSnapshotPreBarrier` 和 `finish` 方法的调用时序有变化，需要避免在 `finish()` 后重复刷新已提交的 checkpoint。IcebergWriteAggregator 和 DynamicWriteResultAggregator 需要追踪 lastCheckpointId 以防止重复提交。

## 如何达成设计目的

通过修改 v2.1 目录下的 7 个文件完成适配：

1. **Variant 类型支持**（3 个文件）：在 FlinkParquetReaders、RowDataProjection、StructRowData 中实现 `getVariant(int)` 方法
2. **Checkpoint 防重复提交**（2 个文件）：在 IcebergWriteAggregator 和 DynamicWriteResultAggregator 中增加 `lastCheckpointId` 追踪和 `initializeState` 方法
3. **测试适配**（2 个文件）：更新 TestIcebergCommitter 和 TestFlinkPackage 中的断言以匹配 Flink 2.1 行为

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/data/FlinkParquetReaders.java` (+6/-0 lines)

**修改目的**：支持 Variant 类型数据的读取。

**工作逻辑**：导入 `org.apache.flink.types.variant.Variant`，在内部 RowData 实现类中新增 `getVariant(int pos)` 方法，直接从 `values` 数组中获取并强制转换为 `Variant` 类型。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/data/RowDataProjection.java` (+6/-0 lines)

**修改目的**：在行数据投影中支持 Variant 类型。

**工作逻辑**：导入 `Variant` 类型，新增 `getVariant(int pos)` 方法，通过 `getValue(pos)` 获取值并强制转换为 `Variant`。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/data/StructRowData.java` (+6/-0 lines)

**修改目的**：在结构体行数据中支持 Variant 类型。

**工作逻辑**：导入 `Variant` 类型，新增 `getVariant(int pos)` 方法，当字段为 null 时返回 null，否则从 `struct` 中获取 `Variant.class` 类型的值。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergWriteAggregator.java` (+28/-2 lines)

**修改目的**：修复 Flink 2.1 中 `finish()` 和 `prepareSnapshotPreBarrier()` 的重复提交问题。

**工作逻辑**：
- 新增 `lastCheckpointId` 字段，初始值为 `CheckpointIDCounter.INITIAL_CHECKPOINT_ID - 1`
- 新增 `initializeState(StateInitializationContext)` 方法，从恢复的 checkpoint 中恢复 `lastCheckpointId`
- `finish()` 方法中，将 `prepareSnapshotPreBarrier(Long.MAX_VALUE)` 改为 `prepareSnapshotPreBarrier(lastCheckpointId + 1)`，使用实际的下一个 checkpoint ID
- `prepareSnapshotPreBarrier()` 中新增防重复检查：如果 `checkpointId == lastCheckpointId`，表示已刷新过，记录日志并直接返回；否则更新 `lastCheckpointId` 并继续执行

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicWriteResultAggregator.java` (+25/-1 lines)

**修改目的**：同 IcebergWriteAggregator，修复 DynamicWriteResultAggregator 的重复提交问题。

**工作逻辑**：与 IcebergWriteAggregator 完全相同的模式——新增 `lastCheckpointId` 字段、`initializeState` 方法、`finish()` 中使用 `lastCheckpointId + 1`、`prepareSnapshotPreBarrier()` 中防重复检查。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergCommitter.java` (+2/-2 lines)

**修改目的**：适配 checkpoint ID 不再使用 `Long.MAX_VALUE` 的变更。

**工作逻辑**：将测试中的 `checkpointId` 从 `Long.MAX_VALUE` 改为 `1`，对应的 `assertMaxCommittedCheckpointId` 断言也从 `Long.MAX_VALUE` 改为 `checkpointId`（即 1）。这与 IcebergWriteAggregator 中 `finish()` 不再使用 `Long.MAX_VALUE` 的变更保持一致。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/util/TestFlinkPackage.java` (+1/-1 lines)

**修改目的**：更新 Flink 版本断言。

**工作逻辑**：将 `assertThat(FlinkPackage.version()).isEqualTo("2.0.0")` 改为 `assertThat(FlinkPackage.version()).isEqualTo("2.1.0")`。

## 总结

本提交对 Flink 2.1 目录中的代码进行了适配修改，主要处理两类变更：一是 Flink 2.1 新增的 Variant 类型支持（在三个数据读取/投影类中实现 `getVariant` 方法），二是修复 checkpoint 提交时序变更导致的潜在重复提交问题（在两个 WriteAggregator 中增加 lastCheckpointId 追踪和防重复逻辑）。此外还更新了相关测试断言以匹配 Flink 2.1 的行为。这些改动确保了 Iceberg 的 Flink 集成代码能够正确编译和运行在 Flink 2.1 上。
