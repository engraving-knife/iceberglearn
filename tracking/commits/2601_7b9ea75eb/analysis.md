# 提交 2601：Fixing AssertJ assertions in TestS3FileIOProperties (#14005)

## 提交信息

- **序号**：2601 / 4088
- **哈希**：7b9ea75ebe93801eaa908c28e9ef18ab205ffb80
- **短哈希**：7b9ea75eb
- **日期**：2025-09-06 15:09:38 -0700
- **作者**：Anatoly Popov
- **提交说明**：Fixing AssertJ assertions in TestS3FileIOProperties (#14005)
- **PR/Issue**：#14005

## 总体目的

本次提交修复了 `TestS3FileIOProperties` 测试类中 AssertJ 断言的参数顺序问题，将 `assertThat(expected).isEqualTo(actual)` 修正为 `assertThat(actual).isEqualTo(expected)`。

在从其他断言库（如 JUnit Assert 或 Hamcrest）迁移到 AssertJ 的过程中，断言的参数顺序没有被正确调整。虽然 `assertThat(A).isEqualTo(B)` 和 `assertThat(B).isEqualTo(A)` 在断言是否通过上结果相同（都是判断 A == B），但在断言失败时，错误信息的可读性完全不同：

- 正确写法 `assertThat(actual).isEqualTo(expected)`：失败时显示 "expected: <expected> but was: <actual>"
- 错误写法 `assertThat(expected).isEqualTo(actual)`：失败时显示 "expected: <actual> but was: <expected>"

当 expected 是常量/默认值而 actual 是被测对象的实际值时，错误的参数顺序会导致失败信息中"期望值"和"实际值"颠倒，使调试者困惑。

## 如何达成设计目的

逐一检查 `TestS3FileIOProperties` 中所有 `assertThat` 调用，将被测对象的实际值（如 `s3FileIOProperties.sseType()`）作为 `assertThat` 的参数，将期望值（如 `S3FileIOProperties.SSE_TYPE_NONE`）作为 `isEqualTo` 的参数。共修正 45 处断言。

## 修改详情

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestS3FileIOProperties.java` (+45/-45 lines)

**修改目的**：修正 AssertJ 断言的参数顺序，使失败信息正确显示期望值和实际值。

**工作逻辑**：对每个断言，将 actual（被测值）和 expected（期望值）的位置交换。典型修改模式：

修改前：
```java
assertThat(S3FileIOProperties.SSE_TYPE_NONE).isEqualTo(s3FileIOProperties.sseType());
assertThat(S3FileIOProperties.PRELOAD_CLIENT_ENABLED_DEFAULT).isEqualTo(s3FileIOProperties.isPreloadClientEnabled());
assertThat(Runtime.getRuntime().availableProcessors()).isEqualTo(s3FileIOProperties.multipartUploadThreads());
assertThat(Sets.newHashSet()).isEqualTo(s3FileIOProperties.writeTags());
```

修改后：
```java
assertThat(s3FileIOProperties.sseType()).isEqualTo(S3FileIOProperties.SSE_TYPE_NONE);
assertThat(s3FileIOProperties.isPreloadClientEnabled()).isEqualTo(S3FileIOProperties.PRELOAD_CLIENT_ENABLED_DEFAULT);
assertThat(s3FileIOProperties.multipartUploadThreads()).isEqualTo(Runtime.getRuntime().availableProcessors());
assertThat(s3FileIOProperties.writeTags()).isEqualTo(Sets.newHashSet());
```

涉及的断言包括：sseType、sseKey、sseMd5、isPreloadClientEnabled、isDualStackEnabled、isCrossRegionAccessEnabled、isPathStyleAccess、isUseArnRegionEnabled、isAccelerationEnabled、isRemoteSigningEnabled、multipartUploadThreads、multiPartSize、multipartThresholdFactor、deleteBatchSize、stagingDirectory、isChecksumEnabled、writeTags、writeTableTagEnabled、isWriteNamespaceTagEnabled、deleteTags、deleteThreads、isDeleteEnabled、bucketToAccessPointMapping 等属性的默认值验证。

## 总结

这是一个测试质量改进提交，修正了迁移到 AssertJ 时遗留的参数顺序问题。虽然不影响测试是否通过，但显著改善了断言失败时的错误信息可读性，有助于开发者快速定位问题。这也提醒在断言库迁移时需要注意参数顺序的差异。
