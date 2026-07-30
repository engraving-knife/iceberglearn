# 提交 0295：Flink: Empty implementation for pauseOrResumeSplits to prevent UnsupportedOperationException (#9308)

## 提交信息

- **序号**：0295 / 4088
- **哈希**：2eea697297a4c724a950a4bdc9dd29822ea90d8c
- **短哈希**：2eea69729
- **日期**：2023-12-21 08:32:33 +0100
- **作者**：pvary <peter.vary.apache@gmail.com>
- **提交说明**：Flink: Empty implementation for pauseOrResumeSplits to prevent UnsupportedOperationException (#9308)
- **PR/Issue**：#9308

## 总体目的

本提交要修复 Flink 1.18 中 Iceberg 源（`IcebergSource`）在启用 watermark 对齐（watermark alignment）时抛出 `UnsupportedOperationException` 的问题。要理解这个缺陷，需要先看清 Flink 1.18 的 `SplitReader` 接口变化与 Iceberg 读取器实现之间的契约错配。

Flink 在 1.18 版本为 `SplitReader` 接口引入了 `pauseOrResumeSplits(Collection splitsToPause, Collection splitsToResume)` 方法，用于支持 source 级别的 watermark 对齐：当某个 source 的 watermark 落后于对齐组中的其他 source 时，Flink 的 `SourceOperator` 会调用该方法请求读取器"暂停"消费部分 split，从而让落后的 source 赶上来。该接口方法在 Flink 中有一个默认实现，其行为是直接抛出 `UnsupportedOperationException`——这意味着任何不支持 split 级别暂停/恢复的读取器若不覆写该方法，在 watermark 对齐被触发时就会崩溃。

Iceberg 的 `IcebergSourceSplitReader`（Flink 1.18 模块）实现了 `SplitReader` 接口，但在本提交之前并未覆写 `pauseOrResumeSplits`。因此当用户为 Iceberg source 配置了 watermark 策略（例如使用 `TestIcebergSourceWithWatermarkExtractor` 中基于事件时间戳的 watermark 提取器）并触发 watermark 对齐时，Flink 框架调用 `pauseOrResumeSplits` 会命中接口的默认实现并抛出 `UnsupportedOperationException`，导致作业失败。这正是 PR 标题中 "to prevent UnsupportedOperationException" 所指的问题。

更深层的动机是：Iceberg 的 split 读取器本身就不适合做 split 级别的暂停/恢复。`IcebergSourceSplitReader` 采用**顺序读取**模型——一次只从队列中取出一个 split（`splits.poll()`），用 `currentReader` 迭代其记录，读完后才切换到下一个 split。在这种模型下，"暂停某个 split"没有实际意义，因为读取器并不会在多个 split 之间交错消费。真正需要的是一种"整体停顿"的语义，而 Iceberg 读取器通过 `ArrayPoolDataIteratorBatcher` 的固定大小对象池已经天然具备这种背压能力：当 `SourceOperator` 因 watermark 对齐而停止消费已取出的批次时，批次数组不会被回收到池中，池被耗尽后 `currentReader.next()` 会自然阻塞，从而实现整体停顿。因此正确的修复不是去实现一套 split 级别的暂停/恢复逻辑，而是提供一个空实现以跳过框架的默认报错，让既有的池背压机制接管流量控制。

## 如何达成设计目的

设计思路是给 `IcebergSourceSplitReader` 增加一个空的 `pauseOrResumeSplits` 覆写方法，并在 Javadoc 中阐明"为何留空"：读取器顺序消费 split，watermark 对齐期间 `SourceOperator` 会停止处理与回收已取出的批次，这会耗尽 `ArrayPoolDataIteratorBatcher` 的对象池，使 `currentReader.next()` 自然阻塞，因此无需在 split 级别做任何暂停/恢复动作，`pauseOrResumeSplits` 与既有的 `wakeUp` 一样保持空实现即可。同时在测试侧移除此前为绕过该问题而启用的 `PipelineOptions.ALLOW_UNALIGNED_SOURCE_SPLITS` 选项（该选项允许非对齐的 split 分配，是一种 workaround），并适度放宽 `monitorInterval` 以提升测试稳定性。另外顺手把 `splits` 队列的构造从 `new ArrayDeque<>()` 改为 Guava 的 `Queues.newArrayDeque()`，并补上 `fetch()` 方法的 Javadoc。

## 修改详情

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/source/reader/IcebergSourceSplitReader.java`

**修改目的**：为 `IcebergSourceSplitReader` 增加空的 `pauseOrResumeSplits` 覆写实现，防止 Flink 1.18 watermark 对齐触发 `UnsupportedOperationException`；同时做若干配套小调整。

**工作逻辑**：

1. **import 调整**

```java
-import java.util.ArrayDeque;
+import java.util.Collection;
 ...
+import org.apache.iceberg.relocated.com.google.common.collect.Queues;
```

`ArrayDeque` 的直接 import 被 `Collection` 取代（`pauseOrResumeSplits` 的参数类型需要 `Collection`），同时新增 Guava 的 `Queues` import。`ArrayDeque` 类型本身仍在用（`splits` 字段），但改由 `Queues.newArrayDeque()` 工厂方法创建，故不再需要直接 import 该类。

2. **队列构造方式改为 Guava 工厂方法**

```java
-    this.splits = new ArrayDeque<>();
+    this.splits = Queues.newArrayDeque();
```

将 `splits` 字段的初始化从 `new ArrayDeque<>()` 改为 `Queues.newArrayDeque()`。这是 Iceberg 项目一贯的风格偏好——使用 relocated Guava 工具方法以保持依赖隔离。该改动与主修复无直接因果，属于顺带的代码风格统一。

3. **新增 `fetch()` 方法的 Javadoc**

```java
+  /**
+   * The method reads a batch of records from the assigned splits. If all the records from the
+   * current split are returned then it will emit a {@link ArrayBatchRecords#finishedSplit(String)}
+   * batch to signal this event. In the next fetch loop the reader will continue with the next split
+   * (if any).
+   *
+   * @return The fetched records
+   * @throws IOException If there is an error during reading
+   */
   @Override
   public RecordsWithSplitIds<RecordAndPosition<T>> fetch() throws IOException {
```

为 `fetch()` 方法补充文档，说明其行为：从已分配的 split 中读取一批记录，当前 split 读完时发出 `ArrayBatchRecords.finishedSplit` 信号，下一次 fetch 继续下一个 split。这进一步明确了"顺序消费单 split"的语义，为 `pauseOrResumeSplits` 留空的设计决策提供文档支撑。

4. **核心修复：新增空的 `pauseOrResumeSplits` 覆写**

```java
+  @Override
+  public void pauseOrResumeSplits(
+      Collection<IcebergSourceSplit> splitsToPause, Collection<IcebergSourceSplit> splitsToResume) {
+    // IcebergSourceSplitReader only reads splits sequentially. When waiting for watermark alignment
+    // the SourceOperator will stop processing and recycling the fetched batches. This exhausts the
+    // {@link ArrayPoolDataIteratorBatcher#pool} and the `currentReader.next()` call will be
+    // blocked even without split-level watermark alignment. Based on this the
+    // `pauseOrResumeSplits` and the `wakeUp` are left empty.
+  }
```

这是本次提交的核心。逐层解析：

- **方法签名**：`pauseOrResumeSplits(Collection<IcebergSourceSplit> splitsToPause, Collection<IcebergSourceSplit> splitsToResume)` 与 Flink 1.18 `SplitReader` 接口定义一致，用 `@Override` 标注以覆写默认实现，从而避免默认实现抛出 `UnsupportedOperationException`。

- **方法体为空**：不执行任何暂停或恢复动作。注释详细解释了留空的理由，其工作逻辑链路如下：
  - `IcebergSourceSplitReader` 顺序读取 split——`fetch()` 中 `splits.poll()` 一次只取一个 split，`currentReader` 迭代完才切换下一个，不存在多 split 交错消费，因此"暂停特定 split"无意义。
  - 当 watermark 对齐触发时，`SourceOperator` 停止处理（不再调用 `fetch` 消费批次）并停止回收已取出的批次数组。
  - `ArrayPoolDataIteratorBatcher` 内部用一个固定大小的 `Pool<T[]>`（Flink 的 `org.apache.flink.connector.file.src.util.Pool`）回收批次数组。池大小等于 `handoverQueueSize`，预创建固定数量数组供读写复用：读取端 `pool.pollEntry()` 取空数组填充，消费端通过 `pool.recycler()` 归还。
  - 当 `SourceOperator` 停止回收后，所有数组都滞留在已发出但未被消费的批次中，池被耗尽。此时若读取端继续 `currentReader.next()`，内部 `pool.pollEntry()` 会因无可用数组而阻塞（其内部在等待可用 entry 时会 `wait`/`LockSupport.park`）。
  - 因此即便不在 split 级别做暂停，池的耗尽也会自然形成背压，使读取停顿——这就是注释所说的 "blocked even without split-level watermark alignment"。
  - 基于以上机制，`pauseOrResumeSplits` 无需做事，与同文件中已有的空 `wakeUp()` 方法保持一致策略。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceWithWatermarkExtractor.java`

**修改目的**：移除为绕过 `UnsupportedOperationException` 而启用的 `ALLOW_UNALIGNED_SOURCE_SPLITS` 选项，并放宽 `monitorInterval` 以提升测试稳定性，从而验证空实现修复后 watermark 对齐场景可正常工作。

**工作逻辑**：

1. **移除 `PipelineOptions.ALLOW_UNALIGNED_SOURCE_SPLITS` 选项与对应 import**

```java
-import org.apache.flink.configuration.PipelineOptions;
 ...
-              .setConfiguration(
-                  reporter.addToConfiguration(
-                      new Configuration().set(PipelineOptions.ALLOW_UNALIGNED_SOURCE_SPLITS, true)))
+              .setConfiguration(reporter.addToConfiguration(new Configuration()))
```

`PipelineOptions.ALLOW_UNALIGNED_SOURCE_SPLITS` 是 Flink 的一个选项，允许在 watermark 对齐未满足时将 split 以"非对齐"方式分配给读取器。此前测试启用它是一种 workaround：通过允许非对齐分配，绕开了会触发 `pauseOrResumeSplits` 的代码路径，从而避免 `UnsupportedOperationException`。本提交为读取器补上空实现后，watermark 对齐可正常工作，不再需要该 workaround，因此将其移除，使测试真正覆盖对齐场景。

2. **放宽 `monitorInterval`**

```java
-        .monitorInterval(Duration.ofMillis(2))
+        .monitorInterval(Duration.ofMillis(10))
```

将 Iceberg source 监控新文件的时间间隔从 2ms 放宽到 10ms。2ms 过于激进，容易在 CI 环境下引入抖动；放宽到 10ms 在仍能及时检测新文件的同时降低测试 flaky 的概率。这是与主修复配套的测试稳定性改进。

## 小结

本提交通过为 Flink 1.18 的 `IcebergSourceSplitReader` 增加一个空的 `pauseOrResumeSplits` 覆写方法，修复了启用 watermark 对齐时因命中 `SplitReader` 接口默认实现而抛出 `UnsupportedOperationException` 的问题。留空的依据是 Iceberg 读取器顺序消费 split、且 `ArrayPoolDataIteratorBatcher` 的固定大小对象池在水印对齐期间会因 `SourceOperator` 停止回收批次而自然耗尽并阻塞读取，从而提供天然的背压，无需 split 级别暂停。配套移除了测试中 `ALLOW_UNALIGNED_SOURCE_SPLITS` 的 workaround 并放宽 `monitorInterval`，使测试真正覆盖对齐路径且更稳定。改动聚焦于 v1.18 模块（v1.17 的 `SplitReader` 接口尚无此方法，故不受影响）。
