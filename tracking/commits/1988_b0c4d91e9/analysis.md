# 提交 1988：Flink: backport fix rateLimit argument check in TableMaintenance (#12776)

## 提交信息

- **序号**：1988 / 4088
- **哈希**：b0c4d91e9194c2d7da7224bd67a9a194fa7b4968
- **短哈希**：b0c4d91e9
- **日期**：2025-04-12 00:04:50 +0200
- **作者**：GuoYu
- **提交说明**：Flink: backport fix rateLimit argument check in TableMaintenance (#12776) / Backports #12773
- **PR/Issue**：#12776（Backports #12773）

## 总体目的

本提交是 #12773（即提交 1987）的反向移植，将相同的 `rateLimit` 参数校验修复应用到 Flink v1.19 模块。

与 1987 相同的缺陷：`TableMaintenance.Builder.rateLimit(Duration)` 中原代码 `Preconditions.checkNotNull(rateLimit.toMillis() > 0, "Rate limit should be greater than 0")` 误用了 `checkNotNull` 校验布尔条件（恒通过）、引用了字段 `rateLimit` 而非参数 `newRateLimit`、且存在 NPE 风险。本提交在 Flink 1.19 模块做完全一致的修复，保证两个支持的 Flink 版本行为一致。

## 如何达成设计目的

与 1987 完全相同的修复方式，仅目标文件位于 `flink/v1.19` 模块。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/TableMaintenance.java` (修改, +3/-1 lines)

**修改目的**：在 Flink v1.19 模块修正 `rateLimit` 方法的参数校验。

**工作逻辑**：将 `Preconditions.checkNotNull(rateLimit.toMillis() > 0, "Rate limit should be greater than 0")` 替换为：`Preconditions.checkNotNull(newRateLimit, "Rate limit should not be null")` + `Preconditions.checkArgument(newRateLimit.toMillis() > 0, "Rate limit should be greater than 0")`，正确引用参数并使用恰当的校验方法。

## 总结

本提交将 #12773 的 `rateLimit` 参数校验修复反向移植到 Flink v1.19 模块，与 1987（v1.20）的改动完全一致：先 `checkNotNull` 判空、再 `checkArgument` 判正，并引用参数 `newRateLimit`。仅 3 行改动，确保两个 Flink 版本行为统一。
