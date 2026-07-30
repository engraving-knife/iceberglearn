# 提交 1070：Flink: put everything together for range distribution in Flink sink (#10859)

## 提交信息

- **序号**：1070 / 4088
- **哈希**：ed07fd1cd707a28a4201ea01e330fe2efdc5b494
- **短哈希**：ed07fd1cd
- **日期**：2024-08-19 14:49:06 -0700
- **作者**：Steven Zhen Wu <stevenz3wu@gmail.com>
- **提交说明**：Flink: put everything together for range distribution in Flink sink (#10859)
- **PR/Issue**：#10859

## 总体目的

Iceberg 表属性 `write.distribution-mode` 支持三种取值：`NONE`、`HASH`、`RANGE`。Spark 引擎早已支持 `RANGE` 模式，但 Flink 流式写入器（`FlinkSink`）此前对 `RANGE` 一直是"显式拒绝"——`Builder.distributionMode(...)` 中有一行 `Preconditions.checkArgument(!DistributionMode.RANGE.equals(mode), "Flink does not support 'range' write distribution mode now.")`，运行时遇到 RANGE 模式还会 fallback 到 NONE 或 keyBy，无法真正做范围分布。

社区在之前的一系列 PR 中已经为 Flink 范围分布铺好了底层基础设施：在 `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/` 包下陆续加入了 `DataStatisticsOperator`（每个 subtask 采集本地数据分布统计）、`DataStatisticsCoordinator`（在 checkpoint 周期内聚合全局统计并广播）、`RangePartitioner`（按统计信息做范围分桶）、`StatisticsOrRecord`（统计与记录的联合类型，便于在算子间传递）、`StatisticsType`（Map / Sketch / Auto 三种统计策略）、`SketchUtil`（高基数场景下的蓄水池采样）等。这些组件此前并未被 `FlinkSink` 实际接入。

本提交的目的是"把所有东西组装起来"——打通 `FlinkSink` 与 shuffle 子系统之间的最后一公里：

1. 移除 `Builder.distributionMode` 中对 `RANGE` 的拒绝，让用户可以在 Flink 流式写入里启用 `RANGE` 分布模式；
2. 在 `distributeDataStream` 的 `RANGE` 分支里，按表的 `SortOrder`（或从 `PartitionSpec` 推导出的 sort order）挂载 `DataStatisticsOperatorFactory`，再接 `RangePartitioner`，把数据按范围分布到 writer；
3. 暴露两个新的写入配置：`range-distribution-statistics-type`（统计采集策略）和 `range-distribution-sort-key-base-weight`（防止长尾小文件过多的基线权重）；
4. 配套补全 `FlinkConfParser` 对 `double` 类型的解析能力，更新用户文档，新增端到端测试覆盖范围分布的多种场景（分区列、SortOrder、Sketch 高基数、Map→Sketch 自动切换等）。

这标志着 Flink 流式写入正式支持 `RANGE` 分布模式（实验性），可用于解决 `HASH` 模式下数据倾斜、writer 并行度受限于 hash key 基数等问题。

## 如何达成设计目的

整体设计思路是：在 `FlinkSink.Builder.append()` 调用链中，把 `distributeDataStream(...)` 的 `RANGE` 分支从"fallback / keyBy"改为"挂载统计算子 + 范围分区器 + 过滤统计包"的完整流水线。具体步骤：

1. **配置层**：在 `FlinkWriteOptions` 中新增两个 `ConfigOption`：`RANGE_DISTRIBUTION_STATISTICS_TYPE`（字符串，默认 `Auto`）和 `RANGE_DISTRIBUTION_SORT_KEY_BASE_WEIGHT`（double，默认 `0.0d`）。为了让 `FlinkWriteConf` 能解析 double 配置，在 `FlinkConfParser` 中新增 `DoubleConfParser` 内部类，提供 `defaultValue(double)` / `parse()` / `parseOptional()` 方法。`FlinkWriteConf` 暴露 `rangeDistributionStatisticsType()` 与 `rangeDistributionSortKeyBaseWeight()` 两个读取方法，遵循"option → flinkConfig → defaultValue"的优先级。
2. **Builder API**：移除 `distributionMode` 对 `RANGE` 的拒绝断言；新增 `rangeDistributionStatisticsType(StatisticsType)` 与 `rangeDistributionSortKeyBaseWeight(double)` 两个 builder 方法，便于 Java API 用户直接设置。
3. **writer 并行度计算前移**：原本在 `appendWriter` 内部才计算 `parallelism`，现在改为在 `append()` 主流程里先算好 `writerParallelism`，再传给 `distributeDataStream` 和 `appendWriter`，因为范围分布算子需要知道下游 writer 并行度才能正确切分范围。
4. **distributeDataStream 改造**：`RANGE` 分支里
   - 若有 equality fields（主键场景），打 warn 后 fallback 到 keyBy，保持向后兼容（range 分布对主键不总是安全）；
   - 否则校验表必须有 SortOrder 或 PartitionSpec，否则抛 `IllegalStateException`；
   - 若 `sortOrder.isUnsorted()`，用 `Partitioning.sortOrderFor(partitionSpec)` 从分区 spec 构造 sort order；
   - 通过 `input.transform("range-shuffle", ..., new DataStatisticsOperatorFactory(...))` 挂载统计算子，并行度与 input 相同以鼓励算子链合并；
   - 接 `partitionCustom(new RangePartitioner(iSchema, sortOrder), r -> r)` 做范围分区；
   - 再 `filter(StatisticsOrRecord::hasRecord).map(StatisticsOrRecord::record)` 过滤掉统计包、还原为 RowData 流，交给下游 writer。
5. **DataStatisticsOperatorFactory**：新建工厂类，实现 `CoordinatedOperatorFactory`，在 `getCoordinatorProvider` 中返回 `DataStatisticsCoordinatorProvider`（负责 checkpoint 周期内的全局统计聚合），在 `createStreamOperator` 中创建 `DataStatisticsOperator` 并注册事件处理器、设置 `OperatorEventGateway`。
6. **测试**：在 `TestFlinkIcebergSinkDistributionMode` 中新增 5 个测试：无 sort order 的非分区表（应抛异常）、无 sort order 的分区表（用分区列做 sort）、显式 SortOrder + Map 统计、Sketch 高基数统计、Auto 模式下的 Map→Sketch 自动迁移；在 `TestFlinkTableSinkExtended` 中新增 SQL 路径的端到端测试；在 `TestFlinkIcebergSinkV2` 中修正 upsert 校验断言以适配 range 分布场景下的错误消息差异。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/FlinkWriteOptions.java`

**修改目的**：新增两个用于范围分布的写入配置项。

**工作逻辑**：

- import `org.apache.iceberg.flink.sink.shuffle.StatisticsType`；
- 新增 `RANGE_DISTRIBUTION_STATISTICS_TYPE`：`ConfigOptions.key("range-distribution-statistics-type").stringType().defaultValue(StatisticsType.Auto.name())`，描述为 "Type of statistics collection: Auto, Map, Sketch"；
- 新增 `RANGE_DISTRIBUTION_SORT_KEY_BASE_WEIGHT`：`ConfigOptions.key("range-distribution-sort-key-base-weight").doubleType().defaultValue(0.0d)`，描述为 "Base weight for every sort key relative to target weight per writer task"。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/FlinkConfParser.java`

**修改目的**：新增 `DoubleConfParser` 内部类，让 Flink 配置解析器支持 double 类型，供 `range-distribution-sort-key-base-weight` 使用。

**工作逻辑**：

- 在 `FlinkConfParser` 中新增公共方法 `doubleConf()` 返回 `new DoubleConfParser()`；
- 新增内部类 `DoubleConfParser extends ConfParser<DoubleConfParser, Double>`，包含字段 `private Double defaultValue`，重写 `self()`，提供 `defaultValue(double value)` 设默认值、`parse()` 返回 `double`（必须设默认值，否则抛 `Preconditions.checkArgument`）、`parseOptional()` 返回 `Double`（可为 null）；
- `parse` 内部委托给父类的 `parse(Double::parseDouble, defaultValue)`，复用现有的"option → flinkConfig → defaultValue"解析链。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/FlinkWriteConf.java`

**修改目的**：暴露范围分布相关的两个配置读取方法。

**工作逻辑**：

- import `StatisticsType`；
- 新增 `rangeDistributionStatisticsType()`：通过 `confParser.stringConf().option(...).flinkConfig(...).defaultValue(...).parse()` 拿到字符串后 `StatisticsType.valueOf(name)` 转枚举；
- 新增 `rangeDistributionSortKeyBaseWeight()`：通过 `confParser.doubleConf().option(...).flinkConfig(...).defaultValue(...).parse()` 直接返回 double。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkSink.java`

**修改目的**：移除对 RANGE 分布的拒绝、新增 builder API、把 RANGE 分支改造为完整的范围分布流水线、前移 writer 并行度计算。

**工作逻辑**：

- 新增多个 import：`Partitioning`、`SortOrder`、`DataStatisticsOperatorFactory`、`RangePartitioner`、`StatisticsOrRecord`、`StatisticsType`；
- `Builder.distributionMode(...)`：删除 `Preconditions.checkArgument(!DistributionMode.RANGE.equals(mode), ...)`，仅保留"非 null 时写入 writeOptions"的逻辑；
- 新增 `Builder.rangeDistributionStatisticsType(StatisticsType type)`：把 type.name() 写入 `FlinkWriteOptions.RANGE_DISTRIBUTION_STATISTICS_TYPE.key()`，附详细 Javadoc 解释 Map/Sketch/Auto 三种策略及默认 Auto 的行为；
- 新增 `Builder.rangeDistributionSortKeyBaseWeight(double weight)`：把 weight 写入 `FlinkWriteOptions.RANGE_DISTRIBUTION_SORT_KEY_BASE_WEIGHT.key()`，附超长 Javadoc 用按天分区的长尾流量例子说明 base weight 的作用；
- `append()` 主流程：提前计算 `writerParallelism = flinkWriteConf.writeParallelism() == null ? rowDataInput.getParallelism() : flinkWriteConf.writeParallelism()`，传入 `distributeDataStream` 与 `appendWriter`；
- `appendWriter(...)` 签名增加 `int writerParallelism` 参数，删除方法内部对 parallelism 的重复计算，直接用传入值 `setParallelism(writerParallelism)`；
- `distributeDataStream(...)` 签名简化为 `(input, equalityFieldIds, flinkRowType, writerParallelism)`，去掉 `partitionSpec`、`iSchema` 参数（改为方法内从 `table` 取）；获取 `iSchema = table.schema()`、`partitionSpec = table.spec()`、`sortOrder = table.sortOrder()`；`RANGE` 分支重写：
  - 若 `!equalityFieldIds.isEmpty()`：打 warn 日志（"Hash distribute rows by equality fields, even though ...=range is set. Range distribution for primary keys are not always safe"），返回 `input.keyBy(new EqualityFieldKeySelector(...))`，保持向后兼容；
  - 否则 `Preconditions.checkState(sortOrder.isSorted() || partitionSpec.isPartitioned(), "Invalid write distribution mode: range. Need to define sort order or partition spec.")`；
  - 若 `sortOrder.isUnsorted()`，用 `Partitioning.sortOrderFor(partitionSpec)` 从分区 spec 构造 sort order；
  - `input.transform("range-shuffle", TypeInformation.of(StatisticsOrRecord.class), new DataStatisticsOperatorFactory(iSchema, sortOrder, writerParallelism, statisticsType, flinkWriteConf.rangeDistributionSortKeyBaseWeight())).setParallelism(input.getParallelism())`，必要时设 uid；
  - `.partitionCustom(new RangePartitioner(iSchema, sortOrder), r -> r).filter(StatisticsOrRecord::hasRecord).map(StatisticsOrRecord::record)` 还原为 RowData 流；
- 顺手清理 `toFlinkRowType` 中的一段多行注释格式。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsOperatorFactory.java`（新建）

**修改目的**：为范围分布算子提供 `CoordinatedOperatorFactory` 实现，同时创建算子实例与对应的 `OperatorCoordinator`。

**工作逻辑**：

- 标注 `@Internal`，继承 `AbstractStreamOperatorFactory<StatisticsOrRecord>` 并实现 `CoordinatedOperatorFactory<StatisticsOrRecord>`、`OneInputStreamOperatorFactory<RowData, StatisticsOrRecord>`；
- 构造参数：`Schema schema`、`SortOrder sortOrder`、`int downstreamParallelism`、`StatisticsType type`、`double closeFileCostWeightPercentage`（即 sort key base weight）；
- `getCoordinatorProvider(...)` 返回 `new DataStatisticsCoordinatorProvider(operatorName, operatorID, schema, sortOrder, downstreamParallelism, type, closeFileCostWeightPercentage)`，让 Flink 在 JobManager 上启动对应的 `DataStatisticsCoordinator`，负责在每个 checkpoint 周期内聚合所有 subtask 上报的本地统计并广播全局统计；
- `createStreamOperator(...)`：通过 `parameters.getOperatorEventDispatcher().getOperatorEventGateway(operatorId)` 获取事件网关，`new DataStatisticsOperator(operatorName, schema, sortOrder, gateway, downstreamParallelism, type)`，调用 `setup(...)` 完成初始化，再 `registerEventHandler(operatorId, rangeStatisticsOperator)` 让算子能接收 coordinator 下发的聚合统计事件；
- `getStreamOperatorClass(...)` 返回 `DataStatisticsOperator.class`。

### `docs/docs/flink-configuration.md`

**修改目的**：在 write-options 表格中补充两个新选项，并新增两个小节详细解释 `range-distribution-statistics-type` 与 `range-distribution-sort-key-base-weight`，同时在 `distribution-mode` 一栏标注 RANGE 为 experimental。

**工作逻辑**：把原表格扩展为 13 行（新增 `range-distribution-statistics-type` 与 `range-distribution-sort-key-base-weight` 两行，并在 `distribution-mode` 描述末尾加上 "RANGE distribution is in experimental status."）；表格后新增 `#### Range distribution statistics type`（解释 Map / Sketch / Auto 三种策略）与 `#### Range distribution sort key base weight`（用按天分区长尾流量例子解释 base weight 的语义与默认 0.0 的含义）两个小节，内容与 FlinkSink Javadoc 一致。

### `docs/docs/flink-writes.md`

**修改目的**：新增 `## Distribution mode` 一节，向用户系统介绍 Flink 流式写入的 HASH 与 RANGE 分布模式、使用场景、流量统计机制、Java API 用法及开销。

**工作逻辑**：在 "write-options" 链接之后插入约 100 行新内容，包含：

- `### Hash distribution`：说明 HASH 通过 `DataStream#keyBy` 实现，并列出三个局限（数据倾斜、低基数导致流量不均、writer 并行度受限于 hash key 基数，引用 PR 4228）；
- `### Range distribution (experimental)`：说明 RANGE 通过自定义 range partitioner 按 sort order 分布，并强调"仅做 shuffle，行在文件内并不排序"；
- `#### Use cases`：列举 RANGE 适用于按 event time / country code / event type 等倾斜分区，以及通过 SortOrder 在非分区列上聚类以提升查询性能；
- `#### Traffic statistics`：解释每个 subtask 采集、coordinator 在 checkpoint 周期聚合、广播到所有 subtask、下一个 checkpoint 生效的机制（最多两个 checkpoint 周期延迟），并区分低基数（Map）与高基数（Sketch 蓄水池采样）两种策略；
- `#### Usage`：给出 Java API 示例（`.distributionMode(DistributionMode.RANGE).rangeDistributionStatisticsType(StatisticsType.Auto).rangeDistributionSortKeyBaseWeight(0.0d)`）；
- `### Overhead`：提示 shuffle 与统计采集会带来 CPU/内存开销，警告高基数场景不要用 Map 统计类型。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/TestFlinkTableSinkExtended.java`

**修改目的**：新增 SQL 路径的端到端测试 `testRangeDistributionPartitionColumn`，验证通过 SQL `INSERT` 启用 RANGE 分布后，按分区列做范围分区的写入行为。

**工作逻辑**：

- 新增 import：`assumeThat`、`Snapshot`、`Lists`；
- `testRangeDistributionPartitionColumn`：先 `assumeThat(isStreamingJob).isTrue()`（范围分区器目前只对带 checkpoint 的流式写入生效）；用 `BoundedTableFactory` 注册 5 个 checkpoint 周期的数据，每周期 26×10 行（'a'-'z' × 10）；创建分区表（`PARTITIONED BY (data)`）并设 `WRITE_DISTRIBUTION_MODE=RANGE`；执行 `INSERT INTO ... SELECT * FROM ...`；断言写入行与源表一致；从 table.snapshots() 取出含新增数据文件的快照，断言数量 ≥ 5；取最后两个快照（已应用范围分区），断言每个快照的新增数据文件数 ≤ 26（因为范围分区后每个分区只分给一个 writer 任务，无 shuffle 时最多可达 26×4=104 个文件）。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkDistributionMode.java`

**修改目的**：新增 5 个测试覆盖 RANGE 分布的多种场景。

**工作逻辑**：

- 新增 import：`assumeThat`、`Collectors`、`DataFile`、`Snapshot`、`StatisticsType`、`BoundedTestSource`、`Lists`、`Conversions`、`Types`；
- `testRangeDistributionWithoutSortOrderUnpartitioned`：非分区表 + 无 SortOrder + RANGE 模式，断言 `builder.append()` 抛 `IllegalStateException` 且消息为 "Invalid write distribution mode: range. Need to define sort order or partition spec."；
- `testRangeDistributionWithoutSortOrderPartitioned`：分区表 + 无 SortOrder + RANGE，验证能正常 append 并执行，快照数 ≥ 6；
- `testRangeDistributionWithSortOrder`：显式 SortOrder（asc("data")）+ Map 统计，验证快照数 ≥ 6，最后两个快照中分区表新增文件数 ≤ 26、非分区表新增文件数 = parallelism，并在 parallelism=2 时验证两个文件的 id 列 min/max 范围无重叠（`assertIdColumnStatsNoRangeOverlap`）；
- `testRangeDistributionSketchWithSortOrder`：SortOrder（asc("id")）+ Sketch 统计 + 高基数（每 checkpoint 1000 行），验证最后两个快照新增文件数 = parallelism，parallelism=2 时 id 列 min/max 无重叠；
- `testRangeDistributionStatisticsMigration`：Auto 模式下，第 2 个 checkpoint 起 emit 11000 行（超过 `OPERATOR_SKETCH_SWITCH_THRESHOLD = 10000`），触发 Map→Sketch 自动迁移，验证最后两个快照新增文件数 = parallelism，parallelism=2 时 id 列 min/max 无重叠；
- 新增辅助方法 `createRangeDistributionBoundedSource`、`createCharRows`、`createIntRows`、`assertIdColumnStatsNoRangeOverlap`（用 `Conversions.fromByteBuffer` 解析 id 列 lower/upper bounds 并断言两个文件范围不交叠）。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2.java`

**修改目的**：修正 upsert 校验测试，使其在 RANGE 分布 + 非分区表场景下断言新的错误消息。

**工作逻辑**：原测试断言 `builder.equalityFieldColumns(ImmutableList.of()).overwrite(false).append()` 抛 `IllegalStateException` 且消息为 "Equality field columns shouldn't be empty when configuring to use UPSERT data stream."；但在 RANGE 分布 + 非分区表场景下，`append()` 会在 `distributeDataStream` 中先抛 "Invalid write distribution mode: range. Need to define sort order or partition spec."。改写为分支断言：若 `writeDistributionMode.equals(DistributionMode.RANGE.modeName()) && !partitioned`，断言抛 distributeDataStream 的错误；否则断言抛 appendWriter 的原错误。

## 小结

- **成效**：把已有的 shuffle 子系统（DataStatisticsOperator/Coordinator、RangePartitioner、StatisticsType、SketchUtil 等）正式接入 `FlinkSink`，让 Flink 流式写入器支持 `RANGE` 分布模式（实验性）；新增两个写入配置（统计类型、sort key base weight）；新增 `DoubleConfParser` 支持 double 配置；更新 `flink-configuration.md` 与 `flink-writes.md` 用户文档；新增 6 个端到端测试覆盖分区列、SortOrder、Map/Sketch/Auto 等多种场景，并验证文件级 min/max 范围无重叠。
- **影响范围**：仅修改 `flink/v1.19` 模块下的 7 个 main 文件与 3 个 test 文件以及 2 个文档文件；不动 `core`、`spark`、`aws` 等其他模块。注意本提交只覆盖 v1.19，未同步到 v1.18/v1.20（其他 Flink 版本模块的同步通常由后续提交或独立 PR 处理）。RANGE 分布被标注为 experimental，向后兼容性方面：原有 `RANGE` 模式会抛 IllegalArgumentException 拒绝，现在改为真正生效，对显式设了 `write.distribution-mode=range` 的用户是一次行为变更（但此前是被拒绝的，不存在真正的旧用户）。
- **回迁到 1.4.x 的注意事项**：这是一次较大的功能新增（实验性），回迁到 1.4.x 风险较高，**不建议直接回迁**。原因：(1) 1.4.x 分支的 Flink 模块版本可能不同（1.4.x 时期维护的是 v1.16/v1.17/v1.18 等），需要确认对应模块下是否已有 shuffle 子系统的基础类（DataStatisticsOperator、RangePartitioner、StatisticsType、SketchUtil 等），如果没有，本提交单独 cherry-pick 无法编译通过；(2) `FlinkConfParser`、`FlinkWriteConf`、`FlinkWriteOptions` 在 1.4.x 上的状态可能与 v1.19 不同，需手工对齐；(3) 该功能被明确标注为 experimental，1.4.x 作为维护分支通常不应引入新的实验性功能，应让用户在 1.5.x 及以后版本使用。如果确需在 1.4.x 上回迁，建议连同前置的 shuffle 子系统 PR 一起整体回迁，并完整跑一遍 Flink 集成测试。
