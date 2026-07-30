# 提交 3623：Spark 4.1: Migrate SparkWriteBuilder to SupportsOverwriteV2 (#16164)

## 提交信息

- **序号**：3623 / 4088
- **哈希**：96d556b8b31ac9e9c2c5ed35a5972e8d040499c3
- **短哈希**：96d556b8b
- **日期**：2026-04-30 09:54:23 -0700
- **作者**：drexler-sky
- **提交说明**：Spark 4.1: Migrate SparkWriteBuilder to SupportsOverwriteV2 (#16164)
- **PR/Issue**：#16164

## 总体目的

这个提交将 Spark 4.1 的 `SparkWriteBuilder` 从实现 `SupportsOverwrite` 接口迁移到实现 `SupportsOverwriteV2` 接口。

Spark 的数据源 API 有两个版本的覆写接口：
- `SupportsOverwrite`（V1）：使用 `Filter[]` 数组来表示覆写条件，使用的是 Spark 旧的过滤 API。
- `SupportsOverwriteV2`（V2）：使用 `Predicate[]` 数组来表示覆写条件，使用的是 Spark 新的 V2 过滤 API。

Spark 4.1 推荐使用 V2 接口，`SupportsOverwrite` 已被废弃。这个迁移使 Iceberg 的 Spark 集成与 Spark 4.1 的最新 API 保持一致，同时使用更现代的谓词表达式（Predicate）替代旧的过滤器（Filter）。

## 如何达成设计目的

1. 将 `SparkWriteBuilder` 实现的接口从 `SupportsOverwrite` 改为 `SupportsOverwriteV2`。
2. 将 `overwrite(Filter[] filters)` 方法改为 `overwrite(Predicate[] predicates)`。
3. 将过滤器转换从 `SparkFilters.convert(filters)` 改为 `SparkV2Filters.convert(predicates)`。
4. 更新相关的 import 语句。

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkWriteBuilder.java` (+6/-6 lines)

**修改目的**：迁移到 SupportsOverwriteV2 接口。

**工作逻辑**：

1. **修改类声明**：
```java
// 之前
class SparkWriteBuilder implements WriteBuilder, SupportsDynamicOverwrite, SupportsOverwrite {
// 之后
class SparkWriteBuilder implements WriteBuilder, SupportsDynamicOverwrite, SupportsOverwriteV2 {
```

2. **修改 overwrite 方法签名和实现**：
```java
// 之前
@Override
public WriteBuilder overwrite(Filter[] filters) {
  Preconditions.checkState(mode == null, "Cannot use overwrite by filter with other modes");
  Expression expr = SparkFilters.convert(filters);
  this.mode = useDynamicOverwrite(expr) ? new DynamicOverwrite() : new OverwriteByFilter(expr);
  return this;
}

// 之后
@Override
public WriteBuilder overwrite(Predicate[] predicates) {
  Preconditions.checkState(mode == null, "Cannot use overwrite by filter with other modes");
  Expression expr = SparkV2Filters.convert(predicates);
  this.mode = useDynamicOverwrite(expr) ? new DynamicOverwrite() : new OverwriteByFilter(expr);
  return this;
}
```

3. **更新 import**：
   - 移除：`org.apache.iceberg.spark.SparkFilters`、`org.apache.spark.sql.connector.write.SupportsOverwrite`、`org.apache.spark.sql.sources.Filter`
   - 新增：`org.apache.iceberg.spark.SparkV2Filters`、`org.apache.spark.sql.connector.expressions.filter.Predicate`、`org.apache.spark.sql.connector.write.SupportsOverwriteV2`

## 总结

这个提交将 Spark 4.1 的 `SparkWriteBuilder` 从已废弃的 `SupportsOverwrite` 接口迁移到 `SupportsOverwriteV2` 接口，使用 Spark 新的 V2 谓词 API（`Predicate[]`）替代旧的过滤 API（`Filter[]`）。这是与 Spark 4.1 最新 API 保持一致的重要迁移，确保 Iceberg 使用推荐的现代接口。
