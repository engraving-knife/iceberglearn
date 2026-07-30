# 提交 3638：AWS, GCP: add Kryo round-trip regression test for refreshed storage credentials (#16112)

## 提交信息

- **序号**：3638 / 4088
- **哈希**：f2e7a65678b4c529bb53b299450faf95ef24385b
- **短哈希**：f2e7a6567
- **日期**：2026-05-04 10:32:05 +0200
- **作者**：Akshay Thorat
- **提交说明**：AWS, GCP: add Kryo round-trip regression test for refreshed storage credentials (#16112)
- **PR/Issue**：#16112

## 总体目的

这个提交为 S3FileIO 和 GCSFileIO 添加了针对"运行时刷新后的存储凭据"的 Kryo 序列化往返（round-trip）回归测试。

Iceberg 的 FileIO 实现需要支持 Kryo 序列化，因为这在 Spark 等计算引擎中将 FileIO 序列化分发到 executor 节点时非常常见。存储凭据会在运行时过期并刷新，刷新后内部 `storageCredentials` 列表会被更新。如果该列表所用的集合实现不被 Kryo 支持（例如使用了不可序列化的集合类型），在序列化/反序列化后会出现问题。此测试正是为了回归验证：凭据被刷新后，FileIO 仍能正确通过 Kryo 序列化往返，且凭据内容保持一致。

## 如何达成设计目的

在已有的 `TestS3FileIOCredentialRefresh` 和 `TestGCSFileIOCredentialRefresh` 测试类中各新增一个 `refreshedCredentialsAreKryoSerializable` 测试方法。测试流程为：构造一个即将过期的初始凭据，触发凭据刷新机制（通过 mock server 返回新凭据），等待刷新完成，然后通过 `TestHelpers.KryoHelpers.roundTripSerialize` 进行 Kryo 序列化往返，最后断言反序列化后的凭据与原凭据一致。

## 修改详情

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestS3FileIOCredentialRefresh.java` (+80 lines)

**修改目的**：新增 S3 凭据刷新后的 Kryo 序列化回归测试。

**工作逻辑**：
1. 构造一个 3 分钟后过期的初始 `StorageCredential`（包含 access key、secret key、session token、过期时间）。
2. 通过 mock server 准备一个刷新响应，返回 1 小时后过期的新凭据。
3. 初始化 `S3FileIO`，设置初始凭据，调用 `fileIO.client()` 触发凭据刷新。
4. 使用 Awaitility 等待（最多 10 秒）凭据被刷新为 "refreshedAccessKey"。
5. 通过 `TestHelpers.KryoHelpers.roundTripSerialize(fileIO)` 进行 Kryo 往返序列化。
6. 断言反序列化后的 `deserialized.credentials()` 与原 `fileIO.credentials()` 相等。

### `gcp/src/test/java/org/apache/iceberg/gcp/gcs/TestGCSFileIOCredentialRefresh.java` (+67 lines)

**修改目的**：新增 GCS 凭据刷新后的 Kryo 序列化回归测试。

**工作逻辑**：与 S3 测试类似，使用 GCP 特有的属性（`GCS_OAUTH2_TOKEN`、`GCS_OAUTH2_TOKEN_EXPIRES_AT`、`GCS_OAUTH2_REFRESH_CREDENTIALS_ENDPOINT`），构造初始凭据、触发刷新、Kryo 往返并断言一致性。

## 总结

这是一个纯测试提交，为 S3 和 GCS 的 FileIO 凭据刷新场景补充了 Kryo 序列化回归测试。该测试确保在运行时凭据被刷新后，FileIO 仍能被 Spark 等引擎正确序列化分发，避免因内部集合实现不可序列化导致的运行时故障。测试覆盖了从凭据初始化、刷新到序列化往返的完整链路。
