# 提交 0073：Flink 1.15: Use Awaitility instead of Thread.sleep() (#8877)

## 提交信息

- **序号**：0073 / 4088
- **哈希**：d3af82f6edd02d8f0c9a03f1f7823c986db10b8e
- **短哈希**：d3af82f6e
- **日期**：2023-10-19
- **作者**：Naveen Kumar
- **提交说明**：Flink 1.15: Use Awaitility instead of Thread.sleep() (#8877)
- **PR/Issue**：#8877

## 总体目的

此提交针对 Flink 1.15 集成模块的测试代码，将原本依赖 `Thread.sleep()` 与手工轮询循环的等待逻辑，统一替换为使用 [Awaitility](https://github.com/awaitility/awaitility) 库的声明式条件等待，以消除测试中的 flaky（不稳定）行为。

`Thread.sleep()` 在测试中是一种典型的反模式：它要么等待时间过长（拖慢测试套件），要么等待时间过短（在 CI 机器负载高、调度抖动时条件尚未满足就继续往下走，导致断言失败）。Iceberg Flink 的流式/连续源测试涉及异步任务、checkpoint、failover 等场景，对时序敏感，固定睡眠特别容易在 CI 环境下偶发失败。Awaitility 提供 `await().atMost(timeout).untilAsserted(() -> ...)` 这样的 API，会在超时窗口内反复轮询执行断言，一旦断言通过立即返回，既缩短了快路径下的测试耗时，又在慢路径下给了足够的容错窗口，从根本上降低了 flaky 率。

本提交只覆盖 `flink/v1.15/` 子模块，对应的 1.16 版本在后续提交 0074 中处理。改动是纯测试基础设施改进，不改变任何产品代码行为。

## 如何达成设计目的

整体思路是把"睡眠固定时长后再断言"的模式，重构为"在超时窗口内反复执行断言，任一次通过即返回"的模式。涉及四类改造：

1. `SimpleDataUtil.assertTableRecords` 的重载签名从 `(table, expected, checkInterval, maxCheckCount)` 改为 `(table, expected, timeout)`，内部用 Awaitility 实现，作为共享工具供多个测试使用。
2. `TestIcebergSourceContinuous.waitUntilJobIsRunning` 把 `while + Thread.sleep(10)` 改为 Awaitility 等待运行中 job 非空。
3. `TestIcebergSourceFailover` 删除薄包装方法 `assertRecords`，直接调用新签名的 `assertTableRecords`，并把原本隐含的总等待时长（`10ms * 12000 = 120s`）显式表达为 `Duration.ofSeconds(120)`。
4. `TestStreamingMonitorFunction` 把分散在五个测试用例中的 `latch.await + Thread.sleep(1000) + Assert.assertEquals(splits.size, 1)` 三段式逻辑，抽取为统一的 `awaitExpectedSplits` 私有方法，用 Awaitility 同时等待 latch 归零与 split 数量断言。

## 修改详情

### `flink/v1.15/flink/src/test/java/org/apache/iceberg/flink/SimpleDataUtil.java`

**修改目的**：重构共享工具方法 `assertTableRecords`，用 Awaitility 替换手工轮询循环。

**工作逻辑**：原方法签名为 `assertTableRecords(Table, List<Record>, Duration checkInterval, int maxCheckCount)`，实现是一个 `for` 循环：每轮检查 `equalsRecords`，若不等则 `Thread.sleep(checkInterval.toMillis())`，循环结束后无论成败再做一次 `assertRecordsEqual`。问题在于：循环结束的"最后一次断言"可能发生在条件刚满足后又被打破的瞬态；且 `maxCheckCount * checkInterval` 构成的总超时语义不直观。

新方法签名简化为 `assertTableRecords(Table, List<Record>, Duration timeout)`（去掉了 `throws IOException, InterruptedException`，因为 Awaitility 内部用 lambda 包装，异常处理更干净），实现为：

```java
Awaitility.await("expected list of records should be produced")
    .atMost(timeout)
    .untilAsserted(() -> {
      equalsRecords(expected, tableRecords(table), table.schema());
      assertRecordsEqual(expected, tableRecords(table), table.schema());
    });
```

`untilAsserted` 会在 `atMost` 设定的超时内反复执行传入的 lambda，直到其中所有 AssertJ/JUnit 断言都通过才返回；任一轮失败则继续轮询，超时后才抛出最后的失败。默认轮询间隔 100ms（Awaitility 默认），既比原 `10ms` 更稀疏、减少无谓 CPU 占用，又能在条件满足时迅速返回。

### `flink/v1.15/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceContinuous.java`

**修改目的**：将 `waitUntilJobIsRunning` 中 `while + Thread.sleep(10)` 的忙等替换为 Awaitility。

**工作逻辑**：原实现 `while (getRunningJobs(client).isEmpty()) Thread.sleep(10);` 没有超时上限——一旦 job 永远起不来，测试会无限挂起，CI 上表现为超时被杀而非清晰失败。新实现：

```java
Awaitility.await("job should be running")
    .atMost(Duration.ofSeconds(30))
    .pollInterval(Duration.ofMillis(10))
    .untilAsserted(() -> assertThat(getRunningJobs(client)).isNotEmpty());
```

显式设定 30 秒上限，并保留 10ms 轮询间隔以快速感知 job 就绪，job 非空即返回。同时也移除了方法的 `throws Exception`。

### `flink/v1.15/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceFailover.java`

**修改目的**：适配 `assertTableRecords` 的新签名，删除冗余包装方法。

**工作逻辑**：原类中有一个薄包装 `assertRecords(Table, List<Record>, Duration, int)`，仅转发到 `SimpleDataUtil.assertTableRecords`。由于新签名去掉了 `maxCount` 参数且无需 `throws Exception`，本提交直接删除该包装，把两处调用点改为静态导入并直接调用 `assertTableRecords`：

- `assertRecords(..., Duration.ofMillis(10), 12000)` → `assertTableRecords(..., Duration.ofSeconds(120))`，把原 `10ms × 12000 = 120s` 的隐式总时长显式表达为 120 秒超时。
- 第二处同样改为 `Duration.ofSeconds(120)`，并保留了原注释"wait longer for continuous source to reduce flakiness because CI servers tend to be overloaded"。

同时清理了不再使用的 `Table`、`SimpleDataUtil` import。

### `flink/v1.15/flink/src/test/java/org/apache/iceberg/flink/source/TestStreamingMonitorFunction.java`

**修改目的**：把分散在五个测试用例中的"等 latch + sleep + 断言 split 数"三段式逻辑，统一替换为 Awaitility 实现。

**工作逻辑**：原每个测试用例都是这样的模式：

```java
Assert.assertTrue("Should have expected elements.",
    latch.await(WAIT_TIME_MILLIS, TimeUnit.MILLISECONDS));
Thread.sleep(1000L);
function.close();
Assert.assertEquals("Should produce the expected splits", 1, sourceContext.splits.size());
TestHelpers.assertRecords(...);
```

问题在于 `Thread.sleep(1000L)` 是固定等待，且 `latch.await` 与 `splits.size` 断言之间没有原子性——latch 归零时 split 可能还没写入 `sourceContext.splits`。本提交抽取了统一私有方法：

```java
private void awaitExpectedSplits(TestSourceContext sourceContext) {
  Awaitility.await("expected splits should be produced")
      .atMost(Duration.ofMillis(WAIT_TIME_MILLIS))
      .untilAsserted(() -> {
        assertThat(sourceContext.latch.getCount()).isEqualTo(0);
        assertThat(sourceContext.splits).as("Should produce the expected splits").hasSize(1);
      });
}
```

在 `WAIT_TIME_MILLIS` 超时内反复校验两个条件同时成立，成立后才返回，随后再 `function.close()` 并断言记录。这消除了固定 1 秒睡眠，也消除了 latch 与 split 之间的竞态窗口。五个测试方法（`testSource`、`testSourceWithBoundedMode`、`testSourceWithSnapshotIngestion`、`testSourceWithCheckpoint`、`testSourceWithRestoredCheckpoint`）统一调用该 helper，去掉了原各自内联的 `latch.await + Thread.sleep + assertEquals` 三行。同时清理了不再使用的 `java.util.concurrent.TimeUnit` import。

## 小结

通过把 Flink 1.15 测试中所有 `Thread.sleep()` 与手工轮询替换为 Awaitility 的 `atMost().untilAsserted()`，本提交显著降低了流式源/failover 测试在 CI 上的 flaky 率，既加快了快路径执行，又给了慢路径明确的超时语义。
