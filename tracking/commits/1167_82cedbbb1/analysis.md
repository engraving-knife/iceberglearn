# 提交 1167：API, Core: Enable removing rewritten delete files in RowDelta (#11166)

## 提交信息

- **序号**：1167 / 4088
- **哈希**：82cedbbb1935db6a765d9029df4e54c38e95bedc
- **短哈希**：82cedbbb1
- **日期**：2024-09-19（Thu Sep 19 13:38:03 2024 -0700）
- **作者**：Anton Okolnychyi <aokolnychyi@apache.org>
- **提交说明**：API, Core: Enable removing rewritten delete files in RowDelta (#11166)
- **PR/Issue**：#11166

## 总体目的

Iceberg 的 `RowDelta` 操作用于向表中增量添加数据文件与删除文件（delete files），典型场景是 Spark 在 MERGE/UPDATE/DELETE 时新增位置删除（position delete）或等值删除（equality delete）。原 `RowDelta` API 只支持 `addDeletes(DeleteFile)`（新增删除文件），不能在同一操作里删除已有的删除文件。

这带来一个限制：当引擎想「重写（rewrite）删除文件」——比如把多个小的 position delete 文件合并成一个大的、或把等值删除转成位置删除——时，无法在一次原子提交里完成「新增新删除文件 + 删除旧删除文件」。要么：

1. 用 `RewriteFiles` / `RemoveDeleteFiles` 单独做删除文件替换，但这要求替换前后删除文件集语义等价（覆盖同一组数据行），否则会丢删除语义；
2. 拆成两次提交，先 `addDeletes(newFile)` 再 `removeDeletes(oldFile)`，但这会留下中间状态（新旧删除文件同时存在），且不是原子操作，并发场景下可能出现其他事务读到「双倍删除」的视图。

本提交在 `RowDelta` API 上新增 `removeDeletes(DeleteFile)` 方法，让引擎在执行「删除文件重写」类 MERGE 操作时，能在一次 `RowDelta` 提交中同时新增新删除文件并删除被替换的旧删除文件，保证原子性与正确性。这也是 Iceberg 走向「RowDelta 支持完整删除文件重写」的第一步，后续 Spark 等引擎可基于此实现更激进的删除文件 compaction。

## 如何达成设计目的

1. **API 层新增 `removeDeletes` 默认方法**：在 `RowDelta` 接口中加 `default RowDelta removeDeletes(DeleteFile deletes)`，默认抛 `UnsupportedOperationException`。`default` 实现保证向后兼容——已有第三方 `RowDelta` 实现无需立刻实现该方法，但调用时会得到清晰错误。
2. **Core 层实现 `removeDeletes`**：`BaseRowDelta` 重写该方法，转调 `delete(deletes)`——这是从 `MergingSnapshotProducer` 继承的受保护方法，把删除文件加入「待删除文件集」，最终在 commit 时写入 manifest 的 `DELETED` 状态条目。
3. **新增 JMH 基准 `ReplaceDeleteFilesBenchmark`**：评估在大规模删除文件场景下（5 万到 250 万个文件）单线程替换删除文件的提交性能，便于回归对比。
4. **新增 3 个测试用例**：
   - `testRewrittenDeleteFiles`：基础场景，单次 RowDelta 同时 `removeDeletes(oldFile)` + `addDeletes(newFile)`，验证 snapshot operation 为 `DELETE`，data manifest 保持不变，delete manifest 含一个 ADDED（新文件）与一个 DELETED（旧文件）。
   - `testConcurrentDeletesRewriteSameDeleteFile`：两个并发 RowDelta 都基于同一基线 snapshot 重写同一个 `deleteFile`，验证第二个 commit 仍能成功（因为删除文件重写允许并发），最终两个新删除文件都 ADDED，旧删除文件被两次 DELETED（仅一次生效）。
   - `testConcurrentMergeRewriteSameDeleteFile`：一个 DELETE 操作先重写 `deleteFile`，另一个 MERGE 操作也尝试重写同一个 `deleteFile`，验证 MERGE 因 `validateNoConflictingDeleteFiles` 失败——因为 DELETE 可能基于更新的删除语义删除了更多位置，MERGE 不能盲目覆盖。

## 修改详情

### `api/src/main/java/org/apache/iceberg/RowDelta.java`

**修改目的**：在 `RowDelta` 接口上声明 `removeDeletes` 方法。

**工作逻辑**：在 `addDeletes(DeleteFile)` 方法后新增：

```java
/**
 * Removes a rewritten {@link DeleteFile} from the table.
 *
 * @param deletes a delete file that can be removed from the table
 * @return this for method chaining
 */
default RowDelta removeDeletes(DeleteFile deletes) {
  throw new UnsupportedOperationException(
      getClass().getName() + " does not implement removeDeletes");
}
```

- 关键词 "rewritten" 暗示该方法的预期用途：删除一个已被重写的删除文件，与 `addDeletes` 配对使用。
- `default` 实现抛 `UnsupportedOperationException` 并带类名，便于调用方快速定位未实现的子类。
- 返回 `RowDelta` 支持链式调用，与 `addDeletes` 风格一致。

### `core/src/main/java/org/apache/iceberg/BaseRowDelta.java`

**修改目的**：实现 `removeDeletes`，把删除文件加入待删除集合。

**工作逻辑**：

```java
@Override
public RowDelta removeDeletes(DeleteFile deletes) {
  delete(deletes);
  return this;
}
```

- `delete(deletes)` 继承自 `MergingSnapshotProducer`，它会把该 `DeleteFile` 加入 `deletedDeleteFiles` 集合（参考 `MergingSnapshotProducer.delete(DeleteFile)`），在 commit 时被写入 delete manifest 的 `DELETED` 条目。
- 与 `addDeletes` 对称：`addDeletes` 调 `addFile`，`removeDeletes` 调 `delete`。
- 这种对称设计让 `BaseRowDelta` 在一次 commit 中既能 ADD 也能 DELETE 删除文件，正好支持「重写」语义。

### `core/src/test/java/org/apache/iceberg/TestRowDelta.java`

**修改目的**：覆盖 `removeDeletes` 的功能与并发语义。

**工作逻辑**：在文件末尾追加 3 个 `@TestTemplate` 方法（基于 `V2TableTestBase` 的参数化测试，覆盖 v2 表）：

1. **`testRewrittenDeleteFiles`**：
   - 准备：`newRowDelta().addRows(dataFile).addDeletes(deleteFile)` 提交得到 `baseSnapshot`（operation = OVERWRITE，因为既有 data 又有 delete）。
   - 操作：`newRowDelta().removeDeletes(deleteFile).addDeletes(newDeleteFile).validateFromSnapshot(baseSnapshot.snapshotId())` 提交得到 `snapshot`（operation = DELETE，因为没有新增 data 文件，只重写了 delete 文件）。
   - 校验：data manifest 仅含原 `dataFile`（ADDED，seq=1）；delete manifest 有 2 个：`newDeleteFile`（ADDED，seq=2，snapshot=snapshot.snapshotId()）与 `deleteFile`（DELETED，seq=1，snapshot=snapshot.snapshotId()）。
   - 验证旧删除文件被正确标记为 DELETED，新删除文件被 ADDED，且 sequence number 递增。

2. **`testConcurrentDeletesRewriteSameDeleteFile`**：
   - 准备：同上 `baseSnapshot`。
   - 第一笔提交：`delete1 = newRowDelta().addDeletes(newDeleteFile1).removeDeletes(deleteFile).validateFromSnapshot(baseSnapshot).validateNoConflictingDataFiles()`，得到 `snapshot1`（seq=2）。
   - 第二笔提交：`delete2 = newRowDelta().addDeletes(newDeleteFile2).removeDeletes(deleteFile).validateFromSnapshot(baseSnapshot).validateNoConflictingDataFiles()`，得到 `snapshot2`（seq=3）。
   - 校验：`snapshot2` 的 data manifest 仅含原 data 文件；delete manifest 含 `newDeleteFile2`（ADDED，seq=3）与 `newDeleteFile1`（ADDED，seq=2）。
   - 注意：第二笔提交虽然也调了 `removeDeletes(deleteFile)`，但 `deleteFile` 在第一笔提交后已不在表中，所以第二笔提交实际只是 ADDED `newDeleteFile2`，旧 `deleteFile` 不会再次 DELETED。两笔并发重写都成功，因为删除文件重写不冲突（语义上是把同一组删除语义换成另一组）。
   - 此用例验证了「删除文件重写」的并发安全性，区别于数据文件重写。

3. **`testConcurrentMergeRewriteSameDeleteFile`**：
   - 准备：同上 `baseSnapshot`。
   - 第一笔提交：`delete = newRowDelta().addDeletes(newDeleteFile1).removeDeletes(deleteFile).validateFromSnapshot(baseSnapshot).validateNoConflictingDataFiles()`，成功。
   - 第二笔提交尝试 MERGE 语义：`merge = newRowDelta().addRows(newDataFile2).addDeletes(newDeleteFile2).removeDeletes(deleteFile).validateFromSnapshot(baseSnapshot).validateNoConflictingDataFiles().validateNoConflictingDeleteFiles()`。
   - 校验：第二笔提交应抛 `ValidationException`，消息以 `"Found new conflicting delete files that can apply"` 开头。
   - 原因：第一笔 DELETE 提交可能基于新的删除文件 `newDeleteFile1` 删除了某些位置，而第二笔 MERGE 仍基于 `baseSnapshot` 校验，`validateNoConflictingDeleteFiles` 检测到表中有 `newDeleteFile1` 这一新删除文件可能影响其读写结果，因此拒绝。这是 RowDelta 一贯的并发冲突保护机制，新增的 `removeDeletes` 不破坏它。

### `core/src/jmh/java/org/apache/iceberg/ReplaceDeleteFilesBenchmark.java`（新增）

**修改目的**：评估 `removeDeletes` + `addDeletes` 在大规模删除文件场景下的提交性能。

**工作逻辑**：
- JMH 注解：`@Fork(1)`、`@Warmup(iterations=3)`、`@Measurement(iterations=5)`、`@BenchmarkMode(Mode.SingleShotTime)`（单次提交耗时）、`@Timeout(10 minutes)`、`@Threads(1)`。
- 表结构：7 列（int/long/decimal/date/timestamp/timestamp_tz/string），无分区。
- 参数：`@Param({"50000", "100000", "500000", "1000000", "2500000"})` 即 5 万到 250 万个删除文件。
- `setupBenchmark`：建表，循环 `numFiles` 次，每次生成 1 个 data 文件 + 1 个 position delete 文件 + 1 个待替换的 position delete 文件，前者 `addRows` + `addDeletes` 提交，后者收集到 `pendingDeleteFiles` 列表。
- `@Benchmark replaceDeleteFiles`：`table.newRowDelta()`，对每个 `deleteFiles` 调 `removeDeletes`，对每个 `pendingDeleteFiles` 调 `addDeletes`，最后 `commit()`。这一过程模拟引擎在大规模删除文件 compaction 时的一次性替换。
- `tearDownBenchmark`：删表清理。
- 该基准便于后续优化 `MergingSnapshotProducer` 在大文件集场景下的提交开销（如 manifest 写入、冲突校验）。

## 小结

- **成效**：`RowDelta` 现在支持在一次原子提交中既新增又删除删除文件，使「删除文件重写」类操作（compaction、equality→position 转换、小文件合并）能在单次 commit 内完成，避免中间状态与并发风险；并发语义经测试验证：删除文件并发重写允许，但 MERGE 与 DELETE 仍受 `validateNoConflictingDeleteFiles` 保护；新增 JMH 基准为后续性能优化提供基线。
- **影响范围**：api 1 文件（新增 11 行）、core 3 文件（实现 + 测试 + 基准），共新增 294 行。属于 API 扩展（新增方法，不修改既有行为），向后兼容。
- **回迁到 1.4.x 的注意事项**：
  - 这是一个**新增 API + 实现的扩展性改动**，对 1.4.x 现有功能无破坏，**可以安全回迁**。
  - 回迁价值：1.4.x 是较早的维护分支，若该分支上的引擎集成（如 Spark 3.3/3.4/3.5）有「删除文件重写」需求，回迁此 API 可让引擎在 1.4.x 上也能用单次 RowDelta 完成重写，避免拆分提交。
  - 回迁时需 cherry-pick `RowDelta.java`、`BaseRowDelta.java`、`TestRowDelta.java` 三个文件改动；`ReplaceDeleteFilesBenchmark.java` 可选（仅用于性能评估，不影响功能）。
  - 注意 1.4.x 的 `MergingSnapshotProducer` 是否已有 `delete(DeleteFile)` 受保护方法。若 1.4.x 早期版本尚未抽出该方法，回迁时需先确认或一并补齐，否则 `BaseRowDelta.removeDeletes` 编译失败。
  - 该 API 是 `default` 方法，对 1.4.x 上已有的第三方 `RowDelta` 实现（如某些 Catalog 自定义实现）兼容，但调用 `removeDeletes` 时会抛 `UnsupportedOperationException`，引擎集成方需注意捕获或检查。
  - 后续 main 分支可能在此基础上进一步扩展（如 `validateNoConflictingDeleteFiles` 行为调整），1.4.x 回迁后不再跟进，需在 release notes 中说明该 API 在 1.4.x 的能力边界。
