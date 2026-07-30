# 提交 1890：Flink: backport for fix read config of connector.iceberg.max-allowed-planning-failures (#12589)

## 提交信息

- **序号**：1890 / 4088
- **哈希**：6779a15759668552665988eab4bcf6c2f8f5bd3b
- **短哈希**：6779a1575
- **日期**：2025-03-20 16:02:37 +0100
- **作者**：GuoYu
- **提交说明**：Flink: backport for fix read config of connector.iceberg.max-allowed-planning-failures (#12589)
- **PR/Issue**：#12589

## 总体目的

这个提交是前一个提交（1889, #12585）的向后移植（backport），将 `connector.iceberg.max-allowed-planning-failures` 配置读取修复应用到 Flink 1.18 和 1.19 版本的连接器中。

前一个提交 #12585 仅修复了 Flink 1.20 版本中的 `ScanContext.java`。由于 Iceberg Flink 连接器同时维护多个 Flink 版本（v1.18、v1.19、v1.20），每个版本都有自己独立的代码副本，因此需要将修复同步到其他仍在维护的版本中。本提交将同样的修复应用到 v1.18 和 v1.19 两个版本。

## 如何达成设计目的

与提交 #12585 完全相同的修复方式，在 Flink 1.18 和 1.19 版本的 `ScanContext.java` 中将 `.maxAllowedPlanningFailures(maxAllowedPlanningFailures)` 改为 `.maxAllowedPlanningFailures(flinkReadConf.maxAllowedPlanningFailures())`。

## 修改详情

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/source/ScanContext.java` (修改, +1/-1 lines)

**修改目的**：将 `max-allowed-planning-failures` 配置修复应用到 Flink 1.18 版本。

**工作逻辑**：与 #12585 相同的修复，将局部变量引用改为直接从 `FlinkReadConf` 读取配置值。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/source/ScanContext.java` (修改, +1/-1 lines)

**修改目的**：将 `max-allowed-planning-failures` 配置修复应用到 Flink 1.19 版本。

**工作逻辑**：与 #12585 相同的修复。

## 总结

本提交是 #12585 的向后移植，将 `max-allowed-planning-failures` 配置读取修复同步到 Flink 1.18 和 1.19 版本，确保所有支持的 Flink 版本都能正确读取用户配置的规划失败容错次数。
