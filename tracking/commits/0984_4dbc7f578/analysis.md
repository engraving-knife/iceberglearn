# 提交 0984：Flink: backport PR #10331 and PR #10457 (#10757)

## 提交信息

- **序号**：0984 / 4088
- **哈希**：4dbc7f578eee7ceb9def35ebfa1a4cc236fb598f
- **短哈希**：4dbc7f578
- **日期**：2024-07-26 09:13:18 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：Flink: backport PR #10331 and PR #10457  (#10757)
- **PR/Issue**：#10757（同时回迁 #10331 与 #10457）

## 总体目的

Iceberg Flink sink 通过 `DataStatisticsOperator` + `DataStatisticsCoordinator` + 自定义 partitioner 实现 RANGE 分发（即按 sort key 把数据聚簇到下游 writer 子任务），以减少写出的小文件数量、提升数据布局。在 PR #10331 之前，统计信息只有一种实现：`MapDataStatistics`，以 `Map<SortKey, Long>` 的形式记录每个 sort key 的频次。这种 Map 方案对低基数列（如 country、event_type，基数在百、千量级）很合适，但对高基数列（如 device_id、user_id、uuid，基数百万到十亿）会爆炸式占用内存，导致 coordinator / operator 子任务 OOM 或性能退化。

PR #10331 引入第二种统计实现 `SketchDataStatistics`，基于 DataSketches 库的 `ReservoirItemsSketch` 进行蓄水池采样，再由 coordinator 计算出 N-1 个 range bounds 作为分区边界。Sketch 方案内存占用低，适合高基数场景。同时还引入 `StatisticsType.Auto` 模式：默认用 Map 跟踪，当 key 基数超过阈值时自动切换到 Sketch，从而对各种基数列都自适应。

PR #10457 主要做了两件事：(1) 把 `MapAssignment`、`KeyAssignment` 等计算从 operator 子任务上的 partitioner 移到 coordinator 上（让 coordinator 一次性算好分配方案，再把更小的 `GlobalStatistics` 广播给子任务）；(2) 增加 rescale（写并发变更）支持——子任务在初始化时通过新的 `RequestGlobalStatisticsEvent` 主动向 coordinator 请求最新统计；partitioner 提供 `adjustPartitionWithRescale` 在 rescale 后短暂使用旧统计做 mod 调整，等新统计到达后再切换。同时把统计切换时机从"立刻应用"改为"在 checkpoint 边界应用"（通过 `applyImmediately` 控制），保证下游所有子任务在同一个 checkpoint 上统一切换，避免分片不齐。

本提交就是把这两个 PR 的完整改动从 main 分支 backport 到 Flink v1.17 与 v1.18 两个旧版本模块，让旧版本也能用上 Sketch 统计与 rescale 能力。

## 如何达成设计目的

整体设计把统计/分区流水线重构为：

1. **Operator 子任务**：本地用 `MapDataStatistics` 或 `SketchDataStatistics` 跟踪 sort key；checkpoint 时序列化为 `StatisticsEvent`（task → coordinator）发给 coordinator。
2. **Coordinator**：用 `AggregatedStatisticsTracker` 聚合所有子任务统计，支持 Map 与 Sketch 两种合并，并在 Auto 模式下当聚合后 key 数量超过阈值时把 Map 转 Sketch；聚合完成后产出 `CompletedStatistics`（含原始 Map 或 reservoir 采样），再据此算出 `GlobalStatistics`（含 `MapAssignment` 或 `SortKey[] rangeBounds`），广播给所有子任务。
3. **Partitioner**：`RangePartitioner` 包装类根据 `GlobalStatistics.type()` 选择 `MapRangePartitioner`（基于 MapAssignment 做 key → subtask 路由）或 `SketchRangePartitioner`（基于 range bounds 做二分查找）；统计未到位时回退到 round-robin；rescale 时用 `adjustPartitionWithRescale` 做 mod 调整。
4. **统计切换时机**：coordinator 广播 `GlobalStatistics` 时设置 `applyImmediately=false`，子任务收到后先存到本地 `globalStatistics`，等下次 `snapshotState` 时再 collect 到下游 partitioner，保证所有子任务在 checkpoint 边界统一切换。

