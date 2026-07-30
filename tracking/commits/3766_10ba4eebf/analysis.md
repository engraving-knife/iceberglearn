# 提交 3766：Flink: Support writing shredded variant (#15596)

## 提交信息

- **序号**：3766 / 4088
- **哈希**：10ba4eebf1b870627fb20b9a75413e8088cd975f
- **短哈希**：10ba4eebf
- **日期**：2026-05-22 06:38:18 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Support writing shredded variant (#15596)
- **PR/Issue**：#15596

## 总体目的

这个提交为 Flink 引擎添加了对 Variant 类型"shredding"（拆分/ shred）写入的支持。Variant 是一种半结构化数据类型（类似 JSON），shredding 是一种优化技术，将 Variant 的部分字段提取出来存储为 Parquet 的原生列，而不是全部以二进制 blob 存储，从而提升查询性能。

此前 Iceberg 的 Spark 引擎已经支持 Variant shredding（通过 `SparkVariantShreddingAnalyzer`），但 Flink 引擎不支持。这个提交补齐了 Flink 的这一功能差距，使 Flink 用户也能利用 Variant shredding 优化写入性能。

同时，这个提交还修改了 `ParquetFormatModel` 的 API，将 copy 函数从 `UnaryOperator<D>` 改为 `Function<S, UnaryOperator<D>>`（即 copy 函数工厂），因为 Flink 的 `RowDataSerializer` 需要根据具体的 `RowType`（schema）来创建，而不能像 Spark 那样使用一个通用的 copy 函数。

## 如何达成设计目的

1. **新增 `FlinkVariantShreddingAnalyzer`**：继承 `VariantShreddingAnalyzer<RowData, RowType>`，负责从 Flink 的 `RowData` 中提取 Variant 值并转换为 Iceberg 的 `VariantValue`。
2. **修改 `ParquetFormatModel`**：将 `copyFunc` 从直接函数改为工厂函数 `Function<S, UnaryOperator<D>>`，这样不同引擎可以根据 schema 创建合适的 copy 函数。
3. **注册 Flink 的 Parquet 格式模型**：在 `FlinkFormatModels` 中注册带有 shredding analyzer 和 copy 函数工厂的 Parquet 格式模型。
4. **添加写入配置**：在 `FlinkWriteConf` 和 `FlinkWriteOptions` 中添加 `shred-variants` 和 `variant-inference-buffer-size` 两个配置项。
5. **传递写入属性**：在 `SinkUtil` 中将配置传递到写入属性。
6. **更新 Spark 适配**：修改 Spark 的 `SparkFormatModels` 以适配新的工厂函数签名。

## 修改详情

### `docs/docs/flink-configuration.md` (+2/-0 lines)

**修改目的**：文档记录新增的 Flink 写入配置项。

**工作逻辑**：在 Flink 配置表格中新增两行：`shred-variants`（覆盖表级 `write.parquet.shred-variants`）和 `variant-inference-buffer-size`（覆盖表级 `write.parquet.variant-inference-buffer-size`）。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/FlinkWriteConf.java` (+18/-0 lines)

**修改目的**：添加 Variant shredding 相关的配置读取方法。

**工作逻辑**：
新增两个方法：
- `parquetShredVariants()`：读取 `shred-variants` 选项或 `PARQUET_SHRED_VARIANTS` 表属性，默认值为 `PARQUET_SHRED_VARIANTS_DEFAULT`。
- `parquetVariantInferenceBufferSize()`：读取 `variant-inference-buffer-size` 选项或 `PARQUET_VARIANT_BUFFER_SIZE` 表属性，默认值为 `PARQUET_VARIANT_BUFFER_SIZE_DEFAULT`。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/FlinkWriteOptions.java` (+6/-0 lines)

**修改目的**：定义两个新的 Flink 写入选项。

**工作逻辑**：
- `SHRED_VARIANTS`：key 为 `shred-variants`，boolean 类型，默认 false。
- `VARIANT_INFERENCE_BUFFER_SIZE`：key 为 `variant-inference-buffer-size`，int 类型，无默认值。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/data/FlinkFormatModels.java` (+6/-2 lines)

**修改目的**：注册 Flink 的 Parquet 格式模型，包含 shredding analyzer 和 copy 函数工厂。

**工作逻辑**：
在 `ParquetFormatModel.create` 调用中新增两个参数：
```java
new FlinkVariantShreddingAnalyzer(),
(Function<RowType, UnaryOperator<RowData>>)
    rowType -> new RowDataSerializer(rowType)::copy)
