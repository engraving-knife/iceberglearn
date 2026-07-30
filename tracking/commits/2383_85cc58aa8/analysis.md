# 提交 2383：Spark: Use bulk deletions for manifests when importing files to an Iceberg table (#13620)

## 提交信息

- **序号**：2383 / 4088
- **哈希**：85cc58aa8acda999926809b3c67bbc3452689490
- **短哈希**：85cc58aa8
- **日期**：2025-07-22 07:14:06 -0600
- **作者**：Hongyue/Steve Zhang
- **提交说明**：Spark: Use bulk deletions for manifests when importing files to an Iceberg table (#13620)
- **PR/Issue**：#13620

## 总体目的

本提交优化了 Spark 中文件导入 Iceberg 表时的 manifest 文件删除逻辑，改为使用批量删除（bulk deletion）操作。在通过 `SparkTableUtil` 将文件导入 Iceberg 表时，可能需要删除旧的或临时的 manifest 文件。

此前删除 manifest 文件的方式是逐个文件调用 `io.deleteFile()`，通过线程池并行执行。这种方式对于支持批量删除操作的文件系统（如 S3 等对象存储）效率较低，因为每次删除都需要单独的 API 调用，无法利用批量删除接口减少网络往返次数。本次修改在 FileIO 支持 `SupportsBulkOperations` 接口时，优先使用 `deleteFiles` 批量删除方法，一次性删除所有 manifest 文件，从而提升删除效率。

## 如何达成设计目的

设计思路是在 `deleteManifests` 方法中增加类型检查，当 FileIO 实现了 `SupportsBulkOperations` 接口时使用批量删除，否则回退到原有的逐个删除逻辑。关键设计点如下：

1. **类型检查与分支**：通过 `io instanceof SupportsBulkOperations` 判断 FileIO 是否支持批量操作。
2. **批量删除路径**：如果支持，将 manifest 文件路径列表转换后调用 `deleteFiles` 方法一次性删除。
3. **兼容性回退**：如果不支持批量操作，保持原有的 `Tasks.foreach` 并行逐个删除逻辑不变。
4. **路径提取**：使用 Guava 的 `Lists.transform` 将 `ManifestFile` 列表转换为路径字符串列表。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java` (+10/-5 lines)

**修改目的**：在 manifest 删除逻辑中增加批量删除支持。

**工作逻辑**：修改 `deleteManifests(FileIO io, List<ManifestFile> manifests)` 私有方法：
- 新增 `import org.apache.iceberg.io.SupportsBulkOperations` 导入。
- 在方法开头检查 `io instanceof SupportsBulkOperations`：
  - 如果是，将 manifests 列表通过 `Lists.transform(manifests, ManifestFile::path)` 转换为路径列表，然后调用 `((SupportsBulkOperations) io).deleteFiles()` 一次性批量删除。
  - 如果不是，执行原有的 `Tasks.foreach(manifests).executeWith(ThreadPools.getWorkerPool()).noRetry().suppressFailureWhenFinished().run(item -> io.deleteFile(item.path()))` 逻辑。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java` (+10/-5 lines)

**修改目的**：对 Spark 3.5 版本进行相同的优化修改。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java` (+10/-5 lines)

**修改目的**：对 Spark 4.0 版本进行相同的优化修改。

## 总结

本提交是一个性能优化改进，在 Spark 文件导入 Iceberg 表时的 manifest 删除逻辑中增加了批量删除支持。当 FileIO 支持 `SupportsBulkOperations` 接口时（如 S3 等对象存储），使用 `deleteFiles` 批量删除方法代替逐个删除，可以显著减少网络往返次数，提升删除效率。修改涉及 Spark 3.4、3.5 和 4.0 三个版本，每个版本修改 15 行代码，保持了向后兼容性（不支持批量操作的 FileIO 仍使用原有逻辑）。
