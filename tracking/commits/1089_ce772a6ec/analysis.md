# 提交 1089：Flink: backport PR #10859 for range distribution (#10990)

## 提交信息

- **序号**：1089 / 4088
- **哈希**：ce772a6ecde5ea9f78a7e9145a34e74bdfb3277d
- **短哈希**：ce772a6ec
- **日期**：2024-08-22 14:59:48 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：Flink: backport PR #10859 for range distribution (#10990)
- **PR/Issue**：#10990（回迁 #10859）

## 总体目的

Iceberg 表的写分布模式（`write.distribution-mode`）支持 `none`、`hash`（按分区分桶）、`range` 三种。其中 `range` 模式可以让数据按 sort order（或分区键）的范围分布到各 writer，使写入的数据文件在排序键上聚集，提升后续查询的 data skipping 效果。但 Flink sink 此前显式禁止了 `range` 模式——`Builder.distributionMode()` 中一旦传入 `RANGE` 就抛异常"Flink does not support 'range' write distribution mode now."

本提交（回迁 main 上的 #10859）为 Flink sink 实现 `range` 分布模式：引入一个统计采集算子（`DataStatisticsOperatorFactory` + OperatorCoordinator）在线收集数据分布统计（低基数用 Map，高基数用 Sketch，Auto 模式自动切换），再通过自定义 `RangePartitioner` 按统计信息做范围分区，把记录均衡地分配到 writer。同时新增两个可调配置：统计类型（`range-distribution-statistics-type`）与 sort key 基础权重（`range-distribution-sort-key-base-weight`，用于避免长尾分区产生过多小文件）。改动同步应用到 v1.18 与 v1.20 两个 Flink 模块。

## 如何达成设计目的

1. **解除 range 禁令**：`FlinkSink.Builder.distributionMode()` 移除对 `RANGE` 抛异常的校验，改为正常存入 writeOptions。
2. **新增配置项与解析**：`FlinkWriteOptions` 新增 `RANGE_DISTRIBUTION_STATISTICS_TYPE`（Auto/Map/Sketch，默认 Auto）与 `RANGE_DISTRIBUTION_SORT_KEY_BASE_WEIGHT`（double，默认 0.0）；`FlinkWriteConf` 新增两个读取方法；`FlinkConfParser` 新增 `DoubleConfParser` 以支持 double 配置解析。
3. **range 分布流程**：在 `distributeDataStream` 的 `RANGE` 分支，要求表有 sort order 或分区 spec；若仅有分区 spec 无 sort order，则用 `Partitioning.sortOrderFor(partitionSpec)` 构造；插入 `DataStatisticsOperatorFactory` 算子采集统计，输出 `StatisticsOrRecord` 流，再用 `RangePartitioner` 做自定义分区，最后过滤掉统计消息只保留 record。
4. **writerParallelism 提前计算**：把 writer 并行度的计算从 `appendWriter` 上移到 `appendAll`，便于统计算子知道下游 writer 数量。
5. **兼容性**：range + equality fields 组合时仍回退到 keyBy（保持向后兼容并告警），因为主键场景用 range 不安全。

## 修改详情

### `flink/v1.18/.../FlinkConfParser.java` 与 `flink/v1.20/.../FlinkConfParser.java`

**修改目的**：支持 double 类型配置解析。

**工作逻辑**：新增 `doubleConf()` 工厂方法返回 `DoubleConfParser`；`DoubleConfParser` 内部支持 `defaultValue(double)`、`parse()`（要求默认值非 null）、`parseOptional()`，底层用 `Double::parseDouble` 解析。

### `flink/v1.18/.../FlinkWriteOptions.java` 与 `flink/v1.20/.../FlinkWriteOptions.java`

**修改目的**：声明 range 分布相关配置项。

**工作逻辑**：
- `RANGE_DISTRIBUTION_STATISTICS_TYPE`：`ConfigOptions.key("range-distribution-statistics-type").stringType().defaultValue(StatisticsType.Auto.name())`，描述"Type of statistics collection: Auto, Map, Sketch"。
- `RANGE_DISTRIBUTION_SORT_KEY_BASE_WEIGHT`：`ConfigOptions.key("range-distribution-sort-key-base-weight").doubleType().defaultValue(0.0d)`，描述为每个 sort key 相对 writer 任务目标权重的基础权重。

### `flink/v1.18/.../FlinkWriteConf.java` 与 `flink/v1.20/.../FlinkWriteConf.java`

**修改目的**：从配置中读取 range 分布参数。

**工作逻辑**：新增 `rangeDistributionStatisticsType()`（按 option/flinkConfig/default 解析字符串后 `StatisticsType.valueOf`）与 `rangeDistributionSortKeyBaseWeight()`（用新增的 `doubleConf()` 解析）。

