# 提交 2864：Core: Fix validation of PlanTableScanRequest (#14561)

## 提交信息

- **序号**：2864 / 4088
- **哈希**：7368e5990d8ba52401c681bd9dfaf03a6b1e7016
- **短哈希**：7368e5990
- **日期**：2025-11-11 10:15:28 -0800
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Fix validation of PlanTableScanRequest (#14561)
- **PR/Issue**：#14561

## 总体目的

这个提交修复了 `PlanTableScanRequest` 的验证逻辑。根据 Iceberg REST Catalog OpenAPI 规范，当请求中没有提供 `snapshotId` 时，服务器可以选择表主分支中的最新快照进行扫描计划。然而，当前的验证逻辑强制要求请求中必须设置 `snapshotId` 或同时设置 `startSnapshotId`/`endSnapshotId`，否则验证失败。

这意味着客户端无法发送一个不包含任何快照 ID 的请求来让服务器自动选择最新快照，这与 OpenAPI 规范的定义不一致。该提交修正了验证逻辑，允许所有快照 ID 字段都为 null 的情况（表示由服务器选择最新快照），同时仍然确保互斥性约束（不能同时提供 `snapshotId` 和 `startSnapshotId`/`endSnapshotId`，增量扫描的 `startSnapshotId` 和 `endSnapshotId` 必须同时提供）。

## 如何达成设计目的

通过重构 `validate()` 方法的验证逻辑来实现：

1. **旧逻辑**：使用异或（XOR）操作符 `snapshotId != null ^ (startSnapshotId != null && endSnapshotId != null)`，强制要求必须提供 `snapshotId` 或同时提供 `startSnapshotId`/`endSnapshotId`，两者都不提供会验证失败。

2. **新逻辑**：分为两个独立的检查：
   - 如果提供了 `snapshotId`，则不能同时提供 `startSnapshotId` 或 `endSnapshotId`（互斥检查）。
   - 如果提供了 `startSnapshotId` 或 `endSnapshotId` 中的一个，则两个都必须提供（增量扫描完整性检查）。
   - 如果都不提供，验证通过（由服务器选择最新快照）。

此外，提交还添加了静态 `builder()` 方法并废弃了公共 Builder 构造函数，这是 API 规范化的一部分。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/requests/PlanTableScanRequest.java` (+18/-3 lines)

**修改目的**：修复验证逻辑，允许所有快照 ID 为 null 的情况；添加静态 builder() 方法。

**工作逻辑**：

1. **validate() 方法重构**：将原来的单行 XOR 检查替换为两个独立的条件检查块。第一个块检查 `snapshotId` 与 `startSnapshotId`/`endSnapshotId` 的互斥性；第二个块检查增量扫描时 `startSnapshotId` 和 `endSnapshotId` 必须同时提供。不再要求至少提供一个快照 ID。

2. **添加静态 builder() 方法**：新增 `public static Builder builder()` 方法，返回新的 Builder 实例。

3. **废弃公共 Builder 构造函数**：给 `Builder()` 构造函数添加 `@Deprecated` 注解，说明自 1.11.0 起废弃，1.12.0 将降低可见性，建议使用 `builder()` 静态方法代替。

### `core/src/main/java/org/apache/iceberg/rest/requests/PlanTableScanRequestParser.java` (+1/-1 lines)

**修改目的**：将解析器中的 Builder 创建方式从 `new PlanTableScanRequest.Builder()` 改为 `PlanTableScanRequest.builder()`。

**工作逻辑**：使用新的静态 builder() 方法替代直接调用公共构造函数，配合 API 规范化变更。

### `core/src/main/java/org/apache/iceberg/rest/responses/PlanTableScanResponse.java` (+7/-0 lines)

**修改目的**：废弃 PlanTableScanResponse.Builder 的公共构造函数。

**工作逻辑**：给 `PlanTableScanResponse.Builder` 的公共构造函数添加 `@Deprecated` 注解，说明自 1.11.0 起废弃，1.12.0 将降低可见性。这是与 PlanTableScanRequest 一致的 API 规范化变更。

### `core/src/test/java/org/apache/iceberg/rest/requests/TestPlanTableScanRequestParser.java` (+54/-8 lines)

**修改目的**：更新测试以验证新的验证逻辑，并使用新的 builder() 方法。

**工作逻辑**：

1. **文件重命名**：从 `TestPlanTableScanRequest.java` 重命名为 `TestPlanTableScanRequestParser.java`，类名相应更改。

2. **新增 `requestWithValidSnapshotIds` 测试**：验证三种有效场景：都不提供（服务器选择最新快照）、仅提供 `snapshotId`、同时提供 `startSnapshotId` 和 `endSnapshotId`。

3. **新增 `requestWithInvalidSnapshotIds` 测试**：验证四种无效场景：同时提供 `snapshotId` 和 `startSnapshotId`、同时提供 `snapshotId` 和 `endSnapshotId`、仅提供 `startSnapshotId`、仅提供 `endSnapshotId`。

4. **更新现有测试**：将所有 `new PlanTableScanRequest.Builder()` 改为 `PlanTableScanRequest.builder()`，并更新验证错误消息以匹配新的验证逻辑输出。

## 总结

这个提交修复了 `PlanTableScanRequest` 的验证逻辑，使其与 OpenAPI 规范一致——允许所有快照 ID 字段都为 null，由服务器选择最新快照。同时进行了 API 规范化，添加静态 `builder()` 方法并废弃公共 Builder 构造函数。测试覆盖了新的验证逻辑，验证了各种有效和无效的快照 ID 组合。这与提交 2865（PlanTableScanResponse 验证修复）是同一作者的相关修复。
