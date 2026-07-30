# 提交 2549：Fix S3InputStream.readFully connection leak (#13899)

## 提交信息

- **序号**：2549 / 4088
- **哈希**：0e6633bce644faf0fed189229bc2145e48f183ec
- **短哈希**：0e6633bce
- **日期**：2025-08-22 14:33:12 -0700
- **作者**：Anatoly Popov
- **提交说明**：Fix S3InputStream.readFully connection leak (#13899)
- **PR/Issue**：#13899

## 总体目的

该提交修复了 AWS S3 模块中 `S3InputStream.readFully` 方法存在的连接泄漏问题。`readFully` 方法在调用 `readRange(range)` 获取 S3 对象的输入流后，直接使用 `IOUtil.readFully` 读取数据，但从未关闭该输入流。由于 S3 的 `getObject` 请求会占用底层 HTTP 连接，如果不关闭返回的输入流，会导致 HTTP 连接无法被释放回连接池，最终造成连接泄漏。

连接泄漏在长时间运行的 Iceberg 作业中尤为危险，因为频繁读取 S3 上的数据文件（如 manifest、数据文件、删除文件等）会不断累积未关闭的连接，最终耗尽连接池资源，导致后续请求阻塞或失败。

该提交通过使用 try-with-resources 语句确保输入流在使用完毕后被正确关闭，从而释放底层的 HTTP 连接。同时新增了单元测试来验证 `readFully` 方法确实会关闭流。

## 如何达成设计目的

- 使用 Java 7 引入的 try-with-resources 语法包装 `readRange(range)` 返回的输入流，确保无论读取是否成功，流都会被自动关闭。
- 新增 `TestS3InputStream` 测试类，使用 Mockito 模拟 S3Client 和 InputStream，验证调用 `readFully` 后输入流的 `close()` 方法被调用。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3InputStream.java` (+3/-1)

**修改目的**：修复 `readFully` 方法中的连接泄漏。

**工作逻辑**：原代码直接调用 `IOUtil.readFully(readRange(range), buffer, offset, length)`，其中 `readRange(range)` 返回的 `InputStream` 没有被任何变量持有，因此无法被关闭。修改后使用 try-with-resources 将其赋值给 `stream` 变量，在 try 块执行完毕后自动调用 `stream.close()`，释放 S3 HTTP 连接。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestS3InputStream.java` (+48/-0)

**修改目的**：新增测试验证 `readFully` 会关闭流。

**工作逻辑**：创建 `TestS3InputStream` 测试类，使用 Mockito mock `S3Client` 和 `InputStream`。配置 mock 的 `S3Client.getObject()` 返回 mock 的 `InputStream`，mock 的 `InputStream.read()` 返回 -1（表示流结束）。创建 `S3InputStream` 实例并调用 `readFully(0, new byte[0])`，然后通过 `verify(inputStream).close()` 断言输入流的 `close()` 方法被调用了一次。

## 总结

该提交修复了一个资源泄漏 bug，通过 try-with-resources 确保 S3 输入流在 `readFully` 方法中被正确关闭，并补充了对应的单元测试以防止回归。
