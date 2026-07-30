# 提交 0719：Flink: Fix bounded source state restore record duplication

## 提交信息
- **序号**：0719 / 4088
- **哈希**：b7d3a7f9d33aae2a85f9a742ed1ab87608283d28
- **短哈希**：b7d3a7f9d
- **日期**：2024-04-26 12:30:16 +0200
- **作者**：pvary <peter.vary.apache@gmail.com>
- **提交说明**：Flink: Fix bounded source state restore record duplication (#10208)
- **PR/Issue**：#10208

## 总体目的

本提交修复 Flink Iceberg Source 在有界（bounded/batch）模式下，从 checkpoint/savepoint 恢复状态时产生的**记录重复（record duplication）**问题。这是一个违反 exactly-once 语义的正确性 bug。

**Bug 成因分析**：

在 `IcebergSource.createEnumerator()` 方法中，当 Flink 从 checkpoint 或 savepoint 恢复作业时，会传入一个非 null 的 `IcebergEnumeratorState enumState`，其中包含了 checkpoint 时刻尚未处理完的 pending splits。代码首先用 `enumState.pendingSplits()` 创建 assigner，将这些 pending splits 恢复到 assigner 中。

问题出在 batch 分支：**修复前**，无论 `enumState` 是否为 null，代码都会无条件调用 `planSplitsForBatch()` 重新扫描表并发现所有 splits，然后通过 `assigner.onDiscoveredSplits(splits)` 加入 assigner。这意味着：

1. 从 checkpoint 状态恢复的 pending splits 已经在 assigner 中
2. `planSplitsForBatch()` 又重新扫描整张表，发现**所有** splits（包括那些在 checkpoint 之前已经被处理完的 splits）
3. 这些重新发现的 splits 也被加入 assigner
4. 结果：已经处理过的记录会被再次读取，造成记录重复

这本质上是一个 exactly-once 语义被破坏的问题。在有界模式下，`StaticIcebergEnumerator` 不会做增量扫描，它依赖 `createEnumerator` 时一次性发现所有 splits。当从 checkpoint 恢复时，正确的做法应该是只使用 checkpoint 中保存的 pending splits，而不是重新扫描全表。

**与流式模式的对比**：流式模式（streaming）不存在这个问题，因为它使用 `ContinuousIcebergEnumerator`，该枚举器会在运行时持续做增量扫描，且 `createEnumerator` 中流式分支没有调用 `planSplitsForBatch`。

## 如何达成设计目的

修复策略简洁而精准：在 batch 分支中，仅当 `enumState == null`（即首次启动、无状态恢复）时才调用 `planSplitsForBatch()` 进行全表扫描。当 `enumState != null`（从 checkpoint/savepoint 恢复）时，跳过扫描，直接使用从状态恢复的 assigner（其中已包含 pending splits）。

这个修复的逻辑前提是：checkpoint 保存的 `enumState.pendingSplits()` 已经包含了所有尚未处理的 splits，不需要重新发现。对于有界作业，splits 的集合在作业启动时就是固定的（不像流式作业会有新增数据），因此从 checkpoint 恢复时，pending splits 集合就是剩余待处理的全部 splits。

同时，本提交还完善了测试基础设施，新增了 `testBoundedWithSavepoint` 测试用例，专门验证从 savepoint 恢复时有界作业不会产生记录重复。这是验证修复有效性的关键。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/source/IcebergSource.java`
**修改目的**：修复有界模式从 checkpoint 恢复时的记录重复问题。

**工作逻辑**：在 `createEnumerator()` 方法的 batch 分支中，将原来的无条件扫描：
```java
} else {
  List<IcebergSourceSplit> splits = planSplitsForBatch(planningThreadName());
  assigner.onDiscoveredSplits(splits);
  return new StaticIcebergEnumerator(enumContext, assigner);
}
```
修改为带条件判断的扫描：
```java
} else {
  if (enumState == null) {
    // Only do scan planning if nothing is restored from checkpoint state
    List<IcebergSourceSplit> splits = planSplitsForBatch(planningThreadName());
    assigner.onDiscoveredSplits(splits);
  }
  return new StaticIcebergEnumerator(enumContext, assigner);
}
```

注释 "Only do scan planning if nothing is restored from checkpoint state" 明确说明了设计意图：仅在没有从 checkpoint 状态恢复任何内容时才执行扫描规划。当 `enumState != null` 时，assigner 已经通过 `assignerFactory.createAssigner(enumState.pendingSplits())` 恢复了 pending splits，无需再次扫描。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/SimpleDataUtil.java`
**修改目的**：简化 `assertTableRecords` 方法的断言逻辑，移除冗余的 `equalsRecords` 调用。

