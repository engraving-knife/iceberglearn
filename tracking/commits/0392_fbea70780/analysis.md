# 提交 0392：Spark: Fix flaky TestSparkReaderDeletes tests due to metric not found

## 提交信息

- **序号**：0392
- **哈希**：fbea7078064039bb7bd89c54fdbd61efff857feb
- **短哈希**：fbea70780
- **日期**：2024-01-19（Fri Jan 19 15:07:07 2024 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Spark: Fix flaky TestSparkReaderDeletes tests due to metric not found
- **PR/Issue**：#9445

## 总体目的

本提交用于修复 `TestSparkReaderDeletes` 测试类在 Spark 3.3、3.4、3.5 三个版本下的 flaky（不稳定）问题。该测试类继承自 `DeleteReadTests`，用于验证 Iceberg Spark 引擎在读取带删除标记的数据时的正确行为，是 Iceberg-Spark 集成中较为重要的测试之一。

不稳定现象表现为 "metric not found" 错误，即测试在断言某些 Spark SQL metric 时找不到对应的 metric。根因在于 Spark UI 的 live update 机制：Spark UI 默认会以固定周期（liveUpdate.period）异步更新 AppStatusStore 中的任务 metric 信息。当测试用例执行速度非常快时，断言发生在 live update 周期到达之前，此时 AppStatusStore 中尚未刷新出最新的 metric，导致查询 metric 失败。这是一种典型的"竞态条件"型 flaky test。

通过将 `spark.ui.liveUpdate.period` 设置为 0，可以让 Spark UI 在每次状态变更时立即同步更新 AppStatusStore，从而消除异步刷新带来的延迟。这与已有的 `spark.appStateStore.asyncTracking.enable=false` 配置思路一致——后者关闭异步跟踪，本提交进一步关闭 UI 的周期性 live update，确保测试环境下 metric 状态的实时一致性。

## 如何达成设计目的

在三个 Spark 版本（3.3、3.4、3.5）的 `TestSparkReaderDeletes.java` 中，于 `SparkSession.builder()` 配置链上增加一行 `.config("spark.ui.liveUpdate.period", 0)`，与已有的 `spark.appStateStore.asyncTracking.enable=false` 紧邻。这样在测试初始化阶段就禁用了 UI 的周期性 live update，使得 metric 一经产生即可见，从根本上消除了"metric not found"的竞态条件。

## 修改详情

### spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkReaderDeletes.java

**修改目的**：修复 Spark 3.3 版本下 `TestSparkReaderDeletes` 测试因 "metric not found" 导致的 flaky 问题。

**工作逻辑**：在 `SparkSession.builder()` 的链式配置中，紧接 `spark.appStateStore.asyncTracking.enable=false` 之后新增 `.config("spark.ui.liveUpdate.period", 0)`。`spark.ui.liveUpdate.period` 是 Spark UI 控制 AppStatusStore live entity 更新周期的配置项，默认值为 100ms（即每 100ms 刷新一次）。设置为 0 表示禁用周期性刷新，转为事件驱动方式即时更新。这样一来，测试中查询 metric 时不再受周期性刷新延迟的影响，确保 metric 在任务完成后立即可见。

### spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkReaderDeletes.java

**修改目的**：修复 Spark 3.4 版本下 `TestSparkReaderDeletes` 测试的同类 flaky 问题。

**工作逻辑**：与 Spark 3.3 完全一致的修改方式，在 `SparkSession.builder()` 配置链中新增 `.config("spark.ui.liveUpdate.period", 0)`。值得注意的是，Spark 3.4 与 3.5 的该文件起始内容完全相同（diff 中两个文件的 index hash 一致），因此修改位置和方式保持一致。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkReaderDeletes.java

**修改目的**：修复 Spark 3.5 版本下 `TestSparkReaderDeletes` 测试的同类 flaky 问题。

**工作逻辑**：与 Spark 3.4 完全一致的修改，新增 `.config("spark.ui.liveUpdate.period", 0)` 配置项，确保三个 Spark 版本下的测试行为一致。

## 小结

本提交通过一行配置修复了一个典型的"竞态条件"型 flaky test。`spark.ui.liveUpdate.period=0` 与已有的 `spark.appStateStore.asyncTracking.enable=false` 形成组合拳，从测试环境层面彻底消除 Spark UI 异步刷新带来的 metric 可见性延迟。修复方式简洁、低风险，且覆盖了 Iceberg 支持的三个 Spark 版本（3.3/3.4/3.5），体现了 Iceberg 在多版本 Spark 适配中保持测试一致性的一贯做法。这类 flaky test 修复对保障 CI 流水线稳定性具有重要意义。
