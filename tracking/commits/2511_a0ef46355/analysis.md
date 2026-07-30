# 提交 2511：Flink: Fix ResultSet resource leak in JdbcLockFactory.initializeLockTables(). (#13821)

## 提交信息

- **序号**：2511 / 4088
- **哈希**：a0ef46355b809d33b83f3e28eec2795051c4578d
- **短哈希**：a0ef46355
- **日期**：2025-08-17 21:02:16 -0700
- **作者**：slfan1989
- **提交说明**：Flink: Fix ResultSet resource leak in JdbcLockFactory.initializeLockTables(). (#13821)
- **PR/Issue**：#13821

## 总体目的

此提交修复了 Flink 维护模块中 `JdbcLockFactory.initializeLockTables()` 方法的 JDBC 资源泄漏问题。在原始代码中，`ResultSet` 和 `PreparedStatement` 对象没有被正确关闭，可能导致数据库连接资源泄漏。

`JdbcLockFactory` 是 Flink 维护 API 中用于实现基于 JDBC 的分布式锁的工厂类。`initializeLockTables()` 方法负责初始化锁表——首先检查锁表是否已存在，如果不存在则创建它。在这个方法中，通过 `DatabaseMetaData.getTables()` 查询表是否存在时返回的 `ResultSet`，以及创建表时使用的 `PreparedStatement`，都没有使用 try-with-resources 语句来确保资源被释放。

资源泄漏在长期运行的 Flink 作业中尤其危险，因为维护任务会反复获取和释放锁，每次泄漏的资源会逐渐累积，最终可能导致连接池耗尽或数据库连接数超限。

## 如何达成设计目的

修复方案采用 Java 7 引入的 try-with-resources 语句，确保 `ResultSet` 和 `PreparedStatement` 在使用完毕后自动关闭。具体设计要点：

1. 将 `ResultSet` 的获取包裹在 try-with-resources 中，确保无论查询是否抛出异常，ResultSet 都会被关闭
2. 将创建表用的 `PreparedStatement` 也包裹在独立的 try-with-resources 中
3. 调整方法返回逻辑：原来直接返回 `execute()` 的布尔结果，现在改为在 PreparedStatement 执行后显式返回 `true`，因为建表操作成功即可认为初始化成功

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/JdbcLockFactory.java` (+11/-8 lines)

**修改目的**：修复 ResultSet 和 PreparedStatement 资源泄漏。

**工作逻辑**：

原始代码流程：
1. 获取 `DatabaseMetaData` 并调用 `getTables()` 获取 `ResultSet`
2. 检查 `ResultSet.next()` 判断表是否存在
3. 如果表已存在，返回 `true`
4. 如果表不存在，执行 `CREATE_LOCK_TABLE_SQL` 并返回 `execute()` 结果

问题在于 `ResultSet` 和 `PreparedStatement` 都没有被关闭。

修复后代码流程：
1. 使用 try-with-resources 包裹 `ResultSet`，查询表是否存在
2. 如果表已存在，在 try 块内返回 `true`，ResultSet 会自动关闭
3. 如果表不存在，使用另一个 try-with-resources 包裹 `PreparedStatement` 执行建表 SQL
4. 建表执行后，显式返回 `true`

关键变更：
- `ResultSet tableExists = dbMeta.getTables(...)` 改为 `try (ResultSet rs = dbMeta.getTables(...))`
- `tableExists.next()` 改为 `rs.next()`
- `conn.prepareStatement(CREATE_LOCK_TABLE_SQL).execute()` 改为 `try (PreparedStatement ps = conn.prepareStatement(CREATE_LOCK_TABLE_SQL)) { ps.execute(); }` 后返回 `true`

## 总结

此提交修复了一个重要的资源泄漏 bug，通过使用 Java 标准的 try-with-resources 语句确保 JDBC 资源被正确释放。这在长期运行的 Flink 维护任务场景中尤为关键，可防止连接池耗尽和数据库连接泄漏。修复方式简洁有效，符合 Java 最佳实践。