**工作逻辑**：原方法在 `untilAsserted` 中同时调用了 `equalsRecords` 和 `assertRecordsEqual` 两个方法。`equalsRecords` 返回 boolean 但不抛出有意义的断言错误信息，而 `assertRecordsEqual` 会使用 AssertJ 提供清晰的失败信息。修改后只保留 `assertRecordsEqual`，避免重复比较且改善错误信息质量。这为后续测试失败时的诊断提供更好的信息。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceFailover.java`
**修改目的**：新增 `testBoundedWithSavepoint` 测试用例验证修复，并重构测试基础设施以支持复用。

**工作逻辑**：

1. **调整并行度**：`PARALLELISM` 从 4 改为 2。注释说明："大于 1 但低于某些测试使用的 split 数量，目的是让恢复状态时 enumerator 中仍有一些未分配的 splits"。这个设计确保测试能覆盖"部分 splits 已分配、部分仍在 enumerator"的场景。

2. **新增 `DO_NOT_FAIL` 常量**：值为 `Integer.MAX_VALUE`，用于恢复后的作业不触发失败。

3. **防止 split 合并**：在 `sourceBuilder()` 中设置 `FlinkReadOptions.SPLIT_FILE_OPEN_COST` 为 `TableProperties.SPLIT_SIZE_DEFAULT`，目的是防止小文件被合并成更少的 splits，确保测试中 split 数量可控。

4. **新增 `testBoundedWithSavepoint` 测试**：
   - 生成 4 批数据，每批 2 条记录，共 8 条
   - 启动作业（并行度 2），设置在处理到一半时失败
   - 等待作业处理部分记录后，使用 `stopWithSavepoint` 停止作业并创建 savepoint
   - 验证 sink 表中已有部分记录（`hasSizeGreaterThan(0)`）
   - 从 savepoint 恢复作业（设置 `SAVEPOINT_PATH`），不触发失败
   - 执行完毕后，断言 sink 表中的记录与期望完全一致，**无重复**（`assertRecords(sinkTable, expectedRecords, ...)`）
   - 这个测试直接验证了修复的有效性：如果 bug 未修复，恢复后会重复读取已处理的记录，断言会失败

5. **提取 `createBoundedStreams` 方法**：将原来 `testBoundedIcebergSource` 中构建流的逻辑提取为独立方法，接收 `failAfter` 参数控制失败时机，供 `testBoundedWithSavepoint` 和 `testBoundedIcebergSource` 复用。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceFailoverWithWatermarkExtractor.java`
**修改目的**：同步简化 `assertRecords` 方法的断言逻辑，与 `SimpleDataUtil` 的修改保持一致。

**工作逻辑**：移除 `equalsRecords` 调用，只保留 `assertRecordsEqual`，与 `SimpleDataUtil.assertTableRecords` 的修改保持一致。

## 小结

- **成效**：成功达成目的。修复后，有界模式从 checkpoint/savepoint 恢复时不再重新扫描全表，消除了记录重复问题，恢复了 exactly-once 语义。新增的 `testBoundedWithSavepoint` 测试通过 savepoint 恢复场景直接验证了修复的有效性。
- **影响范围**：仅影响 Flink v1.19 集成模块的 `IcebergSource` 类（核心修复）和测试类。修复点非常精准——只在 batch 分支增加 `enumState == null` 条件判断，不影响流式模式的行为。
- **回迁到 1.4.x 的注意事项**：
  1. **必须回迁**：这是一个正确性 bug 修复，影响 exactly-once 语义，任何使用有界模式 + checkpoint/savepoint 恢复的用户都会受影响，应优先回迁。
  2. 需要确认 1.4.x 分支支持哪些 Flink 版本。本提交只修改了 v1.19，如果 1.4.x 还支持 v1.17、v1.18 等版本，需要检查这些版本的 `IcebergSource` 是否存在同样的 bug 并同步修复。
  3. 核心修复（`IcebergSource.java` 中增加 `enumState == null` 判断）非常简单且低风险，回迁时应重点保证测试也能同步回迁，以验证修复在 1.4.x 上的有效性。
  4. 测试中使用的 `stopWithSavepoint` API 和 `SavepointFormatType.CANONICAL` 需要确认 1.4.x 对应的 Flink 版本是否支持。