文件层面，本次提交对 Flink v1.17 和 v1.18 两个模块做了一份**完全相同**的镜像改动（每份约 45 个源文件 + 测试），因此下文"修改详情"以 v1.17 为主描述，v1.18 路径一一对应。

## 修改详情

### `flink/v1.17/build.gradle` 与 `flink/v1.18/build.gradle`

**修改目的**：引入 DataSketches 库依赖，供 `ReservoirItemsSketch` 等采样算法使用。

**工作逻辑**：在 `dependencies` 块中加入 `implementation libs.datasketches`，与 iceberg-core 等已有依赖并列。版本由根 build.gradle 的 version catalog 统一管理。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/StatisticsType.java`（新增）

**修改目的**：定义三种统计类型枚举，作为整个 sketch 体系的入口配置。

**工作逻辑**：
- `Map`：以 `Map<SortKey, Long>` 记录频次，适合低基数；
- `Sketch`：以 reservoir 采样收集样本，适合高基数；
- `Auto`：先 Map 后按阈值自动切 Sketch。
类注释说明了三种模式的优缺点。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatistics.java`（修改）

**修改目的**：把原泛型接口 `DataStatistics<D extends DataStatistics<D, S>, S>` 改为非泛型接口，并新增 `StatisticsType type()` 与 `Object result()` 方法，删除原 `merge()` 方法（合并逻辑移到 tracker 内部）。

**工作逻辑**：
- `type()` 返回该统计实现属于 Map 还是 Sketch；
- `result()` 返回底层统计对象（Map 或 `ReservoirItemsSketch`），交由调用方按类型转换。
这样接口可以同时承载两种实现，且去掉泛型简化了上下游代码。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/MapDataStatistics.java`（修改）

**修改目的**：适配新 `DataStatistics` 接口，新增 `type()` 返回 `StatisticsType.Map`，`result()` 返回内部 Map；删除原泛型 `merge` 方法。

**工作逻辑**：内部仍是 `Map<SortKey, Long>` 累加 sort key 频次。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SketchDataStatistics.java`（新增）

**修改目的**：基于 DataSketches 的 `ReservoirItemsSketch` 实现采样统计。

**工作逻辑**：
- 构造时给定 reservoirSize，调用 `ReservoirItemsSketch.newInstance(reservoirSize)`；
- `add(SortKey)` 先 `sortKey.copy()` 再 `sketch.update()`（输入对象可能被复用，必须 clone）；
- `result()` 返回 sketch 对象本身；
- `equals/hashCode` 基于 `getK() / getN() / getSamples()` 实现。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SketchUtil.java`（新增）

**修改目的**：抽取 sketch 相关工具方法，集中管理 reservoir size 计算与 range bounds 计算。

**工作逻辑**：
- 常量：`COORDINATOR_MIN_RESERVOIR_SIZE=10_000`、`COORDINATOR_MAX_RESERVOIR_SIZE=1_000_000`、`COORDINATOR_TARGET_PARTITIONS_MULTIPLIER=100`、`OPERATOR_OVER_SAMPLE_RATIO=10`、`OPERATOR_SKETCH_SWITCH_THRESHOLD=10_000`、`COORDINATOR_SKETCH_SWITCH_THRESHOLD=100_000`；
- `determineCoordinatorReservoirSize(numPartitions)`：目标 `numPartitions*100`，下限 10K、上限 1M，并保证能被 numPartitions 整除；
- `determineOperatorReservoirSize(operatorParallelism, numPartitions)`：`coordinatorReservoirSize * 10 / operatorParallelism`，让多个 operator 子任务合起来对 coordinator 蓄水池过采样 10 倍；
- `rangeBounds(numPartitions, comparator, samples)`：对样本排序后按 `step = ceil(samples.length / numPartitions)` 取 `numPartitions - 1` 个候选边界，跳过重复值；
- `convertMapToSketch(Map, consumer)`：把 Map 统计展开成多次 sketch update（O(N·count)）。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/CompletedStatistics.java`（新增）

**修改目的**：表示 coordinator 一次完整聚合的产物（原始 Map 或原始 samples），供后续计算 GlobalStatistics。

