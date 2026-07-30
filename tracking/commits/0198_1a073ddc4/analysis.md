# 提交 0198：Flink: Backport #8803 to v1.16 and v1.15 (#9144)

## 提交信息

- **序号**：0198 / 4088
- **哈希**：1a073ddc47d53c005adcbb35399da6255123b652
- **短哈希**：1a073ddc4
- **日期**：2023-11-24
- **作者**：pvary
- **提交说明**：Flink: Backport #8803 to v1.16 and v1.15 (#9144)
- **PR/Issue**：#9144（回 port #8803 到 v1.16 与 v1.15）

## 总体目的

这个提交把主线 PR #8803 引入的功能回 port 到 Iceberg 的 Flink v1.15 与 v1.16 两个维护分支。#8803 的核心功能是：为 Flink Iceberg source 增加按"指定列集合"读取列统计信息（column stats）的能力，而在此之前 Flink source 只能选择"全部列的统计"或"完全不要统计"两种极端模式。

在 Iceberg 的 scan API 层面，`TableScan` 已支持 `includeColumnStats(Collection<String> columns)` 来按需加载部分列的统计信息（value counts、column sizes、lower/upper bounds 等），用于在查询计划时做更精细的过滤裁剪。但 Flink source 的 `ScanContext` 与 `FlinkSplitPlanner` 此前只暴露了一个布尔开关 `includeColumnStats`，无法把"只关心某几列的统计"这一需求传递到底层 scan。#8803 在 `ScanContext` 中新增 `includeStatsForColumns` 字段（`Collection<String>`），并在 `FlinkSplitPlanner` 中当该字段非空时调用 `refinedScan.includeColumnStats(columns)`，从而让 Flink source 也能按列粒度请求统计信息。本次 #9144 是把该功能同步到仍在维护的 v1.15/v1.16 两个老版本分支，保证这两个版本的用户也能享受该优化。这对减少 Flink 读取场景下不必要的统计信息加载、提升查询计划效率具有实际价值。

## 如何达成设计目的

回 port 在 v1.15 与 v1.16 两个分支上做对称改动，每个分支改三个文件：`ScanContext` 新增 `includeStatsForColumns` 字段、构造参数、getter、Builder 的重载 `includeColumnStats(Collection<String>)` 方法，并在两处 `copy`/`copyWithoutMaxPlanningSnapshotCount` 风格的 builder 链中传递该字段；`FlinkSplitPlanner` 在已有的 `includeColumnStats` 布尔分支后新增对 `includeStatsForColumns` 的处理；`TestContinuousSplitPlannerImpl` 新增三个测试覆盖"无统计/全统计/单列统计"三种场景，并重构 `verifyOneCycle` 使其同时返回位置与 split 以便校验统计数量。

## 修改详情

### `flink/v1.15/flink/src/main/java/org/apache/iceberg/flink/source/ScanContext.java` 与 `flink/v1.16/.../ScanContext.java`

**修改目的**：在 Flink source 的扫描上下文中承载"按列请求统计信息"的能力。

**工作逻辑**：新增 `import java.util.Collection;`，新增字段 `private final Collection<String> includeStatsForColumns;`；构造方法增加该参数并赋值；新增 getter `public Collection<String> includeStatsForColumns()`；在 Builder 中新增字段 `private Collection<String> includeStatsForColumns = null;` 与重载方法 `public Builder includeColumnStats(Collection<String> newIncludeStatsForColumns)`（与原有布尔版 `includeColumnStats(boolean)` 形成重载）；在两处 builder 链（`copy()` 与另一处构建新 `ScanContext` 的方法）中追加 `.includeColumnStats(includeStatsForColumns)` 调用以传递该值；在 Builder 的 `build()` 中把 `includeStatsForColumns` 加入构造实参列表。

### `flink/v1.15/flink/src/main/java/org/apache/iceberg/flink/source/FlinkSplitPlanner.java` 与 `flink/v1.16/.../FlinkSplitPlanner.java`

**修改目的**：在扫描计划阶段把"按列统计"的需求传递到底层 Iceberg `TableScan`。

**工作逻辑**：在原有 `if (context.includeColumnStats()) { refinedScan = refinedScan.includeColumnStats(); }` 之后新增 `if (context.includeStatsForColumns() != null) { refinedScan = refinedScan.includeColumnStats(context.includeStatsForColumns()); }`，即当指定了列集合时调用 `includeColumnStats(Collection)` 重载。

### `flink/v1.15/flink/src/test/java/org/apache/iceberg/flink/source/enumerator/TestContinuousSplitPlannerImpl.java` 与 `flink/v1.16/.../TestContinuousSplitPlannerImpl.java`

**修改目的**：验证三种统计模式的正确性并适配测试基础设施。

**工作逻辑**：
- 重构 `verifyOneCycle`：返回类型由 `IcebergEnumeratorPosition` 改为新增内部类 `CycleResult`（持有 `lastPosition` 与 `split`），以便后续校验 split 内文件的统计数量；所有调用点由 `lastPosition = verifyOneCycle(...)` 改为 `lastPosition = verifyOneCycle(...).lastPosition`。
- 新增 `CycleResult` 静态内部类（构造字段 `lastPosition`、`split`）。
- 新增 `verifyStatCount(IcebergSourceSplit split, int expected)`：`expected==0` 时断言 valueCounts/columnSizes/lowerBounds/upperBounds/nanValueCounts/nullValueCounts 均为 null；否则断言各统计 Map 的 size 等于 expected（nanValueCounts 对 long/string 字段为 0）。
- 新增三个测试：`testTableScanNoStats`（`includeColumnStats(false)`，期望统计为 0）、`testTableScanAllStats`（`includeColumnStats(true)`，期望 3 列全统计）、`testTableScanSingleStat`（`includeColumnStats(ImmutableSet.of("data"))`，期望仅 1 列统计）；每个测试在初始计划与 3 轮增量循环中均调用 `verifyStatCount`。
- 移除 `testIncrementalFromSnapshotIdWithEmptyTable` 与 `testIncrementalFromSnapshotTimestampWithEmptyTable` 上不必要的 `throws Exception` 声明。

## 小结

该提交把 #8803 的"按列请求统计信息"能力回 port 到 Flink v1.15/v1.16，使这两个维护版本也能按需加载部分列统计，减少不必要的统计开销，提升 Flink source 的查询计划效率。
