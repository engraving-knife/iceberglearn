# 提交 3967：Core, Spark: Migrate Spark table properties to Spark module (#15875)

## 提交信息

- **序号**：3967 / 4088
- **哈希**：8c1ee9d9df4184a7889f85f6a9e1f82f959e0eaf
- **短哈希**：8c1ee9d9d
- **日期**：2026-06-29 18:43:57 -0700
- **作者**：Szehon Ho
- **提交说明**：Core, Spark: Migrate Spark table properties to Spark module (#15875)
- **PR/Issue**：#15875

## 总体目的

本提交将 Spark 特有的表属性（table properties）从 Core 模块迁移到 Spark 模块，实现关注点分离。此前，`write.spark.fanout.enabled`、`write.spark.accept-any-schema`、`write.spark.auto-schema-evolution.enabled` 等 Spark 专有属性定义在 Core 模块的 `TableProperties` 中，但这些属性仅被 Spark 引擎使用，不应污染 Core API。

迁移后，这些属性定义在新增的 `SparkTableProperties` 类中（位于 Spark 模块），Core 模块中的旧定义被标记为 `@Deprecated`（将在 1.14.0 移除），保持向后兼容。

## 如何达成设计目的

1. 在每个 Spark 版本模块（v3.5、v4.0）中新增 `SparkTableProperties` 类，定义 Spark 专有属性常量。
2. 更新 Spark 模块内的代码（`SparkWriteConf`、`SparkTable` 等）引用新类。
3. 将 Core 模块 `TableProperties` 中的 Spark 属性标记为 `@Deprecated`，指向新位置。
4. 更新所有测试文件使用新的引用。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableProperties.java` (+38/-3 lines)

**修改目的**：将 Spark 属性标记为废弃。

**工作逻辑**：为以下属性添加 `@Deprecated` 注解和 Javadoc，说明将在 1.14.0 移除，应使用 `SparkTableProperties` 中的对应常量：
- `SPARK_WRITE_PARTITIONED_FANOUT_ENABLED` / `_DEFAULT`
- `SPARK_WRITE_ACCEPT_ANY_SCHEMA` / `_DEFAULT`
- `SPARK_WRITE_AUTO_SCHEMA_EVOLUTION` / `_DEFAULT`
- `SPARK_WRITE_ADVISORY_PARTITION_SIZE_BYTES`

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkTableProperties.java` (+38/-0 lines, 新文件)

**修改目的**：定义 Spark 专有表属性。

**工作逻辑**：
```java
public class SparkTableProperties {
  public static final String WRITE_PARTITIONED_FANOUT_ENABLED = "write.spark.fanout.enabled";
  public static final boolean WRITE_PARTITIONED_FANOUT_ENABLED_DEFAULT = false;
  public static final String WRITE_ACCEPT_ANY_SCHEMA = "write.spark.accept-any-schema";
  public static final boolean WRITE_ACCEPT_ANY_SCHEMA_DEFAULT = false;
  public static final String WRITE_AUTO_SCHEMA_EVOLUTION = "write.spark.auto-schema-evolution.enabled";
  public static final boolean WRITE_AUTO_SCHEMA_EVOLUTION_DEFAULT = true;
  public static final String WRITE_ADVISORY_PARTITION_SIZE_BYTES = "write.spark.advisory-partition-size-bytes";
}
```

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkWriteConf.java` (+2/-2 lines)

**修改目的**：更新引用从 `TableProperties` 到 `SparkTableProperties`。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkTable.java` (+3/-2 lines)

**修改目的**：更新 `acceptAnySchema` 属性读取使用新类。

### Spark v4.0 模块的同名文件

**修改目的**：同步 v4.0 模块的相同修改，包括新增 `SparkTableProperties`、更新 `SparkWriteConf`、`SparkTable` 等。

### 测试文件（多个）

**修改目的**：更新测试中的静态导入和使用。

**工作逻辑**：将 `import static org.apache.iceberg.TableProperties.SPARK_WRITE_*` 改为 `import static org.apache.iceberg.spark.SparkTableProperties.WRITE_*`。涉及 `SparkRowLevelOperationsTestBase`、`TestSparkDistributionAndOrderingUtil`、`TestDataFrameWriterV2`、`TestSparkDataWrite`、`TestMergeSchemaEvolution` 等。

## 总结

本提交是一个模块化重构，将 Spark 专有表属性从 Core 迁移到 Spark 模块，遵循关注点分离原则。通过 `@Deprecated` 保持向后兼容，计划在 1.14.0 完全移除 Core 中的旧定义。这有助于保持 Core API 的引擎无关性。
