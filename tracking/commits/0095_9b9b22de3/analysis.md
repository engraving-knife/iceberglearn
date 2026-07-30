# 提交 0095：Spec: Fix error response model definition in OpenAPI spec (#8914)

## 提交信息

- **序号**：0095 / 4088
- **哈希**：9b9b22de303ef2075bc3c25c2ee4dc395498340f
- **短哈希**：9b9b22de3
- **日期**：2023-10-25 09:45:49 -0700
- **作者**：Drew Gallardo
- **提交说明**：Spec: Fix error response model definition in OpenAPI spec (#8914)
- **PR/Issue**：#8914

## 总体目的

这个提交修复了 Iceberg REST Catalog OpenAPI 规范中一个长期存在的"schema 与示例不一致"缺陷：所有错误响应（non-2xx）的 schema 引用此前指向的是内部的 `ErrorModel`（裸错误对象 `{message, type, code, stack}`），但规范里所有示例以及实际线上协议返回的都是带 `error` 外层包装的形式 `{"error": {message, type, code, stack}}`。本提交把全部 36 处错误响应的 `$ref` 从 `ErrorModel` 改为指向包装模型 `IcebergErrorResponse`，并为 `IcebergErrorResponse` 补充了 `description` 与 `example`，同时给 Python 端的 `IcebergErrorResponse` 模型补了 docstring，使规范的自描述与实际线上行为完全一致。

要理解这个修复的意义，需要先厘清规范里两个容易混淆的模型：

- **`ErrorModel`**（[rest-catalog-open-api.yaml:1060](file:///Users/fengxiaohang/trae/iceberglearn/open-api/rest-catalog-open-api.yaml)）：是错误负载的"内层"对象，描述错误本身的字段——`message`（人类可读信息）、`type`（内部异常类型，如 `NoSuchNamespaceException`）、`code`（HTTP 状态码 400-600）、`stack`（可选调用栈数组）。它是协议层的"错误内容"。
- **`IcebergErrorResponse`**（[rest-catalog-open-api.yaml:2455](file:///Users/fengxiaohang/trae/iceberglearn/open-api/rest-catalog-open-api.yaml)）：是错误响应的"外层包装"，`type: object`，必填一个 `error` 字段，该字段类型为 `ErrorModel`，且 `additionalProperties: false`（不允许额外字段）。也就是说真实线上返回的 JSON 永远是 `{"error": {...ErrorModel...}}`，而不是直接返回 `ErrorModel`。

修复前的规范存在一个明显矛盾：在 `paths` 下各端点的错误响应、以及 `components/responses` 下的命名响应里，schema 的 `$ref` 都写的是 `#/components/schemas/ErrorModel`，但同一位置给出的 `example` 却几乎清一色是 `{"error": {...}}` 这种带包装的形式。这意味着"声明"与"示例"在结构上对不上——按声明，客户端代码生成器会认为响应体直接就是 `{message, type, code, stack}`；按示例和实际行为，响应体却是 `{error: {message, type, code, stack}}`。

这个矛盾会带来实际影响：Iceberg REST Catalog 规范不仅给人读，也给各种语言的代码生成器（OpenAPI Generator 等）消费，用于自动生成 REST 客户端与服务端 stub。若 schema `$ref` 指向 `ErrorModel`，生成的错误响应类型就会缺少 `error` 外层包装，反序列化真实服务端返回的 `{"error": {...}}` 时会失败或字段全空。本提交把 `$ref` 统一改成 `IcebergErrorResponse`，让生成的类型与线上 JSON 结构对齐，是规范正确性的一次重要修复。

## 如何达成设计目的

整体思路是"让 schema 引用与示例/实际协议对齐"。改动分三部分：第一，在 `rest-catalog-open-api.yaml` 的 `paths` 段和 `components/responses` 段，把全部 36 处错误响应的 schema `$ref` 从 `#/components/schemas/ErrorModel` 改为 `#/components/schemas/IcebergErrorResponse`；第二，在 `components/schemas/IcebergErrorResponse` 定义上补 `description` 和一个典型 `example`，让该 schema 自描述更完整；第三，在 Python 模型文件 `rest-catalog-open-api.py` 的 `IcebergErrorResponse` 类上补一段 docstring，与 yaml 的 description 保持一致。注意 `ErrorModel` 本身并未被删除——它仍然是 `IcebergErrorResponse.error` 字段的类型，作为内层模型继续保留在 `components/schemas` 中。

## 修改详情

### [open-api/rest-catalog-open-api.py](file:///Users/fengxiaohang/trae/iceberglearn/open-api/rest-catalog-open-api.py)

**修改目的**：为 Python 端的 `IcebergErrorResponse` 模型补充 docstring，与 yaml 侧新增的 `description` 对齐，明确该模型是"所有非 2xx 错误响应的 JSON 包装"。

**工作逻辑**：在 `IcebergErrorResponse(BaseModel)` 类体内、`class Config` 之前新增 4 行 docstring：

```python
class IcebergErrorResponse(BaseModel):
    """
    JSON wrapper for all error responses (non-2xx)
    """

    class Config:
        extra = Extra.forbid

    error: Optional[ErrorModel] = None
```

该模型本身的结构未变（仍是 `error: Optional[ErrorModel]`，且 `Extra.forbid` 禁止额外字段），只是补了文档说明，强调它是一个"包装"而非内层错误对象。这与 yaml 侧把错误响应 `$ref` 指向该模型的意图一致。

### [open-api/rest-catalog-open-api.yaml](file:///Users/fengxiaohang/trae/iceberglearn/open-api/rest-catalog-open-api.yaml)

**修改目的**：修正错误响应 schema 引用错误，统一指向包装模型 `IcebergErrorResponse`，并为该 schema 补充 `description` 与 `example`。

**工作逻辑**：改动可分两组。

#### 1. 替换 36 处错误响应的 schema `$ref`

在 `paths` 段（各端点的 4xx/5xx 响应）和 `components/responses` 段（命名响应组件）中，把所有错误响应体里的：

```yaml
              schema:
                $ref: '#/components/schemas/ErrorModel'
```

统一改为：

```yaml
              schema:
                $ref: '#/components/schemas/IcebergErrorResponse'
```

共 36 处。涉及的位置包括：

- **`paths` 段各端点的错误响应**（约 30 处）：覆盖 `GET/POST /v1/namespaces`、`GET/DELETE /v1/namespaces/{namespace}`、`POST /v1/namespaces/{namespace}/tables`、`GET /v1/namespaces/{namespace}/tables`、`GET/HEAD /v1/namespaces/{namespace}/tables/{table}`、`DELETE /v1/namespaces/{namespace}/tables/{table}`、`POST /v1/namespaces/{namespace}/tables/{table}/rename`、`POST /v1/namespaces/{namespace}/tables/{table}/metrics`、`POST /v1/{prefix}/namespaces/...`、`POST /v1/{prefix}/tables/rename`、`GET /v1/{prefix}/tables/{table}`、`POST /v1/{prefix}/tables/{table}/metrics` 等端点的 400/404/406/409/419/500/502/503/504 响应。这些响应的 `example` 一直是 `{"error": {...}}` 形式，现在 schema 引用终于与示例一致。
- **`components/responses` 段的命名响应组件**（6 处）：`BadRequestResponse`（400）、`UnauthorizedResponse`（401）、`ForbiddenResponse`（403）、`AuthenticationTimeoutResponse`（419）、`ThrottledResponse`（429，对应 `TooManyRequestsResponse`）、`InternalServerErrorResponse`（500）。这些命名组件被多处 `$ref` 复用，修正后所有引用它们的端点都自动获得正确的 schema。

注意：`ErrorModel` schema 定义本身（[rest-catalog-open-api.yaml:1060](file:///Users/fengxiaohang/trae/iceberglearn/open-api/rest-catalog-open-api.yaml)）未被删除或修改，它仍然作为 `IcebergErrorResponse.error` 字段的类型存在，是协议的内层错误模型。

#### 2. 为 `IcebergErrorResponse` schema 补充 `description` 与 `example`

在 `components/schemas/IcebergErrorResponse` 定义上新增 `description` 和一个内联 `example`：

```yaml
    IcebergErrorResponse:
      description: JSON wrapper for all error responses (non-2xx)
      type: object
      required:
        - error
      properties:
        error:
          $ref: '#/components/schemas/ErrorModel'
      additionalProperties: false
      example:
        {
          "error": {
            "message": "The server does not support this operation",
            "type": "UnsupportedOperationException",
            "code": 406
          }
        }
```

此前该 schema 没有 `description`，也没有自带 `example`（示例都散落在各端点的响应里）。补上后，该 schema 成为自描述的：读者或代码生成器单看 `IcebergErrorResponse` 就能明白它是"所有非 2xx 错误响应的 JSON 包装"，且能直接看到一个完整的错误响应样例（406 Unsupported Operation）。`additionalProperties: false` 与 `error` 必填的约束未变，继续保证响应体只能形如 `{"error": {...}}`。

## 小结

该提交修正了 Iceberg REST Catalog OpenAPI 规范中错误响应 schema 引用与示例/实际协议不一致的缺陷，将全部 36 处错误响应 `$ref` 从内层 `ErrorModel` 改为包装模型 `IcebergErrorResponse` 并补全其自描述，使规范与线上 `{"error": {...}}` 的真实返回结构对齐，避免下游代码生成器产生与实际不符的错误响应类型，是 REST Catalog 规范正确性的一次重要修复。
