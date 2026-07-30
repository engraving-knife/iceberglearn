# 提交 3018：SPEC: Add NoSuchPlanId to cancel endpoint (#14796)

## 提交信息

- **序号**：3018 / 4088
- **哈希**：90bfc3dff5f40f9ba886832ba9ccaa7b42298e9a
- **短哈希**：90bfc3dff
- **日期**：2025-12-15
- **作者**：Prashant Singh
- **提交说明**：SPEC: Add NoSuchPlanId to cancel endpoint (#14796)
- **PR/Issue**：#14796

## 总体目的

本提交修改 Iceberg REST Catalog 的 OpenAPI 规范文档，在"取消扫描"（cancel scan）端点的 404 响应中补充 `NoSuchPlanIdException` 错误类型。Iceberg REST Catalog 的扫描采用异步计划模型（plan-based scan model）：客户端先调用 `planTableScan` 获取一个 `plan-id`，随后通过 `fetchPlanningResult` 或 `fetchScanTasks` 拉取扫描结果，如果中途放弃则调用 cancel 端点释放服务端资源。

在这一模型中，`plan-id` 是标识一次扫描计划的核心令牌。如果客户端对一个不存在或已过期/已取消的 `plan-id` 调用 cancel 端点，服务端理应返回 404 并指明原因是 `NoSuchPlanIdException`。然而，在此前的 OpenAPI 规范中，cancel 端点的 404 响应仅列出了 `NoSuchTableException` 和 `NoSuchNamespaceException`，遗漏了 `NoSuchPlanIdException`。这是一个规范层面的不一致——与 cancel 端点功能紧密相关的 `fetchPlanningResult` 端点早已在 404 响应中包含了 `NoSuchPlanIdException`，但 cancel 端点却未同步。

这种遗漏会带来实际问题：REST Catalog 的实现者（如 Snowflake、Tabular 等服务）在实现 cancel 端点时，缺乏规范依据来返回 `NoSuchPlanIdException`；同时，客户端 SDK 开发者也无法从规范中预知 cancel 端点可能返回的错误类型，难以编写健壮的错误处理逻辑。本提交补齐这一缺口，使规范与实际语义对齐。

## 如何达成设计目的

改动非常聚焦，仅在 `open-api/rest-catalog-open-api.yaml` 的 cancel 端点 404 响应定义中增加两行：一是在错误类型说明列表中加入 `NoSuchPlanIdException, the plan-id does not exist`，二是在 examples 中增加 `PlanIdDoesNotExist` 示例引用，指向已存在的 `NoSuchPlanIdError` 示例组件。这一改动复用了规范中已有的 `NoSuchPlanIdError` 示例定义（位于 components/examples 下），无需新增任何组件，仅是将其引入到 cancel 端点的响应定义中。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (+3/-0 lines)

**修改目的**：在 cancel 端点的 404 响应中补充 `NoSuchPlanIdException` 错误类型及对应示例。

**工作逻辑**：
cancel 端点（路径为 `.../{table}/scans/{scan-id}` 的 DELETE 请求，用于通知服务端释放扫描计划占用的资源）的 404 响应原先仅描述了表和命名空间不存在的情况。本次改动在 `description` 字段的错误列表中新增一行 `- NoSuchPlanIdException, the plan-id does not exist`，并在 `examples` 映射中新增 `PlanIdDoesNotExist` 条目，通过 `$ref: '#/components/examples/NoSuchPlanIdError'` 引用已有的错误示例。该示例返回的 JSON body 包含 `"type": "NoSuchPlanIdException"` 和 `"code": 404`。

这一改动使 cancel 端点与同一扫描流程中的 `fetchPlanningResult` 端点保持一致——后者早已在 404 响应中包含 `NoSuchPlanIdException`。对于 REST Catalog 实现者而言，现在可以合法地在 cancel 端点中返回 `NoSuchPlanIdException`；对于客户端开发者而言，可以预期 cancel 端点可能返回的错误类型并编写对应处理逻辑。

## 总结

本提交是对 Iceberg REST Catalog OpenAPI 规范的一致性修补，补齐了 cancel 端点缺失的 `NoSuchPlanIdException` 错误定义，使规范完整覆盖扫描计划生命周期中所有合理的错误场景。改动虽小（仅 3 行），但对于 REST Catalog 的多实现互操作性和客户端错误处理具有重要意义。
