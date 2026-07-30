# 提交 0457：Core: Fix performance issue when combining tasks by partition (#9629)

## 提交信息

- **序号**：0457
- **完整哈希**：fb02bd2d713c9bef8b8ccee334f6d65087c344b2
- **短哈希**：fb02bd2d7
- **日期**：2024-02-03 12:42:58 -0800
- **作者**：Anton Okolnychyi <aokolnychyi@apple.com>
- **提交说明**：Core: Fix performance issue when combining tasks by partition (#9629)
- **关联 PR**：#9629
- **修改文件**：4 个，共 99 行新增、122 行删除

## 总体目的

本提交修复 Iceberg 核心模块在按分区合并扫描任务（combining tasks by partition）时存在的一个性能问题。`TableScanUtil.planTaskGroups` 在启用分组键（grouping key，用于将同一分区的多个文件扫描任务合并为一个 task group）时，会为每个任务的分区元组计算其分组键投影，并将投影结果作为 `StructLikeMap` 的键来归并任务。

此前的实现中，`projectGroupingKey` 方法对每一条任务都会 `new PartitionData(groupingKeyType)` 新建一个 `PartitionData` 对象，然后逐字段从 `StructProjection` 读取并调用 `set()` 写入。问题在于 `PartitionData.set()` 内部对每个值都做类型转换判断（Utf8 转 String、ByteBuffer 转 byte[]），而 `StructProjection.get()` 返回的值往往已经是从底层 `PartitionData` 透传出来的内部表示，这些转换在多数情况下是冗余的。更关键的是，对于高基数分区或大量文件（例如每个分区数万个文件）的场景，反复创建 `PartitionData` 并逐字段拷贝会产生显著的对象分配与 GC 压力，成为任务规划阶段的热点。

本提交的优化思路是引入一个可复用的 `PartitionData` 模板（template），通过新增的 `copyFor(StructLike)` 方法以一次拷贝生成新的分组键，避免在 `set()` 中重复执行类型转换逻辑，也减少了方法调用层级。同时将原本散落在 `set()` 中的类型转换逻辑抽取为独立的 `toInternalValue` 静态方法，使 `set()` 与 `copyData()` 共用同一套转换逻辑，既消除重复又提升可读性。

由于 `PartitionData` 的序列化形式发生变化（内部字段与构造方式调整），提交在 `.palantir/revapi.yml` 中为 1.4.0 版本登记了 `PartitionData` 的 `java.class.defaultSerializationChanged` 兼容性豁免，说明跨版本序列化本就不受支持，不影响二进制兼容性判定。

此外，提交同步重构了对应的 JMH 基准测试 `TaskGroupPlanningBenchmark`：用 `FileGenerationUtil` 生成数据文件与删除文件取代原先通过 Spark 真实写入再复制副本的方式，并新增了一个带分组键的基准方法 `planTaskGroupsWithGrouping`，使基准测试既能更轻量地准备数据，又能直接度量本次优化所影响的分组规划路径。

## 如何达成设计目的

实现路径分三步：第一步，在 `PartitionData` 中新增一个接收 `PartitionData toCopy` 与 `StructLike partition` 的私有拷贝构造器，以及一个公开的 `copyFor(StructLike)` 工厂方法，内部通过新增的 `copyData` 静态方法一次性从分区元组拷贝所有字段并做类型转换；第二步，将原 `set()` 中内联的 Utf8/ByteBuffer 转换逻辑抽取为 `toInternalValue` 静态方法，供 `set()` 与 `copyData()` 共用；第三步，在 `TableScanUtil.planTaskGroups` 中提前创建一个 `groupingKeyTemplate`，对每个任务调用 `groupingKeyTemplate.copyFor(groupingKeyProjection.wrap(partition))` 来生成分组键，替换原先的 `projectGroupingKey` 工具方法（该方法被删除）。基准测试侧则重写数据准备逻辑并新增带分组的基准方法以验证优化效果。

## 修改详情

### .palantir/revapi.yml

**修改目的**：为 `PartitionData` 的二进制兼容性变更登记豁免。

**工作逻辑**：在 `acceptedBreaks` 的 `1.4.0` 段、`org.apache.iceberg:iceberg-core` 条目下，新增一条 `java.class.defaultSerializationChanged` 记录，old 与 new 均为 `class org.apache.iceberg.PartitionData`，justification 为 "Serialization across versions is not supported"。这告知 revapi 该类的默认序列化形式变更属于可接受范围，因为 Iceberg 不支持跨版本序列化。该条目与同段中 `NameMapping` 的豁免写法一致。

### core/src/main/java/org/apache/iceberg/PartitionData.java

**修改目的**：引入可复用的分组键拷贝机制，消除 `set()` 中的重复类型转换逻辑。

**工作逻辑**：

1. **新增拷贝构造器**：新增 `private PartitionData(PartitionData toCopy, StructLike partition)` 构造器。它继承 `toCopy` 的 `partitionType`、`size`、`stringSchema`、`schema` 等不变状态，但通过 `copyData(partitionType, partition)` 重新生成 `data` 数组，即用传入的 `partition` 的字段值替换原数据。注释说明 "Copy constructor that inherits the state but replaces the partition values"。

2. **新增 `copyFor` 工厂方法**：`public PartitionData copyFor(StructLike partition)` 直接委托给上述私有构造器返回新实例。这是供 `TableScanUtil` 使用的入口，使得每次生成分组键只需一次 `copyFor` 调用。

3. **抽取 `toInternalValue` 静态方法**：将原 `set()` 中对 Utf8 转 String、ByteBuffer 转 byte[] 的 if-else 逻辑抽取为 `private static Object toInternalValue(Object value)`，返回转换后的内部表示。`set()` 简化为 `data[pos] = toInternalValue(value)`。

4. **新增 `copyData` 静态方法**：`private static Object[] copyData(Types.StructType type, StructLike partition)` 遍历分区类型的所有字段，按各字段的 `javaClass` 从 `partition` 读取值，经 `toInternalValue` 转换后填入新分配的 `Object[]` 数组并返回。这与拷贝构造器配合，一次性完成字段拷贝与类型规范化。

通过以上改动，分组键的生成从"逐字段 set 带类型判断"变为"批量 copyData 带统一转换"，减少了对象创建路径上的冗余判断。

### core/src/main/java/org/apache/iceberg/util/TableScanUtil.java

**修改目的**：使用 `PartitionData.copyFor` 替代原 `projectGroupingKey`，复用模板对象。

**工作逻辑**：

1. **创建模板**：在 `planTaskGroups` 方法中，于分组循环之前新增 `PartitionData groupingKeyTemplate = new PartitionData(groupingKeyType);`，该模板在整个方法生命周期内复用。

2. **替换分组键生成调用**：原 `projectGroupingKey(groupingKeyProjection, groupingKeyType, partition)` 调用改为 `groupingKeyTemplate.copyFor(groupingKeyProjection.wrap(partition))`。`groupingKeyProjection.wrap(partition)` 先将投影绑定到当前分区，`copyFor` 则基于模板的状态（partitionType、size 等）与新分区的投影值生成一个新的 `PartitionData` 作为 `StructLikeMap` 的键。

3. **删除 `projectGroupingKey` 方法**：原私有静态方法 `projectGroupingKey(StructProjection, Types.StructType, StructLike)` 被整体删除，其职责已由 `copyFor` + `copyData` 承接。

### spark/v3.5/spark-extensions/src/jmh/java/org/apache/iceberg/spark/TaskGroupPlanningBenchmark.java

**修改目的**：重构基准测试，使其能轻量准备数据并直接度量带分组键的任务规划路径。

**工作逻辑**：

1. **常量调整**：将原 `NUM_REAL_DATA_FILES_PER_PARTITION = 5` 与 `NUM_REPLICA_DATA_FILES_PER_PARTITION = 50_000` 两个常量合并为单一 `NUM_DATA_FILES_PER_PARTITION = 50_000`；删除 `NUM_ROWS_PER_DATA_FILE = 150`。即不再区分"真实文件"与"副本文件"，统一用 `FileGenerationUtil` 生成 5 万个数据文件。

2. **类型收窄**：将 `List<ScanTask> fileTasks` 改为 `List<FileScanTask> fileTasks`，对应基准方法中的 `ScanTaskGroup<ScanTask>` 改为 `ScanTaskGroup<FileScanTask>`，类型更精确。`loadFileTasks` 中 `table.newBatchScan()` 改为 `table.newScan()`。

3. **数据准备重写**：`initDataAndDeletes` 不再通过 Spark 生成随机数据写入再复制副本（删除了 `loadAddedDataFile`、`loadAddedDeleteFile`、`appendAsFile`、`randomDataDF` 等辅助方法及相关 import），而是对每个分区用 `FileGenerationUtil.generateDataFile(table, partition)` 与 `FileGenerationUtil.generatePositionDeleteFile(table, partition)` 直接生成文件，通过 `RowDelta` 一次性提交。这大幅简化了数据准备且不依赖 Spark DataFrame 写入路径。分区值用 `TestHelpers.Row.of(partitionOrdinal)` 构造。

4. **新增带分组的基准方法**：新增 `planTaskGroupsWithGrouping(Blackhole)`，与原 `planTaskGroups` 的区别在于调用 `planTaskGroups` 时多传一个 `Partitioning.groupingKeyType(table.schema(), table.specs().values())` 参数，即启用按分区分组。该方法度量本次优化直接影响的代码路径。原 `planTaskGroups`（不带分组）保留作为对照。

## 小结

本提交通过在 `PartitionData` 引入可复用模板与 `copyFor` 机制，将按分区合并扫描任务时分组键的生成从"逐字段 set 带冗余类型判断"优化为"批量 copyData 统一转换"，降低了高基数分区场景下的对象分配与方法调用开销。同时抽取 `toInternalValue` 消除 `set()` 与 `copyData()` 间的逻辑重复，并在 revapi 中登记序列化变更豁免。基准测试同步重构为基于 `FileGenerationUtil` 的轻量数据准备，并新增带分组键的基准方法以便度量优化效果。属于核心模块的性能优化，改动聚焦且配套测试完善。
