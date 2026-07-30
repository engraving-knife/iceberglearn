# 提交 1013：Flink: backport PR #10823 for range partitioner fixup (#10847)

## 提交信息

- **序号**：1013 / 4088
- **哈希**：af75440da8d6e7b509b3197251c965543017b015
- **短哈希**：af75440da
- **日期**：2024-08-02 08:39:47 -0700
- **作者**：Steven Zhen Wu <stevenz3wu@gmail.com>
- **提交说明**：Flink: backport PR #10823 for range partitioner fixup (#10847)
- **PR/Issue**：#10847（回引原 PR #10823）

## 总体目的

Iceberg Flink 集成在 `flink/sink/shuffle` 包下实现了 `DistributionMode.RANGE` 分布模式：通过 `DataStatisticsCoordinator`（OperatorCoordinator）收集各 subtask 上报的数据 sketch，聚合出全局 `GlobalStatistics`（含 range bounds），再下发到下游 `RangePartitioner`/`SketchRangePartitioner`，使写入数据按 sort key 均衡分布到各 subtask。该机制依赖 sketch 采样与 comparator 比较。

PR #10823 在 main 分支上修复了 range partitioner 的若干缺陷：

1. **空 sketch 处理**：当某个 checkpoint 周期内 subtask 上报的 sketch 没有样本（`getNumSamples() == 0`）时，原代码仍调用 `sketchStatistics.update(taskSketch)`，且最终 `sketchStatistics.getResult()` 可能返回 `null`，导致 `sketch.getK()` 抛 NPE。空统计应当被安全跳过。
2. **空 CompletedStatistics 传播**：当聚合出的 `CompletedStatistics` 为空（无样本/无频率）时，不应再生成 `GlobalStatistics` 下发到 subtask，否则下游会基于空样本计算错误的 range bounds。
3. **checkpoint 空快照**：`DataStatisticsCoordinator` 在 `checkpointCoordinates` 时若 `completedStatistics == null`，原代码会调用 `StatisticsUtil.serializeCompletedStatistics(null, ...)` 触发 NPE；Flink 不允许 `null` 的 checkpoint 结果，需要返回空字节数组。
4. **restore 时空数据识别**：`resetToCheckpoint` 原本只把 `checkpointData == null` 视为"无数据可恢复"，但新逻辑会写出空字节数组，所以也需要把 `length == 0` 当作无数据处理。
5. **SortKey comparator 修正**：原代码用 `SortOrderComparators.forSchema(schema, sortOrder)` 构造 comparator，这对包含 transform（如 `hour`、`bucket`、`truncate`）的 sort order 可能给出错误的比较结果，因为比较的是源字段类型而非 transform 后的类型。修正方案是先按 `SortKey` 的实际"结果 schema"（每个 sort field 是 transform 后的类型）构造 comparator，即 `Comparators.forType(SortKeyUtil.sortKeySchema(schema, sortOrder).asStruct())`，确保 range bounds 二分查找与 `SortKey` 比较时类型一致。
6. **代码复用**：把 `SketchRangePartitioner.partition` 中的二分查找 + rescale 调整逻辑提取到 `SketchUtil.partition(...)` 静态方法，便于测试与复用。

本提交 #10847 是把这些 fix 回迁（backport）到 Iceberg 仓库内维护的 `flink/v1.17` 和 `flink/v1.18` 两个旧版 Flink 模块（main 分支已切到 v1.19/v1.20，但 v1.17/v1.18 仍需维护）。两个版本目录下的同名文件改动完全一致。

## 如何达成设计目的

总体思路是"防御性补全 + comparator 修正 + 工具提取"：

