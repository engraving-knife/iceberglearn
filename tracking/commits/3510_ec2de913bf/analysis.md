# 提交 3510：AWS: Add chunked encoding configuration for S3 requests (#15242)

## 提交信息

- **序号**：3510 / 4088
- **哈希**：ec2de913bfea2e1799aa2b72a9a9796418a8d839
- **短哈希**：ec2de913bf
- **日期**：2026-04-05 13:56:13 -0700
- **作者**：Jiajia Li
- **提交说明**：AWS: Add chunked encoding configuration for S3 requests (#15242)
- **PR/Issue**：#15242

## 总体目的

为 S3 请求新增 chunked encoding（分块传输编码）配置选项。AWS SDK 默认启用 chunked encoding，但某些场景（如使用特定的 S3 兼容存储或代理）需要禁用它。新增 `s3.chunked-encoding-enabled` 属性允许用户控制此行为，默认为 true 以匹配 AWS SDK 的默认行为。

## 如何达成设计目的

1. 在 `S3FileIOProperties` 中新增 `CHUNKED_ENCODING_ENABLED` 属性常量和默认值（true）。
2. 在属性解析逻辑中读取该配置。
3. 在 S3Client 构建时通过 `S3Configuration.chunkedEncodingEnabled()` 应用配置。
4. 添加单元测试验证默认值和显式禁用。
5. 添加集成测试验证 multipart upload 在 chunked encoding 启用和禁用时的正确性。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIOProperties.java` (+22 lines)

**修改目的**：新增 chunked encoding 配置属性。

**工作逻辑**：
- 新增常量 `CHUNKED_ENCODING_ENABLED = "s3.chunked-encoding-enabled"`，默认值 `CHUNKED_ENCODING_ENABLED_DEFAULT = true`。
- 新增 `isChunkedEncodingEnabled` 字段，在构造函数中初始化为默认值，在 `resolveProperty` 中从 properties 读取。
- 新增 `isChunkedEncodingEnabled()` getter 方法。
- 在 `configureS3Client`（或等效位置）中，构建 S3Configuration 时添加 `.chunkedEncodingEnabled(isChunkedEncodingEnabled)`。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestS3FileIOProperties.java` (+21 lines)

**修改目的**：单元测试验证配置。

**工作逻辑**：
- `testChunkedEncodingEnabledDefaultValue`：验证默认启用。
- `testChunkedEncodingDisabled`：验证显式设为 false 时禁用。

### `aws/src/integration/java/org/apache/iceberg/aws/s3/TestS3MultipartUpload.java` (+90 lines)

**修改目的**：集成测试验证 multipart upload 在不同 chunked encoding 设置下的正确性。

**工作逻辑**：
- `testMultipartUploadWithChunkedEncoding(boolean)`：参数化测试，分别测试启用和禁用 chunked encoding。
- 使用 `S3FileIOProperties.MULTIPART_SIZE` 设为最小值，`CHECKSUM_ENABLED` 设为 true。
- 写入 10 个 distinct parts（用 int 和 byte[] 两种方式），验证文件大小和内容正确性。
- 使用 `IOUtil.readFully` 确保可靠读取。

## 总结

功能增强提交，为 S3 请求新增 `s3.chunked-encoding-enabled` 配置属性（默认 true 匹配 AWS SDK 行为）。该配置在 S3Client 构建时通过 `S3Configuration.chunkedEncodingEnabled()` 应用。添加了单元测试验证配置和集成测试验证 multipart upload 在不同设置下的数据完整性。
