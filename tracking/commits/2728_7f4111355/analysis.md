# 提交 2728：Parquet,Docs: Add new table property to configure bloom-filter ndv

## 提交信息

- **序号**：2728 / 4088
- **哈希**：7f411135562e682916f8f411bfc7455e3f79956d
- **短哈希**：7f4111355
- **日期**：2025-10-09 16:20:21 -0700
- **作者**：André Rosa
- **提交说明**：Parquet,Docs: Add new table property to configure bloom-filter ndv
- **PR/Issue**：#14244

## 总体目的

Parquet 的 Bloom Filter（布隆过滤器）是一种空间效率高的数据结构，用于快速判断某个值是否可能存在于数据块中，从而加速查询过滤。Bloom Filter 的大小和精度取决于两个关键参数：假阳性率（FPP）和预期不同值的数量（NDV，Number of Distinct Values）。

在此之前，Iceberg 已支持通过表属性配置 Bloom Filter 的 FPP（假阳性率），属性前缀为 `write.parquet.bloom-filter-fpp.column.`。但缺少配置 NDV 的能力。NDV 是决定 Bloom Filter 大小的重要参数——Parquet 使用 FPP 和 NDV 来计算所需的 bitset 大小。如果不提供 NDV，Parquet 可能使用默认的估算值，导致 Bloom Filter 大小不合适，影响查询性能或存储效率。

此提交添加了新的表属性 `write.parquet.bloom-filter-ndv.column.<col>`，允许用户为每列配置预期的不同值数量，使 Parquet 能够更精确地计算 Bloom Filter 的大小。

## 如何达成设计目的

主要设计思路：
1. 在 `TableProperties` 中定义新的属性前缀常量
2. 在 `Parquet.java` 中扩展 `WriteConfig` 类，添加 `columnBloomFilterNdv` 映射
3. 在 `setColumnBloomFilterConfigs` 方法中，将 NDV 值传递给 Parquet 的 `withBloomFilterNDV` 回调
4. 在配置文档中添加新属性说明
5. 在测试中验证 NDV 配置正确传递到 Parquet writer

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableProperties.java` (+3/-0 lines)

**修改目的**：定义 Bloom Filter NDV 列属性的常量。

**工作逻辑**：新增 `PARQUET_BLOOM_FILTER_COLUMN_NDV_PREFIX = "write.parquet.bloom-filter-ndv.column."` 常量，用于配置每列的预期不同值数量。

### `parquet/src/main/java/org/apache/iceberg/parquet/Parquet.java` (+26/-2 lines)

**修改目的**：在 Parquet writer 配置中支持 Bloom Filter NDV 参数。

**工作逻辑**：
1. **`setColumnBloomFilterConfigs` 方法**：新增 `BiConsumer<String, Long> withBloomFilterNDV` 参数。在遍历启用了 Bloom Filter 的列时，检查是否有 NDV 配置，如果有则通过回调设置。
2. **调用处更新**：`writeConfigFromKey` 和 `writeBuilderFromKey` 方法中调用 `setColumnBloomFilterConfigs` 时，新增 `propsBuilder::withBloomFilterNDV` 和 `parquetWriteBuilder::withBloomFilterNDV` 回调。
3. **`WriteConfig` 类**：新增 `columnBloomFilterNdv` 字段和对应构造参数。从配置属性中通过 `PropertyUtil.propertiesWithPrefix(config, PARQUET_BLOOM_FILTER_COLUMN_NDV_PREFIX)` 提取 NDV 配置。新增 `columnBloomFilterNdv()` getter 方法。在默认配置创建处添加 `ImmutableMap.of()` 作为空默认值。

### `docs/docs/configuration.md` (+52/-51 lines)

**修改目的**：在配置文档中添加 NDV 属性说明，并对表格格式进行了重新排版。

**工作逻辑**：在 Write properties 表格中，在 `write.parquet.bloom-filter-fpp.column.col1` 行之后添加 `write.parquet.bloom-filter-ndv.column.col1` 行，说明为 "The expected number of distinct values for a bloom filter applied to 'col1' (must > 0)"，默认值为 "(not set)"。同时对整个表格的列宽进行了调整以适应新内容。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/data/TestSparkParquetWriter.java` (+28/-0 lines)

**修改目的**：验证 NDV 配置正确传递到 Parquet writer。

**工作逻辑**：新增 `testNdv` 测试方法：
1. 设置 `PARQUET_BLOOM_FILTER_COLUMN_ENABLED_PREFIX + col` 为 "true" 启用 Bloom Filter
2. 设置 `PARQUET_BLOOM_FILTER_COLUMN_NDV_PREFIX + col` 为 "1000" 指定 NDV
3. 创建 Parquet writer 写入数据
4. 通过反射访问 `ParquetWriter` 的私有 `props` 字段（`ParquetProperties`）
5. 获取对应列的 `ColumnDescriptor` 并调用 `props.getBloomFilterNDV(descriptor)`
6. 验证返回的 `OptionalLong` 包含值且等于 1000

测试通过反射验证了配置属性确实被正确传递到了 Parquet 的 `ParquetProperties` 中。

## 总结

此提交为 Iceberg 的 Parquet Bloom Filter 配置添加了 NDV（预期不同值数量）参数支持。用户现在可以通过 `write.parquet.bloom-filter-ndv.column.<col>` 属性为每列配置预期的不同值数量，使 Parquet 能够更精确地计算 Bloom Filter 的 bitset 大小，从而优化查询性能和存储效率。这是对现有 Bloom Filter FPP 配置的补充，两者共同决定了 Bloom Filter 的质量和大小。
