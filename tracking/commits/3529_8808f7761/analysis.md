# 提交 3529：ADLS: Throw NotFoundException for inexistent input file (#15806)

## 提交信息

- **序号**：3529 / 4088
- **哈希**：8808f7761637415f933b97c386f5646f3356896f
- **短哈希**：8808f7761
- **日期**：2026-04-14 09:16:23 +0200
- **作者**：Marius Grama
- **提交说明**：ADLS: Throw NotFoundException for inexistent input file (#15806)
- **PR/Issue**：#15806

## 总体目的

当 Iceberg 通过 `ADLSInputStream` 读取 Azure Data Lake Storage (ADLS) 上的文件时，如果文件不存在，Azure SDK 会抛出 `BlobStorageException`（错误码 `BLOB_NOT_FOUND`）或 `DataLakeStorageException`（错误码 `PathNotFound`）。这些是 Azure SDK 特有的异常类型，Iceberg 上层的 `TableOperations` 并不认识它们。

Iceberg 的 `TableOperations` 在执行读取操作时有重试逻辑，但重试只对「瞬时性错误」有意义。对于「文件不存在」这种确定性错误，重试毫无意义，只会浪费时间并产生误导性的堆栈。Iceberg 定义了自己的 `NotFoundException`，上层的 `TableOperations` 会识别这个异常并跳过重试。

本提交在 `ADLSInputStream` 打开输入流时捕获 Azure SDK 的「not found」异常，转换为 Iceberg 的 `NotFoundException` 抛出，从而向上层信号化「文件不存在，无需重试」。

## 如何达成设计目的

在 `ADLSInputStream` 中新增 `throwNotFoundIfNotPresent` 和 `isFileNotFoundException` 两个私有静态方法。当 `fileClient.openInputStream(...)` 抛出运行时异常时，先判断是否为「文件未找到」异常（通过 Azure SDK 的错误码判断），如果是则包装成 `NotFoundException` 抛出。

为了在异常消息中包含文件 location，`ADLSInputStream` 构造函数新增 `location` 参数，由 `ADLSInputFile.newStream()` 传入 `location()`。

## 修改详情

### `azure/src/main/java/org/apache/iceberg/azure/adlsv2/ADLSInputStream.java` (+24/-0 lines)

**修改目的**：捕获 Azure「not found」异常并转换为 Iceberg `NotFoundException`。

**工作逻辑**：
1. 新增 import：`BlobErrorCode`、`BlobStorageException`、`DataLakeStorageException`、`NotFoundException`
2. 新增字段 `private final String location;` 并在构造函数中赋值（构造函数新增 `location` 首参）
3. 在 `openInputStream` 的 catch 块中调用 `throwNotFoundIfNotPresent(e, location)`：
```java
} catch (RuntimeException e) {
  throwNotFoundIfNotPresent(e, location);
  LOG.error("Failed to open input stream for file {}, range {}", fileClient.getFilePath(), range, e);
  throw e;
}
```
4. 新增辅助方法：
```java
private static void throwNotFoundIfNotPresent(Throwable throwable, String location) {
  if (isFileNotFoundException(throwable)) {
    throw new NotFoundException(throwable, "Location does not exist: %s", location);
  }
}

private static boolean isFileNotFoundException(Throwable exception) {
  if (exception instanceof BlobStorageException blobStorageException) {
    return BlobErrorCode.BLOB_NOT_FOUND.equals(blobStorageException.getErrorCode());
  }
  if (exception instanceof DataLakeStorageException dataLakeStorageException) {
    return "PathNotFound".equals(dataLakeStorageException.getErrorCode());
  }
  return false;
}
```
`isFileNotFoundException` 判断两种 Azure 异常：Blob API 的 `BLOB_NOT_FOUND` 错误码和 DataLake API 的 `PathNotFound` 错误码。匹配时抛出 `NotFoundException`，原始异常作为 cause 保留。

### `azure/src/main/java/org/apache/iceberg/azure/adlsv2/ADLSInputFile.java` (+1/-1 lines)

**修改目的**：向 `ADLSInputStream` 传入 location。

**工作逻辑**：
```java
@Override
public SeekableInputStream newStream() {
  return new ADLSInputStream(location(), fileClient(), fileSize, azureProperties(), metrics());
}
```
`location()` 来自 `BaseADLSFile`/`InputFile` 接口，返回文件路径字符串。

### `azure/src/test/java/org/apache/iceberg/azure/adlsv2/TestADLSInputStream.java` (+7/-1 lines)

**修改目的**：单元测试构造 `ADLSInputStream` 时补充 location 参数。

**工作逻辑**：由于构造函数签名变化，单元测试中 `new ADLSInputStream(fileClient, 0L, mock(), mock())` 改为传入 `"abfs://container@account.dfs.core.windows.net/path/to/file"` 作为 location。

### `azure/src/integration/java/org/apache/iceberg/azure/adlsv2/TestADLSInputStream.java` (+14/-6 lines)

**修改目的**：集成测试适配新构造函数签名。

**工作逻辑**：
- 新增辅助方法 `private String location() { return AZURITE_CONTAINER.location(FILE_PATH); }`
- 5 处 `new ADLSInputStream(fileClient(), null, azureProperties, MetricsContext.nullMetrics())` 调用都改为传入 `location()` 作为首参。

### `azure/src/integration/java/org/apache/iceberg/azure/adlsv2/TestADLSFileIO.java` (+20/-0 lines)

**修改目的**：集成测试验证读取不存在的文件时抛出 `NotFoundException`。

**工作逻辑**：
```java
@Test
public void readMissingLocation() {
  String path = "path/to/file";
  String location = AZURITE_CONTAINER.location(path);
  ADLSFileIO io = createFileIO();
  DataLakeFileClient fileClient = AZURITE_CONTAINER.fileClient(path);
  assertThat(fileClient.exists()).isFalse();

  InputFile inputFile = io.newInputFile(location);

  assertThatThrownBy(inputFile::newStream)
      .isInstanceOf(NotFoundException.class)
      .hasCauseInstanceOf(BlobStorageException.class)
      .hasMessage(
          "Location does not exist: abfs://container@account.dfs.core.windows.net/path/to/file");
}
```
测试用 Azurite（本地 Azure 模拟器）创建一个不存在的文件路径，调用 `newInputFile` 然后 `newStream`，断言抛出 `NotFoundException`，cause 是 `BlobStorageException`，消息包含 location。

## 总结

本提交让 `ADLSInputStream` 在文件不存在时抛出 Iceberg 的 `NotFoundException`（而非 Azure SDK 原生异常），使上层 `TableOperations` 能够识别这种确定性错误并跳过无意义的重试。通过检测 Azure Blob/DataLake 的特定错误码（`BLOB_NOT_FOUND`/`PathNotFound`）来准确判断「not found」场景，并保留原始异常作为 cause。配有单元测试和基于 Azurite 的集成测试验证。
