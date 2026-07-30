# 提交 3088：Core: Add storage credentials to FetchPlanningResultResponse (#14994)

## 提交信息

- **序号**：3088 / 4088
- **哈希**：4a4d73408aa771ed7ec2f4e94ca3e1b2df03c1de
- **短哈希**：4a4d73408
- **日期**：2026-01-09
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Add storage credentials to FetchPlanningResultResponse (#14994)
- **PR/Issue**：#14994

## 总体目的

该提交为 Iceberg REST 协议中的 `FetchPlanningResultResponse` 响应对象添加了存储凭据（storage credentials）字段。在 Iceberg 的 REST Catalog 架构中，客户端通过 REST API 与服务端交互来规划（plan）和获取扫描任务（scan tasks）。当客户端获取规划结果时，可能需要访问底层存储（如 S3、GCS 等对象存储）来读取数据文件，而这些存储可能需要特定的凭据才能访问。

此前，`FetchPlanningResultResponse` 只包含 `planStatus`、`planTasks`、`fileScanTasks`、`deleteFiles` 和 `specsById` 等字段，但不包含存储凭据。这意味着客户端在获取到扫描任务后，可能需要通过单独的请求来获取存储访问凭据，增加了网络往返次数和延迟。通过在 `FetchPlanningResultResponse` 中直接内嵌 `storage-credentials` 字段，服务端可以在返回规划结果的同时一并下发所需的存储凭据，减少客户端的请求次数，提升整体效率。

这与 Iceberg REST Catalog 规范中凭据下发的设计一致——`LoadTableResponse` 和 `LoadCredentialsResponse` 等响应已支持携带凭据，本提交将这一能力扩展到 `FetchPlanningResultResponse`，使规划结果响应也能携带凭据。凭据以 `Credential` 对象列表的形式存在，每个凭据包含一个 `prefix`（标识凭据适用的存储路径前缀，如 `s3://custom-uri`）和一个 `config`（键值对形式的凭据配置，如 access-key、secret-key、session-token 等）。

## 如何达成设计目的

修改分为三部分：在 `FetchPlanningResultResponse` 数据类中新增 `credentials` 字段及对应的 Builder 方法；在 `FetchPlanningResultResponseParser` 中实现凭据的 JSON 序列化与反序列化（使用 `storage-credentials` 作为 JSON 字段名，复用 `CredentialParser` 和 `LoadCredentialsResponseParser`）；在测试中新增了凭据往返序列化和边界情况测试。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/responses/FetchPlanningResultResponse.java` (+17/-3 lines)

**修改目的**：在响应对象中新增 `credentials` 字段及相关访问方法。

**工作逻辑**：
新增 `private final List<Credential> credentials` 字段，并在构造函数中增加该参数。`credentials()` 访问方法返回该字段，若为 null 则返回 `ImmutableList.of()`（空列表），保证调用方始终获得非 null 值。在 Builder 中新增 `private final List<Credential> credentials = Lists.newArrayList()` 字段和 `withCredentials(List<Credential>)` 方法，该方法使用 `addAll` 将传入凭据追加到列表中（而非替换），支持多次调用累积凭据。`build()` 方法将 credentials 传递给构造函数。同时引入了 `ImmutableList`、`Lists` 和 `Credential` 的 import。

### `core/src/main/java/org/apache/iceberg/rest/responses/FetchPlanningResultResponseParser.java` (+24/-7 lines)

**修改目的**：实现凭据的 JSON 序列化与反序列化。

**工作逻辑**：
序列化方面，定义常量 `STORAGE_CREDENTIALS = "storage-credentials"`。在 `toJson` 方法中，当 `response.credentials()` 非空时，通过 `gen.writeArrayFieldStart(STORAGE_CREDENTIALS)` 写入数组字段，遍历每个 `Credential` 调用 `CredentialParser.toJson(credential, gen)` 进行序列化，最后 `gen.writeEndArray()`。反序列化方面，在 `fromJson` 方法中，先构建基础 Builder（planStatus、planTasks、fileScanTasks、specsById），然后检查 JSON 中是否存在 `STORAGE_CREDENTIALS` 且为非 null 值（`json.hasNonNull(STORAGE_CREDENTIALS)`），若存在则调用 `LoadCredentialsResponseParser.fromJson(json).credentials()` 解析凭据并传入 Builder。这种设计复用了已有的凭据解析逻辑，避免重复代码。`fromJson` 将原先的一行式 Builder 链式调用重构为先创建 Builder 变量再条件性添加凭据，最后 `build()`。

### `core/src/test/java/org/apache/iceberg/rest/responses/TestFetchPlanningResultResponseParser.java` (+107/-2 lines)

**修改目的**：新增凭据序列化往返测试和边界情况测试。

**工作逻辑**：
新增两个测试方法。`emptyOrInvalidCredentials` 测试三种边界情况：`storage-credentials` 为 null 时返回空凭据列表；为空数组 `[]` 时返回空列表；为非数组值（如字符串 `"invalid"`）时抛出 `IllegalArgumentException`，消息为 "Cannot parse credentials from non-array"。`roundTripSerdeWithCredentials` 测试完整的序列化往返：构造包含三个 `Credential` 的响应（分别针对 `s3://custom-uri`、`gs://custom-uri`、`gs` 前缀，配置不同的 S3 和 GCS 凭据键值对），序列化为 JSON 后与预期 JSON 字符串比对（pretty print 格式），再反序列化回来并重新序列化验证一致性。测试覆盖了多凭据、多存储类型（S3/GCS）的场景，验证了序列化格式的正确性和往返一致性。

## 总结

该提交为 Iceberg REST Catalog 的 `FetchPlanningResultResponse` 添加了存储凭据下发能力，使客户端在获取扫描规划结果时能同时获得访问底层存储所需的凭据，减少额外网络请求。实现上复用了已有的 `Credential`/`CredentialParser`/`LoadCredentialsResponseParser` 组件，保持了一致的序列化格式（`storage-credentials` 字段），并提供了完整的边界和往返测试覆盖。
