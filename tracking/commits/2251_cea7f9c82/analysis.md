# 提交 2251：Flink: Dynamic Iceberg Sink: Rename method in DynamicRecordGenerator (#13342)

## 提交信息

- **序号**：2251 / 4088
- **哈希**：cea7f9c8299970eb45cb7fad05a3c414e70b15e3
- **短哈希**：cea7f9c82
- **日期**：2025-06-18 16:10:23 +0200
- **作者**：Maximilian Michels
- **提交说明**：Flink: Dynamic Iceberg Sink: Rename method in DynamicRecordGenerator
- **PR/Issue**：#13342

## 总体目的

本提交是对上一个提交（2250）中引入的 `DynamicRecordGenerator` 接口的方法重命名。将接口中的核心方法 `convert` 重命名为 `generate`，以更准确地表达其语义——该方法的作用是根据输入记录"生成"（generate）零个、一个或多个 `DynamicRecord`，而非简单地"转换"（convert）。

在动态 Iceberg Sink 的设计中，`DynamicRecordGenerator` 接口允许用户自定义输入数据到 `DynamicRecord` 的映射逻辑。方法名 `convert` 暗示的是一种简单的类型转换操作，但实际上用户可以在 `generate` 方法中实现更复杂的逻辑，如根据输入内容路由到不同的表、动态生成 schema 等。"generate"一词更能体现这种灵活的多输出特性，也与其他 Flink 中的命名习惯保持一致。

## 如何达成设计目的

- 将 `DynamicRecordGenerator` 接口中的 `convert(T inputRecord, Collector<DynamicRecord> out)` 方法重命名为 `generate(T inputRecord, Collector<DynamicRecord> out)`。
- 更新所有调用该方法的位置，包括 `DynamicRecordProcessor` 中的调用和测试类中的实现。
- 方法签名和语义保持不变，仅修改方法名。

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecordGenerator.java` (修改, +1/-1 lines)

**修改目的**：将接口方法 `convert` 重命名为 `generate`。

**工作逻辑**：方法签名从 `void convert(T inputRecord, Collector<DynamicRecord> out) throws Exception` 改为 `void generate(T inputRecord, Collector<DynamicRecord> out) throws Exception`。Javadoc 注释保持不变。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecordProcessor.java` (修改, +1/-1 lines)

**修改目的**：更新 `processElement` 方法中对 generator 的调用。

**工作逻辑**：将 `generator.convert(element, this)` 改为 `generator.generate(element, this)`。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicIcebergSink.java` (修改, +1/-1 lines)

**修改目的**：更新测试中 Generator 实现类的方法名。

**工作逻辑**：将内部类 `Generator` 中的 `public void convert(DynamicIcebergDataImpl row, Collector<DynamicRecord> out)` 改为 `public void generate(DynamicIcebergDataImpl row, Collector<DynamicRecord> out)`。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicIcebergSinkPerf.java` (修改, +1/-1 lines)

**修改目的**：更新性能测试中 IdBasedGenerator 实现类的方法名。

**工作逻辑**：将 `public void convert(Integer id, Collector<DynamicRecord> out)` 改为 `public void generate(Integer id, Collector<DynamicRecord> out)`。

## 总结

本提交是一个简单的 API 命名优化，将 `DynamicRecordGenerator.convert()` 重命名为 `generate()`，使方法名更准确地反映其"生成多条记录"的语义而非简单的"转换"。改动涉及接口定义、调用方和测试实现共 4 处。值得注意的是，此修改仅应用于 flink/v2.0 模块，Flink 1.19/1.20 的对应重命名在后续提交（2253）中单独回移植。