1. 在 `AggregatedStatisticsTracker` 中对 `taskSketch` 做"非空才 update"判断；对最终 `sketchStatistics.getResult()` 做 null 判断，null 时返回空样本的 `CompletedStatistics`。
2. 在 `CompletedStatistics` 中新增 `isEmpty()` 方法，区分 Sketch 与 Map 两种类型的空判定。
3. 在 `DataStatisticsCoordinator.handleCurrentEvent`（聚合完成回调）中，若 `maybeCompletedStatistics.isEmpty()` 则仅记录日志跳过，不再生成/下发 `GlobalStatistics`。
4. 在 `DataStatisticsCoordinator.checkpointCoordinates` 中处理 `completedStatistics == null`：返回空字节数组 `new byte[0]` 作为 checkpoint 结果；`resetToCheckpoint` 中把 `checkpointData == null || checkpointData.length == 0` 都视为无数据。
5. 新增 `SortKeyUtil.sortKeySchema(schema, sortOrder)`：根据 sort order 中每个 `SortField` 的 transform 计算结果类型，生成"SortKey 结果 schema"，处理同源字段多次 transform 的命名冲突（字段名加 `_index` 后缀，字段 id 用 sort field 索引）。
6. `DataStatisticsCoordinator` 和 `SketchRangePartitioner` 中的 comparator 构造改为 `Comparators.forType(SortKeyUtil.sortKeySchema(schema, sortOrder).asStruct())`，与 `SortKey` 的实际比较类型对齐。
7. 把 `SketchRangePartitioner.partition` 中的二分查找逻辑提取到 `SketchUtil.partition(SortKey, numPartitions, rangeBounds, comparator)`，原方法体改为单行委托。
8. `RangePartitioner` 仅做注释微调（澄清其用途是实现 `DistributionMode.RANGE`）。
9. 配套新增/补充测试：`TestRangePartitioner`（round-robin 前置行为）、`TestSketchRangePartitioner`（带 range bounds 的分区正确性）、`TestSortKeyUtil`（`sortKeySchema` 对含 transform 的 sort order 输出正确结构）、`TestSketchUtil` 补充 `testPartitioningAndScaleUp` / `testPartitionScaleDown`。

## 修改详情

下面以 `flink/v1.18` 为例说明（`flink/v1.17` 下同名文件改动完全一致，不再赘述）。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/AggregatedStatisticsTracker.java`

**修改目的**：修复空 sketch 导致的 NPE 与错误聚合。

**工作逻辑**：
- 在 `sketchStatistics.update(taskSketch)` 外层加 `if (taskSketch.getNumSamples() > 0)` 守卫，避免把无样本的 sketch 喂入聚合器（这会让 reservoir 状态异常或无意义）。
- 在 `complete()` 阶段，`ReservoirItemsSketch<SortKey> sketch = sketchStatistics.getResult();` 后增加 null 判断：非 null 时按原逻辑记录日志并 `CompletedStatistics.fromKeySamples(checkpointId, sketch.getSamples())`；为 null（即从未有过样本）时记录 `"Empty sketch statistics."` 并返回 `CompletedStatistics.fromKeySamples(checkpointId, new SortKey[0])`，让下游能通过 `isEmpty()` 识别空状态而非崩溃。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/CompletedStatistics.java`

**修改目的**：提供统一的"是否为空"判定，供 coordinator 决定是否跳过下发。

**工作逻辑**：新增 `boolean isEmpty()`：若 `type == StatisticsType.Sketch`，判断 `keySamples.length == 0`；否则（Map 类型）判断 `keyFrequency().isEmpty()`。这样 coordinator 无需关心具体类型即可判断空状态。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsCoordinator.java`

**修改目的**：处理空统计、空 checkpoint、空 restore 数据，并切换 comparator 构造方式。

**工作逻辑**：
- import 调整：去掉 `org.apache.iceberg.SortOrderComparators`，新增 `org.apache.iceberg.types.Comparators`。
- 构造器中 `this.comparator = SortOrderComparators.forSchema(schema, sortOrder);` 改为 `this.comparator = Comparators.forType(SortKeyUtil.sortKeySchema(schema, sortOrder).asStruct());`，使 comparator 按 transform 后类型比较，与 `SortKey` 内部存储一致。
- 聚合完成回调：原代码无条件 `this.completedStatistics = maybeCompletedStatistics;` 并计算 `globalStatistics` 下发；新代码先 `if (maybeCompletedStatistics.isEmpty())` 仅记日志 `"Skip aggregated statistics for checkpoint {} as it is empty."`，否则才进入原下发分支（并补充 `"Completed statistics aggregation for checkpoint {}"` 日志）。
- `checkpointCoordinates`：原代码 `resultFuture.complete(StatisticsUtil.serializeCompletedStatistics(completedStatistics, ...))`；新代码先判断 `completedStatistics == null`，若 null 则 `resultFuture.complete(new byte[0])`（注释说明 "null checkpoint result is not allowed, hence supply an empty byte array"），否则走原序列化路径。
- `resetToCheckpoint`：原 `if (checkpointData == null)` 改为 `if (checkpointData == null || checkpointData.length == 0)`，把新写出的空字节数组也视为"无数据可恢复"。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/RangePartitioner.java`

