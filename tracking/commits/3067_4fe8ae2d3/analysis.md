# 提交 3067：Flink: Backport fix equalityFieldColumns always null in IcebergSink (#14975)

## 提交信息

- **序号**：3067 / 4088
- **哈希**：4fe8ae2d3688fab62270b24dd8c45fb36cef6cd6
- **短哈希**：4fe8ae2d3
- **日期**：2026-01-06
- **作者**：GuoYu
- **提交说明**：Flink: Backport fix equalityFieldColumns always null in IcebergSink (#14975)
- **PR/Issue**：#14975（回移自 #14952）

## 总体目的

本提交是提交 3066（PR #14952）的回移（backport），将"`IcebergSink` 中 `equalityFieldColumns` 始终为 null"的修复从 `flink/v2.1` 同步到 `flink/v1.20` 与 `flink/v2.0`。Iceberg 的 Flink 适配模块同时维护多个版本（v1.20、v2.0、v2.1），新修复先在最新 v2.1 落地，再按相同逻辑回移到仍受支持的旧版本，保证各版本行为一致。

被回移的源改动（详见 3066 分析）解决的问题是：`IcebergSink` 类中 `equalityFieldColumns` 字段被硬编码为 `= null` 且构造函数未接收该参数，导致用户通过 Builder 设置的等值字段列名从未传入实例。该字段仅用于日志和错误消息，在 hash 分布模式下的分区字段校验中，错误消息本应显示实际等值字段列名（如 `'[id]'`），但修复前始终显示 `'null'`，严重影响排障。

回移的原因是使用 Flink 1.20 与 2.0 的生产作业同样会触发该校验逻辑，若不回移，旧版本用户在遇到 hash 分布模式配置错误时仍会看到无意义的 `'null'`，与 v2.1 行为不一致。

## 如何达成设计目的

将 3066 对 `flink/v2.1` 的全部改动原样应用到 `flink/v1.20` 与 `flink/v2.0`：包括 `IcebergSink` 构造函数新增参数、字段声明修改、Builder 中 Set 转换与传参、新增回归测试。两个版本的改动文件清单与内容完全对称，共 4 个文件。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergSink.java` (+12/-3 lines)

**修改目的**：修复 v1.20 版本 `equalityFieldColumns` 始终为 null 的缺陷。

**工作逻辑**：
与 3066 中 `flink/v2.1` 改动一致：新增 `Sets` import；字段声明由 `= null` 改为无初始化器并加注释；构造函数新增 `Set<String> equalityFieldColumns` 参数并赋值；Builder `append()` 中新增 `equalityFieldColumnsSet` 转换并传入构造函数。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2DistributionMode.java` (+33/-0 lines)

**修改目的**：为 v1.20 新增回归测试。

**工作逻辑**：
新增 `testHashDistributionWithPartitionNotInEqualityFields` 测试，验证分区字段源列不在等值字段时抛出的 `IllegalStateException` 消息包含 `'[id]'` 而非 `'null'`，与 v2.1 测试完全一致。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergSink.java` (+12/-3 lines)

**修改目的**：对 v2.0 做完全相同的修复。

**工作逻辑**：与上述 v1.20 改动完全一致。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2DistributionMode.java` (+33/-0 lines)

**修改目的**：为 v2.0 新增相同的回归测试。

**工作逻辑**：与上述 v1.20 测试完全一致。

## 总结

本提交将 3066 的修复回移到 Flink v1.20 与 v2.0 适配模块，确保三个受支持的 Flink 版本在 hash 分布模式校验错误消息中均能正确显示等值字段列名，消除排障时的 `'null'` 困扰，保持版本间行为一致。
