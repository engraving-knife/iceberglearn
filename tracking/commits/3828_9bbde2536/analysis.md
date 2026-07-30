# 提交 3828：GCS, S3, ADLS: Handle EOF in inputStreams (#16055)

## 提交信息

- **序号**：3828 / 4088
- **哈希**：9bbde2536a7da5ddbeed7561eb275087350a5f18
- **短哈希**：9bbde2536
- **日期**：2026-06-05 16:41:32 -0700
- **作者**：Vladislav Sidorovich <vsidorovich@google.com>
- **提交说明**：GCS, S3, ADLS: Handle EOF in inputStreams (#16055)
- **PR/Issue**：#16055

## 总体目的

本提交修复 Iceberg 三大云存储集成模块（GCS、S3、ADLS）的 `SeekableInputStream` 实现在到达文件末尾（EOF）时的行为不符合 `InputStream` 契约的缺陷。根据 Java `InputStream.read()` 的契约：当到达流末尾没有数据可读时，单字节 `read()` 应返回 `-1`，批量 `read(byte[], int, int)` 也应返回 `-1`（而非 0 或抛异常）。

然而在 `S3InputStream`、`ADLSInputStream`、`GCSInputStream` 三者中，底层流的 `read()` 返回 `-1`（EOF）时，上层实现没有显式处理这一返回值，而是直接继续推进位置计数（`pos += 1` 或 `pos += bytesRead`）并更新指标，最后把 `-1` 返回给调用方。问题在于：
- 单字节 `read()`：即使底层返回 -1，代码仍执行 `pos += 1`、`next += 1`、`readBytes.increment()`，错误地推进了位置与指标，然后才返回 -1。
- 批量 `read()`：底层返回 -1 时，`pos += bytesRead` 即 `pos += -1`，会让位置倒退 1，造成位置错乱。

这会导致读取器在 EOF 后位置状态不一致，可能引发后续 seek/read 异常或数据错读。本提交在三个实现的 `read()` 方法中，遇到底层返回 -1 时立即 `return -1`，跳过位置与指标更新，从而正确遵循 `InputStream` 契约。

## 如何达成设计目的

修复方式一致：在 `S3InputStream`、`ADLSInputStream`、`GCSInputStream` 的单字节 `read()` 与批量 `read(byte[], int, int)` 方法中，先获取底层读取结果，若为 -1 则立即 `return -1`，不推进 `pos`/`next` 也不更新指标。对 GCS 单字节 `read()` 还重构了 try/catch 块结构，把 `pos += 1` 与指标更新移到 EOF 检查之后。同时为三个模块新增单元/集成测试覆盖 EOF 行为：单字节读到 EOF 返回 -1、批量读到 EOF 返回 -1 且位置不倒退。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3InputStream.java` (+8/-0 lines)

**修改目的**：让 S3 输入流在 EOF 时正确返回 -1，不推进位置与指标。

**工作逻辑**：
在单字节 `read()` 与批量 `read(b, off, len)` 中，获取 `bytesRead` 后立即检查：
```java
int bytesRead = Failsafe.with(retryPolicy).get(() -> stream.read());
if (bytesRead == -1) {
  return -1;
}
pos += 1;
next += 1;
readBytes.increment();
...
```
批量版本同理。这样 EOF 时不会执行 `pos += bytesRead`（避免位置倒退）。

### `aws/src/integration/java/org/apache/iceberg/aws/s3/TestS3InputStream.java` (+36/-0 lines)

**修改目的**：覆盖 S3 输入流的 EOF 行为。

**工作逻辑**：
新增 `testReadSingle` 与 `testReadBufferedEOF`：
- `testReadSingle`：写入 2 字节，连续 `read()` 第三次应返回 `EOF = -1`。
- `testReadBufferedEOF`：写入 8 字节，用 9 字节缓冲区读取应返回 8 字节，再次读取应返回 -1，且 `getPos()` 仍为 8（不倒退）。

### `azure/src/main/java/org/apache/iceberg/azure/adlsv2/ADLSInputStream.java` (+9/-2 lines)

**修改目的**：让 ADLS 输入流在 EOF 时正确返回 -1。

**工作逻辑**：
- 单字节 `read()`：先读取 `int bytesRead = stream.read()`，若 -1 则 `return -1`，否则再推进 `pos`/`next` 与指标。
- 批量 `read()`：读取后若 -1 则 `return -1`，否则推进位置与指标。

### `azure/src/integration/java/org/apache/iceberg/azure/adlsv2/TestADLSInputStream.java` (+21/-0 lines)

**修改目的**：集成测试覆盖 ADLS EOF 行为（单字节读到 EOF、批量读到 EOF 且位置不变）。

### `azure/src/test/java/org/apache/iceberg/azure/adlsv2/TestADLSInputStream.java` (+50/-3 lines)

**修改目的**：单元测试（mock）覆盖 ADLS EOF 行为，无需真实 Azurite。

**工作逻辑**：
新增 mock 测试，用 `ByteArrayInputStream` 模拟底层流，验证单字节与批量读取在 EOF 时返回 -1。

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/GCSInputStream.java` (+12/-5 lines)

**修改目的**：让 GCS 输入流在 EOF 时正确返回 -1。

**工作逻辑**：
- 单字节 `read()`：把 `channel.read(singleByteBuffer)` 的结果取出，若 -1 则 `return -1`，否则再推进 `pos` 与指标。同时把 `pos += 1` 与指标更新从 try 块外移到 EOF 检查之后，避免 EOF 时误推进：
```java
int bytesRead = channel.read(singleByteBuffer);
if (bytesRead == -1) {
  return -1;
}
pos += 1;
readBytes.increment();
readOperations.increment();
return singleByteBuffer.array()[0] & 0xFF;
```
- 批量 `read()`：获取 `bytesRead` 后若 -1 则 `return -1`。

### `gcp/src/test/java/org/apache/iceberg/gcp/gcs/TestGCSInputStream.java` (+20/-1 lines)

**修改目的**：覆盖 GCS 输入流的 EOF 行为。

**工作逻辑**：
新增测试验证单字节读到 EOF 返回 -1、批量读到 EOF 返回 -1 且位置不倒退。

## 总结

本提交修复了 GCS、S3、ADLS 三个云存储输入流在 EOF 时的契约违规：底层返回 -1 时未及时返回，导致位置倒退与指标错误。修复极其简洁（每个实现加一个 -1 检查），但影响重要——EOF 行为不正确可能导致读取循环异常、位置错乱乃至数据损坏。测试覆盖单字节与批量两种读取路径，含集成与 mock 单元测试。这是云存储集成层正确性的重要保障，体现了对 `InputStream` 契约的严格遵守。
