# 提交 4075：Spark: Add vectorized Parquet reads for variant columns (#16292)

## 提交信息

- **序号**：4075 / 4088
- **哈希**：9821b252ee766072bece865c22acf372f2938c05
- **短哈希**：9821b252e
- **日期**：2026-07-19 09:33:29 -0700
- **作者**：Neelesh Salian
- **提交说明**：Spark: Add vectorized Parquet reads for variant columns (#16292)
- **PR/Issue**：#16292

## 总体目的

这个提交为 Iceberg Spark 集成添加了对 Variant（变体）列的向量化 Parquet 读取支持，显著提升了读取 variant 列的性能。

Variant 是 Iceberg 支持的半结构化数据类型，在 Parquet 中存储为一个包含 `metadata`（元数据）和 `value`（序列化值）两个子字段的 group。此前，Iceberg Spark 读取 variant 列时只能使用逐行（row-at-a-time）读取，无法利用 Arrow 向量化读取的批量处理优势。`VectorizedReaderBuilder` 中甚至直接抛出 `UnsupportedOperationException("Vectorized reads are not supported yet for variant fields")`。

本提交实现了完整的向量化 variant 读取链路：从 Arrow 层的 variant 叶子节点 reader、到 Spark 层的 `VariantColumnVector` 适配、再到 `SparkBatch` 中的智能回退决策。关键挑战在于处理**脱壳（shredded）variant**——当 variant 的部分字段被提取到独立列（typed_value）存储时，向量化读取无法处理这种结构，需要回退到逐行读取。提交通过 manifest 中的 lower bounds 统计信息在**逐文件**粒度检测是否使用了 shredding，从而对未脱壳的文件使用向量化、对脱壳的文件回退到逐行读取。

## 如何达成设计目的

整体设计分为三层：

1. **Arrow 层**（通用，不依赖 Spark）：
   - `VectorizedVariantVisitor`：继承 `ParquetVariantVisitor`，为 variant 的 `metadata` 和 `serialized`（value）叶子节点各创建一个 `VectorizedArrowReader`，然后组合为 `VectorizedVariantReader`。对 shredded 的 `typed_value`/`object`/`array` 抛出 `UnsupportedOperationException`。
   - `VectorizedArrowReader.VectorizedVariantReader`：组合 metadata reader 和 value reader，`read()` 时分别读取两者的 Arrow 向量，包装为 `VariantVectorHolder`。
   - `VectorHolder.VariantVectorHolder`：持有 metadata 和 value 两个 holder。
   - `VectorizedReaderBuilder`：覆写 `variantVisitor()` 返回新 visitor，移除"not supported"异常。

2. **Spark 层**（4.0 和 4.1 相同实现）：
   - `VariantColumnVector`：继承 Spark `ColumnVector`，包装 value 和 metadata 两个 `IcebergArrowColumnVector`，通过 `getChild(0)` 返回 value、`getChild(1)` 返回 metadata，供 Spark 的 `getVariant()` 调用。其他类型访问方法抛出 `UnsupportedOperationException`。
   - `ColumnVectorBuilder`/`ColumnVectorWithFilter`：处理 `VariantVectorHolder` 到 `VariantColumnVector` 的构建，以及带删除过滤的场景。

3. **智能回退决策**（`SparkBatch`）：
   - `useParquetBatchReadsForColumn()`：variant 列允许向量化读取的前提是 `parquet.shred.variants` 未启用且 metrics 模式不是 None/Counts（需要 bounds 来检测 shredding）。
   - `useParquetBatchReads()`（逐文件）：检查文件 manifest 的 lower bounds——如果 variant 字段 ID 出现在 lower bounds 中，说明该文件的 variant 列有 shredded 字段（因为 shredded 字段会有独立的 bounds），回退到逐行读取。
   - `SparkScanBuilder.includeStats()`：即使 `withStats=false`，也只为 variant 列请求 column stats（`scan.includeColumnStats(variantColumns)`），以支持逐文件 shredding 检测，避免请求全部列统计的开销。

## 修改详情

### Arrow 模块

#### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/VectorHolder.java` (+30/-0 lines)

**修改目的**：新增 variant 向量持有者。

**工作逻辑**：`VariantVectorHolder` 持有 `metadataHolder` 和 `valueHolder` 两个 `VectorHolder`，以及行数。提供 `metadataHolder()` 和 `valueHolder()` 访问方法。

#### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/VectorizedArrowReader.java` (+53/-0 lines)

**修改目的**：新增 variant 向量化 reader。

**工作逻辑**：`VectorizedVariantReader` 组合 `metadataReader` 和 `valueReader`：
- `read(reuse, numValsToRead)`：分别读取 metadata 和 value，支持 reuse（从 `VariantVectorHolder` 拆分出各自的 holder 复用），返回新的 `VariantVectorHolder`。
- `setRowGroupInfo()`、`setBatchSize()`、`close()`：委托给两个子 reader。

#### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/VectorizedVariantVisitor.java` (+144/-0 lines, 新文件)

**修改目的**：为 variant 列创建 Arrow reader。

**工作逻辑**：
- `metadata(primitive)`：为 metadata 叶子创建 `VectorizedArrowReader`（required binary）。
- `serialized(primitive)`：为 value 叶子创建 `VectorizedArrowReader`（optional binary）。
- `variant(group, metadataResult, valueResult)`：组合为 `VectorizedVariantReader`。
- `primitive()`、`value()`（带 typedResult）、`object()`、`array()`：对 shredded variant 抛出 `UnsupportedOperationException`。
- `resolveDescriptor()`：根据 variant group 路径 + 叶子名构建完整列路径，解析 Parquet `ColumnDescriptor`，找不到时返回 null。

#### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/VectorizedReaderBuilder.java` (+13/-3 lines)

**修改目的**：接入 variant visitor。

**工作逻辑**：
- 覆写 `variantVisitor()` 返回 `new VectorizedVariantVisitor(currentPath(), parquetSchema, icebergSchema, rootAllocator, setArrowValidityVector)`。
- `variant()` 方法移除 `throw UnsupportedOperationException`，改为直接返回 visitor 产出的 `result`。

### Spark 模块（v4.0 和 v4.1 相同）

#### `VariantColumnVector.java` (+141/-0 lines, 新文件)

**修改目的**：Spark variant 列向量适配。

**工作逻辑**：构造函数接收 `VariantVectorHolder`，创建 `valueChild` 和 `metadataChild` 两个 `IcebergArrowColumnVector`。`getChild(0)` 返回 value，`getChild(1)` 返回 metadata（Spark `getVariant()` 通过 `child(0)` 取 value、`child(1)` 取 metadata）。null 检查委托给 valueChild。所有标量/数组/map 访问方法抛出 unsupported。

#### `SparkBatch.java` (+44/-... lines)

**修改目的**：向量化读取决策与 shredding 检测。

**工作逻辑**：
- `useParquetBatchReads()`（逐文件）：新增检查——若文件 manifest 的 `lowerBounds` 包含投影中的 variant 字段 ID，则该文件有 shredded variant，返回 false 回退逐行读取。
- `useParquetBatchReadsForColumn()`：variant 列允许向量化的条件：`parquet.shred.variants` 未启用 + metrics 模式不是 None/Counts。最后 `return type.isPrimitiveType() || type.isVariantType()`。

#### `SparkScanBuilder.java` (+24/-... lines)

**修改目的**：选择性请求 variant 列统计。

**工作逻辑**：
- `includeStats(scan, projection, withStats)`：若 `withStats` 为 true，请求全部列统计；否则仅请求 variant 列名的统计（`scan.includeColumnStats(variantColumns)`），支持逐文件 shredding 检测。
- `variantColumnNames(projection)`：从投影中筛选 variant 类型列名。

#### `ColumnVectorBuilder.java`、`ColumnVectorWithFilter.java` (各 +4/+7 lines)

**修改目的**：处理 `VariantVectorHolder` 到 `VariantColumnVector` 的构建和带过滤场景。

### 测试文件

- `TestVectorizedVariantVisitor.java` (+153 lines, 新)：测试 variant visitor 的 reader 创建。
- `TestVectorizedReaderBuilder.java` (+10 lines)：测试 variant 列不再抛异常。
- `TestSparkVariantRead.java` (+205 lines)：端到端 Spark variant 向量化读取测试，含 shredding 回退、带删除等场景。
- `TestVariantShredding.java` (+7 lines)：shredding 相关调整。

## 总结

这是一个重要的性能特性提交，为 Iceberg Spark 集成实现了 Variant 列的向量化 Parquet 读取。设计上的核心亮点是**逐文件 shredding 检测**：通过 manifest 的 lower bounds 判断每个文件是否使用了 variant shredding，对未脱壳文件使用高效的 Arrow 向量化读取，对脱壳文件自动回退到逐行读取，兼顾了性能与正确性。同时 `SparkScanBuilder` 仅按需请求 variant 列统计，避免了请求全部列统计的开销。该实现覆盖 Spark 4.0 和 4.1 两个版本，测试充分。这使 Iceberg 的 variant 类型支持在 Spark 上达到了生产可用的性能水平。由 Neelesh Salian 完成。
