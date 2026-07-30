# 提交 2228：Flink: Dynamic Iceberg Sink: Add HashKeyGenerator / RowDataEvolver / TableUpdateOperator (#13277)

## 提交信息

- **序号**：2228 / 4088
- **哈希**：76972ef77b96f864712bdde2749a170f9494b001
- **短哈希**：76972ef77
- **日期**：2025-06-11 17:52:56 +0200
- **作者**：Maximilian Michels
- **提交说明**：Flink: Dynamic Iceberg Sink: Add HashKeyGenerator / RowDataEvolver / TableUpdateOperator (#13277)
- **PR/Issue**：#13277

## 总体目的

这个提交为 Iceberg Flink 动态 Sink 功能添加三个核心组件：`HashKeyGenerator`（哈希键生成器）、`RowDataEvolver`（行数据演化器）和 `DynamicTableUpdateOperator`（动态表更新算子）。动态 sink 允许一条 Flink 数据流路由到不同的 Iceberg 表，这需要解决三个关键问题：1）如何将数据按目标表分区路由到正确的 writer subtask（HashKeyGenerator）；2）当输入数据 schema 与目标表 schema 不完全匹配时如何转换数据（RowDataEvolver）；3）如何在数据流中自动检测并执行表 schema/分区规格更新（DynamicTableUpdateOperator）。本提交在 Flink v2.0 模块中实现了这三个组件，并将 equalityFieldIds 的类型从 List 改为 Set 以支持更灵活的集合操作。这些组件共同构成了动态 sink 数据处理管道的核心逻辑。

## 如何达成设计目的

- 引入 `HashKeyGenerator`：根据 DynamicRecord 的 DistributionMode（NONE/HASH/RANGE）、表 schema、分区规格和 equality fields，生成 Flink keyBy 所需的哈希键。使用 Caffeine 缓存 KeySelector 避免重复创建。支持将数据路由到正确的 writer subtask 子集。
- 引入 `RowDataEvolver`：当输入 schema 与目标表 schema 存在差异时（字段缺失、类型加宽、字段顺序不同），将 RowData 转换为目标 schema 兼容的格式。
- 引入 `DynamicTableUpdateOperator`：Flink MapFunction 算子，在数据流中调用 TableUpdater 检查并执行表更新（schema/分区演化），更新后若需数据转换则调用 RowDataEvolver。
- 引入 `DynamicSinkUtil`：工具类，提供 equality field ID 解析和安全绝对值计算。
- 将 equalityFieldIds 类型从 `List<Integer>` 改为 `Set<Integer>`，影响 IcebergSink 及相关类。

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/HashKeyGenerator.java` (新增, +379 lines)

**修改目的**：为动态 sink 的 keyBy 操作生成正确的哈希键。

**工作逻辑**：
- 使用 Caffeine 缓存按 SelectorKey（tableName+branch+schemaId+specId+inputSchema+inputSpec+equalityFields）缓存 KeySelector。
- `generateKey`：根据 DynamicRecord 的 DistributionMode 选择 KeySelector：
  - **NONE**：无 equality fields 时使用 tableKeySelector（按表名路由到 subtask 子集），有 equality fields 时使用 equalityFieldKeySelector。
  - **HASH**：无 equality fields 且有分区时使用 partitionKeySelector，无分区时 fallback 到 tableKeySelector。有 equality fields 时校验分区字段包含在 equality fields 中，然后使用 partitionKeySelector 或 equalityFieldKeySelector。
  - **RANGE**：Flink 不支持 RANGE 模式，有 identifier fields 时 fallback 到 equalityFieldKeySelector，否则使用 tableKeySelector。
- KeySelector 实现通过 `KeyGroupRangeAssignment.assignKeyToParallelism()` 将键映射到 subtask 子集（maxWriteParallelism 限制）。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/RowDataEvolver.java` (新增, +190 lines)

**修改目的**：将输入 RowData 转换为目标 schema 兼容的格式。

**工作逻辑**：
- `convert(sourceData, sourceSchema, targetSchema)`：入口方法，将 Iceberg Schema 转为 Flink LogicalType 后调用 `convertStruct`。
- `convertStruct`：按目标 schema 的字段顺序遍历，从源 RowData 中按字段名查找对应值，缺失字段填充 null。
- `convert`：按目标类型进行类型转换：
  - 相同类型直接返回
  - Float → Double（加宽）
  - Integer → Long（加宽）
  - Decimal 精度调整
  - Timestamp 精度调整
  - Array/Map/Row 递归转换

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicTableUpdateOperator.java` (新增, +78 lines)

**修改目的**：在数据流中自动执行表更新。

**工作逻辑**：
- 继承 `RichMapFunction<DynamicRecordInternal, DynamicRecordInternal>`。
- `open`：通过 CatalogLoader 加载 catalog，创建 TableUpdater（持有 TableMetadataCache）。
- `map`：调用 `updater.update(tableName, branch, schema, spec)` 获取更新后的 Schema、比较结果和 PartitionSpec。更新 DynamicRecordInternal 的 schema 和 spec。若比较结果为 DATA_CONVERSION_NEEDED，调用 `RowDataEvolver.convert` 转换 RowData。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicSinkUtil.java` (新增, +65 lines)

**修改目的**：提供动态 sink 工具方法。

**工作逻辑**：
- `getEqualityFieldIds(equalityFields, schema)`：将 equality field 名称列表转为 field ID 集合。若未提供则使用 schema 的 identifierFieldIds。
- `safeAbs(input)`：安全绝对值计算，处理 Integer.MIN_VALUE 溢出问题。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergSink.java` (修改, +5/-4 lines)

**修改目的**：将 equalityFieldIds 类型从 List 改为 Set。

**工作逻辑**：将 `equalityFieldIds` 字段、构造函数参数、`equalityFieldColumns` 字段从 `List<Integer>`/`List<String>` 改为 `Set<Integer>`/`Set<String>`，与动态 sink 的 Set 类型保持一致。

### 其他 sink 类的类型修改（各 +2/-2 lines 左右）

**修改目的**：跟随 equalityFieldIds 类型变更。

**工作逻辑**：以下文件将 equality field 相关参数/返回值从 List 改为 Set：
- `BaseDeltaTaskWriter.java`、`EqualityFieldKeySelector.java`、`FlinkSink.java`、`PartitionKeySelector.java`、`PartitionedDeltaWriter.java`、`RowDataTaskWriterFactory.java`、`SinkUtil.java`、`UnpartitionedDeltaWriter.java`

### DynamicRecord / DynamicRecordInternal / DynamicRecordInternalSerializer / WriteTarget (修改)

**修改目的**：将 equalityFields 类型从 List 改为 Set，支持 HashKeyGenerator 使用。

**工作逻辑**：相关字段和序列化逻辑调整为 Set 类型。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicWriter.java` (修改, +5/-5 lines)

**修改目的**：适配 equalityFields 类型变更。

### 测试文件（新增/修改, 共约 +750 lines）

- `TestHashKeyGenerator.java`（+354，新增）：全面测试 HashKeyGenerator 在各种 DistributionMode、equality fields、分区场景下的键生成行为。
- `TestRowDataEvolver.java`（+256，新增）：测试类型转换、字段缺失、字段顺序变更等场景。
- `TestDynamicTableUpdateOperator.java`（+112，新增）：测试表更新算子的端到端行为。
- 其他测试文件小幅修改以适配类型变更。

## 总结

该提交为 Iceberg Flink 动态 Sink 功能添加了三个核心组件：HashKeyGenerator 负责根据分布模式生成正确的路由键，RowDataEvolver 负责处理 schema 差异时的数据转换，DynamicTableUpdateOperator 负责在数据流中自动执行表更新。同时将 equalityFieldIds 从 List 改为 Set 以支持更灵活的集合操作。这三个组件与前序提交（DynamicRecord、TableUpdater、DynamicWriter/Committer）共同构成了完整的动态 sink 数据处理管道。测试覆盖全面，包括各组件的单元测试和端到端测试。
