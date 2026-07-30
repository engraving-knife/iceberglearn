# 提交 2160：AWS: Skip cleanup of analytics accelerator when disabled (#13134)

## 提交信息

- **序号**：2160 / 4088
- **哈希**：793f6cd062c75cc72f6ae6e42840d290cb1b68f0
- **短哈希**：793f6cd06
- **日期**：2025-05-23 05:56:36 -0700
- **作者**：Devin Smith
- **提交说明**：AWS: Skip cleanup of analytics accelerator when disabled (#13134)
- **PR/Issue**：#13134

## 总体目的

当 `S3FileIO` 使用了 `S3AsyncClient` 且 analytics accelerator（S3 分析加速器）被禁用时（默认就是禁用的），在 `S3FileIO` 关闭时可能会抛出异常。原因是 `PrefixedS3Client.close()` 方法在关闭异步客户端前，无条件调用 `AnalyticsAcceleratorUtil.cleanupCache()`，即使 accelerator 未启用也会执行清理逻辑。如果 analytics accelerator 的相关类不在 classpath 上（即用户未引入该依赖），清理操作会因 `ClassNotFoundException` 或 `NoClassDefFoundError` 而失败，导致 `S3FileIO` 关闭时抛出异常。该提交通过在调用清理方法前检查 accelerator 是否启用，来避免在禁用状态下执行不必要的清理，从而修复关闭异常问题。

## 如何达成设计目的

- 在 `PrefixedS3Client.close()` 方法中，将 `AnalyticsAcceleratorUtil.cleanupCache()` 的调用包裹在 `if (s3FileIOProperties().isS3AnalyticsAcceleratorEnabled())` 条件判断中，仅当 accelerator 启用时才执行清理。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/PrefixedS3Client.java` (修改, +3/-1 lines)

**修改目的**：避免在 analytics accelerator 禁用时执行清理操作导致异常。

**工作逻辑**：在 `close()` 方法中，关闭异步客户端前，原代码直接调用 `AnalyticsAcceleratorUtil.cleanupCache(s3AsyncClient, s3FileIOProperties)`。修改后增加条件判断：`if (s3FileIOProperties().isS3AnalyticsAcceleratorEnabled())`，仅当 accelerator 启用时才执行清理。这样当 accelerator 被禁用（默认状态）且其依赖不在 classpath 时，不会触发清理逻辑中的类加载失败。

## 总结

该提交修复了一个在 analytics accelerator 禁用且依赖不在 classpath 时 `S3FileIO` 关闭抛异常的 bug。修复方式简单有效：通过条件判断跳过不必要的清理操作。由于 analytics accelerator 默认禁用，此修复对大多数用户的正确性有实际影响。
