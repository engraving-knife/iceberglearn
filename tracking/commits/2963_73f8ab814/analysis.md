# 提交 2963：Core: Reference IRC to return 204 (#14724)

## 提交信息

- **序号**：2963 / 4088
- **哈希**：73f8ab814dd37ac4590c20ded26e96dc739ad767
- **短哈希**：73f8ab814
- **日期**：2025-12-05
- **作者**：gaborkaszab
- **提交说明**：Core: Reference IRC to return 204 (#14724)
- **PR/Issue**：#14724

## 总体目的

Iceberg REST Catalog 的 OpenAPI 规范定义了多个端点在成功时应返回 HTTP 204（No Content）状态码，例如判断命名空间/表/视图是否存在、删除命名空间/表/视图、重命名表/视图、取消扫描计划、上报指标、提交事务等。这些操作的共同特点是成功响应不携带响应体，因此规范要求使用 204 而非 200 来明确表示"成功且无内容返回"。

然而，Iceberg 项目中作为测试参考实现的 `RESTCatalogServlet`（用于在测试中模拟 REST Catalog 服务端）在处理这些端点时，当响应体为空时默认返回 200 状态码，与规范要求不符。这导致参考实现与规范之间存在行为偏差，可能使依赖参考实现进行兼容性测试的客户端无法正确验证 204 响应的处理逻辑。本提交通过在响应体为空时根据路由类型设置 204 状态码，消除这一偏差。

## 如何达成设计目的

在 `RESTCatalogServlet` 处理响应的逻辑中，已有对 `LOAD_TABLE` 路由返回 304（Not Modified）的特殊处理（用于条件请求的 ETag/If-None-Match 场景）。本提交在该条件分支后新增一个 `else if` 分支，调用新方法 `shouldReturn204(route)` 判断当前路由是否应返回 204，若是则将响应状态设为 `SC_NO_CONTENT`（204）。`shouldReturn204` 方法枚举了规范要求返回 204 的 11 个路由。该逻辑仅作用于响应体为空（`contentLength == 0`）的成功请求，不影响有响应体的正常 200 响应。

## 修改详情

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogServlet.java` (+16/-0 lines)

**修改目的**：在参考 IRC 实现中，对规范要求 204 的端点返回正确的 HTTP 状态码。

**工作逻辑**：
改动位于响应处理段。原有逻辑在响应体为空时，对 `LOAD_TABLE` 路由设置 304（`SC_NOT_MODIFIED`）。新增 `else if (shouldReturn204(route))` 分支，匹配时设置 `response.setStatus(HttpServletResponse.SC_NO_CONTENT)`（即 204）。

新增的 `shouldReturn204` 方法通过 `||` 连接的 11 个路由判断决定是否返回 204，覆盖以下端点：
- **存在性检查**：`NAMESPACE_EXISTS`、`TABLE_EXISTS`、`VIEW_EXISTS`——这些端点仅表示"存在"或"不存在"，成功（存在）时无响应体，应返回 204。
- **删除操作**：`DROP_NAMESPACE`、`DROP_TABLE`、`DROP_VIEW`——删除成功后无内容返回。
- **重命名操作**：`RENAME_TABLE`、`RENAME_VIEW`——重命名成功后无内容返回。
- **扫描控制**：`CANCEL_PLAN_TABLE_SCAN`——取消扫描计划成功后无内容返回。
- **指标上报**：`REPORT_METRICS`——客户端上报指标，服务端确认后无内容返回。
- **事务提交**：`COMMIT_TRANSACTION`——事务提交成功后无内容返回（注意：普通的表 commit 走 `ROUTE.UPDATE_TABLE`，会返回新的表元数据，因此不在 204 列表中）。

该逻辑仅在 `contentLength == 0`（响应体为空）时触发，确保有响应体的请求仍正常返回 200 及响应体。

## 总结

本提交修正了参考 IRC 实现中 11 个端点的 HTTP 状态码偏差，使其在成功且无响应体时返回规范要求的 204 而非 200。虽然改动仅涉及测试辅助类 `RESTCatalogServlet`，但它确保了参考实现与 OpenAPI 规范的行为一致性，使客户端测试能够正确验证 204 响应的处理路径，提升了规范合规性测试的有效性。
