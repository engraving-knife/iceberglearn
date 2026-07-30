# 提交 3417：Spark 3.5: Pass FileIO on Spark's read path (#15683)

## 提交信息

- **序号**：3417 / 4088
- **哈希**：0c46639e0e2e60748d6823d3620bd70838151fa9
- **短哈希**：0c46639e0e
- **日期**：2026-03-19 15:19:18 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark 3.5: Pass FileIO on Spark's read path (#15683)
- **PR/Issue**：#15683

## 总体目的

这是提交 3415（PR #15448）的 Spark 3.5 版本，在 Spark 3.5 的读取路径上传递独立的 FileIO 实例，而非始终使用 `table.io()`。与 Scan 接口返回 `Supplier<FileIO>` 的改动配合，支持 REST 扫描规划场景下使用带有 planId 的专用 FileIO。

## 如何达成设计目的

与提交 3415 完全相同的改动，应用到 Spark 3.5 版本：
1. 新建 `SerializableFileIOWithSize` 包装类
2. 在 `SparkScan` 中新增 `Supplier<FileIO>` 字段
3. 在 `SparkBatch` 中使用广播的 FileIO
4. 在 `SparkInputPartition` 中新增 FileIO 广播变量
5. 各 Reader 类从 InputPartition 获取 FileIO

## 修改详情

改动文件和内容与提交 3415 完全一致，共 22 个文件，+296/-25 lines。包括：

### 新建文件
- `SerializableFileIOWithSize.java` (+120 lines)：FileIO 包装器，实现 `KnownSizeEstimation` 和 `AutoCloseable`

### 主要修改文件
- `SparkScan.java` (+15/-1 lines)：新增 Supplier<FileIO> 字段
- `SparkBatch.java` (+10/-2 lines)：广播 FileIO 并传递给 InputPartition
- `SparkInputPartition.java` (+8 lines)：存储和提供 FileIO
- `BaseReader.java` (+9/-1 lines)：从 InputPartition 获取 FileIO
- `BaseBatchReader.java` (+10/-1 lines)：传递 FileIO
- `BaseRowReader.java` (+10/-1 lines)：传递 FileIO
- 各 Reader 类和 Scan 类更新

### 测试文件
- `TestRemoteScanPlanning.java` (+47 lines)
- 其他测试文件更新

## 总结

本提交是 PR #15448 的 Spark 3.5 版本，将独立的 FileIO 实例传递到 Spark 3.5 读取路径上。改动内容与 Spark 4.1 版本（提交 3415）完全一致。
