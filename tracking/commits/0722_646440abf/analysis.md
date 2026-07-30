# 提交分析：Flink: Prevent setting endTag/endSnapshotId for streaming source (#10207)

## 提交信息

- **提交哈希**: 646440abf6f1d6a5c06979b5939c33c5a410a7ba
- **短哈希**: 646440abf
- **作者**: pvary (peter.vary.apache@gmail.com)
- **提交日期**: Fri Apr 26 19:19:13 2024 +0200
- **提交信息**: Flink: Prevent setting endTag/endSnapshotId for streaming source (#10207)
- **影响文件**: 8 个文件，180 行新增，9 行删除

## 总体目的

本提交修复了 Flink Iceberg 流式源（streaming source）的参数校验缺陷。此前 `ScanContext.validate()` 仅校验了 `tag` 不能用于流式读取，但未校验 `snapshotId`、`asOfTimestamp`、`endSnapshotId`、`endTag` 等仅适用于批式读取的选项。这些选项对流式源无意义且可能导致非预期行为，本提交补全了这些校验，并重构了校验调用时机。

## 如何达成设计目的

### Bug 成因

`ScanContext` 的 `validate()` 方法原本在构造函数中被调用，且为 `private` 方法。流式源的校验逻辑只检查了 `tag`（起始标签），但遗漏了以下批式专用选项：

- `snapshotId`（指定快照 ID 读取）：流式源应从最新位置持续增量读取，指定固定快照无意义。
- `asOfTimestamp`（按时间戳读取指定快照）：同理，流式源不应使用固定时间点快照。
- `endSnapshotId`（结束快照 ID）：流式源是无限流，不应有结束快照。
- `endTag`（结束标签）：同理，流式源不应有结束标签。

这些选项若被流式源错误设置，可能导致读取行为不符合预期但无任何错误提示，属于"静默错误"。

### 修复逻辑

1. **补全校验规则**：在 `validate()` 方法的 `isStreaming` 分支中，新增对 `snapshotId`、`asOfTimestamp`、`endSnapshotId`、`endTag` 四个选项的非空校验，违反时抛出 `IllegalArgumentException`。
2. **重构校验调用时机**：将 `validate()` 从构造函数中移除，改为在 `FlinkSource.buildFlinkInputFormat()` 和 `IcebergSource.createReader()` 中显式调用。这样做的目的是让 `ScanContext` 的构建与校验解耦，便于在测试中单独构建 context 并调用 `validate()` 验证各种非法配置。
3. **可见性调整**：将 `validate()` 从 `private` 改为包级可见（`void validate()`），使测试类 `TestScanContext` 可以直接调用。

## 修改详情

### 1. flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/source/FlinkSource.java
- **变更**:
  - 移除了未使用的 `Logger` 和 `LoggerFactory` 导入及静态字段（`LOG`），清理无用代码。
  - 在 `buildFlinkInputFormat()` 方法中，将 `contextBuilder.build()` 结果存入变量 `context`，显式调用 `context.validate()` 后再传入 `FlinkInputFormat` 构造函数。
- **影响**: 确保 FlinkInputFormat（旧版 Source API）在构建时执行参数校验。

### 2. flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/source/IcebergSource.java
- **变更**: 在 `IcebergSource.Builder.buildReader()` 方法中，`contextBuilder.build()` 后新增 `context.validate()` 调用。
- **影响**: 确保 IcebergSource（新版 Source API）在构建 reader 时执行参数校验。

### 3. flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/source/ScanContext.java
- **变更**:
  - 从构造函数中移除 `validate()` 调用。
  - 将 `validate()` 方法可见性从 `private` 改为包级（`void validate()`）。
  - 在 `isStreaming` 分支中新增 4 条 `Preconditions.checkArgument` 校验：`snapshotId`、`asOfTimestamp`、`endSnapshotId`、`endTag` 均不能为非 null。
- **影响**: 核心校验逻辑变更，补全流式源的参数校验。

### 4. flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkInputFormat.java
- **变更**: 新增 `testValidation` 测试方法，验证同时设置 `endTag` 和 `endSnapshotId` 时抛出 `IllegalArgumentException`，消息为 "END_SNAPSHOT_ID and END_TAG cannot both be set."。

### 5. flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceBounded.java
- **变更**: 新增 `testValidation` 测试方法，验证批式源同时设置 `endTag` 和 `endSnapshotId` 的冲突校验。

### 6. flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceContinuous.java
- **变更**: 新增 `testValidation` 测试方法，验证流式源设置 `endTag` 时抛出 `IllegalArgumentException`，消息为 "Cannot set end-tag option for streaming reader"。

### 7. flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestScanContext.java（新增文件）
- **变更**: 新增 111 行的单元测试类，直接对 `ScanContext.validate()` 进行全面测试，覆盖以下场景：
  - `testIncrementalFromSnapshotId`：验证 `INCREMENTAL_FROM_SNAPSHOT_ID` 策略下 startSnapshotId 为 null 及 startSnapshotTimestamp 非 null 的校验。
  - `testIncrementalFromSnapshotTimestamp`：验证 `INCREMENTAL_FROM_SNAPSHOT_TIMESTAMP` 策略下 startSnapshotTimestamp 为 null 及 startSnapshotId 非 null 的校验。
  - `testStreaming`：验证流式源下 `useTag`、`useSnapshotId`、`asOfTimestamp`、`endSnapshotId`、`endTag` 均被拒绝。
  - `testStartConflict`：验证 `startTag` 和 `startSnapshotId` 不能同时设置。
  - `testEndConflict`：验证 `endTag` 和 `endSnapshotId` 不能同时设置。
  - `testMaxAllowedPlanningFailures`：验证 `maxAllowedPlanningFailures` 不能设为 -1 以外的负数。

### 8. flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestStreamScanSql.java
- **变更**: 在类上添加 `@Timeout(60)` 注解。
- **原因**: 该测试类可能因新增校验导致某些场景行为变化，添加超时保护防止测试挂起。

## 小结

### 成效
- 补全了流式源的参数校验，防止用户错误配置 `snapshotId`、`asOfTimestamp`、`endSnapshotId`、`endTag` 等批式专用选项导致的静默错误。
- 通过重构将校验从构造函数移至显式调用，提升了可测试性，新增了全面的 `TestScanContext` 单元测试。
- 清理了 `FlinkSource` 中未使用的 Logger。

### 影响范围
- 仅影响 Flink v1.19 模块（main 分支）。
- 对已正确使用流式源的用户无影响；对错误配置流式源选项的用户，将从静默错误变为快速失败（fail-fast），属于行为变化但符合预期。
- 批式源的已有校验（如 endTag 与 endSnapshotId 互斥）不受影响。

### 回迁注意事项
- 此提交是原始修复（#10207），后续提交 1e35bf96e（#10235）将其回迁到 v1.18 和 v1.17。
- 校验逻辑变更涉及 `ScanContext`、`FlinkSource`、`IcebergSource` 三个核心类，回迁时需同步修改。
- `TestScanContext` 为新增测试类，回迁时需一并添加。
- 由于将 `validate()` 从构造函数移除，需确认所有构建 `ScanContext` 的路径都正确调用了 `validate()`，否则可能导致校验遗漏。
