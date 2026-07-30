# 提交 1769：Core: Remove namespace/table/view HEAD endpoints from defaults (#12351)

## 提交信息

- **序号**：1769 / 4088
- **哈希**：4958663b4c863a44e4895ecf965fabf541083b39
- **短哈希**：4958663b4
- **日期**：2025-02-21 09:42:14 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Remove namespace/table/view HEAD endpoints from defaults (#12351)
- **PR/Issue**：#12351

## 总体目的

这个提交旨在解决 REST Catalog 客户端与旧版（legacy）REST 服务器之间的兼容性问题。在之前的实现中，`RESTSessionCatalog` 的默认端点（`DEFAULT_ENDPOINTS` 和 `VIEW_ENDPOINTS`）包含了 `V1_NAMESPACE_EXISTS`、`V1_TABLE_EXISTS` 和 `V1_VIEW_EXISTS` 这些 HEAD 请求端点。这些 HEAD 端点是在后续版本中才添加到 REST Catalog 规范中的，旧版服务器并不支持它们。

问题在于，当客户端连接到一个不返回 `endpoints` 配置信息的旧版服务器时，客户端会使用默认端点集合。如果默认端点集合中包含了 HEAD 存在性检查端点，客户端就会尝试发送 HEAD 请求来检查 namespace/table/view 是否存在，而旧版服务器无法处理这些 HEAD 请求，从而导致错误。

通过从默认端点集合中移除这些 HEAD 端点，确保客户端在与不发送 endpoints 信息的旧版服务器通信时，会回退到使用 GET 请求（如 `V1_LOAD_NAMESPACE`、`V1_LOAD_TABLE`、`V1_LOAD_VIEW`）来检查存在性，从而保持与旧版服务器的向后兼容性。

## 如何达成设计目的

提交通过以下层次修改来达成目标：

1. **核心逻辑层**（`RESTSessionCatalog.java`）：从 `DEFAULT_ENDPOINTS` 集合中移除 `V1_NAMESPACE_EXISTS` 和 `V1_TABLE_EXISTS`，从 `VIEW_ENDPOINTS` 集合中移除 `V1_VIEW_EXISTS`。同时添加注释明确说明这些默认端点不应被更新，以维持与旧版服务器的向后兼容性。

2. **测试层**（`TestRESTCatalog.java` 和 `TestRESTViewCatalog.java`）：重构已有测试，提取公共验证方法，并新增针对旧版服务器的测试用例，验证在服务器不返回 endpoints 信息时客户端能正确回退到 GET 请求。

3. **规范文档层**（`rest-catalog-open-api.yaml`）：从 OpenAPI 规范中移除 namespace 和 table 的 HEAD 端点描述。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java`（修改, +5/-3 lines）

**修改目的**：从默认端点集合中移除 HEAD 存在性检查端点，确保与旧版服务器的兼容性。

**工作逻辑**：
- 在 `DEFAULT_ENDPOINTS` 集合中移除了 `Endpoint.V1_NAMESPACE_EXISTS` 和 `Endpoint.V1_TABLE_EXISTS` 两个端点。这两个端点对应 HEAD 请求，用于检查 namespace 和 table 是否存在。
- 在 `VIEW_ENDPOINTS` 集合中移除了 `Endpoint.V1_VIEW_EXISTS` 端点。
- 添加了两段注释，分别标注在 `DEFAULT_ENDPOINTS` 和 `VIEW_ENDPOINTS` 上方，明确说明"these default endpoints must not be updated in order to maintain backwards compatibility with legacy servers"（这些默认端点不得更新，以维持与旧版服务器的向后兼容性）。
- 移除这些端点后，当服务器不返回 endpoints 配置时，客户端会认为 HEAD 端点不被支持，从而回退到使用 GET 请求（`V1_LOAD_NAMESPACE`/`V1_LOAD_TABLE`/`V1_LOAD_VIEW`）来判断存在性。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java`（修改, +28/-12 lines）

**修改目的**：重构存在性检查回退测试，并新增针对旧版服务器的测试用例。

**工作逻辑**：
- 将原有的 `testNamespaceExistsFallbackToGETRequest` 测试中的核心验证逻辑提取为私有方法 `verifyNamespaceExistsFallbackToGETRequest(ConfigResponse configResponse)`，接受 ConfigResponse 参数以支持不同场景。
- 原有测试现在传入只包含 `V1_LOAD_NAMESPACE` 的 ConfigResponse，模拟旧版 REST 服务器只支持 GET 加载 namespace 的场景。
- 新增 `testNamespaceExistsFallbackToGETRequestWithLegacyServer` 测试，传入空的 ConfigResponse（不包含任何 endpoints），模拟旧版服务器不发送 endpoints 信息的场景，验证客户端仍能正确回退到 GET 请求。
- 对 table 存在性检查（`testTableExistsFallbackToGETRequest`）做了同样的重构和新增测试。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTViewCatalog.java`（修改, +18/-9 lines）

**修改目的**：重构 view 存在性检查回退测试，并新增针对旧版服务器的测试用例。

**工作逻辑**：
- 将 `viewExistsFallbackToGETRequest` 测试的验证逻辑提取为私有方法 `verifyViewExistsFallbackToGETRequest(ConfigResponse, Map<String,String>)`，同时支持传入 catalog 属性。
- 原有测试传入包含 `V1_LOAD_VIEW` 的 ConfigResponse，模拟服务器只支持 GET 加载 view 的场景。
- 新增 `viewExistsFallbackToGETRequestWithLegacyServer` 测试，传入空 ConfigResponse 和 `VIEW_ENDPOINTS_SUPPORTED=true` 属性，模拟旧版服务器不返回 endpoints 信息的场景。

### `open-api/rest-catalog-open-api.yaml`（修改, +0/-4 lines）

**修改目的**：从 OpenAPI 规范文档中移除 namespace 和 table 的 HEAD 端点描述。

**工作逻辑**：
- 从 namespace 端点的 `endpoints` 列表中移除了 `HEAD /v1/{prefix}/namespaces/{namespace}`。
- 从 table 端点的 `endpoints` 列表中移除了 `HEAD /v1/{prefix}/namespaces/{namespace}/tables/{table}`。
- 这些变更与代码变更保持一致，确保规范文档不再将 HEAD 端点列为默认支持端点。

## 小结

- **成效**：成功从默认端点集合中移除了 HEAD 存在性检查端点，确保客户端在与不返回 endpoints 配置的旧版 REST 服务器通信时，会自动回退到使用 GET 请求检查 namespace/table/view 的存在性，避免了因旧版服务器不支持 HEAD 请求而导致的错误。
- **影响范围**：涉及 REST Catalog 客户端核心模块（`RESTSessionCatalog`）、对应的测试模块以及 OpenAPI 规范文档。影响所有使用 REST Catalog 的场景，特别是与旧版服务器交互时的存在性检查行为。
- **回迁到 1.4.x 的注意事项**：建议回迁。此提交是对兼容性问题的修复，变更范围小且明确。回迁时需注意确保 1.4.x 分支中的 `RESTSessionCatalog`、`Endpoint` 枚举及相关测试代码与该提交的基础代码一致。此提交不依赖其他前置提交，可独立回迁。需注意 OpenAPI 规范文件的同步修改。
