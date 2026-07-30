# 提交 2297：GCS: Add Iceberg version to UserAgent in GCS requests (#13428)

## 提交信息

- **序号**：2297 / 4088
- **哈希**：dedba4568ee39e7fdf1f29eee3c33351fcc206c3
- **短哈希**：dedba4568
- **日期**：2025-07-01 08:55:43 +0200
- **作者**：Shubham Diwakar
- **提交说明**：GCS: Add Iceberg version to UserAgent in GCS requests (#13428)
- **PR/Issue**：#13428

## 总体目的

本提交为 Iceberg 的 GCS（Google Cloud Storage）请求添加 User-Agent 头，包含 Iceberg 的版本信息。此前，Iceberg 发送到 GCS 的请求没有标识自身身份的 User-Agent，导致 Google Cloud 平台无法识别这些请求来自 Iceberg 项目。

添加 User-Agent 有多个好处：
1. **可观测性**：GCS 服务端可以统计和监控来自 Iceberg 的请求量
2. **问题诊断**：当出现兼容性问题时，GCS 团队可以通过 User-Agent 识别 Iceberg 版本，提供更有针对性的支持
3. **最佳实践**：这是云存储客户端的通用实践，AWS S3 和 Azure 等集成中已有类似机制

## 如何达成设计目的

通过 Google Cloud Storage Java SDK 的 `FixedHeaderProvider` 机制，在创建 `StorageOptions` 时设置自定义的 User-Agent 头。User-Agent 值格式为 `gcsfileio/<iceberg-version>`，其中 Iceberg 版本通过 `EnvironmentContext.get()` 获取，这是 Iceberg 内部统一的版本信息获取方式。

## 修改详情

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/PrefixedStorage.java` (+9/-1 lines)

**修改目的**：在 GCS Storage 客户端创建时添加包含 Iceberg 版本的 User-Agent 头。

**工作逻辑**：
- 定义常量 `GCS_FILE_IO_USER_AGENT = "gcsfileio/" + EnvironmentContext.get()`，其中 `EnvironmentContext.get()` 返回当前 Iceberg 的版本号字符串
- 在创建 `StorageOptions.Builder` 时，通过 `setHeaderProvider(FixedHeaderProvider.create(ImmutableMap.of("User-agent", GCS_FILE_IO_USER_AGENT)))` 设置 User-Agent 头
- `FixedHeaderProvider` 是 GCS SDK 提供的工具类，用于为所有请求添加固定的 HTTP 头

### `gcp/src/test/java/org/apache/iceberg/gcp/gcs/TestPrefixedStorage.java` (+14/-0 lines)

**修改目的**：验证 User-Agent 被正确设置到 GCS Storage 客户端。

**工作逻辑**：新增 `userAgentPrefix` 测试方法，创建一个 `PrefixedStorage` 实例，然后通过 `storage.getOptions().getUserAgent()` 获取实际设置的 User-Agent，验证其等于 `"gcsfileio/" + EnvironmentContext.get()`。

## 总结

本提交为 Iceberg 的 GCS 集成添加了 User-Agent 标识，使 GCS 服务端能够识别来自 Iceberg 的请求及其版本。这是一个可观测性和最佳实践的改进，有助于 GCS 平台团队更好地支持 Iceberg 用户，也便于在出现问题时快速定位涉及的 Iceberg 版本。
