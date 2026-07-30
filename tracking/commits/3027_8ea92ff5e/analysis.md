# 提交 3027：Core: Simplify handling of the current planId in client side of remote planning (#14883)

## 提交信息

- **序号**：3027 / 4088
- **哈希**：8ea92ff5e1e164324950512bd8fde522976c58b4
- **短哈希**：8ea92ff5e
- **日期**：2025-12-18
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Simplify handling of the current planId in client side of remote planning (#14883)
- **PR/Issue**：#14883

## 总体目的

本提交是对远程扫描计划客户端实现中 `planId` 状态管理的简化与重构。在 `RESTTableScan` 中，`planId` 是服务端返回的扫描计划标识，用于后续的 fetch（拉取计划结果）和 cancel（取消计划）操作。原先的实现使用了一个名为 `currentPlanId` 的字段，其赋值逻辑分散在多个分支中，且 `fetchPlanningResult` 方法还通过参数传递 `planId`，导致状态管理冗余且容易出错。

具体来看，原实现在 `planFiles()` 方法中，仅在 `COMPLETED` 和 `SUBMITTED` 两种状态下才设置 `currentPlanId`（前者直接赋值，后者通过调用 `fetchPlanningResult(response.planId())` 在方法内部赋值），而 `FAILED`/`CANCELLED` 分支则直接引用 `response.planId()` 构造异常信息。`fetchPlanningResult` 方法还接收一个 `planId` 参数并赋值给 `currentPlanId`。`cancelPlan()` 方法则将 `currentPlanId` 拷贝到局部变量 `planId` 再使用。这种"字段名 currentPlanId + 方法参数 planId + 局部变量 planId"的混合方式增加了理解成本，也容易在后续维护中引入 bug。

本提交将字段重命名为 `planId`，并在 `planFiles()` 入口处统一从响应中提取 `planId` 赋值给字段，后续所有分支直接使用字段 `planId`，同时移除了 `fetchPlanningResult` 的参数。这样状态赋值集中在一处，所有分支共享同一个已赋值的字段，逻辑更清晰。

## 如何达成设计目的

改动集中在单个文件 `RESTTableScan.java`。核心思路是将 `planId` 的赋值提前到 `planFiles()` 方法解析响应之后、进入 switch 之前，统一为 `this.planId = response.planId()`，然后移除各分支中对 `currentPlanId` 的分散赋值、移除 `fetchPlanningResult` 的参数及其内部赋值、简化 `cancelPlan()` 中局部变量的使用。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTTableScan.java` (+8/-13 lines)

**修改目的**：简化 `planId` 字段的命名与赋值逻辑。

**工作逻辑**：
1. 字段重命名：`private String currentPlanId = null` 改为 `private String planId = null`。
2. 集中赋值：在 `planFiles()` 中调用 `FetchPlanningResultResponse response = ...` 之后、`switch (planStatus)` 之前，新增 `this.planId = response.planId();`，使所有分支都能直接使用 `planId` 字段。
3. 移除 `COMPLETED` 分支中的 `currentPlanId = response.planId();`（已由集中赋值覆盖）。
4. `SUBMITTED` 分支由 `return fetchPlanningResult(response.planId());` 改为 `return fetchPlanningResult();`（无参版本直接使用字段）。
5. `FAILED`/`CANCELLED`/`default` 分支中将 `response.planId()` 替换为 `planId` 字段引用。
6. `fetchPlanningResult` 方法签名从 `fetchPlanningResult(String planId)` 改为无参 `fetchPlanningResult()`，移除方法体内的 `currentPlanId = planId;` 赋值（字段已在入口处赋值）。
7. `cancelPlan()` 方法移除了 `String planId = currentPlanId;` 这行局部变量拷贝，直接使用字段 `planId`；成功取消后置空字段由 `currentPlanId = null` 改为 `this.planId = null`。

这些改动使 `planId` 的生命周期管理从"多处赋值"变为"单点赋值"，降低了状态不一致的风险。

## 总结

本提交通过将 `planId` 赋值集中到响应解析后一处，并移除冗余的参数传递与局部变量拷贝，显著简化了 `RESTTableScan` 中远程扫描计划标识的状态管理，提升了代码可读性与可维护性，属于纯重构性改动，不改变运行时行为。