**修改目的**：注释澄清，无功能改动。

**工作逻辑**：类注释由模糊的 `The wrapper class` 改为 `This custom partitioner implements the DistributionMode#RANGE for Flink sink.`；新增 `import org.apache.iceberg.DistributionMode;` 仅为 javadoc `@link` 引用。另一处把两行注释合并为一行（无语义变化）。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SketchRangePartitioner.java`

**修改目的**：切换 comparator 构造方式，并把二分查找逻辑提取到 `SketchUtil`。

**工作逻辑**：
- 去掉 `import java.util.Arrays;` 与 `import org.apache.iceberg.SortOrderComparators;`，新增 `import org.apache.iceberg.types.Comparators;`。
- 构造器中 `this.comparator = SortOrderComparators.forSchema(schema, sortOrder);` 改为 `Comparators.forType(SortKeyUtil.sortKeySchema(schema, sortOrder).asStruct());`。
- `partition(RowData, int)` 方法体由约 12 行的 `Arrays.binarySearch` + 插入点修正 + 上界裁剪 + `RangePartitioner.adjustPartitionWithRescale` 改为单行 `return SketchUtil.partition(sortKey, numPartitions, rangeBounds, comparator);`。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SketchUtil.java`

**修改目的**：抽取并暴露二分查找分区逻辑，便于测试和复用。

**工作逻辑**：新增静态方法 `static int partition(SortKey key, int numPartitions, SortKey[] rangeBounds, Comparator<StructLike> comparator)`：调用 `Arrays.binarySearch(rangeBounds, key, comparator)`，对负返回值转换为插入点 `-partition - 1`，对超过 `rangeBounds.length` 的结果裁剪到 `rangeBounds.length`，最后调用 `RangePartitioner.adjustPartitionWithRescale(partition, rangeBounds.length + 1, numPartitions)` 处理 scale up/down。需新增 `import java.util.Arrays;` 与 `import java.util.Comparator;` 以及 `import org.apache.iceberg.StructLike;`。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SortKeyUtil.java`（新增）

**修改目的**：提供 `SortKey` 的"结果 schema"计算，作为 comparator 构造与未来 SortKey 序列化的基础。

**工作逻辑**：
- `static Schema sortKeySchema(Schema schema, SortOrder sortOrder)`：遍历 `sortOrder.fields()`，对每个 `SortField`：
  - 通过 `sourceId` 在原 schema 中找到源 `NestedField`，校验非 null；
  - 调用 `sortField.transform().getResultType(sourceField.type())` 得到 transform 后类型；
  - 为处理"同源字段多次 transform"导致的命名/ID 冲突，新生成 `NestedField` 的 id 设为 sort field 索引 `i`，name 设为 `源字段名 + '_' + i`，保留源字段的 optional 与 doc；
  - 收集所有新字段构造 `new Schema(transformedFields)` 返回。
- 注释中对比 `PartitionKey` 的同名冲突处理，说明本方案与之一致。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestSketchUtil.java`

**修改目的**：补充对 `SketchUtil.partition` 的覆盖测试，并把静态导入统一为非限定形式。

