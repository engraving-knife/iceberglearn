# 提交 1093：OpenAPI: Add endpoint field to CatalogConfig (#10928)

## 提交信息

- **序号**：1093 / 4088
- **哈希**：e0596fbba21df594b8aa6504f48f982c10941eba
- **短哈希**：e0596fbba
- **日期**：2024-08-23 20:26:29 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：OpenAPI: Add endpoint field to CatalogConfig (#10928)
- **PR/Issue**：#10928

## 总体目的

本提交为 Iceberg REST Catalog 的 OpenAPI 规范中的 `CatalogConfig` 响应模型新增一个可选的 `endpoints` 字段。该字段是一个字符串列表，用于声明服务器实际支持哪些 REST API 端点（endpoint），格式为 "<HTTP 动词> <OpenAPI 资源路径>"，例如 `GET /v1/{prefix}/namespaces/{namespace}`。

此前 REST Catalog 客户端在获取 catalog 配置后，无法得知服务器具体实现了哪些端点，只能按规范默认假设服务器支持全部标准端点。这在实际部署中存在问题：不同厂商的 REST Catalog 实现可能只实现部分能力（例如某些实现不支持 view，或仅支持读操作）。引入 `endpoints` 字段后，服务器可以显式声明其能力集，客户端据此决定是否调用某个端点、或在能力不足时给出更友好的提示，从而提升互操作性与容错性。

当服务器不返回 `endpoints` 字段时，客户端仍按原有行为假设支持默认的 13 个核心端点（namespace/table 的 CRUD、rename、transactions/commit、metrics 等），保持向后兼容。

## 如何达成设计目的

通过修改 OpenAPI 规范文件实现，无服务端/客户端 Java 代码改动。具体修改两个文件：

1. 在 `rest-catalog-open-api.yaml` 中：
   - 在 `/v1/config` 端点的响应描述里补充 `endpoints` 字段的语义说明，并列出未提供该字段时默认假设的 13 个端点列表。
   - 在 200 响应示例中加上 `endpoints` 数组示例。
   - 在 `components/schemas/CatalogConfig` 中新增 `endpoints` 字段定义（`type: array`，`items: type: string`），并附带描述与示例。

2. 在 `rest-catalog-open-api.py`（Pydantic 模型）中同步新增 `endpoints: Optional[List[str]]` 字段，保持 Python 模型与 YAML 规范一致。

设计上选择"可选字段 + 默认假设列表"的方式，既允许新服务器声明能力，又不破坏旧服务器与旧客户端的兼容性。

## 修改详情

### `open-api/rest-catalog-open-api.py`

**修改目的**：在 Pydantic 模型 `CatalogConfig` 中新增 `endpoints` 字段，与 YAML 规范同步。

**工作逻辑**：在 `CatalogConfig` 类中新增 `endpoints: Optional[List[str]] = Field(None, ...)`，描述为服务器支持的端点列表，格式为 "<HTTP 动词> <资源路径>"，动词与路径之间用空格分隔。示例列出 5 个常见端点。

### `open-api/rest-catalog-open-api.yaml`

**修改目的**：在 OpenAPI 规范中正式定义 `endpoints` 字段并补充文档说明。

**工作逻辑**：
- 在 `/v1/config` 的 `description` 中追加说明：catalog 配置含可选 `endpoints` 字段，列出服务器支持的端点；若服务器不发送该字段，则默认假设支持 13 个核心端点（namespace 的 GET/POST/DELETE、properties 的 POST、table 的 GET/POST/DELETE、register、metrics、rename、transactions/commit），并在文档中以列表形式逐条列出。
- 在 200 响应的 example 中加入 `endpoints` 数组，展示 5 个示例端点。
- 在 `components/schemas/CatalogConfig` 中新增 `endpoints` 字段定义：`type: array`，`items: type: string`，描述与示例与上述一致。

## 小结

- **成效**：为 REST Catalog OpenAPI 规范引入 `endpoints` 能力声明字段，使服务器能够显式声明支持的端点集合，客户端可据此进行能力发现与适配，同时通过"字段缺省即默认全集"的设计保证向后兼容。
- **影响范围**：仅修改 `open-api/` 目录下的规范文件（`.py` 与 `.yaml` 各一个），不涉及 Java 服务端或客户端实现代码。下游实现方需在服务端按需返回该字段、在客户端按需消费该字段。
- **回迁到 1.4.x 的注意事项**：这是 OpenAPI 规范的扩展性增强，新增可选字段，向后兼容，**适合回迁到 1.4.x**。1.4.x 的 REST Catalog 实现可以安全地引入此规范变更：旧客户端忽略新字段，新客户端在字段缺失时回退到默认端点集合，风险极低。回迁时只需将这两个规范文件的对应部分 cherry-pick 即可，无 Java 代码依赖。
