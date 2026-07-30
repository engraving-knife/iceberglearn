# 提交 1275：OpenAPI: Add endpoint for refreshing vended credentials (#11281)

## 提交信息

- **序号**：1275 / 4088
- **哈希**：35a02d035e40344523fdb7a0933e07a8433ea763
- **短哈希**：35a02d035
- **日期**：2024-10-24（Thu Oct 24 16:07:35 2024 +0200）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：OpenAPI: Add endpoint for refreshing vended credentials (#11281)
- **PR/Issue**：#11281

## 总体目的

在 Iceberg REST Catalog 协议中，服务端在响应 `loadTable` / `createTable` 等请求时，可通过 `LoadTableResult.config` 或 `storage-credentials` 字段下发"vended credentials"（被代理的临时存储凭证，例如短时有效的 S3 access key / session token），让客户端无需持有底层存储的长期凭证即可访问表的数据文件。这种短时凭证通常有时效（几分钟到几小时不等），过期后客户端会因 401/403 之类错误而无法继续读写数据。

本提交之前，REST Catalog OpenAPI 规范没有"独立刷新凭证"的端点。客户端若想拿到新凭证，唯一办法是重新调用 `loadTable` 拉取整张表的元数据——既浪费带宽（元数据可能很大），又会在客户端引发不必要的表缓存刷新，甚至触发 metadata location 变更等副作用。对于长会话（例如持续运行的 Spark/Flink 作业）这种开销不可忽视。

本提交新增一个独立的 `GET /v1/{prefix}/namespaces/{namespace}/tables/{table}/credentials` 端点，专门用于按需刷新某张表的 vended credentials，使客户端可在不重新加载表元数据的前提下获取一份新的临时凭证，从而支撑长时运行的任务。

## 如何达成设计目的

仅修改 OpenAPI 规范文件（`rest-catalog-open-api.yaml` 与自动生成的 `rest-catalog-open-api.py`），不涉及 Java/Python 客户端或服务端实现代码，属于"先定规范，后补实现"的协议演进。具体做法：

1. 在 `paths` 中新增 `GET /v1/{prefix}/namespaces/{namespace}/tables/{table}/credentials`，复用已有的 `prefix`/`namespace`/`table` 路径参数，沿用 REST Catalog 既有的错误响应模板（400/401/403/404/419/503/5XX），并定义 404 时为 `NoSuchTableException`；
2. 新增 `LoadCredentialsResponse` Schema：仅包含一个必填字段 `storage-credentials`（数组，元素类型复用已有的 `StorageCredential`），刻意与 `LoadTableResult` 中可能返回的 storage-credentials 形态保持一致，便于客户端复用同一套解析逻辑；
3. 在 `responses` 区块新增同名 `LoadCredentialsResponse` 响应引用，把 Schema 包装成标准响应；
4. Python 模型文件同步追加 `LoadCredentialsResponse` 类，使 pydantic 客户端代码生成器能识别该响应类型。

## 修改详情

### `open-api/rest-catalog-open-api.yaml`（修改，+55 行）

**修改目的**：声明新的"刷新 vended credentials"HTTP 端点及其响应模型。

**工作逻辑**：

- **新增路径 `paths./v1/{prefix}/namespaces/{namespace}/tables/{table}/credentials`**：复用三个公共路径参数；`get` 方法 `operationId` 为 `loadCredentials`，`summary`/`description` 均为 "Load vended credentials for a table from the catalog"，tag 归入 `Catalog API`。响应集合与 Iceberg REST 其它端点风格一致：
  - `200` → `LoadCredentialsResponse`；
  - `400` → `BadRequestErrorResponse`；
  - `401` → `UnauthorizedResponse`；
  - `403` → `ForbiddenResponse`；
  - `404` → 内联描述 "Not Found - NoSuchTableException, table to load credentials for does not exist"，content 引用 `IcebergErrorResponse` 与 `NoSuchTableError` 示例（与 `loadTable` 的 404 处理完全对齐）；
  - `419` → `AuthenticationTimeoutResponse`；
  - `503` → `ServiceUnavailableResponse`；
  - `5XX` → `ServerErrorResponse`。
- **新增 `components.schemas.LoadCredentialsResponse`**：`type: object`，`required: [storage-credentials]`，`storage-credentials` 为数组，元素 `$ref` 指向既有 `StorageCredential`。这里刻意只返回凭证本身，不附带 `config` map，把端点职责限定在"刷新 storage 凭证"上，避免与 `LoadTableResult.config` 的语义重叠。
- **新增 `components.responses.LoadCredentialsResponse`**：标准响应包装，`description` 为 "Table credentials result when loading credentials for a table"，content 为 `application/json` 引用上一步定义的 Schema。

### `open-api/rest-catalog-open-api.py`（修改，+6 行）

**修改目的**：保持 Python 端 pydantic 模型与 YAML 规范同步，便于 pyiceberg 等基于该 .py 文件生成的客户端识别新响应。

**工作逻辑**：在 `StorageCredential` 之后、`PlanStatus` 之前插入：

```python
class LoadCredentialsResponse(BaseModel):
    storage_credentials: List[StorageCredential] = Field(
        ..., alias='storage-credentials'
    )
```

字段名使用 `storage_credentials`（Python 命名风格），通过 `alias='storage-credentials'` 与 YAML 中的 JSON 字段名对齐；`...` 表示必填。结构与 `LoadTableResult` 中的 storage-credentials 同构，客户端可直接复用既有的凭证消费逻辑。

## 小结

- **成效**：补齐 REST Catalog 规范缺失的"按需刷新 vended credentials"能力，为后续服务端/客户端实现长会话场景下的凭证续期提供协议依据。客户端可在凭证临近过期或收到 401/403 时单独调用该端点，无需重新拉取整张表的元数据，显著降低长时任务的额外开销。
- **影响范围**：仅修改 OpenAPI 规范文件，无任何 Java/Python 运行时代码变更，纯协议增量，向后兼容。后续实现需在 `RESTSessionCatalog` 客户端与服务端 `CatalogHandlers`/`RESTCatalogServer` 中补出对应方法。
- **回迁到 1.4.x 的注意事项**：本提交只动规范，回迁零风险，可直接 cherry-pick。需要留意 1.4.x 分支上的 OpenAPI 规范版本是否已包含 `StorageCredential` schema（早期版本可能命名或字段不同），以及 1.4.x 客户端是否已具备消费 `LoadCredentialsResponse` 的能力——若仅回迁规范而不补出客户端实现，则该端点在 1.4.x 上仅是"可用但无人调用"的状态，需配合后续 Java/Spark/Flink 客户端实现 PR 一起评估。
