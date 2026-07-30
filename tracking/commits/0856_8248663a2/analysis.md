# 提交 0856：Fix code depending on JVM default charset (#10529)

## 提交信息
- **序号**：0856 / 4088
- **哈希**：8248663a2a1ffddd2664ea37b45882455466f71c
- **短哈希**：8248663a2
- **日期**：2024-06-19
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Fix code depending on JVM default charset (#10529)
- **PR/Issue**：#10529

## 总体目的

该提交旨在消除 Iceberg 代码库中依赖 JVM 默认字符集（JVM default charset）的代码。JVM 默认字符集由系统环境（如 `file.encoding` 系统属性、操作系统语言设置、容器 locale）决定，在不同环境下可能是 UTF-8、ISO-8859-1、US-ASCII 等，从而让代码行为变得不可预测。

这次修改针对 Flink 模块下的 `MapRangePartitionerBenchmark` JMH 基准测试类，该类中存在一个将 `byte[]` 转换为 `String` 的语句 `new String(buffer)`，其行为依赖 JVM 默认字符集。这是 Java 17/21 之后越发敏感的问题——尤其是 JDK 18 起 `file.encoding` 默认值从 `ISO-8859-1` 改为 `UTF-8`，且未来可能进一步调整；同时项目正准备将 Flink/Hive 等子模块的 CI 在 Java 17 下进行测试（参见本批次的 0857、0858 提交），需要避免默认字符集的差异在多 JDK 下暴露潜在缺陷。

修改将 `new String(buffer)` 显式指定为 `new String(buffer, StandardCharsets.US_ASCII)`，从而固定使用 ASCII 字符集解析字节缓冲，使基准测试的字符串生成结果不再受 JVM 默认字符集变化的影响，行为在不同 JDK 版本与不同 locale 环境下保持一致。

## 如何达成设计目的

修改逻辑非常简单直接：在两个 Flink 版本目录（v1.17、v1.18）下的 `MapRangePartitionerBenchmark.java` 中：
1. 新增 `import java.nio.charset.StandardCharsets;`
2. 将生成测试键的 `new String(buffer)` 调用改为 `new String(buffer, StandardCharsets.US_ASCII)`，并加注释 `// CHARS is all ASCII` 说明此处的字符集选择依据。

这是修复 JVM 默认字符集依赖的标准做法：始终显式传入字符集，且根据数据语义选择最贴切的字符集。由于 `CHARS` 字面量只含 ASCII 字符，`buffer` 中填充的也是从 `CHARS` 取出的 ASCII 字符对应的字节值，使用 `US_ASCII` 解码最契合语义，也最节省算力（ASCII 是单字节字符集，转换行为可预测且无歧义）。

## 修改详情

### `flink/v1.17/flink/src/jmh/java/org/apache/iceberg/flink/sink/shuffle/MapRangePartitionerBenchmark.java`
**修改目的**：消除依赖 JVM 默认字符集的 `new String(byte[])` 调用。
**工作逻辑**：新增 `import java.nio.charset.StandardCharsets;`，将 `prefix + new String(buffer)` 改为 `prefix + new String(buffer, StandardCharsets.US_ASCII)`，并加注释说明 `CHARS` 仅含 ASCII 字符，从而将字符串构造的字符集从「依赖环境」固定为「显式 US-ASCII」。

### `flink/v1.18/flink/src/jmh/java/org/apache/iceberg/flink/sink/shuffle/MapRangePartitionerBenchmark.java`
**修改目的**：与 v1.17 相同，消除依赖 JVM 默认字符集的 `new String(byte[])` 调用。
**工作逻辑**：与 v1.17 中的修改完全一致——同样的 import 新增、同样的方法调用替换与同样的注释。

## 小结
- **成效**：使 `MapRangePartitionerBenchmark` 在生成测试字符串时不再依赖 JVM 默认字符集，行为在不同 JDK 版本与不同 locale 环境下完全一致；为后续在 Java 17 下运行 Flink/Hive 测试（0857、0858）扫清了潜在隐患。
- **影响范围**：仅影响 Flink v1.17 与 v1.18 子模块下的 JMH 基准测试代码，不影响生产路径与运行时行为，对用户无感知。
- **回迁注意事项**：1.4.x 分支若同样存在 `flink/v1.17`、`flink/v1.18` 下的 `MapRangePartitionerBenchmark.java`，可直接 cherry-pick；若 1.4.x 分支 Flink 版本目录结构不同，应按相同模式对对应版本的 benchmark 文件做等价修改；同时建议全局排查 1.4.x 分支是否还有其他 `new String(byte[])`、`String.getBytes()`、`InputStreamReader`/`OutputStreamWriter` 未显式指定字符集的调用点，一并修复以彻底消除默认字符集依赖。
