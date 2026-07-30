# 提交 3122：OpenAPI: Add REST endpoint for registering views (#14869)

## 提交信息

- **序号**：3122 / 4088
- **哈希**：bf549615515157addf94d637538b34f847aea061
- **短哈希**：bf5496155
- **日期**：2026-01-16 21:05:12 -0800
- **作者**：Ajantha Bhat
- **提交说明**：OpenAPI: Add REST endpoint for registering views (#14869)
- **PR/Issue**：#14869

## 总体目的

前一个提交（#14868，序号 3119）为视图目录接口新增了 `registerView` 能力，并在 `BaseMetastoreViewCatalog` 中提供了通用实现，但当时明确将 REST catalog 排除在外（测试用 `assumeThat` 跳过 REST）。要让 REST catalog 也支持注册视图，首先需要在 REST Catalog 的 OpenAPI 规范中定义对应的 HTTP 端点、请求/响应模型与错误码，作为服务端实现与客户端生成的契约基础。

本提交即在 `open-api/rest-catalog-open-api.yaml` 中新增 `POST /v1/{prefix}/namespaces/{namespace}/register-view` 端点，并定义 `RegisterViewRequest` 模型（含 `name` 与 `metadata-location`），同时在 Python 模型文件 `rest-catalog-open-api.py` 中同步生成对应的 `RegisterViewRequest` 类。该端点设计与已有的 `register-table` 端点对称：使用相同的路径风格、`idempotency-key` 参数、`LoadViewResponse` 成功响应（200），以及命名空间不存在（404）、同名表/视图已存在（409）等错误响应。这是为 REST catalog 落地 `registerView` 功能铺路的第一步——先定规范，后续实现再依此编码。

## 如何达成设计目的

在 OpenAPI YAML 的 `paths` 中新增 `register-view` 路径及其 POST 操作，参照 `register-table` 的结构定义参数、请求体、响应码与错误示例；在 `components/schemas` 中新增 `RegisterViewRequest` schema；在 Python 模型文件中新增对应的 pydantic `RegisterViewRequest` 类（含 `metadata-location` 别名映射）。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (+65/-0 lines)

**修改目的**：定义注册视图的 REST 端点与请求模型。

**工作逻辑**：
在 `paths` 中新增 `/v1/{prefix}/namespaces/{namespace}/register-view`，复用 `prefix`、`namespace` 路径参数。POST 操作 `operationId: registerView`，描述为 "Register a view in the given namespace using given metadata file location."，并带 `idempotency-key` 参数（与建表/注册表一致，支持幂等重试）。请求体 `required: true`，引用 `RegisterViewRequest` schema。响应码：200 引用 `LoadViewResponse`（与加载视图一致的响应）；400/401/403/419/503/5XX 引用既有错误响应；404 描述命名空间不存在并引用 `NoSuchNamespaceError` 示例；409 描述标识符已作为表或视图存在，引用 `ViewAlreadyExistsError` 示例——这与 #14868 中 `registerView` 实现抛出 `AlreadyExistsException`（视图已存在或同名表已存在）的语义对应。

在 `components/schemas` 中新增 `RegisterViewRequest`：`type: object`，`required: [name, metadata-location]`，属性 `name`（string）与 `metadata-location`（string），与 `RegisterTableRequest` 结构一致。

### `open-api/rest-catalog-open-api.py` (+5/-0 lines)

**修改目的**：同步 Python 模型定义（由 OpenAPI 生成）。

**工作逻辑**：
新增 pydantic 模型 `class RegisterViewRequest(BaseModel)`，含字段 `name: str` 与 `metadata_location: str = Field(..., alias='metadata-location')`。`alias` 映射使 JSON 中的 kebab-case `metadata-location` 能正确反序列化为 Python 的 snake_case `metadata_location`，与 `RegisterTableRequest` 的处理方式一致。该文件是 OpenAPI 规范的 Python 表达，用于客户端/服务端代码生成与校验。

## 总结

本提交为 REST Catalog 规范补齐了视图注册端点 `POST /v1/{prefix}/namespaces/{namespace}/register-view` 及 `RegisterViewRequest` 模型，设计上与已有的 `register-table` 端点完全对称，并涵盖了幂等键、命名空间不存在、同名表/视图冲突等响应。这是在 #14868 引入 `registerView` API 之后、为 REST catalog 落地该能力所做的规范层面铺垫，为后续服务端实现与客户端生成提供了契约依据。
