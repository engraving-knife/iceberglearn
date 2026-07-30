# 提交 3373：Spark 3.4, 3.5, 4.0: Don't Use table FileIO for Spark Checkpoints (#15574)

## 提交信息

- **序号**：3373 / 4088
- **哈希**：42d01511c52032e4c08da3d08d5e45b980dacffb
- **短哈希**：42d01511c
- **日期**：2026-03-10
- **作者**：c2zwdjnlcg
- **提交说明**：Spark 3.4, 3.5, 4.0: Don't Use table FileIO for Spark Checkpoints (#15574)
- **PR/Issue**：#15574

## 总体目的

本提交是提交 3369（#15239）的同源改动，将"检查点不使用表 FileIO"的修复从 Spark 4.1 回移到 Spark 3.4、3.5 和 4.0 三个版本模块。

在 Spark Structured Streaming 中使用 Iceberg 作为源时，`SparkMicroBatchStream` 内部的 `InitialOffsetStore` 负责读写检查点文件（存储初始偏移量）。此前它使用 `table.io()`（表自身的 FileIO 实现）来操作检查点文件。问题在于：表的 FileIO 可能是自定义实现（如 S3FileIO 等），而检查点是 Spark 框架自身的基础设施，使用表的 FileIO 可能因凭证、路径协议不匹配等原因导致检查点操作失败。正确做法是使用标准的 `HadoopFileIO` 配合 SparkContext 的 Hadoop 配置来操作检查点。

## 如何达成设计目的

改动覆盖 Spark 3.4、3.5 和 4.0 三个模块，每个模块修改 `SparkMicroBatchStream` 中的 `InitialOffsetStore` 内部类以及新增 `TestStreamingCheckpointHadoopIO` 测试类。核心改动与 3369 完全一致：将 `InitialOffsetStore` 构造函数新增 `Configuration conf` 参数，将 `this.io = table.io()` 改为 `this.io = new HadoopFileIO(conf)`，`fromTimestamp` 从 `Long` 改为 `long`。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkMicroBatchStream.java` (+12/-4 lines)、`spark/v3.5/.../SparkMicroBatchStream.java` (+12/-4 lines)、`spark/v4.0/.../SparkMicroBatchStream.java` (+12/-4 lines)

**修改目的**：使 `InitialOffsetStore` 使用 `HadoopFileIO` 而非表的 FileIO 进行检查点读写。

**工作逻辑**：
三个文件的改动一致。新增 `import org.apache.hadoop.conf.Configuration` 和 `import org.apache.iceberg.hadoop.HadoopFileIO`。构造 `InitialOffsetStore` 时传入 `sparkContext.hadoopConfiguration()`。`InitialOffsetStore` 构造函数新增 `Configuration conf` 参数，`fromTimestamp` 类型从 `Long` 改为 `long`，`this.io = table.io()` 改为 `this.io = new HadoopFileIO(conf)`。这样检查点文件始终通过标准 Hadoop FileIO 读写，与表的 FileIO 实现解耦。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestStreamingCheckpointHadoopIO.java` (+162/-0 lines)、`spark/v3.5/.../TestStreamingCheckpointHadoopIO.java` (+162/-0 lines)、`spark/v4.0/.../TestStreamingCheckpointHadoopIO.java` (+162/-0 lines)

**修改目的**：验证流式检查点始终使用 `HadoopFileIO` 而非表的 FileIO。

**工作逻辑**：
三个文件内容完全一致。测试类继承 `TestBaseWithCatalog`，在 catalog 配置中将 `FILE_IO_IMPL` 设为自定义的 `TrackingFileIO`。该类在 `newInputFile`/`newOutputFile` 被调用时若检测到路径包含 `/offsets/` 则将 `USED` 标志置为 true。测试方法 `testCheckpointsUseHadoopIONotTableIO` 创建表、插入数据、启动流式查询（使用嵌套检查点目录验证父目录创建），运行后断言 `TrackingFileIO.wasUsed()` 为 false，同时验证 `offsets/0` 文件存在。`TrackingFileIO` 内部委托 `HadoopFileIO` 完成实际 IO。

## 总结

本提交将检查点 FileIO 修复回移到 Spark 3.4/3.5/4.0 三个版本，与 3369 共同覆盖了所有受支持的 Spark 版本。改动将检查点操作从表 FileIO 解耦到标准 `HadoopFileIO`，提升了流式读取的可靠性。配套的追踪测试设计巧妙，有效验证了表的 FileIO 不参与检查点操作。
