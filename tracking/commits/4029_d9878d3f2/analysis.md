# 提交 4029：Spark 4.0: Add Spark REST_CATALOG_PURGE property to delegate DROP TABLE PURGE to REST catalogs (#17185)

## 提交信息

- **序号**：4029 / 4088
- **哈希**：d9878d3f29b53f48fdd0d4b78c1743014fc7d4b7
- **短哈希**：d9878d3f2
- **日期**：2026-07-14 09:58:44 -0500
- **作者**：jackylee
- **提交说明**：Spark 4.0: Add Spark REST_CATALOG_PURGE property to delegate DROP TABLE PURGE to REST catalogs (#17185)
- **PR/Issue**：#17185

## 总体目的

本提交为 Spark 4.0 的 Iceberg `SparkCatalog` 新增 `rest-catalog-purge` 配置属性，使 Spark 的 `DROP TABLE PURGE` 操作能够委托给 REST catalog 服务端处理，而非由 Spark 客户端执行文件删除。

背景：当使用 REST catalog 时，表的实际数据文件可能存储在 Spark 客户端无法直接访问的位置（如服务端管理的对象存储），或者服务端有特殊的 purge 语义（如权限校验、审计、级联删除）。原实现中 `SparkCatalog.purgeTable` 在客户端通过 Hadoop 文件系统删除数据文件，这在 REST catalog 场景下可能失败或不符合预期。

本提交新增 `rest-catalog-purge` 开关（默认 false 保持向后兼容），开启后 `DROP TABLE PURGE` 会调用 `icebergCatalog.dropTable(identifier, true)` 将 purge 请求转发给 REST catalog 服务端。同时校验只有 REST catalog 才能开启此选项。

## 如何达成设计目的

1. 新增 `SparkCatalogProperties` 类定义 `REST_CATALOG_PURGE = "rest-catalog-purge"` 和默认值 false。
2. `SparkCatalog` 在初始化时解析该属性到 `restCatalogPurge` 字段，并判断 `isRestCatalog = catalog instanceof RESTCatalog`，校验开启 purge 必须是 REST catalog。
3. `purgeTable(ident)` 方法中：若 `isRestCatalog && !isPathIdentifier(ident) && restCatalogPurge`，直接调用 `icebergCatalog.dropTable(buildIdentifier(ident), true)` 委托服务端 purge；否则走原客户端删除逻辑。
4. `dropTable(ident)` 重命名内部方法调用为 `catalogDropTable`。
5. 新增测试覆盖。

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkCatalogProperties.java` (+37/-0 lines, 新文件)

**修改目的**：定义 REST catalog purge 配置属性。

**工作逻辑**：
```java
public static final String REST_CATALOG_PURGE = "rest-catalog-purge";
public static final boolean REST_CATALOG_PURGE_DEFAULT = false;
```
Javadoc 说明：控制 Spark 是否将 DROP TABLE PURGE 委托给 REST catalog，默认 false 保持向后兼容。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java` (+33/-3 lines)

**修改目的**：实现 purge 委托逻辑。

**工作逻辑**：
- 新增 `LOG`、`restCatalogPurge`、`isRestCatalog` 字段。
- 初始化时解析 `restCatalogPurge`，构建 catalog 后判断 `isRestCatalog`，校验：
  ```java
  Preconditions.checkArgument(
      !restCatalogPurge || isRestCatalog,
      "Cannot enable '%s': the configured catalog is not a REST catalog: %s",
      SparkCatalogProperties.REST_CATALOG_PURGE, catalog.getClass().getName());
  ```
- `purgeTable`：
  ```java
  if (isRestCatalog && !isPathIdentifier(ident)) {
    if (restCatalogPurge) {
      return icebergCatalog.dropTable(buildIdentifier(ident), true);
    } else {
      LOG.info("Set '{}' to true to use the REST catalog's capabilities to purge the table.",
          SparkCatalogProperties.REST_CATALOG_PURGE);
    }
  }
  boolean dropped = catalogDropTable(ident);
  // ... 原客户端删除逻辑
  ```
- `dropTableWithoutPurging` 重命名为 `catalogDropTable`。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/TestRestDropPurgeTable.java` (+153/-0 lines, 新文件)

**修改目的**：测试 REST catalog purge 委托行为。

**工作逻辑**：测试覆盖开启/关闭 purge 开关、非 REST catalog 开启 purge 抛异常、path identifier 不委托等场景（具体见文件）。

## 总结

本提交为 Spark 4.0 Iceberg catalog 新增 `rest-catalog-purge` 配置，使 `DROP TABLE PURGE` 能委托给 REST catalog 服务端处理，适应服务端管理数据文件的场景。默认关闭保持向后兼容，并校验只有 REST catalog 可开启。这是 REST catalog 集成的重要功能补全。该提交对应 4030 是 Spark 4.1 版本的相同改动（backport of #15614）。
