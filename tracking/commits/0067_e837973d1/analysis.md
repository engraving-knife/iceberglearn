# 提交 0067：Flink 1.17: Use awaitility instead of Thread.sleep() (#8852)

## 提交信息

- **序号**：0067 / 4088
- **哈希**：e837973d194e3679fe6c52fc3939f1a5613791df
- **短哈希**：e837973d1
- **日期**：2023-10-18 08:07:08 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：Flink 1.17: Use awaitility instead of Thread.sleep() (#8852)
- **PR/Issue**：#8852

## 总体目的

本提交针对 Iceberg 的 Flink 1.17 集成测试做了一次集中性的可观测性/稳定性改造：将测试中分散使用的 `Thread.sleep()` 与手写轮询循环，统一替换为 [Awaitility](https://github.com/awaitility/awaitility) 这个专为异步断言设计的测试库。

在原有的测试代码里，等待一个异步条件（例如 Flink 作业进入 running 状态、连续流式源产出预期 split、sink 表里出现预期记录）通常采用"固定间隔 sleep + 计数循环"或"CountDownLatch.await(timeout) + 再 sleep 一段缓冲时间"的模式。这种模式有几个长期困扰 CI 的痛点：第一，sleep 时长是固定的，环境快时浪费时间、环境慢（CI 超载）时容易超时失败，造成 flaky test；第二，`Thread.sleep(1000L)` 这种"为了等下游稳定再断言"的写法本质上是经验性的盲等，无法根据条件是否满足来提前结束；第三，手写循环 + 末尾 `assertRecordsEqual` 的写法把"轮询"和"最终断言"耦合在一起，一旦中间状态没收敛就直接抛断言，错误信息不够清晰。

引入 Awaitility 后，`await().atMost(timeout).untilAsserted(...)` 会以可配置的轮询间隔反复执行断言，断言一旦通过立即返回、超时才失败，并且失败信息会附带最后一次断言的异常与轮询次数，大幅提升了测试在 CI 上的稳定性与可诊断性。这也是社区在 Flink/Spark 等模块持续推动的"消除 Thread.sleep"工程改进的一部分，对 Iceberg 演进的意义是降低 CI flake 率、提升贡献者体验。

## 如何达成设计目的

改动集中在 Flink 1.17 测试模块的 4 个文件：一是把 `SimpleDataUtil.assertTableRecords` 的"循环 + sleep"版本重写为 Awaitility 版本，作为后续测试调用的公共入口；二是把 `TestIcebergSourceContinuous.waitUntilJobIsRunning` 的忙等循环替换为 Awaitility；三是把 `TestIcebergSourceFailover` 里调用旧签名的地方改成新签名（用单一 `Duration` 超时取代"间隔 + 最大次数"两个参数），同时删除冗余的 `assertRecords` 包装方法；四是把 `TestStreamingMonitorFunction` 5 处重复的"latch.await + Thread.sleep(1000) + assertEquals(splits size)"三件套，提炼为一个统一的 `awaitExpectedSplits` 私有方法并用 Awaitility 实现。整体改动既替换了底层等待机制，又顺手收敛了重复代码。

## 修改详情

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/SimpleDataUtil.java`

**修改目的**：把公共断言工具方法从"轮询循环 + 末尾断言"改造为 Awaitility 版本，作为整个 Flink 测试模块等待表数据收敛的统一入口。

**工作逻辑**：原方法签名是 `assertTableRecords(Table, List<Record>, Duration checkInterval, int maxCheckCount)`，内部用一个 `for` 循环最多检查 `maxCheckCount` 次，每次失败就 `Thread.sleep(checkInterval.toMillis())`，循环结束后无论成功失败都用 `assertRecordsEqual` 做最终断言。这种写法的总等待时长约等于 `checkInterval * maxCheckCount`，且必须在循环外再断言一次。

新方法签名简化为 `assertTableRecords(Table table, List<Record> expected, Duration timeout)`，不再抛 `IOException`/`InterruptedException`（因为 Awaitility 内部用 lambda 包装，异常被统一处理）。方法体改为：

```java
Awaitility.await("expected list of records should be produced")
    .atMost(timeout)
    .untilAsserted(
        () -> {
          equalsRecords(expected, tableRecords(table), table.schema());
          assertRecordsEqual(expected, tableRecords(table), table.schema());
        });
```

这里 `untilAsserted` 接收的 lambda 会在 `timeout` 内反复执行，只要不抛异常就立即返回。值得注意的一个细节是：lambda 里先调 `equalsRecords`（返回 boolean 但这里忽略返回值，仅用于触发可能的副作用读取），再调 `assertRecordsEqual` 做真正的断言；真正起断言作用的是后者，前者相当于一次预读以稳定读取状态。同时新增了 `import org.awaitility.Awaitility;`。文档注释也从"等待 maxCheckCount 次、每次 checkInterval"改为"等待至多配置的 timeout"。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceContinuous.java`

**修改目的**：把 `waitUntilJobIsRunning` 中"while 空就 Thread.sleep(10)"的忙等循环替换为 Awaitility，避免在 CI 上因作业启动慢而 flaky。

**工作逻辑**：原实现是 `while (getRunningJobs(client).isEmpty()) { Thread.sleep(10); }`，没有上限，一旦作业永远起不来就会无限挂起测试线程，且 `throws Exception` 暴露给调用方。新实现：

```java
public static void waitUntilJobIsRunning(ClusterClient<?> client) {
  Awaitility.await("job should be running")
      .atMost(Duration.ofSeconds(30))
      .pollInterval(Duration.ofMillis(10))
      .untilAsserted(() -> assertThat(getRunningJobs(client)).isNotEmpty());
}
```

关键点有三：方法不再声明 `throws Exception`；显式加了 `atMost(30s)` 上限，避免无限挂死；保留 `pollInterval(10ms)` 以维持原有的轮询粒度，并用 AssertJ 的 `assertThat(...).isNotEmpty()` 作为断言。新增了 `import static org.assertj.core.api.Assertions.assertThat;` 和 `import org.awaitility.Awaitility;`。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceFailover.java`

**修改目的**：适配 `SimpleDataUtil.assertTableRecords` 的新签名，并删除冗余的 `assertRecords` 包装方法。

**工作逻辑**：原文件中有一个 `protected void assertRecords(Table, List<Record>, Duration interval, int maxCount)` 方法，仅仅是转发调用 `SimpleDataUtil.assertTableRecords(table, expectedRecords, interval, maxCount)`，本质是一层无意义的包装。本提交删除了这个方法及其对应的 `import org.apache.iceberg.Table;`。

两处调用点（`testBoundedIcebergSource` 末尾、连续式 failover 测试末尾）原本写的是 `assertRecords(sinkTableResource.table(), expectedRecords, Duration.ofMillis(10), 12000)`，含义是"每 10ms 检查一次、最多 12000 次"，折算总等待约 120 秒。改为直接调用 `SimpleDataUtil.assertTableRecords(sinkTableResource.table(), expectedRecords, Duration.ofSeconds(120))`，用单一 120 秒超时表达相同语义，更直观且不再依赖"间隔 × 次数"的隐式换算。原注释"wait longer for continuous source to reduce flakiness because CI servers tend to be overloaded"被保留。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/TestStreamingMonitorFunction.java`

**修改目的**：把 5 个测试方法里重复出现的"latch.await + Thread.sleep(1000) + 断言 splits.size()==1"三件套，提炼为统一的 `awaitExpectedSplits` 方法并用 Awaitility 实现。

**工作逻辑**：原 5 个测试方法（`testConcurrentRead`、`testSourceFunction`、`testSourceFunctionWithCheckpoint`、`testSourceFunctionRestart`、`testSourceFunctionRestore` 等）的开头都有完全相同的样板代码：

```java
CountDownLatch latch = new CountDownLatch(1);
TestSourceContext sourceContext = new TestSourceContext(latch);
runSourceFunctionInTask(sourceContext, function);
Assert.assertTrue("Should have expected elements.", latch.await(WAIT_TIME_MILLIS, TimeUnit.MILLISECONDS));
Thread.sleep(1000L);
...
Assert.assertEquals("Should produce the expected splits", 1, sourceContext.splits.size());
```

问题在于：`latch.await` 只能确认"至少 collect 过一次"，无法保证"恰好 collect 完一个完整 split"；后面的 `Thread.sleep(1000L)` 是经验性盲等，希望 1 秒后状态稳定；最后的 `assertEquals` 才是真正断言。这种"先 await 再 sleep 再断言"的串行写法既慢又不可靠。

改造后：`latch` 不再在测试方法里直接持有（改为 `new CountDownLatch(1)` 直接传入构造器），三件套合并为一次调用 `awaitExpectedSplits(sourceContext)`。新增的私有方法：

```java
private void awaitExpectedSplits(TestSourceContext sourceContext) {
  Awaitility.await("expected splits should be produced")
      .atMost(Duration.ofMillis(WAIT_TIME_MILLIS))
      .untilAsserted(
          () -> {
            assertThat(sourceContext.latch.getCount()).isEqualTo(0);
            assertThat(sourceContext.splits).as("Should produce the expected splits").hasSize(1);
          });
}
```

这里把两个条件合并为一次 `untilAsserted`：latch 计数归零（说明至少 collect 过）且 `splits.size()==1`（说明恰好产出一个 split）。两者同时满足才通过，任一不满足就继续轮询，直至 `WAIT_TIME_MILLIS` 超时。这样既消除了 `Thread.sleep(1000L)` 的盲等，又把原本分散在 5 处的重复逻辑收敛到一个方法。由于 `latch` 现在需要被 `awaitExpectedSplits` 访问，它必须通过 `sourceContext.latch` 访问，而 `TestSourceContext` 是内部类，外部类可以访问其私有字段，因此无需改可见性。同时移除了不再使用的 `import java.util.concurrent.TimeUnit;`，新增 `import static org.assertj.core.api.Assertions.assertThat;` 和 `import org.awaitility.Awaitility;`。

## 小结

本提交将 Flink 1.17 测试模块中散落的 `Thread.sleep()` 与手写轮询循环统一替换为 Awaitility 的 `untilAsserted` 模式，并顺手收敛了重复样板代码，显著降低了 CI 上的 flake 率与失败诊断成本。
