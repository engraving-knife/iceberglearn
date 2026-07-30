# 提交 0859：Core: Prevent duplicate data/delete files (#10007)

## 提交信息
- **序号**：0859 / 4088
- **哈希**：23a578e5c68dcb13d7474a6023d866f09ca512a9
- **短哈希**：23a578e5c
- **日期**：2024-06-20
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core: Prevent duplicate data/delete files (#10007)
- **PR/Issue**：#10007

## 总体目的

Iceberg 的「写快照」API（`AppendFiles`、`RewriteFiles`、`RowDelta`、`OverwriteFiles`、`ReplacePartitions`、`DeleteFiles` 等）允许调用方在一次提交里多次调用 `appendFile` / `addFile` / `deleteFile` / `addDeletes` / `addRows`，把这些文件累积进新快照。但此前这些 add/delete 路径完全没有去重——如果调用方在同一个 commit 里把同一个文件路径（或同一个文件的副本对象）连续 add 多次，Iceberg 会把这些「同一个文件」当成多个独立文件写入 manifest，从而在新快照里出现完全相同的数据/删除文件路径。

这会带来一连串坏后果：
1. **manifest 数据错误**：同一份文件被当成 N 份计入 `added-files`、`deleted-files`、`total-data-files`、`total-records`、`added-file-size`、`total-file-size` 等摘要字段，使 `SnapshotSummary` 严重失真，下游依赖摘要统计的优化与监控会基于错误数据做决策。
2. **数据正确性风险**：在 `OverwriteFiles`、`RewriteFiles` 这类「先 delete 旧文件、再 add 新文件」的场景，如果 delete 和 add 中混入同一文件的重复条目，可能导致 manifest 里出现自相矛盾的条目组合，引发读路径或后续 rewrite 的解析异常。
3. **`TestRewriteFiles` 中暴露的 bug**：原测试 `testRewriteRemoveFiles` 里出现 `ids(pendingId, pendingId, baseSnapshotId)` + `files(FILE_A, FILE_A, FILE_B)` + `statuses(DELETED, DELETED, EXISTING)`，正是 rewrite 时重复 delete `FILE_A` 导致的——一个本应只删除一次的文件被当成删除两次。
4. **`TestSnapshotSummary.testFileSizeSummary` 里也是 bug 的体现**：原 `overwrite().deleteFile(FILE_A).deleteFile(FILE_B).addFile(FILE_C).addFile(FILE_D).addFile(FILE_D).commit()` 里 `addFile(FILE_D)` 被加两次，结果摘要里 `added-file-size` 算成 30、`total-file-size` 算成 30，而实际上只 add 了 FILE_C+FILE_D=20，TOTAL 也应是 20。

本提交通过在两条核心写路径——`FastAppend` 与 `MergingSnapshotProducer`（即 `MergeAppend` / `OverwriteFiles` / `ReplacePartitions` / `RowDelta` / `RewriteFiles` / `DeleteFiles` 共同的父类）——加入「按文件路径去重」逻辑，让同一提交内对同一文件路径的重复 add / delete 只生效一次，从而保证 manifest 与 SnapshotSummary 准确、防止重复 data/delete files 进入快照。

## 如何达成设计目的

去重的核心思路：在累积新文件的集合旁边，再维护一个「已加入文件路径」的 `CharSequenceSet`，每次 add 时先用 `CharSequenceSet.add(path)` 尝试把路径塞进去——`CharSequenceSet` 的 `add` 方法返回 `true` 表示原本不存在、首次加入成功；返回 `false` 表示路径已存在（重复）。基于这个返回值决定是否真正把文件加入 `newFiles` / `newDeleteFiles` 列表与 `SnapshotSummary`，从而把重复 add 静默丢弃。

`CharSequenceSet` 是 Iceberg 自己的工具类（`org.apache.iceberg.util.CharSequenceSet`），基于 `CharSequenceWrapper`，能正确处理 `String` 与 `CharSequence` 之间的相等判断——Iceberg 的 `DataFile.path()` / `DeleteFile.path()` 返回的是 `CharSequence`（可能是 `String`，也可能是更轻量的 `UTF8Byte CharSequence`），用普通 `HashSet<CharSequence>` 会导致不同实现类型但内容相同的字符串被当成不同的 key。`CharSequenceSet` 通过包装按内容比较哈希，正好满足「按文件路径字符串内容去重」的语义需求。

`FastAppend` 改动点：
- 新增字段 `CharSequenceSet newFilePaths = CharSequenceSet.empty();`
- 在 `appendFile(DataFile file)` 里先 `Preconditions.checkNotNull(file, "Invalid data file: null")`（顺手把 null 入参的早失败补上了），再判断 `if (newFilePaths.add(file.path()))`，只有路径首次出现时才真正执行 `hasNewFiles = true; newFiles.add(file); summaryBuilder.addedFile(spec, file);`。重复则直接跳过。

`MergingSnapshotProducer` 改动点（这是 `MergeAppend`、`OverwriteFiles`、`ReplacePartitions`、`RowDelta`、`RewriteFiles`、`DeleteFiles` 的共同基类，覆盖面非常广）：
- 新增两个字段：`CharSequenceSet newDataFilePaths` 与 `CharSequenceSet newDeleteFilePaths`，分别负责去重新增数据文件与新增删除文件。
- `add(DataFile file)`：原来无脑 `setDataSpec(file); addedFilesSummary.addedFile(...); hasNewDataFiles = true; newDataFiles.add(file);`，现在改成「先 `if (newDataFilePaths.add(file.path()))` 才执行后续四步」。`Preconditions.checkNotNull(file, ...)` 早就在那里。
- `add(DeleteFileHolder fileHolder)`：原来无脑 `deleteFiles.add(fileHolder); addedFilesSummary.addedFile(...); hasNewDeleteFiles = true;`，现在改成「先 `if (newDeleteFilePaths.add(fileHolder.deleteFile().path()))` 才执行后续三步」。

由于 `MergingSnapshotProducer` 是上述 6 个写操作类的共同父类，这套去重逻辑一次性覆盖了几乎所有写路径，**只对「单次 commit 内」去重**——它不影响「跨 commit」的重复（跨 commit 的重复属于业务层面的事，Iceberg 也不应阻止）。

配套测试：作者在 `TestFastAppend`、`TestMergeAppend` 里新增 `appendNullFile` 测试，验证 `appendFile(null)` 会抛 NPE 并带正确消息，固化新加的 null 校验行为；在 `TestSnapshotSummary` 里新增 8 个 `*WithDuplicates` 测试，全面覆盖 `fastAppend` / `mergeAppend` / `overwrite` / `delete` / `replacePartitions` / `rowDelta` / `rowDeltaWithDeletes` / `rewrite` / `rewriteWithDeletes` 在重复入参下的摘要表现，断言重复文件被去重后 `added-files` / `total-data-files` / `total-records` / `total-file-size` 等都是「单份」的数值。同时修正 `testFileSizeSummary` 与 `testRewriteRemoveFiles` 里被 bug 隐藏的错误期望值，把原来「重复也算多次」的断言改成「去重后只算一次」的正确期望值。`TestBaseIncrementalAppendScan` 里把原本依赖重复 append 同一文件构造测试场景的写法改成 append 不同文件（`FILE_B FILE_C` / `FILE_D FILE_A2`），因为修复后重复文件无法再用来构造多文件 manifest 场景。

## 修改详情

### `core/src/main/java/org/apache/iceberg/FastAppend.java`
**修改目的**：在 `FastAppend.appendFile` 路径加入按文件路径去重和 null 校验。
**工作逻辑**：
- 新增 `import org.apache.iceberg.util.CharSequenceSet;` 与字段 `private final CharSequenceSet newFilePaths = CharSequenceSet.empty();`。
- 重写 `appendFile(DataFile file)`：先 `Preconditions.checkNotNull(file, "Invalid data file: null");`，再 `if (newFilePaths.add(file.path()))` 守卫——只有路径首次出现才置 `hasNewFiles = true`、`newFiles.add(file)`、`summaryBuilder.addedFile(spec, file)`，否则静默跳过。

### `core/src/main/java/org/apache/iceberg/MergingSnapshotProducer.java`
**修改目的**：在 `MergingSnapshotProducer` 共同父类里给数据文件与删除文件的 add 路径都加去重。
**工作逻辑**：
- 新增字段 `CharSequenceSet newDataFilePaths = CharSequenceSet.empty();` 与 `CharSequenceSet newDeleteFilePaths = CharSequenceSet.empty();`。
- `add(DataFile file)`：把无脑加入改成 `if (newDataFilePaths.add(file.path()))` 守卫后才执行 `setDataSpec(file); addedFilesSummary.addedFile(...); hasNewDataFiles = true; newDataFiles.add(file);`。
- `add(DeleteFileHolder fileHolder)`：把无脑加入改成 `if (newDeleteFilePaths.add(fileHolder.deleteFile().path()))` 守卫后才执行 `deleteFiles.add(fileHolder); addedFilesSummary.addedFile(...); hasNewDeleteFiles = true;`。
- 由于这是 `MergeAppend` / `OverwriteFiles` / `ReplacePartitions` / `RowDelta` / `RewriteFiles` / `DeleteFiles` 共同父类，去重一次覆盖全部 6 条写路径。

### `core/src/test/java/org/apache/iceberg/TestBaseIncrementalAppendScan.java`
**修改目的**：把原本依赖「同一文件重复 append」构造多文件 manifest 的测试场景改成 append 不同文件。
**工作逻辑**：将多处 `.appendFile(FILE_B).appendFile(FILE_B)` / `.appendFile(FILE_C).appendFile(FILE_C)` 改为 `.appendFile(FILE_B).appendFile(FILE_C)` / `.appendFile(FILE_D).appendFile(FILE_A2)`，并把注释里的 manifest 文件清单同步更新。这是因为修复后重复文件无法再累积进 manifest，必须用真正不同的文件才能构造多文件场景。

### `core/src/test/java/org/apache/iceberg/TestFastAppend.java`
**修改目的**：固化 `FastAppend.appendFile(null)` 早失败的行为。
**工作逻辑**：新增 `appendNullFile` 测试，断言 `table.newFastAppend().appendFile(null).commit()` 抛 `NullPointerException` 且消息为 `"Invalid data file: null"`。

### `core/src/test/java/org/apache/iceberg/TestMergeAppend.java`
**修改目的**：固化 `AppendFiles.appendFile(null)`（即走 `MergeAppend` 路径）早失败的行为。
**工作逻辑**：新增 `appendNullFile` 测试，断言 `table.newAppend().appendFile(null).commit()` 抛 `NullPointerException` 且消息为 `"Invalid data file: null"`。

### `core/src/test/java/org/apache/iceberg/TestRewriteFiles.java`
**修改目的**：修正 `testRewriteRemoveFiles` 里被去重 bug 隐藏的错误期望值。
**工作逻辑**：把原断言 `ids(pendingId, pendingId, baseSnapshotId)` / `files(FILE_A, FILE_A, FILE_B)` / `statuses(DELETED, DELETED, EXISTING)` 改为 `ids(pendingId, baseSnapshotId)` / `files(FILE_A, FILE_B)` / `statuses(DELETED, EXISTING)`，正确反映「FILE_A 只被删除一次」的去重后行为。

### `core/src/test/java/org/apache/iceberg/TestSnapshotSummary.java`
**修改目的**：固化重复 add/delete 在各种写路径下被去重后的正确 SnapshotSummary，并修正 `testFileSizeSummary` 里被 bug 隐藏的错误期望值。
**工作逻辑**：
- 新增 `import static org.assertj.core.api.Assumptions.assumeThat;`。
- `testFileSizeSummary`：去掉测试用例里多余的 `.addFile(FILE_D)`（原本用于触发重复 add 的代码）；把 `added-file-size` 期望从 30 改成 20、`total-file-size` 从 30 改成 20；删 FILE_C+FILE_D 后 `total-file-size` 期望从 10 改成 0。`testFileSizeSummaryWithDeletes` 把原来的 `if (formatVersion == 1) return;` 改写为更地道的 `assumeThat(formatVersion).isGreaterThan(1);`（避免在 v1 下提前 return 留下未执行断言，让 AssertJ 假设机制显式跳过）。
- 新增 8 个测试用例：`fastAppendWithDuplicates` / `mergeAppendWithDuplicates` / `overwriteWithDuplicates` / `deleteWithDuplicates` / `replacePartitionsWithDuplicates` / `rowDeltaWithDuplicates` / `rowDeltaWithDeletesAndDuplicates` / `rewriteWithDuplicateFiles` / `rewriteWithDeletesAndDuplicates`，分别对 6 条写路径 + 删除路径 + 同时带 delete 的 rewrite 路径，重复传入同一文件（用 `DataFiles.builder(SPEC).copy(FILE_A).build()` 制造内容相同但对象不同的副本），断言去重后摘要各项均为「单份」数值，覆盖了 `added-files` / `deleted-files` / `added-delete-files` / `added-pos-delete-files` / `removed-delete-files` / `removed-pos-delete-files` / `total-data-files` / `total-delete-files` / `total-eq-deletes` / `total-pos-deletes` / `added-records` / `deleted-records` / `added-file-size` / `removed-file-size` / `total-file-size` / `changed-partition-count` 等关键字段。涉及删除文件的用例统一用 `assumeThat(formatVersion).isGreaterThan(1)` 跳过 v1 表（v1 不支持 delete files）。

## 小结
- **成效**：从根上修复了「同一 commit 内重复 add/delete 同一文件路径导致 manifest 与 SnapshotSummary 失真」的缺陷，覆盖 `FastAppend` 与 `MergingSnapshotProducer` 全部 6 条写路径（`MergeAppend` / `OverwriteFiles` / `ReplacePartitions` / `RowDelta` / `RewriteFiles` / `DeleteFiles`）。顺手补齐了 `appendFile(null)` 的早失败行为；同步修正了 3 处测试里被 bug 隐藏藏的错误期望值；新增 9 个 `*WithDuplicates` 测试用例固化去重语义。
- **影响范围**：核心写路径行为变更，影响所有调用方在单次 commit 内对同一文件路径重复 add/delete 的结果——manifest 里不再出现重复条目，`SnapshotSummary` 的 `added-files` / `deleted-files` / `total-*` / `*-size` / `*-records` 等字段都按去重后单份计数。这是一个行为修正，对外部依赖「重复也算多次」的调用方是 breaking change，但对绝大多数正常用法无影响（正常用法不会重复 add 同一文件）。
- **回迁注意事项**：1.4.x 分支可直接 cherry-pick。回迁后需重点验证：(a) 1.4.x 下的 `MergingSnapshotProducer` / `FastAppend` 类是否仍与 main 分支结构兼容，字段与方法签名是否能直接套用；(b) 确认 1.4.x 下 `CharSequenceSet`（`org.apache.iceberg.util.CharSequenceSet`）已存在且 `add(CharSequence)` 返回 boolean 的语义一致；(c) 把对应的 6 个测试文件（`TestFastAppend` / `TestMergeAppend` / `TestRewriteFiles` / `TestSnapshotSummary` / `TestBaseIncrementalAppendScan`）一起回迁以固化修复；(d) 如果 1.4.x 下有其它子模块（spark / flink / hive）继承或包装了这两个类，需要确认其行为是否也跟着改变，跑一次跨模块测试；(e) 重点关注调用方（下游引擎如 Spark / Flink connector）是否有人「故意」依赖重复 add 的旧行为——若有需要同步通知调用方修正。
