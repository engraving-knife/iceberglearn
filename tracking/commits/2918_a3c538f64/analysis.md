# 提交 2918：Fix NameMapping loss in ParquetUtil.footerMetrics (#14617)

## 提交信息

- **序号**：2918 / 4088
- **哈希**：a3c538f647a113cf61dbfd7855d9c71da8c722ba
- **短哈希**：a3c538f64
- **日期**：2025-11-24 16:11:04 -0800
- **作者**：Tamas Mate
- **提交说明**：Fix NameMapping loss in ParquetUtil.footerMetrics
- **PR/Issue**：#14617

## 总体目的

Iceberg 在读取 Parquet 文件时，可以通过 `NameMapping` 为没有内嵌字段 ID 的旧 Parquet 文件推断字段 ID。`ParquetUtil.footerMetrics` 方法在调用时如果传入了 NameMapping，会通过 `getParquetTypeWithIds` 方法获取带有字段 ID 的 Parquet MessageType，然后转换为 Iceberg fileSchema。

然而此前的代码中存在一个 bug：虽然正确地用 NameMapping 生成了带 ID 的 `parquetTypeWithIds`，但在调用 `ParquetMetrics.metrics` 时传入的第二个参数却是原始的 `messageType`（不带 ID），而非 `parquetTypeWithIds`。这导致 `ParquetMetrics.metrics` 内部在通过字段 ID 匹配列统计时找不到对应的 ID，从而对于没有内嵌字段 ID 的 Parquet 文件返回空的 metrics（columnSizes 等为空）。这会影响表统计信息的准确性，进而影响查询优化器的决策。

本提交修复了这一问题，将 `ParquetMetrics.metrics` 调用中的 `messageType` 替换为 `parquetTypeWithIds`。

## 如何达成设计目的

修复非常简洁：在 `footerMetrics` 方法中，不再从 metadata 获取原始 `messageType`，而是直接将已经通过 NameMapping 处理过的 `parquetTypeWithIds` 传递给 `ParquetMetrics.metrics`。这样 `ParquetMetrics` 内部就能正确使用带字段 ID 的 MessageType 来匹配列统计，确保 metrics 正确返回。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetUtil.java` (+3/-2 lines)

**修改目的**：修复 NameMapping 在 footerMetrics 中被丢失的 bug。

**工作逻辑**：
原代码：
```java
MessageType messageType = metadata.getFileMetaData().getSchema();
MessageType parquetTypeWithIds = getParquetTypeWithIds(metadata, nameMapping);
Schema fileSchema = ParquetSchemaUtil.convertAndPrune(parquetTypeWithIds);
return ParquetMetrics.metrics(fileSchema, messageType, metricsConfig, metadata, fieldMetrics);
```
修复后移除了 `messageType` 变量，将 `ParquetMetrics.metrics` 的第二个参数从 `messageType` 改为 `parquetTypeWithIds`：
```java
MessageType parquetTypeWithIds = getParquetTypeWithIds(metadata, nameMapping);
Schema fileSchema = ParquetSchemaUtil.convertAndPrune(parquetTypeWithIds);
return ParquetMetrics.metrics(fileSchema, parquetTypeWithIds, metricsConfig, metadata, fieldMetrics);
```

### `parquet/src/test/java/org/apache/iceberg/parquet/TestParquet.java` (+49/-0 lines)

**修改目的**：添加测试验证 NameMapping 在无字段 ID 的 Parquet 文件上正确工作。

**工作逻辑**：
新增 `testFooterMetricsWithNameMappingForFileWithoutIds` 测试。该测试创建一个带字段 ID 的 Iceberg Schema（id=1, data=2）并生成对应的 NameMapping，然后使用纯 Avro schema（不带字段 ID）写入一个 Parquet 文件。读取文件后验证 `ParquetSchemaUtil.hasIds(parquetSchema)` 为 false（确认文件确实没有内嵌 ID），然后调用 `ParquetUtil.footerMetrics` 传入 NameMapping，断言 `metrics.columnSizes()` 的键为 `{1, 2}`（即 NameMapping 推断出的字段 ID），证明 NameMapping 被正确传递。

## 总结

本提交修复了一个影响数据统计准确性的 bug：`ParquetUtil.footerMetrics` 在使用 NameMapping 时，将带 ID 的 MessageType 错误地替换为原始的不带 ID 的 MessageType，导致无内嵌字段 ID 的 Parquet 文件返回空 metrics。修复仅涉及一行参数替换，但影响深远——确保旧格式 Parquet 文件的列统计信息能通过 NameMapping 正确提取，对查询优化至关重要。
