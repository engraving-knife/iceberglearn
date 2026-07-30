# 提交 1889：Flink: fix read config of connector.iceberg.max-allowed-planning-failures (#12585)

## 提交信息

- **序号**：1889 / 4088
- **哈希**：31e0f19dff9f6c91a8d95447598fa94e70c5cbdd
- **短哈希**：31e0f19df
- **日期**：2025-03-20 14:56:19 +0100
- **作者**：GuoYu
- **提交说明**：Flink: fix read config of connector.iceberg.max-allowed-planning-failures (#12585)
- **PR/Issue**：#12585

## 总体目的

这个提交修复了 Flink Iceberg 连接器中 `connector.iceberg.max-allowed-planning-failures` 配置项无法正确读取的问题。

`max-allowed-planning-failures` 是一个用于控制 Flink 读取 Iceberg 表时允许的最大规划失败次数的配置项。在 Flink 的增量读取场景中，当某个快照的文件规划失败时，系统可以跳过该快照并继续处理后续快照，这个配置控制了允许跳过的最大失败次数。

问题出在 `ScanContext` 的构建逻辑中，构建扫描上下文时使用了一个局部变量 `maxAllowedPlanningFailures`，而不是直接从 `FlinkReadConf` 中读取用户配置的值。这意味着用户在 Flink SQL 或 Table API 中设置的 `connector.iceberg.max-allowed-planning-failures` 配置值被忽略，实际使用的是某个默认值或上游传入的值，而非用户实际配置的值。

## 如何达成设计目的

修复非常直接：将 `ScanContext` 构建器中对 `maxAllowedPlanningFailures` 局部变量的引用，改为直接调用 `flinkReadConf.maxAllowedPlanningFailures()` 从 Flink 读取配置中获取值。这确保了用户通过 Flink 配置设置的 `max-allowed-planning-failures` 值能被正确传递到底层扫描逻辑。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/source/ScanContext.java` (修改, +1/-1 lines)

**修改目的**：修复 `max-allowed-planning-failures` 配置读取错误。

**工作逻辑**：
在 `ScanContext` 的构建方法中，`.maxAllowedPlanningFailures(maxAllowedPlanningFailures)` 被改为 `.maxAllowedPlanningFailures(flinkReadConf.maxAllowedPlanningFailures())`。原代码使用的是一个可能是默认值或未正确赋值的局部变量，修复后直接从 `FlinkReadConf` 获取用户配置的值，确保配置项 `connector.iceberg.max-allowed-planning-failures` 能正确生效。

## 总结

本提交修复了一个配置读取 bug，使 Flink 用户设置的 `connector.iceberg.max-allowed-planning-failures` 配置能正确传递到 Iceberg 扫描逻辑中。修复仅涉及一行代码变更，但确保了增量读取容错配置的正确性。
