# 提交 2051：Flink: Backport add StreamingStartingStrategy.INCREMENTAL_FROM_LATEST_SNAPSHOT_EXCLUSIVE to Flink 1.19 (#12899)

## 提交信息

- **序号**：2051 / 4088
- **哈希**：eed6dc7f07308c51a60d7e0a6725aff3da2bd786
- **短哈希**：eed6dc7f0
- **日期**：2025-04-28 17:54:56 +0200
- **作者**：Matyas Orhidi
- **提交说明**：Flink: Backport add StreamingStartingStrategy.INCREMENTAL_FROM_LATEST_SNAPSHOT_EXCLUSIVE to Flink 1.19 (#12899) — backports #12839
- **PR/Issue**：#12899（backport #12839）

## 总体目的

Iceberg 的 Flink 集成中，`StreamingStartingStrategy` 枚举用于定义流式读取的起始位置策略。原有策略包含 `INCREMENTAL_FROM_LATEST_SNAPSHOT`（包含最新快照）等，但缺少"从最新快照排他（exclusive）开始增量读取"的策略。这种排他策略在用户希望跳过当前最新快照、仅消费此后新增数据时很有用，例如表已经存在历史数据但流式作业只关心启动之后的新变更。

本提交将 PR #12839 中的新增策略 `INCREMENTAL_FROM_LATEST_SNAPSHOT_EXCLUSIVE` 回溯（backport）到 Flink 1.19 分支，使该 Flink 版本也支持这一行为，与其他 Flink 版本保持功能一致。

## 如何达成设计目的

整体设计分为三个部分：
1. **枚举扩展**：在 `StreamingStartingStrategy` 中新增 `INCREMENTAL_FROM_LATEST_SNAPSHOT_EXCLUSIVE` 枚举常量，并补充其 Javadoc 说明（同时修正原 "empty map" 为 "empty table"）。
2. **分裂计划器分支**：在 `ContinuousSplitPlannerImpl` 的 `planSplits` 与 `startSnapshot` 方法中，为该新策略添加对应的处理分支。其行为是：起始位置指向当前最新快照但不消费其数据（即排他），后续增量扫描只读取该快照之后产生的新快照。
3. **测试覆盖**：将原有针对 `INCREMENTAL_FROM_LATEST_SNAPSHOT` 的空表测试改造为参数化测试，使两种策略共享用例；并新增针对非空表使用排他策略的用例，验证初始结果不返回 splits 但 `toPosition` 正确指向最新快照。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/source/StreamingStartingStrategy.java` (修改, +8/-3 lines)

**修改目的**：新增 `INCREMENTAL_FROM_LATEST_SNAPSHOT_EXCLUSIVE` 枚举常量，并修正 Javadoc。

**工作逻辑**：
在原 `INCREMENTAL_FROM_LATEST_SNAPSHOT` 之后插入新枚举值，Javadoc 描述为"从最新快照排他开始增量模式，空表时所有未来的 append 快照都应被发现"。同时将原有 `INCREMENTAL_FROM_LATEST_SNAPSHOT` 和 `INCREMENTAL_FROM_EARLIEST_SNAPSHOT` 的注释中"empty map"统一改为"empty table"，措辞更准确。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/source/enumerator/ContinuousSplitPlannerImpl.java` (修改, +10/-2 lines)

**修改目的**：在分裂计划器中处理新策略的排他行为。

**工作逻辑**：
- 将局部变量 `splits` 的初值由未初始化改为 `Collections.emptyList()`，避免在新分支中遗漏赋值。
- 在 `planSplits` 中新增 `else if` 分支：当策略为 `INCREMENTAL_FROM_LATEST_SNAPSHOT_EXCLUSIVE` 时，不消费起始快照的数据（splits 保持空），仅将 `toPosition` 设置为起始快照的 id 与时间戳，并打印日志。这实现了"排他"语义——位置指针指向最新快照，但该快照的文件不会被作为 splits 发出。
- 在 `startSnapshot` 方法的 switch 语句中，新增 `case INCREMENTAL_FROM_LATEST_SNAPSHOT_EXCLUSIVE`，与 `INCREMENTAL_FROM_LATEST_SNAPSHOT` 共用 `Optional.ofNullable(table.currentSnapshot())` 的逻辑，即起始快照都选取当前最新快照。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/enumerator/TestContinuousSplitPlannerImpl.java` (修改, +47/-6 lines)

**修改目的**：扩展测试以覆盖新策略。

**工作逻辑**：
- 将 `testIncrementalFromLatestSnapshotWithEmptyTable` 由普通 `@Test` 改为 `@ParameterizedTest` + `@EnumSource`，参数化为 `INCREMENTAL_FROM_LATEST_SNAPSHOT` 与 `INCREMENTAL_FROM_LATEST_SNAPSHOT_EXCLUSIVE` 两种策略，复用同一空表测试逻辑。
- 新增 `testIncrementalFromLatestSnapshotExclusiveWithNonEmptyTable`：先 append 两个快照，使用排他策略进行初始 plan，断言初始 splits 为空且 `toPosition` 指向 snapshot2；随后进行第二轮 plan，断言无新文件发现且位置不变；最后通过 `verifyOneCycle` 进行 3 轮增量验证。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/enumerator/TestContinuousSplitPlannerImplStartStrategy.java` (修改, +25/-6 lines)

**修改目的**：扩展 `startSnapshot` 的测试覆盖。

**工作逻辑**：
- 将原 `testForLatestSnapshotStrategy` 重构为 `testForLatestSnapshotStrategyWithEmptyTable` 参数化测试，覆盖两种策略在空表下的 `startSnapshot` 行为（应返回 `Optional.empty()`）。
- 新增 `testForLatestSnapshotStrategyWithNonEmptyTable` 参数化测试，在 append 三个快照后验证两种策略都返回 snapshot3 作为起始快照。

## 总结

本提交将 `INCREMENTAL_FROM_LATEST_SNAPSHOT_EXCLUSIVE` 策略从主分支回溯到 Flink 1.19 模块，新增枚举常量、在 `ContinuousSplitPlannerImpl` 中实现排他式起始位置逻辑，并通过参数化测试与新用例完整覆盖空表与非空表场景，使 Flink 1.19 与其他版本功能对齐。
