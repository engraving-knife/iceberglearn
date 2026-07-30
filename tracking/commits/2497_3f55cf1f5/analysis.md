# 提交 2497：Docs: fix typo in JdbcLockFactory Javadoc (#13811)

## 提交信息

- **序号**：2497 / 4088
- **哈希**：3f55cf1f5e5cccfbeec0b144b8e914ffe78d8407
- **短哈希**：3f55cf1f5
- **日期**：2025-08-13 21:10:48 -0700
- **作者**：slfan1989
- **提交说明**：Docs: fix typo in JdbcLockFactory Javadoc (#13811)
- **PR/Issue**：#13811

## 总体目的

本提交修复了 `JdbcLockFactory` 类 Javadoc 注释中的一处拼写错误。原注释中 `@param lockId which should indentify the job and the table` 的 "indentify" 是 "identify" 的拼写错误。

虽然这只是一处文档拼写问题，但 Javadoc 是开发者理解 API 用法的重要参考，拼写错误会降低文档的专业性和可读性。该修改同时应用于 Flink v1.19、v1.20 和 v2.0 三个版本的代码，保持一致性。

## 如何达成设计目的

直接将 Javadoc 中的 "indentify" 修正为 "identify"。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/JdbcLockFactory.java` (+1/-1 lines)

**修改目的**：修复 Javadoc 拼写错误。

**工作逻辑**：将构造函数 `JdbcLockFactory` 的 `@param lockId` 注释中 "indentify" 改为 "identify"。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/JdbcLockFactory.java` (+1/-1 lines)

**修改目的**：与 v1.19 相同的拼写修复，保持版本一致。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/JdbcLockFactory.java` (+1/-1 lines)

**修改目的**：与 v1.19 相同的拼写修复，保持版本一致。

## 总结

本提交是一个纯粹的文档拼写修复，不涉及任何代码逻辑变更。虽然改动微小，但保持了三个 Flink 版本 Javadoc 的一致性和专业性。
