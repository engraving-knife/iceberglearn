# 提交 2264：Flink: Revise the display of the task name in TableMaintenance to show the specific task name. (#13024)

## 提交信息

- **序号**：2264 / 4088
- **哈希**：6e432fcb24fd55c9024a3192b17a364623886047
- **短哈希**：6e432fcb2
- **日期**：2025-06-23 11:55:05 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Revise the display of the task name in TableMaintenance to show the specific task name.
- **PR/Issue**：#13024

## 总体目的

本提交改进了 Flink TableMaintenance 中任务名称的显示方式。在原有实现中，`TableMaintenance` 使用 `streamBuilder.getClass().getSimpleName()` 作为任务名称显示在 Flink 作业中。这导致显示的是 Builder 类的简单类名（如 "ExpireSnapshots$ExpireSnapshotsBuilder" 或 "RewriteDataFiles$RewriteDataFilesBuilder"），而非简洁的、有意义的任务名称。

对于运维人员和开发者来说，在 Flink Web UI 或日志中看到冗长且不直观的 Builder 类名不利于快速识别当前运行的是哪种维护任务。本提交通过在 `MaintenanceTaskBuilder` 中新增 `maintenanceTaskName()` 抽象方法，让每个维护任务返回其简洁名称（如 "ExpireSnapshots"、"RewriteDataFiles"），从而改善任务的可观测性。

## 如何达成设计目的

- 在 `MaintenanceTaskBuilder` 抽象类中新增 `abstract String maintenanceTaskName()` 方法。
- 在 `ExpireSnapshots.Builder` 和 `RewriteDataFiles.Builder` 中分别实现该方法，返回 "ExpireSnapshots" 和 "RewriteDataFiles"。
- 修改 `TableMaintenance.nameFor()` 方法，使用 `streamBuilder.maintenanceTaskName()` 替代 `streamBuilder.getClass().getSimpleName()`。
- 更新测试以验证新的任务名称格式。

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/MaintenanceTaskBuilder.java` (修改, +2/0 lines)

**修改目的**：声明任务名称的抽象方法。

**工作逻辑**：新增 `abstract String maintenanceTaskName();` 抽象方法，强制所有维护任务 Builder 子类实现，返回该任务的简洁名称字符串。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/ExpireSnapshots.java` (修改, +5/0 lines)

**修改目的**：为快照过期任务提供名称。

**工作逻辑**：在 `ExpireSnapshots.Builder` 中实现 `maintenanceTaskName()` 方法，返回字符串 `"ExpireSnapshots"`。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/RewriteDataFiles.java` (修改, +5/0 lines)

**修改目的**：为数据文件重写任务提供名称。

**工作逻辑**：在 `RewriteDataFiles.Builder` 中实现 `maintenanceTaskName()` 方法，返回字符串 `"RewriteDataFiles"`。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/TableMaintenance.java` (修改, +3/-2 lines)

**修改目的**：使用新的任务名称方法替代类名。

**工作逻辑**：将 `nameFor()` 方法中的 `streamBuilder.getClass().getSimpleName()` 改为 `streamBuilder.maintenanceTaskName()`，使得 Flink 作业中显示的任务名称为 "ExpireSnapshots [0]" 或 "RewriteDataFiles [0]" 等简洁格式。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestTableMaintenance.java` (修改, +14/-6 lines)

**修改目的**：更新测试以验证新的任务名称格式。

**工作逻辑**：将测试中对任务名称的断言从 Builder 类名改为期望的简洁任务名（如 "ExpireSnapshots" 和 "RewriteDataFiles"）。

## 总结

本提交改善了 Flink TableMaintenance 的任务命名显示，从使用 Builder 类的简单类名改为使用每个任务自定义的简洁名称。这是一个可观测性改进，使得在 Flink Web UI 和日志中更容易识别维护任务的类型。改动简洁明确，通过抽象方法模式让每个维护任务自行决定其显示名称。
