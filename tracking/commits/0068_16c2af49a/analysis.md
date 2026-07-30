# 提交 0068：Open-API: Make error required (#8765)

## 提交信息

- **序号**：0068 / 4088
- **哈希**：16c2af49a142d7044b619403de3e52aed13893e0
- **短哈希**：16c2af49a
- **日期**：2023-10-18 17:23:10 +0200
- **作者**：Fokko Driesprong
- **提交说明**：Open-API: Make error required (#8765)
- **PR/Issue**：#8765

## 总体目的

本提交收紧了 Iceberg REST Catalog OpenAPI 规范中错误响应的契约：把 `IcebergErrorResponse.error` 字段从"可选"改为"必填"。在 REST Catalog 协议里，所有非 2xx 响应都使用 `IcebergErrorResponse` 作为 JSON 包装体，其内部只有一个 `error` 字段指向 `ErrorModel`（包含 message、type、code 等错误详情）。

在改动前，`error` 字段被声明为 `Optional`（Pydantic 模型里 `Optional[ErrorModel] = None`，YAML 里没有列入 `required` 列表），这意味着协议理论上允许服务器返回一个"完全没有 error 体"的错误响应——一个空的 `{}` JSON。但这样的响应在语义上是毫无意义的：错误响应存在的唯一目的就是告诉客户端发生了什么错误，如果连 `error` 都没有，客户端既无法分类错误、也无法展示给用户，等于返回了一个无法被消费的错误。

把 `error` 设为必填后，规范明确要求：只要服务器返回非 2xx，响应体里就必须携带 `ErrorModel`，这迫使所有 REST Catalog 实现方在错误路径上都要构造有意义的错误信息，避免出现"哑错误"。这一改动对 Iceberg 演进的意义在于：加强了协议契约的严格性，提升跨实现（参考实现、第三方 catalog、客户端 SDK）的互操作性与可诊断性，让错误处理路径的契约和成功路径一样明确。

## 如何达成设计目的

改动极其聚焦，同步修改了 OpenAPI 规范的两个镜像文件：声明式 YAML 规范（`rest-catalog-open-api.yaml`）和等价的 Pydantic Python 模型（`rest-catalog-open-api.py`，通常由 YAML 生成或反之）。在 YAML 中给 `IcebergErrorResponse` schema 增加 `required: - error` 列表；在 Python 中把字段类型从 `Optional[ErrorModel] = None` 改为 `ErrorModel`（去掉 `Optional` 包装与默认值 `None`）。两处改动语义一致，确保无论是基于 YAML 生成客户端、还是直接使用 Python 模型，都会强制要求 `error` 字段存在。

## 修改详情

### `open-api/rest-catalog-open-api.yaml`

**修改目的**：在 `IcebergErrorResponse` schema 上声明 `error` 为必填字段，从规范层面强制错误响应必须携带错误体。

**工作逻辑**：在 `components.schemas.IcebergErrorResponse` 下、`properties` 之前插入：

```yaml
required:
  - error
```

改动后该 schema 变为：

```yaml
IcebergErrorResponse:
  type: object
  required:
    - error
  properties:
    error:
      $ref: '#/components/schemas/ErrorModel'
  additionalProperties: false
```

值得注意的几个细节：第一，`additionalProperties: false` 已经存在，说明响应体不允许携带 `error` 以外的字段，这次加上 `required` 后，`IcebergErrorResponse` 被完全约束为"有且仅有一个必填的 error 字段"，契约非常严格。第二，被引用的 `ErrorModel` 本身也已经是严格定义的——它自己的 `required` 列表里包含 `message`、`type`、`code` 三个字段（见 [rest-catalog-open-api.yaml:1062-1065](open-api/rest-catalog-open-api.yaml#L1062)），所以改动后整个错误响应链路从外层 wrapper 到内层 model 都被强制必填，客户端可以放心地直接读取 `response.error.message` 等字段而无需 null 检查。第三，规范中所有非 2xx 响应（404、406、409 等）都通过 `$ref` 引用 `IcebergErrorResponse`（见 [rest-catalog-open-api.yaml:2455-2465](open-api/rest-catalog-open-api.yaml#L2455) 处的 response 定义及其 example），因此这次改动一次性收紧了所有错误响应的契约。

### `open-api/rest-catalog-open-api.py`

**修改目的**：把 Pydantic 模型 `IcebergErrorResponse.error` 从可选改为必填，与 YAML 规范保持同步。

**工作逻辑**：原定义（[rest-catalog-open-api.py:458](open-api/rest-catalog-open-api.py#L458)）为：

```python
error: Optional[ErrorModel] = None
```

改为：

```python
error: ErrorModel
```

去掉 `Optional` 包装和 `= None` 默认值后，Pydantic 在反序列化时会要求 JSON 体里必须存在 `error` 键且值合法，否则抛 `ValidationError`。这与 YAML 里加 `required` 的语义完全对应。该类还保留了 `class Config: extra = Extra.forbid`，对应 YAML 里的 `additionalProperties: false`，两者继续一致。

## 小结

本提交通过在 OpenAPI 规范的 YAML 与 Python 模型中把 `IcebergErrorResponse.error` 从可选改为必填，强制 REST Catalog 的所有非 2xx 响应都必须携带结构化错误体，收紧了错误路径的协议契约，提升了跨实现的互操作性与可诊断性。
