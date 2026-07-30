# 提交 3491：GCS: Throw NotFoundException for nonexisting input GCS file (#15734)

## 提交信息

- **序号**：3491 / 4088
- **哈希**：fd21d667fb51433f08df8e538054a1461822b328
- **短哈希**：fd21d667fb
- **日期**：2026-04-01 07:34:46 -0700
- **作者**：Marius Grama
- **提交说明**：GCS: Throw NotFoundException for nonexisting input GCS file (#15734)
- **PR/Issue**：#15734

## 总体目的

当读取不存在的 GCS 文件时，将底层的 `IOException`（由 `StorageException` 404 引起）转换为 Iceberg 的 `NotFoundException`。这向 TableOperations 信号该文件不存在，无需重试。原行为是抛出原始 IOException，TableOperations 可能会进行不必要的重试。

## 如何达成设计目的

1. 新增 `GCSExceptionUtil` 工具类，提供 `throwNotFoundIfNotPresent` 方法，检查 IOException 的 cause 是否为 404 StorageException，如果是则抛出 `NotFoundException`。
2. 在所有 GCS 输入流的读取方法（`read`、`read(byte[],int,int)`、`readFully`、`readTail`、`readVectored`）中捕获 IOException，调用 `throwNotFoundIfNotPresent` 转换异常。
3. 在 `GcsInputStreamWrapper` 构造函数中新增 `BlobId` 参数，用于异常消息中生成文件 URI。
4. 更新 `GCSInputFile` 传递 `blobId` 给 wrapper。
5. 更新测试验证 NotFoundException 行为。

## 修改详情

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/GCSExceptionUtil.java` (+35 lines, 新文件)

**修改目的**：异常转换工具类。

**工作逻辑**：
```java
static void throwNotFoundIfNotPresent(IOException ioException, BlobId blobId) {
  if (ioException.getCause() instanceof StorageException storageException
      && storageException.getCode() == 404) {
    throw new NotFoundException(ioException, "Location does not exist: %s", blobId.toGsUtilUri());
  }
}
```
检查 IOException 的 cause 是否为 404 StorageException，是则抛出 NotFoundException。

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/GCSInputFile.java` (+2/-2 lines)

**修改目的**：传递 blobId 给 GcsInputStreamWrapper。

**工作逻辑**：两处 `new GcsInputStreamWrapper(stream, metrics())` 改为 `new GcsInputStreamWrapper(stream, blobId(), metrics())`。

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/GCSInputStream.java` (+10/-2 lines)

**修改目的**：在 GCSInputStream 的 read 方法中捕获并转换 404 异常。

**工作逻辑**：`read()` 单字节读取和 `read(ByteBuffer)` 方法中，用 try-catch 包裹 channel.read，捕获 IOException 后调用 `GCSExceptionUtil.throwNotFoundIfNotPresent(e, blobId)`。

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/GcsInputStreamWrapper.java` (+35/-8 lines)

**修改目的**：在 GcsInputStreamWrapper 的所有读取方法中捕获并转换 404 异常。

**工作逻辑**：
- 构造函数新增 `BlobId blobId` 参数并校验非 null。
- `read()`、`read(byte[],int,int)`、`readFully`、`readTail`、`readVectored` 方法中用 try-catch 包裹 stream 调用，捕获 IOException 后调用 `GCSExceptionUtil.throwNotFoundIfNotPresent(e, blobId)`。

### `gcp/src/integration/java/org/apache/iceberg/gcp/gcs/TestGcsFileIO.java` (+33/-4 lines)

**修改目的**：更新集成测试验证 NotFoundException 行为。

**工作逻辑**：
- `readMissingLocation`：扩展测试，先创建流和 seek（本地操作），再 read 时验证抛出 NotFoundException，cause 为 IOException，消息为 "Location does not exist: gs://..."。
- 新增 `readMissingLocationGcsAnalyticsCoreEnabled`：验证启用 GCS Analytics Core 时的 NotFoundException 行为。

### `gcp/src/test/java/org/apache/iceberg/gcp/gcs/TestGCSInputStream.java` (+3 lines)

**修改目的**：修复 testClose 测试，先写入数据再测试关闭。

**工作逻辑**：在 `testClose` 中先写入 1MB 随机数据，避免因空文件导致的问题。

### `gcp/src/test/java/org/apache/iceberg/gcp/gcs/TestGcsInputStreamWrapper.java` (+4/-1 lines)

**修改目的**：更新测试以适配新的 BlobId 构造参数。

**工作逻辑**：`GcsInputStreamWrapper` 构造时传入 `BlobId.of("mockbucket", "mockname")`。

## 总结

修复 GCS 文件读取时不存在文件异常处理的提交。将 404 StorageException 转换为 Iceberg 的 NotFoundException，使 TableOperations 能正确识别文件不存在而无需重试。新增 GCSExceptionUtil 工具类，在所有 GCS 输入流的读取方法中应用异常转换。
