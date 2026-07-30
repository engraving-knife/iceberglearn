# 提交 0214：Flink: fix flaky test that might fail due to classloader check (#9216)

## 提交信息

- **序号**：0214 / 4088
- **哈希**：cbd33a6f626161bd1a4f0894a9dd4d96aed1a35b
- **短哈希**：cbd33a6f6
- **日期**：2023-12-05 13:08:48 +0100
- **作者**：Steven Zhen Wu
- **提交说明**：Flink: fix flaky test that might fail due to classloader check (#9216)
- **PR/Issue**：#9216

## 总体目的

本提交修复 Flink 引擎测试 `TestIcebergSourceWithWatermarkExtractor` 的一个偶发失败（flaky test）。该测试使用 Flink 的 MiniCluster 跑端到端流式读取，并启用了一个自定义的 metric reporter 来观测 watermark。在某些运行环境下，Flink 会在作业结束时检测"类加载器泄漏（leaked classloader）"——即作业使用的 classloader 是否在作业结束后仍被强引用，若是则报错使测试失败。Iceberg 的 Avro 序列化路径会把 class 缓存到 Flink 的 serializer 内部，导致 classloader 在作业结束后无法立即释放，从而触发该检查并使测试不稳定失败。本提交通过在测试的 MiniCluster 配置中关闭 `CoreOptions.CHECK_LEAKED_CLASSLOADER` 来消除这一偶发失败。

## 如何达成设计目的

仅修改一个测试文件 `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceWithWatermarkExtractor.java`，在构建 MiniCluster 配置时显式将 `CoreOptions.CHECK_LEAKED_CLASSLOADER` 设为 `false`，并加注释说明原因（Avro 可能在 serializer 中缓存 class）。其余测试逻辑不变。

## 修改详情

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceWithWatermarkExtractor.java`

**修改目的**：在 MiniCluster 配置中关闭 classloader 泄漏检查，避免 Avro serializer 的 class 缓存导致 flaky 失败。

**工作逻辑**：

1. **新增 import**：`import org.apache.flink.configuration.CoreOptions;`。

2. **修改 MiniClusterConfiguration 构建**：原代码为
   ```java
   .setConfiguration(reporter.addToConfiguration(new Configuration()))
   ```
   新代码为
   ```java
   .setConfiguration(
       reporter.addToConfiguration(
           // disable classloader check as Avro may cache class in the serializers.
           new Configuration().set(CoreOptions.CHECK_LEAKED_CLASSLOADER, false)))
   ```
   在原本空白的 `Configuration` 上设置 `CHECK_LEAKED_CLASSLOADER=false`，再交给 reporter 添加其配置，最后传入 MiniCluster。注释明确指出关闭原因是"Avro may cache class in the serializers"——这与 Iceberg 使用 Avro 读写 manifest/data file 的实际行为相符：Avro 的 `SpecificDatumReader`/`ReflectDatumReader` 等会缓存 schema 解析结果与 class 对象到 serializer，而 Flink 的 serializer 实例生命周期可能跨越作业 classloader 的卸载，导致 classloader 被引用而触发泄漏检查。

   该配置项（`CHECK_LEAKED_CLASSLOADER`，对应 `classloader.check-leaked-classloader`）是 Flink 1.x 引入的运维检查，默认开启，在作业结束时若检测到用户代码 classloader 仍被引用则记录或抛错。在测试场景下，由于 Avro 缓存是已知且不可避免的"良性泄漏"，关闭它是合适的处理方式，不影响被测功能的正确性。

## 小结

本提交通过在测试 MiniCluster 中关闭 Flink 的 classloader 泄漏检查，消除了 Avro serializer class 缓存引起的偶发测试失败，提升 CI 稳定性，不改变任何产品代码行为。
