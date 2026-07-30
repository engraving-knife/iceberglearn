# 提交 3627：Spark 3.4, 3.5, 4.0: Migrate SparkWriteBuilder to SupportsOverwriteV2 (#16178)

## 提交信息

- **序号**：3627 / 4088
- **哈希**：53f1f1a0a7fbdb013676358e8198f2cbd88ef296
- **短哈希**：53f1f1a0a
- **日期**：2026-05-01 07:50:18 -0700
- **作者**：drexler-sky
- **提交说明**：Spark 3.4, 3.5, 4.0: Migrate SparkWriteBuilder to SupportsOverwriteV2 (#16178)
- **PR/Issue**：#16178

## 总体目的

这个提交将提交 3623（Spark 4.1 的 `SparkWriteBuilder` 迁移到 `SupportsOverwriteV2`）的变更同步到 Spark 3.4、3.5 和 4.0 版本。

Iceberg 同时维护多个 Spark 版本，`SupportsOverwriteV2` 的迁移需要应用到所有支持的版本。提交 3623 首先在 Spark 4.1 中完成迁移，本提交将相同的变更应用到 Spark 3.4、3.5 和 4.0。

## 如何达成设计目的

将 Spark 4.1 中的 `SparkWriteBuilder` 变更原样应用到 Spark 3.4、3.5 和 4.0 的对应文件中。三个版本的修改内容基本一致。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkWriteBuilder.java` (+6/-6 lines)

**修改目的**：迁移到 SupportsOverwriteV2 接口。

**工作逻辑**：
1. 类声明从 `SupportsOverwrite` 改为 `SupportsOverwriteV2`。
2. `overwrite(Filter[] filters)` 方法改为 `overwrite(Predicate[] predicates)`。
3. 过滤器转换从 `SparkFilters.convert(filters)` 改为 `SparkV2Filters.convert(predicates)`。
4. 更新 import 语句：移除 `SparkFilters`、`SupportsOverwrite`、`Filter`，新增 `SparkV2Filters`、`Predicate`、`SupportsOverwriteV2`。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkWriteBuilder.java` (+6/-6 lines)

**修改目的**：与 Spark 3.4 相同的迁移。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/SparkWriteBuilder.java` (+6/-6 lines)

**修改目的**：与 Spark 3.4 相同的迁移。

## 总结

这个提交是提交 3623 的扩展，将 `SparkWriteBuilder` 从 `SupportsOverwrite` 迁移到 `SupportsOverwriteV2` 的工作同步到 Spark 3.4、3.5 和 4.0 版本。这使所有支持的 Spark 版本都使用 V2 覆写接口和谓词 API，与 Spark 最新推荐保持一致。
