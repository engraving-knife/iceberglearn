# 提交 0419：Flink: Implement enumerator metrics for pending splits, pending records, and split discovery (#9524)

## 提交信息

- **序号**：0419
- **哈希**：e5dc5ec9675950ae763b1c5813c2e9e8daa2769a
- **短哈希**：e5dc5ec96
- **日期**：Tue Jan 30 00:22:37 2024 -0800
- **作者**：Mason Chen <mas.chen@berkeley.edu>
- **提交说明**：Flink: Implement enumerator metrics for pending splits, pending records, and split discovery (#9524)
- **PR/Issue**：#9524

## 总体目的

Flink 的 Source V2 API 把"发现 split"（SplitEnumerator）和"读取 split"（SourceReader）分成两个角色，enumerator 负责扫描 Iceberg 表、把数据划分成 split 并下发给 reader。在流式场景下，enumerator 是整个作业"上游进度"的咽喉：它什么时候发现新快照、有多少 split 堆积在 assigner 里没被消费、积压了多少条记录——这些信息直接反映了 source 的 lag、背压和健康度。然而在本提交之前，Iceberg 的 Flink Source 几乎没有暴露任何 enumerator 层的可观测指标，源码里还留着一条 TODO 注释：`TODO: publish enumerator monitor metrics like number of pending metrics after FLINK-21000 is resolved`。也就是说，原本在等 Flink 框架侧的 FLINK-21000 解决，但这条等待一直没有兑现，导致生产环境里用户只能看到 reader 侧的吞吐/记录数，看不到 enumerator 侧的"积压/发现延迟"，排障流式作业卡顿、lag 增长时缺乏关键信号。

本提交的目的就是补齐这块可观测性短板，向 Flink MetricGroup 注册三个核心 enumerator 指标：(1) `unassignedSplits`——已发现但尚未被 reader 取走的 split 数；(2) `pendingRecords`——这些积压 split 中预估的记录总数，作为 source lag 的度量；(3) `elapsedSecondsSinceLastSplitDiscovery`——距离上次成功发现 split 过去了多少秒，用来发现"上游停写"或"发现频率过高但拿不到数据"等异常。这三个指标组合起来，让运维人员能从"积压量"和"发现节奏"两个维度监视流式 source 的健康状态，是流式 Iceberg source 走向生产可观测的重要一步。

另外，提交顺带把原本嵌在 `IcebergFilesCommitterMetrics` 内部的私有 `ElapsedTimeGauge` 提升为 `org.apache.iceberg.flink.util.ElapsedTimeGauge` 公共工具类，避免重复实现、方便 enumerator 复用——这是一次合理的代码去重和复用重构。

## 如何达成设计目的

实现路径分四块：(1) 在 `SplitAssigner` 接口新增 `pendingRecords()` 方法，`DefaultSplitAssigner` 通过把所有 pending split 的 `estimatedRowsCount` 求和实现；(2) 在 `AbstractIcebergEnumerator` 构造时，利用 Flink 的 `metricGroup().setUnassignedSplitsGauge(...)` 和 `metricGroup().gauge("pendingRecords", ...)` 注册前两个 gauge（前者是 Flink 框架为 source enumerator 提供的标准 gauge 名，后者是自定义 gauge）；(3) 在 `ContinuousIcebergEnumerator` 中持有一个 `ElapsedTimeGauge` 实例，注册为 `elapsedSecondsSinceLastSplitDiscovery` gauge，并在每次成功发现 split 后调用 `refreshLastRecordedTime()` 重置基准时间——这样 gauge 的值就是"自上次发现以来的秒数"；(4) 把 `ElapsedTimeGauge` 抽取到 `util` 包成为公共类，sink 端改为引用。测试侧通过 `InMemoryReporter` 接入 MiniCluster，断言三个 metric group 和指标名确实存在。

## 修改详情

### flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergFilesCommitterMetrics.java

**修改目的**：把原本内嵌在此类的私有 `ElapsedTimeGauge` 内部类移除，改为引用新抽取的公共工具类，消除重复代码。

**工作逻辑**：删除 `import org.apache.flink.metrics.Gauge`，新增 `import org.apache.iceberg.flink.util.ElapsedTimeGauge`；把类底部那段 `private static class ElapsedTimeGauge implements Gauge<Long>` 的 27 行实现整段删掉。原本 sink 端 committer 用的 `ElapsedTimeGauge` 现在全部由新的 `util.ElapsedTimeGauge` 提供，行为不变（同样是基于 `System.nanoTime()` 计算距上次 `refreshLastRecordedTime()` 的耗时，按指定 `TimeUnit` 输出）。

### flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/source/assigner/SplitAssigner.java

**修改目的**：在 `SplitAssigner` 接口上新增 `pendingRecords()` 方法契约，让所有 split assigner 实现都能暴露"积压记录数"这一 lag 信号。

**工作逻辑**：在已有的 `pendingSplitCount()` 方法之后，新增接口方法：
```java
/**
 * Return the number of pending records, which can act as a measure of the source lag. This value
 * could be an estimation if the exact number of records cannot be accurately computed.
 */
long pendingRecords();
```
Javadoc 明确说明这个值可作为 source lag 度量，且允许是估值（因为底层 split 的记录数本身可能是估算的）。

### flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/source/assigner/DefaultSplitAssigner.java

**修改目的**：为默认的 split assigner 实现 `pendingRecords()`，把所有待分配 split 的预估行数累加起来。

**工作逻辑**：在 `pendingSplitCount()` 之后新增：
```java
@Override
public long pendingRecords() {
  return pendingSplits.stream()
      .map(split -> split.task().estimatedRowsCount())
      .reduce(0L, Long::sum);
}
```
遍历当前 `pendingSplits` 集合，对每个 split 调用 `split.task().estimatedRowsCount()`（这是 Iceberg `ScanTask` 上已有的估值接口，通常基于 manifest 中的文件统计），用 `reduce(0L, Long::sum)` 求和。注意这里没有加锁——和 `pendingSplitCount()` 一致，依赖 `pendingSplits` 的并发安全结构。

### flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/source/enumerator/AbstractIcebergEnumerator.java

**修改目的**：在所有 enumerator（批/流共用基类）构造时注册"未分配 split 数"和"积压记录数"两个 gauge，把前面定义的 `pendingSplitCount`/`pendingRecords` 暴露到 Flink 指标系统。

**工作逻辑**：
- 删除类顶部的 TODO 注释（`TODO: publish enumerator monitor metrics like number of pending metrics after FLINK-21000 is resolved`），表示这个长期搁置的待办项终于落地。
- 在构造函数中新增两行注册：
  ```java
  this.enumeratorContext
      .metricGroup()
      // This number may not capture the entire backlog due to split discovery throttling to avoid
      // excessive memory footprint. Some pending splits may not have been discovered yet.
      .setUnassignedSplitsGauge(() -> Long.valueOf(assigner.pendingSplitCount()));
  this.enumeratorContext.metricGroup().gauge("pendingRecords", assigner::pendingRecords);
  ```
  `setUnassignedSplitsGauge` 是 Flink `SplitEnumeratorContext` 提供的标准方法，注册的值会以框架约定的标准指标名暴露（即 `unassignedSplits`）；注释明确指出由于 split discovery 有意做了节流（避免一次性发现过多 split 撑爆内存），这个值可能低估真实 backlog。`gauge("pendingRecords", assigner::pendingRecords)` 用方法引用把 assigner 的 `pendingRecords()` 暴露为自定义 gauge。

### flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/source/enumerator/ContinuousIcebergEnumerator.java

**修改目的**：为流式 enumerator 注册"距上次成功 split 发现的秒数"gauge，并在每次发现到 split 时重置基准时间。

**工作逻辑**：
- 新增字段 `private final ElapsedTimeGauge elapsedSecondsSinceLastSplitDiscovery;`，在构造函数中创建 `new ElapsedTimeGauge(TimeUnit.SECONDS)` 并通过 `enumeratorContext.metricGroup().gauge("elapsedSecondsSinceLastSplitDiscovery", elapsedSecondsSinceLastSplitDiscovery)` 注册。gauge 构造时调用 `refreshLastRecordedTime()` 把基准时间设为当前，所以初始值为 0。
- 在 `discoverSplits`（或类似发现流程）的成功分支里，原本只是处理 `EnumerationResult`，现在在 `else` 分支（即有新 split 被发现的分支，对应 `result.fromPosition()` 不为空的情况）调用 `elapsedSecondsSinceLastSplitDiscovery.refreshLastRecordedTime();` 重置基准。这样 gauge 的语义就是"距离上次成功发现 split 经过的秒数"——如果上游长时间没有新快照、或者发现频率远高于写入频率导致连续空轮询，这个值就会持续增长，是发现"上游停滞/轮询空转"的灵敏信号。注释里也说明了 enumeration 可能 yield no splits 的几种原因（上游暂停/延迟写、轮询频率高于写入频率），这正是这个 gauge 想要刻画的现象。

### flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/util/ElapsedTimeGauge.java

**修改目的**：把原来嵌在 sink 端的私有 gauge 实现抽取为 `util` 包下的公共 `@Internal` 类，供 sink 和 source 共同复用。

**工作逻辑**：新增 47 行的公共类，实现 `org.apache.flink.metrics.Gauge<Long>`。核心字段 `reportUnit`（输出单位）和 `volatile long lastRecordedTimeNano`（基准纳秒时间戳）。构造函数接收 `TimeUnit` 并立即调用 `refreshLastRecordedTime()` 初始化基准。`refreshLastRecordedTime()` 用 `System.nanoTime()` 更新基准。`getValue()` 返回 `reportUnit.convert(System.nanoTime() - lastRecordedTimeNano, TimeUnit.NANOSECONDS)`，即按指定单位（如 SECONDS）输出距基准时间的 elapsed 值。用 `volatile` 保证可见性，因为 gauge 的 `getValue()` 会被 Flink metrics 线程周期性调用，而 `refreshLastRecordedTime()` 由 enumerator 主流程调用。`@Internal` 注解表明这是 Iceberg Flink 集成内部 API，不对外稳定。

### flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/MiniClusterResource.java

**修改目的**：提供一个能挂载 `InMemoryReporter` 的 MiniCluster 工厂方法，让测试可以采集并断言 metric。

**工作逻辑**：新增重载方法 `createWithClassloaderCheckDisabled(InMemoryReporter inMemoryReporter)`，在原配置基础上调用 `inMemoryReporter.addToConfiguration(configuration)` 把内存 reporter 注入到 MiniCluster 配置中，再创建 `MiniClusterWithClientResource`。这样测试中注册的 metric 会被 `InMemoryReporter` 收集，可被 `findGroup`/`getMetricsByGroup` 查询到。

### flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceContinuous.java

**修改目的**：验证三个 enumerator metric 在流式作业运行后确实被注册并可被发现。

**工作逻辑**：
- 在测试类上新增静态字段 `public static final InMemoryReporter METRIC_REPORTER = InMemoryReporter.create();`，并把 `MINI_CLUSTER_RESOURCE` 改为 `MiniClusterResource.createWithClassloaderCheckDisabled(METRIC_REPORTER)`，让 MiniCluster 启动时挂上内存 reporter。
- 新增两个私有静态断言方法：
  - `assertThatIcebergEnumeratorMetricsExist()`：依次断言三个指标存在——`coordinator.enumerator.elapsedSecondsSinceLastSplitDiscovery`、`coordinator.enumerator.unassignedSplits`、`coordinator.enumerator.pendingRecords`。
  - `assertThatIcebergSourceMetricExists(String metricGroupPattern, String metricName)`：用 `METRIC_REPORTER.findGroup(metricGroupPattern)` 找到对应的 `MetricGroup`，断言其存在；然后从 `getMetricsByGroup` 拿到该组下所有 metric 名，通过 `getMetricIdentifier(name)` 转换为完整名，断言其中"恰有一条"（`satisfiesOnlyOnce`）包含目标 metric 名的子序列。这样既验证 group 存在，又验证 metric 名注册正确。
- 在 6 个已有的流式测试方法（如 `testTableScanThenIncremental`、`testIncremental` 等）的 try-with-resources 块末尾各加一行 `assertThatIcebergEnumeratorMetricsExist();`，复用已有测试场景顺便验证 metric 注册，无需为 metric 单独构造复杂场景。

## 小结

这是一个聚焦于可观测性的提交，补齐了 Iceberg Flink Source 长期缺失的 enumerator 侧指标：未分配 split 数、积压记录数、距上次 split 发现的秒数。三个指标分别从"积压量"和"发现节奏"两个维度刻画流式 source 健康，对生产环境排障 lag 增长、上游停滞、轮询空转等问题至关重要。实现上充分利用了 Flink Source V2 的 `metricGroup` API，把已有的 `pendingSplitCount`/新增的 `pendingRecords`/新增的 elapsed-time gauge 桥接出去，改动小而精准。顺带把 sink 端私有的 `ElapsedTimeGauge` 提取为公共 `util` 类，消除了重复。测试通过 `InMemoryReporter` + MiniCluster 验证 metric 注册，复用已有测试场景降低测试成本。提交也清掉了源码里等待 FLINK-21000 的 TODO，标志着 Iceberg Flink 集成在可观测性上摆脱了对框架待办的依赖。
