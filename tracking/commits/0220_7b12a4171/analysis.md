# 提交 0220：Flink: backport PR #9216 for disabling classloader check (#9226)

## 提交信息

- **序号**：0220 / 4088
- **哈希**：7b12a4171e9e6b7829e7a66b447fc15cada151fc
- **短哈希**：7b12a4171
- **日期**：2023-12-05
- **作者**：Steven Zhen Wu
- **提交说明**：Flink: backport PR #9216 for disabling classloader check (#9226)
- **PR/Issue**：#9226（backport 自 #9216）

## 总体目的

`TestIcebergSourceWithWatermarkExtractor` 是 Iceberg-Flink 的一个端到端集成测试：它启动一个 Flink `MiniClusterWithClientResource`，用 `IcebergSource` + watermark extractor 读取 Iceberg 数据，跑 window 聚合并通过 `InMemoryReporter` 校验 watermark 指标。该测试在 Flink 较新版本中偶发性失败，根因是 Flink 的 `CHECK_LEAKED_CLASSLOADER`（即作业结束后检测 user classloader 是否被泄漏的检查）会误报：Avro 序列化器在内部缓存了类（典型现象是 `AvroSerializer`/`SpecificRecord` 把类对象缓存在静态或实例字段里），导致作业结束后 user classloader 仍被持有，触发 classloader leak 检测，进而让测试因 Flink 抛出的泄漏异常而 flaky 失败。

PR #9216 已经先在 `flink/v1.17` 修了这个问题（commit `cbd33a6f6`，2023-12-05），方式是在 MiniCluster 的 `Configuration` 里把 `CoreOptions.CHECK_LEAKED_CLASSLOADER` 设为 `false`，并加注释说明"Avro 可能在 serializer 中缓存类"。本提交 #9226 把同一修复 backport 到 1.4.x 分支仍在维护的 `flink/v1.15` 与 `flink/v1.16` 两个版本目录，diff 内容与原 PR 完全一致。属于稳定性/测试基础设施修复，不影响产品代码行为。

## 如何达成设计目的

思路很简单：在测试构造 MiniCluster 时，往 `Configuration` 里追加一项 `CoreOptions.CHECK_LEAKED_CLASSLOADER = false`，关掉 Flink 的 classloader 泄漏检测。该选项默认为 true，是 Flink 1.13+ 引入的用于发现 user code classloader 泄漏的诊断特性；在 Avro 这类有合法缓存需求的场景下会误报，因此测试场景下显式关闭。两份测试文件（v1.15/v1.16）改动完全镜像 v1.17 的原始修复。

## 修改详情

### `flink/v1.15/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceWithWatermarkExtractor.java`（v1.16 同样改动）

**修改目的**：禁用 MiniCluster 的 classloader 泄漏检测，消除 Avro 缓存类导致的 flaky 失败。

**工作逻辑**：
- 新增 import `org.apache.flink.configuration.CoreOptions`。
- 在 `MiniClusterWithClientResource` 的 `@Rule` 配置块里，原本是 `.setConfiguration(reporter.addToConfiguration(new Configuration()))`，改为：
  ```java
  .setConfiguration(
      reporter.addToConfiguration(
          // disable classloader check as Avro may cache class in the serializers.
          new Configuration().set(CoreOptions.CHECK_LEAKED_CLASSLOADER, false)))
  ```
  即在 reporter 配置之外，再叠加一项 `CHECK_LEAKED_CLASSLOADER=false`。注释点明原因——Avro 可能在 serializer 中缓存类。

其余测试逻辑（watermark extractor、window 聚合、指标校验）不变。

## 小结

本提交把 PR #9216 在 v1.17 上修复 `TestIcebergSourceWithWatermarkExtractor` flaky 失败的方案 backport 到 v1.15/v1.16，通过禁用 Flink classloader 泄漏检测来规避 Avro 序列化器缓存类导致的误报，是测试基础设施层面的稳定性修复。
