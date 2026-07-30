# 提交 3709：Flink: Backport support UUID type in Avro and Parquet readers and writers (#16333)

## 提交信息

- **序号**：3709 / 4088
- **哈希**：adfd4a4c80dbe8a8e4ad874514c927ba32576716
- **短哈希**：adfd4a4c8
- **日期**：2026-05-14 15:57:48 +0200
- **作者**：Joy Haldar
- **提交说明**：Flink: Backport support UUID type in Avro and Parquet readers and writers (#16333)
- **PR/Issue**：#16333

## 总体目的

这个提交是 PR #16097（提交 3707）向 Flink 2.0 和 1.20 模块的回移植。它为这两个 Flink 版本添加了 UUID 数据类型在 Avro 和 Parquet 文件格式中的完整读写支持，与 Flink 2.1 的实现保持一致。

## 如何达成设计目的

通过与提交 3707 完全相同的修改，将 UUID 类型支持添加到 Flink 2.0 和 1.20 模块。

## 修改详情

### Flink 2.0 模块

**修改目的**：添加 UUID 类型支持。

- `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/data/FlinkAvroWriter.java` (+1/-1 line)：使用 `FlinkValueWriters.uuids()` 替代 `ValueWriters.uuids()`
- `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/data/FlinkParquetReaders.java` (+6 lines)：添加 `UUIDLogicalTypeAnnotation` 的 visit 方法
- `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/data/FlinkParquetWriters.java` (+6 lines)：添加 `UUIDLogicalTypeAnnotation` 的 visit 方法
- `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/data/FlinkValueWriters.java` (+13 lines)：新增 `UUIDWriter` 和 `uuids()` 方法
- `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkSink.java` (+4/-6 lines)：改进 schema 转换逻辑
- `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/TestFlinkUuidType.java` (new file, +192 lines)：端到端测试

### Flink 1.20 模块

**修改目的**：添加 UUID 类型支持。

与 Flink 2.0 完全相同的修改。

## 总结

这是提交 3707 向 Flink 2.0 和 1.20 的回移植，内容完全一致。至此，所有三个 Flink 版本（1.20、2.0、2.1）都完成了 UUID 类型的完整读写支持，确保跨版本一致性。
