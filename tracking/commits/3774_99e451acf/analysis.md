# 提交 3774：Core: Skip testAddManyFilesWithConsistentOrdering if WORKER_THREAD_POOL_SIZE < 3 (#16506)

## 提交信息

- **序号**：3774 / 4088
- **哈希**：99e451acf38266c5eed9482c7feeaf20914620d5
- **短哈希**：99e451acf
- **日期**：2026-05-22 15:14:10 -0700
- **作者**：Yuya Ebihara
- **提交说明**：Core: Skip testAddManyFilesWithConsistentOrdering if WORKER_THREAD_POOL_SIZE < 3 (#16506)
- **PR/Issue**：#16506

## 总体目的

这个提交修复了一个 flaky 测试（不稳定测试）`testAddManyFilesWithConsistentOrdering`。该测试验证在多线程环境下合并追加文件时的顺序一致性。测试使用 `multiplier = 3` 来创建多个文件组，预期工作线程池大小至少为 3 才能正确测试多线程排序行为。

当 `WORKER_THREAD_POOL_SIZE` 小于 3 时（例如在资源受限的 CI 环境或单线程测试环境中），测试无法真正测试多线程排序行为，可能因为线程不足而导致不确定的结果或失败。

## 如何达成设计目的

在测试开始前添加 `assumeThat` 前置条件检查，当 `ThreadPools.WORKER_THREAD_POOL_SIZE` 小于 3 时跳过测试，而不是让测试在不满足条件的环境中失败。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestMergeAppend.java` (+6/-0 lines)

**修改目的**：添加测试前置条件，线程池不足时跳过测试。

**工作逻辑**：
```java
int multiplier = 3;
assumeThat(ThreadPools.WORKER_THREAD_POOL_SIZE)
    .as(
        "Worker thread pool size should be at least 3 to test manifest file ordering with multiple threads")
    .isGreaterThanOrEqualTo(multiplier);
```

使用 AssertJ 的 `assumeThat`（来自 `org.assertj.core.api.Assumptions`），当条件不满足时测试会被跳过（标记为 skipped/aborted）而非失败。`ThreadPools.WORKER_THREAD_POOL_SIZE` 是 Iceberg 中工作线程池的大小配置，可能由系统属性或环境配置决定。

## 总结

这个提交通过添加前置条件检查修复了 `testAddManyFilesWithConsistentOrdering` 测试在低线程数环境中的不稳定性。当工作线程池大小不足 3 时跳过测试，避免因环境限制导致的误报。这是处理 flaky 测试的常见做法，确保测试只在能够真正验证目标行为的条件下运行。
