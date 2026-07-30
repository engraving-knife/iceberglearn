# 提交 2284：Fix race condition in JdbcCatalog (#13345)

## 提交信息

- **序号**：2284 / 4088
- **哈希**：83cc7d8749f2ba7ed7f0bff09e7bcf10d77785e8
- **短哈希**：83cc7d874
- **日期**：2025-06-27 23:14:23 +0200
- **作者**：Claude Warren
- **提交说明**：Fix race condition in `JdbcCatalog` (#13345)
- **PR/Issue**：#13345（修复 ICEBERG-13343）

## 总体目的

本提交修复了 `JdbcCatalog` 在初始化时创建 catalog 表的竞态条件（race condition，ICEBERG-13343）。`JdbcCatalog` 在初始化时需要创建两张数据库表（`iceberg_tables` 和 `iceberg_namespace_properties`），原实现采用"先检查表是否存在，不存在则创建"的 check-then-act 模式。

问题在于：当多个 JdbcCatalog 实例（或多个线程）同时初始化并连接同一数据库时，两个实例可能同时检查到表不存在，然后都尝试创建表，导致其中一个因表已存在而抛出 `SQLException` 失败。原代码未处理这种并发创建冲突。此外，原实现依赖数据库的 `IF NOT EXISTS` 语法或简单的存在性检查，但并非所有数据库都支持 `IF NOT EXISTS`，且检查时未考虑某些数据库强制将表名转为大写的特性。

本提交通过引入 `atomicCreateTable` 方法，采用"尝试创建，失败后复查"的模式处理竞态：先检查表是否存在，不存在则尝试创建；若创建时抛出 `SQLException`，再次检查表是否已被其他线程/进程创建，若已存在则视为成功，否则重新抛出异常。同时增加了大写表名检查（适配强制大写的数据库）。

## 如何达成设计目的

- 新增 `atomicCreateTable(String tableName, String sqlCommand, String reason)` 方法，封装原子化建表逻辑：
  1. 定义 `tableTest` 谓词：通过 `DatabaseMetaData.getTables()` 检查表是否存在。
  2. 定义 `tableExists` 谓词：检查原始表名和大写表名（适配强制大写的数据库）。
  3. 若表已存在，直接返回 true。
  4. 若不存在，尝试执行建表 SQL；若抛出 `SQLException`，复查表是否已被其他线程创建，若已存在则返回 true，否则重新抛出异常。
- `initializeCatalogTables()` 改为调用 `atomicCreateTable` 创建两张表，消除重复代码。
- 新增 Apache Derby 数据库依赖（derby-core、derby-tools），用于并发测试。
- 新增 `TestJdbcTableConcurrency.java`（1052 行）并发测试，验证多线程下建表的正确性。

## 修改详情

### `core/src/main/java/org/apache/iceberg/jdbc/JdbcCatalog.java` (+53/-38 lines)

**修改目的**：消除建表时的竞态条件，支持并发初始化。

**工作逻辑**：

新增 `atomicCreateTable` 方法：
```java
private void atomicCreateTable(String tableName, String sqlCommand, String reason)
    throws SQLException, InterruptedException {
  connections.run(conn -> {
    DatabaseMetaData dbMeta = conn.getMetaData();
    // 检查表名是否存在（含大写检查）
    Predicate<String> tableTest = name -> { ... dbMeta.getTables(..., name, ...) ... };
    Predicate<String> tableExists = name -> tableTest.test(name) || tableTest.test(name.toUpperCase(Locale.ROOT));
    if (tableExists.test(tableName)) { return true; }  // 已存在
    try {
      conn.prepareStatement(sqlCommand).execute();  // 尝试建表
      return true;
    } catch (SQLException e) {
      if (tableExists.test(tableName)) { return true; }  // 被其他线程创建了
      throw e;  // 真正的错误
    }
  });
}
```

`initializeCatalogTables()` 简化为两次 `atomicCreateTable` 调用：
```java
atomicCreateTable(JdbcUtil.CATALOG_TABLE_VIEW_NAME, JdbcUtil.V0_CREATE_CATALOG_SQL, "to store iceberg catalog tables");
atomicCreateTable(JdbcUtil.NAMESPACE_PROPERTIES_TABLE_NAME, JdbcUtil.CREATE_NAMESPACE_PROPERTIES_TABLE_SQL, "to store iceberg catalog namespace properties");
```

原代码中两段近乎重复的"检查存在 → 创建"逻辑被合并为一个通用方法。关键改进：建表失败后复查表是否存在，处理了两个线程同时建表的竞态——其中一个线程建表成功，另一个线程建表失败但复查发现表已存在，从而正确返回而非抛异常。

### `build.gradle` (+2/0 lines)

**修改目的**：为 core 模块测试添加 Derby 数据库依赖。

**工作逻辑**：在 `iceberg-core` 项目的测试依赖中新增 `testImplementation libs.derby.core` 和 `testImplementation libs.derby.tools`，用于并发测试中使用 Derby 嵌入式数据库验证多线程建表场景。

### `gradle/libs.versions.toml` (+3/0 lines)

**修改目的**：在版本目录中注册 Derby 依赖。

**工作逻辑**：新增 `derby = "10.15.2.0"` 版本变量，以及 `derby-core`（`org.apache.derby:derby`）和 `derby-tools`（`org.apache.derby:derbytools`）两个库坐标。

### `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcTableConcurrency.java` (新增, +1052/0 lines)

**修改目的**：验证 JdbcCatalog 在并发场景下建表的正确性。

**工作逻辑**：使用 Derby 嵌入式数据库，模拟多线程/多 catalog 实例同时初始化并创建表的场景，验证不会因竞态条件导致异常，且表最终被正确创建。测试覆盖多线程并发初始化、表已存在时的重复初始化等场景。

## 总结

本提交通过"尝试创建，失败后复查"的模式修复了 JdbcCatalog 初始化时的建表竞态条件，同时增加了大写表名检查以适配更多数据库。新增的 `atomicCreateTable` 方法消除了原代码的重复，配套的 Derby 并发测试（1052 行）确保修复的正确性。这是一个重要的并发安全修复，对多实例共享同一 JDBC catalog 的生产场景至关重要。
