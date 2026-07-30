# 提交 3365：Spark: Add ExecutorService support to RewriteTablePath (#15381)

## 提交信息

- **序号**：3365 / 4088
- **哈希**：9b7f67d7862949d8815dacfae9bd0cfb821ead3a
- **短哈希**：9b7f67d78
- **日期**：2026-03-09
- **作者**：Maximilian Michels
- **提交说明**：Spark: Add ExecutorService support to RewriteTablePath (#15381)
- **PR/Issue**：#15381

## 总体目的

该提交为 `RewriteTablePath`（表路径重写）Action 增加了通过自定义 `ExecutorService` 并行执行元数据重写的能力，以提升大表路径迁移的性能。

`RewriteTablePath` 是 Iceberg Spark 模块提供的一个 Action，用于将表的元数据文件（包括 version 文件、manifest list、manifest 文件、删除文件等）从源路径重写到目标路径，常用于表数据迁移或存储位置变更场景。在本次改动之前，该 Action 在重写 manifest list 文件和 version 文件时采用的是串行流式处理（`stream().map().reduce()`），当表拥有大量快照和版本文件时，逐个重写会成为性能瓶颈。

本次改动在 API 层为 `RewriteTablePath` 接口新增 `executeWith(ExecutorService)` 方法，在 Spark 实现 `RewriteTablePathSparkAction` 中将 manifest list 重写和 version 文件重写两处串行逻辑改为基于 `Tasks` 工具的并行执行。用户可传入自定义线程池来并行化这些元数据重写操作；若不调用该方法，则保持原有的串行行为（向后兼容）。

## 如何达成设计目的

整体思路分三部分：

1. **API 层**：在 `RewriteTablePath` 接口新增 `executeWith(ExecutorService)` 默认方法，默认抛出 `UnsupportedOperationException`，与 Iceberg 其他 Action 的 `executeWith` 约定一致。
2. **Spark 实现**：在 `RewriteTablePathSparkAction` 中实现该方法，存储 ExecutorService 字段，并将 `rewriteManifestLists` 和 `rewriteVersionFiles` 中的串行流式处理改为 `Tasks.foreach(...).executeWith(executorService)` 并行执行，使用并发集合（`Sets.newConcurrentHashSet`）汇总结果。
3. **测试**：新增 `testRewritePathWithExecutorService` 测试，使用 4 线程固定池对 50 个快照的表执行路径重写并验证文件数量正确。

涉及 API 层 1 个文件、Spark v4.1 模块 2 个文件。

## 修改详情

### `api/src/main/java/org/apache/iceberg/actions/RewriteTablePath.java` (+12/-0 lines)

**修改目的**：在接口层声明 `executeWith(ExecutorService)` 方法。

**工作逻辑**：
新增 import `java.util.concurrent.ExecutorService`，并添加默认方法：
```java
default RewriteTablePath executeWith(ExecutorService executorService) {
  throw new UnsupportedOperationException(
      "This implementation does not support providing an ExecutorService.");
}
```
Javadoc 说明该方法用于传入替代的执行器服务以并行化 version 文件和 manifest list 的重写；若不调用则串行执行。默认抛出异常的约定与 Iceberg 其他 Action（如 `RewriteDataFiles`）保持一致，强制实现类显式覆盖。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java` (+42/-9 lines)

**修改目的**：实现并行 manifest list 和 version 文件重写。

**工作逻辑**：
- 新增字段 `private ExecutorService executorService;` 和 import `ExecutorService`、`Tasks`。
- 实现 `executeWith`：存储传入的 service 并返回 `this`。
- **manifest list 重写并行化**（`rewriteManifestLists` 方法）：
  原代码：
  ```java
  RewriteResult<ManifestFile> rewriteManifestListResult =
      validSnapshots.stream()
          .map(snapshot -> rewriteManifestList(snapshot, endMetadata, manifestsToRewrite))
          .reduce(new RewriteResult<>(), RewriteResult::append);
  ```
  改为使用 `Tasks` 并行执行：
  ```java
  Set<RewriteResult<ManifestFile>> manifestListResults = Sets.newConcurrentHashSet();
  Tasks.foreach(validSnapshots)
      .noRetry()
      .throwFailureWhenFinished()
      .executeWith(executorService)
      .run(snapshot -> manifestListResults.add(
          rewriteManifestList(snapshot, endMetadata, manifestsToRewrite)));
  RewriteResult<ManifestFile> rewriteManifestListResult = new RewriteResult<>();
  manifestListResults.forEach(rewriteManifestListResult::append);
  ```
  使用 `Sets.newConcurrentHashSet` 保证多线程写入安全，`throwFailureWhenFinished` 确保任一任务失败时整体失败。
- **version 文件重写并行化**（`rewriteVersionFiles` 方法）：
  原代码在 for 循环中逐个处理 version 文件路径，串行调用 `new StaticTableOperations(...)` 读取元数据并收集快照和 copy plan。
  改为：先收集所有 `versionFilePaths` 到列表，再用 `Tasks.foreach(versionFilePaths).executeWith(executorService)` 并行处理，每个任务将结果加入并发集合 `allSnapshots` 和 `allCopyPlan`，最后统一合并到 `result`。同样使用 `Sets.newConcurrentHashSet` 保证线程安全。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteTablePathsAction.java` (+24/-0 lines)

**修改目的**：验证使用 ExecutorService 时路径重写的正确性。

**工作逻辑**：
新增 `testRewritePathWithExecutorService` 测试：
- 创建一个有 50 个快照的源表。
- 创建 4 线程的固定线程池 `Executors.newFixedThreadPool(4)`。
- 调用 `actions().rewriteTablePath(sourceTable).rewriteLocationPrefix(...).startVersion("v1.metadata.json").executeWith(service).execute()`。
- 通过 `checkFileNum(50, 50, 50, 200, result)` 验证重写后的 version 文件数、manifest list 数、manifest 数和数据文件数正确。
- 在 finally 块中 `service.shutdown()` 确保线程池释放。

## 总结

本次提交为 `RewriteTablePath` Action 增加了并行元数据重写能力，通过新增 `executeWith(ExecutorService)` API 并在 Spark 实现中将 manifest list 和 version 文件两处串行重写改为基于 `Tasks` 的并行执行，显著提升了拥有大量快照的大表在路径迁移场景下的性能。改动保持向后兼容（不调用则串行），并配有针对 50 快照表的并行重写正确性测试。
