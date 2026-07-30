# 提交 0814：Flink: refactor sink shuffling statistics collection (#10331)

## 提交信息

- **序号**：0814 / 4088
- **哈希**：cbe391d1faad059a23c861de941636350642f6b4
- **短哈希**：cbe391d1f
- **日期**：2024-06-05 10:00:45 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：Flink: refactor sink shuffling statistics collection (#10331)
- **PR/Issue**：#10331

提交说明原文：refactor sink shuffling statistics collection to support sketch statistics and auto migration from Map stats to reservoir sampling sketch if cardinality is detected high

## 总体目的

对 Iceberg Flink sink shuffle 模块（`flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/`）的统计收集子系统进行重构，以支持两种互补的统计方式并能在运行时自动切换：

1. **Map 统计**（原有，`MapDataStatistics`）：用 `Map<SortKey, Long>` 精确记录每个 sort key 的出现次数。适用于低基数场景（如 country、event_type 等数百到数千种值）。
2. **Sketch 统计**（新增，`SketchDataStatistics`）：基于 Apache DataSketches 的 `ReservoirItemsSketch<SortKey>` 蓄水池采样。适用于高基数场景（如 device_id、user_id、uuid 等百万到十亿级），内存占用远低于全量 Map。
3. **Auto 模式**（新增）：默认用 Map 跟踪；当检测到 key 基数超过阈值时，自动迁移到 Sketch 采样。

重构前，整套代码虽然用了 `<D extends DataStatistics<D, S>, S>` 自引用泛型试图预留扩展点，但实际只有 `MapDataStatistics` 一个实现，所有类型签名被泛型污染得非常繁琐；同时 `AggregatedStatistics`、`DataStatisticsOperator`、`DataStatisticsCoordinator`、`AggregatedStatisticsTracker`、事件类、序列化器等都被泛型化，难以新增第二种统计实现。本重构通过去泛型化 + 引入 `StatisticsType` 枚举 + 把"合并 / 类型迁移"逻辑集中到 tracker 的 `Aggregation` 内部类，为新增 Sketch 统计扫清障碍。

## 如何达成设计目的（重构前后设计差异）

### 重构前

- `DataStatistics<D extends DataStatistics<D, S>, S>` 自引用泛型接口，方法包括 `isEmpty()`、`add(SortKey)`、`merge(D)`、`statistics()` 返回底层 `S`。
- 仅 `MapDataStatistics implements DataStatistics<MapDataStatistics, Map<SortKey, Long>>` 一个实现。
- `AggregatedStatistics<D, S>` 持有一个 `DataStatistics<D, S> dataStatistics`，并提供 `mergeDataStatistic(...)` 方法把子任务上报的统计合并进来。
- `AggregatedStatisticsTracker<D, S>` 用单变量 `inProgressStatistics` + `inProgressSubtaskSet` 跟踪当前 checkpoint 的聚合；当某个 checkpoint 上报量达到阈值（90%）才完成（"部分聚合"机制），并且如果新 checkpoint 来了旧的不完整就丢弃。
- `DataStatisticsCoordinator<D, S>`、`DataStatisticsCoordinatorProvider<D, S>`、`DataStatisticsOperator<D, S>`、`DataStatisticsOrRecord<D, S>`、`DataStatisticsOrRecordSerializer<D, S>`、`DataStatisticsEvent<D, S>` 全部跟着泛型化。
- 序列化器：只有 `MapDataStatisticsSerializer`，与 Map 类型强绑定。
- 事件类 `DataStatisticsEvent<D, S>` 在 coordinator ↔ operator 间传输 `DataStatistics` 的字节数组。
- 工具类 `DataStatisticsUtil` 提供序列化/反序列化静态方法。

### 重构后

1. **去泛型化**：所有类去掉 `<D, S>` 泛型参数，统一用裸类型 `DataStatistics` / `AggregatedStatistics`。这极大简化了类型签名和调用方代码。

2. **新增 `StatisticsType` 枚举**：明确表达 `Map` / `Sketch` / `Auto` 三种模式，并附 JavaDoc 说明各自适用场景与利弊。

3. **`DataStatistics` 接口精简**：
   - 去掉 `merge(D)`：合并逻辑不再放在 `DataStatistics` 实现里，而是下沉到 `AggregatedStatisticsTracker.Aggregation` 内部类，因为合并时还要处理 Map↔Sketch 转换，属于跨实现类型的逻辑，不应放在某个具体实现里。
   - `statistics()` 改为 `result()` 返回 `Object`：可能是 `Map<SortKey, Long>` 或 `ReservoirItemsSketch<SortKey>`，由调用方根据 `type()` 判断。新增 `type()` 方法返回 `StatisticsType`。

4. **`AggregatedStatistics` 重构为非泛型并直接持有结果**：
   - 不再包装 `DataStatistics` 对象，而是直接持有 `Map<SortKey, Long> keyFrequency`（Map 模式）或 `SortKey[] rangeBounds`（Sketch 模式，已经计算好的范围边界）。
   - 提供 `fromKeyFrequency(...)` 和 `fromRangeBounds(...)` 两个静态工厂方法，并校验二者互斥（一个非空、另一个必须为空）。
   - 新增 `equals` / `hashCode`（用 `Arrays.equals` 处理 rangeBounds）。

5. **`AggregatedStatisticsTracker` 重构 + 新增内部类 `Aggregation`**：
   - 用 `NavigableMap<Long, Aggregation> aggregationsPerCheckpoint` 替代单变量 `inProgressStatistics`，可同时跟踪多个未完成 checkpoint 的聚合状态。当某 checkpoint 完成时，`headMap(checkpointId, true).clear()` 一次性清理掉所有更早的未完成聚合。
   - 内部类 `Aggregation` 封装单个 checkpoint 的子任务合并状态：
     - `subtaskSet`：已上报的子任务集合。
     - `currentType`：当前正在使用的统计类型（可能因 Auto 迁移从 Map 变为 Sketch）。
     - `mapStatistics` 与 `sketchStatistics`：根据 currentType 二选一活跃。
     - `merge(DataStatistics)`：处理子任务上报的统计，根据双方类型组合执行不同策略：
       - 任务 Map + 当前 Map：直接 `merge(key, count, Long::sum)`；若配置为 Auto 且合并后 size 超过 `switchToSketchThreshold`（coordinator 默认 10w），调用 `convertCoordinatorToSketch()`。
       - 任务 Map + 当前 Sketch：先把任务 Map 转 Sketch（按计数重复 update），再合并。
       - 任务 Sketch + 当前 Map：先把 coordinator 的全局 Map 转 Sketch，再合并。
       - 任务 Sketch + 当前 Sketch：直接 `sketchStatistics.update(taskSketch)`。
     - `convertCoordinatorToSketch()`：用 `ReservoirItemsUnion` 替换 `mapStatistics`，通过 `SketchUtil.convertMapToSketch` 把所有 (key, count) 对按 count 次数 update 进 sketch，最后置 `mapStatistics = null` 并切换 `currentType`。
     - `completedStatistics(checkpointId)`：根据 `currentType` 生成 `AggregatedStatistics.fromKeyFrequency` 或 `fromRangeBounds`（后者调用 `SketchUtil.rangeBounds` 计算范围边界）。
   - 去掉了原来的 90% 部分聚合阈值：现在必须等所有子任务都上报后才完成。这样更严格，避免部分聚合导致统计偏差。
   - 支持恢复：构造函数接受 `@Nullable AggregatedStatistics restoredStatistics`，恢复后 `completedStatistics` 即为恢复值，新聚合的 `currentType` 通过 `StatisticsUtil.collectType(config, restoredStatistics)` 决定（恢复值优先于配置）。

6. **`DataStatisticsOperator` 重构**：
   - 字段调整：增加 `downstreamParallelism`、`statisticsType`；`localStatistics` 类型改为裸 `DataStatistics`；`globalStatistics` 类型从 `DataStatistics` 改为 `AggregatedStatistics`（与 coordinator 对齐）。
   - 新增 `taskStatisticsType`：跟踪当前 subtask 实际使用的统计类型（Auto 模式下会从 Map 迁移到 Sketch）。
   - 新增 `checkStatisticsTypeMigration()` 方法：在 `processElement` 和 `handleOperatorEvent` 中调用。当配置为 Auto 且 `localStatistics.type() == Map` 时，若本地 Map 大小超过 `OPERATOR_SKETCH_SWITCH_THRESHOLD`（默认 1w），或收到的全局 `AggregatedStatistics.type() == Sketch`（说明 coordinator 已迁移），就把本地 Map 转成 Sketch：新建 `SketchDataStatistics`，通过 `SketchUtil.convertMapToSketch(map, localStatistics::add)` 把已有 (key, count) 按 count 次数喂入 sketch。
   - 状态恢复：从 union list state 恢复 `AggregatedStatistics`，并通过 `StatisticsUtil.collectType(statisticsType, globalStatistics)` 决定 `taskStatisticsType`（恢复值优先）。

7. **`DataStatisticsCoordinator` 重构**：
   - 构造参数从 `TypeSerializer<DataStatistics<D, S>>` 改为 `Schema`、`SortOrder`、`downstreamParallelism`、`StatisticsType`，让 coordinator 自己构造所需的序列化器与 comparator。
   - 持有 `aggregatedStatisticsSerializer`（用于 checkpoint 序列化）与 `subtaskGateways`；`statisticsSerializer` 不再单独持有。
   - `start()` 时才创建 `AggregatedStatisticsTracker`（传入 `completedStatistics` 以支持恢复）。
   - `handleDataStatisticRequest` 接收 `StatisticsEvent`，调用 tracker 后若返回非空 `AggregatedStatistics`，则用 `sendAggregatedStatisticsToSubtasks` 广播给所有 subtask。
   - checkpoint 时用 `aggregatedStatisticsSerializer` 序列化 `completedStatistics`；恢复时反序列化。
   - 直接使用 `context.currentParallelism()` 而非缓存到字段。

8. **`DataStatisticsCoordinatorProvider` 重构**：去除泛型，构造参数改为 `Schema`、`SortOrder`、`downstreamParallelism`、`StatisticsType`。

9. **新增 `SketchUtil`**（核心 sketch 算法工具）：
   - `COORDINATOR_MIN_RESERVOIR_SIZE = 10_000`、`COORDINATOR_MAX_RESERVOIR_SIZE = 1_000_000`、`COORDINATOR_TARGET_PARTITIONS_MULTIPLIER = 100`、`OPERATOR_OVER_SAMPLE_RATIO = 10`。
   - `OPERATOR_SKETCH_SWITCH_THRESHOLD = 10_000`、`COORDINATOR_SKETCH_SWITCH_THRESHOLD = 100_000`（Auto 模式触发迁移的阈值，目前硬编码）。
   - `determineCoordinatorReservoirSize(numPartitions)`：目标 `numPartitions * 100`，并 clamp 到 [10K, 1M]，且调整为 numPartitions 的整数倍以方便后续范围边界计算。
   - `determineOperatorReservoirSize(operatorParallelism, numPartitions)`：`coordinatorReservoirSize * 10 / operatorParallelism`（过采样 10 倍后均摊到各 subtask）。
   - `rangeBounds(numPartitions, comparator, sketch)`：从 sketch 取出采样数组，调用 `determineBounds` 计算范围边界。
   - `determineBounds(numPartitions, comparator, sortKeys)`：先排序，再以 `ceil(length/numPartitions)` 为步长等距采样 `numPartitions - 1` 个候选边界，跳过重复值（线性探测下一个不同值）。假设 sort key 等权，适用于高基数场景。
   - `convertMapToSketch(taskMapStats, sketchConsumer)`：把 `Map<SortKey, Long>` 中每个 (key, count) 按 count 次数重复喂入 sketch consumer。注意这是 O(总记录数) 操作，可能较昂贵。

10. **新增 `StatisticsUtil`**（替代原 `DataStatisticsUtil`）：
    - `createTaskStatistics(type, operatorParallelism, numPartitions)`：根据 type 创建 `MapDataStatistics` 或 `SketchDataStatistics`（用 `determineOperatorReservoirSize` 计算 reservoir 大小）。
    - `serializeDataStatistics` / `deserializeDataStatistics`：基于 `TypeSerializer<DataStatistics>` 与 Flink 的 `DataOutputSerializer` / `DataInputDeserializer`。
    - `serializeAggregatedStatistics` / `deserializeAggregatedStatistics`：同上但针对 `AggregatedStatistics`。
    - `collectType(config)` 和 `collectType(config, restoredStatistics)`：决定实际使用的统计类型。Sketch 配置直接返回 Sketch；其它配置返回 Map；若有恢复值则优先返回恢复值的类型（保证恢复后行为一致）。

11. **重命名**（去掉 `Data` 前缀，因为已经统称为 "Statistics"）：
    - `DataStatisticsEvent` → `StatisticsEvent`
    - `DataStatisticsOrRecord` → `StatisticsOrRecord`
    - `DataStatisticsOrRecordSerializer` → `StatisticsOrRecordSerializer`
    - `DataStatisticsUtil` 删除（功能拆分到 `StatisticsUtil`）
    - `MapDataStatisticsSerializer` 删除（被通用的 `DataStatisticsSerializer` + `SortKeySketchSerializer` 替代）

12. **新增多个序列化器**：
    - `DataStatisticsSerializer`：通用 `DataStatistics` 序列化器，内部用 `EnumSerializer<StatisticsType>` + `MapSerializer<SortKey, Long>` + `SortKeySketchSerializer` 组合，按 type 选择性序列化对应字段。
    - `AggregatedStatisticsSerializer`：序列化 `AggregatedStatistics`，包含 `checkpointId`、`type`、以及根据 type 序列化 `keyFrequency`（Map）或 `rangeBounds`（List<SortKey>）。配套 `AggregatedStatisticsSerializerSnapshot` 实现状态兼容性。
    - `SortKeySketchSerializer`：序列化 `ReservoirItemsSketch<SortKey>`，包含 reservoir size (k)、n（见过的总元素数）、samples 数组。

13. **`MapRangePartitioner` 调整**：
    - 构造参数从 `MapDataStatistics dataStatistics` 改为 `Map<SortKey, Long> mapStatistics`（解耦，不再依赖具体 `DataStatistics` 实现类）。
    - 新 sort key 计数器日志改为每分钟输出后清零，并提示 "Fall back to round robin as statistics not learned yet"。

14. **`SortKeySerializer` 修复**：
    - `SortKeySerializerSnapshot.resolveSchemaCompatibility` 参数从 `TypeSerializer<SortKey>` 改为 `TypeSerializerSnapshot<SortKey>`（适配 Flink 1.19 API 变化，旧签名在 1.19 已被弃用/不存在）。
    - 同时增加 `sortOrder.sameOrder(oldSnapshot.sortOrder)` 检查，sort order 不一致时返回 `incompatible`，避免错误恢复。

15. **新增依赖**：`gradle/libs.versions.toml` 增加 `datasketches = "6.0.0"` 与 `datasketches = { module = "org.apache.datasketches:datasketches-java", version.ref = "datasketches" }`；`flink/v1.19/build.gradle` 增加 `implementation libs.datasketches`。

16. **测试重构**：新增 `Fixtures` 测试夹具类统一构造测试数据；新增 `TestAggregatedStatisticsSerializer`、`TestDataStatisticsSerializer`、`TestSketchDataStatistics`、`TestSketchUtil`、`TestSortKeySerializerPrimitives`；既有测试同步去泛型化并补充 Sketch / Auto 迁移路径覆盖。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：引入 DataSketches 依赖。

**工作逻辑**：新增版本号 `datasketches = "6.0.0"` 与库别名 `datasketches = { module = "org.apache.datasketches:datasketches-java", version.ref = "datasketches" }`。

### `flink/v1.19/build.gradle`

**修改目的**：让 Flink 1.19 模块依赖 DataSketches。

**工作逻辑**：在 `project(":iceberg-flink:iceberg-flink-${flinkMajorVersion}")` 块内 `implementation` 区添加 `implementation libs.datasketches`。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/StatisticsType.java`（新增）

**修改目的**：定义三种统计模式枚举。

**工作逻辑**：枚举值 `Map`、`Sketch`、`Auto`，每个值附 JavaDoc 说明适用场景与利弊。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SketchDataStatistics.java`（新增）

**修改目的**：基于蓄水池采样的 `DataStatistics` 实现。

**工作逻辑**：
- 持有 `ReservoirItemsSketch<SortKey> sketch`。
- `add(sortKey)` 调用 `sketch.update(sortKey.copy())`（拷贝避免上游复用对象）。
- `result()` 返回 sketch 本身。
- `equals` 比较 k、n、samples（用 `Arrays.deepEquals`）。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SketchUtil.java`（新增）

**修改目的**：封装 sketch 相关算法与阈值常量。

**工作逻辑**：见上文"如何达成设计目的"第 9 点。核心方法 `determineCoordinatorReservoirSize`、`determineOperatorReservoirSize`、`rangeBounds`、`determineBounds`、`convertMapToSketch`。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/StatisticsUtil.java`（新增）

**修改目的**：替代原 `DataStatisticsUtil`，提供统计对象创建与序列化辅助。

**工作逻辑**：见上文"如何达成设计目的"第 10 点。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatistics.java`

**修改目的**：去泛型化，简化接口。

**工作逻辑**：去掉 `<D, S>` 泛型与 `merge(D)` 方法；`statistics()` 改名 `result()` 返回 `Object`；新增 `type()` 返回 `StatisticsType`。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/MapDataStatistics.java`

**修改目的**：适配去泛型化的接口；增加 equals/hashCode。

**工作逻辑**：实现 `DataStatistics`（裸类型）；`type()` 返回 `StatisticsType.Map`；`result()` 返回 `keyFrequency` map；新增 equals/hashCode 基于 `keyFrequency`。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/AggregatedStatistics.java`

**修改目的**：去泛型化，直接持有 Map 或 rangeBounds，提供工厂方法。

**工作逻辑**：见上文"如何达成设计目的"第 4 点。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/AggregatedStatisticsTracker.java`

**修改目的**：去泛型化；引入 `Aggregation` 内部类支持多 checkpoint 并发聚合与 Map↔Sketch 自动迁移。

**工作逻辑**：见上文"如何达成设计目的"第 5 点。这是本次重构最复杂的文件，核心是 `Aggregation.merge` 的四种类型组合分支与 `convertCoordinatorToSketch` 迁移逻辑。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsCoordinator.java`

**修改目的**：去泛型化；直接持有 schema/sortOrder/downstreamParallelism/statisticsType；用 `AggregatedStatisticsSerializer` 序列化 completedStatistics。

**工作逻辑**：见上文"如何达成设计目的"第 7 点。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsCoordinatorProvider.java`

**修改目的**：去泛型化；构造参数改为 schema/sortOrder/downstreamParallelism/type。

**工作逻辑**：去泛型，传递新参数给 `DataStatisticsCoordinator`。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsOperator.java`

**修改目的**：去泛型化；支持 Auto 模式下 task 端 Map→Sketch 迁移；用 `AggregatedStatistics` 替代 `DataStatistics` 作为 globalStatistics。

**工作逻辑**：见上文"如何达成设计目的"第 6 点。核心新增 `checkStatisticsTypeMigration()` 方法。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/StatisticsEvent.java`（由 `DataStatisticsEvent.java` 重命名）

**修改目的**：去泛型化；同时支持 task→coordinator 上报 `DataStatistics` 和 coordinator→task 下发 `AggregatedStatistics`。

**工作逻辑**：类改为裸类型；新增两个静态工厂 `createTaskStatisticsEvent(...)` 和 `createAggregatedStatisticsEvent(...)`，分别用对应序列化器序列化。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/StatisticsOrRecord.java`（由 `DataStatisticsOrRecord.java` 重命名）

**修改目的**：去泛型化；内部 `statistics` 字段类型改为 `AggregatedStatistics`。

**工作逻辑**：包裹 `AggregatedStatistics` 或 `RowData`，二选一互斥；提供 `fromRecord` / `fromStatistics` / `reuseRecord` / `reuseStatistics` 静态方法。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/StatisticsOrRecordSerializer.java`（由 `DataStatisticsOrRecordSerializer.java` 重命名）

**修改目的**：去泛型化；用 `AggregatedStatisticsSerializer` 替代原 `DataStatistics` 序列化。

**工作逻辑**：组合 `AggregatedStatisticsSerializer` 与 `RowDataSerializer`，按字节标志位区分序列化的是 statistics 还是 record。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/AggregatedStatisticsSerializer.java`（新增）

**修改目的**：序列化 `AggregatedStatistics` 用于 checkpoint 持久化与协调器→算子下发。

**工作逻辑**：组合 `EnumSerializer<StatisticsType>` + `LongSerializer`（checkpointId）+ `MapSerializer<SortKey, Long>`（keyFrequency）+ `ListSerializer<SortKey>`（rangeBounds）；根据 type 选择性序列化对应字段。配套 `Snapshot` 实现状态兼容性。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsSerializer.java`（新增）

**修改目的**：替代原 `MapDataStatisticsSerializer`，支持 Map 与 Sketch 两种 `DataStatistics`。

**工作逻辑**：组合 `EnumSerializer<StatisticsType>` + `MapSerializer<SortKey, Long>` + `SortKeySketchSerializer`；根据 type 选择性序列化对应字段。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SortKeySketchSerializer.java`（新增）

**修改目的**：序列化 `ReservoirItemsSketch<SortKey>`。

**工作逻辑**：序列化 reservoir size (k)、n（已见元素数）、samples 数组（用 `SortKeySerializer` 序列化每个 sample）。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/MapDataStatisticsSerializer.java`（删除）

**修改目的**：被通用的 `DataStatisticsSerializer` 替代。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsUtil.java`（删除）

**修改目的**：功能拆分到 `StatisticsUtil`。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/MapRangePartitioner.java`

**修改目的**：解耦对 `MapDataStatistics` 的依赖；改善日志。

**工作逻辑**：构造参数改为 `Map<SortKey, Long> mapStatistics`；新 sort key 计数器日志每分钟输出后清零，提示 "Fall back to round robin as statistics not learned yet"。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SortKeySerializer.java`

**修改目的**：适配 Flink 1.19 API 变化；增强 schema 兼容性检查。

**工作逻辑**：`resolveSchemaCompatibility` 参数从 `TypeSerializer<SortKey>` 改为 `TypeSerializerSnapshot<SortKey>`；增加 `sortOrder.sameOrder(oldSnapshot.sortOrder)` 检查。

### 测试文件（多份新增与重写）

**修改目的**：覆盖新增 Sketch 路径与 Auto 迁移逻辑；同步去泛型化。

**工作逻辑**：新增 `Fixtures` 统一构造测试数据；新增 `TestAggregatedStatisticsSerializer`、`TestDataStatisticsSerializer`、`TestSketchDataStatistics`、`TestSketchUtil`、`TestSortKeySerializerPrimitives`；重写 `TestAggregatedStatisticsTracker`（535 行，覆盖四种合并组合与 Auto 迁移）、`TestDataStatisticsCoordinator`、`TestDataStatisticsCoordinatorProvider`、`TestDataStatisticsOperator`、`TestMapDataStatistics`、`TestMapRangePartitioner`。

## 小结

- **成效**：
  - 引入 Sketch 统计方式，使高基数 sort key 场景（device_id、user_id、uuid 等）下的内存占用从 O(基数) 降到 O(reservoir size)（默认上限 1M on coordinator）。
  - Auto 模式让用户无需预先判断基数高低，运行时自动选择合适统计方式。
  - 去泛型化大幅简化代码，所有相关类签名变清晰。
  - `AggregatedStatisticsTracker` 改为按 checkpoint 维度并发聚合，且去掉"90% 部分聚合"机制，统计更准确。
  - 合并 / 类型迁移逻辑集中到 `Aggregation.merge`，单一地点维护四种类型组合策略。
- **影响范围**：仅 Flink 1.19 模块（`flink/v1.19/`），不涉及其它 Flink 版本或非 Flink 模块。新增 `datasketches-java` 6.0.0 依赖（仅 Flink 1.19 模块）。状态序列化格式变化（globalStatisticsState 改为 `AggregatedStatistics`），与旧版本状态不兼容（但 `AggregatedStatisticsSerializer` 配套 Snapshot 应能处理升级路径，需验证）。
- **回迁注意事项**：
  1. **依赖兼容性**：1.4.x 分支需新增 `datasketches-java` 6.0.0 依赖。需确认 1.4.x 使用的 Gradle 版本和依赖解析机制能正确引入该库，且不与已有依赖冲突。
  2. **状态兼容性**：本次重构改变了 `globalStatisticsState` 的序列化格式（从 `DataStatistics` 改为 `AggregatedStatistics`）。如果有用户从旧版本（1.4.x 之前的 sketch 支持版本）升级，需验证 `AggregatedStatisticsSerializer` 的 Snapshot 能否正确处理旧状态；如果不能，需要清空状态或提供迁移逻辑。但对 1.4.x 来说，因为 1.4.x 当前还没有 Sketch 统计，从 1.4.x 升级到带本提交的版本时，旧状态就是 Map 格式，需要确认能否恢复。
  3. **Flink 1.19 API 依赖**：`SortKeySerializer.resolveSchemaCompatibility` 的签名变化依赖 Flink 1.19 的新 API。1.4.x 若仍维护 1.16/1.17/1.18 等更老 Flink 版本，需保留旧签名或提供版本适配。
  4. **Flink 版本同步**：本提交只改了 `flink/v1.19/`。1.4.x 若支持多个 Flink 版本（1.16/1.17/1.18/1.19/1.20），需要把同样的改动同步到每个版本的 `shuffle/` 目录。这是一个大工作量回迁。
  5. **测试同步**：测试文件改动量大（新增多个测试类、重写 TestAggregatedStatisticsTracker 等），回迁时需一并同步，否则覆盖不全。
  6. **行为变化**：去掉了 90% 部分聚合阈值，现在必须等所有子任务上报才完成聚合。在某些子任务慢或部分失败的场景下，聚合完成时间可能延后。需评估对生产环境的影响。
  7. **Sketch 算法准确性**：`determineBounds` 假设 sort key 等权，对长尾分布可能不够准确。回迁后若用于高度倾斜数据，需评估分区均衡度。
