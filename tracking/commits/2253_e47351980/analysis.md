# 提交 2253：Flink: Backport dynamic Iceberg Sink: Rename method in DynamicRecordGenerator to Flink 1.19 / 1.20 (#13346)

## 提交信息

- **序号**：2253 / 4088
- **哈希**：e47351980fe8805769ffcaa07016c7fb79e12a5a
- **短哈希**：e47351980
- **日期**：2025-06-18 22:03:29 +0200
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport dynamic Iceberg Sink: Rename method in DynamicRecordGenerator to Flink 1.19 / 1.20
- **PR/Issue**：#13346

## 总体目的

本提交将提交 2251 中的方法重命名（`DynamicRecordGenerator.convert()` -> `generate()`）回移植到 Flink 1.19 和 1.20 模块。在提交 2250 中，动态 Iceberg Sink 的核心逻辑被回移植到 Flink 1.19/1.20，但当时使用的是原始的方法名 `convert`。随后在提交 2251 中，Flink 2.0 模块的方法已被重命名为 `generate`。为了保持各 Flink 版本之间的 API 一致性，本提交将相同的方法重命名应用到 Flink 1.19 和 1.20 模块。

## 如何达成设计目的

- 在 flink/v1.19 和 flink/v1.20 两个模块中，将 `DynamicRecordGenerator` 接口的 `convert` 方法重命名为 `generate`。
- 更新 `DynamicRecordProcessor` 中的调用方，以及两个测试类中的实现类方法名。
- 改动完全对称，两个模块各修改 4 个文件，每个文件修改 1 行。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecordGenerator.java` (修改, +1/-1 lines)

**修改目的**：将接口方法 `convert` 重命名为 `generate`。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecordProcessor.java` (修改, +1/-1 lines)

**修改目的**：更新 `processElement` 中 `generator.convert(element, this)` 为 `generator.generate(element, this)`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicIcebergSink.java` (修改, +1/-1 lines)

**修改目的**：更新测试 Generator 实现的方法名。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicIcebergSinkPerf.java` (修改, +1/-1 lines)

**修改目的**：更新性能测试 IdBasedGenerator 实现的方法名。

### `flink/v1.20/flink/...` (4 个文件, 各 +1/-1 lines)

与 v1.19 完全相同的修改，应用于 Flink 1.20 模块。

## 总结

本提交是提交 2251 的回移植，将 `DynamicRecordGenerator.convert()` 重命名为 `generate()` 的改动同步到 Flink 1.19 和 1.20 模块，确保所有 Flink 版本的 API 命名一致。共修改 8 个文件，每个文件仅改 1 行，是一个纯粹的命名一致性维护提交。
