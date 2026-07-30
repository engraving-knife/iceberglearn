# 提交 3107：AWS, Azure, Core, GCP: Pass planId when refreshing vended credentials (#14767)

## 提交信息

- **序号**：3107 / 4088
- **哈希**：cbf07cba8c89a49e42baa7073ee15bd914d2e1e0
- **短哈希**：cbf07cba8
- **日期**：2026-01-13
- **作者**：Eduard Tudenhoefner
- **提交说明**：AWS, Azure, Core, GCP: Pass planId when refreshing vended credentials (#14767)
- **PR/Issue**：#14767

## 总体目的

本提交服务于 REST Catalog 的"远程扫描规划"（rest-scan-planning）特性。在该特性下，扫描不再由客户端本地规划，而是把规划请求发给 REST 服务端，服务端返回一个 `planId`（规划任务标识）以及一批 vended credentials（服务端下发的短期存储凭证，按 prefix 作用域分发，用于读取该规划涉及的数据文件）。这些短期凭证会过期，凭证提供方（AWS S3 的 `VendedCredentialsProvider`、Azure ADLS 的 `VendedAdlsCredentialProvider`、GCP GCS 的 `OAuth2RefreshCredentialsHandler`）在过期时会回打到 `/v1/credentials` 端点刷新。

问题在于：刷新请求此前并不携带 `planId`，服务端无法把"这次刷新"与"某个具体规划任务"关联起来，也就无法下发与该 plan 作用域匹配的存储凭证，刷新出来的可能是通用凭证而非 plan 级别凭证，削弱了凭证最小化作用域的安全收益，也可能导致权限不足。本提交的目标是在凭证刷新链路上贯通 `planId`：让扫描在拿到 `planId` 后，构造一个内嵌 `planId` 的专用 FileIO，其凭证提供方在刷新时把 `planId` 作为查询参数发送给 `/v1/credentials?planId=...`，使服务端能精准下发该 plan 作用域内的凭证。

## 如何达成设计目的

整体设计分两层。核心层在 `core` 模块：新增 `REST_SCAN_PLAN_ID` 属性常量；`RESTSessionCatalog` 创建 `RESTTable` 时把 catalog properties 与 hadoop conf 一并传入；`RESTTable` 再把它们连同 `io()`（FileIO）传给 `RESTTableScan`；`RESTTableScan` 在收到服务端返回的凭证后，用 `CatalogUtil.loadFileIO` 构造一个"携带 planId 与 storage credentials 的专用 FileIO"（`fileIOForPlanId`），并重写 `io()` 让后续数据读取使用这个专用 FileIO。云端层在 AWS/Azure/GCP 三个凭证提供方中读取 `REST_SCAN_PLAN_ID` 属性，刷新凭证时在 GET 请求上附加 `planId` 查询参数。每个云模块都新增了 `planIdQueryParamIsSent` 测试，用 mockserver 校验刷新请求确实带上了 `planId` 且多次刷新会重复发送。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/VendedCredentialsProvider.java` (+4/-1 lines)

**修改目的**：AWS S3 凭证刷新时携带 `planId` 查询参数。

**工作逻辑**：构造时从 properties 读取 `REST_SCAN_PLAN_ID` 存入 `planId` 字段（缺省为 `null`）；在 `loadCredentials` 调用 `httpClient().get(...)` 时，把原先写死的 `null` 查询参数改为 `null != planId ? Map.of("planId", planId) : null`。这样当 planId 存在时刷新请求会带上 `?planId=...`，服务端可据此下发 plan 作用域的 S3 凭证；无 planId 时行为不变，保持向后兼容。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestVendedCredentialsProvider.java` (+50/-0 lines)

**修改目的**：验证 AWS 凭证刷新会发送 `planId` 查询参数。

**工作逻辑**：新增 `planIdQueryParamIsSent` 测试。用 mockserver 注册一个期望请求 `/v1/credentials` 且带 `planId=randomPlanId` 查询参数的 stub，构造含 `REST_SCAN_PLAN_ID` 的 properties 创建 provider，连续调用两次 `resolveCredentials()`，并断言两次解析到的凭证不同实例（未走缓存），最后用 `mockServer.verify(mockRequest, VerificationTimes.exactly(2))` 校验刷新请求确实命中了带 planId 的端点两次。

### `azure/src/main/java/org/apache/iceberg/azure/adlsv2/VendedAdlsCredentialProvider.java` (+4/-1 lines)

**修改目的**：Azure ADLS 凭证刷新时携带 `planId` 查询参数。

**工作逻辑**：与 AWS 完全对称——构造时读取 `REST_SCAN_PLAN_ID`，`loadCredentials` 中 GET 请求的查询参数由 `null` 改为 `null != planId ? Map.of("planId", planId) : null`，使 Azure SAS token 刷新也能关联到具体 plan。

### `azure/src/test/java/org/apache/iceberg/azure/adlsv2/TestVendedAdlsCredentialProvider.java` (+44/-0 lines)

**修改目的**：验证 Azure 凭证刷新会发送 `planId` 查询参数。

**工作逻辑**：新增 `planIdQueryParamIsSent` 测试，注册带 `planId` 查询参数的 `/v1/credentials` stub，连续两次调用 `credentialForAccount(STORAGE_ACCOUNT).block()`，校验返回的 SAS token 正确且 mockserver 命中两次，证明刷新会重复发送 planId。

### `core/src/main/java/org/apache/iceberg/rest/RESTCatalogProperties.java` (+2/-0 lines)

**修改目的**：定义 `planId` 的属性键常量。

**工作逻辑**：新增 `public static final String REST_SCAN_PLAN_ID = "rest-scan-plan-id";`，作为整个链路传递 planId 的统一属性键，供扫描层注入、凭证层读取。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (+3/-1 lines)

**修改目的**：创建 `RESTTable` 时把 catalog properties 与 hadoop conf 传入。

**工作逻辑**：在 `loadTable`/创建 `RESTTable` 的构造调用末尾追加 `properties()` 与 `conf` 两个参数，使 RESTTable 持有 catalog 级配置与 Hadoop 配置，便于后续扫描构造带 planId 的 FileIO。

### `core/src/main/java/org/apache/iceberg/rest/RESTTable.java` (+9/-4 lines)

**修改目的**：`RESTTable` 持有 catalog properties 与 hadoop conf，并下传给扫描。

**工作逻辑**：新增字段 `catalogProperties`、`hadoopConf`，构造函数增加这两个参数并赋值；在 `newScan()` 创建 `RESTTableScan` 时把 `io()`（表的 FileIO）、`catalogProperties`、`hadoopConf` 一并传入，让扫描具备构造专用 FileIO 的全部上下文。

### `core/src/main/java/org/apache/iceberg/rest/RESTTableScan.java` (+47/-3 lines)

**修改目的**：扫描拿到 planId 与凭证后构造专用 FileIO，使后续数据读取与凭证刷新都绑定到该 plan。

**工作逻辑**：
- 新增字段 `catalogProperties`、`hadoopConf`、`tableIo`（表原始 FileIO）、`fileIOForPlanId`（plan 专用 FileIO），构造函数相应增加参数。
- 重写 `io()`：`return null != fileIOForPlanId ? fileIOForPlanId : tableIo;`——一旦为某 plan 构造了专用 FileIO，后续文件读取就改用它，从而其凭证提供方在刷新时能带上 planId。
- 新增 `fileIOForPlanId(List<Credential> storageCredentials)`：用 `CatalogUtil.loadFileIO` 加载 FileIO（实现类取 `catalogProperties` 中的 `file-io-impl`，缺省 `org.apache.iceberg.io.ResolvingFileIO`），关键在于把 `REST_SCAN_PLAN_ID=planId` 通过 `buildKeepingLast()` 注入到传给 FileIO 的 properties 中，并把服务端下发的 `Credential` 列表转成 `StorageCredential` 一并传入。这样 FileIO 内部的凭证提供方就能读到 planId。
- 在 `doExecutePlan`（拿到 planId 后）与 `fetchPlanningResult`（轮询规划结果）中，只要 `response.credentials()` 非空就调用 `fileIOForPlanId(...)` 设置 `this.fileIOForPlanId`，确保无论是首次规划还是轮询结果阶段拿到的凭证都会触发专用 FileIO 的构建。

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/OAuth2RefreshCredentialsHandler.java` (+4/-1 lines)

**修改目的**：GCP GCS OAuth2 凭证刷新时携带 `planId` 查询参数。

**工作逻辑**：与 AWS/Azure 对称，构造时读取 `REST_SCAN_PLAN_ID`，`refreshAccessToken` 中 GET 请求的查询参数由 `null` 改为 `null != planId ? Map.of("planId", planId) : null`。

### `gcp/src/test/java/org/apache/iceberg/gcp/gcs/TestOAuth2RefreshCredentialsHandler.java` (+42/-0 lines)

**修改目的**：验证 GCP 凭证刷新会发送 `planId` 查询参数。

**工作逻辑**：新增 `planIdQueryParamIsSent` 测试，注册带 `planId` 查询参数的 `/v1/credentials` stub，连续两次调用 `refreshAccessToken()`，断言两次返回的 `AccessToken` 不是同一实例，并用 `mockServer.verify(..., VerificationTimes.exactly(2))` 校验刷新请求带 planId 命中两次。

## 总结

本提交在 REST Catalog 远程扫描规划场景下打通了 `planId` 在凭证刷新链路上的传递：扫描层把服务端下发的 planId 与存储凭证封装进一个专用 FileIO，AWS/Azure/GCP 三大云的 vended credentials 提供方读取该 planId，在刷新短期凭证时以查询参数形式回传给 `/v1/credentials?planId=...`，使服务端能够下发与具体规划任务作用域匹配的存储凭证。核心价值在于保证 plan 级别凭证在过期刷新后仍保持最小作用域，强化安全性并避免凭证权限与 plan 不匹配导致的读取失败，同时通过三个云模块的测试覆盖保证了行为正确性。
