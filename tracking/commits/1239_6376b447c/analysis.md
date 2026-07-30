# 提交 1239：Spark 3.3, 3.4, 3.5: Remove unnecessary copying of FileScanTask (#11319)

## 提交信息

- **序号**：1239 / 4088
- **哈希**：6376b447c9b18dc9f0b72dc8dba46d78ec655049
- **短哈希**：6376b447c
- **日期**：2024-10-15（Tue Oct 15 02:59:10 2024 -0700）
- **作者**：Huaxin Gao <huaxin.gao11@gmail.com>
- **提交说明**：Spark 3.3, 3.4, 3.5: Remove unnecessary copying of FileScanTask (#11319)
- **PR/Issue**：#11319

## 总体目的

在 Spark 模块的 `SparkScanBuilder` 中，决定是否下推聚合（aggregate pushdown）前需要扫描 `planFiles()` 返回的所有 `FileScanTask`，检查每个 task 是否携带 deletes（行级删除文件）。如果有 deletes，则跳过聚合下推（`LOG.info("Skipping aggregate pushdown: detected row level deletes"); return false;`）。

原代码在 try-with-resources 中先把 `CloseableIterable<FileScanTask>` 完整复制到一个 `ImmutableList<FileScanTask> tasks`，然后再遍历这个 list。这种"先物化再遍历"的方式有两个问题：

1. 多一次内存拷贝：把所有 FileScanTask 复制到 list 中，仅仅为了遍历一次。
2. 物化时如果 `planFiles()` 是惰性迭代器，会强制把所有 task 都生成出来，而实际上只要遇到第一个带 deletes 的 task 就可以立即 `return false`，无需继续遍历。

本提交把三个 Spark 版本（3.3 / 3.4 / 3.5）的 `SparkScanBuilder` 中这段代码统一改为直接遍历 `CloseableIterable<FileScanTask>`，移除 `ImmutableList.copyOf(fileScanTasks)` 这一步。这样在遇到第一个含 deletes 的 task 时即可提前 return，避免不必要的物化与拷贝，提升聚合下推判断的效率，尤其是大表场景下 FileScanTask 数量较多时。

## 如何达成设计目的

在 `SparkScanBuilder` 的相关方法中：

```java
try (CloseableIterable<FileScanTask> fileScanTasks = scan.planFiles()) {
-  List<FileScanTask> tasks = ImmutableList.copyOf(fileScanTasks);
-  for (FileScanTask task : tasks) {
+  for (FileScanTask task : fileScanTasks) {
     if (!task.deletes().isEmpty()) {
       LOG.info("Skipping aggregate pushdown: detected row level deletes");
       return false;
     }
   }
}
```

同时移除不再使用的 `ImmutableList` import。`CloseableIterable` 实现了 `Iterable`，可直接用于增强 for 循环；try-with-resources 仍保证资源关闭。

## 修改详情

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java`

**修改目的**：移除聚合下推检测中的 FileScanTask 物化拷贝。

**工作逻辑**：在判断聚合下推的方法中（`planFiles()` 后），把 `ImmutableList.copyOf(fileScanTasks)` + `for (task : tasks)` 改为直接 `for (task : fileScanTasks)`；移除 `import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java`

**修改目的**：同 v3.3。

**工作逻辑**：与 v3.3 完全相同的改动。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java`

**修改目的**：同 v3.3。

**工作逻辑**：与 v3.3 完全相同的改动。

## 小结

- **成效**：Spark 3.3 / 3.4 / 3.5 的 `SparkScanBuilder` 在聚合下推可行性判断时不再物化整个 FileScanTask 列表，改为流式遍历 `CloseableIterable`，遇首个含 deletes 的 task 即提前 return，减少不必要的内存分配与遍历，对大表场景有性能改善。
- **影响范围**：仅三个 Spark 版本的 `SparkScanBuilder.java`，每个文件 3 行改动（删 2 行、改 1 行）；无公共 API 或行为变化，纯性能优化。
- **回迁到 1.4.x 的注意事项**：
  - 这是性能优化且改动极小，建议回迁。
  - 1.4.x 通常维护 Spark 3.3 / 3.4 / 3.5 三个版本（更老的 1.4.x 早期可能还有 Spark 3.2），三个版本的 `SparkScanBuilder` 代码结构应与 main 一致，回迁时直接套用即可。
  - 注意 1.4.x 中若 Spark 3.2 仍存在，应同步检查该版本的 `SparkScanBuilder` 是否有同样问题，一并优化（本提交未覆盖 3.2，因为 main 已不再维护 3.2）。
  - 回迁后无需新增测试（行为不变，原有聚合下推测试已覆盖）。
