# 提交 3030：Core: Close planFiles() iterable in CatalogHandler (#14891)

## 提交信息

- **序号**：3030 / 4088
- **哈希**：2a006ba8d0f9b668efcfbc6c523c87ceddf35b94
- **短哈希**：2a006ba8d
- **日期**：2025-12-19
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Close planFiles() iterable in CatalogHandler (#14891)
- **PR/Issue**：#14891

## 总体目的

本提交修复了 `CatalogHandlers` 中服务端远程扫描计划实现的一个资源泄漏问题。`CatalogHandlers.planFilesFor()` 方法在服务端执行扫描计划时调用 `scan.planFiles()` 获取文件扫描任务的可迭代对象。`planFiles()` 返回的是 `CloseableIterable<FileScanTask>`，这意味着底层可能持有需要显式关闭的资源（如打开的文件句柄、内存中的迭代器缓冲等）。

原实现直接将 `scan.planFiles()` 的返回值赋给 `Iterable<FileScanTask> planTasks` 变量，类型被向上转型为普通 `Iterable`，丢失了 `Closeable` 语义，在整个方法执行完毕后从未调用 `close()`。虽然方法内通过 `Iterables.partition()` 消费了所有任务并放入内存状态 `IN_MEMORY_PLANNING_STATE`，但底层迭代器持有的资源不会被自动释放，长期运行可能导致文件句柄泄漏或内存占用增长。

本提交将 `planFiles()` 的返回值正确接收为 `CloseableIterable<FileScanTask>`，并使用 try-with-resources 语句包裹整个消费逻辑，确保无论正常返回还是抛异常，迭代器都会被正确关闭。

## 如何达成设计目的

改动集中在单个文件 `CatalogHandlers.java`。核心思路是将 `planFiles()` 返回值类型从 `Iterable<FileScanTask>` 改为 `CloseableIterable<FileScanTask>`，并用 try-with-resources 包裹整个任务分组的处理逻辑，在 try 块结束时自动调用 `close()`。同时新增 `java.io.IOException` 和 `CloseableIterable` 的 import，并将 try-with-resources 抛出的 `IOException` 包装为 `RuntimeException`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/CatalogHandlers.java` (+31/-26 lines)

**修改目的**：确保 `planFiles()` 返回的可关闭迭代器被正确关闭，避免资源泄漏。

**工作逻辑**：
1. 新增 import：`java.io.IOException` 和 `org.apache.iceberg.io.CloseableIterable`。
2. `planFilesFor()` 方法体中，将原来的 `Iterable<FileScanTask> planTasks = scan.planFiles();` 改为 `try (CloseableIterable<FileScanTask> planTasks = scan.planFiles()) { ... }`，整个原有的处理逻辑（空扫描处理、`Iterables.partition` 分组、循环写入 `IN_MEMORY_PLANNING_STATE`、返回 `Pair.of(initialFileScanTasks, firstPlanTaskKey)`）全部移入 try 块内。
3. 在 try 块后新增 `catch (IOException e) { throw new RuntimeException(e); }`，将 try-with-resources 自动关闭时可能抛出的 `IOException` 包装为非受检异常抛出。

这样，`CloseableIterable` 的 `close()` 方法会在 try-with-resources 退出时被调用（无论正常退出还是异常），确保底层资源被释放。需要注意的是，任务数据本身已被消费并复制到 `IN_MEMORY_PLANNING_STATE` 中，关闭迭代器不会影响已存储的计划任务数据。

## 总结

本提交通过将 `planFiles()` 返回值正确接收为 `CloseableIterable` 并使用 try-with-resources 确保其关闭，修复了服务端远程扫描计划实现中的资源泄漏隐患，属于健壮性改进，对功能行为无影响。
