# 提交 2781：Flink: add serializer test for StatisticsOrRecord (#14381)

## 提交信息

- **序号**：2781 / 4088
- **哈希**：81cd5b5e3f71412e1580b7fda19e27fc988845a3
- **短哈希**：81cd5b5e3
- **日期**：2025-10-21 09:12:00 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：Flink: add serializer test for StatisticsOrRecord (#14381)
- **PR/Issue**：#14381

## 总体目的

本提交为 Flink sink shuffle 模块中的 `StatisticsOrRecord` 添加序列化器测试，并补充 `equals`/`hashCode` 方法以支持测试中的对象比较。

`StatisticsOrRecord` 是 Flink sink shuffle 功能中的一个核心数据结构，用于在 sink shuffle 过程中传递全局统计信息或数据记录。它有一个对应的 `StatisticsOrRecordSerializer` 负责序列化/反序列化。然而在此之前，该序列化器缺少专门的测试覆盖。

Flink 提供了 `SerializerTestBase` 基类，可以自动测试序列化器的各种场景（如序列化-反序列化往返、并发序列化、快照等）。为了使用 `SerializerTestBase`，被序列化的对象需要正确实现 `equals` 和 `hashCode` 方法。`StatisticsOrRecord` 之前缺少这两个方法，本提交一并补充。

## 如何达成设计目的

1. **为 `StatisticsOrRecord` 添加 `equals` 和 `hashCode`**：使用 Guava 的 `Objects.equal` 和 `Objects.hashCode` 方法，基于 `statistics` 和 `record` 两个字段进行比较。

2. **在 `Fixtures` 中添加 `StatisticsOrRecordSerializer` 实例**：供测试引用。同时将 `ROW_SERIALIZER` 的类型从 `TypeSerializer<RowData>` 改为更具体的 `RowDataSerializer`，因为测试中需要调用 `toBinaryRow()` 方法。

3. **新增 `TestStatisticsOrRecordSerializer` 测试类**：继承 Flink 的 `SerializerTestBase<StatisticsOrRecord>`，提供三种测试数据：fromRecord（含一条记录）、fromStatistics（map 分配型统计）、fromStatistics（range bounds 型统计）。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/StatisticsOrRecord.java` (+20/-0 lines)

**修改目的**：添加 `equals` 和 `hashCode` 方法以支持序列化器测试中的对象比较。

**工作逻辑**：`equals` 方法先检查引用相等，再检查类型，最后用 `Objects.equal` 比较 `statistics` 和 `record` 字段。`hashCode` 用 `Objects.hashCode(statistics, record)` 计算。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/Fixtures.java` (+5/-2 lines)

**修改目的**：添加 `StatisticsOrRecordSerializer` 常量供测试使用。

**工作逻辑**：新增 `STATISTICS_OR_RECORD_SERIALIZER` 常量，通过 `new StatisticsOrRecordSerializer(GLOBAL_STATISTICS_SERIALIZER, ROW_SERIALIZER)` 创建。将 `ROW_SERIALIZER` 类型从 `TypeSerializer<RowData>` 改为 `RowDataSerializer`，以支持测试中调用 `toBinaryRow()`。移除未使用的 `RowData` import。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestStatisticsOrRecordSerializer.java` (+72 lines, 新文件)

**修改目的**：为 `StatisticsOrRecordSerializer` 添加基于 Flink `SerializerTestBase` 的全面序列化测试。

**工作逻辑**：继承 `SerializerTestBase<StatisticsOrRecord>`，实现四个方法：
- `createSerializer()`：返回 `STATISTICS_OR_RECORD_SERIALIZER`
- `getLength()`：返回 -1（变长序列化）
- `getTypeClass()`：返回 `StatisticsOrRecord.class`
- `getTestData()`：返回三种测试数据——使用 `ROW_SERIALIZER.toBinaryRow()` 创建的记录型（因为反序列化产生 BinaryRowData）、map 分配型统计、range bounds 型统计

`SerializerTestBase` 会自动运行一系列测试：序列化-反序列化往返一致性、并发序列化线程安全、序列化快照兼容性等。

## 总结

本提交为 `StatisticsOrRecordSerializer` 补充了基于 Flink `SerializerTestBase` 的全面测试覆盖，确保序列化器在各种场景下（往返序列化、并发访问、快照等）的正确性。为此也给 `StatisticsOrRecord` 类补充了 `equals`/`hashCode` 方法。注意此提交针对 Flink 2.1 版本，2781 是向 Flink 1.20/2.0 的 backport。
