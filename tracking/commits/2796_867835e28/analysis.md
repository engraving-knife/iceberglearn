# 提交 2796：Core: Use time-travel schema when resolving partition spec in scan (#13301)

## 提交信息

- **序号**：2796 / 4088
- **哈希**：867835e2829f5ee5ecbd84ae8098d484cf9eed0e
- **短哈希**：867835e28
- **日期**：2025-10-27 14:39:14 +0100
- **作者**：chenjian2664
- **提交说明**：Core: Use time-travel schema when resolving partition spec in scan (#13301)
- **PR/Issue**：#13301

## 总体目的

本提交修复了 Iceberg 在时间旅行（time-travel）扫描时使用错误 schema 解析分区规范的问题。

当用户执行时间旅行扫描（通过 `useSnapshot()` 指定历史快照）时，扫描操作应使用该历史快照对应时刻的表 schema 来解析分区规范。然而，之前代码中直接使用 `table().specs()` 获取的是当前表的分区规范，这些规范是基于当前（最新）schema 绑定的。如果表经历了 schema 演进（如列重命名、列删除、分区字段变更等），使用当前 schema 绑定的分区规范去解析历史快照数据会导致错误，例如过滤条件无法正确匹配分区字段。

典型场景包括：列重命名后用旧列名过滤历史快照、列删除后用旧列名过滤历史快照、分区字段源列被删除等。在这些情况下，历史快照的分区数据是按旧 schema 写入的，必须用旧 schema 来正确解析分区规范。

## 如何达成设计目的

核心思路是在 `SnapshotScan` 中新增 `specs()` 方法，该方法判断当前是否为时间旅行扫描，如果是则使用历史快照的 schema 重新绑定分区规范：

1. 在 `SnapshotScan` 中新增 `protected specs()` 方法，默认返回 `table().specs()`（当前分区规范）。
2. 当满足时间旅行条件（`useSnapshotSchema()` 为 true、指定了 snapshotId、且不是当前快照）时，获取历史快照的 schema，将所有分区规范用 `toUnbound().bind(snapshotSchema)` 重新绑定到历史 schema。
3. 将 `BaseDistributedDataScan`、`DataScan`、`DataTableScan` 中原来调用 `table().specs()` 的地方改为调用 `specs()`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SnapshotScan.java` (+22/-0 lines)

**修改目的**：新增 `specs()` 方法，在时间旅行扫描时使用历史 schema 重新绑定分区规范。

**工作逻辑**：`specs()` 方法首先获取当前表的分区规范。如果不需要使用快照 schema、未指定 snapshotId、或 snapshotId 就是当前快照的 ID，则直接返回当前分区规范。否则，获取历史快照的 schema（通过 `tableSchema()`），遍历所有分区规范，使用 `toUnbound().bind(snapshotSchema)` 将每个分区规范解绑后重新绑定到历史 schema，返回新的分区规范映射。

### `core/src/main/java/org/apache/iceberg/BaseDistributedDataScan.java` (+1/-1 lines)

**修改目的**：将 `table().specs()` 改为 `specs()`，使用时间旅行感知的分区规范。

**工作逻辑**：在构建扫描任务时，将 `.specsById(table().specs())` 改为 `.specsById(specs())`，使分布式数据扫描在时间旅行时使用正确的分区规范。

### `core/src/main/java/org/apache/iceberg/DataScan.java` (+1/-1 lines)

**修改目的**：将 `table().specs()` 改为 `specs()`。

**工作逻辑**：同上，在数据扫描中使用时间旅行感知的分区规范。

### `core/src/main/java/org/apache/iceberg/DataTableScan.java` (+1/-1 lines)

**修改目的**：将 `table().specs()` 改为 `specs()`。

**工作逻辑**：同上，在数据表扫描中使用时间旅行感知的分区规范。

### `core/src/test/java/org/apache/iceberg/TestScansAndSchemaEvolution.java` (+142/-7 lines)

**修改目的**：添加时间旅行扫描与 schema 演进组合场景的测试。

**工作逻辑**：
- 重构 `createDataFile` 方法，支持传入自定义 schema 和分区规范。
- 新增 `testPartitionSourceDrop` 测试：验证分区源列被删除后，时间旅行扫描仍能正确过滤历史快照数据。
- 新增 `testColumnRename` 测试：验证列重命名后，时间旅行扫描能用旧列名过滤历史快照、用新列名过滤重命名后的快照。
- 新增 `testColumnDrop` 测试：验证列删除后，时间旅行扫描仍能用旧列名过滤历史快照。
- 在已有测试中增加时间旅行场景验证。

## 总结

本提交修复了时间旅行扫描中使用当前 schema 解析分区规范导致的问题。通过在 `SnapshotScan` 中新增 `specs()` 方法，在时间旅行场景下使用历史快照的 schema 重新绑定分区规范，确保 schema 演进后仍能正确解析历史快照的分区数据。新增了多个测试覆盖列重命名、列删除、分区字段删除等场景。