### `flink/v1.18/.../sink/FlinkSink.java` 与 `flink/v1.20/.../sink/FlinkSink.java`

**修改目的**：实现 range 分布模式并解除禁令。

**工作逻辑**：
- `distributionMode(mode)`：移除 `RANGE` 抛异常的校验。
- 新增 `rangeDistributionStatisticsType(StatisticsType)` 与 `rangeDistributionSortKeyBaseWeight(double)` 两个 Builder 方法（写入 writeOptions），Javadoc 详述 Auto 模式的 Map→Sketch 自动切换（基数超 10K 阈值）与 base weight 的长尾小文件抑制语义。
- `appendAll()`：提前计算 `writerParallelism`，传入 `distributeDataStream` 与 `appendWriter`。
- `distributeDataStream()`：签名改为接收 `writerParallelism` 而非 schema/spec；`RANGE` 分支重写：若有 equality fields 则告警并 keyBy 回退；否则要求 `sortOrder.isSorted() || partitionSpec.isPartitioned()`，无 sort order 时用 `Partitioning.sortOrderFor(partitionSpec)` 构造；插入 `DataStatisticsOperatorFactory` 算子（并行度同输入以利于 chaining），再 `partitionCustom(new RangePartitioner(...), r -> r)`，`filter(StatisticsOrRecord::hasRecord)`，`map(StatisticsOrRecord::record)` 还原 RowData 流。
- `appendWriter()`：接收 `writerParallelism` 参数，移除内部并行度计算。
- 顺手整理 `toFlinkRowType` 的注释格式。

### `flink/v1.18/.../sink/shuffle/DataStatisticsOperatorFactory.java` 与 `flink/v1.20/.../sink/shuffle/DataStatisticsOperatorFactory.java`（新增）

**修改目的**：统计采集算子工厂，产出 `StatisticsOrRecord`。

**工作逻辑**：`@Internal`，继承 `AbstractStreamOperatorFactory<StatisticsOrRecord>` 并实现 `CoordinatedOperatorFactory`（带 OperatorCoordinator，用于汇总各子任务统计并下发全局统计）与 `OneInputStreamOperatorFactory<RowData, StatisticsOrRecord>`。持有 schema、sortOrder、downstreamParallelism、statisticsType、sortKeyBaseWeight。`createStreamOperator`/`getCoordinatorProvider` 创建对应算子与协调器（具体算子与协调器实现在同包其它类中，本提交回迁时引入工厂类）。

### `flink/v1.18/.../sink/TestFlinkIcebergSinkDistributionMode.java` 与 `flink/v1.20/.../sink/TestFlinkIcebergSinkDistributionMode.java`（新增）

**修改目的**：覆盖三种分布模式（none/hash/range）的端到端测试，314 行。

**工作逻辑**：验证 range 模式下数据按 sort order 聚集、writer 负载均衡、统计类型切换、base weight 抑制小文件等行为。

### `flink/v1.18/.../sink/TestFlinkIcebergSinkV2.java` 与 `flink/v1.20/.../sink/TestFlinkIcebergSinkV2.java`

**修改目的**：适配 range 模式启用后的行为。

**工作逻辑**：约 21 行调整，适配新的分布逻辑与并行度计算。

### `flink/v1.20/.../TestFlinkTableSinkExtended.java`

**修改目的**：v1.20 额外补充 range 分布相关测试。

**工作逻辑**：新增约 92 行，覆盖 table sink 场景下的 range 分布。

## 小结

- **成效**：为 Flink v1.18 与 v1.20 sink 实现 `range` 写分布模式，解除此前的禁令；通过在线统计采集（Map/Sketch/Auto）+ 范围分区，使写入数据按 sort order 聚集并均衡 writer 负载，提升查询 data skipping 效果；新增统计类型与 sort key 基础权重两个可调参数。
- **影响范围**：`flink/v1.18` 与 `flink/v1.20` 两个模块，主代码 5 个文件（含 1 个新增算子工厂）+ 测试 3 个文件，约 1318 行新增、74 行删除。属于新功能，引入 `sink.shuffle` 包下的统计/分区相关类。
- **回迁到 1.4.x 的注意事项**：不建议回迁。这是 main 分支上的新功能（range 分布模式），依赖 `sink.shuffle` 包下的 `DataStatisticsOperator`/`OperatorCoordinator`/`RangePartitioner`/`SketchUtil` 等一整套新类（本提交只回迁了工厂类，实际算子实现来自更早的 main 提交）。1.4.x 不具备这套基础设施，强行回迁工作量极大且与维护分支定位不符。1.4.x 用户若需要 range 分布，应升级到支持该功能的版本。
