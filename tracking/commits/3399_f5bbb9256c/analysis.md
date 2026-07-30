# 提交 3399：Spark: Deprecate constructor with branch in SparkReadConf/SparkWriteConf (#15591)

## 提交信息

- **序号**：3399 / 4088
- **哈希**：f5bbb9256c70cb66b662458b3c0b36bfa803471a
- **短哈希**：f5bbb9256c
- **日期**：2026-03-16 17:08:33 -0700
- **作者**：Yuya Ebihara
- **提交说明**：Spark: Deprecate constructor with branch in SparkReadConf/SparkWriteConf (#15591)
- **PR/Issue**：#15591

## 总体目的

弃用 `SparkReadConf` 和 `SparkWriteConf` 中带有 `branch` 参数的构造函数。这些构造函数接收 branch 参数但实际上 branch 参数已不再被使用（在之前的版本中已被移除相关逻辑），保留该参数会产生误导。标记为 `@Deprecated` 以引导用户使用不带 branch 参数的构造函数。

## 如何达成设计目的

1. 在 `SparkReadConf` 中，为带 branch 参数的构造函数添加 `@Deprecated` 注解和 Javadoc
2. 在 `SparkReadConf` 中，移除不再使用的 `branch` 和 `options` 字段（构造函数参数 branch 不再存储）
3. 在 `SparkWriteConf` 中，同样为带 branch 参数的构造函数添加 `@Deprecated` 注解和 Javadoc
4. 注解指明自 1.11.0 版本弃用，将在 1.12.0 版本移除，建议使用替代构造函数

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkReadConf.java` (+9/-4 lines)

**修改目的**：弃用带 branch 参数的构造函数，清理未使用的字段。

**工作逻辑**：
- 移除 `private final String branch;` 和 `private final CaseInsensitiveStringMap options;` 字段
- 为 `SparkReadConf(SparkSession spark, Table table, String branch, CaseInsensitiveStringMap options)` 构造函数添加 `@Deprecated` 注解
- 添加 Javadoc 说明：`@deprecated since 1.11.0, will be removed in 1.12.0. Use {@link #SparkReadConf(SparkSession, Table, CaseInsensitiveStringMap)} instead.`
- 构造函数体中不再赋值 `this.branch = branch` 和 `this.options = options`

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkWriteConf.java` (+5 lines)

**修改目的**：弃用带 branch 参数的构造函数。

**工作逻辑**：
- 为 `SparkWriteConf(SparkSession spark, Table table, String branch, CaseInsensitiveStringMap options)` 构造函数添加 `@Deprecated` 注解
- 添加与 SparkReadConf 相同的 Javadoc 弃用说明

## 总结

本提交弃用了 `SparkReadConf` 和 `SparkWriteConf` 中带有 `branch` 参数的构造函数，这些参数实际上已不再使用。通过 `@Deprecated` 注解引导用户迁移到不带 branch 参数的构造函数，计划在 1.12.0 版本移除。同时清理了 SparkReadConf 中不再使用的字段。
