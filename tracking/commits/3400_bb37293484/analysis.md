# 提交 3400：Fix JDBC resource leaks in JdbcCatalog and JdbcUtil (#15463)

## 提交信息

- **序号**：3400 / 4088
- **哈希**：bb37293484468f0502de9986955860505aef9776
- **短哈希**：bb37293484
- **日期**：2026-03-16 17:12:26 -0700
- **作者**：ZIHAN DAI
- **提交说明**：Fix JDBC resource leaks in JdbcCatalog and JdbcUtil (#15463)
- **PR/Issue**：#15463, 关联 #15462

## 总体目的

修复 JdbcCatalog 和 JdbcUtil 中的 JDBC 资源泄漏问题。在原有的代码中，`ResultSet` 和 `PreparedStatement` 对象没有使用 try-with-resources 语句管理，当异常发生时这些资源不会被关闭，导致数据库连接泄漏。具体涉及 `JdbcUtil.tableOrView()`、`JdbcCatalog.atomicCreateTable()` 和 `JdbcCatalog.updateSchemaIfRequired()` 三个方法。

## 如何达成设计目的

1. 在 `JdbcUtil.tableOrView()` 中，将 `ResultSet` 改为 try-with-resources 管理，移除手动 `rs.close()` 调用
2. 在 `JdbcCatalog.atomicCreateTable()` 中，将表名检查的 `ResultSet` 和 SQL 执行的 `PreparedStatement` 都改为 try-with-resources
3. 在 `JdbcCatalog.updateSchemaIfRequired()` 中，将检查列类型的 `ResultSet` 改为 try-with-resources，并提取嵌套的 PreparedStatement 执行逻辑到辅助方法 `executeV1CatalogUpdate` 以满足 checkstyle 要求

## 修改详情

### `core/src/main/java/org/apache/iceberg/jdbc/JdbcCatalog.java` (+58/-39 lines)

**修改目的**：修复 `atomicCreateTable` 和 `updateSchemaIfRequired` 方法中的资源泄漏。

**工作逻辑**：

**atomicCreateTable 方法**：
- 表名检查的 `ResultSet` 从 `ResultSet result = dbMeta.getTables(...)` 改为 `try (ResultSet result = dbMeta.getTables(...)) {`
- SQL 执行从 `conn.prepareStatement(sqlCommand).execute()` 改为 `try (PreparedStatement stmt = conn.prepareStatement(sqlCommand)) { stmt.execute(); }`

**updateSchemaIfRequired 方法**：
- 检查列类型的 `ResultSet` 从直接赋值改为 try-with-resources
- 将嵌套在 try 块中的 `conn.prepareStatement(JdbcUtil.V1_UPDATE_CATALOG_SQL).execute()` 提取到独立的辅助方法 `executeV1CatalogUpdate`，以避免嵌套的 try-with-resources 导致 checkstyle 失败
- 新增 `import java.sql.Connection;`

**executeV1CatalogUpdate 辅助方法**：
```java
private static boolean executeV1CatalogUpdate(Connection conn) throws SQLException {
    try (PreparedStatement stmt = conn.prepareStatement(JdbcUtil.V1_UPDATE_CATALOG_SQL)) {
      return stmt.execute();
    }
}
```

### `core/src/main/java/org/apache/iceberg/jdbc/JdbcUtil.java` (+26/-15 lines)

**修改目的**：修复 `tableOrView` 方法中的 `ResultSet` 资源泄漏。

**工作逻辑**：
- 将 `ResultSet rs = sql.executeQuery()` 改为 `try (ResultSet rs = sql.executeQuery()) { ... }`
- 移除末尾的手动 `rs.close()` 调用
- 将 `if (rs.next())` 块的内容移入 try-with-resources 块内

## 总结

本提交通过将 JDBC 的 `ResultSet` 和 `PreparedStatement` 对象改为 try-with-resources 管理，修复了 JdbcCatalog 和 JdbcUtil 中的资源泄漏问题。在异常发生时这些资源现在能被自动关闭，避免了数据库连接泄漏。为满足 checkstyle 规范，提取了一个辅助方法处理嵌套的 try-with-resources。
