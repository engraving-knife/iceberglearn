# 提交 1824：Flink: support create table like in flink catalog (#12199)

## 提交信息

- **序号**：1824 / 4088
- **哈希**：ffe9ad501f932ffa64ce4b9a8f58ccb29e73990c
- **短哈希**：ffe9ad501
- **日期**：2025-03-05 11:32:47 -0800
- **作者**：Swapna Marru
- **提交说明**：Flink: support create table like in flink catalog (#12199)
- **PR/Issue**：#12199

## 总体目的

该提交为 Flink 的 Iceberg Catalog 增加了 `CREATE TABLE LIKE` 支持，使用户能够通过 `CREATE TABLE new_table LIKE iceberg_table` 语法在非 Iceberg catalog 中创建基于 Iceberg 表结构的新表。

Flink 的 `CREATE TABLE LIKE` 语法依赖于 `Catalog.getTable()` 返回的表属性来复制源表定义。当源表来自 Iceberg catalog 时，Flink 需要能够通过返回的属性重新连接到源 Iceberg catalog 以加载表。但在此之前，`FlinkCatalog.getTable()` 仅返回 Iceberg 表自身的属性，不包含 catalog 连接信息，导致 LIKE 创建的新表无法定位回源 Iceberg 表。

本提交通过在 `getTable()` 时将源 catalog 的连接属性（catalog 名、数据库、表名及 catalog 配置）序列化为 JSON 注入到表属性中，在 `FlinkDynamicTableFactory` 创建动态表时反序列化并合并这些属性，从而支持 LIKE 语法跨 catalog 复制 Iceberg 表。

## 如何达成设计目的

整体设计通过"属性携带 + 序列化传递"实现。新增 `FlinkCreateTableOptions` 类封装 catalog 连接信息的 JSON 序列化/反序列化。在 `FlinkCatalog.getTable()` 时，将源 catalog 属性序列化为 JSON 字符串，连同 connector 标识注入到返回的 CatalogTable 属性中。在 `FlinkDynamicTableFactory` 创建表时，通过 `mergeSrcCatalogProps()` 将序列化的 catalog 属性展开合并回表属性，用于重建 FlinkCatalog 实例。同时增加保留属性校验，防止用户直接使用保留键。

## 修改详情

### flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/FlinkCreateTableOptions.java (新增, 116 lines)

新建类，封装 CREATE TABLE LIKE 所需的 catalog 连接信息。定义配置选项常量（CATALOG_NAME、CATALOG_TYPE、CATALOG_DATABASE、CATALOG_TABLE、CATALOG_PROPS）和保留属性键（SRC_CATALOG_PROPS_KEY="src-catalog"、CONNECTOR_PROPS_KEY="connector"、LOCATION_KEY="location"）。提供 `toJson()` 将 catalog 信息序列化为 JSON 字符串，`fromJson()` 反序列化。从 FlinkDynamicTableFactory 迁移配置选项定义至此。

### flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/FlinkCatalog.java (修改, 68 lines)

- 构造方法新增 `catalogProps` 参数字段，保存 catalog 连接属性。
- `getTable()` 改造：将 catalog 名、库名、表名和 catalogProps 序列化为 `src-catalog` JSON，连同 `connector=iceberg` 标识注入返回的表属性，供 LIKE 语法使用。校验源表不含保留属性键。
- `createTable()` 调整校验：仅当 connector=iceberg 但缺少 src-catalog 时才禁止（允许 LIKE 创建的表通过）。
- `createIcebergTable()` 增加 `isReservedProperty()` 过滤，不将保留属性（connector/src-catalog/location）持久化到 Iceberg 表属性。
- 新增 `toCatalogTableWithProps(Table, Map)` 方法支持注入属性，原 `toCatalogTable` 委托调用。
- location 相关字面量改用 `FlinkCreateTableOptions.LOCATION_KEY` 常量。

### flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/FlinkDynamicTableFactory.java (修改, 89 lines)

- 移除内联的 CATALOG_NAME/CATALOG_TYPE/CATALOG_DATABASE/CATALOG_TABLE 配置选项定义，改引用 `FlinkCreateTableOptions` 中的常量。
- 新增 `mergeSrcCatalogProps(Map)` 方法：从表属性中提取 `src-catalog` JSON，反序列化为 catalog 连接信息，与表属性合并（catalog 属性优先），用于重建 FlinkCatalog。
- `createCatalogTable` 调用 `mergeSrcCatalogProps` 处理属性。

### flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/FlinkCatalogFactory.java (修改, 1 line)

`createCatalog` 时将 `properties` 传入 FlinkCatalog 构造方法。

### 测试文件 (修改)

`TestFlinkCatalogTable.java`（44 行）和 `TestIcebergSourceSql.java`（16 行）新增 CREATE TABLE LIKE 场景测试。

## 小结

该提交通过属性序列化传递机制实现了 Flink CREATE TABLE LIKE 对 Iceberg 表的支持。影响范围为 Flink 1.20 模块的 catalog 层。回迁到 1.4.x 分支时需注意：1.4.x 分支可能同时维护多个 Flink 版本（v1.18/v1.19/v1.20），需确认回迁目标版本；新增 FlinkCreateTableOptions 类和 FlinkCatalog 构造方法签名变更需同步所有调用方。该提交改动较大但自洽，建议整体回迁。注意完整哈希对应的短哈希为 `ffe9ad501`（取前9位）。
