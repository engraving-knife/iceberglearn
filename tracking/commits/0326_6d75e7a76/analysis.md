# 提交 0326：Flink: Disable classloader check in TestIcebergSourceWithWatermarkExtractor to fix flakiness (#9408)

## 提交信息

- **序号**：0326 / 4088
- **哈希**：6d75e7a76ad3aeac702e0e5f62267da141907606
- **短哈希**：6d75e7a76
- **日期**：2024-01-04 15:27:26 +0100
- **作者**：Manu Zhang
- **提交说明**：Flink: Disable classloader check in TestIcebergSourceWithWatermarkExtractor to fix flakiness (#9408)
- **PR/Issue**：#9408

## 总体目的

这个提交要解决的是 `TestIcebergSourceWithWatermarkExtractor` 这个 Flink 集成测试的 flakiness（不稳定、偶发失败）问题。该测试位于 Flink connector 的 testframe 测试套件中，用于验证 IcebergSource 配合 WatermarkExtractor 的行为，它依赖 Flink 的 MiniClusterResource 启动一个嵌入式集群运行测试。测试在 CI 上偶发失败，影响项目的构建稳定性，也增加维护成本。

Flink 的类加载器检查（classloader check）是 Flink runtime 在作业执行完毕后做的一项一致性校验：它会检查用户代码所使用的类加载器是否被正确释放、是否存在类加载器泄漏。在测试框架环境下，尤其是使用 connector testframe 拉起 MiniCluster 并运行 IcebergSource 这类复杂 connector 时，由于 Iceberg 大量使用 reloc shaded 类、Flink 的 child-first classloading 机制以及测试 reporter 等组件，类加载器的生命周期很容易出现非确定性冲突，从而触发该检查失败、导致测试偶发红。这种失败与被测功能（watermark extractor）本身无关，属于测试基础设施层面的噪声。

修复方式是直接复用 `MiniClusterResource` 中已经定义好的 `DISABLE_CLASSLOADER_CHECK_CONFIG` 常量——它是一组 `Configuration`，内置了关闭 classloader check 的开关——把原先传入的空 `new Configuration()` 替换为该常量。这种做法在 Flink connector 测试生态中已是惯用模式，Iceberg 项目内部其他测试也大多使用同一常量来规避该类问题。本提交把三个 Flink 版本（v1.16、v1.17、v1.18）下三个同名测试文件统一改成同一写法，保证各版本行为一致。

## 如何达成设计目的

整体思路很简单：把构建 MiniCluster 时传给 `reporter.addToConfiguration(...)` 的配置对象从空的 `new Configuration()` 换成预置了"关闭类加载器检查"开关的 `DISABLE_CLASSLOADER_CHECK_CONFIG`。该常量来自 `org.apache.iceberg.flink.MiniClusterResource`，由 Flink connector 测试基础设施统一维护。同时移除对 `org.apache.flink.configuration.Configuration` 的 import（因为不再 `new Configuration()`），改为静态导入 `DISABLE_CLASSLOADER_CHECK_CONFIG` 常量。三个 Flink 版本目录下的测试文件改动完全一致，是同步移植。

## 修改详情

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceWithWatermarkExtractor.java`

**修改目的**：在 Flink 1.16 版本的 `TestIcebergSourceWithWatermarkExtractor` 测试中关闭 classloader check，消除偶发失败。

**工作逻辑**：
- import 调整：移除 `import org.apache.flink.configuration.Configuration;`，新增静态导入 `import static org.apache.iceberg.flink.MiniClusterResource.DISABLE_CLASSLOADER_CHECK_CONFIG;`，以便直接使用该常量。
- 在 `MiniClusterResource.create()` 的 builder 链中，把 `.setConfiguration(reporter.addToConfiguration(new Configuration()))` 改为 `.setConfiguration(reporter.addToConfiguration(DISABLE_CLASSLOADER_CHECK_CONFIG))`。`reporter.addToConfiguration(...)` 会把 metric reporter 配置叠加到传入的 Configuration 上，再交给 MiniCluster；改为传入 `DISABLE_CLASSLOADER_CHECK_CONFIG` 后，最终生效的 Configuration 既包含 reporter 设置，又包含关闭 classloader check 的开关，从而绕开偶发的类加载器检查失败。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceWithWatermarkExtractor.java`

**修改目的**：在 Flink 1.17 版本同步应用同样的修复。

**工作逻辑**：与 v1.16 完全一致——同样移除 `Configuration` 的 import、新增 `DISABLE_CLASSLOADER_CHECK_CONFIG` 的静态导入，并把 `.setConfiguration(...)` 的实参从 `new Configuration()` 替换为 `DISABLE_CLASSLOADER_CHECK_CONFIG`。三处改动逐行对应。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceWithWatermarkExtractor.java`

**修改目的**：在 Flink 1.18 版本同步应用同样的修复。

**工作逻辑**：与 v1.16、v1.17 完全一致。Iceberg 对 Flink 多版本维护采用平行目录复制策略，因此同一修复需要在每个版本目录下各做一次，以保持三个版本测试行为一致。

## 小结

这是一个小而精准的测试稳定性修复：通过复用项目内既有的 `DISABLE_CLASSLOADER_CHECK_CONFIG` 常量，关闭 MiniCluster 的类加载器检查，消除 `TestIcebergSourceWithWatermarkExtractor` 在三个 Flink 版本（1.16/1.17/1.18）下的偶发失败。改动量小、风险低，但能切实改善 CI 的稳定性，让维护者更专注于功能本身的回归。
