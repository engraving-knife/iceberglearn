# 提交 3346：OpenAPI: Include storage credentials for PlanTableScanResponse/FetchPlanningResultResponse on plan completed given include-credentials flag is set (#15524)

## 提交信息

- **序号**：3346 / 4088
- **哈希**：48518ee31229b659611b1500c012a385093f14ef
- **短哈希**：48518ee31
- **日期**：2026-03-06 10:31:19 -0800
- **作者**：Eduard Tudenhoefner
- **提交说明**：OpenAPI: Include storage credentials for PlanTableScanResponse/FetchPlanningResultResponse on plan completed given include-credentials flag is set (#15524)
- **PR/Issue**：#15524

## 总体目的

Iceberg 的 REST Catalog v2 协议引入了"凭据（credentials）"机制：服务端可以在响应中下发存储凭据（如 S3 的 access key/secret/token、GCS 的 OAuth2 token、ADLS 的 SAS token 等），让客户端在执行真正的数据读写时无需自行配置云存储认证。该机制由请求中的 `include-credentials` 标志触发。`RESTServerCatalogAdapter` 是 `open-api` 模块 testFixtures 中的测试夹具服务端适配器（被 REST OpenAPI 契约测试 / TCK 使用），用于在测试环境中模拟一个真实的 REST Catalog 后端。

在本次提交之前，`RESTServerCatalogAdapter` 仅在 `LoadTableResponse` 时按 `include-credentials` 注入凭据（通过 `applyCredentials` 把存储配置项塞进 `LoadTableResponse.config()`）。但 REST v2 规范的扫描计划接口（`PlanTableScan`、`FetchPlanningResult`）在其响应体 `PlanTableScanResponse` / `FetchPlanningResultResponse` 中同样定义了 `credentials` 字段，用于在扫描计划完成（`planStatus == COMPLETED`）时下发存储凭据，供后续读取文件使用。

本提交补齐这一缺口：当客户端请求 `include-credentials=true` 且扫描计划状态为 `COMPLETED` 时，在 `PlanTableScanResponse` 与 `FetchPlanningResultResponse` 中也返回存储凭据。这使 OpenAPI 契约测试能覆盖凭据下发的完整链路，确保参考实现（reference server fixture）与规范描述一致，避免客户端依赖 `LoadTableResponse` 凭据而忽略计划响应凭据时出现认证失败或测试盲区。

## 如何达成设计目的

整体思路是在 `handleRequest` 中扩展原先只针对 `LoadTableResponse` 的分支判断：把 `include-credentials` 判断提到外层，再按响应类型分发。对于新增的两个响应类型，由于它们是不可变对象，采用"重建响应"的方式——读取原响应各字段后，通过 builder 重新构造一个带 `credentials` 的新响应返回。凭据的生成被抽到一个新的私有方法 `createStorageCredentials`，集中处理 S3 / GCS / ADLS 三种存储后端的凭据挑选逻辑，避免与 `applyCredentials`（写入 table config）的既有逻辑混淆。

## 修改详情

### `open-api/src/testFixtures/java/org/apache/iceberg/rest/RESTServerCatalogAdapter.java` (+93/-5 lines)

**修改目的**：在计划扫描响应中按 `include-credentials` 下发存储凭据。

**工作逻辑**：

1. **重构 `handleRequest` 的分发逻辑**：原先 `if (restResponse instanceof LoadTableResponse)` 内部再判断 `INCLUDE_CREDENTIALS`；改动后把 `INCLUDE_CREDENTIALS` 判断提到外层，内部依次匹配 `LoadTableResponse`、`PlanTableScanResponse`（仅当 `planStatus() == COMPLETED`）、`FetchPlanningResultResponse`（同样要求 `COMPLETED`）。对 `LoadTableResponse` 仍走原 `applyCredentials`（写入 `config()`），对两个新响应则调用 `createStorageCredentials` 并通过 builder 重建响应。因返回类型与泛型 `T` 不一致，方法加了 `@SuppressWarnings("unchecked")` 并以 `(T)` 强转。

2. **重建 `PlanTableScanResponse`**：通过 `PlanTableScanResponse.builder()` 逐一回填 `planStatus`、`planId`、`fileScanTasks`、`specsById`，并新增 `withCredentials(createStorageCredentials(...))`。

3. **重建 `FetchPlanningResultResponse`**：同样回填 `planStatus`、`fileScanTasks`、`planTasks`、`specsById`，并附加 `credentials`。

4. **新增 `createStorageCredentials(Map<String, String> catalogConfig)`**：从 catalog 配置中挑选存储凭据，构造 `List<Credential>`，三类后端各自判定：
   - **S3**：当同时存在 `S3FileIOProperties.ACCESS_KEY_ID`、`SECRET_ACCESS_KEY`、`SESSION_TOKEN` 时构造一个 `prefix("s3")` 的 `ImmutableCredential`，写入三项 key，若存在 `SESSION_TOKEN_EXPIRES_AT_MS` 则一并带上。
   - **GCS**：当存在 `GCPProperties.GCS_OAUTH2_TOKEN` 时构造 `prefix("gcp")` 的凭据，附带可选的 `GCS_OAUTH2_TOKEN_EXPIRES_AT`。
   - **ADLS**：当任一 key 以 `AzureProperties.ADLS_SAS_TOKEN_PREFIX` 开头时构造 `prefix("adls")` 的凭据，并把所有以 `ADLS_SAS_TOKEN_PREFIX`、`ADLS_CONNECTION_STRING_PREFIX`、`ADLS_SAS_TOKEN_EXPIRES_AT_MS_PREFIX` 开头的配置项原样透传。

   该方法与 `applyCredentials` 形成对照：前者生成 v2 规范的 `Credential` 列表（按 prefix 分组），后者仍把存储相关 key 直接注入 `LoadTableResponse.config()`（兼容旧式表配置下发）。

## 总结

本提交把 `include-credentials` 凭据下发从 `LoadTableResponse` 扩展到 `PlanTableScanResponse` / `FetchPlanningResultResponse`，补齐了 REST v2 扫描计划链路上的凭据契约。核心价值在于让 OpenAPI 测试夹具服务端与规范保持一致，使凭据下发路径获得真实的端到端测试覆盖，避免客户端在仅依赖计划响应凭据时出现认证盲区。
