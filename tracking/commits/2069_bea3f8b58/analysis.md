# 提交 2069：Core: Broaden exception handling in writer clean up logic

## 提交信息

- **序号**：2069 / 4088
- **哈希**：bea3f8b58cebe1458d8edb2172287cae0a04cb38
- **短哈希**：bea3f8b58
- **日期**：2025-05-01 07:59:46 -0600
- **作者**：Xiaoxuan
- **提交说明**：Core: Broaden exception handling in writer clean up logic (#12863)
- **PR/Issue**：#12863

## 总体目的

在 Iceberg 的任务写入器（TaskWriter）清理逻辑中，当一个写入的文件最终没有任何数据行（即 `currentRows == 0L` 或 `currentFileRows == 0L`）时，会尝试删除该空文件以避免提交无意义的数据文件。原有的清理逻辑只捕获 `UncheckedIOException`，意味着只有当底层 FileIO 抛出该特定异常时才会跳过删除失败。然而在实际场景中，删除文件可能会因为多种原因失败，例如底层存储返回了其他类型的异常（如 S3 的 503 慢响应、网络超时、权限错误等），这些异常并非 `UncheckedIOException` 的子类。

如果删除失败时抛出了非 `UncheckedIOException` 的异常，会导致整个写入任务失败，即便该文件只是一个未被提交的空文件——这显然是不合理的，因为清理未提交的空文件不应影响作业的成功完成。

本提交将异常处理范围扩大：使用 Iceberg 内置的 `Tasks` 工具类来执行删除操作，并通过 `suppressFailureWhenFinished()` 在任务完成时抑制失败，同时通过 `onFailure` 回调以 WARN 级别记录日志，便于排查问题但不中断主流程。

## 如何达成设计目的

设计思路是在两个写入器类中统一采用 `Tasks.foreach(...).suppressFailureWhenFinished().onFailure(...).run(...)` 的模式来执行文件删除。关键组件协作关系如下：

- `Tasks`：Iceberg 工具类，提供重试、失败抑制、失败回调等能力。
- `io.deleteFile`：底层的 FileIO 删除操作，作为 `Tasks.foreach().run()` 的执行体。
- `onFailure` 回调：使用 SLF4J Logger 以 WARN 级别记录删除失败的具体文件和异常，便于运维排查。
- `suppressFailureWhenFinished()`：确保即使删除失败，外层写入流程也不会被中断。

## 修改详情

### `core/src/main/java/org/apache/iceberg/io/BaseTaskWriter.java` (修改, +17/-8 lines)

**修改目的**：扩大 `BaseTaskWriter` 中清理空文件时的异常处理范围，避免因删除未提交的空文件失败而导致整个写入任务失败。

**工作逻辑**：
- 新增 SLF4J Logger 静态字段，用于记录删除失败警告。
- 移除对 `java.io.UncheckedIOException` 的 import，因为不再单独捕获该异常。
- 在 `currentRows == 0L` 分支中，将原来的 `try { io.deleteFile(...) } catch (UncheckedIOException e) { ... }` 替换为 `Tasks.foreach(currentFile.encryptingOutputFile()).suppressFailureWhenFinished().onFailure((file, exc) -> LOG.warn(...)).run(io::deleteFile)`。这样无论删除过程中抛出什么异常，都会被 Tasks 框架捕获并以 WARN 日志记录，而不会向上抛出。

### `core/src/main/java/org/apache/iceberg/io/RollingFileWriter.java` (修改, +16/-6 lines)

**修改目的**：与 `BaseTaskWriter` 保持一致，扩大 `RollingFileWriter` 中清理空文件时的异常处理范围。

**工作逻辑**：
- 新增 `Tasks` 和 SLF4J Logger 的 import，并添加 Logger 静态字段。
- 在 `currentFileRows == 0L` 分支中，同样将原来的 `try/catch(UncheckedIOException)` 替换为 `Tasks.foreach(...).suppressFailureWhenFinished().onFailure(...).run(io::deleteFile)` 模式，使用相同的 WARN 日志格式记录失败信息。

## 总结

本提交将写入器清理未提交空文件的异常处理从仅捕获 `UncheckedIOException` 扩大为捕获所有异常，使用 `Tasks` 工具类的失败抑制和回调机制，确保删除失败只记录 WARN 日志而不中断写入任务。修改涉及 `BaseTaskWriter` 和 `RollingFileWriter` 两个类，改动模式一致，提升了写入流程在底层存储不稳定情况下的鲁棒性。
