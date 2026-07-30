# 提交 3747：Flink: Allow setting slot sharing group for fine-grained resource management in DynamicSink (#16065)

## 提交信息

- **序号**：3747 / 4088
- **哈希**：6d8ebbb1033cbda1360b0ab5d21e5706b1dcadce
- **短哈希**：6d8ebbb10
- **日期**：2026-05-19 15:02:05 +0200
- **作者**：Han You
- **提交说明**：Flink: Allow setting slot sharing group for fine-grained resource management in DynamicSink (#16065)
- **PR/Issue**：#16065

## 总体目的

本提交为 Iceberg 的 Flink DynamicIcebergSink 新增了设置 slot sharing group（槽位共享组）的能力，以支持 Flink 的细粒度资源管理（fine-grained resource management）。

Flink 的 slot sharing group 允许将多个算子（operator）部署到同一个 slot 中，并为该 slot 指定特定的资源规格（CPU 核数、堆内存等）。在生产环境中，不同算子的资源需求差异很大：例如 writer 算子可能需要更多内存来缓冲数据，而 generator 算子可能 CPU 密集。原先 DynamicIcebergSink 内部的算子（generator、forward-writer、shuffle sink 的 writer+committer）无法单独指定 slot sharing group，只能继承上游算子的组，导致无法针对不同算子进行精细化的资源分配。

本提交允许用户分别为 generator（及其链式连接的 forward-writer）和 shuffle sink（writer + committer）指定 slot sharing group 名称。用户需先通过 `env.registerSlotSharingGroup(...)` 在 StreamExecutionEnvironment 上注册带资源规格的组，然后在 sink builder 上引用组名，即可实现细粒度资源管理。

## 如何达成设计目的

通过四个层面的改动实现：
1. **新增配置选项**：在 `FlinkDynamicSinkOptions` 中新增两个 `ConfigOption<String>`：`GENERATOR_SLOT_SHARING_GROUP` 和 `SHUFFLE_SINK_SLOT_SHARING_GROUP`，无默认值，支持通过 builder 或 Flink 配置设置。
2. **配置解析**：在 `FlinkDynamicSinkConf` 中新增两个方法解析上述配置，支持从 writeOptions 或 Flink 配置读取，可选返回。
3. **Builder API**：在 `DynamicIcebergSink.Builder` 中新增 `generatorSlotSharingGroup(String)` 和 `shuffleSinkSlotSharingGroup(String)` 两个方法，将值写入 writeOptions。
4. **拓扑应用**：在 `DynamicIcebergSink` 的拓扑构建逻辑中，读取配置并在对应算子上调用 `.slotSharingGroup(ssg)`，分别为 generator/forward-writer 和 shuffle sink 应用组。
5. **文档与测试**：在 `flink-writes.md` 中补充两个新 builder 方法的说明，并新增 `testSlotSharingGroup` 测试验证组被正确应用。

## 修改详情

### `docs/docs/flink-writes.md` (+2/-0 lines)

**修改目的**：在 Dynamic Iceberg Flink Sink 的 builder 配置表中补充两个新方法。

**工作逻辑**：
新增两行配置说明：
- `shuffleSinkSlotSharingGroup(String ssg)`：shuffle sink 的 slot sharing group 名称，需通过 `env.registerSlotSharingGroup(...)` 注册。
- `generatorSlotSharingGroup(String ssg)`：generator（及链式 forward sink）的 slot sharing group 名称，同样需先注册。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/FlinkDynamicSinkOptions.java` (+20/-0 lines)

**修改目的**：声明两个 slot sharing group 配置选项。

**工作逻辑**：
新增两个 `ConfigOption<String>`：
```java
public static final ConfigOption<String> GENERATOR_SLOT_SHARING_GROUP =
    ConfigOptions.key("dynamic-sink.generator-slot-sharing-group")
        .stringType()
        .noDefaultValue()
        .withDescription(
            "Name of the slot sharing group for the generator and the forward-writer chained to it. "
                + "Register the slot sharing group with its resource spec on the StreamExecutionEnvironment "
                + "via env.registerSlotSharingGroup(...). If unset, Flink inherits the slot sharing group "
                + "from the upstream operator.");

public static final ConfigOption<String> SHUFFLE_SINK_SLOT_SHARING_GROUP =
    ConfigOptions.key("dynamic-sink.shuffle-sink-slot-sharing-group")
        .stringType()
        .noDefaultValue()
        .withDescription(
            "Name of the slot sharing group for the shuffling sink (writer plus committer). "
                + "Register the slot sharing group with its resource spec on the StreamExecutionEnvironment "
                + "via env.registerSlotSharingGroup(...). If unset, Flink inherits the slot sharing group "
                + "from the upstream operator.");
```
均无默认值，未设置时 Flink 继承上游算子的组。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/FlinkDynamicSinkConf.java` (+16/-0 lines)

**修改目的**：新增解析 slot sharing group 配置的方法。

**工作逻辑**：
新增两个方法，通过 `confParser` 从 writeOptions 或 Flink 配置中可选解析字符串：
```java
String generatorSlotSharingGroup() {
  return confParser
      .stringConf()
      .option(FlinkDynamicSinkOptions.GENERATOR_SLOT_SHARING_GROUP.key())
      .flinkConfig(FlinkDynamicSinkOptions.GENERATOR_SLOT_SHARING_GROUP)
      .parseOptional();
}

String shuffleSinkSlotSharingGroup() {
  return confParser
      .stringConf()
      .option(FlinkDynamicSinkOptions.SHUFFLE_SINK_SLOT_SHARING_GROUP.key())
      .flinkConfig(FlinkDynamicSinkOptions.SHUFFLE_SINK_SLOT_SHARING_GROUP)
      .parseOptional();
}
```
使用 `parseOptional()` 使未设置时返回 null 而非抛异常。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicIcebergSink.java` (+36/-1 lines)

**修改目的**：在 Builder 中新增 API 并在拓扑构建时应用 slot sharing group。

**工作逻辑**：
- Builder 新增两个方法：
```java
public Builder<T> generatorSlotSharingGroup(String ssg) {
  writeOptions.put(FlinkDynamicSinkOptions.GENERATOR_SLOT_SHARING_GROUP.key(), ssg);
  return this;
}

public Builder<T> shuffleSinkSlotSharingGroup(String ssg) {
  writeOptions.put(FlinkDynamicSinkOptions.SHUFFLE_SINK_SLOT_SHARING_GROUP.key(), ssg);
  return this;
}
```
- 在 `createForwardWriteStream` 中，将 `forwardWriteResults` 的类型从 `DataStream` 改为 `SingleOutputStreamOperator`（以便调用 `slotSharingGroup`），并在配置非 null 时应用 generator SSG：
```java
String generatorSsg = flinkDynamicSinkConf.generatorSlotSharingGroup();
if (generatorSsg != null) {
  forwardWriteResults.slotSharingGroup(generatorSsg);
}
```
- 在 `append()` 中对 generator 算子（`converted`）应用 generator SSG。
- 在 `append()` 中对 shuffle sink 算子（`result`）应用 shuffle sink SSG：
```java
String shuffleSinkSsg = flinkDynamicSinkConf.shuffleSinkSlotSharingGroup();
if (shuffleSinkSsg != null) {
  result.slotSharingGroup(shuffleSinkSsg);
}
```

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicIcebergSink.java` (+63/-0 lines)

**修改目的**：验证 slot sharing group 被正确应用到对应算子。

**工作逻辑**：
新增 `testSlotSharingGroup` 测试：
- 注册两个 slot sharing group：`shuffle-sink-ssg`（CPU 123，堆内存 123 字节）和 `generator-ssg`（CPU 456，堆内存 456 字节）。
- 构建 DynamicIcebergSink，分别设置 `shuffleSinkSlotSharingGroup` 和 `generatorSlotSharingGroup`。
- 从 `env.getStreamGraph().getJobGraph().getVertices()` 获取所有 JobVertex。
- 验证名称包含 "Sink: Writer" 的顶点的 slot sharing group 资源 profile 的 task heap memory 等于 shuffleSinkMemorySize（123）。
- 验证名称包含 "generator" 的顶点的 slot sharing group 资源 profile 的 task heap memory 等于 generatorMemorySize（456）。
- 通过资源 profile 的内存值间接验证组被正确应用。

## 总结

本提交为 Flink DynamicIcebergSink 新增了 slot sharing group 配置能力，允许用户分别为 generator（及链式 forward-writer）和 shuffle sink（writer + committer）指定 slot sharing group 名称，以支持 Flink 细粒度资源管理。用户通过 `env.registerSlotSharingGroup(...)` 注册带资源规格的组，再通过 builder 的 `generatorSlotSharingGroup` 和 `shuffleSinkSlotSharingGroup` 方法引用。这使生产环境中不同算子可以按需分配 CPU 和内存资源，提升资源利用率和作业性能。配套测试验证了组被正确应用到对应算子。这是面向生产运维场景的实用性增强。
