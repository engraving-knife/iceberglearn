# 提交 3698：Spark 3.4: Pass FileIO on Spark's read path (#16307)

## 提交信息

- **序号**：3698 / 4088
- **哈希**：062e822df12c802f594dee8f7ff1e9a8e9c14f75
- **短哈希**：062e822df
- **日期**：2026-05-12 19:03:32 -0700
- **作者**：Kevin Liu
- **提交说明**：Spark 3.4: Pass FileIO on Spark's read path (#16307)
- **PR/Issue**：#16307

## 总体目的

这个提交是 PR #15683（及长度修复 #16284）向 Spark 3.4 模块的回移植。它将 `FileIO` 对象传递到 Spark 的读取路径上，使得读取操作能够使用 `FileIO` 进行文件解密等操作，而不是依赖旧的 `table.encryption().decrypt(...)` 路径。

此前 Spark 3.4 的读取路径使用 `table.encryption().decrypt(...)` 进行文件解密，这与 v3.5/4.0/4.1 的实现不一致。新版本使用 `fileIO.bulkDecrypt(...)` 方式，更加统一和高效。此回移植将 v3.4 的实现与后续版本对齐。

同时引入了 `SerializableFileIOWithSize` 类（已在 v3.5/4.0/4.1 中存在），用于在 Spark 任务序列化时包装 `FileIO` 并保留文件大小信息，避免不必要的元数据查询（与提交 3689 的修复一致）。

## 如何达成设计目的

通过以下方式实现回移植：
1. 新建 `SerializableFileIOWithSize` 类
2. 修改多个 Reader 类（`BaseReader`、`BaseBatchReader`、`BaseRowReader` 等）使用 `FileIO` 而非 `EncryptionManager`
3. 修改 Scan 类（`SparkBatch`、`SparkScan`、`SparkMicroBatchStream` 等）传递 `FileIO`
4. 适配 v3.4 的特殊性：`BaseReader` 从 `table.encryption().decrypt(...)` 切换到 `fileIO.bulkDecrypt(...)`

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SerializableFileIOWithSize.java` (new file, +125 lines)

**修改目的**：新建可序列化的 FileIO 包装类。

**工作逻辑**：该类包装 `FileIO` 使其可序列化，并在 `newInputFile` 方法中保留文件长度信息，避免底层 IO 模块执行不必要的元数据查询。包含 `newInputFile(String path, long length)` 重写方法（与提交 3689 的修复一致）。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/BaseReader.java` (+13/-13 lines)

**修改目的**：从 encryption 解密切换到 FileIO 解密。

**工作逻辑**：v3.4 特殊适配——将 `table.encryption().decrypt(...)` 替换为 `fileIO.bulkDecrypt(...)`，与 v3.5/4.0/4.1 保持一致，因为广播的 FileIO 现在是 `EncryptingFileIO`（在构造函数中组合）。

### 其他 Reader 和 Scan 类修改

**修改目的**：在读取路径中传递 FileIO。

**工作逻辑**：修改 `BaseBatchReader`、`BaseRowReader`、`BatchDataReader`、`ChangelogRowReader`、`EqualityDeleteRowReader`、`PositionDeletesRowReader`、`RowDataReader`、`SparkBatch`、`SparkChangelogScan`、`SparkInputPartition`、`SparkMicroBatchStream`、`SparkPartitioningAwareScan`、`SparkScan`、`SparkStagedScan` 等类，将 `FileIO` 对象通过读取路径传递。

### 测试文件

**修改目的**：添加测试验证 FileIO 传递。

**工作逻辑**：新建 `TestSerializableFileIOWithSize.java`（+51 lines），更新 `TestRemoteScanPlanning.java`、`TestSparkReaderDeletes.java`、`TestChangelogReader.java` 等测试。

## 总结

这是一个重要的 Spark 3.4 回移植提交，将 FileIO 传递到读取路径，使 v3.4 的文件解密方式与后续版本对齐。同时引入了 `SerializableFileIOWithSize` 类解决文件长度丢失的性能问题。提交说明指出，除 `BaseReader` 需要适配（从 `table.encryption().decrypt` 切换到 `fileIO.bulkDecrypt`）外，其他文件与 v3.5 的补丁逐字节一致。
