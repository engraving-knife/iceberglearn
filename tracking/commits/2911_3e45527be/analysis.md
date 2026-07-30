# 提交 2911：Core: Add storage credentials to PlanTableScanResponse (#14518)

## 提交信息

- **序号**：2911 / 4088
- **哈希**：3e45527bebca8a316e2722e0b2a55b6b41f6b7ee
- **短哈希**：3e45527be
- **日期**：2025-11-22 18:57:46 -0800
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Add storage credentials to PlanTableScanResponse
- **PR/Issue**：#14518

## 总体目的

在 Iceberg REST 协议中，当服务端进行远程扫描规划（remote scan planning）时，客户端通过 `PlanTableScanRequest` 请求获取扫描任务，服务端返回 `PlanTableScanResponse`。然而此前的 `PlanTableScanResponse` 不包含存储凭证（storage credentials），客户端在读取返回的扫描任务中的数据文件时，可能缺少访问底层存储（如 S3、GCS）所需的凭证。

在远程扫描规划场景下，客户端需要直接从存储系统读取数据文件，因此服务端需要能够下发短期的存储凭证。这与 Iceberg REST 规范中 `storage-credentials` 字段的设计一致，其他响应类型（如 LoadTableResponse）已经支持存储凭证。本提交为 `PlanTableScanResponse` 增加了 `storage-credentials` 字段，使服务端能够在规划扫描任务时同时下发存储凭证，客户端无需额外的凭证获取步骤即可读取数据。

## 如何达成设计目的

在 `PlanTableScanResponse` 的数据模型中新增 `List<Credential> credentials` 字段，复用已有的 `Credential` 和 `CredentialParser` 类进行序列化。Builder 模式中新增 `withCredentials` 方法，`credentials()` 访问器在 null 时返回空列表保证安全。在 `PlanTableScanResponseParser` 中，序列化时若 credentials 非空则写入 `storage-credentials` 数组字段，反序列化时若 JSON 中存在非 null 的 `storage-credentials` 则通过 `LoadCredentialsResponseParser.fromJson` 复用已有的凭证解析逻辑。同时补充了全面的序列化往返测试和边界用例测试。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/responses/PlanTableScanResponse.java` (+22/-4 lines)

**修改目的**：在响应数据模型中增加 credentials 字段及其 Builder 支持。

**工作逻辑**：
新增 `private final List<Credential> credentials` 字段，构造函数增加对应参数。`credentials()` 方法在 null 时返回 `ImmutableList.of()`。Builder 中新增 `withCredentials(List<Credential>)` 方法将凭证添加到内部列表，`build()` 时将 credentials 传入构造函数。

### `core/src/main/java/org/apache/iceberg/rest/responses/PlanTableScanResponseParser.java` (+27/-8 lines)

**修改目的**：实现 storage-credentials 字段的 JSON 序列化和反序列化。

**工作逻辑**：
定义常量 `STORAGE_CREDENTIALS = "storage-credentials"`。序列化时，若 `response.credentials()` 非空，使用 `gen.writeArrayFieldStart(STORAGE_CREDENTIALS)` 写入数组，遍历每个 Credential 调用 `CredentialParser.toJson(credential, gen)` 写入。反序列化时，若 `json.hasNonNull(STORAGE_CREDENTIALS)`，则复用 `LoadCredentialsResponseParser.fromJson(json).credentials()` 解析凭证列表并传入 Builder。这种复用避免了重复实现凭证解析逻辑。

### `core/src/test/java/org/apache/iceberg/rest/responses/TestPlanTableScanResponseParser.java` (+229/-1 lines)

**修改目的**：为 storage-credentials 功能添加测试覆盖。

**工作逻辑**：
新增 `emptyOrInvalidCredentials` 测试，验证 null credentials 返回空列表、空数组返回空列表、非数组类型抛出 IllegalArgumentException。新增 `roundTripSerdeWithCredentials` 测试，构造包含 S3 和 GCS 凭证的响应，验证序列化输出格式和往返一致性。新增 `roundTripSerdeWithValidStatusAndFileScanTasksAndCredentials` 测试，验证同时包含扫描任务和凭证的完整响应往返序列化，确保字段顺序和格式正确。

## 总结

本提交为远程扫描规划场景补齐了存储凭证下发能力，使 `PlanTableScanResponse` 能够携带 `storage-credentials` 字段，客户端获取扫描任务后即可直接使用附带凭证读取数据文件。实现上复用了已有的 Credential 和 LoadCredentialsResponseParser，保持了代码一致性。测试覆盖了正常往返、空值边界和非法输入场景，是一个完整的协议功能增强。
