# 提交 2398：improve error logging in ADLSFileIO (#13517)

## 提交信息

- **序号**：2398 / 4088
- **哈希**：494b27e0d9663c88756ec9b5a73b10b8934c3b1e
- **短哈希**：494b27e0d
- **日期**：2025-07-23 21:50:54 -0700
- **作者**：NikitaMatskevich
- **提交说明**：improve error logging in ADLSFileIO (#13517)
- **PR/Issue**：#13517

## 总体目的

此提交改善了 Azure Data Lake Storage (ADLS) FileIO 的错误日志记录。原先 ADLSFileIO 在文件操作（删除、读取、写入、存在性检查）失败时，要么不记录错误日志（如输入/输出流打开失败），要么仅捕获特定异常类型（如 `DataLakeStorageException`），导致非预期异常被静默吞掉或日志信息不足以诊断问题。

改进后，所有 ADLS 文件操作的关键路径都添加了 error 级别的日志记录，包含文件路径和操作上下文信息，并将异常捕获范围从特定的 `DataLakeStorageException` 扩大到 `RuntimeException`，确保所有运行时异常都能被记录和传播。

## 如何达成设计目的

关键设计点：

1. **BaseADLSFile 添加 Logger**：在基类中引入 SLF4J Logger，供子类（ADLSInputStream、ADLSOutputStream）使用。
2. **扩大异常捕获范围**：将 `deleteFile` 中的 `DataLakeStorageException` 捕获改为 `RuntimeException`，确保所有运行时异常都被记录。
3. **添加操作上下文日志**：在 `openRange`、`getOutputStream`、`exists` 等方法中添加 try-catch，记录失败时的文件路径和操作参数。
4. **保留异常传播**：日志记录后重新抛出异常，不改变原有的错误处理语义，仅增加可观测性。
5. **openRange 返回类型调整**：从返回 `InputStream` 改为返回 `DataLakeFileOpenInputStreamResult`，使调用方能获取文件属性（如 fileSize），同时统一了异常处理点。

## 修改详情

### `azure/src/main/java/org/apache/iceberg/azure/adlsv2/ADLSFileIO.java` (+1/-1 lines)

**修改目的**：扩大删除操作的异常捕获范围。

**工作逻辑**：`deleteFile` 方法中将 `catch (DataLakeStorageException e)` 改为 `catch (RuntimeException e)`。原先仅捕获 Azure SDK 的特定异常，其他运行时异常（如网络超时、认证失败等）会未被捕获地传播。改为 RuntimeException 后，所有异常都被记录为 warn 日志，但不会中断删除操作（因为删除是非关键操作）。

### `azure/src/main/java/org/apache/iceberg/azure/adlsv2/ADLSInputStream.java` (+14/-6 lines)

**修改目的**：改善输入流读取的错误日志。

**工作逻辑**：`openRange` 方法返回类型从 `InputStream` 改为 `DataLakeFileOpenInputStreamResult`，添加 try-catch 记录文件路径和读取范围的错误日志。`openStream`、`readRange`（用于 RangeReadable）和 `readTail` 方法相应调整，通过 `.getInputStream()` 获取输入流。这样在打开输入流失败时，日志会包含文件路径和请求的读取范围，便于诊断。

### `azure/src/main/java/org/apache/iceberg/azure/adlsv2/ADLSOutputStream.java` (+6/-1 lines)

**修改目的**：添加输出流打开的错误日志。

**工作逻辑**：在构造函数中打开输出流时添加 try-catch，失败时记录文件路径的 error 日志后重新抛出异常。

### `azure/src/main/java/org/apache/iceberg/azure/adlsv2/BaseADLSFile.java` (+10/-1 lines)

**修改目的**：在基类中添加 Logger 并改善 exists 方法的错误日志。

**工作逻辑**：新增 SLF4J Logger 字段。`exists` 方法添加 try-catch，失败时记录文件路径的 error 日志后重新抛出异常。

## 总结

此提交系统性地改善了 ADLSFileIO 的错误可观测性，在文件删除、读取、写入、存在性检查等关键路径添加了带上下文信息的 error 日志。同时扩大了异常捕获范围从特定的 `DataLakeStorageException` 到 `RuntimeException`，确保所有运行时异常都被记录。这些改进不影响原有功能逻辑，仅增强了问题诊断能力。
