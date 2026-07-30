# 提交 3228：SPEC: Add AccessDelegation header to planAPI calls (#14781)

## 提交信息

- **序号**：3228 / 4088
- **哈希**：3aec208149e0dd54bb5285d7127e52c8a0fb629e
- **短哈希**：3aec20814
- **日期**：2026-02-09
- **作者**：Prashant Singh
- **提交说明**：SPEC: Add AccessDelegation header to planAPI calls (#14781)
- **PR/Issue**：#14781

## 总体目的

本提交对 Iceberg REST Catalog 的 OpenAPI 规范进行扩展，在 planAPI 相关的两个端点上添加 `AccessDelegation` 请求头（即 `X-Iceberg-Access-Delegation` 头）的引用。Iceberg 的 REST Catalog 规范定义了客户端与 REST catalog 服务之间的交互协议，其中 `LoadTableResult` 响应可以包含 vended credentials（委托凭证），使客户端无需自行管理云存储的认证凭证即可访问数据文件。

`X-Iceberg-Access-Delegation` 头是一种可选信号，客户端通过它在请求中声明自己支持的委托访问机制（如 `vended-credentials` 或 `remote-signing`），服务器据此决定是否以及如何提供访问凭证。此前该请求头已在 `LoadTable` 端点（`/{prefix}/v1/namespaces/{namespace}/tables/{table}` 的 POST）上定义，但在 planAPI 调用中缺失。

planAPI 涉及两个端点：一是获取计划 ID 的端点（`/{prefix}/v1/namespaces/{namespace}/tables/{table}/plan`），二是使用计划 ID 获取扫描任务的端点（`/{prefix}/v1/namespaces/{namespace}/tables/{table}/plan/{plan-id}`）。在这些端点上支持 `AccessDelegation` 头是必要的，因为分布式规划场景下，规划阶段同样可能需要通过 vended credentials 或 remote signing 来访问底层数据文件，与 `LoadTable` 阶段的访问委托机制保持一致。

## 如何达成设计目的

通过在 OpenAPI YAML 规范文件中，为 planAPI 的两个端点的 parameters 列表添加对 `data-access` 参数的引用（`$ref: '#/components/parameters/data-access'`），复用已有的参数定义。该参数已在 `components/parameters` 中定义为 `X-Iceberg-Access-Delegation` 请求头，类型为可选字符串枚举。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (+2/-0 lines)

**修改目的**：为 planAPI 的两个端点添加 `AccessDelegation` 请求头支持。

**工作逻辑**：
在 `paths` 节点下，planAPI 的第一个端点（`/{prefix}/v1/namespaces/{namespace}/tables/{table}/plan`）的 parameters 列表中新增了 `- $ref: '#/components/parameters/data-access'`。该端点用于获取计划 ID（POST 请求触发异步规划）。第二个端点（`/{prefix}/v1/namespaces/{namespace}/tables/{table}/plan/{plan-id}`）的 parameters 列表中同样新增了对 `data-access` 的引用，该端点用于通过计划 ID 获取扫描任务结果。`data-access` 参数在规范中已定义（name 为 `X-Iceberg-Access-Delegation`，in 为 `header`，required 为 `false`，schema 为字符串枚举包含 `vended-credentials` 和 `remote-signing`）。此修改使客户端在 planAPI 调用时也能声明委托访问偏好，服务器可据此在规划阶段提供相应的访问机制。

## 总结

本提交通过在 REST Catalog OpenAPI 规范的 planAPI 端点上添加 `X-Iceberg-Access-Delegation` 请求头引用，使分布式规划阶段与 `LoadTable` 阶段在访问委托机制上保持一致，支持客户端在规划调用时声明 vended-credentials 或 remote-signing 偏好，完善了 REST Catalog 规范的访问委托能力。
