# 提交分析：Flink: Remove reading of the data files to fix flakiness

## 提交信息

- 哈希: 13d2160bd00e08e1e700a980f2a3e64017211bb8
- 短哈希: 13d2160bd
- 日期: 2024-01-16 09:23:54 -0800
- 作者: pvary
- 说明: Flink: Remove reading of the data files to fix flakiness (#9451)

## 总体目的

本次提交修复 `TestIcebergSourceWithWatermarkExtractor` 测试用例的 flakiness（不稳定失败）。该测试在 Flink 1.16、1.17、1.18 三个模块中各有一份几乎相同的副本（Iceberg 对每个 Flink 版本维护独立模块），三份副本同时存在相同的 flaky 行为，因此本次提交对三个文件做了一致性修改。

测试本身的目的是验证 IcebergSource 在流式模式下配合 Flink `WatermarkAlignment`（最大漂移 `maxWatermarkDrift=20min`、更新间隔 `updateInterval=10ms`）时的水位对齐行为：被对齐机制节流的 reader 应在新增足够新的数据后解除阻塞，水位漂移指标（`WATERMARK_ALIGNMENT_DRIFT`）应从 80min → 65min → <20min 三个阶段演进。测试的"正确性锚点"本应是这个漂移指标的变化轨迹，而旧版本测试额外在三个数据写入阶段之后通过 `waitForRecords(resultIterator, N)` 主动从结果迭代器中读取若干条记录来断言"已经读到了 N 条数据"。问题在于，这些"读到了 N 条"的断言本质上依赖于 Flink 运行时线程调度：当被节流的 reader 处于阻塞状态时，它究竟会先消费几条旧记录、未阻塞的 reader 会不会同时抢到两个 split、记录到达 `CollectSink` 的时序如何，这些都是不确定的——旧代码注释自己也承认"1 or more from the runaway reader should be arrived depending on thread scheduling"。

flakiness 的根因正是这种"用易变的副作用（消费到的记录数）来验证不变量（漂移指标）"的做法。在不同 CI 负载、JVM 调度策略下，`waitForRecords` 在 `DEFAULT_COLLECT_DATA_TIMEOUT` 内可能等不到预期数量的记录（因为 reader 被阻塞或调度延迟），从而抛出 `IllegalStateException("Fail to get %d records.")` 让测试失败，但被测逻辑本身（水位对齐）实际上是正确的。

修复思路是剥离所有"读数据文件并断言记录数"的步骤，只保留基于 `findAlignmentDriftMetric` + `Awaitility` 的指标断言——后者才是测试真正想要验证的不变量，且对线程调度不敏感（只依赖 Flink metric 的最终值）。由此移除三个 `waitForRecords` 调用点以及不再被使用的 `assertRecords`、`waitForRecords` 辅助方法和相关导入。

## 如何达成设计目的

实现层面是"删除式修复"：在主测试方法 `testWatermarkExtractor`（实际方法名见上下文）中，三处 `dataAppender.appendToTable(...)` 之后紧跟的 `waitForRecords(resultIterator, ...)` 调用连同其上的解释性注释一并删除；类尾部移除 `assertRecords(Collection<Record>, CloseableIterator<RowData>)` 与 `waitForRecords(CloseableIterator<RowData>, int)` 两个 helper 方法；清理因此变为未使用的导入（`DEFAULT_COLLECT_DATA_TIMEOUT`、`assertThat`、`Collection`、`Set`、`CompletableFuture`、`Collectors`、`RowDataConverter`、`Sets`、`Assert`）。三个 Flink 版本目录下的同名文件做相同修改，保证各版本行为一致。删除后测试方法只保留：写批次 → `Awaitility` 等待漂移指标达到期望值 → 再写下一批次 → 再等待指标 → ……，全部以指标为唯一断言点，消除线程调度相关的非确定性。

## 修改详情

### flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceWithWatermarkExtractor.java

**修改目的**：移除 flaky 的数据读取断言，仅保留水位漂移指标断言。

**工作逻辑**：
- 删除导入：`DEFAULT_COLLECT_DATA_TIMEOUT`、`assertThat`、`Collection`、`Set`、`CompletableFuture`、`Collectors`、`RowDataConverter`、`Sets`、`org.junit.Assert`。
- 主测试方法中三处删除：
  1. 写入 batch1+batch2 后的 `waitForRecords(resultIterator, RECORD_NUM_FOR_2_SPLITS + 1)` 及其上"1 or more from the runaway reader..."注释——该断言依赖未阻塞 reader 提前消费的记录数，调度敏感。
  2. 写入 batch3+batch4 后的 `waitForRecords(resultIterator, 3)` 及注释——同样依赖线程调度决定哪个 reader 拿到哪个 split。
  3. 写入 batch5 后的 `waitForRecords(resultIterator, 6)`——断言"全部记录都被读到"，但其本质上会被随后的 drift < 20min 断言覆盖。
- 类尾删除 `assertRecords` 与 `waitForRecords` 两个 helper：`waitForRecords` 内部用 `CompletableFuture.supplyAsync` 在 `DEFAULT_COLLECT_DATA_TIMEOUT` 内尝试从迭代器拉取 N 条记录，拉不够就抛 `IllegalStateException`，并由 `assertThat(...).succeedsWithin(...)` 包装——这正是 flakiness 的具体来源。删除后这些代码不再被引用。
- 保留：基于 `Awaitility` 的三段 `findAlignmentDriftMetric` 断言（80min 存在 → drift==65min → drift<20min），它们是测试真正的不变量。

### flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceWithWatermarkExtractor.java

**修改目的**：对 Flink 1.17 模块做与 1.16 完全一致的 flakiness 修复。

**工作逻辑**：与 1.16 文件逐字相同的删除（导入、三处 `waitForRecords` 调用、两个 helper 方法）。Iceberg 仓库中各 Flink 版本模块的测试文件常以拷贝方式同步，故 1.16/1.17/1.18 三份文件的 diff 内容完全一致。

### flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceWithWatermarkExtractor.java

**修改目的**：对 Flink 1.18 模块做相同的 flakiness 修复。

**工作逻辑**：与上述两份文件完全一致，删除三处 `waitForRecords` 调用与两个 helper 方法及相关导入，保留漂移指标断言作为唯一不变量。

## 小结

本次提交通过删除 `TestIcebergSourceWithWatermarkExtractor`（Flink 1.16/1.17/1.18 三份副本）中所有"主动读取结果迭代器并断言记录数"的逻辑（三处 `waitForRecords` 调用 + `assertRecords`/`waitForRecords` 两个 helper + 相关导入），消除了因 Flink 运行时线程调度不确定性导致的测试 flakiness。修复后的测试只保留对水位对齐漂移指标（`WATERMARK_ALIGNMENT_DRIFT`）的 `Awaitility` 断言——即测试真正想要验证的不变量——使测试在不牺牲验证能力的前提下变得确定性可重复。
