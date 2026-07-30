# 提交 3788：Flink: Fix flaky TestMonitorSource.testStateRestore (#16548)

## 提交信息

- **序号**：3788 / 4088
- **哈希**：8629e7c2de5bf36dcdb5a5a077d5eaaea05e4a2e
- **短哈希**：8629e7c2d
- **日期**：2026-05-27 16:20:21 +0200
- **作者**：Vova Kolmakov
- **提交说明**：Flink: Fix flaky TestMonitorSource.testStateRestore (#16548)
- **PR/Issue**：#16548

## 总体目的

这个提交修复了 `TestMonitorSource.testStateRestore` 测试的 flaky（不稳定）问题。该测试在停止 Flink 作业并创建 savepoint 后，从 savepoint 恢复作业状态。问题在于原来的实现使用了不可靠的等待机制来检测 savepoint 是否创建完成。

原来的代码调用 `jobClient.stopWithSavepoint(...)` 后，使用 `Awaitility.await().until(() -> savepointDir.listFiles(File::isDirectory).length == 1)` 来等待 savepoint 目录出现。这种方式存在竞态条件：
1. `stopWithSavepoint` 返回的是 `CompletableFuture`，但原代码没有等待它完成。
2. 通过文件系统轮询来检测 savepoint 创建完成不可靠，可能在 savepoint 完全写入前就检测到目录存在，导致从 savepoint 恢复时竞态。

## 如何达成设计目的

使用 `jobClient.stopWithSavepoint(...).get()` 同步等待 savepoint 创建完成并获取其路径。`get()` 方法会阻塞直到 future 完成，返回 savepoint 的完整路径，确保 savepoint 已完全写入后再从其恢复。

## 修改详情

### `flink/v1.20/flink/src/test/java/.../OperatorTestBase.java` (+12/-5 lines)

**修改目的**：修复 savepoint 创建的等待逻辑。

**工作逻辑**：
将原来的文件系统轮询方式：
```java
jobClient.stopWithSavepoint(false, savepointDir.getPath(), SavepointFormatType.CANONICAL);
Awaitility.await().until(() -> savepointDir.listFiles(File::isDirectory).length == 1);
conf.set(SavepointConfigOptions.SAVEPOINT_PATH,
    savepointDir.listFiles(File::isDirectory)[0].getAbsolutePath());
```
改为同步等待 future 完成：
```java
try {
  conf.set(SavepointConfigOptions.SAVEPOINT_PATH,
      jobClient.stopWithSavepoint(false, savepointDir.getPath(), SavepointFormatType.CANONICAL).get());
} catch (InterruptedException | ExecutionException e) {
  throw new RuntimeException(e);
}
```

`.get()` 阻塞直到 savepoint 完全写入并返回其路径，消除了竞态条件。

### `flink/v2.0/flink/src/test/java/.../OperatorTestBase.java` (+9/-4 lines)
### `flink/v2.1/flink/src/test/java/.../OperatorTestBase.java` (+9/-4 lines)

**修改目的**：对 v2.0 和 v2.1 应用相同的修复。

**工作逻辑**：与 v1.20 相同的修改，使用 `.get()` 同步等待 savepoint 完成并返回路径。

## 总结

这个提交通过使用 `CompletableFuture.get()` 同步等待 savepoint 创建完成，修复了 `TestMonitorSource.testStateRestore` 测试中的竞态条件。原来的文件系统轮询方式可能在 savepoint 完全写入前就检测到目录存在，导致从 savepoint 恢复时失败。修改覆盖了 Flink v1.20、v2.0 和 v2.1 三个版本。
