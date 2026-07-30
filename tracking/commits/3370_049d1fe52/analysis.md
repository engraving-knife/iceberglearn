# 提交 3370：Spark: Backport: Add ExecutorService support to RewriteTablePath (#15578)

## 提交信息

- **序号**：3370 / 4088
- **哈希**：049d1fe5207b085d7e1b3e122ed783ef361ed91e
- **短哈希**：049d1fe52
- **日期**：2026-03-10
- **作者**：Maximilian Michels
- **提交说明**：Spark: Backport: Add ExecutorService support to RewriteTablePath (#15578)
- **PR/Issue**：#15578（回移自 #15381）

## 总体目的

这是一个回移（backport）提交，将 PR #15381 的功能回移到 Spark 3.4、3.5 和 4.0 三个版本模块。原 PR 的目标是为 `RewriteTablePathSparkAction` 增加 `ExecutorService` 支持，使表路径重写过程中的清单列表（manifest list）重写和版本文件（version file）重写可以并行执行，从而提升大规模表的重写性能。

`RewriteTablePath` 是 Iceberg 中用于迁移表存储路径的 Spark Action，需要重写快照的清单列表文件、清单文件、位置删除文件以及历史版本文件。此前这些重写操作都是串行的，对于拥有大量快照和版本文件的大表来说，串行处理耗时长、效率低。通过引入 `ExecutorService`，可以将这些独立的重写任务并行化，显著缩短执行时间。

## 如何达成设计目的

改动覆盖 Spark 3.4、3.5 和 4.0 三个模块，每个模块修改 `RewriteTablePathSparkAction`（实现）和 `TestRewriteTablePathsAction`（测试）两个文件。核心设计：新增 `executeWith(ExecutorService)` 方法允许用户传入线程池；将原来用 stream 串行处理的清单列表重写改为使用 `Tasks.foreach().executeWith(executorService)` 并行处理；将原来在 for 循环中串行处理的版本文件重写也改为并行处理。使用 `Sets.newConcurrentHashSet()` 收集并行结果以保证线程安全。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java` (+51/-15 lines)、`spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java` (+51/-15 lines)、`spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java` (+51/-15 lines)

**修改目的**：为 `RewriteTablePath` Action 增加 `ExecutorService` 支持以并行执行重写任务。

**工作逻辑**：
三个文件的改动完全一致，主要包括：
1. 新增 `import java.util.concurrent.ExecutorService` 和 `import org.apache.iceberg.util.Tasks`，新增字段 `private ExecutorService executorService`。
2. 新增 `executeWith(ExecutorService service)` 方法实现 `RewriteTablePath` 接口的方法，将传入的线程池保存到字段中并返回 `this`。
3. 清单列表重写并行化：原来用 `validSnapshots.stream().map(...).reduce(...)` 串行处理，改为：
```java
Set<RewriteResult<ManifestFile>> manifestListResults = Sets.newConcurrentHashSet();
Tasks.foreach(validSnapshots)
    .noRetry().throwFailureWhenFinished()
    .executeWith(executorService)
    .run(snapshot -> manifestListResults.add(rewriteManifestList(snapshot, endMetadata, manifestsToRewrite)));
```
   然后将所有结果合并。使用并发集合保证线程安全。
4. 版本文件重写并行化：原来在 for 循环中逐个处理 `versionFilePath`，先收集到 `List<String> versionFilePaths`，再使用 `Tasks.foreach(versionFilePaths).executeWith(executorService).run(...)` 并行处理每个版本文件，结果收集到 `Sets.newConcurrentHashSet()` 中。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteTablePathsAction.java` (+24/-0 lines)、`spark/v3.5/.../TestRewriteTablePathsAction.java` (+24/-0 lines)、`spark/v4.0/.../TestRewriteTablePathsAction.java` (+24/-0 lines)

**修改目的**：验证使用 `ExecutorService` 时表路径重写的正确性。

**工作逻辑**：
三个文件的改动一致，新增 `testRewritePathWithExecutorService` 测试方法。测试创建一个有 50 个快照的表，创建一个固定大小为 4 的线程池 `Executors.newFixedThreadPool(4)`，调用 `rewriteTablePath().rewriteLocationPrefix(...).startVersion("v1.metadata.json").executeWith(service).execute()`，最后通过 `checkFileNum(50, 50, 50, 200, result)` 验证重写后的文件数量正确。在 finally 中关闭线程池。

## 总结

本次回移为 Spark 3.4/3.5/4.0 的 `RewriteTablePath` Action 增加了 `ExecutorService` 支持，将清单列表重写和版本文件重写从串行改为并行，显著提升大表路径迁移的性能。改动设计合理，使用并发集合保证线程安全，并通过测试验证了正确性，对大规模表的运维操作有直接价值。