**工作逻辑**：
- 新增 `@ParameterizedTest @ValueSource(ints = {4, 6}) testPartitioningAndScaleUp(int numPartitions)`：基于 4 分区的 range bounds（`c, j, m`），断言各 `SortKey` 落入预期分区，覆盖 scale-up（numPartitions > rangeBounds.length+1）场景。
- 新增 `@Test testPartitionScaleDown()`：用 numPartitions=3 验证 scale-down 时超出 range 的 key 通过 `adjustPartitionWithRescale` 的 mod 机制重新分配到 0 号分区。
- 新增私有 `assertPartition(expected, key, numPartitions, rangeBounds)` 调用 `SketchUtil.partition(...)` 断言。
- 把 `Fixtures.SORT_ORDER_COMPARTOR` 改为静态导入 `SORT_ORDER_COMPARTOR`（纯风格调整）。新增 `ParameterizedTest`、`ValueSource` import。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestSortKeyUtil.java`（新增）

**修改目的**：验证 `SortKeyUtil.sortKeySchema` 对含 transform 的 sort order 生成正确的结果 schema。

**工作逻辑**：构造一个含嵌套 struct 的 schema（id、ratio、user[name/ts/device_id/location[lat/long/blob]]），sort order 为 `asc(ratio)` + `hour(user.ts)` + `bucket(user.device_id, 16)` + `truncate(user.location.blob, 16)`。断言 `SortKeyUtil.sortKeySchema(schema, sortOrder).asStruct()` 等于：
- `ratio_0`（DoubleType，required）
- `ts_1`（IntegerType，required，hour transform 结果）
- `device_id_2`（IntegerType，optional，bucket transform 结果，因 source optional）
- `blob_3`（BinaryType，required，truncate transform 结果）

字段 id 依次为 0/1/2/3（sort field 索引），命名加 `_索引` 后缀，与设计一致。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestRangePartitioner.java`（新增）

**修改目的**：验证 `RangePartitioner` 在统计信息未就绪时的 round-robin 行为。

**工作逻辑**：
- `testRoundRobinRecordsBeforeStatisticsAvailable`：在未提供 `GlobalStatistics` 时，连续对 4 条 record 调 `partition`，断言结果集合恰好为 `{0,1,2,3}`（round-robin 均匀分配）。
- `testRoundRobinStatisticsWrapper`：对 `StatisticsOrRecord.fromStatistics(...)` 包装的统计事件（而非 record）也走 round-robin，断言同样覆盖 4 个分区。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestSketchRangePartitioner.java`（新增）

**修改目的**：验证 `SketchRangePartitioner` 在给定 range bounds 下的分区正确性。

**工作逻辑**：构造 16 分区的 range bounds（id 步长 1000：1000, 2000, ..., 15000），对 `id` 从 0 到 16000 的行调用 `partitioner.partition(row, 16)`，断言 `partition == (id == 0 ? 0 : (id-1)/1000)`，覆盖边界（恰好等于 range bound 的 key 归入更高分区）。

### `flink/v1.17/...` 下同名 11 个文件

**修改目的**：把上述修复与测试同步到 v1.17 模块。

**工作逻辑**：与 v1.18 完全一致，逐文件对应。

## 小结

- **成效**：修复了 Flink range partitioner 在空统计、空 checkpoint、含 transform 的 sort order 下的若干 NPE/错误分区缺陷；通过提取 `SortKeyUtil` 与 `SketchUtil.partition` 让 comparator 构造与二分查找逻辑可被独立测试；新增 4 个测试类覆盖 round-robin、scale up/down、transform schema 等场景。本提交是 main 分支 PR #10823 向 v1.17/v1.18 旧版 Flink 模块的回迁。
- **影响范围**：`flink/v1.17/flink` 与 `flink/v1.18/flink` 下 `sink/shuffle` 包的 7 个 main 类（其中 1 个新增 `SortKeyUtil`）和 4 个 test 类（其中 3 个新增），共 22 个文件，840 增 / 98 删。仅影响 `DistributionMode.RANGE` 相关路径，不涉及 HASH/NONE 分布模式与其它 sink 行为。
- **回迁到 1.4.x 的注意事项**：本提交本身已是"回迁到旧 Flink 版本"的产物，针对的是 v1.17/v1.18 模块。1.4.x 分支若仍维护 v1.17/v1.18 模块且存在同样的 range partitioner 缺陷，可直接 cherry-pick；但需注意：(1) 1.4.x 是否已包含 `flink/sink/shuffle` 包（range partitioner 是较新功能，1.4.x 早期版本可能没有，若没有则本提交无意义）；(2) `SortKeyUtil`、`SketchUtil.partition` 等新增类需要一并带入；(3) 该 fix 涉及 comparator 语义变化（从源字段类型比较改为 transform 后类型比较），属于行为修正，可能影响既有 range 分区结果分布，但更接近正确语义，回迁后建议跑一遍 `TestSketchRangePartitioner`/`TestSketchUtil`/`TestSortKeyUtil` 验证。整体适合回迁，风险点在于确认 1.4.x 是否已有等价修复或是否需要先回迁 range partitioner 功能本身。
