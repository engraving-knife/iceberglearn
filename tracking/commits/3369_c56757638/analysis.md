# 提交 3369：Spark 4.1: Don't Use table FileIO for Spark Checkpoints (#15239)

## 提交信息

- **序号**：3369 / 4088
- **哈希**：c567576389bbc44d86d9383f21ecd9584bc38f20
- **短哈希**：c56757638
- **日期**：2026-03-09
- **作者**：c2zwdjnlcg
- **提交说明**：Spark 4.1: Don't Use table FileIO for Spark Checkpoints (#15239)
- **PR/Issue**：#15239

## 总体目的

Spark Structured Streaming 在使用 Iceberg 作为源时，需要将检查点（checkpoint）信息（如初始偏移量）写入到检查点目录。此前 `SparkMicroBatchStream` 中的 `InitialOffsetStore` 使用 `table.io()`（即表自身的 FileIO 实现）来读写检查点文件。这存在两个问题：

第一，表的 FileIO 可能是自定义实现（如 S3FileIO、HadoopFileIO 配合特定凭证等），检查点是 Spark 框架自身的基础设施，不应依赖于表的 FileIO 实现，否则可能导致检查点操作失败或行为不一致。例如，当表使用自定义 FileIO 但检查点目录位于本地或 HDFS 时，用表的 FileIO 写入可能因路径不匹配或凭证配置问题而失败。

第二，检查点目录的创建需要使用 Hadoop 的 `Configuration`，而表 FileIO 可能没有正确的 Hadoop 配置上下文。

本次提交将 `InitialOffsetStore` 中的 FileIO 从 `table.io()` 改为使用 `HadoopFileIO`，并通过 SparkContext 的 Hadoop 配置来初始化，确保检查点操作始终使用标准的 Hadoop FileIO。

## 如何达成设计目的

改动涉及 Spark 4.1 模块的 `SparkMicroBatchStream` 中的 `InitialOffsetStore` 内部类，将其构造函数新增 `Configuration conf` 参数，并用 `new HadoopFileIO(conf)` 替代 `table.io()`。同时新增了一个完整的测试类 `TestStreamingCheckpointHadoopIO`，通过自定义 `TrackingFileIO` 验证表的 FileIO 不会被用于检查点操作。

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkMicroBatchStream.java` (+12/-4 lines)

**修改目的**：使 `InitialOffsetStore` 使用 `HadoopFileIO` 而非表的 FileIO 进行检查点读写。

**工作逻辑**：
新增 `import org.apache.hadoop.conf.Configuration` 和 `import org.apache.iceberg.hadoop.HadoopFileIO`。在构造 `InitialOffsetStore` 时，新增传入 `sparkContext.hadoopConfiguration()` 作为 Hadoop 配置。`InitialOffsetStore` 内部类的构造函数签名从 `InitialOffsetStore(Table table, String checkpointLocation, Long fromTimestamp)` 改为 `InitialOffsetStore(Table table, String checkpointLocation, long fromTimestamp, Configuration conf)`，其中 `fromTimestamp` 的类型从 `Long`（包装类型）改为 `long`（基本类型）。最关键的改动是将 `this.io = table.io()` 改为 `this.io = new HadoopFileIO(conf)`，这样检查点文件的读写就不再依赖表的 FileIO 实现。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/source/TestStreamingCheckpointHadoopIO.java` (+162/-0 lines)

**修改目的**：验证流式检查点始终使用 `HadoopFileIO` 而非表的 FileIO。

**工作逻辑**：
新增测试类继承 `TestBaseWithCatalog`。核心设计是在 catalog 配置中将 `FILE_IO_IMPL` 设为自定义的 `TrackingFileIO`，该类在 `newInputFile`/`newOutputFile` 被调用时若检测到路径包含 `/offsets/`（检查点偏移量路径），则将 `USED` 标志置为 true。测试方法 `testCheckpointsUseHadoopIONotTableIO` 创建表并插入数据后，启动流式查询并指定嵌套检查点目录（`nested/checkpoint`，用于验证父目录创建），运行后断言 `TrackingFileIO.wasUsed()` 为 false（表的 FileIO 未被用于检查点），同时验证 `offsets/0` 文件确实存在。`TrackingFileIO` 内部委托 `HadoopFileIO` 完成实际 IO 操作，仅在调用时记录是否触发了检查点路径。

## 总结

本次提交修复了 Spark 4.1 流式读取中检查点使用表 FileIO 的潜在问题，改为统一使用 `HadoopFileIO` 配合 SparkContext 的 Hadoop 配置，提升了检查点操作的可靠性和隔离性。配套的追踪测试通过自定义 FileIO 包装器有效验证了表的 FileIO 不再参与检查点操作，设计巧妙且覆盖完整。
