# 提交 2749：Hive: Fixing the trailing slash issue for the database paths in HMS

## 提交信息

- **序号**：2749 / 4088
- **哈希**：34096a7a2abe89c5794087b86c856bb0e57afacf
- **短哈希**：34096a7a2
- **日期**：2025-10-14 16:03:40 -0700
- **作者**：Trivedhi
- **提交说明**：Hive: Fixing the trailing slash issue for the database paths in HMS
- **PR/Issue**：#14295

## 总体目的

在 Iceberg 的 Hive 集成中，当通过 `HiveCatalog` 创建表时，如果数据库（database）本身没有为表指定显式的存储位置（location），Iceberg 会使用数据库的位置（location URI）作为基础路径来构建表的位置。构建方式是 `String.format("%s/%s", databaseLocation, tableName)`。

问题在于：如果 Hive Metastore 中数据库的 location URI 带有尾部斜杠（trailing slash），例如 `s3://bucket/database.db/`，那么构建出来的表位置会变成 `s3://bucket/database.db//test_table`——出现双斜杠。这种非规范化的路径可能导致多种问题：
- 某些文件系统或存储服务对双斜杠路径的处理不一致
- 路径比较和规范化逻辑失效
- 日志和元数据中出现不一致的路径表示

本提交的目的是在构建表位置之前，先使用 `LocationUtil.stripTrailingSlash()` 移除数据库位置 URI 的尾部斜杠，确保生成的表位置路径规范、不含多余斜杠。

## 如何达成设计目的

修改简单而精确：
1. 在 `HiveCatalog` 中构建表位置时，先用 `LocationUtil.stripTrailingSlash()` 处理数据库位置 URI
2. 再用处理后的位置拼接表名
3. 添加测试用例验证带尾部斜杠的数据库位置能正确生成表位置

`LocationUtil.stripTrailingSlash()` 是 Iceberg 中已有的工具方法，用于移除路径末尾的斜杠，在多个存储集成模块中已广泛使用。

## 修改详情

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveCatalog.java` (+2/-1 lines)

**修改目的**：修复表位置构建时的尾部斜杠问题。

**工作逻辑**：在 `defaultTableLocation` 方法中（约第 707 行），将：
```java
return String.format("%s/%s", databaseData.getLocationUri(), tableIdentifier.name());
```
改为：
```java
String databaseLocation = LocationUtil.stripTrailingSlash(databaseData.getLocationUri());
return String.format("%s/%s", databaseLocation, tableIdentifier.name());
```
先通过 `LocationUtil.stripTrailingSlash` 移除数据库位置 URI 的尾部斜杠，再拼接表名，确保不会产生双斜杠路径。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveCatalog.java` (+36/-0 lines)

**修改目的**：添加测试验证尾部斜杠修复。

**工作逻辑**：新增 `testTableLocationWithTrailingSlashInDatabaseLocation` 测试方法：
1. 创建一个 location 带尾部斜杠的数据库（`dbLocationWithSlash = temp.resolve(dbName) + "/"`）
2. 在该数据库下创建表
3. 断言表位置不含 `//test_table`（无双斜杠）
4. 断言表位置以 `/test_table` 结尾
5. 断言表位置路径与预期规范化路径一致（`temp.resolve(dbName) + "/test_table"`）
6. 在 finally 块中清理测试表和数据库

## 总结

本提交修复了 Hive Metastore 中数据库位置 URI 带尾部斜杠时导致表位置出现双斜杠的问题。修复方式是在拼接表位置前使用已有的 `LocationUtil.stripTrailingSlash()` 移除尾部斜杠。改动小而精准，并附带完整的测试用例验证修复效果。这类路径规范化问题在跨存储系统集成中很常见，及时修复有助于避免路径不一致引发的后续问题。
