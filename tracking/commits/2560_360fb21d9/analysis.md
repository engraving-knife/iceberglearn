# 提交 2560：AWS, Azure: Fix S3InputStream and ADLSInputStream connection leaks (#13905)

## 提交信息

- **序号**：2560 / 4088
- **哈希**：360fb21d96b381ee4bc7b9594e2c4ed242f08754
- **短哈希**：360fb21d9
- **日期**：2025-08-25 08:05:22 -0600
- **作者**：Anatoly Popov
- **提交说明**：AWS, Azure: Fix S3InputStream and ADLSInputStream connection leaks (#13905)
- **PR/Issue**：#13905（follow up to #13899）

## 总体目的

该提交是 PR #13899（提交 2549）的后续修复，对 S3InputStream 和 ADLSInputStream 中遗漏的同类连接泄漏问题进行修复。PR #13899 修复了 `S3InputStream.readFully` 方法的连接泄漏，但 `readTail` 方法以及 Azure 的 `ADLSInputStream` 中的 `readFully` 和 `readTail` 方法存在完全相同的问题：获取的输入流在使用后未被关闭，导致底层连接泄漏。

具体而言：
1. **S3InputStream.readTail**：通过 `readRange(range)` 获取输入流后直接传给 `IOUtil.readRemaining`，未关闭流。
2. **ADLSInputStream.readFully**：通过 `openRange(range).getInputStream()` 获取输入流后直接传给 `IOUtil.readFully`，未关闭流。
3. **ADLSInputStream.readTail**：通过 `openRange(new FileRange(readStart)).getInputStream()` 获取输入流后直接传给 `IOUtil.readRemaining`，未关闭流。

这些方法与 `readFully` 一样会发起对远程存储的请求并获取输入流，不关闭流会导致 HTTP 连接无法释放，造成连接泄漏。

## 如何达成设计目的

- 对 S3InputStream 的 `readTail` 和 ADLSInputStream 的 `readFully`、`readTail` 方法使用 try-with-resources 包装返回的输入流，确保自动关闭。
- 扩展 S3 测试类，增加 `testReadTailClosesTheStream` 测试；新增 ADLS 测试类，包含 `readFully` 和 `readTail` 的关闭验证测试。
- 在 build.gradle 中为 AWS 和 Azure 模块添加 `mockito-junit-jupiter` 测试依赖。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3InputStream.java` (+2/-2)

**修改目的**：修复 `readTail` 方法的连接泄漏。

**工作逻辑**：将 `IOUtil.readRemaining(readRange(range), buffer, offset, length)` 改为使用 try-with-resources 包装 `readRange(range)` 返回的流。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestS3InputStream.java` (+24/-7)

**修改目的**：扩展测试覆盖 `readTail` 方法。

**工作逻辑**：重构测试类使用 `@ExtendWith(MockitoExtension.class)` 和 `@Mock` 注解，在 `@BeforeEach` 中统一设置 mock。新增 `testReadTailClosesTheStream` 测试，验证调用 `readTail` 后输入流被关闭。

### `azure/src/main/java/org/apache/iceberg/azure/adlsv2/ADLSInputStream.java` (+5/-4)

**修改目的**：修复 `readFully` 和 `readTail` 方法的连接泄漏。

**工作逻辑**：将 `readFully` 中 `IOUtil.readFully(openRange(range).getInputStream(), buffer, offset, length)` 改为 try-with-resources 包装 `openRange(range).getInputStream()`。`readTail` 同理。

### `azure/src/test/java/org/apache/iceberg/azure/adlsv2/TestADLSInputStream.java` (+65/-0)

**修改目的**：新增 ADLS 输入流关闭测试。

**工作逻辑**：创建 `TestADLSInputStream` 测试类，使用 Mockito mock `DataLakeFileClient` 和 `InputStream`。在 `@BeforeEach` 中配置 mock 返回包含 mock InputStream 的结果。新增 `testReadFullyClosesTheStream` 和 `testReadTailClosesTheStream` 两个测试。

### `build.gradle` (+2/-0)

**修改目的**：添加 Mockito JUnit Jupiter 测试依赖。

**工作逻辑**：在 `iceberg-aws` 和 `iceberg-azure` 项目的测试依赖中添加 `libs.mockito.junit.jupiter`。

## 总结

该提交是连接泄漏修复的后续，对 S3InputStream 的 `readTail` 和 ADLSInputStream 的 `readFully`、`readTail` 方法应用了与 PR #13899 相同的 try-with-resources 修复，并新增了对应的单元测试和测试依赖。
