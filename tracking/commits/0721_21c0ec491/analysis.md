# 提交分析：Flink: Backport #10208 to v1.18 and v1.17 (#10230)

## 提交信息

- **提交哈希**: 21c0ec491d394ff15739d986f3bae460cdd722e4
- **短哈希**: 21c0ec491
- **作者**: pvary (peter.vary.apache@gmail.com)
- **提交日期**: Fri Apr 26 15:51:37 2024 +0200
- **提交信息**: Flink: Backport #10208 to v1.18 and v1.17 (#10230)
- **影响文件**: 8 个文件，198 行新增，74 行删除

## 总体目的

本提交将 PR #10208 的修复回迁（backport）到 Flink v1.17 和 v1.18 两个维护分支。该修复解决了一个有界（批式）Iceberg Source 在从 checkpoint/savepoint 恢复时重复规划 splits 的 Bug，该 Bug 会导致数据重复读取，破坏 exactly-once 语义。

## 如何达成设计目的

### Bug 成因

在 `IcebergSource.createReader` 方法中，对于批式（静态）源，无论是否从 checkpoint 状态恢复，都会执行 `planSplitsForBatch` 进行全量 split 规划，并通过 `assigner.onDiscoveredSplits(splits)` 将 splits 添加到分配器中。

问题在于：当作业从 checkpoint/savepoint 恢复时，`enumState` 中已经保存了之前已发现的 splits（包括尚未被读取的 splits），这些 splits 会通过状态恢复被重新加载到 assigner 中。如果此时再次执行全量 split 规划，就会把所有 splits 重复添加到 assigner，导致已处理和未处理的 splits 都被重复分配，最终造成数据重复消费。

### 修复逻辑

在批式源的分支中增加 `enumState == null` 判断：只有当没有从 checkpoint 状态恢复任何内容时（即首次启动），才执行 split 规划。当 `enumState` 不为 null（从 checkpoint 恢复）时，splits 已通过状态恢复进入 assigner，无需重新规划。

```java
// 修复前
List<IcebergSourceSplit> splits = planSplitsForBatch(planningThreadName());
assigner.onDiscoveredSplits(splits);

// 修复后
if (enumState == null) {
  // Only do scan planning if nothing is restored from checkpoint state
  List<IcebergSourceSplit> splits = planSplitsForBatch(planningThreadName());
  assigner.onDiscoveredSplits(splits);
}
```

## 修改详情

### 1. flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/source/IcebergSource.java
- **变更**: 在 `createReader` 方法的批式源分支中，为 `planSplitsForBatch` 调用增加 `enumState == null` 守卫条件。
- **影响**: 这是核心修复，防止从 checkpoint 恢复时重复规划 splits。

### 2. flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/source/IcebergSource.java
- **变更**: 与 v1.17 完全相同的核心修复。

### 3. flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/SimpleDataUtil.java
- **变更**: 简化 `assertTableRecords(Table, List<Record>, Duration)` 方法，移除了冗余的 `equalsRecords` 调用。
- **原因**: `equalsRecords` 仅打印日志而不做断言，`assertRecordsEqual` 才是真正执行断言的方法。移除冗余调用使测试断言更清晰。

### 4. flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/SimpleDataUtil.java
- **变更**: 与 v1.17 相同的简化。

### 5. flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceFailover.java
- **变更**:
  - 新增 `testBoundedWithSavepoint` 测试方法：验证有界源在 savepoint 恢复后不产生重复数据。该测试先启动作业写入部分数据，通过 `stopWithSavepoint` 停止作业并创建 savepoint，然后从 savepoint 恢复继续执行，最终断言无数据重复。
  - 将 `PARALLELISM` 从 4 改为 2：注释说明目的是让并行度大于 1 但小于部分测试使用的 split 数量，使恢复时仍有部分 splits 留在 enumerator 中。
  - 新增 `DO_NOT_FAIL` 常量（`Integer.MAX_VALUE`）：用于从 savepoint 恢复时不触发失败。
  - 在 `sourceBuilder` 中设置 `FlinkReadOptions.SPLIT_FILE_OPEN_COST` 为 `SPLIT_SIZE_DEFAULT`：防止 splits 被合并，确保有足够的 split 数量来验证恢复逻辑。
  - 抽取 `createBoundedStreams` 私有方法：将创建有界流的逻辑封装，接收 `failAfter` 参数，供 failover 测试和 savepoint 测试复用。

### 6. flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceFailover.java
- **变更**: 与 v1.17 相同的测试改动。

### 7. flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceFailoverWithWatermarkExtractor.java
- **变更**: 简化 `assertRecords` 方法中的 Awaitility 断言，移除冗余的 `equalsRecords` 调用，仅保留 `assertRecordsEqual`。

### 8. flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceFailoverWithWatermarkExtractor.java
- **变更**: 与 v1.17 相同的简化。

## 小结

### 成效
- 修复了有界 Iceberg Flink Source 从 checkpoint/savepoint 恢复时数据重复的严重 Bug，恢复了 exactly-once 语义保证。
- 新增的 `testBoundedWithSavepoint` 测试覆盖了 savepoint 恢复场景，防止回归。
- 简化了测试工具方法，去除了仅打印日志不做断言的冗余调用。

### 影响范围
- 仅影响 Flink 集成模块的 v1.17 和 v1.18 分支。
- 核心修复位于 `IcebergSource.createReader`，仅影响批式（有界）源从 checkpoint 恢复的行为。
- 对流式（连续）源无影响，因其使用 `ContinuousIcebergEnumerator`，不经过该代码路径。

### 回迁注意事项
- 此提交本身即为回迁（从 main 分支的 #10208 回迁到 v1.17/v1.18），v1.19 分支已包含该修复。
- 回迁时同步修改了 v1.17 和 v1.18 两套代码，两套代码改动完全一致。
- 测试中调整了 `PARALLELISM` 和 split 合并策略，这些是测试基础设施调整，确保新测试能正确触发恢复时 split 残留的场景。
