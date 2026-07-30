# 提交 3927：Revert "Spark: Spark tests cache rewrite input (#16740)" (#16912)

## 提交信息

- **序号**：3927 / 4088
- **哈希**：902ed1b80d3cc24733fc72544578c58ca0ffe9c6
- **短哈希**：902ed1b80
- **日期**：2026-06-22 09:23:51 -0600
- **作者**：Andrei Tserakhau
- **提交说明**：Revert "Spark: Spark tests cache rewrite input (#16740)" (#16912)
- **PR/Issue**：#16912（回退 #16740）

## 总体目的

这次提交回退了先前合入的 #16740（"Spark: Spark tests cache rewrite input"）。原 PR #16740 修改了 Spark 各分支（3.5、4.0、4.1）的 `TestRewriteDataFilesAction` 测试类，引入了对 rewrite 输入的缓存机制。从 diff 统计来看，回退操作在每个分支的测试文件中删除了约 127 行代码（共删除 369 行，新增 12 行），说明原 PR 引入了相当数量的测试基础设施代码。

回退的原因通常是因为该改动引入了测试不稳定（flaky）问题、破坏了既有测试、或与后续改动产生冲突。由于这是对测试代码的回退而非生产代码，影响范围限于测试套件本身。回退后测试恢复到 #16740 合入前的状态，确保 CI 的稳定性。

## 如何达成设计目的

通过 `git revert` 操作，自动生成反向 diff，撤销 #16740 对三个 Spark 分支 `TestRewriteDataFilesAction.java` 的所有修改。回退涉及移除原 PR 引入的 import（如 `Path`、`TreeMap`、`AtomicInteger`、`Supplier`、`AppendFiles`、`ImmutableList` 等）、辅助方法和测试用例，恢复原有的测试实现。

## 修改详情

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (+4/-123 lines)

**修改目的**：回退 Spark 3.5 测试中 rewrite 输入缓存相关的改动。

**工作逻辑**：移除原 #16740 引入的 import 语句、缓存相关辅助方法和测试用例，恢复测试到原始状态。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (+4/-123 lines)

**修改目的**：回退 Spark 4.0 测试中 rewrite 输入缓存相关的改动。

**工作逻辑**：同 Spark 3.5，移除 #16740 引入的改动。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (+4/-123 lines)

**修改目的**：回退 Spark 4.1 测试中 rewrite 输入缓存相关的改动。

**工作逻辑**：同上，移除 #16740 引入的改动。

## 总结

这次提交回退了 #16740 对 Spark 三个分支测试代码的改动，恢复 rewrite data files 测试到原始状态。回退操作针对的是测试基础设施，不影响生产代码，目的是消除该 PR 可能引入的测试不稳定或冲突问题，保证 CI 的可靠性。