**工作逻辑**：
- 字段：checkpointId、type、keyFrequency（Map 模式）、keySamples（Sketch 模式）；
- 工厂方法 `fromKeyFrequency` / `fromKeySamples`；
- 标准的 equals/hashCode/toString，且对 keySamples 用 `Arrays.equals` 比较。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/GlobalStatistics.java`（新增）

**修改目的**：表示 coordinator 广播给子任务的"已计算好的"统计，体积远小于 CompletedStatistics（sketch 模式下只含 range bounds 而非全部样本）。

**工作逻辑**：
- 字段：checkpointId、type、mapAssignment（Map 模式）、rangeBounds（Sketch 模式）；
- 工厂方法 `fromMapAssignment` / `fromRangeBounds`；
- 缓存 `transient Integer hashCode`，因为 coordinator 在响应子任务统计请求时会多次调用 hashCode（用作 signature 比对）。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/MapAssignment.java`（新增，从原 MapRangePartitioner 抽取）

**修改目的**：把"按 Map 统计计算每个 key → 子任务分配"的逻辑从 partitioner 中抽出到独立类，使 coordinator 可以预先算好再下发，子任务 partitioner 只需查表。

**工作逻辑**：
- `numPartitions` + `Map<SortKey, KeyAssignment>`；
- 工厂方法 `fromKeyFrequency(numPartitions, mapStatistics, closeFileCostWeightPercentage, comparator)`：
  - 计算总权重与每个子任务的目标权重；
  - 计算每个 key 的预估 split 数与对应 close file cost，把它加到 key 权重里（防止小文件过多）；
  - 用 `NavigableMap<SortKey, Long>` 按 comparator 排序；
  - 调 `buildAssignment` 贪心分配——遍历排序后的 key，把 key 拆分到一个或多个子任务，直到把每个子任务目标权重填满；
- `assignmentInfo()` 用于测试/日志，输出每个子任务分到的总权重与 key 数。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/KeyAssignment.java`（新增，从原 MapRangePartitioner 抽取）

**修改目的**：表示某个 sort key 被分配到哪些子任务，以及每个子任务分到的权重；partitioner 在每条记录上调用 `select()` 决定该记录送哪个子任务。

**工作逻辑**：
- 字段：`assignedSubtasks`、`subtaskWeightsWithCloseFileCost`、`closeFileCostWeight`、`subtaskWeightsExcludingCloseCost`、`keyWeight`、`cumulativeWeights`；
- `select()`：单子任务时直接返回；多子任务时按累计权重做加权随机抽样（`ThreadLocalRandom.nextLong(keyWeight)` 后在 `cumulativeWeights` 上二分查找）；
- 通过 `cumulativeWeights` 把 O(n) 的查找降到 O(log n)。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/MapRangePartitioner.java`（修改，大幅瘦身）

**修改目的**：移除原类内的 assignment 计算逻辑（已抽到 MapAssignment），改成只接受 coordinator 下发的 MapAssignment 做 lookup。

**工作逻辑**：
- 构造方法改为 `MapRangePartitioner(Schema, SortOrder, MapAssignment)`；
- `partition()` 直接 `sortKey.wrap(row)`，再 `mapAssignment.keyAssignments().get(sortKey)`，命中则 `keyAssignment.select()`，未命中则按 `newSortKeyCounter` 做 round-robin（每分钟打一次日志）；
- 删除 `assignment()`、`buildAssignment()` 等大量旧方法，类从约 320 行减到几十行。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SketchRangePartitioner.java`（新增）

**修改目的**：基于 range bounds 做分区。

**工作逻辑**：
- 构造时计算 comparator 并接收 `SortKey[] rangeBounds`；
- `partition()`：`Arrays.binarySearch(rangeBounds, sortKey, comparator)`，根据返回的负值算 `insertion point`，再通过 `RangePartitioner.adjustPartitionWithRescale` 适配当前 numPartitions。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/RangePartitioner.java`（新增）

**修改目的**：作为对外的 wrapper partitioner，根据统计类型委托给 Map 或 Sketch 实现，并处理 rescale 与未到位时的回退。