```
其中 copy 函数工厂根据 `RowType` 创建 `RowDataSerializer`，并使用其 `copy` 方法。这是因为 Flink 的 `RowDataSerializer` 需要知道具体的 RowType 才能正确序列化。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/data/FlinkVariantShreddingAnalyzer.java` (+72/-0 lines, 新文件)

**修改目的**：实现 Flink 特有的 Variant shredding 分析器。

**工作逻辑**：
继承 `VariantShreddingAnalyzer<RowData, RowType>`，实现两个方法：
- `extractVariantValues(List<RowData> bufferedRows, int variantFieldIndex)`：遍历缓冲的行数据，从每行中提取 Variant 字段值。将 Flink 的 `BinaryVariant` 转换为 Iceberg 的 `VariantValue`，包括元数据和值两部分，都使用 LITTLE_ENDIAN 字节序。
- `resolveColumnIndex(RowType flinkSchema, String columnName)`：通过 `flinkSchema.getFieldIndex(columnName)` 解析列名到索引。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/SinkUtil.java` (+6/-0 lines)

**修改目的**：将 shredding 配置传递到 Parquet 写入属性。

**工作逻辑**：
在 Parquet 写入属性的构建中新增：
```java
writeProperties.put(PARQUET_SHRED_VARIANTS, String.valueOf(conf.parquetShredVariants()));
writeProperties.put(PARQUET_VARIANT_BUFFER_SIZE, String.valueOf(conf.parquetVariantInferenceBufferSize()));
```

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetFormatModel.java` (+34/-8 lines)

**修改目的**：将 copy 函数改为工厂模式以支持需要 schema 的 copy 实现。

**工作逻辑**：
1. 将字段 `copyFunc` 改为 `copyFuncFactory`（类型 `Function<S, UnaryOperator<D>>`）。
2. 原有的 `create` 方法（接收 `UnaryOperator<D>`）标记为 `@Deprecated`，内部委托给新的 `create` 方法。
3. 新增 `create` 方法接收 `Function<S, UnaryOperator<D>>` 参数。
4. 在 `buildShreddedAppender()` 方法中，通过 `copyFuncFactory.apply(engineSchema)` 获取实际的 copy 函数，并添加非空校验。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestParquetDataWriter.java` (+4/-4 lines)

**修改目的**：适配 `ParquetFormatModel` API 变更。

**工作逻辑**：更新测试中对 `ParquetFormatModel.create` 的调用，将 copy 函数包装为工厂函数形式。

### `spark/v4.0/spark/src/main/java/.../SparkFormatModels.java` (+4/-0 lines)
### `spark/v4.1/spark/src/main/java/.../SparkFormatModels.java` (+4/-0 lines)

**修改目的**：适配新的 `ParquetFormatModel` API 签名。

**工作逻辑**：
将原来的 `InternalRow::copy` 改为 `(Function<StructType, UnaryOperator<InternalRow>>) unused -> InternalRow::copy`，即忽略 schema 参数直接返回通用的 copy 函数（Spark 的 `InternalRow.copy()` 不需要 schema 信息）。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/TestFlinkVariantShreddingType.java` (+1008/-0 lines, 新文件)

**修改目的**：全面测试 Flink Variant shredding 功能。

**工作逻辑**：包含大量测试用例验证 Variant shredding 的写入和读取行为，覆盖各种 Variant 数据类型和 shredding 场景。

## 总结

这个提交为 Flink 引擎添加了 Variant shredding 写入支持，使 Flink 与 Spark 在 Variant 优化方面功能对齐。核心改动包括新增 `FlinkVariantShreddingAnalyzer`、重构 `ParquetFormatModel` 的 copy 函数为工厂模式（以适应 Flink 需要schema 的序列化器），以及添加相关配置项。这是一个较大的功能提交，涉及 api、parquet、flink 和 spark 多个模块的协调修改。
