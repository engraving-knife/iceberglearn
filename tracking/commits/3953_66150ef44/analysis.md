# 提交 3953：Flink: Backport Pass only white-listed Catalog properties to 1.20, 2.0 (#16966)

## 提交信息

- **序号**：3953 / 4088
- **哈希**：66150ef442be414c9580fc1c73962f1f9719d441
- **短哈希**：66150ef44
- **日期**：2026-06-25 13:43:41 -0700
- **作者**：Swapna Marru
- **提交说明**：Flink: Backport Pass only white-listed Catalog properties to 1.20, 2.0 (#16966)
- **PR/Issue**：#16966（backport #16728，即提交 3946）

## 总体目的

这次提交是 #16728（提交 3946）的 backport，将 Flink SQL `CREATE TABLE LIKE` 中只传递白名单 catalog 属性（不再传递完整 catalog 属性）的安全修复同步到 Flink 1.20 和 2.0 分支。

原问题：`CREATE TABLE LIKE` 功能在 `FlinkCatalog.getTable()` 中将整个 catalog 的属性（可能包含敏感信息如认证凭证）序列化传递到表属性中，存在信息泄露风险。

修复后：只传递 catalog 名称、数据库名和表名，不再传递 `catalogProps`。

## 如何达成设计目的

与 #16728 相同的修改方式：从 `FlinkCreateTableOptions` 移除 `catalogProps` 字段和 `CATALOG_PROPS` 配置选项，修改 `FlinkCatalog` 移除 `catalogProps` 字段和构造参数，修改 `FlinkDynamicTableFactory` 不再合并 catalog 属性。

## 修改详情

### Flink 1.20 模块（7 个文件）

**修改目的**：将白名单 catalog 属性修复应用到 Flink 1.20。

涉及文件：
- `FlinkCatalog.java`：移除 catalogProps 字段和构造参数。
- `FlinkCatalogFactory.java`：移除 catalogProps 传递。
- `FlinkCreateTableOptions.java`：移除 catalogProps 序列化/反序列化。
- `FlinkDynamicTableFactory.java`：不再合并 catalog 属性。
- `CatalogTestBase.java`、`TestFlinkCatalogTable.java`、`TestIcebergSourceSql.java`：适配测试。

### Flink 2.0 模块（7 个文件）

**修改目的**：将白名单 catalog 属性修复应用到 Flink 2.0。

涉及文件与 Flink 1.20 相同。

## 总结

这次提交将 #16728 的 catalog 属性白名单安全修复 backport 到 Flink 1.20 和 2.0，确保所有受支持的 Flink 版本在 `CREATE TABLE LIKE` 中都不再传递可能包含敏感信息的完整 catalog 属性。
