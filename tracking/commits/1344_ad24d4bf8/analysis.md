# 提交 1344：Spark: Synchronously merge new position deletes with old deletes (#11273)

## 提交信息

- **序号**：1344 / 4088
- **哈希**：ad24d4bf85cd43e1f6337dd4b38fbe2b0e3ffbf4
- **短哈希**：ad24d4bf8
- **日期**：2024-11-05（Tue Nov 5 11:15:49 2024 -0700）
- **作者**：Amogh Jahagirdar <amoghj@apache.org>
- **提交说明**：Spark: Synchronously merge new position deletes with old deletes (#11273)
- **PR/Issue**：#11273

## 总体目的

Iceberg 的 `merge-on-read` 删除模式下，每次 DELETE/UPDATE 都会产出一批 position delete 文件，扫描时数据文件要同时应用所有历史 position delete 文件。如果不及时合并，同一数据文件上会堆积大量小删除文件，导致扫描时 I/O 与打开文件数线性增长、性能下降。

此前社区在 core 层已经支持"写入时合并 position deletes"（#11222 `c8fe01e71`）：`FanoutPositionOnlyDeleteWriter`/`SortingPositionOnlyDeleteWriter` 接受一个 `loadPreviousDeletes` 函数，按数据文件路径加载旧的位置删除索引，写入时把新旧行号合并到同一个输出文件中，并把被替代的旧删除文件作为 `rewrittenDeleteFiles` 返回，由上层在 commit 时通过 `removeDeletes` 移除。

本提交把这一能力接入 Spark 3.5 的 `SparkPositionDeltaWrite`，使 Spark 在执行 DELETE/UPDATE（position delta 写入）时，能够"同步"地把同一数据文件上已有的、文件级粒度（file-scoped）的旧 position deletes 与本次新增的 position deletes 合并，并在同一次 commit 中移除旧文件，避免小删除文件累积。这相当于把"rewrite position deletes"这一原本需要独立触发（如 `rewrite_position_deletes` 存储过程）的维护操作，下沉到普通的 DELETE/UPDATE 流程中同步完成。

## 如何达成设计目的

- **入口条件**：仅当 `context.deleteGranularity() == DeleteGranularity.FILE` 且 Spark 侧持有 `scan`（即数据写入伴随读取的场景，如 row-level delete/update）时才启用合并。
- **收集可重写删除**：在 `SparkBatchQueryScan` 新增 `rewritableDeletes()` 方法，遍历所有 scan task 收集每个数据文件路径对应的、`ContentFileUtil.isFileScoped` 的旧 position delete 文件，组织成 `Map<String, DeleteFileSet>`。
- **广播到 executor**：driver 端通过 `sparkContext.broadcast(...)` 把这张表广播给所有 task，避免每个 task 重复序列化。
- **按数据文件加载旧索引**：在 `PositionDeltaWriteFactory` 中通过 `PreviousDeleteLoader`（实现 `Function<CharSequence, PositionDeleteIndex>`）封装：当 writer 询问某数据文件路径的旧索引时，从 `DeleteFileSet` 取出该路径的旧删除文件，用 `BaseDeleteLoader` 加载并返回 `PositionDeleteIndex`。
- **强制走 fanout writer**：`BaseDeltaWriter.newDeleteWriter` 中，原本"输入有序则用 ClusteredPositionDeleteWriter，否则用 FanoutPositionOnlyDeleteWriter"的分支改为：当存在 rewritableDeletes 时强制走 `FanoutPositionOnlyDeleteWriter`（因为合并需要 fanout writer 的合并能力），否则保持原逻辑。
- **commit 时移除旧文件**：`DeltaTaskCommit` 新增 `rewrittenDeleteFiles` 字段（来自 `DeleteWriteResult.rewrittenDeleteFiles()`），driver 端 `commit` 时遍历所有 task 的 `rewrittenDeleteFiles`，调用 `rowDelta.removeDeletes(deleteFile)` 将旧删除文件标记为移除，并在 commit 日志中打印 `rewritten delete files` 计数。
- **工具方法下沉**：把原本 `SortingPositionOnlyDeleteWriter` 私有的 `isFileScoped` 判断提到 `ContentFileUtil.isFileScoped`，便于 `SparkBatchQueryScan` 复用。

## 修改详情

### `core/src/main/java/org/apache/iceberg/deletes/SortingPositionOnlyDeleteWriter.java`

**修改目的**：把 `isFileScoped` 私有方法下沉到工具类，避免重复实现。

**工作逻辑**：
- 删除私有方法 `private boolean isFileScoped(DeleteFile deleteFile)`。
- `validatePreviousDeletes` 改为引用 `ContentFileUtil::isFileScoped` 方法引用，语义不变：所有 previous deletes 必须是 file-scoped（即 `referencedDataFile` 非空）。

### `core/src/main/java/org/apache/iceberg/util/ContentFileUtil.java`

**修改目的**：暴露 `isFileScoped` 工具方法供 Spark 复用。

**工作逻辑**：
```java
public static boolean isFileScoped(DeleteFile deleteFile) {
  return referencedDataFile(deleteFile) != null;
}
```
即一个删除文件若带有 `referencedDataFile`（指向具体数据文件），则视为 file-scoped，可被合并。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkBatchQueryScan.java`

**修改目的**：从扫描任务中收集可被重写的 file-scoped position deletes。

**工作逻辑**：
- 新增 import `DeleteFile`、`FileScanTask`、`ContentFileUtil`、`DeleteFileSet`。
- 新增 `protected Map<String, DeleteFileSet> rewritableDeletes()`：
  ```java
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
  ```
  即按数据文件路径聚合所有 file-scoped 删除文件。该方法位于基类，便于其他 Spark 扫描实现复用。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeltaWrite.java`

**修改目的**：把可重写删除广播到 executor，并在 commit 时移除被合并掉的旧删除文件。

**工作逻辑**：

1. **广播 rewritableDeletes**：`PositionDeltaWriteBuilder.createBatchWriterFactory(...)` 改为：
   ```java
   return new PositionDeltaWriteFactory(
       sparkContext.broadcast(SerializableTableWithSize.copyOf(table)),
       broadcastRewritableDeletes(),
       command, context, writeProperties);
   ```
   `broadcastRewritableDeletes()` 仅在 `deleteGranularity == FILE && scan != null` 且 `rewritableDeletes` 非空时广播，否则返回 null。

2. **PreviousDeleteLoader 内部类**：实现 `Function<CharSequence, PositionDeleteIndex>`，封装按路径加载旧位置删除索引的逻辑：
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
   使用 `EncryptingFileIO.combine` 确保读取加密表时也能正确解密旧删除文件。

3. **BaseDeltaWriter.newDeleteWriter** 改造：
   ```java
   if (inputOrdered && rewritableDeletes == null) {
     return new ClusteredPositionDeleteWriter<>(...);
   } else {
     return new FanoutPositionOnlyDeleteWriter<>(
         writers, files, io, targetFileSize, deleteGranularity,
         rewritableDeletes != null ? new PreviousDeleteLoader(table, rewritableDeletes) : path -> null);
   }
   ```
   即存在 rewritableDeletes 时强制走 fanout writer 并注入 loader。

4. **DeltaTaskCommit 新增 rewrittenDeleteFiles**：
   - 新增 `DeleteFile[] rewrittenDeleteFiles` 字段及访问方法。
   - 两个构造函数分别从 `WriteResult.rewrittenDeleteFiles()` 与 `DeleteWriteResult.rewrittenDeleteFiles()` 取值（前者和后者均在 #11222 中已支持）。

5. **PositionDeltaWriteFactory** 新增 `rewritableDeletesBroadcast` 字段；`createWriter(int partitionId, long taskId)` 把 `rewritableDeletes()` 传给三种 writer（DeleteOnly/Unpartitioned/Partitioned）。

6. **DeltaWriter 子类构造**：`DeleteOnlyDeltaWriter`、`DeleteAndDataDeltaWriter`、`UnpartitionedDeltaWriter`、`PartitionedDeltaWriter` 均新增 `rewritableDeletes` 参数，沿调用链传到 `newDeleteWriter`。

7. **commit 阶段移除旧文件**：`PositionDeltaWriteBuilder.commit(...)` 中遍历所有 `DeltaTaskCommit.rewrittenDeleteFiles()`，调用 `rowDelta.removeDeletes(deleteFile)`，并维护 `removedDeleteFilesCount`，最终在 commit 日志中输出 `... and N rewritten delete files ...`。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMergeOnReadDelete.java`

**修改目的**：验证 DELETE 操作下旧 position delete 文件被同步合并移除。

**工作逻辑**：
- 新增 `testPositionDeletesAreMaintainedDuringDelete`（分区表）和 `testUnpartitionedPositionDeletesAreMaintainedDuringDelete`（非分区表）：建表时设置 `DELETE_MODE=merge-on-read`、`DELETE_GRANULARITY=file`，连续执行 3 次 DELETE（其中两次作用于同一文件 path 的不同行），断言最新快照的 `removedDeleteFiles` 大小为 1（即旧的 file-scoped 删除文件被合并并移除），且查询结果正确。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMergeOnReadUpdate.java`

**修改目的**：验证 UPDATE 操作下旧 position delete 文件被同步合并移除。

**工作逻辑**：
- 重构出 `initTable(String partitionedBy, DeleteGranularity)` 工具方法。
- 新增 `testUpdateFileGranularityMergesDeleteFiles`（分区表）：在已有 file-granularity 更新基础上再执行一次 UPDATE，断言 `removedDeleteFiles` 大小为 2、`validateMergeOnRead` 的删除文件数为 2（而非继续累积为 4）。
- 新增 `testUpdateUnpartitionedFileGranularityMergesDeleteFiles`（非分区表）：连续两次 UPDATE 后断言 `removedDeleteFiles` 大小为 2、删除文件总数降回 2。
- 两测试都用 `assumeThat(distributionMode).isNotEqualToIgnoringCase("range")` 跳过 range 分布模式，因为 range 会产生 partition-scoped 删除文件，不在合并范围。

## 小结

- **成效**：Spark 3.5 的 row-level DELETE/UPDATE 在 `DELETE_GRANULARITY=file` 时，会把同一数据文件上已有的 file-scoped position deletes 与本次新增的 position deletes 合并写入新文件，并在同一次 commit 中移除被替代的旧文件，从而避免小删除文件无限累积。这是把"rewrite_position_deletes"维护操作下沉到普通写入路径的同步版本。
- **影响范围**：Spark 3.5 模块 `SparkBatchQueryScan`、`SparkPositionDeltaWrite` 与对应扩展测试；core 模块仅工具方法位置调整（`isFileScoped` 提至 `ContentFileUtil`），无功能变更。
- **回迁到 1.4.x 的注意事项**：
  - 本提交依赖 core 层 #11222（`c8fe01e71` "Support combining position deletes during writes"），后者引入了 `DeleteWriteResult.rewrittenDeleteFiles`、`FanoutPositionOnlyDeleteWriter` 的 `loadPreviousDeletes` 构造参数、`SortingPositionOnlyDeleteWriter` 的合并逻辑等。1.4.x 若未回迁 #11222，则单独回迁本提交会编译失败。
  - 还依赖 `DeleteFileSet`、`BaseDeleteLoader.loadPositionDeletes(DeleteFileSet, CharSequence)`、`EncryptingFileIO.combine` 等较新 API，需确认 1.4.x 已具备。
  - 这是 merge-on-read 性能优化，对 v1/v2/v3 表均适用（不依赖 DV），属于较为独立的维护性改进，**可作为 1.4.x 优化候选**，但必须连同 #11222 及相关依赖一并回迁，并回归测试 Spark DELETE/UPDATE 在 file/partition granularity 下的行为。
  - 注意 1.4.x 通常对应较旧的 Spark 版本（如 Spark 3.4/3.5），需要确认 `SparkPositionDeltaWrite` 在该分支的具体形态与回迁冲突。
