# 提交 3801：Spark: Deprecate SparkFilters in favor of SparkV2Filters (#16616)

## 提交信息

- **序号**：3801 / 4088
- **哈希**：6a737004506176a902784ec30fc4d545c1ce6997
- **短哈希**：6a7370045
- **日期**：2026-05-30 15:37:46 -0700
- **作者**：Huaxin Gao <huaxin.gao11@gmail.com>
- **提交说明**：Spark: Deprecate SparkFilters in favor of SparkV2Filters (#16616)
- **PR/Issue**：#16616
- **协作者**：Cursor

## 总体目的

本提交将 Iceberg Spark 模块中基于 Spark DSv1 `Filter[]` API 的 `SparkFilters` 类标记为 `@Deprecated`，计划在 1.12.0 版本移除，引导用户迁移到基于 DSv2 `Predicate[]` API 的 `SparkV2Filters`。`SparkFilters` 负责把 Spark DSv1 的过滤表达式转换为 Iceberg 的 `Expression`，但随着 Spark 全面转向 DSv2 数据源协议，Iceberg 的所有生产代码（覆盖 Spark 3.5、4.0、4.1 三个版本）已经迁移到 `SparkV2Filters`，`SparkFilters` 仅被 `TestSparkFilters` 测试类引用。

继续保留 `SparkFilters` 会给外部使用者传递"仍在维护"的错误信号，也增加维护负担。通过正式标注 `@Deprecated` 并在 Javadoc 中说明替代方案与移除时间线，可以提前警告依赖该类的下游项目，并在 1.12.0 顺利清理。同时给测试类加 `@SuppressWarnings("deprecation")`，确保在移除前测试仍能干净编译运行。

## 如何达成设计目的

设计思路是"标记 + 抑制"：在 Spark 3.5、4.0、4.1 三个版本的 `SparkFilters` 类上加 `@Deprecated` 注解和 Javadoc 说明（注明 `since 1.11.0`，将在 `1.12.0` 移除，并指向 `SparkV2Filters` 作为替代），然后在对应的 `TestSparkFilters` 测试类上加 `@SuppressWarnings("deprecation")` 以避免编译告警。三个 Spark 版本的改动完全一致，体现了 Iceberg 多版本并行维护的模式。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkFilters.java` (+7/-0 lines)

**修改目的**：在 Spark 3.5 模块的 `SparkFilters` 类上标注废弃。

**工作逻辑**：
在类声明前新增 Javadoc 与注解：
```java
/**
 * Converts Spark DSv1 filters into Iceberg expressions.
 *
 * @deprecated since 1.11.0, will be removed in 1.12.0; use {@link SparkV2Filters} which converts
 *     Spark DSv2 {@link org.apache.spark.sql.connector.expressions.filter.Predicate} instead.
 */
@Deprecated
public class SparkFilters {
```

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestSparkFilters.java` (+1/-0 lines)

**修改目的**：抑制因测试废弃类而产生的编译告警。

**工作逻辑**：
在测试类上加 `@SuppressWarnings("deprecation")`，让现有测试在类被移除前继续编译运行：
```java
@SuppressWarnings("deprecation")
public class TestSparkFilters {
```

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkFilters.java` (+7/-0 lines)

**修改目的**：同 Spark 3.5，对 Spark 4.0 模块的 `SparkFilters` 标注废弃。改动与 3.5 完全一致。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/TestSparkFilters.java` (+1/-0 lines)

**修改目的**：抑制 Spark 4.0 测试的废弃告警。改动与 3.5 一致。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkFilters.java` (+7/-0 lines)

**修改目的**：同上，对 Spark 4.1 模块的 `SparkFilters` 标注废弃。改动与 3.5 一致。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/TestSparkFilters.java` (+1/-0 lines)

**修改目的**：抑制 Spark 4.1 测试的废弃告警。改动与 3.5 一致。

## 总结

本提交是 Iceberg Spark 模块向 DSv2 全面迁移的收尾步骤之一：将已被淘汰的 DSv1 过滤器转换类正式标记为废弃，明确移除时间线（1.12.0），并通过 Javadoc 引导用户使用 `SparkV2Filters`。改动覆盖所有受支持的 Spark 版本（3.5、4.0、4.1），保持了多版本一致性。这是一次典型的 API 演进治理工作，体现了项目对向后兼容与有序清理的重视。本提交由人与 Cursor AI 协作完成。
