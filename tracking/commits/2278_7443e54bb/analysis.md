# 提交 2278：Flink: Dynamic Iceberg Sink: Optimise RowData evolution (#13340)

## 提交信息

- **序号**：2278 / 4088
- **哈希**：7443e54bbba011001988e02335508b8a50ac7ec8
- **短哈希**：7443e54bb
- **日期**：2025-06-26 21:27:20 +0200
- **作者**：aiborodin
- **提交说明**：Flink: Dynamic Iceberg Sink: Optimise RowData evolution (#13340)
- **PR/Issue**：#13340

## 总体目的

本提交优化了动态 Iceberg Sink 中 RowData 演进（evolution）的性能。动态 Sink 支持向多个 schema 可能演进的 Iceberg 表写入数据，当输入数据 schema 与目标表 schema 不完全一致时（字段缺失、类型更宽、字段顺序不同），需要将输入 RowData 转换为目标 schema 兼容的格式。

此前，这一转换由 `RowDataEvolver.convert()` 完成，存在性能问题：每次需要转换时都重新执行 schema 比对和转换逻辑，且对每条记录都重复工作。此外，schema 比对结果（`CompareSchemasVisitor.Result`）没有按输入 schema 缓存，导致相同输入 schema 的记录反复比对。

本提交通过两项关键优化解决这些问题：1) 引入 `DataConverter` 接口，将转换逻辑预编译为可复用的转换器（包括 RowDataConverter/ArrayConverter/MapConverter），当 schema 相同时使用 identity 转换器避免开销；2) 在 `TableMetadataCache` 中为每个表按输入 schema 缓存预编译的 `DataConverter`（使用 LRU 缓存），使后续相同 schema 的记录直接复用已编译的转换器，无需重新比对。同时删除了原 `RowDataEvolver`（190 行）。

## 如何达成设计目的

- 新增 `DataConverter` 接口：定义 `convert(Object)` 方法，通过 `DataConverter.get(sourceType, targetType)` 工厂方法按目标类型根创建具体转换器。处理类型加宽（int→long、float→double、decimal 精度提升、date→timestamp）、嵌套结构（Row/Array/Map 递归转换）、字段缺失（nullable 字段补 null）和字段重排。
- 新增 `LRUCache` 类：通用 LRU 缓存，支持驱逐监听器，用于缓存每个表多个输入 schema 的比对/转换结果。
- 重构 `TableMetadataCache`：新增 `ResolvedSchemaInfo` 记录类（含 `resolvedTableSchema`、`compareResult`、预编译的 `recordConverter`），替代原 `SchemaInfo`。每个表的 `CacheItem` 维护一个 `LRUCache<Schema, ResolvedSchemaInfo>`，缓存不同输入 schema 的解析结果。`schema()` 方法返回 `ResolvedSchemaInfo`，调用方直接使用其中的 `recordConverter`。
- 重构 `DynamicRecordProcessor` 和 `DynamicTableUpdateOperator`：使用 `ResolvedSchemaInfo` 和 `DataConverter`，对每条记录调用 `recordConverter.convert(rowData)`（identity 转换器在 schema 相同时零开销），不再调用 `RowDataEvolver`。
- `DynamicIcebergSink` 新增 `inputSchemasPerTableCacheMaxSize` builder 选项（默认 10），控制每个表的输入 schema 缓存容量。

## 修改详情

### `flink/v2.0/.../sink/dynamic/DataConverter.java` (新增, +235/0 lines)

**修改目的**：提供预编译的数据转换器，替代每次调用都重新比对的 `RowDataEvolver`。

**工作逻辑**：`DataConverter` 是包级接口，核心方法 `convert(Object)`。工厂方法 `get(sourceType, targetType)` 按目标 `LogicalType` 的类型根分支：BOOLEAN/INTEGER/FLOAT/VARCHAR/DATE 等直接返回 identity；DOUBLE 将 Float 转 Double；BIGINT 将 Integer 转 Long；DECIMAL 按精度重新构造 DecimalData；TIMESTAMP_WITHOUT_TIME_ZONE 将 Integer（date）转 TimestampData；ROW/ARRAY/MAP 分别由内部类递归处理。`getNullable` 包装 null 处理。`RowDataConverter` 按 target schema 字段构建 `FieldGetter[]` 和 `DataConverter[]`：对每个目标字段，若源 schema 中不存在则 nullable 补 null 否则抛异常，存在则创建对应字段获取器和类型转换器；`convert` 生成 `GenericRowData`。`ArrayConverter`/`MapConverter` 递归转换元素/键值。

### `flink/v2.0/.../sink/dynamic/LRUCache.java` (新增, +60/0 lines)

**修改目的**：提供带驱逐监听的 LRU 缓存，用于按输入 schema 缓存转换器。

