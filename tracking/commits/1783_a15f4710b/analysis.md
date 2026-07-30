# 提交 1783：Arrow, Parquet, Spark 3.5, Flink 1.20: Avoid deprecated method (#11874)

## 提交信息

- **序号**：1783 / 4088
- **哈希**：a15f4710b246f356eee5ae3594e6e758d88556b0
- **短哈希**：a15f4710b
- **日期**：2025-02-25 08:08:43 +0100
- **作者**：Yuya Ebihara
- **提交说明**：Arrow, Parquet, Spark 3.5, Flink 1.20: Avoid deprecated method (#11874)
- **PR/Issue**：#11874

## 总体目的

这个提交将多个模块中对已弃用（deprecated）API 的调用替换为推荐的现代 API。主要涉及三类弃用 API 的替换：

1. **Parquet 的 `OriginalType` 替换为 `LogicalTypeAnnotation`**：Parquet 库中 `PrimitiveType.getOriginalType()` 和 `GroupType.getOriginalType()` 方法已被弃用，推荐使用 `getLogicalTypeAnnotation()` 方法配合 `LogicalTypeAnnotation` 类层次结构来判断类型。这影响 Arrow、Parquet、Flink 1.20 和 Spark 3.5 模块中所有使用 `OriginalType` 的代码。

2. **Spark 3.5 的 Table schema API 替换**：Spark 3.5 中 `Table.schema().fields()` 已被弃用，推荐使用 `Table.columns()` 和 `Column` 类。

3. **Spark 3.5 的 Parquet footer 读取和 MetricsConfig API 替换**：`ParquetFileReader.readFooter()` 已被弃用，推荐使用 `ParquetFileReader.open()`。`MetricsConfig.fromProperties()` 被替换为 `MetricsConfig.forTable()`。

这些替换确保代码使用上游库的最新推荐 API，避免在未来版本中因弃用 API 被移除而导致的兼容性问题。

## 如何达成设计目的

提交通过在多个模块中逐一替换弃用方法调用来达成目标。对于 Parquet 类型判断，将基于 `OriginalType` 枚举的 switch-case 逻辑重构为基于 `LogicalTypeAnnotation` 子类型的 instanceof 判断；对于 Spark API，直接替换方法调用为等价的新 API。

## 修改详情

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/GenericArrowVectorAccessorFactory.java`（修改, +3/-1 lines）

**修改目的**：将 Decimal 类型判断从 OriginalType 替换为 LogicalTypeAnnotation。

**工作逻辑**：
- `isDecimal()` 方法从 `OriginalType.DECIMAL.equals(primitive.getOriginalType())` 改为 `primitive.getLogicalTypeAnnotation() instanceof LogicalTypeAnnotation.DecimalLogicalTypeAnnotation`，使用 instanceof 判断是否为 Decimal 逻辑类型。

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/VectorizedArrowReader.java`（修改, +3/-2 lines）

**修改目的**：同上，替换 Decimal 类型判断。

**工作逻辑**：
- 将 `OriginalType.DECIMAL.equals(primitive.getOriginalType())` 替换为 `primitive.getLogicalTypeAnnotation() instanceof LogicalTypeAnnotation.DecimalLogicalTypeAnnotation`。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetTypeVisitor.java`（修改, +5/-11 lines）

**修改目的**：将 LIST/MAP 类型判断从 OriginalType switch 改为 LogicalTypeAnnotation equals 判断。

**工作逻辑**：
- 原先使用 `group.getOriginalType()` 获取 `OriginalType` 枚举，通过 switch-case 判断 `LIST` 和 `MAP`。
- 改为使用 `group.getLogicalTypeAnnotation()` 获取 `LogicalTypeAnnotation`，通过 `LogicalTypeAnnotation.listType().equals(annotation)` 和 `LogicalTypeAnnotation.mapType().equals(annotation)` 判断，代码更简洁。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/data/ParquetWithFlinkSchemaVisitor.java`（修改, +95/-103 lines）

**修改目的**：将 LIST/MAP 类型判断从 OriginalType switch 改为 LogicalTypeAnnotation instanceof 判断。

**工作逻辑**：
- 将原先基于 `OriginalType` 的 switch-case 结构（LIST/MAP/default 分支）重构为基于 `LogicalTypeAnnotation` 的 if-else if 结构。
- LIST 判断改为 `annotation instanceof LogicalTypeAnnotation.ListLogicalTypeAnnotation`，MAP 判断改为 `annotation instanceof LogicalTypeAnnotation.MapLogicalTypeAnnotation`。
- 逻辑内容（list 和 map 的处理）保持不变，仅控制结构从 switch 改为 if-else，消除了 default 分支穿透的风险。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/ParquetWithSparkSchemaVisitor.java`（修改, +92/-104 lines）

**修改目的**：同 Flink 1.20 版本，将 LIST/MAP 类型判断替换为 LogicalTypeAnnotation。

**工作逻辑**：
- 与 Flink 版本相同的重构模式，将 `OriginalType` switch-case 改为 `LogicalTypeAnnotation` if-else if 结构。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java`（修改, +1/-1 lines）

**修改目的**：替换弃用的 MetricsConfig.fromProperties 为 MetricsConfig.forTable。

**工作逻辑**：
- 将 `MetricsConfig.fromProperties(targetTable.properties())` 替换为 `MetricsConfig.forTable(targetTable)`，后者是推荐的新 API，直接接受 Table 对象。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestSparkCatalogOperations.java`（修改, +3/-3 lines）

**修改目的**：替换弃用的 Spark Table schema API。

**工作逻辑**：
- 将 `DataTypes.createStructField(...)` + `table.schema().fields()[2]` 替换为 `Column.create(...)` + `table.columns()[2]`，使用 Spark 3.5 推荐的 `Column` API 和 `Table.columns()` 方法。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestCompressionSettings.java`（修改, +4/-2 lines）

**修改目的**：替换弃用的 ParquetFileReader.readFooter 方法。

**工作逻辑**：
- 将 `ParquetFileReader.readFooter(CONF, new Path(inputFile.location()), NO_FILTER)` 替换为 `ParquetFileReader.open(HadoopInputFile.fromPath(new Path(inputFile.location()), CONF)).getFooter()`，使用推荐的 `ParquetFileReader.open()` API 通过 `HadoopInputFile` 读取 footer。
- 移除了不再需要的 `NO_FILTER` 常量导入。

## 小结

- **成效**：成功将 Arrow、Parquet、Flink 1.20、Spark 3.5 模块中所有已弃用的 API 调用替换为推荐的现代 API，确保代码与上游库的最新版本保持兼容，避免未来因弃用 API 被移除而导致的构建或运行时问题。
- **影响范围**：涉及 Arrow、Parquet、Flink 1.20、Spark 3.5 四个模块共 8 个文件。主要影响 Parquet schema 类型判断逻辑和 Spark 表操作测试代码，不影响业务逻辑的正确性，仅是 API 调用方式的现代化。
- **回迁到 1.4.x 的注意事项**：建议回迁。此提交是技术债务清理，将弃用 API 替换为新 API。回迁时需确认 1.4.x 分支中 Parquet 和 Spark 依赖版本是否支持 `LogicalTypeAnnotation` API（Parquet 1.12+ 支持）。各模块可独立回迁，无强前置依赖。Flink 1.20 和 Spark 3.5 的修改可分别回迁到对应版本目录。
