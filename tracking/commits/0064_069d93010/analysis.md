# 提交 0064：Spark 3.5: Use Awaitility instead of Thread.sleep() (#8853)

## 提交信息

- **序号**：0064 / 4088
- **哈希**：069d93010ca9142c96757ba3288b77fa02e74a60
- **短哈希**：069d93010
- **日期**：2023-10-17
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark 3.5: Use Awaitility instead of Thread.sleep() (#8853)
- **PR/Issue**：#8853

## 总体目的

该提交把 Spark 3.5 模块测试辅助类 `SparkSQLExecutionHelper` 中用于"等待 Spark SQL 执行完成并聚合指标"的固定时延轮询逻辑，替换为 Awaitility 异步等待框架，以降低测试的 flakiness（不稳定度）并清理对 JUnit `Assert` 的遗留依赖。

原有的等待逻辑用 `Thread.sleep(100)` 加 3 次重试（即最多等 300ms，每 100ms 轮询一次）来等 Spark 的 `SQLExecutionUIData.metricValues()` 由 null 变为非 null。这种"固定重试次数 + 固定 sleep 时长"的模式有两个典型问题：(1) 在慢机器或 CI 高负载时 300ms 不够，metric 还没聚合好就提前放弃，断言失败，造成 flaky test；(2) 在快机器上又得死等满 3 次 100ms，浪费时间。Awaitility 通过"轮询直到条件满足或超时"的语义一次性解决这两个问题：条件早满足就早返回（快路径），条件迟迟不满足就等到上限超时再失败（慢路径），且超时上限可以设得比固定 sleep 总和更长以提升容错。

此外，该提交顺手把同一方法内对 JUnit `Assert` 的调用迁移到 AssertJ 的 `assertThat(...)`，与项目近年统一向 AssertJ 迁移的趋势一致；同时把 `import org.junit.Assert;` 替换为 `import org.awaitility.Awaitility;` 与 AssertJ 的静态 import，并新增 `java.time.Duration` 的 import。

## 如何达成设计目的

整体设计分两步：

1. 在 `spark/v3.5/build.gradle` 中给测试配置追加 `awaitility` 依赖（通过 libs catalog 引用），让测试代码能使用 `org.awaitility.Awaitility`。
2. 重写 `SparkSQLExecutionHelper` 中等待 metric 聚合的逻辑：用 `Awaitility.await().atMost(...).pollInterval(...).untilAsserted(...)` 替换 `while` + `Thread.sleep` 重试循环；并把后续对 `metricValues` 的断言从 `Assert.assertNotNull` 改为 AssertJ 的 `assertThat(...).as(...).isNotNull()`，断言失败信息通过 `.as(...)` 描述而非 `Assert` 的字符串参数。

## 修改详情

### [spark/v3.5/build.gradle](file:///Users/fengxiaohang/trae/iceberglearn/spark/v3.5/build.gradle)

**修改目的**：为 Spark 3.5 模块测试引入 Awaitility 依赖。

**工作逻辑**：在 `dependencies { testImplementation ... }` 块中追加一行 `testImplementation libs.awaitility;`，与已有的 `libs.sqlite.jdbc` 等测试依赖并列。`libs.awaitility` 引用的是项目根目录版本目录（version catalog）中已声明的 awaitility 坐标，无需在此处指定版本号，保证全项目 awaitility 版本一致。该依赖仅作用于测试 classpath，不影响产物。

### [spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/SparkSQLExecutionHelper.java](file:///Users/fengxiaohang/trae/iceberglearn/spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/SparkSQLExecutionHelper.java)

**修改目的**：用 Awaitility 替换 `Thread.sleep` 重试循环，并把断言迁移到 AssertJ。

**工作逻辑**：

原逻辑（已删除）：

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

Assert.assertNotNull("Metric values were not finalized", lastExecution.metricValues());
String metricValue = lastExecution.metricValues().get(metricId).getOrElse(null);
Assert.assertNotNull(String.format("Metric '%s' was not finalized", metricName), metricValue);
```

新逻辑：

```java
Awaitility.await()
    .atMost(Duration.ofMillis(500))
    .pollInterval(Duration.ofMillis(100))
    .untilAsserted(
        () -> assertThat(statusStore.execution(lastExecution.executionId()).get()).isNotNull());

SQLExecutionUIData exec = statusStore.execution(lastExecution.executionId()).get();

assertThat(exec.metricValues()).as("Metric values were not finalized").isNotNull();
String metricValue = exec.metricValues().get(metricId).getOrElse(null);
assertThat(metricValue)
    .as(String.format("Metric '%s' was not finalized", metricName))
    .isNotNull();
```

关键变化：

1. **等待时长**：从最多 300ms（3 次 × 100ms）提升到最多 500ms，且每 100ms 轮询一次；条件一旦满足立即返回，不再死等。`atMost(500ms)` 是失败上限，`pollInterval(100ms)` 是轮询节奏。
2. **`untilAsserted`**：Awaitility 会反复执行传入 lambda 中的断言，断言通过则等待结束，断言抛 `AssertionError` 则继续轮询直到超时。这里用 `assertThat(statusStore.execution(...).get()).isNotNull()` 等待"该执行 ID 在 status store 中存在且非 null"——这是后续 `exec.metricValues()` 可用的前提。
3. **重新取值**：等待成功后再 `statusStore.execution(lastExecution.executionId()).get()` 取一次最新的 `SQLExecutionUIData exec`，再校验 `exec.metricValues()` 非空、对应 metric 值非空。原代码在 while 体内每次循环都重新取 `lastExecution`，新代码把"等待存在"与"取最终值"分离，语义更清晰。
4. **断言风格**：`Assert.assertTrue`/`assertNotNull` 改为 `assertThat(...).as(...).isTrue()/isNotNull()`，失败信息通过 `.as(...)` 描述，符合 AssertJ 风格。方法开头对 `sqlPlanMetric.isDefined()` 的校验也一并迁移。

import 调整：移除 `import org.junit.Assert;`，新增 `import static org.assertj.core.api.Assertions.assertThat;`、`import java.time.Duration;`、`import org.awaitility.Awaitility;`。

## 小结

该提交用 Awaitility 替换 Spark 3.5 测试中固定 sleep 的重试循环，并把断言统一到 AssertJ，显著降低了等待 Spark 指标聚合的测试在 CI 上的 flakiness，是测试稳定性与代码风格现代化的一次小修。
