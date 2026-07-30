# 提交 2114：Add table property to disable/enable parquet column statistics

## 提交信息

- **序号**：2114 / 4088
- **哈希**：d04be9b6df0e4bc59bceb6a80c2b828d5318d270
- **短哈希**：d04be9b6d
- **日期**：2025-05-12 16:06:22 -0700
- **作者**：huaxiangsun
- **提交说明**：Add table property to disable/enable parquet column statistics #12770 (#12771)
- **PR/Issue**：#12771 (关联 Issue #12770)

## 总体目的

本提交为 Iceberg 添加了表属性来按列控制 Parquet 列统计信息的启用/禁用。在 Parquet 文件中，列统计信息（如 min/max 值、null 计数等）会占用额外的存储空间，对于某些不需要统计信息过滤的列（例如大型字符串列、嵌套列等），禁用统计信息可以显著减少文件大小和写入开销。此前 Iceberg 已经支持按列配置 Bloom Filter，但缺少按列控制统计信息的能力。本提交填补了这一功能空白，让用户可以通过 `write.parquet.stats-enabled.column.<column_name>` 属性灵活控制每列的统计信息收集行为。

## 如何达成设计目的

1. 在 `TableProperties` 中新增 `PARQUET_COLUMN_STATS_ENABLED_PREFIX` 常量，定义表属性前缀 `write.parquet.stats-enabled.column.`
2. 在 `Parquet.java` 的 `Context` 类中添加 `columnStatsEnabled` 映射，从表配置中提取按列的统计信息开关
3. 新增 `setColumnStatsConfig` 方法，将列名映射到 Parquet 列路径并设置统计信息开关
4. 重构了 `setBloomFilterConfig` 方法，将原来基于 fieldId 到 Parquet 路径的映射改为基于列名到 Parquet 路径的映射（`colNameToParquetPathMap`），使两个配置方法共享同一映射逻辑
5. 在 `SchemaUpdate` 中将该新前缀加入需要随 schema 变更同步更新的列属性集合，确保列重命名或删除时统计信息配置能正确更新
6. 添加了单元测试验证功能正确性和 schema 更新时的属性同步

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableProperties.java` (修改, +3/-0 lines)

**修改目的**：定义新的表属性前缀常量。

**工作逻辑**：新增 `PARQUET_COLUMN_STATS_ENABLED_PREFIX = "write.parquet.stats-enabled.column."` 常量，与已有的 Bloom Filter 前缀保持一致的命名风格。

### `core/src/main/java/org/apache/iceberg/SchemaUpdate.java` (修改, +2/-1 lines)

**修改目的**：确保列统计配置在 schema 变更（重命名、删除列）时能正确同步。

**工作逻辑**：在 `columnProperties` 集合中添加了 `PARQUET_COLUMN_STATS_ENABLED_PREFIX`，使得当列被重命名或删除时，`PropertyUtil.applySchemaChanges` 会自动更新对应的统计信息配置键名或移除配置。

### `parquet/src/main/java/org/apache/iceberg/parquet/Parquet.java` (修改, +56/-21 lines)

**修改目的**：核心实现，支持按列控制 Parquet 统计信息写入。

**工作逻辑**：
- **重构 setBloomFilterConfig**：原方法接收 `MessageType parquetSchema` 并内部构建 `fieldIdToParquetPath` 映射，现改为接收 `Map<String, String> colNameToParquetPathMap`，将映射构建逻辑提取到 build 方法中统一处理
- **新增 colNameToParquetPathMap 构建**：在 build 方法中，从 Parquet schema 的列中过滤出有 ID 且在 Iceberg schema 中有对应列名的列，构建列名到 Parquet 路径的映射
- **新增 setColumnStatsConfig 方法**：遍历 `columnStatsEnabled` 配置，将列名映射为 Parquet 路径后，通过 `withColumnStatsEnabled` 回调设置统计信息开关
- **Context 类变更**：新增 `columnStatsEnabled` 字段、构造参数、访问方法，并在 `fromProperties` 中从配置提取按列统计信息配置
- 在两条写入路径（createWriterFunc 和 ParquetWriteBuilder）中都调用了 `setColumnStatsConfig`

### `core/src/test/java/org/apache/iceberg/TestSchemaAndMappingUpdate.java` (修改, +15/-0 lines)

**修改目的**：验证列统计配置在 schema 变更时的同步行为。

**工作逻辑**：新增 `testModificationWithParquetColumnStats` 测试，验证列重命名时配置键名同步更新，列删除时配置被移除。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestParquet.java` (修改, +46/-0 lines)

**修改目的**：验证按列统计信息开关的实际效果。

**工作逻辑**：新增 `testColumnStatisticsEnabled` 测试，对 int_field 设置 stats-enabled=true，对 string_field 设置 stats-enabled=false，写入数据后通过 `ParquetFileReader` 读取文件元数据，验证 int_field 有统计信息而 string_field 统计信息为空。

### `docs/docs/configuration.md` (修改, +1/-0 lines)

**修改目的**：文档化新的表属性。

**工作逻辑**：在配置文档表格中添加 `write.parquet.stats-enabled.column.col1` 属性说明。

## 总结

本提交实现了按列控制 Parquet 统计信息的功能，通过表属性让用户灵活管理统计信息的收集。同时重构了 Bloom Filter 配置逻辑，统一使用列名到 Parquet 路径的映射，提高了代码复用性。该功能对于优化存储空间和写入性能具有实际意义，特别是对于不依赖统计信息过滤的大列。
