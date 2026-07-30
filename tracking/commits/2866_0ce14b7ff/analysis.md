# 提交 2866：Core: Fix PlanTableScanResponse validation (#14562)

## 提交信息

- **序号**：2866 / 4088
- **哈希**：0ce14b7ffd77337a4aee3de5cd19e697e731c19e
- **短哈希**：0ce14b7ff
- **日期**：2025-11-11 19:45:34 -0800
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Fix PlanTableScanResponse validation (#14562)
- **PR/Issue**：#14562

## 总体目的

这个提交修复了 `PlanTableScanResponse` 的验证逻辑。根据 Iceberg REST Catalog OpenAPI 规范，当计划状态为 `completed` 时，响应中可以包含 `planId`。然而，当前的验证逻辑错误地要求 `planId` 只能在状态为 `submitted` 时存在，当状态为 `completed` 且包含 `planId` 时会验证失败。

这与提交 2863 是同一作者的关联修复，两者都在修正 PlanTableScan 相关的验证逻辑以符合 OpenAPI 规范。2863 修复了请求（Request）的验证，本提交修复了响应（Response）的验证。

具体来说，OpenAPI 规范允许 `completed` 状态的响应携带 `planId`（用于标识已完成的扫描计划），但旧代码强制要求 `planId` 只能在 `submitted` 状态下出现，导致合规的响应被错误拒绝。

## 如何达成设计目的

通过重构 `validate()` 方法中关于 `planId` 的验证逻辑来实现：

1. **旧逻辑**：`planStatus() == PlanStatus.SUBMITTED || planId() == null`，即如果状态不是 `submitted`，则 `planId` 必须为 null。这排除了 `completed` 状态携带 `planId` 的情况。

2. **新逻辑**：当 `planId` 不为 null 时，检查状态是否为 `submitted` 或 `completed`，否则验证失败。如果 `planId` 为 null，则不做此检查。这样允许 `completed` 状态的响应携带 `planId`。

此外，还将错误消息从硬编码字符串改为使用 `String.format` 动态填充状态值，使错误消息更具信息量。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/responses/PlanTableScanResponse.java` (+14/-6 lines)

**修改目的**：修正 `planId` 验证逻辑，允许 `completed` 状态携带 `planId`；改进错误消息。

**工作逻辑**：

1. **planId 验证逻辑重构**：将原来的无条件检查 `planStatus() == PlanStatus.SUBMITTED || planId() == null` 改为条件检查——仅当 `planId != null` 时才验证 `planStatus` 是否为 `SUBMITTED` 或 `COMPLETED`。这允许 `completed` 状态的响应携带 `planId`，也允许不携带 `planId`。

2. **错误消息改进**：将所有验证错误消息从硬编码的状态字符串改为使用 `String.format` 动态填充 `PlanStatus.status()` 的值。例如，将 `"Invalid response: plan id should be defined when status is 'submitted'"` 改为 `"Invalid response: plan id should be defined when status is '%s'"` 并填充 `PlanStatus.SUBMITTED.status()`。这使得错误消息能准确反映实际的状态值。

3. **新的 planId 验证消息**：当 `planId` 存在但状态不是 `submitted` 或 `completed` 时，错误消息变为 `"Invalid response: plan id can only be defined when status is 'submitted' or 'completed'"`。

### `core/src/test/java/org/apache/iceberg/rest/responses/TestPlanTableScanResponseParser.java` (+49/-17 lines)

**修改目的**：添加测试验证 `completed` 状态可以携带或不携带 `planId`；更新现有测试以使用枚举常量。

**工作逻辑**：

1. **新增 `roundTripSerdeWithCompletedPlanningWithAndWithoutPlanId` 测试**：验证 `completed` 状态的响应可以不包含 `planId`（序列化为 `{"plan-status":"completed"}`），也可以包含 `planId`（序列化为 `{"plan-status":"completed","plan-id":"somePlanId"}`）。

2. **新增 `roundTripSerdeWithSubmittedPlanningWithPlanId` 测试**：验证 `submitted` 状态必须携带 `planId` 的行为仍然正确。

3. **更新现有测试**：
   - 将所有 `PlanStatus.fromName("submitted")` 等字符串解析方式改为直接使用 `PlanStatus.SUBMITTED` 枚举常量，更简洁且类型安全。
   - 更新 `roundTripSerdeWithInvalidPlanIdWithIncorrectStatus` 测试中的错误消息，从 `"plan id can only be defined when status is 'submitted'"` 改为 `"plan id can only be defined when status is 'submitted' or 'completed'"`。

## 总结

这个提交修复了 `PlanTableScanResponse` 的验证逻辑，使其与 OpenAPI 规范一致——允许 `completed` 状态的响应携带 `planId`。修改涉及验证逻辑重构和错误消息改进，同时添加了针对新行为的测试。与提交 2863（PlanTableScanRequest 验证修复）是同一作者的关联工作，共同完善了 PlanTableScan 的请求和响应验证。
