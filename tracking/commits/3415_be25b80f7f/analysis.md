# 提交 3415：Spark 4.1: Pass FileIO on Spark's read path (#15448)

## 提交信息

- **序号**：3415 / 4088
- **哈希**：be25b80f7fa06406343f894463b72ded5f383e9e
- **短哈希**：be25b80f7f
- **日期**：2026-03-19 15:18:48 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark 4.1: Pass FileIO on Spark's read path (#15448)
- **PR/Issue**：#15448

## 总体目的

在 Spark 4.1 的读取路径上传递独立的 FileIO 实例，而非始终使用 `table.io()`。这与提交 3413（Scan 接口改为返回 `Supplier<FileIO>`）配合，支持 REST 扫描规划场景下使用带有 planId 的专用 FileIO。通过广播 FileIO 到 executor，确保读取操作使用正确的 FileIO 实例（可能包含 REST 扫描规划所需的认证信息）。

## 如何达成设计目的

1. 新建 `SerializableFileIOWithSize` 包装类，实现 `KnownSizeEstimation` 以避免 Spark SizeEstimator 的开销，并实现 `AutoCloseable` 避免资源泄漏
2. 在 `SparkScan` 中新增 `Supplier<FileIO>` 字段，从扫描获取而非直接使用 `table.io()`
3. 在 `SparkBatch` 中使用广播的 FileIO 替代 `table.io()`
4. 在 `SparkInputPartition` 中新增 FileIO 广播变量
5. 在各 Reader 类中从 `SparkInputPartition` 获取 FileIO 而非从 Table 获取
6. 更新 `SparkMicroBatchStream` 传递 FileIO

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SerializableFileIOWithSize.java` (+120 lines, 新文件)

**修改目的**：提供可序列化的 FileIO 包装器，避免 SizeEstimator 开销。

**工作逻辑**：
- 实现 `KnownSizeEstimation` 接口，`estimatedSize()` 返回固定值 32768，避免 Spark 调用 SizeEstimator
- 实现 `AutoCloseable`，通过 `serializationMarker` 区分主实例和反序列化副本，仅在副本上执行 close
- 实现 `HadoopConfigurable` 代理方法
- 所有 FileIO 方法委托给包装的 `fileIO` 实例
- `wrap(FileIO)` 静态工厂方法创建包装

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkScan.java` (+16/-2 lines)

**修改目的**：在 SparkScan 中存储和使用 Supplier<FileIO>。

**工作逻辑**：
- 新增 `Supplier<FileIO> fileIO` 字段
- 构造函数新增 `Supplier<FileIO> fileIO` 参数
- `toBatch()` 方法将 `fileIO` 传递给 SparkBatch
- `toMicroBatchStream()` 方法将 `fileIO` 传递给 SparkMicroBatchStream

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkBatch.java` (+10/-2 lines)

**修改目的**：使用广播的 FileIO 替代 table.io()。

**工作逻辑**：
- 新增 `Supplier<FileIO> fileIO` 字段
- 在 `createInputPartitions` 中，使用 `SerializableFileIOWithSize.wrap(fileIO.get())` 包装 FileIO 并广播
- 将 `fileIOBroadcast` 传递给 `SparkInputPartition`
- `computePreferredLocations` 使用 `fileIO.get()` 替代 `table.io()`

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkInputPartition.java` (+8 lines)

**修改目的**：存储和提供广播的 FileIO。

**工作逻辑**：
- 新增 `Broadcast<FileIO> fileIOBroadcast` 字段
- 新增 `io()` 方法返回 `fileIOBroadcast.value()`

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/BaseReader.java` (+9/-1 lines)

**修改目的**：从 InputPartition 获取 FileIO。

**工作逻辑**：
- 从 `SparkInputPartition.io()` 获取 FileIO 而非 `table().io()`

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/BaseBatchReader.java` (+4/-1 lines)

**修改目的**：传递 FileIO 给读取器。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/BatchDataReader.java` (+4 lines)

**修改目的**：使用传入的 FileIO。

### 其他 Reader 和 Scan 类 (+various lines)

**修改目的**：各 Reader 类（RowDataReader、ChangelogRowReader、PositionDeletesRowReader、EqualityDeleteRowReader等）和 Scan 类（SparkChangelogScan、SparkStagedScan、SparkPartitioningAwareScan、SparkMicroBatchStream）更新以传递和使用 FileIO。

### 测试文件 (+various lines)

**修改目的**：更新测试以适配新的构造函数签名和 FileIO 传递方式。

- `TestRemoteScanPlanning.java`：新增测试验证 FileIO 在远程扫描规划中被正确传递
- `TestSparkReaderDeletes.java`、`TestChangelogReader.java`、`TestPositionDeletesReader.java`等：更新构造函数调用

## 总结

本提交在 Spark 4.1 读取路径上传递独立的 FileIO 实例，支持 REST 扫描规划场景下使用带有 planId 的专用 FileIO。通过新建 `SerializableFileIOWithSize` 包装类解决广播时的序列化和大小估算问题。FileIO 通过 Spark 广播变量传递到 executor，各 Reader 从广播变量获取 FileIO 而非从 Table 获取。