**工作逻辑**：
- `partition(StatisticsOrRecord wrapper, int numPartitions)`：如果是 statistics 事件，则更新 `delegatePartitioner` 并按 round-robin 返回；否则用 delegatePartitioner 路由记录，未到位时回退 round-robin；
- `delegatePartitioner(GlobalStatistics)`：按 type 构造 MapRangePartitioner 或 SketchRangePartitioner；
- `roundRobinCounter` 用随机起点 `AtomicLong` 避免子任务间同步；
- `adjustPartitionWithRescale(partition, numPartitionsStatsCalculation, numPartitions)`：scale-up 时忽略新增子任务（瞬时），scale-down 时用 `partition % numPartitions` 兜底。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/AggregatedStatisticsTracker.java`（修改，重写）

**修改目的**：支持 Map 与 Sketch 两种聚合，并实现 Auto 模式下的 Map → Sketch 自动切换；按 checkpoint 维度管理多个进行中的聚合。

**工作逻辑**：
- 类去掉泛型，构造参数新增 `schema`、`sortOrder`、`downstreamParallelism`、`statisticsType`、`switchToSketchThreshold`、`restoredStatistics`；
- 内部新增 `Aggregation` 静态内部类：每个 checkpoint 一个实例，记录已收到子任务集合、当前类型、Map 或 sketchStatistics；
- `merge(subtask, taskStatistics)`：
  - task 是 Map 而 current 是 Map：直接合并 Map；
  - 若 `configuredType == Auto` 且合并后 size > 阈值，调 `convertCoordinatorToSketch()`；
  - task 是 Map 但 current 是 Sketch：先把 task Map 转成 sketch 再 union；
  - task 是 Sketch 但 current 是 Map：先把 coordinator 全部 Map 转成 sketch 再 union；
- 聚齐所有 subtask 后产出 `CompletedStatistics`（Map 模式 `fromKeyFrequency`，Sketch 模式 `fromKeySamples`），并清理该 checkpoint 及之前的所有 Aggregation；
- 老的"部分完成阈值（90%）"逻辑被移除，改为必须聚齐全部子任务才算完成。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsCoordinator.java`（修改）

**修改目的**：协调统计聚合与下发，承担"算 GlobalStatistics"和"响应子任务统计请求"两个新职责。

**工作逻辑**：
- 构造参数增加 schema、sortOrder、downstreamParallelism、statisticsType、closeFileCostWeightPercentage；
- 内部新增 `completedStatisticsSerializer` 与 `globalStatisticsSerializer`，分别用于状态保存与事件序列化；
- `start()` 中创建 `AggregatedStatisticsTracker`，并把 restored 的 `completedStatistics` 传入（restore 在 `resetToCheckpoint` 中已完成）；
- `handleDataStatisticRequest(subtask, event)` 调 tracker，若聚合完成则同时更新 `completedStatistics` 与 `globalStatistics`，并广播 `globalStatistics`；
- `globalStatistics(completedStatistics, downstreamParallelism, comparator, closeFileCostWeightPercentage)` 静态方法：Sketch 模式调 `SketchUtil.rangeBounds`；Map 模式调 `MapAssignment.fromKeyFrequency`；
- `sendGlobalStatisticsToSubtasks`：`applyImmediately=false`，让子任务在 checkpoint 边界切换；
- 新增 `handleRequestGlobalStatisticsEvent(subtask, event)`：根据 event 中的 signature（即 hashCode）决定是否需要重发，避免重复广播；
- `resetToCheckpoint` 中从 completedStatistics 序列化数据反序列化恢复状态，配合 `subtaskGateways.reset()`。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsCoordinatorProvider.java`（修改）

**修改目的**：Provider 构造参数与 Coordinator 同步调整，去泛型。

**工作逻辑**：构造参数从 `(operatorName, operatorID, statisticsSerializer)` 改为 `(operatorName, operatorID, schema, sortOrder, downstreamParallelism, type, closeFileCostWeightPercentage)`，`getCoordinator` 直接构造对应 Coordinator。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsOperator.java`（修改）

**修改目的**：operator 子任务端切换为 task statistics 类型，新增 rescale 请求逻辑与统计类型迁移检查。

**工作逻辑**：
- 字段去泛型，新增 `parallelism`、`subtaskIndex`、`downstreamParallelism`、`statisticsType`、`taskStatisticsType`、`taskStatisticsSerializer`、`globalStatisticsSerializer`；
- `initializeState`：
  - 用 union state 保存 `GlobalStatistics`，让 scale-up 新增子任务也能恢复；
  - restore 时若没有状态，仅打 info 日志（之前是 warn）；
  - **无论是否 restore，都向 coordinator 发送 `RequestGlobalStatisticsEvent`**（带 signature 优化），让 coordinator 决定是否需要回发最新统计，覆盖 rescale 与之前发送失败两种情况；
  - 根据 `statisticsType` 与已恢复的 `globalStatistics.type()` 决定 `taskStatisticsType`，并构造对应 `localStatistics`；
