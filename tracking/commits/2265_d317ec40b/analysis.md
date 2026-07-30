# 提交 2265：Flink: Backport revise the display of the task name in TableMaintenance to show the specific task name to Flink 1.20, 1.19 (#13372)

## 提交信息

- **序号**：2265 / 4088
- **哈希**：d317ec40b68aa4f008f4db95e42ff9f076d0d9e4
- **短哈希**：d317ec40b
- **日期**：2025-06-24 11:00:43 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Backport revise the display of the task name in TableMaintenance to show the specific task name to Flink 1.20, 1.19
- **PR/Issue**：#13372 (backports #13024)

## 总体目的

本提交将提交 2264 中的 TableMaintenance 任务名称显示改进回移植到 Flink 1.19 和 1.20 模块。与提交 2264 相同，改动目的是将 Flink 维护任务名称从使用 Builder 类的简单类名改为使用每个任务自定义的简洁名称（如 "ExpireSnapshots"、"RewriteDataFiles"），以改善任务在 Flink Web UI 和日志中的可观测性。

## 如何达成设计目的

- 在 flink/v1.19 和 flink/v1.20 两个模块中应用与提交 2264 完全相同的改动。
- 在 `MaintenanceTaskBuilder` 中新增 `maintenanceTaskName()` 抽象方法。
- 在 `ExpireSnapshots.Builder` 和 `RewriteDataFiles.Builder` 中实现该方法。
- 修改 `TableMaintenance.nameFor()` 使用新方法。
- 更新测试断言。

## 修改详情

### `flink/v1.19/flink/...` 和 `flink/v1.20/flink/...` (各 5 个文件, 共 +42/-16 lines)

两个模块的改动完全对称，每个模块修改以下 5 个文件：

1. **`MaintenanceTaskBuilder.java`** (+2 lines)：新增 `abstract String maintenanceTaskName();` 抽象方法。
2. **`ExpireSnapshots.java`** (+5 lines)：实现 `maintenanceTaskName()` 返回 `"ExpireSnapshots"`。
3. **`RewriteDataFiles.java`** (+5 lines)：实现 `maintenanceTaskName()` 返回 `"RewriteDataFiles"`。
4. **`TableMaintenance.java`** (+3/-2 lines)：`nameFor()` 方法使用 `streamBuilder.maintenanceTaskName()` 替代 `streamBuilder.getClass().getSimpleName()`。
5. **`TestTableMaintenance.java`** (+14/-6 lines)：更新测试断言为新的任务名称格式。

## 总结

本提交是提交 2264 的回移植，将 TableMaintenance 任务名称显示改进同步到 Flink 1.19 和 1.20 模块。两个模块的改动与 Flink 2.0 完全一致，确保所有 Flink 版本的维护任务命名行为统一。共修改 10 个文件，属于一致性维护提交。
