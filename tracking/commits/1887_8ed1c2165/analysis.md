# 提交 1887：OpenAPI: Handle NamespaceNotEmptyException when dropping a namespace (#12518)

## 提交信息

- **序号**：1887 / 4088
- **哈希**：8ed1c216503b7193924ca57bd2694025660ac02c
- **短哈希**：8ed1c2165
- **日期**：2025-03-20 07:49:01 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：OpenAPI: Handle NamespaceNotEmptyException when dropping a namespace (#12518)
- **PR/Issue**：#12518

## 总体目的

本提交在 Iceberg REST Catalog 的 OpenAPI 规范中，为"删除命名空间"接口（`DELETE /v1/{prefix}/namespaces/{namespace}`）补充 409 Conflict 响应，以规范 `NamespaceNotEmptyException` 的处理。

背景：当用户尝试删除一个非空的命名空间（即命名空间下仍有表或视图）时，Iceberg 会抛出 `NamespaceNotEmptyException`。但此前的 OpenAPI 规范中，删除命名空间接口的响应定义只包含 404（NoSuchNamespace）、419（认证超时）、503（不可用）等，未明确文档化 409 响应。这导致 REST 客户端实现无法预知该错误场景，可能无法正确处理非空命名空间的删除失败。

本提交补充 409 响应定义，使其与实际 REST 实现行为一致，并为客户端提供明确的错误契约。

## 如何达成设计目的

在 OpenAPI YAML 中：
1. 在删除命名空间接口的 responses 中新增 `409` 响应，描述"Not Empty - Namespace to delete is not empty."，引用 `IcebergErrorResponse` schema，并使用 `NamespaceNotEmptyError` 示例。
2. 在 components/examples 中新增 `NamespaceNotEmptyError` 示例，包含 error message、type（`NamespaceNotEmptyException`）和 code（409）。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (修改, +19/-0 lines)

**修改目的**：为删除命名空间接口补充 409 响应。

**工作逻辑**：
- 在 `DELETE /v1/{prefix}/namespaces/{namespace}` 的 responses 中，404 之后、419 之前新增 `409` 响应：
  - `description: Not Empty - Namespace to delete is not empty.`
  - content 为 `application/json`，schema 引用 `IcebergErrorResponse`，examples 引用 `NamespaceNotEmptyError`。
- 在 `components/examples` 中新增 `NamespaceNotEmptyError`：
  ```json
  {
    "error": {
      "message": "The given namespace is not empty",
      "type": "NamespaceNotEmptyException",
      "code": 409
    }
  }
  ```

## 总结

本提交是 OpenAPI 规范的文档补充，为删除命名空间接口新增 409 Conflict 响应（`NamespaceNotEmptyException`），使 REST 规范与实际实现行为一致，为客户端提供明确的错误契约。
