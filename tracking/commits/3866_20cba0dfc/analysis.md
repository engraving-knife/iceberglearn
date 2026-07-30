# 提交分析：3866 - Spark 3.5: Add REST_CATALOG_PURGE property

## 提交信息

| 字段 | 值 |
|------|-----|
| 序号 | 3866 |
| 短哈希 | 20cba0dfc |
| 完整哈希 | 20cba0dfc05019c7235257c5da556cd259df0382 |
| 日期 | 2026-06-12 10:54:02 -0500 |
| 作者 | Felix Schneider |
| 提交说明 | Spark 3.5: Add Spark REST_CATALOG_PURGE property to delegate DROP TABLE PURGE to REST catalogs (#15614) |

## 总体目的

为 Spark 3.5 的 `SparkCatalog` 添加一个新配置属性 `rest-catalog-purge`，允许将 `DROP TABLE PURGE` 操作委托给 REST Catalog 处理，而不是由 Spark 客户端执行本地文件删除。

### 背景

在默认行为下，当用户执行 `DROP TABLE PURGE` 时，Spark 的 `SparkCatalog` 会先通过 catalog 删除表元数据，然后在客户端侧删除数据文件。对于 REST Catalog 而言，这种客户端侧的文件删除行为可能不合适——REST Catalog 服务端可能有自己的清理策略或权限控制，客户端直接删除文件可能导致权限问题或不一致状态。此提交通过引入一个配置开关，让用户可以选择将 purge 操作完全委托给 REST Catalog 服务端处理。

## 修改详情

### 1. 新增 `SparkCatalogProperties.java`

**文件路径**: `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkCatalogProperties.java`

新增了一个配置属性类，定义了 `REST_CATALOG_PURGE` 属性：

- **属性名**: `rest-catalog-purge`
- **默认值**: `false`（向后兼容）
- **用途**: 控制是否将 `DROP TABLE PURGE` 请求委托给 REST Catalog

```java
public static final String REST_CATALOG_PURGE = "rest-catalog-purge";
public static final boolean REST_CATALOG_PURGE_DEFAULT = false;
```

### 2. 修改 `SparkCatalog.java`

**文件路径**: `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java`

#### 2.1 新增字段和日志器

```java
private static final Logger LOG = LoggerFactory.getLogger(SparkCatalog.class);
private boolean restCatalogPurge;
private boolean isRestCatalog;
```

#### 2.2 初始化时读取配置并进行校验

在 `initialize` 方法中：
- 从配置中读取 `rest-catalog-purge` 属性
- 构建 catalog 后检测是否为 `RESTCatalog` 实例
- 校验：如果启用了 `rest-catalog-purge` 但 catalog 不是 RESTCatalog，则抛出 `IllegalArgumentException`

```java
this.restCatalogPurge =
    PropertyUtil.propertyAsBoolean(
        options,
        SparkCatalogProperties.REST_CATALOG_PURGE,
        SparkCatalogProperties.REST_CATALOG_PURGE_DEFAULT);

this.isRestCatalog = catalog instanceof RESTCatalog;

Preconditions.checkArgument(
    !restCatalogPurge || isRestCatalog,
    "Cannot enable '%s': the configured catalog is not a REST catalog: %s",
    SparkCatalogProperties.REST_CATALOG_PURGE,
    catalog.getClass().getName());
```

#### 2.3 修改 `purgeTable` 方法逻辑

当满足以下条件时，将 purge 委托给 REST Catalog：
- catalog 是 RESTCatalog
- 标识符不是路径标识符（`isPathIdentifier`）

如果 `restCatalogPurge` 为 `true`，直接调用 `icebergCatalog.dropTable(identifier, true)` 让 REST Catalog 处理 purge。如果为 `false`，记录一条 INFO 日志提示用户可以启用该属性。

方法 `dropTableWithoutPurging` 被重命名为 `catalogDropTable`，更准确地反映其职责。

### 3. 新增测试 `TestRestDropPurgeTable.java`

**文件路径**: `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestRestDropPurgeTable.java`

新增 4 个测试用例：
- `purgeTableDelegatesToCatalogWhenEnabled`: 验证启用时 purge 委托给 REST Catalog（`dropTable(any, eq(true))`）
- `purgeNotDelegatedToCatalogWhenDisabled`: 验证禁用时不委托 purge（`dropTable(any, eq(false))`）
- `initializationFailsWhenPurgeEnabledWithNonRestCatalog`: 验证非 REST Catalog 启用 purge 时抛出异常
- `purgeTableDelegatesToCatalogWhenEnabledViaSessionCatalog`: 验证通过 `SparkSessionCatalog` 也能正确委托

## 总结

此提交为 Spark 3.5 的 Iceberg 集成添加了将 `DROP TABLE PURGE` 委托给 REST Catalog 的能力。设计上采用了配置开关（默认关闭）以确保向后兼容，同时在初始化阶段进行类型校验防止误用。测试覆盖了启用/禁用、非 REST Catalog 校验、以及 Session Catalog 场景。
