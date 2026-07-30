# 提交 2231：Flink: Backport dynamic Iceberg Sink: Add HashKeyGenerator / RowDataEvolver / TableUpdateOperator to Flink 1.19 / 1.20

## 提交信息

- **序号**：2231 / 4088
- **哈希**：e141ff73b8572522cefaeba182d4b802ec96d1a5
- **短哈希**：e141ff73b
- **日期**：2025-06-12 12:55:55 +0200
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport dynamic Iceberg Sink: Add HashKeyGenerator / RowDataEvolver / TableUpdateOperator to Flink 1.19 / 1.20
- **PR/Issue**：#13303（backports #13277）

## 总体目的

本提交是动态 Iceberg Sink 功能向 Flink 1.19 和 1.20 版本的反向移植（backport）。动态 Iceberg Sink 允许在运行时动态地将数据写入不同的 Iceberg 表，而无需预先固定目标表的 schema 和分区规格。该提交引入了三个核心组件：HashKeyGenerator（哈希键生成器）、RowDataEvolver（行数据演化器）和 DynamicTableUpdateOperator（动态表更新算子）。这些组件共同支撑了动态 Sink 在多表写入场景下的数据路由、schema 适配和表元数据更新能力。由于 Flink 不同版本的 API 存在差异，需要分别维护 v1.19 和 v1.20 两套代码。

## 如何达成设计目的

- 新增 `HashKeyGenerator`：根据 DynamicRecord 和表元数据，为 Flink 的 keyBy 操作生成合适的哈希键，内部维护一组 Flink KeySelector 来实现不同的 Iceberg DistributionMode（如 hash、range、none），并使用 Caffeine 缓存避免重复创建 KeySelector。
- 新增 `RowDataEvolver`：负责将输入 RowData 转换为目标 schema 兼容的格式，处理字段缺失（填充 null）、类型扩展（如 int 转 long）、字段顺序不一致（重排列）三种场景。
- 新增 `DynamicTableUpdateOperator`：一个可选的 Flink RichMapFunction 算子，以非并发方式执行表更新（如 schema 更新），记录必须先按表名进行 keyBy 路由以确保非并发更新，更新后的记录被转发到下游。
- 新增 `DynamicSinkUtil`：提供获取 equality field ID 和安全取绝对值等工具方法。
- 修改多个现有 sink 类（如 `RowDataTaskWriterFactory`、`FlinkSink`、`IcebergSink` 等），将 equalityFieldIds 的类型从 `List<Integer>` 改为 `Collection<Integer>`/`Set<Integer>`，以支持动态场景下更灵活的相等字段集合操作。
- 同时新增对应的测试类：`TestHashKeyGenerator`、`TestRowDataEvolver`、`TestDynamicTableUpdateOperator` 等。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/HashKeyGenerator.java` (新增, +379 lines)

**修改目的**：为动态 Sink 提供基于表元数据和 DistributionMode 的哈希键生成能力。

**工作逻辑**：HashKeyGenerator 维护一个 Caffeine 缓存，以 SelectorKey（包含表标识符、分支、分布模式、写入并行度、schema、spec 等）为键缓存 KeySelector。当 generateKey 被调用时，根据 DynamicRecord 携带的表信息和用户配置的 DistributionMode 选择合适的 KeySelector（PartitionKeySelector 或 EqualityFieldKeySelector），然后计算 Flink 的 key group 分配。缓存确保表元数据或用户元数据变化时创建新的 KeySelector，保证哈希的确定性。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/RowDataEvolver.java` (新增, +190 lines)

**修改目的**：处理输入数据 schema 与目标表 schema 不一致的情况。

**工作逻辑**：`convert` 方法接收源数据、源 schema 和目标 schema，遍历目标 schema 的字段，对于每个字段：如果在源 schema 中存在同名字段则转换并拷贝数据（支持类型扩展如 int→long），如果不存在则填充 null（要求字段可选）。支持所有 Flink 逻辑类型（Decimal、Timestamp、Array、Map、Row 等）的递归转换。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicTableUpdateOperator.java` (新增, +78 lines)

**修改目的**：在数据流中非并发地检测并应用表元数据更新。

**工作逻辑**：继承 RichMapFunction，在 open 时加载 Catalog 并初始化 TableUpdater（依赖 TableMetadataCache）。map 方法对每条 DynamicRecordInternal 调用 updater.update，比较记录中的 schema/spec 与当前表元数据，若检测到变化则更新记录中的 schema/spec 信息并转发。通过 keyBy 按表名路由确保同一表的更新不会并发执行。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicSinkUtil.java` (新增, +65 lines)

**修改目的**：提供动态 Sink 通用工具方法。

**工作逻辑**：`getEqualityFieldIds` 根据用户指定的 equality 字段名集合和 schema 获取字段 ID 集合，若未指定则使用 schema 的标识符字段。`safeAbs` 安全取绝对值，处理 Integer.MIN_VALUE 溢出问题。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/RowDataTaskWriterFactory.java` (修改, +9/-8 lines)

**修改目的**：将 equalityFieldIds 从 List 改为 Set/Collection 以适配动态 Sink 场景。

**工作逻辑**：字段类型从 `List<Integer>` 改为 `Set<Integer>`（内部存储）和 `Collection<Integer>`（构造参数），构造时将传入的 Collection 转为 HashSet。同时将 `ArrayUtil.toIntArray` 调用改为 `ArrayUtil.toPrimitive`，因为 Set 不再支持 toIntArray 的 List 专用重载。

### 其他 sink 类修改 (各小量修改)

`BaseDeltaTaskWriter`、`EqualityFieldKeySelector`、`FlinkSink`、`IcebergSink`、`PartitionKeySelector`、`PartitionedDeltaWriter`、`SinkUtil`、`UnpartitionedDeltaWriter`、`DynamicRecord`、`DynamicRecordInternal`、`DynamicRecordInternalSerializer`、`DynamicWriter`、`WriteTarget` 等文件均做了类似的 List→Set/Collection 类型适配和少量接口调整。

### 测试文件 (新增/修改)

新增 `TestHashKeyGenerator`（354 行）、`TestRowDataEvolver`（256 行）、`TestDynamicTableUpdateOperator`（113 行），并修改了多个已有动态 Sink 测试类以适配接口变更。所有修改在 v1.19 和 v1.20 两个 Flink 版本目录下同步进行。

## 总结

这是动态 Iceberg Sink 功能的重要 backport 提交，向 Flink 1.19/1.20 引入了 HashKeyGenerator、RowDataEvolver、DynamicTableUpdateOperator 三个核心组件，使动态 Sink 能在运行时路由数据到不同表、适配 schema 差异并处理表元数据更新。同时调整了现有 sink 类的 equalityFieldIds 类型以适配新场景。这是 Iceberg Flink 集成中支持多表动态写入的关键基础设施。
