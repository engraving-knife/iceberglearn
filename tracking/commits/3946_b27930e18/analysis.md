# 提交 3946：Flink: SQL: Pass only white-listed Catalog properties for Table LIKE (#16728)

## 提交信息

- **序号**：3946 / 4088
- **哈希**：b27930e1827b7ff00d76b36c2037ac93d2bb3bed
- **短哈希**：b27930e18
- **日期**：2026-06-24 22:06:30 -0700
- **作者**：Swapna Marru
- **提交说明**：Flink: SQL: Pass only white-listed Catalog properties for Table LIKE (#16728)
- **PR/Issue**：#16728

## 总体目的

这次提交修复了 Flink SQL 中 `CREATE TABLE LIKE` 功能的一个安全问题。当用户使用 `CREATE TABLE LIKE` 语法基于一个 Iceberg 表创建新表时，Flink 的 `FlinkCatalog.getTable()` 方法会将源表的元数据序列化后传递给目标表的创建流程。

原本的实现不仅传递了 catalog 名称、数据库名和表名，还传递了整个 catalog 的属性（`catalogProps`）。这些 catalog 属性可能包含敏感信息（如认证凭证、连接字符串、密钥等），将它们传递到表属性中存在信息泄露风险。

修复后，`getTable()` 只序列化白名单信息（catalog 名称、数据库名、表名），不再传递完整的 catalog 属性。创建表时也不再合并 `catalogProps`，仅使用必要的标识信息。

## 如何达成设计目的

从 `FlinkCreateTableOptions` 中移除 `catalogProps` 字段和 `CATALOG_PROPS` 配置选项，修改 `toJson()` 和 `fromJson()` 方法不再序列化 catalog 属性。在 `FlinkCatalog` 中移除 `catalogProps` 字段和构造参数。在 `FlinkDynamicTableFactory.mergeSrcCatalogProps()` 中移除 `mergedProps.putAll(createTableOptions.catalogProps())` 调用。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/FlinkCatalog.java` (+4/-7 lines)

**修改目的**：移除 catalogProps 字段和传递。

**工作逻辑**：
- 移除 `catalogProps` 字段声明和构造函数参数。
- `getTable()` 中调用 `FlinkCreateTableOptions.toJson()` 时不再传递 `catalogProps`。
- 更新注释说明改为只传递 catalog name、database、table。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/FlinkCreateTableOptions.java` (+4/-19 lines)

**修改目的**：移除 catalogProps 的序列化/反序列化。

**工作逻辑**：
- 移除 `Map<String, String> catalogProps` 字段和构造参数。
- 移除 `CATALOG_PROPS` ConfigOption 定义。
- `toJson()` 不再写入 `catalogProps` 字段。
- `fromJson()` 不再读取 `catalogProps` 字段。
- 移除 `catalogProps()` getter 方法。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/FlinkDynamicTableFactory.java` (+6/-5 lines)

**修改目的**：不再合并 catalog 属性。

**工作逻辑**：
- `mergeSrcCatalogProps()` 中移除 `mergedProps.putAll(createTableOptions.catalogProps())` 调用。
- 更新方法注释说明只合并 catalog name、database、table。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/FlinkCatalogFactory.java` (+0/-1 line)

**修改目的**：移除 catalogProps 传递。

### 测试文件（CatalogTestBase.java、TestFlinkCatalogTable.java、TestIcebergSourceSql.java）

**修改目的**：适配 API 变更并更新测试。

## 总结

这次提交通过移除 `CREATE TABLE LIKE` 流程中 catalog 属性的传递，修复了一个潜在的信息安全问题。只传递必要的白名单信息（catalog name、database、table），避免了将可能包含敏感信息的 catalog 属性暴露到表属性中。该修复随后在 #16966 中被 backport 到 Flink 1.20 和 2.0。
