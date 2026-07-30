# 提交 3184：AWS: Remove HEAD operation from AnalyticsAcceleratorUtil (#15116)

## 提交信息

- **序号**：3184 / 4088
- **哈希**：f23486ffd7499d2b2f0bfd8ce7552c361fd86796
- **短哈希**：f23486ffd
- **日期**：2026-01-30
- **作者**：Vaibhav Ahuja
- **提交说明**：AWS: Remove HEAD operation from AnalyticsAcceleratorUtil (#15116)
- **PR/Issue**：#15116

## 总体目的

AWS S3 Analytics Accelerator（`software.amazon.s3.analyticsaccelerator`）是 Amazon 提供的 S3 读取优化库，通过预取、缓存和并行读取等策略加速 S3 对象的 seekable 读取。Iceberg 的 `AnalyticsAcceleratorUtil` 负责将该库集成到 S3 FileIO 中，为每个 S3 对象创建 `S3SeekableInputStream`。

此前的实现在打开每个输入流时，会先向 S3 发起一次 HEAD 请求（通过 `inputFile.getObjectMetadata()` 获取 `HeadObjectResponse`），提取对象的 `contentLength` 和 `etag`，封装为 `ObjectMetadata` 并构建 `OpenStreamInformation`，再传给 `factory.createStream(uri, openStreamInfo)`。这次额外的 HEAD 请求发生在每次流打开时，会产生以下问题：

1. **性能开销**：每次打开文件（如读取 Parquet footer、读取 metadata 文件）都多一次 S3 API 往返，增加尾延迟。
2. **API 成本**：S3 HEAD 请求虽不收费（LIST 和 HEAD 在 S3 上免费），但在高并发场景下仍占用连接池和请求配额。
3. **冗余**：Analytics Accelerator 库的新版本已能在内部自行获取对象元数据，无需调用方预先通过 HEAD 提供。

本提交移除了显式的 HEAD 操作和 `OpenStreamInformation` 构建，改为直接调用 `factory.createStream(uri)`，让 Analytics Accelerator 库内部负责元数据获取。这简化了集成代码并消除了冗余的 HEAD 请求。

## 如何达成设计目的

改动集中在 `AnalyticsAcceleratorUtil.java` 的 `newStream` 方法：删除获取 `HeadObjectResponse`、构建 `ObjectMetadata` 和 `OpenStreamInformation` 的代码块，将 `factory.createStream(uri, openStreamInfo)` 简化为 `factory.createStream(uri)`，并移除不再需要的导入（`HeadObjectResponse`、`ObjectMetadata`、`OpenStreamInformation`）。这依赖于 Analytics Accelerator 库提供了无 `OpenStreamInformation` 参数的 `createStream` 重载。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/AnalyticsAcceleratorUtil.java` (+1/-13 lines)

**修改目的**：移除打开流前的 HEAD 请求，简化为直接创建流。

**工作逻辑**：

**移除的导入**（3 个）：
- `software.amazon.awssdk.services.s3.model.HeadObjectResponse`：AWS SDK S3 HEAD 响应模型。
- `software.amazon.s3.analyticsaccelerator.request.ObjectMetadata`：Analytics Accelerator 的对象元数据封装。
- `software.amazon.s3.analyticsaccelerator.util.OpenStreamInformation`：打开流时的附加信息封装。

**移除的代码块**（`newStream` 方法内）：
```java
HeadObjectResponse metadata = inputFile.getObjectMetadata();
OpenStreamInformation openStreamInfo =
    OpenStreamInformation.builder()
        .objectMetadata(
            ObjectMetadata.builder()
                .contentLength(metadata.contentLength())
                .etag(metadata.eTag())
                .build())
        .build();
```
此代码块通过 `inputFile.getObjectMetadata()` 发起 S3 HEAD 请求，获取对象的内容长度和 ETag，封装为 `OpenStreamInformation`。

**修改的调用**：
```java
// 原：S3SeekableInputStream seekableInputStream = factory.createStream(uri, openStreamInfo);
// 新：
S3SeekableInputStream seekableInputStream = factory.createStream(uri);
```
改用无 `openStreamInfo` 参数的重载，由 Analytics Accelerator 库内部按需获取对象元数据。

其余逻辑（URI 构建、factory 缓存、异常包装为 `RuntimeIOException`）不变。

## 总结

本提交移除了 `AnalyticsAcceleratorUtil` 中每次打开 S3 输入流时的冗余 HEAD 请求，改为直接调用 `factory.createStream(uri)` 由库内部处理元数据获取。改动精简了 13 行代码，消除了额外的 S3 API 往返，降低了读取延迟并简化了集成逻辑。改动依赖于 Analytics Accelerator 库版本的 `createStream` 重载支持，属于性能优化与代码简化。
