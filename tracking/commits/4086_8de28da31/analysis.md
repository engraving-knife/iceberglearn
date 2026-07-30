# 提交 4086：Flink: SQL: Add variant avro dynamic record generator

## 提交信息

- **序号**：4086 / 4088
- **哈希**：8de28da314ec2f13251dac8b7dc0c7de1f899d2e
- **短哈希**：8de28da31
- **日期**：2026-07-24 08:20:07 +0200
- **作者**：Swapna Marru
- **提交说明**：Flink: SQL: Add variant avro dynamic record generator (#16450)
- **PR/Issue**：#16450

## 总体目的

Iceberg 的 Flink dynamic sink 机制（`DynamicTableRecordGenerator`）允许用户在 SQL 写入时根据每条记录动态决定目标表和 schema，适用于多租户/多表路由场景。此前该机制需要用户自行实现 `DynamicTableRecordGenerator` 来把输入行转换成 `DynamicRecord`。

随着 Iceberg 引入 Variant 类型（一种通用的半结构化数据类型，类似 JSON/Variant），以及 Flink 也支持了 Variant 类型，出现了一个典型用例：上游以 Variant 形式携带业务数据，同时附带 Avro schema 字符串和 schema id 描述这批数据的结构。下游希望根据 Avro schema 动态地把 Variant 数据路由并写入对应的 Iceberg 表（按 schema 路由到不同表，且同一表可能随时间演化出多个 schema 版本）。

本提交新增 `VariantAvroDynamicTableRecordGenerator`，正是为这个场景设计的内置 record generator。它从输入 `RowData` 中读取：
- `data`（Variant 类型）：业务数据；
- `avro_schema`（STRING）：描述数据结构的 Avro schema JSON；
- `avro_schema_id`（STRING）：schema 版本标识；
- `catalog-database`、`catalog-table`：目标表位置（可在行中或表选项中提供）；
- 可选的 `branch`、`write-parallelism`、`partition_columns`、分布模式等。

它把 Avro schema 转换为 Iceberg schema，用 `VariantRowDataWrapper` 把 Variant 包装成符合该 schema 的 `RowData`，并附带分区规格、分支、分布模式等信息输出 `DynamicRecord`，供下游 dynamic sink 写入。

为支持高效处理大量 schema/表，generator 内部维护了 LRU 缓存：按 `TableIdentifier` 缓存每个表的 schema 和分区规格集合，再按 `avroSchemaId` 缓存具体 schema 解析结果（含对应的 `VariantRowDataWrapper`），按 `partitionColumns` 缓存 `PartitionSpec`，避免每条记录都重复解析 Avro schema。

## 如何达成设计目的

整体设计分为三层：

1. **增强基类 `DynamicTableRecordGenerator`**：在原有仅持 `RowType` 的基础上，新增接受 `Map<String,String> writeProperties` 和 `Configuration flinkConfiguration` 的构造函数，统一在 `open(OpenContext)` 时构建 `FlinkDynamicSinkConf`（用于读取缓存大小等动态 sink 配置）。基类还提供 `fieldNameToPosition` 映射、`validateRequiredColumnAndType` 列校验工具，供子类复用。

2. **新增 `VariantAvroDynamicTableRecordGenerator`**：实现 `generate(RowData, Collector<DynamicRecord>)`，完成 Variant→RowData 转换与目标表/schema 路由。内部用嵌套 LRU 缓存管理 schema 和分区规格。

3. **新增 `VariantRowDataWrapper`**：实现 `RowData` 接口，把一个 Variant 值按给定 `RowType` 暴露为各类型字段的访问器（`getInt`、`getString`、`getDecimal`、`getTimestamp`、`getArray`、`getMap`、`getRow`、`getVariant` 等）。它依赖 Flink 的 `BinaryVariantAccessorUtils` 读取 Variant 内部结构。

4. **新增 `BinaryVariantAccessorUtils`**：临时工具类（位于 `org.apache.flink.types.variant` 包），提供 `arraySize(Variant)` 和 `fieldNames(Variant)` 等访问 BinaryVariant 内部结构的静态方法。这是 Flink 上游 PR #27600 即将合入的 API，在 Iceberg 用的 Flink 版本尚未包含，故本地复制一份作为过渡，等 Flink 升级后移除。

5. **`IcebergTableSink` 适配**：在反射构造 `DynamicTableRecordGenerator` 时，新增一个接受 `(RowType, Map, Configuration)` 的构造函数候选，并把 `writeProps` 和从 `readableConfig` 转换来的 `Configuration` 传入，让新的 generator 能拿到表选项和 Flink 配置。

6. **可见性调整**：`FlinkCreateTableOptions` 和 `FlinkDynamicSinkConf` 从包级改为 `public`（加 `@Internal` 注解），供 generator 引用其配置常量。

## 修改详情

### `LICENSE` (+1/-0 lines)

**修改目的**：声明从 Apache Flink 借用的代码。

**工作逻辑**：在 Apache Flink 借用代码清单中新增一行：
```
* Binary variant accessor utility methods in BinaryVariantAccessorUtils.java
```
因为 `BinaryVariantAccessorUtils` 是从 Flink 上游 PR 复制过来的代码，需按 LICENSE 规范声明来源。

### `flink/v2.1/flink/src/main/java/org/apache/flink/types/variant/BinaryVariantAccessorUtils.java` (+86/-0 lines, new file)

**修改目的**：提供访问 Flink `BinaryVariant` 内部结构的过渡工具方法，直到 Flink 上游 #27600 合入并升级。

**工作逻辑**：继承 Flink 的 `BinaryVariantUtil`，新增两个静态方法：
- `arraySize(Variant)`：校验是 `BinaryVariant` 且类型为 `ARRAY`，调用 `handleArray` 解析数组元素个数；
- `fieldNames(Variant)`：校验是 `BinaryVariant` 且类型为 `OBJECT`，调用 `handleObject` 遍历字段 ID，从 metadata 取出字段名列表。

类注释明确说明这是临时方案，待 Flink 升级后移除。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/FlinkCreateTableOptions.java` (+2/-2 lines)

**修改目的**：开放 `FlinkCreateTableOptions` 供 generator 引用其配置常量（如 `CATALOG_DATABASE`、`CATALOG_TABLE`）。

**工作逻辑**：类从 `class`（包级）改为 `@Internal public class`，新增 `org.apache.flink.annotation.Internal` 导入。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/IcebergTableSink.java` (+8/-2 lines)

**修改目的**：在反射构造 generator 时支持新的三参构造函数，并传入 writeProps 与 Configuration。

**工作逻辑**：
```java
DynConstructors.builder(DynamicTableRecordGenerator.class)
    .loader(IcebergTableSink.class.getClassLoader())
    .impl(generatorImpl, RowType.class)
    .impl(generatorImpl, RowType.class, Map.class, Configuration.class)  // 新增候选
    .buildChecked();
return ctor.newInstance(rowType, writeProps, fromReadableConfig());
```
`DynConstructors` 会按候选列表匹配第一个可用的构造函数；新 generator 提供三参构造，旧 generator 仍可用单参构造（`ctor.newInstance` 会根据实际匹配的构造函数忽略多余参数——这里实际依赖 `DynConstructors` 的匹配逻辑）。新增私有方法 `fromReadableConfig()` 把 `ReadableConfig` 转为 `Configuration`（若本身就是 `Configuration` 直接强转，否则 `Configuration.fromMap(readableConfig.toMap())`）。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/data/VariantRowDataWrapper.java` (+322/-0 lines, new file)

**修改目的**：把一个 Variant 值包装成符合给定 `RowType` 的 `RowData`，让下游 sink 能像读普通行一样读 Variant 内的字段。

**工作逻辑**：实现 `RowData` 接口，核心是 `field(int pos)` 根据 `rowType` 的字段名从 Variant 中取出对应字段值（一个子 Variant），然后各 `getXxx(pos)` 方法把子 Variant 转成对应的 Flink 类型：
- `getInt`/`getLong`：从 Variant 取数值；
- `getString`：`StringData.fromString(value.getString())`；
- `getDecimal`：`DecimalData.fromBigDecimal(value.getDecimal(), precision, scale)`；
- `getTimestamp`：根据 precision 区分微秒/纳秒精度，构造 `TimestampData`；
- `getBinary`：`value.getBytes()`；
- `getArray`：用 `BinaryVariantAccessorUtils.arraySize` 取数组大小，逐元素递归转换；
- `getMap`：把 Variant object 的字段名/值转成 `GenericMapData`；
- `getRow`：递归用子 RowType 包装子 Variant；
- `getVariant`：直接返回子 Variant；
- `isNullAt`：通过 `isNull(field(pos))` 判定。

`wrap(Variant)` 方法用于重置当前包装的 Variant，使 wrapper 可复用。`MICROSECOND_PRECISION=6`、`NANOSECOND_PRECISION=9` 常量用于时间精度判定。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicTableRecordGenerator.java` (+57/-3 lines)

**修改目的**：增强基类以支持 writeProperties、Flink 配置和列校验。

**工作逻辑**：
- 新增字段 `flinkConfiguration`、`writeProperties`、`fieldNameToPosition`、`flinkDynamicSinkConf`；
- 新增三参构造函数 `(RowType, Map<String,String> writeProperties, Configuration flinkConfiguration)`，原单参构造委托给三参（传空 map 和空 Configuration）保持向后兼容；
- `open(OpenContext)` 中构建 `FlinkDynamicSinkConf(writeProperties, flinkConfiguration)`；
- 新增 `validateRequiredColumnAndType(String columnName, LogicalType expectedType)`：校验 rowType 中存在该列且类型匹配，否则抛 `IllegalArgumentException`；
- 新增 `fieldNameToPosition()` 访问器和私有 `fieldNameToPositionMapping()` 构建字段名→位置映射。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/FlinkDynamicSinkConf.java` (+2/-1 lines)

**修改目的**：开放配置类供 generator 使用。

**工作逻辑**：类从包级改为 `@Internal public class`，新增 `Internal` 导入。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/VariantAvroDynamicTableRecordGenerator.java` (+223/-0 lines, new file)

**修改目的**：核心新增类，实现 Variant+Avro schema 到 Iceberg 动态记录的转换。

**工作逻辑**：

- **构造**：调用超类三参构造，并校验必需列：
  - `data` 必须是 `VariantType`；
  - `avro_schema`、`avro_schema_id` 必须是 `STRING`；
  - `catalog-database`、`catalog-table` 可在行中或在 writeProperties 中（`validateColumnWithConfigFallback`）。
- **`open`**：调用 `super.open`，读取 `inputSchemasPerTableCacheMaxSize` 和 `cacheMaxSize` 配置，初始化 `tableCache`（LRU）。
- **`generate`**：
  1. 从输入行取 `data`（Variant）、`avro_schema`、`avro_schema_id`，任一为 null 则跳过；
  2. `extractTableIdentifier` 从行或 writeProps 取 `catalog-database`/`catalog-table` 组成 `TableIdentifier`；
  3. 取 `branch`、分布模式、`write-parallelism`；
  4. 从 `tableCache` 取或创建该表的 `SchemaAndPartitionSpecCacheItem`；
  5. 用 `avroSchemaId`+`avroSchema` 从缓存取或解析 `SchemaCacheItem`（含 Iceberg schema 和 `VariantRowDataWrapper`）；
  6. 取 `partition_columns`，从缓存取或构建 `PartitionSpec`；
  7. 用 `variantRowDataWrapper().wrap(variantData)` 包装 Variant，输出 `DynamicRecord`。
- **缓存嵌套类**：
  - `SchemaAndPartitionSpecCacheItem`：每个表一个，含 `schemaCache`（按 avroSchemaId 缓存 `SchemaCacheItem`）和 `partitionSpecCache`（按 partitionColumns 字符串缓存 `PartitionSpec`），都是 LRU；
  - `SchemaCacheItem`：record，含 `tableSchema`（Iceberg Schema）和 `variantRowDataWrapper`（按该 schema 的 RowType 构造的 wrapper）。
- **辅助方法**：`columnValueAsString`、`columnValueWithConfigFallback`、`validateColumnWithConfigFallback`、`extractTableIdentifier`。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/DataGenerator.java` (+3/-0 lines)

**修改目的**：在测试数据生成器接口新增 `generateFlinkVariantData()` 方法。

**工作逻辑**：接口新增 `Variant generateFlinkVariantData();` 方法，新增 `Variant` 导入。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/DataGenerators.java` (+360/-3 lines)

**修改目的**：为各测试数据生成器实现 `generateFlinkVariantData()`，构造覆盖所有支持类型的 Variant 测试数据。

**工作逻辑**：在两个内部 generator 实现（StructOfArrayTestType、StructOfPrimitiveTestType）中实现 `generateFlinkVariantData()`：
- 使用 `VariantBuilder` 构造 object variant，加入 `row_id`、`boolean_field`、`int_field`、`long_field`、`float_field`、`double_field`、`string_field`、`date_field`、`time_field`、`ts_with_zone_field`、`ts_without_zone_field`、`uuid_field`、`binary_field`、`decimal_field`、`fixed_field`、`ts_ns_with_zone_field`、`ts_ns_without_zone_field` 等字段，覆盖所有 Iceberg 支持的类型；
- 注释说明 Flink Variant 原生支持 TIMESTAMP/TIMESTAMP_LTZ（微秒精度），纳秒精度字段需序列化为 BIGINT；
- StructOfPrimitiveTestType 实现更简单，含 `row_id` 和嵌套 `struct_of_primitive`。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/TestIcebergConnector.java` (+73/-0 lines)

**修改目的**：端到端集成测试验证 Variant Avro dynamic sink 工作。

**工作逻辑**：新增 `testVariantAvroDynamicIcebergSink`：
- 配置 dynamic sink 表选项（`use-dynamic-iceberg-sink=true`、`dynamic-record-generator-impl=VariantAvroDynamicTableRecordGenerator`）；
- 建源表 `(data VARIANT, catalog-database STRING, catalog-table STRING, avro_schema STRING, avro_schema_id STRING, branch STRING, write-parallelism INT)`；
- 用 `PARSE_JSON` 构造 Variant 数据并 INSERT，携带 Avro schema（id+name 两字段）和目标库表名；
- 验证目标 Iceberg 表被自动创建、表属性 `key1=val1` 被传递、数据正确写入（id/name 值匹配）。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/data/TestVariantRowDataWrapper.java` (+292/-0 lines, new file)

**修改目的**：单元测试 `VariantRowDataWrapper` 对各类型的转换正确性。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestVariantAvroDynamicTableRecordGenerator.java` (+502/-0 lines, new file)

**修改目的**：单元测试 `VariantAvroDynamicTableRecordGenerator` 的列校验、schema 缓存、分区规格缓存、表标识符提取等核心逻辑。

## 总结

这是一个较大的功能新增提交，为 Flink dynamic sink 提供了内置的 `VariantAvroDynamicTableRecordGenerator`，支持"上游 Variant 数据 + Avro schema 描述"的动态路由写入场景。核心能力包括：Avro schema 到 Iceberg schema 的转换、Variant 到 RowData 的包装（`VariantRowDataWrapper`）、按表/schema/分区规格的多级 LRU 缓存以避免重复解析、以及通过行字段或表选项灵活配置目标库表与写入参数。配套的单元测试和端到端集成测试覆盖了类型转换、缓存行为和完整写入流程。同时为过渡期从 Flink 借用的 `BinaryVariantAccessorUtils` 在 LICENSE 中做了声明。这个特性显著降低了用户在 Variant 数据动态路由写入场景下的开发成本。
