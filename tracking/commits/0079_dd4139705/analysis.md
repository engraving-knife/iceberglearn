# 提交 0079：Spark 3.2: Use Awaitility instead of Thread.sleep() (#8882)

## 提交信息

- **序号**：0079 / 4088
- **哈希**：dd4139705c1bd1cff347793a8e9bf481ac11077b
- **短哈希**：dd4139705
- **日期**：2023-10-20 08:49:34 +0200
- **作者**：Naveen Kumar
- **提交说明**：Spark 3.2: Use Awaitility instead of Thread.sleep() (#8882)
- **PR/Issue**：#8882

## 总体目的

这个提交是提交 0077（Spark 3.4）与 0078（Spark 3.3）同名改动在 Spark 3.2 模块上的对应版本，三者构成一组跨 Spark 主版本的同步维护。目的是将 Iceberg Spark 3.2 测试代码中基于 `Thread.sleep()` 的轮询等待替换为 Awaitility 的声明式异步等待，并将 JUnit `Assert` 断言迁移到 AssertJ 的 `assertThat` 风格。

被修改的 `SparkSQLExecutionHelper`（位于 `spark/v3.2/spark/src/test/java/...`）与 0077、0078 中的是同名同结构的测试辅助类，用于从 Spark 的 `SQLAppStatusStore` 读取最近一次 SQL 执行的指标值。原实现用最多 3 次、每次 `Thread.sleep(100)` 的循环等待 `metricValues` 聚合完成，总等待上限仅 300ms，且阻塞式盲目睡眠语义不清晰，在 CI 或高负载环境下容易因指标未及时聚合而引发偶发测试失败（flaky test）。

引入 Awaitility 后，等待变为 `atMost(500ms) + pollInterval(100ms) + untilAsserted(...)`：在 500ms 上限内每 100ms 轮询一次断言，条件满足立即返回，超时才失败。这既放宽了慢环境下的等待余量，又让"等待异步条件就绪"的意图在代码中清晰可见。AssertJ 的链式断言则提供了更友好的失败描述。本提交与 0077、0078 一起，把同一测试改进同时落到 Iceberg 当时维护的 Spark 3.2/3.3/3.4 三条版本线上，体现了 Iceberg "每个 Spark 主版本独立子项目、同一改动逐版本复制"的并行维护模式——三份 `SparkSQLExecutionHelper.java` 在各自 `spark/v3.x/` 目录下独立存在，需各自应用相同修复以保持行为一致。

## 如何达成设计目的

整体设计与 0077、0078 完全一致，只是落地到 `spark/v3.2` 目录。第一步在 `spark/v3.2/build.gradle` 的测试依赖中新增 `libs.awaitility`；第二步重写 `SparkSQLExecutionHelper#executeMetricValue` 的等待与断言逻辑，用 Awaitility 替换 `Thread.sleep()` 循环，并将 JUnit `Assert` 替换为 AssertJ `assertThat`。改动范围局限于一个测试辅助类与一行构建依赖。

## 修改详情

### `spark/v3.2/build.gradle`

**修改目的**：为 Spark 3.2 模块的测试引入 Awaitility 依赖。

**工作逻辑**：在 `dependencies` 块的 `testImplementation` 列表中新增 `testImplementation libs.awaitility`，与 `sqlite.jdbc` 等已有测试依赖并列。版本由 `gradle/libs.versions.toml` 版本目录统一管理。注意 Spark 3.2 的构建文件中紧随其后的是 `tasks.withType(Test)` 块，与 3.3/3.4 的 `test` 块结构略有不同，但依赖声明位置一致。

### `spark/v3.2/spark/src/test/java/org/apache/iceberg/spark/source/SparkSQLExecutionHelper.java`

**修改目的**：用 Awaitility 替换 `Thread.sleep()` 轮询，并将断言迁移到 AssertJ。

**工作逻辑**：

1. 导入调整：移除 `org.junit.Assert`，新增 `org.awaitility.Awaitility`、`java.time.Duration` 以及 `static org.assertj.core.api.Assertions.assertThat`。

2. 指标存在性断言改写：`Assert.assertTrue(String.format("Metric '%s' not found in last execution", metricName), sqlPlanMetric.isDefined())` 改为 `assertThat(sqlPlanMetric.isDefined()).as(String.format("Metric '%s' not found in last execution", metricName)).isTrue()`，语义等价但采用 AssertJ 链式写法。

3. 等待逻辑核心改写：原 `Thread.sleep(100)` 三次重试循环被替换为：
   ```java
   Awaitility.await()
       .atMost(Duration.ofMillis(500))
       .pollInterval(Duration.ofMillis(100))
       .untilAsserted(
           () -> assertThat(statusStore.execution(lastExecution.executionId()).get()).isNotNull());
   ```
   等待上限由 300ms 提升到 500ms，轮询由 Awaitility 调度，条件检查对象改为每次从 store 重新拉取最新执行快照。

4. 取值与最终断言：等待结束后新增 `SQLExecutionUIData exec = statusStore.execution(lastExecution.executionId()).get();` 取最新快照，`metricValues` 与 `metricValue` 的非空断言均迁移到 AssertJ 并附 `as(...)` 描述，最终返回 `metricValue`。

## 小结

本提交将 Spark 3.2 测试中的 `Thread.sleep()` 轮询替换为 Awaitility 并迁移到 AssertJ 断言，是 0077/0078 在 Spark 3.2 分支的对应版本，完成了跨 Spark 3.2/3.3/3.4 三版本的测试基础设施同步现代化。
