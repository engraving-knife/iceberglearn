# 提交 0943：Spark 3.3: Ignore flaky test taking up all device space (#10704)

## 提交信息

- **序号**：0943 / 4088
- **哈希**：f59055a07030f19904648a4738774cb605196985
- **短哈希**：f59055a07
- **日期**：2024-07-17 15:13:13 +0800
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Spark 3.3: Ignore flaky test taking up all device space (#10704)
- **PR/Issue**：#10704

## 总体目的

Spark 3.3 模块中的测试 `TestCopyOnWriteMerge.testMergeWithConcurrentTableRefresh` 是一个用于验证并发场景下表刷新行为的并发测试。该测试存在不稳定问题（flaky），表现为在某些 CI 环境下运行时会不断失败并产生大量临时数据，最终把构建节点（device）的磁盘空间耗尽，影响其他测试与构建任务的可执行性。

提交说明明确指出该 flaky 测试"taking up all device space"（占满所有磁盘空间）。相关根因追踪于 issue #10040。为避免 CI 资源被一个尚未修复的不稳定测试持续拖累，本提交在 PR #10704 中先通过 JUnit 的 `@Ignore` 注解将该测试禁用，并在注解中标注对应 issue 链接，待根因修复后再恢复执行。这是典型的"先止血、后追因"的 CI 治理手段。

## 如何达成设计目的

实现方式是在该测试方法上添加 JUnit 4 的 `@Ignore` 注解，并附上指向 issue #10040 的注释作为禁用理由与后续追踪入口。同时新增 `import org.junit.Ignore;` 导入。这样 JUnit 运行器在执行测试套件时会跳过该方法，既不再触发失败、也不再产生其运行时生成的大量临时数据。方法本身代码、`@Test` 注解与 `synchronized` 修饰符均保留，便于将来直接去掉 `@Ignore` 即可恢复。

## 修改详情

### `spark/v3.3/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestCopyOnWriteMerge.java`

**修改目的**：禁用不稳定且会耗尽磁盘空间的并发测试 `testMergeWithConcurrentTableRefresh`，恢复 CI 环境的稳定性。

**工作逻辑**：
- 新增 `import org.junit.Ignore;`，引入 JUnit 4 的忽略注解；
- 在 `testMergeWithConcurrentTableRefresh` 方法上（已有 `@Test`）叠加 `@Ignore // Ignored due to https://github.com/apache/iceberg/issues/10040`，使 JUnit 跳过该用例；
- 方法签名 `public synchronized void testMergeWithConcurrentTableRefresh() throws Exception` 与方法体保持不变，等根因修复后移除 `@Ignore` 即可恢复执行。

```diff
+import org.junit.Ignore;
 ...
   @Test
+  @Ignore // Ignored due to https://github.com/apache/iceberg/issues/10040
   public synchronized void testMergeWithConcurrentTableRefresh() throws Exception {
```

## 小结

- **成效**：通过 `@Ignore` 暂时禁用导致磁盘占满的不稳定并发测试，缓解 CI 资源被拖垮的问题，并将追踪指向 issue #10040。
- **影响范围**：仅影响 Spark 3.3 扩展测试模块中的单个测试方法，不涉及任何生产代码。
- **回迁到 1.4.x 的注意事项**：若 1.4.x 分支同样存在该 flaky 测试并困扰 CI，则可回迁此止血补丁；但应同时确认 1.4.x 是否已修复 issue #10040 的根因——若已修复则无需禁用。回迁时仅需关注 `@Ignore` 注解与导入语句的添加位置一致即可，风险极低。
