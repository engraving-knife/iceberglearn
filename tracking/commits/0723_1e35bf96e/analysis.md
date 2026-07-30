# 提交分析：Flink: Backport #10207 to v1.18 and v1.17 (#10235)

## 提交信息

- **提交哈希**: 1e35bf96ecacd5c5175116f40fa3e097991d04d2
- **短哈希**: 1e35bf96e
- **作者**: pvary (peter.vary.apache@gmail.com)
- **提交日期**: Sat Apr 27 15:44:39 2024 +0200
- **提交信息**: Flink: Backport #10207 to v1.18 and v1.17 (#10235)
- **共同作者**: Peter Vary (peter_vary4@apple.com)
- **影响文件**: 16 个文件，360 行新增，18 行删除

## 总体目的

本提交将 PR #10207（提交 646440abf）的修复回迁到 Flink v1.17 和 v1.18 两个维护分支。该修复补全了流式源的参数校验，防止用户为流式源错误设置 `snapshotId`、`asOfTimestamp`、`endSnapshotId`、`endTag` 等仅适用于批式读取的选项。

## 如何达成设计目的

本提交是 646440abf 的纯回迁，修复逻辑与原提交完全一致，只是目标目录从 `flink/v1.19/` 变为 `flink/v1.17/` 和 `flink/v1.18/`。由于 v1.17 和 v1.18 两套代码结构相同，每个文件的改动内容与原提交逐一对应。

### 修复逻辑（与原提交一致）

1. 在 `ScanContext.validate()` 的 `isStreaming` 分支新增 4 条校验：`snapshotId`、`asOfTimestamp`、`endSnapshotId`、`endTag` 不能为非 null。
2. 将 `validate()` 从构造函数移除，改为在 `FlinkSource.buildFlinkInputFormat()` 和 `IcebergSource.buildReader()` 中显式调用。
3. 将 `validate()` 可见性从 `private` 改为包级，便于测试。

## 修改详情

以下文件改动在 v1.17 和 v1.18 中各有一份，内容完全相同：

### 1. flink/v1.17,flink/v1.18/.../source/FlinkSource.java
- 移除未使用的 `Logger`/`LoggerFactory` 导入及静态字段。
- 在 `buildFlinkInputFormat()` 中显式调用 `context.validate()`。

### 2. flink/v1.17,flink/v1.18/.../source/IcebergSource.java
- 在 `buildReader()` 中 `contextBuilder.build()` 后新增 `context.validate()` 调用。

### 3. flink/v1.17,flink/v1.18/.../source/ScanContext.java
- 从构造函数移除 `validate()` 调用。
- `validate()` 可见性从 `private` 改为包级。
- `isStreaming` 分支新增 4 条 `Preconditions.checkArgument` 校验。

### 4. flink/v1.17,flink/v1.18/.../source/TestFlinkInputFormat.java
- 新增 `testValidation` 测试，验证同时设置 `endTag` 和 `endSnapshotId` 抛出异常。

### 5. flink/v1.17,flink/v1.18/.../source/TestIcebergSourceBounded.java
- 新增 `testValidation` 测试，验证批式源 endTag 与 endSnapshotId 冲突校验。

### 6. flink/v1.17,flink/v1.18/.../source/TestIcebergSourceContinuous.java
- 新增 `testValidation` 测试，验证流式源设置 `endTag` 抛出 "Cannot set end-tag option for streaming reader"。

### 7. flink/v1.17,flink/v1.18/.../source/TestScanContext.java（新增文件）
- 新增 111 行单元测试类，全面测试 `ScanContext.validate()` 的各种校验场景，包括流式源选项校验、起始/结束选项冲突校验、maxAllowedPlanningFailures 校验等。

### 8. flink/v1.17,flink/v1.18/.../source/TestStreamScanSql.java
- 类上添加 `@Timeout(60)` 注解。

## 小结

### 成效
- 将流式源参数校验修复同步到 v1.17 和 v1.18 维护分支，确保三个支持的 Flink 版本行为一致。
- 每个分支均新增了 `TestScanContext` 全面单元测试，保障校验逻辑的正确性。

### 影响范围
- 影响 Flink v1.17 和 v1.18 模块，与原提交 646440abf（v1.19）形成完整的跨版本修复。
- 对正确使用流式源的用户无影响；对错误配置的用户从静默错误变为快速失败。

### 回迁注意事项
- 此提交本身即为回迁，v1.19（main）已包含原修复（646440abf）。
- v1.17 和 v1.18 的改动内容与 v1.19 完全一致，无版本差异适配。
- 需注意此前提交 21c0ec491（#10230）已修改了 v1.17/v1.18 的 `IcebergSource.java`（批式源恢复修复），本提交在同一文件的 `buildReader()` 方法中新增 `validate()` 调用，两处修改位于不同方法，无冲突。
