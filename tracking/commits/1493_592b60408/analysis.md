# 提交 1493：Core: Add missing REST endpoint definitions (#11756)

## 提交信息

- **序号**：1493 / 4088
- **哈希**：592b60408aaf1783f0ca4cdffb9a2b487c392806
- **短哈希**：592b60408
- **日期**：2024-12-15（Sun Dec 15 22:52:43 2024 -0800）
- **作者**：ajreid21 <alex.james.reid@gmail.com>
- **提交说明**：Core: Add missing REST endpoint definitions (#11756)
- **PR/Issue**：#11756

## 总体目的

Iceberg 的 REST Catalog 规范定义了一组 HTTP 端点（如 `HEAD /v1/{prefix}/namespaces/{namespace}` 用于判断 namespace 是否存在，`HEAD /v1/{prefix}/namespaces/{namespace}/tables/{table}` 用于判断 table 是否存在等）。客户端 `RESTSessionCatalog` 在调用相应方法（`tableExists`、`namespaceExists`、`viewExists`）前，需要通过 `Endpoint.check(endpoints, ...)` 校验服务端在配置（`endpoints` 属性）中是否声明了对应端点支持，从而决定走 HEAD 短路径还是回退到 GET 等替代路径。

此前代码中缺少若干端点的 `Endpoint` 常量定义与默认 `endpoints` 集合注册，导致：

1. `tableExists`、`namespaceExists`、`viewExists` 方法未做端点校验，可能在不支持 HEAD 的服务端上发起不被允许的请求，或无法利用服务端配置正确路由。
2. 表扫描计划（table scan plan）相关的新端点、表凭证（credentials）端点、视图存在性（HEAD）端点等没有常量定义，难以在配置层统一引用。

本提交补齐这些"缺失"的 REST 端点定义，使客户端的端点校验逻辑与服务端配置声明保持完整与一致。

## 如何达成设计目的

1. 在 `Endpoint.java` 中新增常量：namespace/table/view 的 `HEAD` 存在性检查端点、表凭证端点、表扫描计划的提交/获取/取消/获取任务端点。
2. 在 `ResourcePaths.java` 中补充对应的 URL 路径常量，供 `Endpoint` 引用。
3. 在 `RESTSessionCatalog.java` 的默认 endpoints 集合（`tableEndpoints()`、`viewEndpoints()`）中加入 `V1_NAMESPACE_EXISTS`、`V1_TABLE_EXISTS`、`V1_VIEW_EXISTS`，并在 `tableExists`、`namespaceExists`、`viewExists` 方法入口处加入 `Endpoint.check(...)` 调用，与服务端配置对齐。

注意：扫描计划与表凭证端点仅定义常量，并未在默认集合中注册，因为它们是较新特性，是否启用由服务端通过 `endpoints` 配置显式声明。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/Endpoint.java`

**修改目的**：补齐缺失的 REST 端点常量定义。

**工作逻辑**：每个 `Endpoint` 由 HTTP 方法 + `ResourcePaths` 中的路径模板组成。新增常量如下：

- `V1_NAMESPACE_EXISTS = create("HEAD", V1_NAMESPACE)`：namespace 存在性检查（HEAD）。
- `V1_TABLE_EXISTS = create("HEAD", V1_TABLE)`：table 存在性检查（HEAD）。
- `V1_TABLE_CREDENTIALS = create("GET", V1_TABLE_CREDENTIALS)`：获取表凭证。
- 表扫描计划端点区块：
  - `V1_SUBMIT_TABLE_SCAN_PLAN = create("POST", V1_TABLE_SCAN_PLAN_SUBMIT)`
  - `V1_FETCH_TABLE_SCAN_PLAN = create("GET", V1_TABLE_SCAN_PLAN)`
  - `V1_CANCEL_TABLE_SCAN_PLAN = create("DELETE", V1_TABLE_SCAN_PLAN)`
  - `V1_FETCH_TABLE_SCAN_PLAN_TASKS = create("POST", V1_TABLE_SCAN_PLAN_TASKS)`
- `V1_VIEW_EXISTS = create("HEAD", V1_VIEW)`：view 存在性检查（HEAD）。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java`

**修改目的**：在默认端点集合中注册存在性检查端点，并在对应方法中加入端点校验。

**工作逻辑**：

- 在 `tableEndpoints()` 默认集合中追加 `V1_TABLE_EXISTS` 与 `V1_NAMESPACE_EXISTS`；在 `viewEndpoints()` 中追加 `V1_VIEW_EXISTS`。这意味着当服务端未显式配置 `endpoints` 时，客户端默认认为这些 HEAD 端点可用。
- 在 `tableExists(SessionContext, TableIdentifier)` 入口处加入 `Endpoint.check(endpoints, Endpoint.V1_TABLE_EXISTS);`，在执行前断言该端点被服务端声明支持。
- 在 `namespaceExists(...)` 入口加入 `Endpoint.check(endpoints, Endpoint.V1_NAMESPACE_EXISTS);`。
- 在 `viewExists(...)` 入口加入 `Endpoint.check(endpoints, Endpoint.V1_VIEW_EXISTS);`。

`Endpoint.check` 会在端点未被声明支持时抛出异常，提示调用方该服务端不支持此操作，应改用替代路径（例如先 list 再判断）。

### `core/src/main/java/org/apache/iceberg/rest/ResourcePaths.java`

**修改目的**：补充新增端点对应的 URL 路径模板常量。

**工作逻辑**：新增以下路径常量，供 `Endpoint` 引用：

- `V1_TABLE_CREDENTIALS = "/v1/{prefix}/namespaces/{namespace}/tables/{table}/credentials"`
- `V1_TABLE_SCAN_PLAN_SUBMIT = "/v1/{prefix}/tables/{table}/plan"`
- `V1_TABLE_SCAN_PLAN = "/v1/{prefix}/tables/{table}/plan/{plan-id}"`
- `V1_TABLE_SCAN_PLAN_TASKS = "/v1/{prefix}/tables/{table}/tasks"`

## 小结

- **成效**：补齐了 REST Catalog 客户端缺失的端点常量与默认注册，使 `tableExists`、`namespaceExists`、`viewExists` 现在会按规范进行端点校验；同时为表凭证与表扫描计划等较新特性预留了端点定义，便于后续实现引用。
- **影响范围**：仅 `core` 模块的 3 个文件（`Endpoint.java`、`RESTSessionCatalog.java`、`ResourcePaths.java`），共 27 行新增，无破坏性变更。
- **回迁到 1.4.x 的注意事项**：本提交是 REST 客户端对规范的完善，属于兼容性增强（在支持 HEAD 的服务端上走更高效路径，否则抛出明确异常而非默默发起请求）。**可考虑回迁**到 1.4.x，但需注意：
  - 1.4.x 时期的 `RESTSessionCatalog` 可能尚未引入扫描计划（scan plan）相关 API，回迁时应只取 `V1_*_EXISTS` 端点与 `V1_TABLE_CREDENTIALS` 部分，扫描计划相关常量可保留但需确认 `ResourcePaths` 中路径与 1.4.x 一致。
  - 默认集合中加入 `V1_*_EXISTS` 意味着客户端默认认为服务端支持 HEAD 端点；若 1.4.x 配套的 REST 服务端实现不支持 HEAD，则会触发 `Endpoint.check` 失败，需评估服务端兼容性后再回迁。
  - 若 1.4.x 已有等价的端点校验逻辑或常量（可能命名不同），需合并去重，避免冲突。
