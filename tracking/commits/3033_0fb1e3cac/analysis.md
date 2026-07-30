# 提交 3033：OpenAPI: Etag for CommitTableResponse (#14760)

## 提交信息

- **序号**：3033 / 4088
- **哈希**：0fb1e3cac5fcae43b3b2f7c77ff26457f5d715dd
- **短哈希**：0fb1e3cac
- **日期**：2025-12-19
- **作者**：Christian
- **提交说明**：OpenAPI: Etag for CommitTableResponse (#14760)
- **PR/Issue**：#14760

## 总体目的

本提交为 Iceberg REST Catalog 的 OpenAPI 规范补充了 `CommitTableResponse` 响应中的 `etag` 头部定义。ETag（实体标签）是 REST Catalog 中的乐观并发控制机制核心：客户端在加载表时获取 ETag，在后续请求（如提交变更）时通过 `If-None-Match` 头部带回该 ETag，服务端据此判断表的元数据是否已变更，若未变可返回 304 Not Modified，若已变则拒绝或返回新数据。

在本次修改之前，OpenAPI 规范中 `CommitTableResponse` 的响应定义没有声明 `etag` 头部，尽管 `CreateTableResponse` 和 `LoadTableResponse` 已经包含了 ETag。这意味着按照规范，提交表变更后客户端无法从响应中获取新的 ETag 值，导致后续的乐观并发检查缺少最新的表版本标识。同时，`If-None-Match` 请求头部的描述中虽然提到了 `CreateTableResponse` 和 `LoadTableResponse`，但遗漏了 `CommitTableResponse`，文档描述不完整。

本提交做了两处修改：一是更新 `If-None-Match` 头部的描述文本，将 `CommitTableResponse` 加入 ETag 来源列表；二是在 `CommitTableResponse` 的响应定义中新增 `etag` 头部，引用已有的 `etag` 参数 schema。这样客户端在提交表变更后也能获取最新的 ETag，形成完整的乐观并发控制闭环。

## 如何达成设计目的

改动集中在单个文件 `open-api/rest-catalog-open-api.yaml`，分两处修改：更新 `If-None-Match` 请求参数的描述文本，以及在 `CommitTableResponse` 响应定义中添加 `etag` 头部引用。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (+5/-2 lines)

**修改目的**：补充 `CommitTableResponse` 的 ETag 头部定义并完善文档描述。

**工作逻辑**：
1. 在 `If-None-Match` 请求头部参数的 `description` 中，将原文 "The content is the value of the ETag received in a CreateTableResponse or LoadTableResponse." 修改为 "The content is the value of the ETag received in a CreateTableResponse, LoadTableResponse or CommitTableResponse."，使描述与实际支持的 ETag 来源一致。
2. 在 `CommitTableResponse` 的响应定义中，于 `application/json` schema 引用之后新增 `headers` 段，声明 `etag` 头部并引用 `'#/components/parameters/etag'`（复用已定义的 etag 参数 schema）。这样 `CommitTableResponse` 与 `CreateTableResponse`、`LoadTableResponse` 一样，在响应中返回 ETag 头部。

## 总结

本提交通过在 OpenAPI 规范中为 `CommitTableResponse` 补充 ETag 头部定义并完善 `If-None-Match` 的描述，使 REST Catalog 的乐观并发控制机制在文档层面形成完整闭环，客户端可在提交表变更后获取最新 ETag 用于后续并发检查，属于规范完整性改进。
