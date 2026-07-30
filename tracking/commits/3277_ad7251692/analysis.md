# 提交 3277：Spark: Various fixes for SparkFileWriterFactory (#15356)

## 提交信息

- **序号**：3277 / 4088
- **哈希**：ad72516922f5b56133d3ab930ea82fa369369cdb
- **短哈希**：ad7251692
- **日期**：2026-02-18
- **作者**：pvary
- **提交说明**：Spark: Various fixes for SparkFileWriterFactory (#15356)
- **PR/Issue**：#15356

## 总体目的

本提交针对 Spark 4.1 模块中因引入 `FormatModel` API（参见前序提交将 Spark 迁移到新 `FormatModelRegistry` 的改动）而遗留在 `SparkFileWriterFactory` 与 `SparkParquetWriters` 中的若干问题进行修复。在迁移到基于注册表的写入工厂之后，代码中存在两类缺陷：一是 `SparkFileWriterFactory` 中保存的格式字段 `format` 错误地取自数据文件格式（`dataFileFormat`），而该字段实际只用于构建 position delete 写入器，应当使用删除文件格式（`deleteFileFormat`）；二是 Parquet position delete 写入路径中 `metricsConfig` 被重复设置了两次；三是 `SparkParquetWriters` 中存在一个与另一重载完全等价的冗余 `buildWriter` 方法。这些问题虽然在大多数场景下（数据格式与删除格式相同）不会暴露，但当表配置了与数据格式不同的删除文件格式时，会导致 position delete 以错误格式写入，引发读取异常。

## 如何达成设计目的

将 `SparkFileWriterFactory` 中字段 `format` 重命名为 `deleteFormat` 并改用 `deleteFileFormat` 赋值，使语义与实际用途一致；删除 Parquet position delete 路径中重复的 `.metricsConfig(metricsConfig)` 调用；移除 `SparkParquetWriters` 中冗余的 `buildWriter(StructType, MessageType, Schema)` 重载。改动范围小而精准，集中在 v4.1 目录的两个文件。

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkFileWriterFactory.java` (+3/-3 lines)

**修改目的**：修正 position delete 写入使用的格式字段并清理重复配置。

**工作逻辑**：
字段声明由 `private final FileFormat format;` 改为 `private final FileFormat deleteFormat;`，两个构造方法中的赋值由 `this.format = dataFileFormat;` 改为 `this.deleteFormat = deleteFileFormat;`。`newPositionDeleteWriter` 中已弃用的旧写入路径的 `switch (format)` 改为 `switch (deleteFormat)`，对应的异常消息也同步更新。这一修复确保当表通过 `DELETE_DEFAULT_FILE_FORMAT` 配置了与数据格式不同的删除格式时，position delete 文件以正确的删除格式写出。此外，Parquet 分支中删除了重复的 `.metricsConfig(metricsConfig)` 调用——该方法在之前已设置过一次，重复设置既无必要也容易引起误解。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/data/SparkParquetWriters.java` (+0/-9 lines)

**修改目的**：移除冗余的 `buildWriter` 重载。

**工作逻辑**：
删除了 `buildWriter(StructType dfSchema, MessageType type, Schema icebergSchema)` 方法。该方法体与已存在的 `buildWriter(Schema icebergSchema, MessageType type, StructType dfSchema)` 完全相同（都是 `dfSchema != null ? dfSchema : SparkSchemaUtil.convert(icebergSchema)` 后调用 `ParquetWithSparkSchemaVisitor.visit`），仅参数顺序不同，属于迁移过程中遗留的重复代码。移除后注册表回调统一使用另一重载，避免维护两份等价实现。

## 总结

本提交修复了 `FormatModel` API 迁移后 `SparkFileWriterFactory` 中 position delete 格式取值错误、Parquet 路径重复设置 metrics 以及 `SparkParquetWriters` 冗余重载三个问题，使 v4.1 的写入工厂在数据格式与删除格式不一致时也能正确工作，代码更整洁。
