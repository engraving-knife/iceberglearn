# 提交 0074：Flink 1.16: Use Awaitility instead of Thread.sleep() (#8880)

## 提交信息

- **序号**：0074 / 4088
- **哈希**：d581e79d39b3d2c00e8b70f9921be823bd6434f5
- **短哈希**：d581e79d3
- **日期**：2023-10-19
- **作者**：Naveen Kumar
- **提交说明**：Flink 1.16: Use Awaitility instead of Thread.sleep() (#8880)
- **PR/Issue**：#8880

## 总体目的

此提交是提交 0073（`d3af82f6e`，针对 Flink 1.15）的姊妹提交，将相同的 Awaitility 重构应用到 `flink/v1.16/` 子模块。Iceberg 为每个支持的 Flink 版本维护独立的源码副本（`flink/v1.15/`、`flink/v1.16/` 等），因此同一项测试基础设施改进需要按版本逐个落地，本提交补齐 1.16 版本。

动机与 0073 完全一致：流式源、连续源、failover 类测试对时序敏感，`Thread.sleep()` 与手工轮询循环在 CI 负载抖动下容易偶发失败（flaky）。Awaitility 的 `await().atMost(timeout).untilAsserted()` 在超时窗口内反复轮询执行断言，一旦通过立即返回，既缩短快路径耗时，又给慢路径明确的上限，从根本上降低 flaky 率。本提交为纯测试基础设施改进，不改变任何产品代码行为。

## 如何达成设计目的

整体思路与 0073 完全一致，仅目标目录不同：把"睡眠固定时长后再断言"重构为"在超时窗口内反复执行断言，任一次通过即返回"。涉及四类改造，分别对应四个测试文件，与 0073 一一对应：

1. `SimpleDataUtil.assertTableRecords` 重载签名从 `(table, expected, checkInterval, maxCheckCount)` 改为 `(table, expected, timeout)`，内部用 Awaitility 实现。
2. `TestIcebergSourceContinuous.waitUntilJobIsRunning` 把 `while + Thread.sleep(10)` 改为 Awaitility，并补 30 秒超时上限。
3. `TestIcebergSourceFailover` 删除薄包装方法 `assertRecords`，直接调用新签名的 `assertTableRecords`，把 `10ms × 12000` 的隐式总时长显式表达为 `Duration.ofSeconds(120)`。
4. `TestStreamingMonitorFunction` 把五个测试用例中重复的 `latch.await + Thread.sleep(1000) + Assert.assertEquals` 三段式逻辑抽取为统一的 `awaitExpectedSplits` 私有方法，用 Awaitility 同时等待 latch 归零与 split 数量断言。

## 修改详情

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/SimpleDataUtil.java`

**修改目的**：重构共享工具方法 `assertTableRecords`，用 Awaitility 替换手工轮询循环。

**工作逻辑**：新增 `org.awaitility.Awaitility` import；原方法签名 `assertTableRecords(Table, List<Record>, Duration checkInterval, int maxCheckCount) throws IOException, InterruptedException` 内部是 `for` 循环 + `Thread.sleep(checkInterval.toMillis())`，循环结束后做一次 `assertRecordsEqual`。新签名简化为 `assertTableRecords(Table, List<Record>, Duration timeout)`，实现为：

```java
Awaitility.await("expected list of records should be produced")
    .atMost(timeout)
    .untilAsserted(() -> {
      equalsRecords(expected, tableRecords(table), table.schema());
      assertRecordsEqual(expected, tableRecords(table), table.schema());
    });
```

`untilAsserted` 在 `atMost` 超时内反复执行 lambda，断言全过即返回，超时才抛最后失败。文档注释也从"waiting up to `maxCheckCount` with `checkInterval`"改为"waiting up to the configured `timeout`"。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceContinuous.java`

**修改目的**：将 `waitUntilJobIsRunning` 中无超时的 `while + Thread.sleep(10)` 忙等替换为 Awaitility。

**工作逻辑**：新增 `assertThat` 静态导入与 `Awaitility` import。原实现 `while (getRunningJobs(client).isEmpty()) Thread.sleep(10);` 无上限，job 永远起不来时测试会无限挂起。新实现：

```java
public static void waitUntilJobIsRunning(ClusterClient<?> client) {
  Awaitility.await("job should be running")
      .atMost(Duration.ofSeconds(30))
      .pollInterval(Duration.ofMillis(10))
      .untilAsserted(() -> assertThat(getRunningJobs(client)).isNotEmpty());
}
```

显式 30 秒上限，保留 10ms 轮询以快速感知 job 就绪，并移除 `throws Exception`。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceFailover.java`

**修改目的**：适配 `assertTableRecords` 新签名，删除冗余薄包装方法。

**工作逻辑**：新增 `assertTableRecords` 静态导入，删除不再使用的 `Table`、`SimpleDataUtil` import 与私有方法 `assertRecords(Table, List<Record>, Duration, int)`。两处调用点 `assertRecords(..., Duration.ofMillis(10), 12000)` 改为 `assertTableRecords(..., Duration.ofSeconds(120))`，把原 `10ms × 12000 = 120s` 隐式总时长显式表达为 120 秒超时。第二处保留原注释"wait longer for continuous source to reduce flakiness because CI servers tend to be overloaded"。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/source/TestStreamingMonitorFunction.java`

**修改目的**：把五个测试用例中重复的"等 latch + sleep + 断言 split 数"三段式逻辑统一替换为 Awaitility 实现。

**工作逻辑**：新增 `assertThat`、`Awaitility` import，删除不再使用的 `java.util.concurrent.TimeUnit` import。新增统一私有方法：

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

在 `WAIT_TIME_MILLIS` 超时内反复校验 latch 归零与 split 数为 1 同时成立，成立后才返回，随后再 `function.close()` 并断言记录。五个测试方法（`testSource`、`testSourceWithBoundedMode`、`testSourceWithSnapshotIngestion`、`testSourceWithCheckpoint`、`testSourceWithRestoredCheckpoint`）统一调用该 helper，删除各自内联的 `latch.await + Thread.sleep(1000) + Assert.assertEquals` 三行。这消除了固定 1 秒睡眠，也消除了 latch 归零与 split 写入之间的竞态窗口。

## 小结

作为 0073 的姊妹提交，本提交把同样的 Awaitility 重构应用到 Flink 1.16 子模块，使两个 Flink 版本的测试基础设施保持一致，统一消除了 `Thread.sleep()` 引发的 flaky 风险。
