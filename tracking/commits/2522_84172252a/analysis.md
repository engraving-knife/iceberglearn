# 提交 2522：Add planWith to FindFiles to leverage ParallelIterable (#13836)

## 提交信息

- **序号**：2522 / 4088
- **哈希**：84172252a1f2537549e52b174299cbf715138760
- **短哈希**：84172252a
- **日期**：2025-08-18 14:18:45 -0700
- **作者**：Filipe Regadas
- **提交说明**：Add planWith to FindFiles to leverage ParallelIterable (#13836)
- **PR/Issue**：#13836

## 总体目的

此提交为 `FindFiles` API 添加了 `planWith(ExecutorService)` 方法，使其能够利用并行迭代器（ParallelIterable）来并行化文件规划操作，从而提高大表中文件查找的性能。

`FindFiles` 是 Iceberg Core 模块中用于在表中查找数据文件的 API。它通过构建器模式（Builder）允许用户指定行过滤器、文件过滤器、分区过滤器等条件来查找匹配的数据文件。在底层，`FindFiles` 通过 `ManifestGroup` 来扫描清单文件并获取匹配的数据文件条目。

当表有大量分区和清单文件时，文件规划（planning）操作可能成为性能瓶颈。`ManifestGroup` 已经支持通过 `planWith(ExecutorService)` 方法使用线程池并行扫描清单文件，但 `FindFiles` 之前没有暴露这一能力，导致文件查找始终是单线程的。

## 如何达成设计目的

设计方案在 `FindFiles.Builder` 中添加 `ExecutorService` 字段和对应的 `planWith` 方法，并在 `collect()` 方法中将该执行器传递给底层的 `ManifestGroup`。

具体设计要点：
1. 在 `FindFiles.Builder` 类中添加 `private ExecutorService executorService` 字段
2. 添加 `planWith(ExecutorService)` 方法，允许用户设置并行执行器
3. 在 `collect()` 方法中，调用 `manifestGroup.planWith(executorService)` 将执行器传递给清单组扫描

## 修改详情

### `core/src/main/java/org/apache/iceberg/FindFiles.java` (+8/-0 lines)

**修改目的**：添加 `planWith` 方法和执行器支持。

**工作逻辑**：
1. 导入 `java.util.concurrent.ExecutorService`
2. 在 `Builder` 类中添加 `executorService` 字段
3. 添加 `planWith(ExecutorService newExecutorService)` 方法，设置执行器并返回 `this` 以支持链式调用
4. 在 `collect()` 方法中，在构建 `ManifestGroup` 时调用 `.planWith(executorService)`，将执行器传递给清单组，使清单扫描可以并行执行

### `core/src/test/java/org/apache/iceberg/TestFindFiles.java` (+26/-0 lines)

**修改目的**：添加 `testPlanWith` 测试用例验证并行执行器功能。

**工作逻辑**：
1. 创建包含 4 个文件（FILE_A, FILE_B, FILE_C, FILE_D）的表
2. 创建一个固定大小为 2 的线程池
3. 使用 `FindFiles.in(table).planWith(executorService)` 查找文件
4. 验证返回的文件集合包含所有 4 个文件
5. 在 `finally` 块中关闭线程池，确保资源释放

## 总结

此提交为 FindFiles API 添加了并行文件规划能力，使大表的文件查找操作可以利用多线程加速。这与 `ManifestGroup` 已有的并行扫描能力对齐，为需要批量查找文件的高性能场景提供了更好的支持。设计简洁、向后兼容，未设置执行器时行为与之前一致。
