# 提交 3600：Core, Catalogs: Add support for unique table locations via catalog property (#12892)

## 提交信息

- **序号**：3600 / 4088
- **哈希**：b809dcd770d3c10cc6d81b70dc198422749cfa0e
- **短哈希**：b809dcd77
- **日期**：2026-04-27 14:57:29 +0200
- **作者**：Dmitriy Avseitsev
- **提交说明**：Core, Catalogs: Add support for unique table locations via catalog property (#12892)
- **PR/Issue**：#12892

## 总体目的

这个提交为 Iceberg catalog 添加了通过 catalog 属性 `unique-table-location` 来为新建表生成唯一存储位置的能力。

在 Iceberg 中，表的默认存储位置通常是基于表名构建的（如 `{warehouse}/{namespace}.db/{tableName}`）。当用户重命名一个表后再用原名创建新表时，新表会使用与之前相同的存储路径，这可能导致数据冲突或孤立文件清理问题。特别是当使用 `DROP TABLE ... PURGE` 删除重命名后的表时，可能会误删新表的数据文件，因为两者的存储路径相同。

通过启用 `unique-table-location`，每个新创建的表都会获得一个带有 UUID 后缀的唯一路径（如 `tableName-a1b2c3d4...`），确保即使重命名后再创建同名表，它们的物理存储路径也不会冲突。这对于需要频繁重命名和重建表的场景特别重要，可以避免数据损坏。

## 如何达成设计目的

实现方案分为几个层次：
1. 在 `CatalogProperties` 中定义新的配置属性 `unique-table-location`，默认为 `false`。
2. 在 `LocationUtil` 中新增 `tableLocation()` 工具方法，根据 `useUniqueLocation` 标志决定是否在表名后追加 UUID 后缀。
3. 在各个 catalog 实现（InMemoryCatalog、JdbcCatalog、GlueCatalog、DynamoDbCatalog、HiveCatalog、EcsCatalog）中，读取该配置属性并在 `defaultWarehouseLocation()` 方法中使用 `LocationUtil.tableLocation()` 生成表路径。
4. 添加了通用的 `CatalogTests` 测试用例和 Spark 专属的 `TestUniqueTableLocation` 集成测试。

## 修改详情

### `core/src/main/java/org/apache/iceberg/CatalogProperties.java` (+9/-0 lines)

**修改目的**：定义新的 catalog 配置属性。

**工作逻辑**：
新增属性常量：
```java
public static final String UNIQUE_TABLE_LOCATION = "unique-table-location";
public static final boolean UNIQUE_TABLE_LOCATION_DEFAULT = false;
```
默认值为 `false`，保持向后兼容。

### `core/src/main/java/org/apache/iceberg/util/LocationUtil.java` (+24/-0 lines)

**修改目的**：新增生成唯一表路径的工具方法。

**工作逻辑**：
```java
public static String tableLocation(TableIdentifier tableIdentifier, boolean useUniqueLocation) {
  Preconditions.checkArgument(null != tableIdentifier, "Invalid identifier: null");
  if (useUniqueLocation) {
    String uniqueSuffix = UUID.randomUUID().toString().replace("-", "");
    return String.format("%s-%s", tableIdentifier.name(), uniqueSuffix);
  } else {
    return tableIdentifier.name();
  }
}
```
当 `useUniqueLocation` 为 `true` 时，返回 `表名-32位UUID` 格式的路径组件；否则返回纯表名。

### `core/src/main/java/org/apache/iceberg/inmemory/InMemoryCatalog.java` (+9/-2 lines)

**修改目的**：在 InMemoryCatalog 中支持唯一表位置。

**工作逻辑**：
在 `initialize()` 中读取 `unique-table-location` 属性，在 `defaultWarehouseLocation()` 中使用 `LocationUtil.tableLocation(tableIdentifier, uniqueTableLocation)` 替代直接使用表名。

### `core/src/main/java/org/apache/iceberg/jdbc/JdbcCatalog.java` (+7/-1 lines)

**修改目的**：在 JdbcCatalog 中支持唯一表位置。

**工作逻辑**：
与 InMemoryCatalog 类似，在 `initialize()` 中读取属性，在 `defaultWarehouseLocation()` 中使用 `LocationUtil.tableLocation()`。

### `aws/src/main/java/org/apache/iceberg/aws/glue/GlueCatalog.java` (+22/-4 lines)

**修改目的**：在 GlueCatalog 中支持唯一表位置。

**工作逻辑**：
新增 `uniqueTableLocation` 字段，修改 `initialize()` 方法签名（包括 `@VisibleForTesting` 的重载版本）增加 `uniqTableLocation` 参数，在 `defaultWarehouseLocation()` 中使用 `LocationUtil.tableLocation()`。

### `aws/src/main/java/org/apache/iceberg/aws/dynamodb/DynamoDbCatalog.java` (+18/-4 lines)

**修改目的**：在 DynamoDbCatalog 中支持唯一表位置。

**工作逻辑**：
修改 `initialize()` 方法签名增加 `uniqTableLocation` 参数，在 `defaultWarehouseLocation()` 中使用 `LocationUtil.tableLocation()` 生成路径组件。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveCatalog.java` (+10/-2 lines)

**修改目的**：在 HiveCatalog 中支持唯一表位置。

**工作逻辑**：
在 `initialize()` 中读取属性，在 `defaultWarehouseLocation()` 的两个分支（数据库位置已设/未设）中都使用 `LocationUtil.tableLocation()` 生成表路径。

### `dell/src/main/java/org/apache/iceberg/dell/ecs/EcsCatalog.java` (+10/-2 lines)

**修改目的**：在 EcsCatalog 中支持唯一表位置。

**工作逻辑**：
在 `initialize()` 中读取属性，在 `defaultWarehouseLocation()` 中使用 `LocationUtil.tableLocation()`。

### `core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java` (+82/-0 lines)

**修改目的**：添加通用的 catalog 测试用例。

**工作逻辑**：
新增两个测试：
1. `createTableInUniqueLocation()`：验证启用 `unique-table-location` 后，重命名表再创建同名表时，两个表的位置不同。
2. `dropAfterRenameDoesntCorruptTable()`：验证重命名后创建同名表，再 PURGE 删除重命名的表不会损坏新表的数据文件。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/SparkCatalogConfig.java` (+16/-1 lines)

**修改目的**：添加带唯一表位置的 Spark catalog 测试配置。

**工作逻辑**：
新增两个枚举值：
- `SPARK_SESSION_WITH_UNIQUE_LOCATION`：SparkSessionCatalog 配置，启用 `unique-table-location`。
- `HIVE_WITH_UNIQUE_LOCATION`：HiveCatalog 配置，启用 `unique-table-location`。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/sql/TestUniqueTableLocation.java` (+132 lines, new file)

**修改目的**：Spark 集成测试，验证唯一表位置功能。

**工作逻辑**：
包含两个测试：
1. `noCollisionAfterRename()`：验证重命名后创建同名表，两个表位置不同。
2. `orphanCleanupDoesntCorruptTable()`：验证重命名后创建同名表，对新表执行孤立文件清理不会影响重命名后的表。

### 其他测试文件

多个 catalog 测试文件（GlueTestBase、TestGlueCatalogTable、TestDynamoDbCatalog、TestGlueCatalog、TestBigQueryCatalog、TestRESTCatalog、TestEcsCatalog、RESTCompatibilityKitCatalogTests）进行了相应调整以适配新的方法签名或测试新功能。

### `docs/docs/configuration.md` (+1/-0 lines)

**修改目的**：文档记录新的配置属性。

**工作逻辑**：
在 catalog 属性表中新增一行：`unique-table-location | false | Whether to use a unique location for new tables`。

## 总结

这是一个重要的功能增强，通过为表添加 UUID 后缀来生成唯一的物理存储路径，解决了表重命名后重建同名表可能导致的数据冲突和 PURGE 删除误删数据的问题。该特性通过 catalog 级别的配置属性控制，默认关闭以保持向后兼容。实现覆盖了 Iceberg 支持的所有主要 catalog（InMemory、JDBC、Glue、DynamoDB、Hive、ECS），并配有完善的测试。这对于在生产环境中安全地使用表重命名和重建操作具有重要意义。
