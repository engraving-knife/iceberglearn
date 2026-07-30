# 提交 1620 e86d25f28 分析

## 提交信息
- 哈希：e86d25f28c8157ce9292182ea3580eb3757fba08
- 日期：2025-01-22 17:53:07 +0100
- 作者：Yuya Ebihara
- 消息：Core: Add missing default HEAD endpoints and V1_COMMIT_TRANSACTION (#11980)

## 总体目的

本提交修复 REST Catalog 客户端在"服务端未返回 `endpoints` 字段时使用默认端点集"这一场景下的两处不一致问题：一处是真正的 bug，另一处是文档与实现不一致。

**背景**：Iceberg REST Catalog 协议规定，服务端在响应 `/v1/config` 时可以返回一个可选的 `endpoints` 字段，声明本服务支持的端点列表。客户端据此决定能否调用某端点（如 `commitTransaction`、`listTables` 等）。如果服务端不返回 `endpoints` 字段，客户端就假定服务端支持"一组默认端点"。这组默认端点在两处定义：

1. **Java 客户端实现**：`RESTSessionCatalog.DEFAULT_ENDPOINTS` 静态集合，是客户端实际使用的事实来源。`commitTransaction`、`listTables` 等方法在调用前会通过 `Endpoint.check(endpoints, Endpoint.V1_xxx)` 校验该端点是否在集合内，不在则抛异常。
2. **OpenAPI 规范文档**：`open-api/rest-catalog-open-api.yaml` 中 `/v1/config` 端点的 description 文本列出了"默认假定的端点清单"，供规范读者与服务端实现者参考。

**问题一（bug）**：OpenAPI 文档的默认端点清单中包含 `POST /v1/{prefix}/transactions/commit`（对应 `Endpoint.V1_COMMIT_TRANSACTION`），即规范承诺"默认支持 commit-transaction"。但 Java 客户端的 `DEFAULT_ENDPOINTS` 集合中**没有**包含 `V1_COMMIT_TRANSACTION`。后果是：当服务端不返回 `endpoints` 字段时，客户端会拒绝调用 `commitTransaction`（`Endpoint.check` 抛异常），即使服务端实际支持该端点。这违反了规范承诺，导致多表事务提交在默认配置下失败。

**问题二（文档不一致）**：OpenAPI 文档的默认端点清单中**缺少两个 HEAD 端点**：`HEAD /v1/{prefix}/namespaces/{namespace}` 和 `HEAD /v1/{prefix}/namespaces/{namespace}/tables/{table}`（对应 `Endpoint.V1_NAMESPACE_EXISTS` 和 `Endpoint.V1_TABLE_EXISTS`）。但 Java 客户端的 `DEFAULT_ENDPOINTS` 集合中**已经**包含这两个端点。也就是说，客户端实际默认支持 namespace/table 的存在性检查，但规范文档没写明，让服务端实现者无法从文档得知这两个端点属于默认集合。

本提交通过两处最小改动同时修复这两个问题：在 Java `DEFAULT_ENDPOINTS` 中补加 `V1_COMMIT_TRANSACTION`，并在 OpenAPI 文档默认清单中补加两个 HEAD 端点，使实现与规范保持一致。

## 如何达成设计目的

设计思路是分别对齐两端：

1. **Java 侧**：在 `RESTSessionCatalog.DEFAULT_ENDPOINTS` 不可变集合的构建链中，于 `V1_REPORT_METRICS` 之后追加 `.add(Endpoint.V1_COMMIT_TRANSACTION)`。这样当服务端不返回 `endpoints` 时，客户端的默认端点集就包含 commit-transaction，`commitTransaction` 方法中的 `Endpoint.check` 校验通过，可以正常发起 `POST /v1/{prefix}/transactions/commit` 请求。

2. **OpenAPI 侧**：在 `rest-catalog-open-api.yaml` 中 `/v1/config` 端点 description 的默认端点清单里，分别在第 111 行和第 123 行附近插入两行：
   - `HEAD /v1/{prefix}/namespaces/{namespace}`（位于 `GET .../namespaces/{namespace}` 之后、`DELETE .../namespaces/{namespace}` 之前）；
   - `HEAD /v1/{prefix}/namespaces/{namespace}/tables/{table}`（位于 `GET .../tables/{table}` 之后、`POST .../tables/{table}` 之前）。

   这样文档列出的默认端点顺序与 REST 风格惯例（GET → HEAD → POST → DELETE）一致，且与 Java 客户端 `DEFAULT_ENDPOINTS` 的内容完全对齐。

两处改动都是纯增量（共 5 行新增、0 行删除），不修改任何已有端点的处理逻辑，向后兼容。

### 修改详情

#### core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java

在 `DEFAULT_ENDPOINTS` 集合构建链中，紧接 `.add(Endpoint.V1_REPORT_METRICS)` 之后新增一行 `.add(Endpoint.V1_COMMIT_TRANSACTION)`。修改后的集合包含 16 个端点（原 15 个 + V1_COMMIT_TRANSACTION）。

这个集合的使用路径：`RESTSessionCatalog` 初始化时调用 `/v1/config`，若响应的 `endpoints` 字段为空，则 `this.endpoints` 取 `DEFAULT_ENDPOINTS`（或并上 `VIEW_ENDPOINTS`，取决于 `view-endpoints-supported` 属性）。之后 `commitTransaction(SessionContext, List<TableCommit>)` 在执行 `client.post(paths.commitTransaction(), ...)` 之前调用 `Endpoint.check(endpoints, Endpoint.V1_COMMIT_TRANSACTION)`，若端点不在集合内则抛异常。本提交让默认集合包含该端点，从而使默认配置下的 commit-transaction 调用通过校验。

#### open-api/rest-catalog-open-api.yaml

在 `/v1/config` 端点 description 的"默认端点清单"中插入两行：

1. 在 `GET /v1/{prefix}/namespaces/{namespace}` 与 `DELETE /v1/{prefix}/namespaces/{namespace}` 之间插入 `HEAD /v1/{prefix}/namespaces/{namespace}`。
2. 在 `GET /v1/{prefix}/namespaces/{namespace}/tables/{table}` 与 `POST /v1/{prefix}/namespaces/{namespace}/tables/{table}` 之间插入 `HEAD /v1/{prefix}/namespaces/{namespace}/tables/{table}`。

修改后文档清单共列出 16 个默认端点，与 Java `DEFAULT_ENDPOINTS` 集合一一对应（清单中的 `POST /v1/{prefix}/transactions/commit` 对应 `V1_COMMIT_TRANSACTION`，原本就在文档中）。

## 小结

此次修复使 Java REST 客户端的默认端点集与 OpenAPI 规范文档完全对齐：commit-transaction 端点在默认配置下可用，两个 HEAD（namespace/table exists）端点也被规范文档明确列入默认集合。修复了多表事务在默认配置下被错误拒绝的 bug，并消除了文档与实现的不一致。

影响范围：
- **Java 侧**：仅影响"服务端不返回 `endpoints` 字段"这一默认配置场景下的 `commitTransaction` 行为。若服务端显式返回了 `endpoints` 字段（无论是否包含 `V1_COMMIT_TRANSACTION`），客户端行为不变（仍以服务端返回为准）。若服务端确实不支持 commit-transaction 却又不返回 `endpoints` 字段，则客户端原本会拒绝、现在会尝试调用并收到 404/405——但这属于服务端配置不当，且与规范承诺一致。
- **OpenAPI 侧**：仅文档文本变化，不影响任何代码路径。服务端实现者现在能从文档得知两个 HEAD 端点属于默认集合。

回迁到 1.4.x 分支的注意事项：1.4.x 是较早的维护分支，其 REST Catalog 实现可能尚未引入 `V1_COMMIT_TRANSACTION` 端点或 `Endpoint` 枚举中可能没有该常量。回迁前需确认 1.4.x 的 `core` 模块是否已具备：
- `Endpoint.V1_COMMIT_TRANSACTION` 枚举常量；
- `RESTSessionCatalog.commitTransaction` 方法及 `paths.commitTransaction()` 路径；
- `CommitTransactionRequest` 类型。

若 1.4.x 尚未引入多表事务支持，则 Java 侧改动不适用，只能回迁 OpenAPI 文档改动（且需确认 1.4.x 的 yaml 中默认端点清单结构是否与 main 一致）。OpenAPI 文档改动的回迁风险极低，纯属文本补充。Java 侧改动本身是单行追加，回迁后无破坏性风险，但需要 1.4.x 的 core 已支持该端点的实际调用逻辑（否则把端点加入默认集合却无对应实现，反而会引发调用失败）。
