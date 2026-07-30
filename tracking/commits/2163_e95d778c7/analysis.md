# 提交 2163：Flink: Backport fix npe in TaskResultAggregator when job recovery (#13140)

## 提交信息

- **序号**：2163 / 4088
- **哈希**：e95d778c79212e99ea0b75e8bf93ff2d9c162b7b
- **短哈希**：e95d778c7
- **日期**：2025-05-26 15:18:36 +0800
- **作者**：GuoYu
- **提交说明**：Flink: Backport fix npe in TaskResultAggregator when job recovery (#13140)
- **PR/Issue**：#13140（回 port #13086）

## 总体目的

该提交是将 #13086（提交 2161，修复 Flink v2.0 中 `TaskResultAggregator` 的 NPE 问题）回移植（backport）到 Flink v1.19 和 v1.20 版本。Flink 维护算子 `TaskResultAggregator` 在作业恢复场景下存在 NPE bug：`startTime` 字段为 `Long` 包装类型，在恢复路径中可能为 null 导致拆箱 NPE；且 `processWatermark` 在无元素处理时仍输出无效的 `TaskResult`。由于该 bug 同样存在于 Flink v1.19 和 v1.20 的集成代码中，需要将相同的修复应用到这两个版本，确保所有支持的 Flink 版本都不受此问题影响。

## 如何达成设计目的

- 对 `flink/v1.19` 和 `flink/v1.20` 两个模块的 `TaskResultAggregator.java`、`OperatorTestBase.java`、`TestTaskResultAggregator.java` 应用与 #13086 相同的修改。
- 将 `startTime` 从 `Long` 改为 `long`，移除构造函数中的显式初始化。
- 在 `processWatermark` 中增加 `if (startTime != 0L)` 判断。
- 新增 `testProcessWatermarkWithoutElement` 和 `testProcessWatermark` 测试用例。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TaskResultAggregator.java` (修改, +25/-16 lines 中的主要部分)

**修改目的**：修复 Flink v1.19 版本的 TaskResultAggregator NPE。

**工作逻辑**：与提交 2161 对 Flink v2.0 的修改完全一致——将 `startTime` 从 `transient Long` 改为 `transient long`，移除构造函数初始化，在 `processWatermark` 中增加 `startTime != 0L` 条件判断。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/OperatorTestBase.java` (修改, +2 lines)

**修改目的**：为 v1.19 测试基类添加共享 Watermark 常量。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestTaskResultAggregator.java` (修改, +41/-3 lines 中的主要部分)

**修改目的**：为 v1.19 新增 watermark 处理的测试用例。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TaskResultAggregator.java` (修改, +25/-16 lines 中的主要部分)

**修改目的**：修复 Flink v1.20 版本的 TaskResultAggregator NPE。

**工作逻辑**：与 v1.19 和 v2.0 的修改完全一致。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/OperatorTestBase.java` (修改, +2 lines)

**修改目的**：为 v1.20 测试基类添加共享 Watermark 常量。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestTaskResultAggregator.java` (修改, +41/-3 lines 中的主要部分)

**修改目的**：为 v1.20 新增 watermark 处理的测试用例。

## 总结

该提交是 #13086 的回移植，将 `TaskResultAggregator` NPE 修复同步到 Flink v1.19 和 v1.20 两个版本，确保 Iceberg 支持的所有 Flink 版本都修复了此问题。修改内容与原修复完全一致。
