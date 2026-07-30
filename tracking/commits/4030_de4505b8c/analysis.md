# 提交 4030：Spark 4.1: Add Spark REST_CATALOG_PURGE property to delegate DROP TABLE PURGE to REST catalogs (#17186)(Backport of #15614)

## 提交信息

- **序号**：4030 / 4088
- **哈希**：de4505b8c102fb4972efed97702107bce0e26cc6
- **短哈希**：de4505b8c
- **日期**：2026-07-14 09:59:52 -0500
- **作者**：jackylee
- **提交说明**：Spark 4.1: Add Spark REST_CATALOG_PURGE property to delegate DROP TABLE PURGE to REST catalogs (#17186)(Backport of #15614)
- **PR/Issue**：#17186（backport of #15614）

## 总体目的

本提交是为 Spark 4.1 版本添加与提交 4029（Spark 4.0）相同的 `rest-catalog-purge` 功能，使 Spark 4.1 的 `DROP TABLE PURGE` 能够委托给 REST catalog 服务端处理。提交说明标注这是 #15614 的 backport。

功能目的与 4029 完全一致：新增 `rest-catalog-purge` 配置属性（默认 false），开启后 `DROP TABLE PURGE` 调用 `icebergCatalog.dropTable(identifier, true)` 转发给 REST catalog 服务端，并校验只有 REST catalog 才能开启此选项。

## 如何达成设计目的

将 4029 在 `spark/v4.0` 下的全部 3 个文件改动原样复制到 `spark/v4.1` 对应路径。代码逻辑与 4029 完全一致，仅包路径前缀（`spark/v4.0` vs `spark/v4.1`）不同。

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkCatalogProperties.java` (+37/-0 lines, 新文件)

**修改目的**：定义 REST catalog purge 配置属性（同 4029）。

**工作逻辑**：
```java
public static final String REST_CATALOG_PURGE = "rest-catalog-purge";
public static final boolean REST_CATALOG_PURGE_DEFAULT = false;
```

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java` (+33/-3 lines)

**修改目的**：实现 purge 委托逻辑（同 4029）。

**工作逻辑**：
- 新增 `LOG`、`restCatalogPurge`、`isRestCatalog` 字段。
- 初始化时解析配置并校验 `!restCatalogPurge || isRestCatalog`。
- `purgeTable` 中：`isRestCatalog && !isPathIdentifier(ident) && restCatalogPurge` 时委托 `icebergCatalog.dropTable(buildIdentifier(ident), true)`。
- `dropTableWithoutPurging` 重命名为 `catalogDropTable`。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/TestRestDropPurgeTable.java` (+153/-0 lines, 新文件)

**修改目的**：测试 REST catalog purge 委托行为（同 4029）。

## 总结

本提交是 4029 的 Spark 4.1 版本对应改动（backport of #15614），为 Spark 4.1 Iceberg catalog 添加 `rest-catalog-purge` 配置，使 `DROP TABLE PURGE` 能委托给 REST catalog 服务端。代码逻辑与 4029 完全一致，确保 Spark 4.0 和 4.1 功能对齐。默认关闭保持向后兼容，校验只有 REST catalog 可开启。
