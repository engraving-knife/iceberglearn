# 提交 2799：Flink: add jmh benchmark for StatisticsOrRecordSerializer (#14394)

## 提交信息

- **序号**：2799 / 4088
- **哈希**：d5979670cffa740a3ceeb97947ea8f2a71fc9ef6
- **短哈希**：d5979670c
- **日期**：2025-10-27 11:43:02 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：Flink: add jmh benchmark for StatisticsOrRecordSerializer (#14394)
- **PR/Issue**：#14394

## 总体目的

本提交为 Flink sink shuffle 模块中的 `StatisticsOrRecordSerializer` 添加 JMH（Java Microbenchmark Harness）基准测试，用于评估自定义序列化器与 Flink 默认 Kryo 序列化器的性能差异。

Iceberg Flink sink 的 shuffle 机制使用 `StatisticsOrRecord` 来在 Flink 算子间传输统计信息或记录数据。序列化器性能直接影响 shuffle 的效率，因为每条记录都需要经过序列化/反序列化。项目提供了自定义的 `StatisticsOrRecordTypeInformation` 及其序列化器，相比 Flink 默认的 Kryo 序列化器应有性能优势，但需要基准测试来量化这一优势。

基准测试分别在 Flink 1.20、Flink 2.0 和 Flink 2.1 三个版本的模块中添加，因为不同 Flink 版本的序列化基础设施有差异。

## 如何达成设计目的

为每个 Flink 版本模块创建 `StatisticsRecordSerializerBenchmark` JMH 基准测试类，主要设计如下：

1. **测试数据准备**：构建一个包含 10 个字段（1 个 int + 9 个 200 字符长度的 string）的 Schema 和 SortOrder，生成 100,000 条 `StatisticsOrRecord` 样本数据。
2. **对比两种序列化器**：`testCustomSerializer` 使用自定义的 `StatisticsOrRecordTypeInformation` 创建的序列化器；`testDefaultSerializer` 使用 Flink 默认的 `TypeInformation.of(StatisticsOrRecord.class)` 创建的序列化器（基于 Kryo）。
3. **序列化/反序列化循环**：对每条记录执行序列化到 `DataOutputSerializer`，再从 `DataInputDeserializer` 反序列化，使用 Blackhole 消费结果防止 JIT 优化。
4. **JMH 配置**：单次执行模式（SingleShotTime），1 个 fork，3 次预热迭代，5 次测量迭代，单线程。

## 修改详情

### `flink/v1.20/flink/src/jmh/java/.../StatisticsRecordSerializerBenchmark.java` (+148/-0 lines, new file)

**修改目的**：为 Flink 1.20 模块添加序列化器基准测试。

**工作逻辑**：Flink 1.20 版本的基准测试类包含额外的文档说明：由于使用了 `ArraysAsListSerializer`（来自 `FlinkChillPackageRegistrar`），该基准测试只能在 Java 11 下运行，Java 17 会因模块系统限制导致反射访问异常。Flink 2.0+ 已切换到 Kryo 5.6 的 `DefaultSerializers.ArraysAsListSerializer`，不再有此问题。测试逻辑与整体设计一致。

### `flink/v2.0/flink/src/jmh/java/.../StatisticsRecordSerializerBenchmark.java` (+127/-0 lines, new file)

**修改目的**：为 Flink 2.0 模块添加序列化器基准测试。

**工作逻辑**：与 Flink 1.20 版本逻辑基本相同，但移除了 Java 11 限制相关的文档说明和 `FlinkChillPackageRegistrar` 的导入。因为 Flink 2.0+ 使用 Kryo 5.6，不再有 `ArraysAsListSerializer` 的反射问题。

### `flink/v2.1/flink/src/jmh/java/.../StatisticsRecordSerializerBenchmark.java` (+127/-0 lines, new file)

**修改目的**：为 Flink 2.1 模块添加序列化器基准测试。

**工作逻辑**：与 Flink 2.0 版本基本相同。

## 总结

本提交为 Flink sink shuffle 模块的 `StatisticsOrRecordSerializer` 添加了 JMH 基准测试，覆盖 Flink 1.20、2.0 和 2.1 三个版本。通过对比自定义序列化器和 Flink 默认 Kryo 序列化器的性能，可以量化自定义序列化器的性能优势，为 shuffle 性能优化提供数据支撑。Flink 1.20 版本的测试有 Java 11 限制说明，反映了不同 Flink 版本序列化基础设施的差异。
