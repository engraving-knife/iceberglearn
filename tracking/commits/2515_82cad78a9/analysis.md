# 提交 2515：backport #13821. (#13849)

## 提交信息

- **序号**：2515 / 4088
- **哈希**：82cad78a9ad6e413a2047bef33b08ae8bfcc856f
- **短哈希**：82cad78a9
- **日期**：2025-08-17 23:51:04 -0700
- **作者**：slfan1989
- **提交说明**：backport #13821. (#13849)
- **PR/Issue**：#13849（backport of #13821）
- **原PR**：#13821（提交 2511）

## 总体目的

此提交将 PR #13821（提交 2511）的修复回溯（backport）到 Flink v1.19 和 v1.20 版本。原 PR 修复了 `JdbcLockFactory.initializeLockTables()` 方法中的 `ResultSet` 和 `PreparedStatement` 资源泄漏问题，但仅应用于 Flink v2.0 版本。

由于 Iceberg 项目同时维护多个 Flink 版本（v1.19、v1.20、v2.0），每个版本有独立的代码副本，因此修复需要在所有受影响的版本中同步应用。此回溯提交确保 v1.19 和 v1.20 版本也获得相同的资源泄漏修复。

## 如何达成设计目的

回溯提交将完全相同的修复代码应用到 Flink v1.19 和 v1.20 的 `JdbcLockFactory.java` 文件中。修复内容与提交 2511 完全一致：

1. 使用 try-with-resources 包裹 `ResultSet`，确保数据库元数据查询结果集被正确关闭
2. 使用 try-with-resources 包裹 `PreparedStatement`，确保建表语句的预处理对象被正确关闭
3. 调整返回逻辑，在建表执行后显式返回 `true`

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/JdbcLockFactory.java` (+11/-8 lines)

**修改目的**：将资源泄漏修复应用到 Flink v1.19 版本。

**工作逻辑**：与提交 2511 相同的修复逻辑——将 `ResultSet` 和 `PreparedStatement` 包裹在 try-with-resources 中。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/JdbcLockFactory.java` (+11/-8 lines)

**修改目的**：将资源泄漏修复应用到 Flink v1.20 版本。

**工作逻辑**：与提交 2511 相同的修复逻辑。

## 总结

此提交是提交 2511 的回溯版本，将 JdbcLockFactory 资源泄漏修复同步到 Flink v1.19 和 v1.20 版本。这确保了所有支持的 Flink 版本都受益于该 bug 修复，保持了多版本维护的一致性。