- `processElement`：累加 sort key 到 `localStatistics`，并调 `checkStatisticsTypeMigration()` 后转发记录；
- `handleOperatorEvent`：反序列化 `GlobalStatistics`，若 `applyImmediately=true` 才立刻下发，否则等 `snapshotState`；
- `snapshotState`：在 checkpoint 边界 collect `StatisticsOrRecord.fromStatistics(globalStatistics)`（保证所有子任务同步切换），子任务 0 持久化状态，并向 coordinator 发送 `StatisticsEvent.createTaskStatisticsEvent`；最后重建 `localStatistics`；
- `checkStatisticsTypeMigration()`：Auto 模式下若 Map 大小超阈值或收到的 globalStatistics 已是 Sketch，则把 `taskStatisticsType` 改为 Sketch，并用 `SketchUtil.convertMapToSketch` 把已有 Map 数据迁过去。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/RequestGlobalStatisticsEvent.java`（新增）

**修改目的**：定义 operator → coordinator 的统计请求事件，用于 rescale / 失败重发场景。

**工作逻辑**：可选携带 `signature`（即子任务当前 globalStatistics 的 hashCode）。coordinator 收到后如果 hashCode 一致就跳过回发，节省传输。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/StatisticsEvent.java`（新增，原 DataStatisticsEvent 重命名/重写）

**修改目的**：统一 task → coordinator 与 coordinator → operator 两类事件的载体，并加入 `applyImmediately` 字段控制应用时机。

**工作逻辑**：
- 字段：checkpointId、statisticsBytes、applyImmediately；
- 两个工厂方法：`createTaskStatisticsEvent`（applyImmediately 强制 true，task 上报立刻被 coordinator 合并）、`createGlobalStatisticsEvent`（applyImmediately 由调用方决定，coordinator → operator 默认 false）。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/StatisticsOrRecord.java`（新增，原 DataStatisticsOrRecord 重命名）

**修改目的**：operator → partitioner 之间同时承载 statistics 与 record 的封装类，配合 `RangePartitioner` 路由。

**工作逻辑**：内部 `GlobalStatistics statistics` 与 `RowData record` 互斥，提供 `fromRecord` / `fromStatistics` / `reuseRecord` / `hasStatistics` / `hasRecord` 等方法。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/StatisticsOrRecordSerializer.java`（修改，原 DataStatisticsOrRecordSerializer 重命名/重写）

**修改目的**：序列化新 StatisticsOrRecord 类型，并优化 reuse 逻辑。

**工作逻辑**：序列化时先写标记位（statistics 或 record），再写对应数据；反序列化尽量 reuse 上一个对象以减少分配。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/StatisticsUtil.java`（新增，原 DataStatisticsUtil 替代）

**修改目的**：集中统计序列化/反序列化与类型决策辅助方法。

**工作逻辑**：
- `createTaskStatistics(type, operatorParallelism, numPartitions)`：按 type 构造 MapDataStatistics 或 SketchDataStatistics（reservoirSize 由 SketchUtil 计算）；
- `serializeDataStatistics` / `deserializeDataStatistics`、`serializeCompletedStatistics` / `deserializeCompletedStatistics`、`serializeGlobalStatistics` / `deserializeGlobalStatistics`：基于 `DataOutputSerializer` / `DataInputDeserializer` 的字节数组转换；
- `collectType(config, ...)`：根据配置与已有统计决定当前应当采用的类型。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SortKeySketchSerializer.java`（新增）

**修改目的**：让 DataSketches 的 `ReservoirItemsSketch<SortKey>` 能用 Flink 的 `SortKeySerializer` 做序列化（DataSketches 只接受 `ArrayOfItemsSerDe` 抽象类）。

