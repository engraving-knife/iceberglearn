# 提交 0009：Open-API: Add namespaceExist API (#8569)

## 提交信息

- **序号**：0009 / 4088
- **哈希**：06d420f7cbfc0b19bc5208348fe589100b75e4fe
- **短哈希**：06d420f7c
- **日期**：2023-10-02 21:12:10 +0200
- **作者**：Hongyue/Steve Zhang
- **提交说明**：Open-API: Add namespaceExist API (#8569)
- **PR/Issue**：#8569

## 总体目的

这个提交为 Iceberg REST Catalog 的 OpenAPI 规范新增了"检查命名空间是否存在"的接口，同时对已有的"检查表是否存在"接口做了规范化和完善。

Iceberg REST Catalog 规范定义了基于 HTTP 的 catalog 交互协议。在此之前，规范已经具备 `tableExists`（HEAD 请求检查表是否存在）接口，但**缺少对应的命名空间存在性检查接口**。这意味着 REST 客户端想要知道某个命名空间是否存在时，只能先尝试 `GET /v1/namespaces/{namespace}` 然后根据是否返回 404 来判断，或者依赖 `listNamespaces` 间接判断——这些方式都不够语义化，也增加了不必要的网络开销和错误处理复杂度。一个语义明确的 `HEAD /v1/namespaces/{namespace}` 接口（返回 204 表示存在、404 表示不存在）能更高效、更清晰地表达"是否存在"这一意图，与 `tableExists` 接口形成对称。

此外，本提交还顺带对已有的 `tableExists` 接口做了规范化改进：原先该接口的 400/401/404 响应只写了简单的描述文本（如 "Bad Request"、"Unauthorized"、"Not Found"），没有引用统一的错误响应模型，也缺少 403 Forbidden 响应。本提交将这些响应改为引用 `#/components/responses/...` 通用定义，新增 403 响应，并为 404 补充了 `ErrorModel` schema 和 `NoSuchTableError` 示例，使两个 exists 接口的错误响应规范保持一致。提交说明中的 "Address feedback" 和 "Reword exists API description" 表明该 PR 经过评审迭代，最终将描述措辞统一为 "The response does not contain a body."

这一改动对 Iceberg REST 协议的完整性和对称性有意义：让命名空间和表在存在性检查能力上对齐，方便各语言客户端实现统一的存在性探测逻辑。

## 如何达成设计目的

设计思路分两部分。第一部分是新增 `namespaceExists` 接口：在 `rest-catalog-open-api.yaml` 中命名空间相关路径（`/v1/namespaces/{namespace}`）下，在已有的 `get` 和 `delete` 操作之间新增一个 `head` 操作，定义 `operationId: namespaceExists`，返回 204 表示存在、404 表示不存在，并补全 400/401/403/419/503/5XX 等错误响应。第二部分是规范化 `tableExists` 接口：将其 400/401 响应改为引用通用响应定义、新增 403 响应、为 404 补充带 ErrorModel schema 和示例的响应体，并把描述措辞统一为与新增接口一致的 "The response does not contain a body."。两部分改动落在同一个 YAML 文件中，整体结构是"新增对称接口 + 完善既有接口规范"。

## 修改详情

### `open-api/rest-catalog-open-api.yaml`

**修改目的**：新增 `namespaceExists` HEAD 接口，并规范化 `tableExists` 接口的错误响应定义。

**工作逻辑**：

1. **新增 `namespaceExists` 接口**：在 `/v1/namespaces/{namespace}` 路径下（位于 `get` 之后、`delete` 之前）新增 `head` 操作。关键定义如下：
   - `tags: [Catalog API]`、`summary: Check if a namespace exists`、`operationId: namespaceExists`
   - `description: Check if a namespace exists. The response does not contain a body.`
   - 响应码：`204`（Success, no content）表示命名空间存在；`404`（Not Found - Namespace not found）表示不存在，并携带 `ErrorModel` schema 和 `NoSuchNamespaceError` 示例；`400`/`401`/`403`/`419`/`503`/`5XX` 分别引用通用错误响应定义。这与已有 `tableExists` 接口的响应结构对称。

2. **规范化 `tableExists` 接口**：对 `/v1/{prefix}/namespaces/{namespace}/tables/{table}` 路径下的 `head` 操作（`operationId: tableExists`）做以下改动：
   - 将描述从 "This request does not return a response body." 改为 "The response does not contain a body."，与新增的 `namespaceExists` 措辞统一（对应提交说明中的 "Reword exists API description"）。
   - `400` 响应：从内联的 `description: Bad Request` 改为 `$ref: '#/components/responses/BadRequestErrorResponse'`，复用统一错误模型。
   - `401` 响应：从 `description: Unauthorized` 改为 `$ref: '#/components/responses/UnauthorizedResponse'`。
   - 新增 `403` 响应：`$ref: '#/components/responses/ForbiddenResponse'`（原先缺失）。
   - `404` 响应：从简单的 `description: Not Found` 改为带 `content` 的完整定义——`description: Not Found - NoSuchTableException, Table not found`，并附 `ErrorModel` schema 和 `TableToLoadDoesNotExist` 示例（引用 `#/components/examples/NoSuchTableError`）。

通过这两处改动，命名空间和表的存在性检查接口在语义、响应码、错误模型上达到一致，提升了 REST Catalog 规范的完整性和一致性。

## 小结

该提交为 Iceberg REST Catalog OpenAPI 规范新增了 `namespaceExists` HEAD 接口，使命名空间与表在存在性检查能力上对称，同时规范化了 `tableExists` 接口的错误响应定义，提升了 REST 协议的完整性与一致性。