**工作逻辑**：基于 `LinkedHashMap` 的 LRU 实现，构造时指定最大容量和驱逐监听器（`BiConsumer<key, value>`）。当插入导致超出容量时，淘汰最久未访问的条目并调用监听器。`TableMetadataCache` 用它缓存每个表的输入 schema → ResolvedSchemaInfo 映射，驱逐时可通过监听器联动清理。

### `flink/v2.0/.../sink/dynamic/TableMetadataCache.java` (+66/-57 lines)

**修改目的**：缓存预编译的 DataConverter，避免重复 schema 比对。

**工作逻辑**：
- 新增 `ResolvedSchemaInfo` 内部类（替代 `SchemaInfo`），含 `resolvedTableSchema`（解析后的表 schema）、`compareResult`（比对结果）、`recordConverter`（预编译的 DataConverter）。静态常量 `NOT_FOUND` 使用 identity 转换器。
- `CacheItem` 中 `inputSchemas` 由简单 Map 改为 `LRUCache<Schema, ResolvedSchemaInfo>`，容量由 `inputSchemaCacheMaximumSize` 控制。
- `schema(identifier, input)` 方法：先查 `inputSchemas` LRU 缓存，命中则直接返回缓存的 `ResolvedSchemaInfo`（含预编译转换器）；未命中则执行 `CompareSchemasVisitor` 比对，根据结果（SAME→identity 转换器；DATA_CONVERSION_NEEDED→`DataConverter.get` 预编译）创建 `ResolvedSchemaInfo` 并存入 LRU 缓存。
- 构造函数新增 `inputSchemaCacheMaximumSize` 参数。

### `flink/v2.0/.../sink/dynamic/DynamicRecordProcessor.java` (+25/-16 lines)

**修改目的**：使用缓存的 DataConverter 转换记录，替代 RowDataEvolver。

**工作逻辑**：构造函数新增 `inputSchemasPerTableCacheMaximumSize` 参数并传给 `TableMetadataCache`。`collect` 方法中 `foundSchema` 类型从 `Tuple2<Schema, Result>` 改为 `ResolvedSchemaInfo`，通过 `foundSchema.compareResult()`、`foundSchema.resolvedTableSchema()`、`foundSchema.recordConverter()` 访问。`emit` 方法签名中 `CompareSchemasVisitor.Result result` 参数改为 `DataConverter recordConverter`，转换逻辑由原先按 result 分支调用 `RowDataEvolver.convert` 改为统一调用 `recordConverter.convert(data.rowData())`（SAME 时为 identity，零开销）。

### `flink/v2.0/.../sink/dynamic/DynamicTableUpdateOperator.java` (+13/-10 lines)

**修改目的**：更新算子使用 ResolvedSchemaInfo 和 DataConverter。

**工作逻辑**：构造函数新增 `inputSchemasPerTableCacheMaximumSize`。`map` 方法返回类型从 `Tuple3<Schema, Result, PartitionSpec>` 改为 `Tuple2<ResolvedSchemaInfo, PartitionSpec>`。原先仅在 `DATA_CONVERSION_NEEDED` 时调用 `RowDataEvolver.convert`，现在统一调用 `newData.f0.recordConverter().convert(data.rowData())`（identity 转换器在无需转换时零开销）。

### `flink/v2.0/.../sink/dynamic/DynamicIcebergSink.java` (+19/-6 lines)

**修改目的**：新增输入 schema 缓存容量配置项。

**工作逻辑**：Builder 新增 `inputSchemasPerTableCacheMaximumSize` 字段（默认 10）和 `inputSchemasPerTableCacheMaxSize(int)` 方法。在构建 `DynamicRecordProcessor` 和 `DynamicTableUpdateOperator` 时传入该参数。

### `flink/v2.0/.../sink/dynamic/RowDataEvolver.java` (删除, -190 lines)

**修改目的**：移除被 DataConverter 替代的旧转换器。

### 测试文件

- `TestLRUCache.java`（新增, +90）：LRU 缓存单元测试。
- `TestRowDataConverter.java`（由 `TestRowDataEvolver` 重命名, +20/-18）：适配 DataConverter API。
- `TestTableMetadataCache.java`、`TestTableUpdater.java`、`TestDynamicTableUpdateOperator.java`：适配新构造参数和 ResolvedSchemaInfo。

## 总结

本提交通过预编译 DataConverter 和 LRU 缓存 schema 比对结果，显著优化了动态 Iceberg Sink 的 RowData 演进性能。核心思路是将"每次转换都重新比对 schema"优化为"按输入 schema 缓存预编译转换器，转换时直接应用"。新增的 DataConverter 支持类型加宽、字段缺失补 null 和字段重排，覆盖了原 RowDataEvolver 的全部能力。identity 转换器的引入使 schema 相同时零开销。这是一个以空间换时间的性能优化，对高频写入动态 Sink 场景有明显收益。
