# 提交 1697 e91655bfa 分析

## 提交信息
- 哈希：e91655bfafe4cddeac25d62b52515ac4c2b47b34
- 日期：2025-02-06 21:38:27 -0800
- 作者：Hongyue/Steve Zhang
- 消息：Core: Exclude deleted content file in RewriteTablePathUtil copy plan (#12006)

## 总体目的

本提交修复 `RewriteTablePathUtil` 在生成"路径重写复制计划（copyPlan）"时的一个 bug：当表的某个快照已被过期（expire），对应的 manifest entry 标记为已删除（status=DELETED，即 `entry.isLive()` 为 false）时，这些已删除的内容文件仍然被加入 copyPlan，导致路径重写动作尝试复制一个源端可能已经不存在的文件，最终在 `copyTableFiles` 阶段抛出 FileNotFoundException 或产生无效的目标文件。

正确行为应该是：保留 manifest 中已删除的 entry（因为 manifest 的历史完整性需要保留，目标表的 metadata 仍需记录这些 entry，只是状态为 DELETED），但把这些已删除 entry 对应的源文件从"需要物理复制的文件清单"中排除。

这个 bug 在以下场景触发：用户对表做过 `expireSnapshots`，原表的部分数据文件已从 manifest list 中移除但其 manifest entry 仍以 DELETED 状态保留在某个 manifest 中，随后对这张表执行 `rewriteTablePath`。修复前会尝试复制这些已不存在的文件。

## 如何达成设计目的

核心思路是在 `RewriteTablePathUtil` 的三处 copyPlan 构造点上加一个 `if (entry.isLive())` 守卫：
- 数据文件（DataFile）的 copyPlan 构造
- 位置删除文件（PositionDelete）的 copyPlan 构造
- 等值删除文件（EqualityDelete）的 copyPlan 构造

注意：仍然调用 `appendEntryWithFile(entry, writer, ...)` 把已删除 entry 写入新的 manifest（保持 manifest 的历史完整性），只是不把对应的文件路径加入 `result.copyPlan()`。`result.toRewrite()` 的逻辑保持不变（对 position delete 仍加入 toRewrite）。

### 修改详情

#### core/src/main/java/org/apache/iceberg/RewriteTablePathUtil.java
共 21 行变更。三处修改：

1. 数据文件处理（约 360 行，`copyDataFile` 方法）：
   - 原代码：`result.copyPlan().add(Pair.of(sourceDataFilePath, newDataFile.location()));`
   - 新代码：加注释 "keep deleted data file entries but exclude them from copyPlan"，并包裹在 `if (entry.isLive()) { ... }` 中。

2. 位置删除文件处理（约 387 行，case POSITION_DELETES 分支）：
   - 原代码：`result.copyPlan().add(Pair.of(stagingPath(file.location(), stagingLocation), movedFile.location()));`
   - 新代码：加注释 "keep deleted position delete entries but exclude them from copyPlan"，包裹在 `if (entry.isLive()) { ... }` 中。`result.toRewrite().add(file);` 保持在 if 之外。

3. 等值删除文件处理（EQUALITY_DELETES 分支）：
   - 原代码：注释 "No need to rewrite equality delete files as they do not contain absolute file paths." 后 `result.copyPlan().add(Pair.of(file.location(), eqDeleteFile.location()));`
   - 新代码：加注释 "keep deleted equality delete entries but exclude them from copyPlan"，把原注释与 add 调用一起包裹在 `if (entry.isLive()) { ... }` 中。

#### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteTablePathsAction.java
共 126 行变更，主要内容：

1. 新增 `import`：`java.util.function.Predicate`、`org.apache.iceberg.Snapshot`、`org.apache.iceberg.actions.ExpireSnapshots`、`org.apache.iceberg.relocated.com.google.common.collect.Iterables`、`org.apache.iceberg.util.SnapshotUtil`。

2. `createTableWithSnapshots` 重载：原方法只支持 append 模式，新增一个私有重载接受 `String mode` 参数（"append" 或 "overwrite"），原公开方法委托给新重载并传 "append"。这是为了测试覆盖 overwrite 场景（每次 overwrite 在非分区表上会生成 2 个 manifest 和 1 个 data file，便于构造已删除 entry）。

3. `setupTableLocation`/`cleanupTableSetup`/`testMoveVersionWithInvalidSnapshots` 方法签名移除 `throws Exception`（无受检异常需要声明）。

4. 既有测试 `testRewritePathWithExpiredSnapshot`（约 488 行）增强：
   - 原来只断言过期后做了 rewrite，现在额外断言 `expireResult.deletedManifestListsCount() == 1`。
   - 引入 `totalIteration=102`、`missingVersionFile=1`、`expiredManifestListCount=1` 等变量，把原来硬编码的 `checkFileNum(101, 101, 101, 406, result)` 改为 `checkFileNum(totalIteration - missingVersionFile, totalIteration - expiredManifestListCount, totalIteration, totalIteration*4 - missingVersionFile - expiredManifestListCount, result)`，让数字含义可读且与过期行为一致。

5. 新增测试 `testRewritePathWithNonLiveEntry`（核心新测试）：
   - 用 overwrite 模式创建 3 个快照的表，每次 overwrite 产生 1 个 data file，共 3 个 data file。
   - 取最早快照 `oldest`，记录其 addedDataFile 的路径，并预先计算出该文件在目标位置应有的路径 `deletedDataFilePathInTargetLocation`。
   - 过期最早快照，断言 `deletedManifestListsCount=1`、`deletedManifestsCount=1`、`deletedDataFilesCount=1`。
   - 执行 rewriteTablePath，调用 `checkFileNum(5, 2, 4, 13, result)` 校验文件数量（5 个 version file、2 个 manifest list、4 个 manifest、共 13 个文件）。
   - `copyTableFiles(result)` 执行物理复制。
   - 用 Spark 读取目标表的 `#all_files`，断言 copiedDataFiles 大小为 2（即已删除的 data file 未被复制），且不包含 `deletedDataFilePathInTargetLocation`。
   - 读取目标表的 `#all_entries` 过滤 `status == 2`（DELETED），断言仍包含 `deletedDataFilePathInTargetLocation`，即 manifest entry 保留但物理文件未复制。

6. `checkFileNum` 方法（约 1024 行）重构：
   - 把原来基于字符串结尾匹配的内联 lambda 改为命名 `Predicate<String>`：`isManifest`（-m0.avro 或 -m1.avro）、`isManifestList`（含 "snap-" 且以 .avro 结尾）、`isMetadataJSON`（.metadata.json）。
   - 断言风格从 `.withFailMessage(...)` 改为 AssertJ 的 `.as(...)`。

7. `testStartSnapshotWithoutValidSnapshot` 中 `((List) table.snapshots()).size()` 改为 AssertJ 的 `table.snapshots()).hasSize(1)`。

## 小结

本次修复正确区分了"manifest entry 的逻辑保留"与"物理文件的复制"两个概念，避免 rewriteTablePath 在源表做过 expireSnapshots 后尝试复制已删除文件而失败。新增测试 `testRewritePathWithNonLiveEntry` 精确覆盖了该场景，断言既有 manifest entry 的 DELETED 记录保留、对应物理文件不复制。

回迁到 1.4.x 的注意事项：
1. 核心修改在 `core/src/main/java/org/apache/iceberg/RewriteTablePathUtil.java`，三处 `if (entry.isLive())` 守卫是纯加法，回迁风险低，但需确认 1.4.x 的 `RewriteTablePathUtil` 结构与 main 一致（后续提交 1698、1700、1701 也涉及此类，注意合并冲突）。
2. `entry.isLive()` 方法的语义：返回 status != DELETED。回迁时确认该方法在 1.4.x 中存在且语义一致。
3. 测试改动较大且依赖 `#all_files`/`#all_entries` 这种 Spark 读取元数据的方式，回迁测试需确认 1.4.x 的 Spark 版本支持该语法。
4. `createTableWithSnapshots` 的 overwrite 重载是新方法，回迁测试时需同步引入。
5. 该 bug 影响所有做过快照过期再重写路径的用户，建议优先回迁。
