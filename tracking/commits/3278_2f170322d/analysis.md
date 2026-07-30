# 提交 3278：Spark: Backport various fixes for SparkFileWriterFactory (#15357)

## 提交信息

- **序号**：3278 / 4088
- **哈希**：2f170322d425a4c6267a9033efa2107c9bfc53db
- **短哈希**：2f170322d
- **日期**：2026-02-18
- **作者**：pvary
- **提交说明**：Spark: Backport various fixes for SparkFileWriterFactory (#15357)
- **PR/Issue**：#15357（回移自 #15356）

## 总体目的

本提交是将 #15356（即序号 3277 提交，针对 Spark 4.1 的 `SparkFileWriterFactory` 修复）回移到 1.4.x 维护分支的 Spark 3.4、3.5、4.0 三个版本目录。提交说明明确标注 "Backports #15356"。被回移的源改动修复了 `FormatModel` API 迁移后遗留的三个问题：position delete 写入器错误地使用数据文件格式（`dataFileFormat`）而非删除文件格式（`deleteFileFormat`）、Parquet position delete 路径重复设置 `metricsConfig`、以及 `SparkParquetWriters` 中存在等价冗余的 `buildWriter` 重载。回移的动机是让 1.4.x 分支上三个 Spark 版本与 v4.1 / main 分支保持同样的修复水平，避免维护分支在表配置了与数据格式不同的删除格式（`DELETE_DEFAULT_FILE_FORMAT`）时出现 position delete 以错误格式写入的缺陷。

## 如何达成设计目的

将 #15356 的改动逐字应用到 `spark/v3.4/`、`spark/v3.5/`、`spark/v4.0/` 三个目录下的 `SparkFileWriterFactory.java` 与 `SparkParquetWriters.java`，改动内容与 v4.1 完全一致：重命名 `format` 为 `deleteFormat` 并改用 `deleteFileFormat` 赋值、删除重复的 `.metricsConfig` 调用、移除冗余重载方法。

## 修改详情

### `spark/v3.4/`、`spark/v3.5/`、`spark/v4.0/` 下 `.../spark/source/SparkFileWriterFactory.java` (各 +3/-3 lines，共 3 文件)

**修改目的**：修正 position delete 写入格式取值并清理重复配置。

**工作逻辑**：
字段 `private final FileFormat format;` 重命名为 `deleteFormat`，两个构造方法中赋值由 `this.format = dataFileFormat;` 改为 `this.deleteFormat = deleteFileFormat;`。`newPositionDeleteWriter` 旧路径的 `switch (format)` 改为 `switch (deleteFormat)`，异常消息同步更新。Parquet 分支删除重复的 `.metricsConfig(metricsConfig)` 调用。这样当删除文件格式与数据文件格式不同时，position delete 仍按正确的删除格式写出。

### `spark/v3.4/`、`spark/v3.5/`、`spark/v4.0/` 下 `.../spark/data/SparkParquetWriters.java` (各 +0/-9 lines，共 3 文件)

**修改目的**：移除冗余的 `buildWriter` 重载。

**工作逻辑**：
删除 `buildWriter(StructType dfSchema, MessageType type, Schema icebergSchema)` 方法。其方法体与同文件中 `buildWriter(Schema icebergSchema, MessageType type, StructType dfSchema)` 完全等价，仅参数顺序不同，属于迁移遗留的重复代码。移除后统一使用另一重载。

## 总结

本提交把 #15356 的三项修复（删除格式取值、重复 metricsConfig、冗余重载）同步到 1.4.x 分支的 Spark 3.4/3.5/4.0 三个版本，与 v4.1 保持一致，确保维护分支在数据格式与删除格式不一致时 position delete 写入正确。
