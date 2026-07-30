# 提交 1983：AWS: Add unit tests for AWS s3Async (#12758)

## 提交信息

- **序号**：1983 / 4088
- **哈希**：20811c6fb65543d5e198ef37d87872c211316aeb
- **短哈希**：20811c6fb
- **日期**：2025-04-11 10:54:47 +0200
- **作者**：sullis
- **提交说明**：AWS: Add unit tests for AWS s3Async (#12758)
- **PR/Issue**：#12758

## 总体目的

本提交为 AWS 模块的 `AwsClientFactories` 新增针对 S3 异步客户端（`s3Async()`）的单元测试，覆盖 S3 CRT（Common Runtime）启用/禁用的行为。

此前 `TestAwsClientFactories` 主要测试同步客户端与工厂加载逻辑，对 `s3Async()` 的构建路径（特别是 `S3_CRT_ENABLED` 属性控制是否使用基于 CRT 的异步客户端）缺少测试覆盖。新增测试验证：当显式启用 CRT、显式禁用 CRT、以及默认（不设置）三种情况下，`AwsClientFactories.from(...).s3Async()` 返回的客户端类型是否符合预期（启用/默认时为 `DefaultS3CrtAsyncClient`，禁用时不是）。

## 如何达成设计目的

通过三个测试用例，使用最小化的属性配置（access key、secret key、region）配合不同的 `S3_CRT_ENABLED` 取值，断言 `s3Async()` 返回实例的类型。

## 修改详情

### `aws/src/test/java/org/apache/iceberg/aws/TestAwsClientFactories.java` (修改, +51/-0 lines)

**修改目的**：覆盖 s3Async 客户端的 CRT 启用/禁用/默认行为。

**工作逻辑**：
- 新增 import：`ImmutableMap` 与 `software.amazon.awssdk.services.s3.internal.crt.DefaultS3CrtAsyncClient`。
- `testS3AsyncClientCrtEnabled`：设置 `S3_CRT_ENABLED=true`，断言 `AwsClientFactories.from(...).s3Async()` 是 `DefaultS3CrtAsyncClient` 实例。
- `testS3AsyncClientWithCrtDisabled`：设置 `S3_CRT_ENABLED=false`，断言返回的异步客户端**不是** `DefaultS3CrtAsyncClient`。
- `testS3AsyncClientDefaultIsCrt`：不设置 `S3_CRT_ENABLED`，断言默认返回的仍是 `DefaultS3CrtAsyncClient`（即默认启用 CRT）。

## 总结

测试增强提交，为 `AwsClientFactories.s3Async()` 新增三个单元测试，验证 `S3_CRT_ENABLED` 属性在 true/false/默认三种取值下异步客户端类型（CRT vs 非 CRT）符合预期，填补了 S3 异步客户端构建路径的测试空白。仅新增 51 行测试，无生产代码改动。
