# 提交 1846：OpenAPI: Handle NamespaceNotEmptyException when dropping a namespace

## 提交信息

- **序号**：1846 / 4088
- **哈希**：fe258463f5aba15d45bbe1e401d50279aef05fc5
- **短哈希**：fe258463f
- **日期**：2025-03-13 16:01:03 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：OpenAPI: Handle NamespaceNotEmptyException when dropping a namespace
- **PR/Issue**：#12517（推测，与后续 revert PR 同号）

## 总体目的

本提交修改了 REST Catalog OpenAPI 规范文件 `rest-catalog-open-api.yaml`，在删除命名空间（drop namespace）的 API 响应中增加了对 `NamespaceNotEmptyException` 的处理。在此之前，DELETE `/v1/{prefix}/namespaces/{namespace}` 端点的 400 错误响应只引用了通用的 `BadRequestErrorResponse`，没有明确说明当命名空间非空时返回的特定错误类型。

REST Catalog 规范中，删除命名空间时如果命名空间下仍有表，服务端会返回 `NamespaceNotEmptyException`（HTTP 400）。本提交在 400 响应中增加了两个示例：`NamespaceNotEmptyError`（命名空间非空时的错误）和 `BadRequestError`（通用请求格式错误），使 API 消费方能更清晰地了解可能的错误响应。同时将 400 响应从简单的 `$ref` 引用改为带 `description`、`content`、`schema`、`examples` 的完整响应定义。

**注意**：此提交在几分钟后即被下一个提交（1847）回退。

## 如何达成设计目的

在 OpenAPI YAML 中将 drop namespace 端点的 400 响应从 `$ref: '#/components/responses/BadRequestErrorResponse'` 展开为完整的响应对象，包含 description、content（application/json）、schema（仍引用 BadRequestErrorResponse）和 examples（NamespaceNotEmptyError + BadRequestError）。同时在 components/examples 中新增两个示例定义。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (修改)

**修改目的**：在 drop namespace 的 400 响应中增加 NamespaceNotEmptyException 示例。

**工作逻辑**：

1. DELETE `/v1/{prefix}/namespaces/{namespace}` 端点的 `400` 响应：从 `$ref: '#/components/responses/BadRequestErrorResponse'` 改为完整定义：
   - `description`：说明 400 可能是 bad request 错误或 NamespaceNotEmptyException
   - `content.application/json.schema`：引用 `#/components/responses/BadRequestErrorResponse`
   - `content.application.json.examples`：包含 `NamespaceNotEmptyExample`（引用 `#/components/examples/NamespaceNotEmptyError`）和 `BadRequestExample`（引用 `#/components/examples/BadRequestError`）

2. 在 `components/examples` 中新增两个示例：
   - `BadRequestError`：summary "The request is malformed"，value 包含 error.message="Malformed request"、type="BadRequestException"、code=400
   - `NamespaceNotEmptyError`：summary "The requested namespace is not empty"，value 包含 error.message="The given namespace is not empty"、type="NamespaceNotEmptyException"、code=400

## 小结

本提交为 OpenAPI 规范增加了 NamespaceNotEmptyException 的文档说明。但此提交几乎立即被提交 1847 回退，因此实际效果为零。回迁到 1.4.x 时无需单独回迁此提交和其回退提交（1847），两者互相抵消。
