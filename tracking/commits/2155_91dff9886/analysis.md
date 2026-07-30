# 提交 2155：Parquet: Log corrupted parquet filenames to trace bad nodes that may have written them. (#13108)

## 提交信息

- **序号**：2155 / 4088
- **哈希**：91dff9886e6e6c494f6a970129f4c08487c98a0d
- **短哈希**：91dff9886
- **日期**：2025-05-22 06:39:47 -0500
- **作者**：Dhruv Pratap
- **提交说明**：Parquet: Log corrupted parquet filenames to trace bad nodes that may have written them. (#13108)
- **PR/Issue**：#13108

## 总体目的

在生产环境中，可能会遇到 Parquet 文件损坏的情况，这些损坏的文件可能由某些有问题的节点（如磁盘故障、内存问题或软件 bug 的节点）写入。然而，当 Parquet 解码失败时，底层的 Parquet 库抛出的 `ParquetDecodingException` 通常不包含具体是哪个文件出错的信息，这使得运维人员难以定位是哪个节点产生了损坏文件。该提交在 Iceberg 的 `ParquetReader` 中捕获 `ParquetDecodingException`，并在重新抛出异常之前，将出错的 Parquet 文件路径记录到错误日志中，从而便于追踪产生损坏文件的坏节点，加速问题诊断和修复。

## 如何达成设计目的

- 在 `ParquetReader.FileIterator` 的 `next()` 方法中，将原有的读取逻辑用 try-catch 包裹。
- 捕获 `ParquetDecodingException` 异常后，检查 `reader` 是否非空，若非空则通过 `LOG.error` 记录出错的具体 Parquet 文件路径（`reader.getFile()`）。
- 将异常重新抛出，不改变原有的错误处理行为，只是在抛出前增加了日志记录。
- 为 `FileIterator` 内部类添加了 SLF4J Logger 静态字段。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetReader.java` (修改, +27/-11 lines)

**修改目的**：在 Parquet 解码失败时记录出错的文件路径，便于追踪产生损坏文件的节点。

**工作逻辑**：
- 新增 `ParquetDecodingException`、`Logger`、`LoggerFactory` 的导入。
- 在 `FileIterator` 内部类中新增静态 Logger 字段 `LOG`。
- 重构 `next()` 方法：将原有的行组推进（`advance()`）和数据读取（`model.read()`）逻辑放入 try 块中。在 catch 块中捕获 `ParquetDecodingException`，当 `reader` 非空时，调用 `LOG.error("Error decoding Parquet file {}", reader.getFile(), e)` 记录文件路径和异常堆栈，随后将异常重新抛出。这确保了原有的异常传播行为不变，但在异常发生时额外记录了关键的文件路径信息。

## 总结

该提交是一个实用的可观测性改进，通过在 Parquet 解码异常时记录文件路径，帮助运维和开发人员快速定位损坏文件的来源节点，提升生产环境中的问题排查效率。修改轻量且不改变原有错误处理语义。
