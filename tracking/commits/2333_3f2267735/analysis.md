# 提交 2333：Spark: Use native table FileIO instead of Hadoop to save file list in RewriteTablePath (#13459)

## 提交信息

- **序号**：2333 / 4088
- **哈希**：3f2267735b80c49f3cfc3e22c29bb610d1183582
- **短哈希**：3f2267735
- **日期**：2025-07-09 12:01:18 -0700
- **作者**：NikitaMatskevich
- **提交说明**：Spark: Use native table FileIO instead of Hadoop to save file list in RewriteTablePath (#13459)
- **PR/Issue**：#13459

## 总体目的

本提交修改了 `RewriteTablePathSparkAction` 中保存文件列表的实现方式，从使用 Spark/Hadoop 的 Dataset CSV 写入改为使用 Iceberg 表原生的 `FileIO` 进行写入。

`RewriteTablePathSparkAction` 是 Iceberg Spark 模块中用于重写表路径（如表迁移）的动作。在执行过程中，它需要将待移动文件的列表（源路径到目标路径的映射）保存为一个 CSV 文件。此前，这个保存操作使用 Spark 的 `Dataset.write().format("csv")` 来完成，这依赖于 Hadoop 文件系统 API。

使用 Spark/Hadoop 写入存在几个问题：
1. 它绕过了 Iceberg 表配置的原生 `FileIO`，可能导致在某些存储后端（如 S3、GCS 等非 Hadoop 原生支持的存储）上行为不一致。
2. 通过 Spark Dataset 写入一个小文件列表是重量级操作，需要创建 Dataset、repartition、序列化等开销。
3. 文件列表本身数据量很小，使用 Spark 分布式写入并不必要。

本提交改为使用 `table.io().newOutputFile()` 创建输出文件，并通过简单的 `BufferedWriter` 写入 CSV 内容，既使用了表原生的 FileIO 保证一致性，又简化了实现。

## 如何达成设计目的

设计思路是用直接的文件 I/O 替代 Spark Dataset 写入，使用表配置的 `FileIO` 实例。

关键设计点：
1. 使用 `table.io().newOutputFile(fileListPath)` 获取 `OutputFile`，复用表的 FileIO 配置。
2. 新增 `writeAsCsv` 私有方法，使用 `BufferedWriter` + `OutputStreamWriter`（UTF-8 编码）将文件对列表写入 CSV。
3. CSV 格式保持简单：每行 `源路径,目标路径`。
4. 同步修改 Spark 3.4、3.5、4.0 三个版本的代码，保持一致。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java` (+18/-12 lines)

**修改目的**：改用原生 FileIO 保存文件列表。

**工作逻辑**：
1. **导入变更**：新增 `BufferedWriter`、`OutputStreamWriter`、`StandardCharsets`；移除 `SaveMode`、`Tuple2`。
2. **`saveFileList` 方法简化**：移除了将 `Set<Pair>` 转换为 `List<Tuple2>`、创建 `Dataset`、repartition、使用 Spark CSV 写入的整段逻辑。改为直接调用 `table.io().newOutputFile(fileListPath)` 创建输出文件，并委托给 `writeAsCsv` 方法写入。
3. **新增 `writeAsCsv` 方法**：使用 `outputFile.createOrOverwrite()` 创建输出流，包装为 `BufferedWriter`，遍历文件对写入 `first,second` 格式的 CSV 行，异常包装为 `RuntimeIOException`。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java` (+18/-12 lines)

**修改目的**：同 v3.4，保持版本间一致。

**工作逻辑**：与 v3.4 完全相同的修改。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java` (+18/-12 lines)

**修改目的**：同 v3.4，保持版本间一致。

**工作逻辑**：与 v3.4 完全相同的修改。

## 总结

本提交将 `RewriteTablePathSparkAction` 中保存文件列表的实现从 Spark/Hadoop Dataset 写入改为使用 Iceberg 表原生的 `FileIO`。这不仅保证了与表存储后端的一致性，还简化了实现并减少了不必要的 Spark 序列化开销。三个 Spark 版本（3.4、3.5、4.0）的代码同步修改，保持一致性。