**工作逻辑**：继承 `ArrayOfItemsSerDe<SortKey>`，实现 `serializeToByteArray(SortKey)`、`serializeToByteArray(SortKey[])`、`deserializeFromMemory` 等，内部委托给 Flink 的 `TypeSerializer<SortKey>` 与 `ListSerializer<SortKey>`。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsSerializer.java`（新增）

**修改目的**：Flink `TypeSerializer<DataStatistics>` 实现，根据 type 字段在 MapDataStatistics 与 SketchDataStatistics 间分发，并使用 `SortKeySketchSerializer` 序列化 sketch。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/CompletedStatisticsSerializer.java`（新增）

**修改目的**：Flink `TypeSerializer<CompletedStatistics>`，用于 coordinator 持久化 completed statistics 状态。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/GlobalStatisticsSerializer.java`（新增）

**修改目的**：Flink `TypeSerializer<GlobalStatistics>`，用于 operator 端 union state 持久化与 coordinator → operator 事件序列化。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SortKeySerializer.java`（修改）

**修改目的**：修复 sort order schema 兼容性检查。

**工作逻辑**：`resolveSchemaCompatibility` 中除了比较 schema，再调用 `sortOrder.sameOrder(...)` 比较 sort order，确保 sort order 变化时被识别为 incompatible，避免状态恢复错乱。

### `flink/v1.17/flink/src/jmh/java/org/apache/iceberg/flink/sink/shuffle/MapRangePartitionerBenchmark.java`（修改）

**修改目的**：适配新的 partitioner 构造方式。

**工作逻辑**：用 `SortOrderComparators` 替换原内联比较器构造，benchmark 调用改为通过 `MapAssignment.fromKeyFrequency` + `MapRangePartitioner(schema, sortOrder, mapAssignment)` 来构造被测对象。

### 测试文件

提交对测试做了大规模重写：

- 新增 `Fixtures.java`：共享测试数据与构造辅助；
- 重写 `TestAggregatedStatisticsTracker.java`：覆盖 Map、Sketch、Auto 切换、跨 checkpoint 聚合等场景；
- 新增 `TestCompletedStatisticsSerializer.java`、`TestDataStatisticsSerializer.java`、`TestGlobalStatisticsSerializer.java`：测试三类序列化器；
- 重写 `TestDataStatisticsCoordinator.java` 与 `TestDataStatisticsCoordinatorProvider.java`：覆盖 coordinator 接收事件、广播 globalStatistics、响应 RequestGlobalStatisticsEvent、reset 等流程；
- 重写 `TestDataStatisticsOperator.java`：覆盖 initializeState 请求、checkpoint 切换、类型迁移、union state restore 等；
- 重写 `TestMapDataStatistics.java` 与 `TestMapRangePartitioner.java`：适配新接口；
- 新增 `TestSketchDataStatistics.java`、`TestSketchUtil.java`、`TestSortKeySerializerPrimitives.java`：测试 sketch 实现与 rangeBounds 计算与 SortKeySerializer 基础类型分支。

### v1.18 模块

`flink/v1.18/...` 下所有同名文件做了与 v1.17 完全一致的修改（共 45 个文件），路径一一对应，不再赘述。

## 小结

- **成效**：把 main 分支上 Sketch 统计（PR #10331）与 coordinator 重构 + rescale 支持（PR #10457）两个 PR 的完整能力 backport 到 Flink v1.17 与 v1.18，使旧版本 Flink 也能使用 Auto 模式自动选择 Map/Sketch 统计、对高基数 sort key 不再 OOM，并能优雅应对写并发变更。
- **影响范围**：90 个文件、约 +8396/-3298 行；新增依赖 datasketches；仅作用于 Flink sink shuffle 子系统（v1.17/v1.18），不影响其他模块。
- **回迁到 1.4.x 的注意事项**：这是从 main backport 到 flink v1.17/v1.18 的"次级 backport"，1.4.x 维护分支上若 Flink 集成仍走老路径，回迁工作量较大。需要满足：(1) 1.4.x 已引入 datasketches 依赖；(2) SortKeySerializer、Flink 类型序列化框架版本兼容；(3) 需要一并迁移所有相关测试与 Fixtures。回迁前建议先评估 1.4.x 上是否已有同样的 RANGE distribution 高基数问题；若没有强烈需求，可暂缓回迁，仅维持 v1.19+ 默认行为；若回迁，需整套一起回迁（接口签名变更会牵动 Provider、Coordinator、Operator、Partitioner 全链路），不建议部分回迁。
