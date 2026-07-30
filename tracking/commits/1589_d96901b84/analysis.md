# 提交 1589：Spark 3.4: Backport rewriting historical file-scoped deletes (#11273) to 3.4 (#11975)

## 提交信息

- **序号**：1589
- **哈希**：d96901b843395fe669f6bd4f618f8e5e46c0eed4
- **短哈希**：d96901b84
- **日期**：2025-01-15（Wed Jan 15 10:57:56 2025 -0700）
- **作者**：Amogh Jahagirdar <amoghj@apache.org>
- **提交说明**：Spark 3.4: Backport rewriting historical file-scoped deletes (#11273) to 3.4 (#11975)
- **PR/Issue**：#11975（回迁自 #11273）

## 总体目的

本提交把 main 分支上 PR #11273 引入的"重写历史 file-scoped 删除文件"能力回迁到 Spark 3.4 模块，解决 merge-on-read 模式下 file-scoped position delete 文件无限累积的问题。

背景：Iceberg 的 position delete（位置删除）以 delete file 形式记录"某数据文件的第 N 行被删除"。`delete-granularity` 属性控制 delete file 的作用范围：
- `partition`（默认）：一个 delete file 可覆盖分区内的多个数据文件。
- `file`：每个 delete file 只针对单个数据文件（file-scoped）。

在 `delete-mode = merge-on-read` + `delete-granularity = file` 的组合下，**对同一数据文件的多次 DELETE 操作会生成多个 file-scoped delete file**，每次 DELETE 都追加一个新的 delete file，而不是合并。随着 DELETE 次数增加，读路径需要在查询时把所有这些 delete file 都加载并合并（apply positions），导致读放大和元数据膨胀。例如：对某数据文件做 10 次 DELETE，就会留下 10 个 delete file，每次读取都要叠加 10 个索引。

#11273 的解决方案是：在写入新的 position delete 时，**把同一数据文件上已有的 file-scoped delete file 一起读出来，与新删除合并后写成一个新的 delete file，并把旧的 delete file 从当前快照移除**（通过 `RowDelta.removeDeletes`）。这样每个数据文件始终只保留一个（或少数）合并后的 delete file，避免累积。这就是标题中的"rewriting historical file-scoped deletes"。

本提交把这个能力回迁到 Spark 3.4，使 1.4.x / Spark 3.4 用户也能受益于 file-scoped delete 的自动合并。

## 如何达成设计目的

整体设计分两部分：
1. **读侧识别可重写的 delete file**：在 `SparkBatchQueryScan` 中新增 `rewritableDeletes()` 方法，扫描当前查询的 file scan tasks，把每个数据文件关联的、属于 file-scoped 的 delete file 收集起来，按数据文件路径分组返回 `Map<String, DeleteFileSet>`。
2. **写侧重写**：在 `SparkPositionDeltaWrite` 中，把这个映射广播到 executor，写入端用 `FanoutPositionOnlyDeleteWriter` 配合一个 `PreviousDeleteLoader` 回调，在写新 delete 时先加载该数据文件已有的 position delete 索引，合并后写出；提交时通过 `RowDelta.removeDeletes` 移除被重写的旧 delete file。

下面分别说明。

### 修改详情

#### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkBatchQueryScan.java`

**修改目的**：在读侧识别并收集可重写的 file-scoped delete file。

**工作逻辑**：新增 protected 方法
```java
protected Map<String, DeleteFileSet> rewritableDeletes() {
  Map<String, DeleteFileSet> rewritableDeletes = Maps.newHashMap();
  for (ScanTask task : tasks()) {
    FileScanTask fileScanTask = task.asFileScanTask();
    for (DeleteFile deleteFile : fileScanTask.deletes()) {
      if (ContentFileUtil.isFileScoped(deleteFile)) {
        rewritableDeletes
            .computeIfAbsent(fileScanTask.file().location(), ignored -> DeleteFileSet.create())
            .add(deleteFile);
      }
    }
  }
  return rewritableDeletes;
}
```
- 遍历所有 scan task，取每个 task 关联的 delete file 列表。
- 用 `ContentFileUtil.isFileScoped(deleteFile)` 判断该 delete file 是否只针对单个数据文件（file-scoped）。只有 file-scoped 的 delete file 才能安全重写——partition-scoped 的 delete file 跨多个数据文件，不能在单数据文件维度合并。
- 按数据文件 location 分组存入 `DeleteFileSet`。结果是"数据文件路径 → 该文件上所有 file-scoped delete file 集合"的映射。

#### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeltaWrite.java`

**修改目的**：在写侧实现重写逻辑，并把被重写的旧 delete file 从快照移除。

**主要变更**：

1. **广播可重写 delete file**：
   `createBatchWriterFactory` 改造，新增 `broadcastRewritableDeletes()`：
   ```java
   private Broadcast<Map<String, DeleteFileSet>> broadcastRewritableDeletes() {
     if (context.deleteGranularity() == DeleteGranularity.FILE && scan != null) {
       Map<String, DeleteFileSet> rewritableDeletes = scan.rewritableDeletes();
       if (rewritableDeletes != null && !rewritableDeletes.isEmpty()) {
         return sparkContext.broadcast(rewritableDeletes);
       }
     }
     return null;
   }
   ```
   只有当 `delete-granularity = file` 且 scan 存在且确有可重写 delete file 时才广播，避免无谓开销。`PositionDeltaWriteFactory` 新增 `rewritableDeletesBroadcast` 字段并透传给各 writer。

2. **写入选用 Fanout writer + PreviousDeleteLoader**：
   `BaseDeltaWriter.newDeleteWriter` 的分支逻辑改变：
   ```java
   if (inputOrdered && rewritableDeletes == null) {
     return new ClusteredPositionDeleteWriter<>(...);          // 原有路径，无重写
   } else {
     return new FanoutPositionOnlyDeleteWriter<>(
         writers, files, io, targetFileSize, deleteGranularity,
         rewritableDeletes != null
             ? new PreviousDeleteLoader(table, rewritableDeletes)
             : path -> null /* no previous file scoped deletes */);
   }
   ```
   - 当存在可重写 delete file 时，**强制走 Fanout writer**（即使 input 有序），因为重写需要按数据文件路径逐个加载旧 delete 索引，Fanout writer 支持这种乱序写入。
   - `FanoutPositionOnlyDeleteWriter` 新增一个 `Function<CharSequence, PositionDeleteIndex>` 参数：在写某个数据文件的 delete 时，调用此函数获取该文件已有的 position delete 索引，与本次新 delete 合并后写出，从而把新旧 delete 合并到一个新文件。

3. **`PreviousDeleteLoader`**：
   ```java
   private static class PreviousDeleteLoader implements Function<CharSequence, PositionDeleteIndex> {
     PreviousDeleteLoader(Table table, Map<String, DeleteFileSet> deleteFiles) {
       this.deleteFiles = deleteFiles;
       this.deleteLoader = new BaseDeleteLoader(
           deleteFile -> EncryptingFileIO.combine(table.io(), table.encryption()).newInputFile(deleteFile));
     }
     @Override
     public PositionDeleteIndex apply(CharSequence path) {
       DeleteFileSet deleteFileSet = deleteFiles.get(path.toString());
       if (deleteFileSet == null) return null;
       return deleteLoader.loadPositionDeletes(deleteFileSet, path);
     }
   }
   ```
   - 它是 executor 端的回调：给定数据文件路径，从广播的 map 取该文件的旧 delete file 集合，用 `BaseDeleteLoader` 加载并返回 `PositionDeleteIndex`（已删除位置索引）。
   - `EncryptingFileIO.combine` 处理加密表的 delete file 读取。
   - writer 拿到旧索引后，会把本次新 delete 的位置并进去，写出一个涵盖新旧所有删除位置的合并 delete file。

4. **`DeltaTaskCommit` 新增 `rewrittenDeleteFiles`**：
   - 新增字段 `DeleteFile[] rewrittenDeleteFiles`，记录本任务重写（即合并后替代）的旧 delete file。
   - 从 `WriteResult.rewrittenDeleteFiles()` 或 `DeleteWriteResult.rewrittenDeleteFiles()` 取得。这些是底层 writer 产出的"被重写的旧文件"列表。
   - 新增访问器 `rewrittenDeleteFiles()`。

5. **`commit` 阶段移除旧 delete file**：
   ```java
   for (DeleteFile deleteFile : taskCommit.rewrittenDeleteFiles()) {
     rowDelta.removeDeletes(deleteFile);
     removedDeleteFilesCount += 1;
   }
   ```
   - 对每个被重写的旧 delete file，调用 `RowDelta.removeDeletes` 把它从本次 commit 的 delete 集合中移除，使其不在新快照中引用——相当于"删除掉被合并的旧 delete file"。
   - 新增计数 `removedDeleteFilesCount`，写入 commit 日志消息：`"position delta with %d data files, %d delete files and %d rewritten delete files(...)"`，便于排查。

6. **各 writer 构造函数透传 `rewritableDeletes`**：
   `DeleteOnlyDeltaWriter`、`DeleteAndDataDeltaWriter`、`UnpartitionedDeltaWriter`、`PartitionedDeltaWriter` 的构造函数均新增 `Map<String, DeleteFileSet> rewritableDeletes` 参数，并传给 `newDeleteWriter`。这样无论 DELETE 还是 UPDATE，无论分区表还是非分区表，都支持重写。

#### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMergeOnReadDelete.java`

**修改目的**：验证 DELETE 场景下 file-scoped delete 被重写。

新增两个测试：
- `testPositionDeletesAreMaintainedDuringDelete`（分区表，`PARTITIONED BY (id)`）
- `testUnpartitionedPositionDeletesAreMaintainedDuringDelete`（非分区表）

两个测试结构相同：
1. 建 v2 表，`delete-mode=merge-on-read`，`delete-granularity=file`。
2. 写入 5 条记录（`(1,a),(1,b),(1,c),(2,d),(2,e)`），`coalesce(1)` 确保每个分区单文件。
3. 执行 3 次 DELETE：删 `(1,a)`、`(2,d)`、`(1,c)`。注意 `(1,a)` 和 `(1,c)` 命中同一数据文件（id=1 分区），会产生对同一文件的两次 delete。
4. 断言 `latest.removedDeleteFiles(table.io()).hasSize(1)`——即第二次 DELETE id=1 时，把第一次 DELETE 产生的 file-scoped delete file 重写合并，旧的那一个被移除。
5. 断言剩余行为 `(1,b)` 和 `(2,e)`，验证正确性。

#### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMergeOnReadUpdate.java`

**修改目的**：验证 UPDATE 场景下 delete file 被合并。

**主要变更**：
1. 把原 `checkUpdateFileGranularity` 中的建表/插数据逻辑抽出为 `initTable(partitionedBy, deleteGranularity)` 辅助方法，便于复用。
2. 新增 `testUpdateFileGranularityMergesDeleteFiles`（分区表）：
   - `assumeThat(distributionMode).isNotEqualToIgnoringCase("range")`——range 分布会产生 partition-scoped delete，不会被清理，跳过。
   - `checkUpdateFileGranularity(DeleteGranularity.FILE)` 先做一次 UPDATE 产生 delete file。
   - 再 `UPDATE ... WHERE id = 4`，触发对已有 delete file 的重写。
   - 断言 `currentSnapshot.removedDeleteFiles(table.io()).hasSize(2)`（两个分区各重写一个），并验证最终行数据正确。
3. 新增 `testUpdateUnpartitionedFileGranularityMergesDeleteFiles`（非分区表）：
   - 类似地，先 UPDATE 产生 delete file，再 UPDATE 触发重写。
   - 断言 `removedDeleteFiles` 数量与数据正确性。
   - 用 `validateMergeOnRead(snapshot, addedData, addedDeletes, removedDeletes)` 验证快照元数据计数。

## 小结

- **成效**：Spark 3.4 的 merge-on-read + file-granularity 场景下，对同一数据文件的多次 DELETE/UPDATE 不再累积 file-scoped delete file，而是自动合并重写，旧 delete file 被移除。这显著减少了读放大（每次读取只需 apply 一个合并后的 delete 索引而非多个）和元数据膨胀，提升查询性能与存储效率。
- **影响范围**：Spark 3.4 模块的 `SparkBatchQueryScan`、`SparkPositionDeltaWrite` 两个生产类，以及对应测试。变更涉及写入路径的核心逻辑（writer 选择、广播、commit 移除），但仅在 `delete-granularity=file` 时启用，其他配置下行为不变（`rewritableDeletes` 为 null，走原 Clustered writer 路径），向后兼容。
- **回迁到 1.4.x 的注意事项**：本提交**本身就是向 Spark 3.4 的回迁**（从 main 的 #11273 回迁），因此天然适合 1.4.x（1.4.x 主打 Spark 3.4/3.5）。**强烈建议回迁到 1.4.x**，因为这是性能与存储优化的关键能力。需注意：
  1. 依赖底层 core 模块的 `DeleteFileSet`、`ContentFileUtil.isFileScoped`、`BaseDeleteLoader`、`FanoutPositionOnlyDeleteWriter`（带 PreviousDeleteLoader 回调的重载）、`WriteResult.rewrittenDeleteFiles()` / `DeleteWriteResult.rewrittenDeleteFiles()` 等基础能力。这些是 #11273 在 main 上引入的，1.4.x 需先确认这些 core 基础设施已回迁，否则本提交无法独立工作。换言之，回迁需以 #11273 的 core 部分已落地为前提。
  2. 测试依赖 `Snapshot.removedDeleteFiles(io)` API，需确认 1.4.x 已有。
  3. 若 1.4.x 的 Spark 3.4 写入路径结构与 main 有差异，需手工对齐。
