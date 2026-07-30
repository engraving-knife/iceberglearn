# 提交 0965：Flink: handle rescale properly and refactor statistics (#10457)

## 提交信息

- **序号**：0965 / 4088
- **哈希**：604b2bb85ccd33a132e0f588fad1611ab655e2c5
- **短哈希**：604b2bb85
- **日期**：2024-07-22 13:59:18 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：Flink: handle rescale properly and refactor statistics (#10457)
- **PR/Issue**：#10457

## 总体目的

Iceberg 的 Flink sink shuffle 模块通过 `DataStatisticsCoordinator`（运行在 JobManager 上的 `OperatorCoordinator`）收集所有 `DataStatisticsOperator` 子任务上报的本地统计，聚合后下发一份"全局统计"给所有下游子任务，下游 `RangePartitioner` 据此把数据按 sort key 路由到合适 subtask，实现按 key 聚簇写入。

旧实现存在两个核心问题：

1. **rescale 处理不正确**：coordinator 在 checkpoint 时把"已计算好的全局统计"直接序列化保存，重启后原样恢复并下发给子任务。如果作业发生了 rescale（下游 writer 并行度变化），原统计对应的分区数/分配方案与新并行度不匹配，但旧代码没有重新计算。operator 子任务侧也只在状态恢复时拿一次统计，scale-up 时新启动的 subtask 可能完全拿不到统计。
2. **统计模型混淆**：`AggregatedStatistics` 既承担"所有 subtask 上报的原始统计聚合结果"的职责，又承担"下发给 subtask 的路由依据"（Map 模式下包含 key 频率，Sketch 模式下直接携带算好的 rangeBounds）。这意味着 Sketch 模式下要把采样数组整体广播给所有 subtask，传输量较大；同时也把"聚合"和"派生路由信息"两个关注点耦合在一个类里。

本提交的目标是：(a) 正确处理 rescale——coordinator 在 checkpoint 中只保存原始完成的统计（`CompletedStatistics`），重启时根据当前 `downstreamParallelism` 重新计算路由信息（`GlobalStatistics`）；operator 启动时主动向 coordinator 请求最新统计，并通过 hashCode 让 coordinator 判断是否需要重发。(b) 拆分统计模型，把"已完成原始统计"和"全局路由统计"分为两个类，并把 Map 模式下原本嵌在 partitioner 内部的赋值算法抽到独立类，使 partitioner 只负责"按已计算好的分配做路由"。

由于 Iceberg 同时维护 Flink 1.17/1.18/1.19 三套目录，本提交在三套目录中同步落地，逻辑一致。

## 如何达成设计目的

整体设计思路：

1. **拆分两类统计对象**：
   - `CompletedStatistics`：tracker 收齐所有 subtask 上报后产生的"原始完成统计"，Map 模式下保存 `Map<SortKey, Long> keyFrequency`，Sketch 模式下保存 `SortKey[] keySamples`（采样数组）。它是 checkpoint 中持久化的内容，体积较大但可重算。
   - `GlobalStatistics`：coordinator 基于 `CompletedStatistics` 派生的"路由依据"，Map 模式下携带 `MapAssignment`，Sketch 模式下携带体积小得多的 `SortKey[] rangeBounds`。它只用于下发，不进 checkpoint。
2. **rescale 重新计算**：`DataStatisticsCoordinator.resetToCheckpoint()` 反序列化出 `CompletedStatistics` 后立即调用 `globalStatistics(...)` 用当前 `downstreamParallelism` 重新计算 `GlobalStatistics`，保证 scale-up/scale-down 后下发的是与新并行度匹配的路由信息。
3. **operator 主动请求统计**：`DataStatisticsOperator.initializeState()` 中无论是否从 state 恢复都向 coordinator 发送 `RequestGlobalStatisticsEvent`，事件可选携带当前本地 `globalStatistics.hashCode()` 作为 signature；coordinator 收到后若 signature 匹配则跳过下发（节省开销），不匹配则用 `applyImmediately=true` 单播给该 subtask。
4. **partitioner 适配 rescale**：新增顶层 `RangePartitioner` 包装类，在没有统计时走 round-robin，拿到统计后委托给 `MapRangePartitioner` 或 `SketchRangePartitioner`。两者调用 `RangePartitioner.adjustPartitionWithRescale(partition, numPartitionsStatsCalculation, numPartitions)` 处理新统计到来前的临时不一致：scale-up 时新 subtask 暂时不分配 key（短暂次优），scale-down 时对超范围 partition 取模避免越界。
5. **applyImmediately 标志**：`StatisticsEvent` 新增布尔字段。常规广播下用 `false`，要求 operator 在 checkpoint 边界才切换统计（避免数据乱序）；operator 主动请求的补发下用 `true`，让 operator 立即应用（已是恢复场景，没有正在处理的数据流）。
6. **MapAssignment/KeyAssignment 抽取**：把原本嵌在 `MapRangePartitioner` 内部的赋值算法（含 close file cost 加权、跨 subtask 切分）抽到独立的 `MapAssignment`（负责构造）和 `KeyAssignment`（负责按权重随机选择 subtask），让 partitioner 只剩"按 sort key 查 assignment 并 select"的薄逻辑。

## 修改详情

以下描述以 `flink/v1.19` 目录为基准，1.17/1.18 目录改动一致。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/CompletedStatistics.java`（新增）

**修改目的**：定义"已完成原始统计"对象，作为 tracker 的产出和 checkpoint 持久化内容。

**工作逻辑**：携带 `checkpointId`、`StatisticsType type`、`Map<SortKey, Long> keyFrequency`（Map 模式）、`SortKey[] keySamples`（Sketch 模式）。提供工厂方法 `fromKeyFrequency` 与 `fromKeySamples`。实现 `equals/hashCode/toString` 以便后续比较与签名计算。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/CompletedStatisticsSerializer.java`（由 `AggregatedStatisticsSerializer.java` 重命名）

**修改目的**：负责 `CompletedStatistics` 的 Flink 序列化，用于 checkpoint 持久化。

**工作逻辑**：基于 `sortKeySerializer` 构造 `MapSerializer<SortKey, Long>` 与 `ListSerializer<SortKey>`。序列化时先写 `checkpointId`、`type` 枚举，再按 type 写 `keyFrequency` 或 `keySamples`。包含 `CompletedStatisticsSerializerSnapshot` 内部类用于版本化的序列化器快照（`CURRENT_VERSION = 1`）。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/GlobalStatistics.java`（新增）

**修改目的**：定义"全局路由统计"对象，作为 coordinator 下发给 subtask 的内容。

**工作逻辑**：携带 `checkpointId`、`StatisticsType`、`MapAssignment` 或 `SortKey[] rangeBounds`（二者互斥，构造时校验）。提供 `fromMapAssignment` 与 `fromRangeBounds` 两个工厂方法。`hashCode` 缓存（`transient Integer hashCode`），因为 coordinator 在响应 subtask 请求时可能多次调用。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/GlobalStatisticsSerializer.java`（新增）

**修改目的**：序列化 `GlobalStatistics` 用于 coordinator→operator 的事件传输。

**工作逻辑**：序列化时写 `checkpointId`、`type`；Map 模式下写 `numPartitions`、`keyAssignments.size()`，再逐条写 `SortKey` + `assignedSubtasks` + `subtaskWeightsWithCloseFileCost` + `closeFileCostWeight`；Sketch 模式下写 `rangeBounds` 列表。包含 `GlobalStatisticsSerializerSnapshot` 快照类。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/MapAssignment.java`（新增）

**修改目的**：把原 `MapRangePartitioner` 内部的赋值算法抽出来，可在 partitioner 之外（coordinator）预先构造。

**工作逻辑**：核心方法 `fromKeyFrequency(numPartitions, mapStatistics, closeFileCostWeightPercentage, comparator)`：先按 `comparator` 把 key 排进 `NavigableMap`，估算每个 key 的 split 数加上 close file cost 加权，再用 `buildAssignment` 贪心填充：依次给每个 subtask 填到目标权重，跨 subtask 的 key 会被切分到多个 `KeyAssignment` 上，剩余权重小于 close file cost 的尾部直接丢弃。`assignmentInfo()` 给出每个 subtask 的总权重与 key 数量（用于测试/调试）。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/KeyAssignment.java`（新增，原为 `MapRangePartitioner` 内部静态类）

**修改目的**：单个 sort key 到 subtask 的分配信息。

**工作逻辑**：保存 `assignedSubtasks` 列表、`subtaskWeightsWithCloseFileCost` 列表、`closeFileCostWeight`，并预计算 `subtaskWeightsExcludingCloseCost`、`keyWeight`、`cumulativeWeights`。`select()` 在多个 subtask 时按累计权重做二分查找选取一个 subtask（保证按权重比例随机分配），单 subtask 时直接返回。所有权重都校验大于 `closeFileCostWeight`。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/RangePartitioner.java`（新增）

**修改目的**：作为对外公开的 partitioner 包装类，处理"无统计时 round-robin + 有统计时委托"。

**工作逻辑**：实现 `Partitioner<StatisticsOrRecord>`。`partition()` 时若 wrapper 携带统计（即 checkpoint 边界的切换信号），构建对应的 `MapRangePartitioner` 或 `SketchRangePartitioner` 作为 `delegatePartitioner` 并 round-robin 当前记录；否则委托给 delegate，没有 delegate 时 round-robin 兜底。`roundRobinCounter` 用 `Random` 随机化起点避免 subtask 间同步。静态方法 `adjustPartitionWithRescale` 处理 rescale 临时情况：scale-up 时直接返回原 partition（新 subtask 暂不分配），scale-down 时对 numPartitions 取模。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SketchRangePartitioner.java`（新增）

**修改目的**：基于 rangeBounds 的 partitioner，从 `MapRangePartitioner` 中分离出来。

**工作逻辑**：用 `Arrays.binarySearch(rangeBounds, sortKey, comparator)` 找到插入点，转换成 partition 索引，再调用 `RangePartitioner.adjustPartitionWithRescale` 处理 rescale。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/MapRangePartitioner.java`

**修改目的**：瘦身为只负责"按 sort key 查 `MapAssignment` 并 select"，移除赋值算法与 `KeyAssignment` 内部类。

**工作逻辑**：构造函数接收 `MapAssignment`（不再接收原始 `mapStatistics` 和百分比）。`partition()` 中 wrap sortKey 后从 `mapAssignment.keyAssignments()` 查找，找不到时走 round-robin 兜底（用 `newSortKeyCounter` 计数并按 numPartitions 取模）。原 `assignment(...)`、`buildAssignment(...)`、`assignmentInfo()`、`mapStatistics()`、内部 `KeyAssignment` 全部删除，约 270 行被移除。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsCoordinator.java`

**修改目的**：拆分 `completedStatistics` 与 `globalStatistics`，正确处理 rescale，处理 subtask 的统计请求。

**工作逻辑**：
- 新增字段 `comparator`（`SortOrderComparators.forSchema`）、`closeFileCostWeightPercentage`、`completedStatisticsSerializer`、`globalStatisticsSerializer`、`globalStatistics`。
- 构造函数新增 `closeFileCostWeightPercentage` 参数。
- `handleDataStatisticRequest`：tracker 返回 `CompletedStatistics` 后，存到 `completedStatistics`，并调用 `globalStatistics(...)` 派生出 `GlobalStatistics`，调用 `sendGlobalStatisticsToSubtasks`。
- 静态方法 `globalStatistics(CompletedStatistics, downstreamParallelism, comparator, closeFileCostWeightPercentage)`：Sketch 模式调用 `SketchUtil.rangeBounds` 生成 rangeBounds，Map 模式调用 `MapAssignment.fromKeyFrequency` 生成赋值。
- `sendGlobalStatisticsToSubtasks`：广播 `StatisticsEvent.createGlobalStatisticsEvent(statistics, serializer, false)`（applyImmediately=false，checkpoint 边界切换）。
- `handleRequestGlobalStatisticsEvent`：根据 `RequestGlobalStatisticsEvent.signature` 与 `globalStatistics.hashCode()` 比较，相同则跳过，不同则单播 `applyImmediately=true` 的事件给请求 subtask。
- `handleEventFromOperator` 增加对 `RequestGlobalStatisticsEvent` 类型的分支与非法类型的校验。
- `checkpointCoordinator` 用 `serializeCompletedStatistics` 序列化 `completedStatistics`（注意是原始统计而非派生）。
- `resetToCheckpoint` 反序列化 `completedStatistics` 后立即 `globalStatistics(...)` 重新计算，注释说明这是为了应对并行度变化。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsCoordinatorProvider.java`

**修改目的**：透传新增的 `closeFileCostWeightPercentage` 参数。

**工作逻辑**：构造函数新增 `double closeFileCostWeightPercentage` 字段并保存，在 `getCoordinator` 时传给 `DataStatisticsCoordinator`。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsOperator.java`

**修改目的**：在 operator 启动时主动请求统计，并按 `applyImmediately` 决定何时切换。

**工作逻辑**：
- 字段 `aggregatedStatisticsSerializer` 改为 `globalStatisticsSerializer`，`globalStatistics` 类型由 `AggregatedStatistics` 改为 `GlobalStatistics`，state descriptor 同步更新。
- `initializeState`：恢复时通过 union list state 取 `GlobalStatistics`（注释说明 union state 让 scale-up 时新 subtask 也能拿到旧统计）；无论是否恢复都通过 `operatorEventGateway.sendEventToCoordinator` 发送 `RequestGlobalStatisticsEvent`，事件中带上当前 `globalStatistics.hashCode()` 作为 signature。
- `handleOperatorEvent`：反序列化得到 `GlobalStatistics`，若 `statisticsEvent.applyImmediately()` 为 true 立即 `output.collect(...)` 切换，否则等到下次 checkpoint（由既有 checkpoint 流程触发）。
- 测试方法 `globalStatistics()` 返回类型同步更新。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/AggregatedStatisticsTracker.java`

**修改目的**：tracker 只返回原始完成统计，不再做 rangeBounds 计算。

**工作逻辑**：
- 移除 `comparator` 字段及 `SortOrderComparators`/`StructLike` 导入。
- 字段 `completedStatistics` 类型由 `AggregatedStatistics` 改为 `CompletedStatistics`。
- `updateAndCheckCompletion` 返回类型改为 `CompletedStatistics`。
- 内部 `Aggregation.completedStatistics(checkpointId)`：Map 模式返回 `CompletedStatistics.fromKeyFrequency`，Sketch 模式返回 `CompletedStatistics.fromKeySamples(sketch.getSamples())`（不再调用 `SketchUtil.rangeBounds`）。
- 构造 `Aggregation` 时不再传 `comparator` 参数。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/StatisticsEvent.java`

**修改目的**：支持 `applyImmediately` 标志与 `GlobalStatistics`。

**工作逻辑**：新增 `boolean applyImmediately` 字段。`createTaskStatisticsEvent` 始终设 `true`（任务侧上报立即合并）。`createAggregatedStatisticsEvent` 改名为 `createGlobalStatisticsEvent(statistics, serializer, applyImmediately)`，从 `statistics.checkpointId()` 取 id。新增 `applyImmediately()` 访问器。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/StatisticsOrRecord.java` 与 `StatisticsOrRecordSerializer.java`

**修改目的**：把内部 `statistics` 字段类型从 `AggregatedStatistics` 改为 `GlobalStatistics`。

**工作逻辑**：相应构造函数、工厂方法、访问器签名同步替换；`StatisticsOrRecordSerializer` 的嵌套序列化器类型与 `createOuterSerializerWithNestedSerializers` 强转同步更新。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/StatisticsUtil.java`

**修改目的**：替换序列化方法与 `collectType` 重载。

**工作逻辑**：
- `serializeAggregatedStatistics` → `serializeCompletedStatistics`，参数与返回类型改为 `CompletedStatistics`。
- `deserializeAggregatedStatistics` → `deserializeCompletedStatistics`。
- 新增 `serializeGlobalStatistics` / `deserializeGlobalStatistics` 用于 `GlobalStatistics`。
- 新增 `collectType(StatisticsType config, @Nullable GlobalStatistics)` 重载，优先用统计自身的 type；原 `collectType(config, AggregatedStatistics)` 改为接收 `CompletedStatistics`。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SketchUtil.java`

**修改目的**：让 `rangeBounds` 直接接收 samples 而非 sketch 对象，使 coordinator 可在 partitioner 之外调用。

**工作逻辑**：`rangeBounds` 签名由 `(int, Comparator, ReservoirItemsSketch<SortKey>)` 改为 `(int, Comparator, SortKey[] samples)`。删除 `determineBounds`（其逻辑合并进 `rangeBounds`）。Javadoc 改用 `<ul>/<li>` 格式，并补充"假设单个 key 不会跨越多个 subtask"的说明。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/Fixtures.java`

**修改目的**：测试夹具替换为新的 serializer。

**工作逻辑**：删除 `AGGREGATED_STATISTICS_SERIALIZER`，新增 `GLOBAL_STATISTICS_SERIALIZER` 与 `COMPLETED_STATISTICS_SERIALIZER`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestAggregatedStatisticsTracker.java`

**修改目的**：适配 tracker 返回 `CompletedStatistics` 且 Sketch 模式返回 samples 而非 rangeBounds。

**工作逻辑**：把多处 `AggregatedStatistics` 替换为 `CompletedStatistics`；Sketch 模式断言由 `rangeBounds()` 改为 `keySamples()`，验证完整采样数组（含重复元素顺序）。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestCompletedStatisticsSerializer.java`（由 `TestAggregatedStatisticsSerializer.java` 重命名）

**修改目的**：测试新 serializer。

**工作逻辑**：类名与 serializer 类型同步替换，测试用例逻辑基本一致。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestDataStatisticsCoordinator.java`

**修改目的**：覆盖 rescale 重算与 subtask 请求事件。

**工作逻辑**：新增测试验证 `resetToCheckpoint` 后 `globalStatistics` 按新并行度重算；新增对 `RequestGlobalStatisticsEvent` 的处理测试，包括 signature 匹配跳过与不匹配单播的场景。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestDataStatisticsCoordinatorProvider.java`

**修改目的**：透传新参数。

**工作逻辑**：构造 provider 时传入 `closeFileCostWeightPercentage`，断言其透传到 coordinator。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestDataStatisticsOperator.java`

**修改目的**：覆盖 operator 启动时发请求与 `applyImmediately` 行为。

**工作逻辑**：新增测试验证 initialize 时会发 `RequestGlobalStatisticsEvent`，以及 `applyImmediately=true` 时立即下发统计、`false` 时不立即下发。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestGlobalStatisticsSerializer.java`（新增）

**修改目的**：覆盖 `GlobalStatisticsSerializer` 的序列化往返。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestMapRangePartitioner.java`

**修改目的**：适配 `MapRangePartitioner` 新构造签名（直接接收 `MapAssignment`）。

**工作逻辑**：测试用例改为先构造 `MapAssignment` 再传入 partitioner；移除对已删除的 `assignment()`、`assignmentInfo()` 方法的依赖。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestSketchUtil.java`

**修改目的**：适配 `rangeBounds` 新签名（接收 samples 数组）。

## 小结

- **成效**：实现了 Flink sink shuffle 模块对 rescale 的正确处理——coordinator 在恢复时按当前并行度重算 `GlobalStatistics`，operator 启动时主动请求统计并通过 hashCode 让 coordinator 高效判定是否需要重发；同时把统计模型拆分为 `CompletedStatistics`（原始、可重算、入 checkpoint）与 `GlobalStatistics`（派生、轻量、只下发），让 Sketch 模式下广播的数据从完整 samples 缩减为 range bounds，大幅减少传输量；并把 Map 赋值算法抽到独立类，partitioner 退化为薄路由层。
- **影响范围**：Flink 1.17/1.18/1.19 三套目录共 28 个文件，主代码与测试同步更新；新增 `CompletedStatistics`、`CompletedStatisticsSerializer`、`GlobalStatistics`、`GlobalStatisticsSerializer`、`MapAssignment`、`KeyAssignment`、`RangePartitioner`、`SketchRangePartitioner`、`RequestGlobalStatisticsEvent` 共 9 个主类；重命名 `AggregatedStatistics` → `CompletedStatistics`、`AggregatedStatisticsSerializer` → `CompletedStatisticsSerializer`、`TestAggregatedStatisticsSerializer` → `TestCompletedStatisticsSerializer`；改写 `DataStatisticsCoordinator`、`DataStatisticsOperator`、`AggregatedStatisticsTracker`、`MapRangePartitioner`、`StatisticsEvent`、`StatisticsOrRecord`、`StatisticsOrRecordSerializer`、`StatisticsUtil`、`SketchUtil`、`DataStatisticsCoordinatorProvider`；测试同步更新 6 个文件。
- **回迁到 1.4.x 的注意事项**：本提交是 sink shuffle 模块的重要修复与重构，**回迁价值高**，特别是对在生产中遇到 rescale 后 partitioner 行为异常的用户。但需注意：(1) 本提交打破了 `AggregatedStatistics`/`AggregatedStatisticsSerializer` 的二进制兼容性，1.4.x 上若有下游/外部代码引用这些类型会受影响；Flink 自身对 state 序列化器有快照机制（`CURRENT_VERSION = 1`），但旧 state 中的 `AggregatedStatistics` 数据无法直接被新 `CompletedStatisticsSerializer` 读取，**回迁后从旧 checkpoint 恢复会失败**，建议作为不兼容变更处理。(2) `closeFileCostWeightPercentage` 配置项的默认值需在 1.4.x 上确认一致。(3) 改动量很大，cherry-pick 时三套 Flink 目录需同步处理且大概率有上下文冲突，建议先在 1.4.x 上单独评估是否已有相同模块（早期 1.4.x 可能根本没有 shuffle 模块）；若 1.4.x 上尚未引入该模块，则不需要回迁，等下一个 minor 版本统一带入即可。
