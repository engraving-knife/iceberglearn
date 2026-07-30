# 提交 0077：Spark 3.4: Use Awaitility instead of Thread.sleep() (#8884)

## 提交信息

- **序号**：0077 / 4088
- **哈希**：1a9a7d004b6e8fb135782caf810c5066ac8408bc
- **短哈希**：1a9a7d004
- **日期**：2023-10-20 08:48:54 +0200
- **作者**：Naveen Kumar
- **提交说明**：Spark 3.4: Use Awaitility instead of Thread.sleep() (#8884)
- **PR/Issue**：#8884

## 总体目的

这个提交针对 Iceberg 的 Spark 3.4 模块测试代码，将原本基于 `Thread.sleep()` 的轮询等待替换为 Awaitility 库提供的声明式异步等待，并同时将 JUnit 的 `Assert` 断言迁移到 AssertJ 的 `assertThat` 风格。被修改的 `SparkSQLExecutionHelper` 是测试辅助类，用于从 Spark 的 `SQLAppStatusStore` 中读取最近一次 SQL 执行的指标（metric）值，而指标值在执行提交后并不会立即聚合完成，需要等待执行结束、metric 聚合后才会写入 `metricValues`。

原实现用一个最多 3 次、每次 `Thread.sleep(100)` 的循环来轮询 `lastExecution.metricValues()` 是否为非空，总等待上限仅 300ms，且这种"固定睡眠 + 固定重试次数"的写法存在两类问题：其一，在 CI 环境或负载较高的机器上，Spark 作业的 metric 聚合可能超过 300ms 才完成，导致测试因指标未就绪而偶发失败（flaky test）；其二，`Thread.sleep()` 是阻塞式盲目等待，无论条件是否满足都会睡满时长，浪费时间且语义不清晰。

引入 Awaitility 后，等待变为 `atMost(500ms) + pollInterval(100ms) + untilAsserted(...)` 的形式：在 500ms 上限内每 100ms 轮询一次断言，一旦断言通过立即返回，超时则抛出带有上下文的失败。这既提高了测试在慢环境下的鲁棒性，又让"等待异步条件就绪"的意图在代码中一目了然。同时断言改用 AssertJ 的链式 `assertThat(...).as(...).isTrue()/isNotNull()` 写法，错误描述更友好、可读性更高。这是 Iceberg 测试基础设施现代化的一个具体环节，与同期的 Spark 3.3、Spark 3.2 等版本的同名提交构成一组跨版本同步维护。

## 如何达成设计目的

整体设计分两步：第一步在 `spark/v3.4/build.gradle` 的测试依赖中加入 `libs.awaitility`（通过版本目录引用），使 Awaitility 在测试 classpath 中可用；第二步重写 `SparkSQLExecutionHelper#executeMetricValue` 方法中的等待与断言逻辑，用 Awaitility 的 `await().atMost().pollInterval().untilAsserted(...)` 替换 `Thread.sleep()` 循环，并将 JUnit `Assert` 全部替换为 AssertJ `assertThat`。改动结构清晰、范围局限于单个测试辅助类与一行构建依赖。

## 修改详情

### `spark/v3.4/build.gradle`

**修改目的**：为 Spark 3.4 模块的测试引入 Awaitility 依赖。

**工作逻辑**：在 `dependencies` 块的 `testImplementation` 列表中新增一行 `testImplementation libs.awaitility`，与已有的 `sqlite.jdbc` 等测试依赖并列。`libs.awaitility` 引用自 `gradle/libs.versions.toml` 版本目录，由该文件统一管理版本号，无需在此处硬编码版本。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/SparkSQLExecutionHelper.java`

**修改目的**：用 Awaitility 替换 `Thread.sleep()` 轮询，并将断言迁移到 AssertJ。

**工作逻辑**：

1. 导入调整：移除 `org.junit.Assert`，新增 `org.awaitility.Awaitility`、`java.time.Duration` 以及 `static org.assertj.core.api.Assertions.assertThat`。

2. 指标存在性断言改写：原 `Assert.assertTrue(String.format("Metric '%s' not found in last execution", metricName), sqlPlanMetric.isDefined())` 改为 AssertJ 链式写法 `assertThat(sqlPlanMetric.isDefined()).as(String.format("Metric '%s' not found in last execution", metricName)).isTrue()`。`as(...)` 提供失败描述，语义与原 `assertTrue(message, condition)` 等价但更易读。

3. 等待逻辑核心改写：原本的
   ```java
   int attempts = 3;
   while (lastExecution.metricValues() == null && attempts > 0) {
     try {
       Thread.sleep(100);
       attempts--;
     } catch (InterruptedException e) {
       throw new RuntimeException(e);
     }
     lastExecution = statusStore.execution(lastExecution.executionId()).get();
   }
   ```
   被替换为：
   ```java
   Awaitility.await()
       .atMost(Duration.ofMillis(500))
       .pollInterval(Duration.ofMillis(100))
       .untilAsserted(
           () -> assertThat(statusStore.execution(lastExecution.executionId()).get()).isNotNull());
   ```
   关键变化有三点：等待上限从 300ms 提升到 500ms，给慢环境更多余量；轮询语义从"睡眠后手动检查"变为"由 Awaitility 调度断言直至通过或超时"；条件检查的对象从 `lastExecution.metricValues() == null` 改为 `statusStore.execution(...).get()` 非空（即每次轮询都重新从 store 拉取最新执行快照，确保拿到聚合后的数据）。

4. 取值与最终断言：等待结束后新增 `SQLExecutionUIData exec = statusStore.execution(lastExecution.executionId()).get();` 显式取出最新执行快照，随后 `Assert.assertNotNull("Metric values were not finalized", lastExecution.metricValues())` 改为 `assertThat(exec.metricValues()).as("Metric values were not finalized").isNotNull()`，metric value 的非空断言同样迁移到 AssertJ 并附带描述。最终返回 `metricValue`。

## 小结

本提交通过在 Spark 3.4 测试中引入 Awaitility 替代 `Thread.sleep()` 轮询，并将断言统一到 AssertJ，提升了异步指标等待的鲁棒性与可读性，是 Iceberg 跨 Spark 版本测试基础设施现代化同步维护的一环。
