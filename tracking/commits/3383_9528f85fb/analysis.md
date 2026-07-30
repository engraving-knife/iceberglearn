# 提交 3383：Spark, Flink: Backport add TCK for File Format API (#15619)

## 提交信息

- **序号**：3383 / 4088
- **哈希**：9528f85fb29a8140a7cd6dc332f0efb4ba2592bb
- **短哈希**：9528f85fb
- **日期**：2026-03-13
- **作者**：Joy Haldar
- **提交说明**：Spark, Flink: Backport add TCK for File Format API (#15619)
- **PR/Issue**：#15619（回移 #15441）

## 总体目的

本提交是上一个提交 #15441（提交 3381，"Add TCK for File Format API"）的回移（backport），将 File Format API 的技术兼容性工具包（TCK）测试覆盖到 Spark v3.5、v4.0 与 Flink v1.20、v2.0 这四个较早版本模块，确保这些维护分支上的 File Format API 同样具备跨格式、跨引擎互操作的正确性验证。

原提交 #15441 仅在最新的 Spark v4.1 和 Flink v2.1 模块中新增了 `TestSparkFormatModel`、`TestFlinkFormatModel` 及 `InternalRowConverter`。但由于 Iceberg 同时维护多个 Spark/Flink 版本分支，各分支的 File Format API 代码相同或高度相似，若不在较早版本模块中同步建立 TCK，这些分支上的格式 API 修改将缺乏回归保护。本回移将完全相同的测试类与转换工具复制到 v1.20、v2.0（Flink）和 v3.5、v4.0（Spark）模块，使 TCK 覆盖全部受支持的引擎版本。

## 如何达成设计目的

直接将 #15441 新增的测试文件原样复制到对应的较早版本模块目录下。具体为：Flink v1.20 与 v2.0 各新增 `TestFlinkFormatModel.java`；Spark v3.5、v4.0 各新增 `InternalRowConverter.java` 与 `TestSparkFormatModel.java`。文件内容与 #15441 中 v4.1/v2.1 版本完全一致，因为这些测试依赖的基类 `BaseFormatModelTests`、数据生成器 `DataGenerators` 等位于共享的 `data` 模块，各 Spark/Flink 版本模块通过测试依赖即可复用，无需版本特定适配。

## 修改详情

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkFormatModel.java` (+51/-0 lines，新建)

**修改目的**：为 Flink 1.20 模块提供 File Format API TCK 实现。

**工作逻辑**：
`TestFlinkFormatModel extends BaseFormatModelTests<RowData>`，实现四个钩子：`engineType()` 返回 `RowData.class`；`engineSchema()` 调用 `FlinkSchemaUtil.convert(schema)`；`convertToEngine()` 调用 `RowDataConverter.convert(schema, record)`；`assertEquals()` 调用 `TestHelpers.assertRows(actual, expected, FlinkSchemaUtil.convert(schema))`。内容与 #15441 中 Flink v2.1 版本完全相同，使该模块的 File Format API 获得 Avro/Parquet/ORC 跨格式、Generic/RowData 跨引擎的双向往返测试覆盖。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkFormatModel.java` (+51/-0 lines，新建)

**修改目的**：为 Flink 2.0 模块提供 File Format API TCK 实现。

**工作逻辑**：与 Flink v1.20 完全相同。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/InternalRowConverter.java` (+115/-0 lines，新建)

**修改目的**：为 Spark 3.5 模块提供 Iceberg Record 到 Spark InternalRow 的转换工具。

**工作逻辑**：
`InternalRowConverter` 静态工具类，`convert(Schema, Record)` 将 Iceberg Record 转为 Spark `GenericInternalRow`。按 `Type.typeId()` 分支处理各类型：基本类型直接透传；DATE/TIMESTAMP 转为自 epoch 的天数/微秒；STRING/UUID 转为 `UTF8String`；FIXED/BINARY 提取字节数组；DECIMAL 转为 `Decimal`；STRUCT 递归；LIST 转 `GenericArrayData`；MAP 转 `ArrayBasedMapData`；TIME/VARIANT 落入 default 抛 `UnsupportedOperationException`。内容与 #15441 中 Spark v4.1 版本完全相同。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/TestSparkFormatModel.java` (+54/-0 lines，新建)

**修改目的**：为 Spark 3.5 模块提供 File Format API TCK 实现。

**工作逻辑**：
`TestSparkFormatModel extends BaseFormatModelTests<InternalRow>`，实现四个钩子：`engineType()` 返回 `InternalRow.class`；`engineSchema()` 调用 `SparkSchemaUtil.convert(schema)`；`convertToEngine()` 调用 `InternalRowConverter.convert(schema, record)`；`assertEquals()` 逐行调用 `TestHelpers.assertEquals(schema, expected.get(i), actual.get(i))`。内容与 #15441 中 Spark v4.1 版本完全相同。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/data/InternalRowConverter.java` (+115/-0 lines，新建)

**修改目的**：为 Spark 4.0 模块提供 InternalRowConverter 工具。

**工作逻辑**：与 Spark v3.5 完全相同。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/data/TestSparkFormatModel.java` (+54/-0 lines，新建)

**修改目的**：为 Spark 4.0 模块提供 File Format API TCK 实现。

**工作逻辑**：与 Spark v3.5 完全相同。

## 总结

本提交将 #15441 新建的 File Format API TCK 测试回移到 Spark v3.5、v4.0 与 Flink v1.20、v2.0 四个较早版本模块，使所有受支持的 Spark/Flink 版本分支都具备跨格式（Avro/Parquet/ORC）、跨引擎（Generic Record 与引擎原生行类型）的双向写入读取往返测试覆盖。回移的文件与原提交完全一致，无需版本特定适配，有效补齐了维护分支上 File Format API 的回归保护。
