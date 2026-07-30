# 提交 4037：Spark 4.1: Implement listTableSummaries (#16891)

## 提交信息

- **序号**：4037 / 4088
- **哈希**：2876f3f575b88695d8fbff854154863be8d2a4b3
- **短哈希**：2876f3f57
- **日期**：2026-07-15 17:35:06 -0700
- **作者**：drexler-sky
- **提交说明**：Spark 4.1: Implement listTableSummaries (#16891)
- **PR/Issue**：#16891

## 总体目的

Spark 4.1 在 `TableCatalog` 接口中引入了新的 `listTableSummaries` 方法，用于返回命名空间下所有表和视图的轻量级摘要（`TableSummary`，包含标识符和表类型），而不需要加载每个表的完整元数据。Iceberg 的 Spark 4.1 catalog 此前没有实现该方法，会落到默认实现上——而默认实现会逐个 `loadTable` 来构建摘要，对于 Iceberg 这种元数据存储在远端（如 Hive Metastore、对象存储）的 catalog 来说代价很高，会触发大量远程调用。

这个提交为 Iceberg Spark 4.1 catalog 实现了高效的 `listTableSummaries`，直接基于已有的 `listTables` / `listViews` 列表结果构建摘要，避免加载每个表。同时将 Iceberg 表统一标记为 `EXTERNAL`（外部表），以符合 Spark 对外部表的语义：Iceberg 表总有显式存储位置，且通过 catalog 删除表时默认只删除 catalog 条目（除非显式 purge），这正是 Spark 外部表的行为。

此外，提交还处理了一个边界场景：当 `HiveCatalog` 配置 `list-all-tables=true` 时，`listTables` 会返回 metastore 中的所有条目（包括视图），此时需要去重以避免视图被同时报告为表和视图。

## 如何达成设计目的

设计上分两部分：

1. **`SparkCatalog.listTableSummaries`**：先通过 `listViews(namespace)` 收集视图标识符集合，再遍历 `listTables(namespace)` 的结果，跳过那些同时是视图的标识符（去重），将其余的标记为 `EXTERNAL` 加入摘要；最后把所有视图标记为 `VIEW` 加入摘要。这样一次列表操作即可构建全部摘要，无需 `loadTable`。

2. **`BaseSparkTable.properties`**：在表属性中加入 `TableCatalog.PROP_TABLE_TYPE = TableSummary.EXTERNAL_TABLE_TYPE`，使 Iceberg 表在任何查询表属性的路径上都一致地报告为外部表。同时将 `PROP_TABLE_TYPE` 加入 `BASE_PROPERTIES` 集合，确保相关属性过滤逻辑覆盖该键。

测试上新增了两个用例：`testTableTypeIsExternal` 验证表属性中包含 EXTERNAL 类型；`testListTableSummaries` 验证摘要中表被报告为 EXTERNAL、视图被报告为 VIEW 且不重复。为覆盖 HiveCatalog `list-all-tables=true` 的去重场景，测试参数矩阵新增了 `testhive_list_all` catalog 配置。

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java` (+26/-0 lines)

**修改目的**：实现 `listTableSummaries` 方法，提供高效的表/视图摘要列表。

**工作逻辑**：
```java
@Override
public TableSummary[] listTableSummaries(String[] namespace) {
  List<TableSummary> summaries = Lists.newArrayList();
  Set<Identifier> viewIdents = Sets.newHashSet(listViews(namespace));
  for (Identifier ident : listTables(namespace)) {
    if (!viewIdents.contains(ident)) {
      summaries.add(TableSummary.of(ident, TableSummary.EXTERNAL_TABLE_TYPE));
    }
  }
  for (Identifier ident : viewIdents) {
    summaries.add(TableSummary.of(ident, TableSummary.VIEW_TABLE_TYPE));
  }
  return summaries.toArray(new TableSummary[0]);
}
```
先收集视图标识符集合用于去重；遍历 `listTables` 结果时跳过视图标识符，将纯表标记为 EXTERNAL；再将所有视图标记为 VIEW 加入。注释说明这样做的两个关键点：避免默认实现逐个 `loadTable` 的高开销；处理 HiveCatalog `list-all-tables=true` 下 `listTables` 会返回视图的去重需求。新增 import `org.apache.spark.sql.connector.catalog.TableSummary`。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/BaseSparkTable.java` (+9/-1 lines)

**修改目的**：让 Iceberg 表在属性中声明自身为 EXTERNAL 外部表。

**工作逻辑**：
- 在 `BASE_PROPERTIES` 集合中加入 `TableCatalog.PROP_TABLE_TYPE`，使该属性键被纳入基础属性过滤集合。
- 在 `properties()` 方法中添加：
  ```java
  propsBuilder.put(TableCatalog.PROP_TABLE_TYPE, TableSummary.EXTERNAL_TABLE_TYPE);
  ```
  注释解释：Iceberg 表总有显式存储位置，且通过 catalog 删除表时只删除 catalog 条目（除非 purge），这匹配 Spark 对 EXTERNAL 外部表的定义。新增 import `TableCatalog` 和 `TableSummary`。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/TestSparkCatalogOperations.java` (+76/-0 lines)

**修改目的**：为新增的 `listTableSummaries` 实现和 EXTERNAL 表类型声明添加测试覆盖。

**工作逻辑**：
- 在参数化 catalog 配置矩阵中新增 `testhive_list_all` 配置：使用 `SparkCatalog` + `type=hive` + `list-all-tables=true`，专门用于触发 `listTables` 返回视图的去重路径。
- `testTableTypeIsExternal`：加载表并断言其 properties 中 `PROP_TABLE_TYPE` 等于 `EXTERNAL_TABLE_TYPE`。
- `testListTableSummaries`：
  - 判断 catalog 是否支持视图（`SparkCatalog` 且 `validationCatalog` 是 `ViewCatalog`）。
  - 若支持视图，通过 Iceberg `ViewCatalog` API 直接创建一个视图（注释说明：SQL CREATE VIEW 需要 Iceberg Spark 扩展，该测试模块未加载，故直接用 API）。
  - 调用 `listTableSummaries`，断言基础表恰好出现一次且类型为 EXTERNAL。
  - 若支持视图，断言视图恰好出现一次且类型为 VIEW（验证去重：视图不会被同时报告为表）。
  - finally 块中清理创建的视图。
  新增多个 import：`ViewCatalog`、`NoSuchNamespaceException`、`TableCatalog`、`TableSummary`。

## 总结

这个提交为 Iceberg 的 Spark 4.1 集成补齐了 `listTableSummaries` 这一新 catalog API 的高效实现，避免了默认实现逐表加载带来的性能问题，并正确地将 Iceberg 表声明为 Spark 外部表以匹配其存储与删除语义。同时细致地处理了 HiveCatalog `list-all-tables=true` 下视图与表去重的边界场景，并通过参数化测试覆盖了该路径。整体提升了 Iceberg 在 Spark 4.1 上的元数据列举性能与语义正确性。
