# 提交 3963：Flink: Backport: Fix monitor source rate limit for sub-second intervals (#16979) (#16992)

## 提交信息

- **序号**：3963 / 4088
- **哈希**：958864156d92e56d93b8cde45e21364c05d1ee30
- **短哈希**：958864156
- **日期**：2026-06-28 08:24:30 -0700
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport: Fix monitor source rate limit for sub-second intervals (#16979) (#16992)
- **PR/Issue**：#16979, #16992

## 总体目的

本提交是 PR #16979（提交 3958）的 backport，将 monitor source 亚秒级速率限制修复同步到 Flink v1.20 和 v2.0 模块。原修复仅应用于 flink v2.1，但维护版本的 Flink 模块也需要同样的 bug 修复。

## 如何达成设计目的

将 v2.1 模块中的修复（`monitorRatePerSecond` 方法和相关测试）原样复制到 v1.20 和 v2.0 模块中。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/TableMaintenance.java` (+14/-1 lines)

**修改目的**：将速率计算从 `getSeconds()` 改为基于 `toMillis()`。

**工作逻辑**：与提交 3958 完全相同的修复，新增 `monitorRatePerSecond(long rateLimitMillis)` 方法返回 `1000.0 / rateLimitMillis`。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestTableMaintenance.java` (+8/-0 lines)

**修改目的**：验证亚秒级速率计算。

### `flink/v2.0/flink/src/main/java/.../TableMaintenance.java` 和测试文件 (+14/-1 lines, +8/-0 lines)

**修改目的**：v2.0 模块的同样修复。

## 总结

标准 backport 操作，将 v2.1 的 bug 修复同步到 v1.20 和 v2.0 模块，确保所有维护的 Flink 版本都有正确的亚秒级速率限制行为。
