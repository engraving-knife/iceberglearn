# 提交 3645：Spark: Migrate RollBackStageTable to use SupportsDeleteV2 (#16211)

## 提交信息

- **序号**：3645 / 4088
- **哈希**：0011a85e4d28b7bc8ff670a3a0d32a8331d87d0f
- **短哈希**：0011a85e4
- **日期**：2026-05-05 18:06:35 -0700
- **作者**：drexler-sky
- **提交说明**：Spark: Migrate RollBackStageTable to use SupportsDeleteV2 (#16211)
- **PR/Issue**：#16211

## 总体目的

这个提交将 Spark 的 `RollbackStagedTable` 类从实现 `SupportsDelete` 接口迁移为实现 `SupportsDeleteV2` 接口。

`SupportsDelete` 是 Spark 较早的删除接口，使用 `Filter[]`（来自 `org.apache.spark.sql.sources`）作为删除条件。`SupportsDeleteV2` 是 Spark 较新的删除接口，使用 `Predicate[]`（来自 `org.apache.spark.sql.connector.expressions.filter`）作为删除条件，提供了更丰富的谓词表达能力。这是 Spark 数据源 API V2 演进的一部分，社区鼓励各实现迁移到 V2 接口。本提交将 `RollbackStagedTable` 对齐到新的 V2 删除接口，覆盖 Spark 3.4、3.5、4.0、4.1 四个版本。

## 如何达成设计目的

将 `RollbackStagedTable` 的接口声明从 `SupportsDelete` 改为 `SupportsDeleteV2`，将 `deleteWhere` 方法的参数类型从 `Filter[]` 改为 `Predicate[]`，并相应更新 import 和方法内部的反射调用类型。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/RollbackStagedTable.java` (+5/-5 lines)

**修改目的**：迁移到 SupportsDeleteV2 接口。

**工作逻辑**：
1. import 变更：移除 `org.apache.spark.sql.connector.catalog.SupportsDelete` 和 `org.apache.spark.sql.sources.Filter`，新增 `org.apache.spark.sql.connector.catalog.SupportsDeleteV2` 和 `org.apache.spark.sql.connector.expressions.filter.Predicate`。
2. 类声明变更：
```java
// 旧
public class RollbackStagedTable
    implements StagedTable, SupportsRead, SupportsWrite, SupportsDelete {
// 新
public class RollbackStagedTable
    implements StagedTable, SupportsRead, SupportsWrite, SupportsDeleteV2 {
```
3. 方法签名和实现变更：
```java
// 旧
@Override
public void deleteWhere(Filter[] filters) {
  call(SupportsDelete.class, t -> t.deleteWhere(filters));
}
// 新
@Override
public void deleteWhere(Predicate[] predicates) {
  call(SupportsDeleteV2.class, t -> t.deleteWhere(predicates));
}
```

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/RollbackStagedTable.java` (+5/-5 lines)

**修改目的**：同上，为 Spark 3.5 应用相同迁移。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/RollbackStagedTable.java` (+5/-5 lines)

**修改目的**：同上，为 Spark 4.0 应用相同迁移。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/RollbackStagedTable.java` (+5/-5 lines)

**修改目的**：同上，为 Spark 4.1 应用相同迁移。

## 总结

这个提交将 `RollbackStagedTable` 从旧的 `SupportsDelete` 接口（基于 `Filter[]`）迁移到新的 `SupportsDeleteV2` 接口（基于 `Predicate[]`），对齐 Spark 数据源 API V2 的演进方向。改动简洁，涉及接口声明、方法签名和反射调用类型的更新，覆盖 Spark 3.4/3.5/4.0/4.1 四个版本。`SupportsDeleteV2` 提供了更丰富的谓词表达能力，是 Spark 推荐使用的删除接口。
