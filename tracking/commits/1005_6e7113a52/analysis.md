# 提交 1005：Flink: a few small fixes or tuning for range partitioner (#10823)

## 提交信息

- **序号**：1005 / 4088
- **哈希**：6e7113a5291dffad38ffacc7d264456a2366a707
- **短哈希**：6e7113a52
- **日期**：2024-08-01 14:10:37 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：Flink: a few small fixes or tuning for range partitioner (#10823)
- **PR/Issue**：#10823

## 总体目的

本提交针对 Iceberg Flink sink 的 range 分区器（用于实现 `DistributionMode.RANGE`）做了一系列小修复与调优。这套 range 分区机制依赖一个 coordinator 在 checkpoint 时聚合各 subtask 的数据统计（基于 Map 或 Reservoir Sketch），计算出全局 range bounds，再下发给下游 subtask 用于按 sort key 做范围分区。在实测与代码 review 中发现以下几类问题：

1. **比较器使用错误的类型**：原本 `DataStatisticsCoordinator` 与 `SketchRangePartitioner` 使用 `SortOrderComparators.forSchema(schema, sortOrder)` 构造 SortKey 比较器。该比较器是基于源字段（source field）的类型来比较的，但 SortKey 内部存储的是经过 sort order 中 transform（如 `bucket`、`truncate`、`hour`）变换后的值。当 sort order 包含 transform 时，比较器使用的类型与实际存储类型不一致，可能导致比较结果错误，从而让 range bounds 计算与分区选择出错。
2. **空统计处理不完善**：当某个 checkpoint 周期内所有 subtask 都没有样本（例如该周期内没有数据流入）时，sketch 结果可能为 null，原代码直接对 null 调用 `sketch.getK()` 等方法会 NPE；同时空统计会被下发到下游 subtask，造成不必要的统计切换与潜在错误。
3. **checkpoint 序列化对 null 处理不完善**：`DataStatisticsCoordinator` 在 checkpoint 时如果 `completedStatistics` 为 null，会直接对 null 调用序列化方法；Flink 不允许 checkpoint 结果为 null，需要返回空字节数组。对应地，恢复时也需要把空字节数组当作"无状态可恢复"处理。
4. **代码重复**：`SketchRangePartitioner.partition` 中的二分查找 + rescale 调整逻辑与 `SketchUtil` 中其它逻辑属于同一族，应抽取到 `SketchUtil` 中复用。

本提交一次性修复上述问题，并为新增的 `SortKeyUtil`、抽取后的 `SketchUtil.partition` 以及 `RangePartitioner`/`SketchRangePartitioner` 的行为补充了测试。

## 如何达成设计目的

设计思路分四点：

1. 新增 `SortKeyUtil.sortKeySchema(schema, sortOrder)`，根据 sort order 中每个 sort field 的 transform 计算其结果类型，构造出一个反映 SortKey 真实存储布局的 Schema。把 `DataStatisticsCoordinator` 与 `SketchRangePartitioner` 中的比较器由 `SortOrderComparators.forSchema(...)` 改为 `Comparators.forType(SortKeyUtil.sortKeySchema(...).asStruct())`，让比较器按变换后的类型比较。
2. 在 `CompletedStatistics` 增加 `isEmpty()` 方法，在 `AggregatedStatisticsTracker` 中跳过空 taskSketch 的 update 并处理 sketch 结果为 null 的情况（返回空 samples 的 `CompletedStatistics`）；在 `DataStatisticsCoordinator` 中收到空统计时跳过下发，仅记日志。
3. 在 `DataStatisticsCoordinator.checkpoint` 中处理 `completedStatistics == null` 的情况，返回空字节数组；`resetToCheckpoint` 中把空字节数组也视为无状态可恢复。
4. 把 `SketchRangePartitioner.partition` 的二分查找 + rescale 逻辑抽到 `SketchUtil.partition`，`SketchRangePartitioner` 改为调用它。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SortKeyUtil.java`（新增）

**修改目的**：提供计算 SortKey 真实存储 schema 的工具方法，解决 sort order 含 transform 时比较器类型不匹配的问题。

**工作逻辑**：`sortKeySchema(schema, sortOrder)` 遍历 `sortOrder.fields()`，对每个 sort field：
- 通过 `sourceId` 找到源字段；
- 调用 `sortField.transform().getResultType(sourceField.type())` 得到变换后的类型；
- 构造一个新的 `NestedField`，field id 设为 transform 索引 `i`，field name 设为 `sourceFieldName_i`（避免同一源列上多个 transform 时的命名冲突），保留源字段的可选性与 doc。
最终返回由这些变换后字段组成的 `Schema`。这样基于该 schema 的 `Comparators.forType(...)` 就能按变换后的类型对 SortKey 元组做正确比较。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsCoordinator.java`

**修改目的**：修正比较器构造、处理空统计与 null checkpoint 状态。

**工作逻辑**：
- import 由 `SortOrderComparators` 改为 `Comparators`。
- 构造函数中 `this.comparator = SortOrderComparators.forSchema(schema, sortOrder)` 改为 `this.comparator = Comparators.forType(SortKeyUtil.sortKeySchema(schema, sortOrder).asStruct())`。
- `handleCurrentEvent` 收到聚合完成的 `maybeCompletedStatistics` 后，新增 `isEmpty()` 判断：若为空，仅记日志 `"Skip aggregated statistics for checkpoint {} as it is empty."`，不更新 `completedStatistics`/`globalStatistics`，也不下发；否则走原逻辑并记 `"Completed statistics aggregation for checkpoint {}"`。
- `checkpoint()` 中若 `completedStatistics == null`，返回 `new byte[0]`（Flink 不允许 null checkpoint 结果），否则正常序列化。
- `resetToCheckpoint()` 中判断由 `checkpointData == null` 改为 `checkpointData == null || checkpointData.length == 0`，把空字节数组也当作无状态可恢复。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/AggregatedStatisticsTracker.java`

**修改目的**：避免对空 sketch 调用方法导致 NPE，并跳过空 taskSketch 的更新。

**工作逻辑**：
- 在 sketch 模式下更新统计时，新增 `if (taskSketch.getNumSamples() > 0)` 判断，仅当 taskSketch 有样本时才调用 `sketchStatistics.update(taskSketch)`，避免空更新。
- 聚合完成取 `sketchStatistics.getResult()` 后，新增 `if (sketch != null)` 判断：非 null 时记日志并返回 `CompletedStatistics.fromKeySamples(...)`；为 null 时记 `"Empty sketch statistics."` 并返回 `CompletedStatistics.fromKeySamples(checkpointId, new SortKey[0])`。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/CompletedStatistics.java`

**修改目的**：提供判断统计是否为空的能力，供 coordinator 决定是否下发。

**工作逻辑**：新增 `boolean isEmpty()` 方法，Sketch 类型时返回 `keySamples.length == 0`，否则返回 `keyFrequency().isEmpty()`。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SketchUtil.java`

**修改目的**：抽取 range 分区选择逻辑为公共方法，便于复用与测试。

**工作逻辑**：新增静态方法 `partition(SortKey key, int numPartitions, SortKey[] rangeBounds, Comparator<StructLike> comparator)`：
- 用 `Arrays.binarySearch(rangeBounds, key, comparator)` 二分查找；
- 若返回负数，转换为插入点 `(-partition - 1)`；
- 若超过 `rangeBounds.length`，截断为 `rangeBounds.length`；
- 最后调用 `RangePartitioner.adjustPartitionWithRescale(partition, rangeBounds.length + 1, numPartitions)` 处理 rescale。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SketchRangePartitioner.java`

**修改目的**：复用抽取后的 `SketchUtil.partition`，并修正比较器构造。

**工作逻辑**：
- import 移除 `Arrays`、`SortOrderComparators`，新增 `Comparators`。
- 构造函数中比较器改为 `Comparators.forType(SortKeyUtil.sortKeySchema(schema, sortOrder).asStruct())`。
- `partition` 方法体由原来的二分查找 + rescale 内联逻辑替换为单行 `return SketchUtil.partition(sortKey, numPartitions, rangeBounds, comparator);`。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/RangePartitioner.java`

**修改目的**：补充类注释与轻微格式调整。

**工作逻辑**：将原本含糊的注释 `/** The wrapper class */` 改为 `/** This custom partitioner implements the {@link DistributionMode#RANGE} for Flink sink. */`，并 import `DistributionMode`；另有一处注释换行调整，无功能变化。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestRangePartitioner.java`（新增）

**修改目的**：覆盖 `RangePartitioner` 在统计可用前对 record 与 statistics wrapper 的 round-robin 行为。

**工作逻辑**：两个测试：
- `testRoundRobinRecordsBeforeStatisticsAvailable`：无 GlobalStatistics 时，连续 4 条 record 应被 round-robin 分配到 0/1/2/3。
- `testRoundRobinStatisticsWrapper`：对 `StatisticsOrRecord.fromStatistics(...)` 同样 round-robin 分配。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestSketchRangePartitioner.java`（新增）

**修改目的**：覆盖 `SketchRangePartitioner` 在给定 range bounds 下的分区选择正确性。

**工作逻辑**：构造 16 个分区、15 个 range bounds（按 id 步长 1000 划分），遍历 0..16000 的 id，验证每条记录落到预期分区（`expectedPartition = id == 0 ? 0 : (id - 1) / RANGE_STEP`）。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestSketchUtil.java`

**修改目的**：为抽取后的 `SketchUtil.partition` 添加分区与 rescale 测试。

**工作逻辑**：
- 静态 import 改为 `SORT_ORDER_COMPARTOR`（小写拼写修正）。
- 新增 `testPartitioningAndScaleUp`（参数化 4/6 分区）：基于 4 分区计算的 range bounds，验证各 key 落到正确分区，scale-up 时多出的分区不分配 key。
- 新增 `testPartitionScaleDown`：3 分区下，超出 range 的分区通过 mod 重新分配。
- 新增辅助方法 `assertPartition`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestSortKeyUtil.java`（新增）

**修改目的**：覆盖 `SortKeyUtil.sortKeySchema` 对含 transform 的 sort order 的 schema 计算。

**工作逻辑**：构造一个含嵌套结构、多种类型字段的 schema，sort order 包含 `asc("ratio")`、`hour("user.ts")`、`bucket("user.device_id", 16)`、`truncate("user.location.blob", 16)`，验证 `sortKeySchema` 返回的 struct 由四个变换后字段组成：`ratio_0: double`、`ts_1: int`、`device_id_2: int`、`blob_3: binary`，field id 为 0/1/2/3，名称为 `源列名_索引`。

## 小结

- **成效**：修复了 range 分区器在 sort order 含 transform 时比较器类型错误的问题（通过新增 `SortKeyUtil.sortKeySchema`）；修复了空统计与 null checkpoint 状态导致的 NPE/异常；将分区选择逻辑抽取到 `SketchUtil.partition` 便于复用；并补充了 4 个测试类覆盖 RangePartitioner、SketchRangePartitioner、SketchUtil.partition、SortKeyUtil 的行为。
- **影响范围**：仅 Flink v1.19 模块的 `sink/shuffle` 子包，11 个文件、+420/-49 行，其中 4 个为新增测试类、1 个为新增工具类。
- **回迁到 1.4.x 的注意事项**：是否回迁移取决于 1.4.x 是否包含该 range partitioner 功能。若 1.4.x 已合入 range partitioner 相关代码（PR #10411 系列等），则比较器类型修复（SortKeyUtil）属于功能性 bug 修复，建议回迁；空统计与 null checkpoint 处理也属于健壮性修复，建议回迁。回迁时需注意 Flink 版本目录差异（本提交改的是 `flink/v1.19/`，1.4.x 可能同时维护 v1.17/v1.18/v1.19/v1.20 多个版本目录，需同步修改对应版本目录）。若 1.4.x 未合入 range partitioner，则无需回迁。整体属于中低风险回迁（前提是功能已存在）。
