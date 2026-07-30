# 提交 4066：Flink: BackPort Fix TableMaintenance operator uid instability that breaks savepoint restore (#17283)

## 提交信息

- **序号**：4066 / 4088
- **哈希**：25654ab4b29c8b5b5c20fc427da01cb70d94ed14
- **短哈希**：25654ab4b
- **日期**：2026-07-18 08:55:42 +0200
- **作者**：GuoYu
- **提交说明**：Flink: BackPort Fix TableMaintenance operator uid instability that breaks savepoint restore (#17283)
- **PR/Issue**：#17283（Backports #17210）

## 总体目的

这个提交是提交 4059（PR #17210，修复 TableMaintenance 算子 uid 不稳定导致 savepoint 恢复失败）的回移（backport），将相同的修复应用到 Flink 1.20 和 2.0 两个版本分支。

原始问题已在 4059 的分析中详述：`TableMaintenance` 的 `uidSuffix` 默认值使用随机 UUID，导致每次构建作业图时 uid 变化，从 savepoint 恢复时 Flink 无法匹配算子状态，恢复失败。修复将默认 uid 改为基于表名的稳定值。

由于 Iceberg 同时维护多个 Flink 版本分支（1.20、2.0、2.1），原始修复在 2.1 分支（4059）合入后，需要回移到仍被使用的 1.20 和 2.0 分支，确保所有维护版本都获得该修复。

## 如何达成设计目的

与 4059 完全相同的修复方案，应用于 `flink/v1.20` 和 `flink/v2.0` 两个目录下的 `TableMaintenance.java` 和 `TestTableMaintenance.java`：将 `uidSuffix` 默认值从随机 UUID 改为 null，在 `append()` 中基于表名生成稳定 uid，移除非空校验，新增两个测试用例。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/TableMaintenance.java` (+6/-3 lines)

**修改目的**：修复 Flink 1.20 的 uid 不稳定问题。

**工作逻辑**：与 4059 完全相同：
- `uidSuffix` 默认值从 `"TableMaintenance-" + UUID.randomUUID()` 改为 `null`。
- 移除 `append()` 中的 `Preconditions.checkNotNull(uidSuffix, ...)`。
- 在 `append()` 中 `if (uidSuffix == null) { this.uidSuffix = "TableMaintenance-" + tableName; }`。
- 移除 `import java.util.UUID;`。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestTableMaintenance.java` (+42/-0 lines)

**修改目的**：新增 uid 行为测试。

**工作逻辑**：与 4059 完全相同的两个测试：`testUidSuffixUserProvidedIsUsedAsIs` 和 `testUidSuffixDefaultContainsTableName`。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/TableMaintenance.java` (+6/-3 lines)

**修改目的**：修复 Flink 2.0 的 uid 不稳定问题。

**工作逻辑**：与上述 1.20 完全相同。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestTableMaintenance.java` (+42/-0 lines)

**修改目的**：新增 uid 行为测试。

**工作逻辑**：与上述 1.20 完全相同。

## 总结

这是 4059 提交的回移，将 TableMaintenance uid 不稳定修复应用到 Flink 1.20 和 2.0 两个维护版本分支，确保所有并行维护的 Flink 版本都能正确支持 savepoint 恢复。修复内容与原始提交完全一致。这体现了多版本并行维护中对关键 bug 修复及时回移的严谨做法。
