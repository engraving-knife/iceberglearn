# 提交 3538：Spec: Add 404 response for config endpoint (#15746)

## 提交信息

- **序号**：3538 / 4088
- **哈希**：304c00f3c17a67a7eb2306cf018fe8ba5647e4b1
- **短哈希**：304c00f3c
- **日期**：2026-04-15 14:51:10 -0700
- **作者**：Oguzhan Unlu
- **提交说明**：Spec: Add 404 response for config endpoint (#15746)
- **PR/Issue**：#15746

## 总体目的

Iceberg REST Catalog 规范的 OpenAPI 定义中，`GET /v1/config` 端点（用于客户端获取 catalog 配置）此前定义了 401、403、419、503 等错误响应，但缺少 404 响应。实际上，当客户端在 `warehouse` 查询参数中提供了一个不存在的 warehouse 时，服务端应返回 404 Not Found。

规范文档缺少这个 404 响应定义，会导致：
1. 规范读者/实现者不清楚该端点可以返回 404
2. 自动生成的客户端代码可能没有处理 404 响应的逻辑
3. 规范与实际服务端行为不一致

本提交为 `GET /v1/config` 端点补充 404 响应定义，并新增一个 `NoSuchWarehouseError` 示例，明确仓库不存在时的错误响应格式。

## 如何达成设计目的

在 OpenAPI YAML 中两处新增内容：
1. 在 `GET /v1/config` 的 responses 部分，在 403 和 419 之间新增 404 响应，描述「warehouse 查询参数提供的仓库未找到」，响应体引用 `IcebergErrorResponse` schema，并使用 `NoSuchWarehouseError` 示例
2. 在 components/examples 部分新增 `NoSuchWarehouseError` 示例，展示一个 404 错误响应的具体 JSON 内容（error.message="The given warehouse does not exist", error.type="NoSuchWarehouseException", error.code=404）

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (+19/-0 lines)

**修改目的**：为 config 端点新增 404 响应定义和示例。

**工作逻辑**：
在 `GET /v1/config` 的 responses 中新增 404：
```yaml
404:
  description: Not Found - Warehouse provided in the `warehouse` query parameter is not found.
  content:
    application/json:
      schema:
        $ref: '#/components/schemas/IcebergErrorResponse'
      examples:
        NoSuchWarehouseExample:
          $ref: '#/components/examples/NoSuchWarehouseError'
```
在 components/examples 中新增示例：
```yaml
NoSuchWarehouseError:
  summary: The requested warehouse does not exist
  value: {
    "error": {
      "message": "The given warehouse does not exist",
      "type": "NoSuchWarehouseException",
      "code": 404
    }
  }
```
示例使用了与 `NoSuchNamespaceError` 等其他错误示例一致的 `IcebergErrorResponse` 格式（`error.message`/`error.type`/`error.code`），错误类型命名为 `NoSuchWarehouseException`，与项目中其他「NoSuchXxxException」命名风格一致。

## 总结

本提交为 Iceberg REST Catalog OpenAPI 规范的 `GET /v1/config` 端点补充 404 Not Found 响应定义，覆盖「warehouse 查询参数指向不存在的仓库」场景，并配套新增 `NoSuchWarehouseError` 示例。这使规范更完整，便于服务端实现者和自动生成的客户端正确处理该错误场景。
