# 提交 3553：Flink: Set generator parallelism to match input in DynamicIcebergSink (#15849)

## 提交信息

- **序号**：3553 / 4088
- **哈希**：8f1f483985bfbef3c19472b9e2ec474593fee219
- **短哈希**：8f1f48398
- **日期**：2026-04-17 19:58:06 +0200
- **作者**：Sachin Ranjalkar
- **提交说明**：Flink: Set generator parallelism to match input in DynamicIcebergSink (#15849)
- **PR/Issue**：#15849

## 总体目的

`DynamicIcebergSink` 的 `append()` 方法在构建拓扑时，会创建一个 `DynamicRecordProcessor`（generator）算子来处理输入流。此前这个 generator 算子没有显式设置并行度，会使用 Flink 作业的默认并行度（`StreamExecutionEnvironment` 的全局并行度）。

问题在于：当输入流的并行度与全局默认并行度不一致时（例如输入 source 并行度为 8，但全局默认并行度为 4），generator 算子会以全局默认并行度（4）运行，导致输入流（8 并行度）到 generator（4 并行度）之间发生不必要的 rebalance/repartition，既浪费性能又可能不符合用户预期。

正确的行为是 generator 算子的并行度应与输入流保持一致，这样 generator 可以与输入通过 forward edge 连接（或 chaining），避免多余的 shuffle。本提交通过 `.setParallelism(input.getParallelism())` 显式将 generator 并行度设为输入流并行度。

## 如何达成设计目的

在 `DynamicIcebergSink.Builder.append()` 方法中，创建 generator 算子的 `process(...)` 调用链上新增 `.setParallelism(input.getParallelism())`，使 generator 继承输入流的并行度。改动跨 Flink v1.20、v2.0、v2.1 三个版本同步应用。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicIcebergSink.java` (+1/-0 lines)

**修改目的**：设置 generator 算子并行度与输入流一致。

**工作逻辑**：
```java
              .process(
                  new DynamicRecordProcessor<>(
                      generator,
                      catalogLoader,
                      immediateUpdate,
                      tableCreator,
                      caseSensitive,
                      dropUnusedColumns))
+              .setParallelism(input.getParallelism())
              .uid(prefixIfNotNull(uidPrefix, "-generator"))
              .name(operatorName("generator"))
              .returns(type);
```
`input.getParallelism()` 获取输入 DataStream 的并行度，显式应用到 generator 算子。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicIcebergSink.java` (+27/-0 lines)

**修改目的**：测试 generator 并行度与输入源一致。

**工作逻辑**：
```java
@Test
void testGeneratorDefaultParallelism() {
  StreamExecutionEnvironment streamEnv = StreamExecutionEnvironment.getExecutionEnvironment();
  streamEnv.setParallelism(4);

  DataStreamSource<DynamicIcebergDataImpl> source =
      streamEnv.fromData(Collections.emptySet(), TypeInformation.of(new TypeHint<>() {}));
  source.setParallelism(8);

  DynamicIcebergSink.forInput(source)
      .generator(new Generator())
      .catalogLoader(CATALOG_EXTENSION.catalogLoader())
      .uidPrefix("test")
      .append();

  int generatorParallelism =
      streamEnv.getStreamGraph().getStreamNodes().stream()
          .filter(node -> "test--generator".equals(node.getTransformationUID()))
          .findFirst()
          .map(StreamNode::getParallelism)
          .orElseThrow(() -> new AssertionError("Generator node not found"));

  assertThat(generatorParallelism).isEqualTo(source.getParallelism());
}
```
测试设置全局并行度 4，输入 source 并行度 8，构建 sink 后通过 StreamGraph 查找 generator 节点，断言其并行度为 8（= source 并行度），而非全局默认的 4。

### `flink/v2.0/...` 和 `flink/v1.20/...`（各 +1/-0 和 +27/-0 lines）

**修改目的**：Flink v2.0 和 v1.20 版本同步应用相同改动（主代码 1 行 + 测试 27 行）。

**工作逻辑**：与 v2.1 完全相同。

## 总结

本提交为 `DynamicIcebergSink` 的 generator 算子显式设置并行度为输入流并行度（`.setParallelism(input.getParallelism())`），避免当输入并行度与全局默认并行度不一致时产生不必要的 repartition/shuffle。改动跨 Flink v1.20/v2.0/v2.1 三个版本同步应用，并配有通过 StreamGraph 验证 generator 并行度的测试。属于性能优化，减少不必要的网络 shuffle。
