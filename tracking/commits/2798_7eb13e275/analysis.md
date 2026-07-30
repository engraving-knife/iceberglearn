# 提交 2798：Spark: Deprecate unused methods in SparkTableUtil and SparkSchemaUtil (#14308)

## 提交信息

- **序号**：2798 / 4088
- **哈希**：7eb13e2757e87cfbb1d314ff6da3f0f535c61159
- **短哈希**：7eb13e275
- **日期**：2025-10-27 10:24:55 -0700
- **作者**：Yuya Ebihara
- **提交说明**：Spark: Deprecate unused methods in SparkTableUtil and SparkSchemaUtil (#14308)
- **PR/Issue**：#14308

## 总体目的

本提交将 Spark 模块中 `SparkTableUtil` 和 `SparkSchemaUtil` 里不再使用的方法标记为 `@Deprecated`（自 1.11.0 起废弃，将在 1.12.0 中移除）。

随着 Iceberg Spark 集成的演进，许多早期提供的方法已经不再被内部代码或公共 API 使用。保留这些未使用的方法会增加维护负担、造成 API 表面积膨胀，并可能误导用户使用过时的方法。通过先标记 `@Deprecated` 再在后续版本移除的策略，可以给用户迁移时间，同时明确 API 的废弃计划。

被废弃的方法主要涉及：
- `SparkSchemaUtil` 中的 `convertWithFreshIds` 和 `prune` 方法。
- `SparkTableUtil` 中的分区获取（`getPartitions`、`getPartitionsByFilter`、`partitionDF`、`partitionDFByFilter`）、Spark 表/分区导入（`importSparkTable`、`importSparkPartitions` 多个重载）、以及 `filterPartitions` 方法。

## 如何达成设计目的

对每个需要废弃的方法添加 `@Deprecated` 注解，并在 Javadoc 中添加 `@deprecated since 1.11.0, will be removed in 1.12.0` 说明。不修改方法实现，仅做标记。

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkSchemaUtil.java` (+4/-0 lines)

**修改目的**：废弃两个未使用的 schema 工具方法。

**工作逻辑**：
- `convertWithFreshIds(Schema baseSchema, StructType sparkType)`：标记为 `@Deprecated`，该单参数重载版本不再使用。
- `prune(Schema schema, StructType requestedType, List<Expression> filters)`：标记为 `@Deprecated`，该 schema 裁剪方法不再使用。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java` (+24/-0 lines)

**修改目的**：废弃多个未使用的 Spark 表工具方法。

**工作逻辑**：以下方法被标记为 `@Deprecated`：
- `partitionDF(SparkSession, String)` -- 返回分区 DataFrame
- `partitionDFByFilter(SparkSession, String, String)` -- 按过滤条件返回分区 DataFrame
- `getPartitions(SparkSession, String)` -- 获取表的所有分区
- `getPartitionsByFilter(SparkSession, String, String)` -- 按谓词获取分区
- `getPartitionsByFilter(SparkSession, TableIdentifier, Expression)` -- 按表达式获取分区
- `importSparkTable` 的三个重载版本 -- 导入 Spark 表到 Iceberg
- `importSparkPartitions` 的两个重载版本 -- 导入 Spark 分区到 Iceberg
- `filterPartitions(List<SparkPartition>, Map<String, String>)` -- 过滤分区列表

## 总结

本提交将 Spark 模块中 12 个不再使用的方法标记为 `@Deprecated`，计划在 1.12.0 版本中移除。这是 API 清理的标准做法：先标记废弃、给用户迁移时间、再移除。涉及的方法主要是早期的分区获取和 Spark 表导入功能，这些功能已被更好的实现替代。
