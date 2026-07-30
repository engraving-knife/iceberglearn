# 提交 1014：Core: Remove reflection from TestParallelIterable (#10857)

## 提交信息

- **序号**：1014 / 4088
- **哈希**：b17d1c9abdb8fbd668ac02194cadd6003c3e37f7
- **短哈希**：b17d1c9ab
- **日期**：2024-08-02 20:44:48 +0200
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Core: Remove reflection from TestParallelIterable (#10857)\n\nThis is a unit test, so can leverage package-private access.
- **PR/Issue**：#10857

## 总体目的

`TestParallelIterable` 是 `core` 模块下针对 `ParallelIterable`（用于并行迭代多个 `CloseableIterable` 的工具类）的单元测试。测试需要观察 `ParallelIterable.ParallelIterator` 内部 `queue`（一个 `ConcurrentLinkedQueue`，用于缓存预取任务产出的元素）的状态：例如断言队列被填充、队列被清空、队列长度不超过 `maxQueueSize + iterables.size()` 等。

由于 `ParallelIterator` 原本是 `ParallelIterable` 内的 `private static class`，`queue` 字段也是 private，测试无法直接访问。原测试通过 Java 反射绕过访问控制：`iterator.getClass().getDeclaredField("queue")`、`queueField.setAccessible(true)`、`queueField.get(iterator)` 取出 `ConcurrentLinkedQueue` 实例后再断言。这种做法有几个缺点：(1) 反射代码冗长且脆弱——若字段名/类型变更，测试会在运行时才以 `NoSuchFieldException` 失败，编译期无法发现；(2) `setAccessible(true)` 在更严格的 JDK 安全策略下可能被拒绝；(3) `getClass()` 返回的是 `ParallelIterator` 的运行时类，但变量声明类型是 `CloseableIterator`，可读性差；(4) 反射本身就是测试代码的"代码异味"。

本提交的目的是利用"单元测试与被测类同包"这一事实——`TestParallelIterable` 位于 `org.apache.iceberg.util` 包，与 `ParallelIterable` 同包，可以访问其 package-private 成员。因此把 `ParallelIterator` 从 `private` 改为 package-private（无修饰符）并加上 `@VisibleForTesting`，新增 package-private 的 `queueSize()` 访问器，测试侧即可去掉所有反射代码，改用强类型直接调用。

## 如何达成设计目的

实现思路非常直接：

1. 在 `ParallelIterable` 中把内部类 `ParallelIterator` 的访问修饰符从 `private static class` 改为 `static class`（即 package-private），并加 `@VisibleForTesting` 注解表明它仅为测试可见而暴露。
2. 在 `ParallelIterator` 中新增 package-private 方法 `int queueSize()`，返回内部 `queue.size()`，同样加 `@VisibleForTesting`。这样测试无需直接接触 `queue` 字段，只需调用 `iterator.queueSize()`。
3. 在 `TestParallelIterable` 中：
   - 删除 `import java.lang.reflect.Field;`、`import java.util.Queue;`、`import java.util.concurrent.ConcurrentLinkedQueue;`；
   - 新增 `import org.apache.iceberg.util.ParallelIterable.ParallelIterator;`；
   - 三处测试方法把 `CloseableIterator<Integer> iterator = parallelIterable.iterator();` + 反射取 `queue` 的代码块，替换为 `ParallelIterator<Integer> iterator = (ParallelIterator<Integer>) parallelIterable.iterator();`（强转，因为 `iterator()` 返回类型是 `CloseableIterator<T>`）；
   - 把对 `queue` 的断言改为对 `iterator.queueSize()` 的断言：`assertThat(queue).isEmpty()` → `assertThat(iterator.queueSize()).isEqualTo(0)`，`assertThat(queue).isNotEmpty()` → `assertThat(iterator.queueSize()).isGreaterThan(0)`，`assertThat(queue).hasSizeLessThanOrEqualTo(...)` → `assertThat(iterator.queueSize()).isLessThanOrEqualTo(...)`；
   - 私有辅助方法 `queueHasElements(CloseableIterator<Integer>, Queue)` 改为 `queueHasElements(ParallelIterator<Integer>)`，参数从 iterator+queue 两参简化为单参。

## 修改详情

### `core/src/main/java/org/apache/iceberg/util/ParallelIterable.java`

**修改目的**：把 `ParallelIterator` 与其 `queueSize()` 暴露为 package-private，供同包测试访问，去除测试对反射的依赖。

**工作逻辑**：
- 新增 import `org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;`。
- `private static class ParallelIterator<T> implements CloseableIterator<T>` 改为 `@VisibleForTesting` + `static class ParallelIterator<T> implements CloseableIterator<T>`，即去掉 `private`，变为包级可见。
- 在 `ParallelIterator` 末尾新增方法：
  ```java
  @VisibleForTesting
  int queueSize() {
      return queue.size();
  }
  ```
  返回内部 `queue`（`ConcurrentLinkedQueue<Task<T>>` 类型，存预取任务）的当前大小。注意：`queue` 存的是 `Task<T>` 而非数据元素 `T` 本身，但作为"队列负载程度"的指标，其大小仍能反映预取缓冲的繁忙程度，与原测试用 `queue.isEmpty()/size()` 的语义一致。
- `Task` 内部类保持 `private`，未对外暴露。

### `core/src/test/java/org/apache/iceberg/util/TestParallelIterable.java`

**修改目的**：用强类型 `ParallelIterator` 替换反射，简化并加固测试。

**工作逻辑**：
- import 调整：删除 `java.lang.reflect.Field`、`java.util.Queue`、`java.util.concurrent.ConcurrentLinkedQueue`；新增 `org.apache.iceberg.util.ParallelIterable.ParallelIterator`。
- **`testParallelIterableDoesNotConsumeExtraTasksOnClose`**（第一个测试，验证 close 后队列被清空）：
  - 原 4 行反射取 `queue`：
    ```java
    CloseableIterator<Integer> iterator = parallelIterable.iterator();
    Field queueField = iterator.getClass().getDeclaredField("queue");
    queueField.setAccessible(true);
    ConcurrentLinkedQueue<?> queue = (ConcurrentLinkedQueue<?>) queueField.get(iterator);
    ```
    改为单行：
    ```java
    ParallelIterator<Integer> iterator = (ParallelIterator<Integer>) parallelIterable.iterator();
    ```
  - `untilAsserted(() -> queueHasElements(iterator, queue))` → `untilAsserted(() -> queueHasElements(iterator))`；
  - `untilAsserted(() -> assertThat(queue).isEmpty())` → `untilAsserted(() -> assertThat(iterator.queueSize()).isEqualTo(0))`。
- **第二个测试**（同样验证 close 后队列清空，逻辑相近）：
  - 同样把反射块改为强转单行；
  - `queueHasElements(iterator, queue)` → `queueHasElements(iterator)`；
  - `assertThat(queue).as("Queue is not empty after cleaning").isEmpty()` → `assertThat(iterator.queueSize()).as("Queue is not empty after cleaning").isEqualTo(0)`（因 lambda 多行而格式略有折行）。
- **第三个测试**（验证队列长度不超过 `maxQueueSize + iterables.size()`）：
  - 反射块改为强转单行；
  - 循环内 `assertThat(queue).as("iterator internal queue").hasSizeLessThanOrEqualTo(maxQueueSize + iterables.size())` → `assertThat(iterator.queueSize()).as("iterator internal queue size").isLessThanOrEqualTo(maxQueueSize + iterables.size())`。
- **私有辅助方法**：
  - 签名 `private void queueHasElements(CloseableIterator<Integer> iterator, Queue queue)` → `private void queueHasElements(ParallelIterator<Integer> iterator)`；
  - 体内 `assertThat(queue).isNotEmpty()` → `assertThat(iterator.queueSize()).as("queue size").isGreaterThan(0)`（用 `isGreaterThan(0)` 替代 `isNotEmpty()`，因为 `queueSize()` 返回 int 而非集合）。

## 小结

- **成效**：移除了 `TestParallelIterable` 中全部三处反射代码（约 12 行 `getDeclaredField`/`setAccessible`/`get` 模板），改用同包 package-private 访问 + `@VisibleForTesting` 暴露的 `queueSize()` 方法。测试可读性、类型安全性、编译期校验均提升；不再依赖 `setAccessible`，对严格安全策略更友好。`ParallelIterator` 的暴露面被标注 `@VisibleForTesting` 明确意图，避免被误用为正式 API。
- **影响范围**：仅两个文件——`core/src/main/java/org/apache/iceberg/util/ParallelIterable.java`（生产代码：1 个 import、1 处类修饰符、1 个新方法，共约 7 行新增/修改）和 `core/src/test/java/org/apache/iceberg/util/TestParallelIterable.java`（测试代码：3 个测试方法 + 1 个辅助方法改造）。生产代码的功能行为无任何变化，只是可见性放宽到 package-private 并新增一个只读访问器。
- **回迁到 1.4.x 的注意事项**：本提交是纯测试基础设施优化，回迁风险极低。1.4.x 分支的 `ParallelIterable` 与 `TestParallelIterable` 应当存在且结构类似，可直接 cherry-pick。需注意：(1) 1.4.x 若已用其他方式（如保留反射或已暴露访问器）解决该问题，则无需回迁；(2) `@VisibleForTesting` 注解来自 `org.apache.iceberg.relocated.com.google.common.annotations`（Iceberg 自带的 relocated Guava），1.4.x 中应已存在；(3) 暴露 `ParallelIterator` 为 package-private 不影响公开 API 兼容性（仍非 public）。整体非常适合回迁。
