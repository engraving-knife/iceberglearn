# 提交 2018：Flink: Add StreamingStartingStrategy.INCREMENTAL_FROM_LATEST_SNAPSHOT_EXCLUSIVE (#12839)

## 提交信息

- **序号**：2018 / 4088
- **哈希**：ad6d6cd47458f509e7b395fb7bc7e6bb8cd26318
- **短哈希**：ad6d6cd47
- **日期**：2025-04-21 10:37:32 -0700
- **作者**：Matyas Orhidi
- **提交说明**：Flink: Add StreamingStartingStrategy.INCREMENTAL_FROM_LATEST_SNAPSHOT_EXCLUSIVE (#12839)
- **PR/Issue**：#12839

## 总体目的

这个提交为 Flink Iceberg source 新增了一个流式启动策略 `INCREMENTAL_FROM_LATEST_SNAPSHOT_EXCLUSIVE`，允许从最新快照的下一个快照开始增量消费，即排除最新快照本身。

此前 Flink Iceberg source 提供了以下流式启动策略：
- `TABLE_SCAN_THEN_INCREMENTAL`：先全表扫描，然后增量消费
- `INCREMENTAL_FROM_LATEST_SNAPSHOT`：从最新快照开始（包含），增量消费
- `INCREMENTAL_FROM_EARLIEST_SNAPSHOT`：从最早快照开始（包含），增量消费

其中 `INCREMENTAL_FROM_LATEST_SNAPSHOT` 是包含最新快照的（inclusive），但缺少一个排除最新快照的选项。某些场景下用户希望从最新快照之后开始消费（即只处理新写入的数据，不处理最新快照中已有的数据），这就需要 `INCREMENTAL_FROM_LATEST_SNAPSHOT_EXCLUSIVE` 策略。

## 如何达成设计目的

1. 在 `StreamingStartingStrategy` 枚举中新增 `INCREMENTAL_FROM_LATEST_SNAPSHOT_EXCLUSIVE` 值。
2. 在 `ContinuousSplitPlannerImpl` 中处理新策略：与 `TABLE_SCAN_THEN_INCREMENTAL` 类似，将起始快照位置设为当前快照（作为 toPosition），但不生成该快照的 splits（splits 为空列表），从而实现"排除"语义。
3. 在 `matchStartingStrategy` 方法中，新策略与 `INCREMENTAL_FROM_LATEST_SNAPSHOT` 一样使用 `table.currentSnapshot()` 作为起始快照。
4. 更新文档和测试。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/source/StreamingStartingStrategy.java` (修改, +8/-2 lines)

**修改目的**：新增 `INCREMENTAL_FROM_LATEST_SNAPSHOT_EXCLUSIVE` 枚举值。

**工作逻辑**：
在 `INCREMENTAL_FROM_LATEST_SNAPSHOT` 之后新增 `INCREMENTAL_FROM_LATEST_SNAPSHOT_EXCLUSIVE`，带有 Javadoc 说明"Start incremental mode from the latest snapshot exclusive"。同时修正了其他策略注释中的笔误（"empty map" 改为 "empty table"）。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/source/enumerator/ContinuousSplitPlannerImpl.java` (修改, +12/-2 lines)

**修改目的**：实现新策略的增量扫描逻辑。

**工作逻辑**：
1. `planSplits` 方法中，将 `splits` 初始化为 `Collections.emptyList()`（原来是延迟赋值）。新增 `else if` 分支处理 `INCREMENTAL_FROM_LATEST_SNAPSHOT_EXCLUSIVE`：将 `toPosition` 设为当前快照的 ID 和时间戳，但不生成 splits（保持为空列表），从而实现排除最新快照的语义。记录日志说明以 exclusive 模式启动。

2. `matchStartingStrategy` 方法中，新增 `case INCREMENTAL_FROM_LATEST_SNAPSHOT_EXCLUSIVE`，与 `INCREMENTAL_FROM_LATEST_SNAPSHOT` 一样返回 `table.currentSnapshot()` 作为起始快照。

### `docs/docs/flink-configuration.md` (修改, +1/-1 lines)

**修改目的**：更新文档中流式启动策略的描述。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/source/enumerator/TestContinuousSplitPlannerImpl.java` (修改, +47/-6 lines)

**修改目的**：新增新策略的测试用例。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/source/enumerator/TestContinuousSplitPlannerImplStartStrategy.java` (修改, +25/-4 lines)

**修改目的**：新增策略枚举值的测试。

## 总结

本提交为 Flink Iceberg source 新增了 `INCREMENTAL_FROM_LATEST_SNAPSHOT_EXCLUSIVE` 流式启动策略，允许用户从最新快照之后开始增量消费（排除最新快照本身），填补了现有策略的空白。实现方式是将起始快照位置记录为 toPosition 但不生成 splits，与 `TABLE_SCAN_THEN_INCREMENTAL` 的 exclusive 行为一致。
