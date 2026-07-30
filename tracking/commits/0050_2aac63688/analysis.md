# 提交 0050：Core: Replace `.size() > 0` with `!.isEmpty()` (#8813)

## 提交信息

- **序号**：0050 / 4088
- **哈希**：2aac63688054ac36540072cfa13aac08fccf9421
- **短哈希**：2aac63688
- **日期**：2023-10-13 19:45:50 +0200（作者时区为 -0700）
- **作者**：Kirill Saied
- **提交说明**：Core: Replace `.size() > 0` with `!.isEmpty()` (#8813)
- **PR/Issue**：#8813（关联 ISSUE #8810）

## 总体目的

这是 ISSUE #8810 系列代码清理在 Iceberg 核心模块（`iceberg-core`）的专项提交，与 0046（外围多模块）、0047（Spark 模块）属同一清理批次，按模块拆分以便审阅。提交将 `iceberg-core` 模块中所有形如 `coll.size() > 0` 的非空判断替换为 `!coll.isEmpty()`，并在涉及 `== 0` 的判断处替换为 `isEmpty()`。

`iceberg-core` 是 Iceberg 表引擎的心脏，承载了快照提交（`FastAppend`、`BaseOverwriteFiles`、`BaseRewriteFiles`、`MergingSnapshotProducer`）、manifest 过滤管理（`ManifestFilterManager`）、扫描规划（`BaseDistributedDataScan`、`BaseIncrementalChangelogScan`）、表/视图元数据（`TableMetadata`、`ViewMetadata`、`PositionDeletesTable`）、文件可达性工具（`ReachableFileUtil`）、提交服务（`BaseCommitService`）、删除写入结果（`DeleteWriteResult`）等关键路径。本提交在这些关键路径上进行了 16 个文件、25 处替换。

虽然这些替换不改变运行时行为（对 `List`/`Set`/`Map` 而言 `size() > 0` 与 `!isEmpty()` 等价），但统一为 `!isEmpty()` 有两点收益：一是可读性，"非空"语义更直接；二是潜在性能，对某些集合实现 `size()` 可能 O(n) 而 `isEmpty()` 通常 O(1)。更重要的意义在于：核心模块是整个项目被引用最频繁、被新 contributor 最先阅读的部分，统一风格有助于降低阅读成本、避免后续代码模仿旧写法继续引入 `size() > 0`。本提交与 0046/0047 共同完成了 ISSUE #8810 在全代码库的清理闭环。

## 如何达成设计目的

整体思路是机械式替换，按文件逐处把 `xxx.size() > 0` 改为 `!xxx.isEmpty()`，把 `xxx.size() == 0` 改为 `xxx.isEmpty()`。改动集中在 `core/src/main/java` 下，无测试文件改动（核心模块的对应测试多以 `Assert.assertEquals`/`Assert.assertTrue(具体条件)` 形式存在，`size() > 0` 模式相对较少）。部分文件中多处改动密集（如 `ManifestFilterManager` 4 处、`MergingSnapshotProducer` 4 处、`BaseCommitService` 4 处），这些恰是核心提交与扫描路径中频繁做"是否有变更文件/删除文件/任务组"判断的位置。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseDistributedDataScan.java`

**修改目的**：分布式数据扫描规划中"是否存在删除 manifest"判断改用 `isEmpty`。

**工作逻辑**：`deleteManifests.size() > 0 && mayHaveEqualityDeletes(snapshot)` 改为 `!deleteManifests.isEmpty() && mayHaveEqualityDeletes(snapshot)`，用于决定是否可能存在等值删除文件，进而影响本地/远端删除规划策略。

### `core/src/main/java/org/apache/iceberg/BaseIncrementalChangelogScan.java`

**修改目的**：增量 changelog 扫描中"快照是否携带删除 manifest"判断改用 `isEmpty`。

**工作逻辑**：`snapshot.deleteManifests(table().io()).size() > 0` 改为 `!...isEmpty()`。如果增量 changelog 扫描遇到带删除文件的快照，会抛 `UnsupportedOperationException`（删除文件目前不在 changelog 扫描支持范围内），所以这个判断是功能守卫。

### `core/src/main/java/org/apache/iceberg/BaseOverwriteFiles.java` 和 `core/src/main/java/org/apache/iceberg/BaseRewriteFiles.java`

**修改目的**：覆盖文件/重写文件操作中"是否有被删除/被替换的数据文件"判断改用 `isEmpty`，决定是否执行冲突校验。

**工作逻辑**：
- `BaseOverwriteFiles` 中 `deletedDataFiles.size() > 0` 改为 `!deletedDataFiles.isEmpty()`，控制是否调用 `validateNoNewDeletesForDataFiles`（校验在被删除文件上没有新增的行级删除）。
- `BaseRewriteFiles` 中 `replacedDataFiles.size() > 0` 改为 `!replacedDataFiles.isEmpty()`，控制是否校验被替换数据文件上没有新删除。这两处都是 OCC（乐观并发控制）冲突检测的关键分支。

### `core/src/main/java/org/apache/iceberg/FastAppend.java`

**修改目的**：快速追加提交中"是否有新文件待写入 manifest"判断改用 `isEmpty`。

**工作逻辑**：`newManifests == null && newFiles.size() > 0` 改为 `!newFiles.isEmpty()`，决定是否新开一个 rolling manifest writer 把新增数据文件落盘。这是 FastAppend 提交路径的核心分支。

### `core/src/main/java/org/apache/iceberg/ManifestFilterManager.java`

**修改目的**：manifest 过滤管理器中四处"是否存在待删除路径/待丢弃分区"判断统一改用 `isEmpty`。

**工作逻辑**：四处改动：
1. `containsDeletes()` 中 `deletePaths.size() > 0 || ... || dropPartitions.size() > 0` 改为 `!deletePaths.isEmpty() || ... || !dropPartitions.isEmpty()`，判断 manifest 过滤器是否持有任何删除谓词。
2. `canContainDroppedPartitions` 计算前 `dropPartitions.size() > 0` 改为 `!dropPartitions.isEmpty()`，决定是否调用 `ManifestFileUtil.canContainAny` 检查 manifest 是否可能含待丢弃分区。
3. `canContainDroppedFiles` 分支中 `deletePaths.size() > 0` 改为 `!deletePaths.isEmpty()`，决定是否按删除文件分区集合做交集检查。这些判断影响 manifest 在重写时的跳过/包含决策，是 manifest 增量重写的性能关键路径。

### `core/src/main/java/org/apache/iceberg/MergingSnapshotProducer.java`

**修改目的**：合并类快照生产者（Append/Overwrite/Rewrite 的公共基类）中四处"是否有新增数据/删除文件"判断改用 `isEmpty`。

**工作逻辑**：四处改动：
1. `addsDataFiles()` 返回 `newDataFiles.size() > 0` 改为 `!newDataFiles.isEmpty()`；
2. `addsDeleteFiles()` 返回 `newDeleteFilesBySpec.size() > 0` 改为 `!newDeleteFilesBySpec.isEmpty()`；
3. `prepareNewDataManifests()` 中 `newDataFiles.size() > 0` 改为 `!newDataFiles.isEmpty()`，决定新数据 manifest 的拼接方式；
4. `newDeleteFilesAsManifests()` 中 `cachedNewDeleteManifests.size() > 0` 改为 `!cachedNewDeleteManifests.isEmpty()`。这些都是提交前准备 manifest 集合的分支判断，影响最终快照的 manifest 列表构成。

### `core/src/main/java/org/apache/iceberg/PositionDeletesTable.java`

**修改目的**：位置删除表 schema 构造中"分区类型是否有字段"判断改用 `isEmpty`。

**工作逻辑**：`partitionType.fields().size() > 0` 改为 `!partitionType.fields().isEmpty()`，决定是否返回带分区列的 schema 还是避免返回空 struct（空 struct 在某些引擎不被支持）。

### `core/src/main/java/org/apache/iceberg/ReachableFileUtil.java`

**修改目的**：可达文件工具中"是否有历史元数据日志条目"判断改用 `isEmpty`。

**工作逻辑**：`metadataLogEntries.size() > 0` 改为 `!metadataLogEntries.isEmpty()`，决定是否遍历历史元数据文件位置收集。该工具用于过期文件清理与表文件枚举。

### `core/src/main/java/org/apache/iceberg/SnapshotSummary.java`

**修改目的**：快照摘要生成中"是否有变更分区"判断改用 `isEmpty`。

**工作逻辑**：`setIf(changedPartitions.size() > 0, builder, PARTITION_SUMMARY_PROP, "true")` 改为 `setIf(!changedPartitions.isEmpty(), ...)`，控制是否在快照摘要中写入分区级摘要开关。

### `core/src/main/java/org/apache/iceberg/TableMetadata.java`

**修改目的**：表元数据变更跟踪中"是否有待丢弃变更"判断改用 `isEmpty`。

**工作逻辑**：`hasChanges()` 中 `discardChanges && changes.size() > 0` 改为 `discardChanges && !changes.isEmpty()`，判断元数据 builder 是否处于有变更状态。

### `core/src/main/java/org/apache/iceberg/actions/BaseCommitService.java`

**修改目的**：重写动作的提交服务后台线程中四处"是否有待提交/进行中的提交组"判断改用 `isEmpty`。

**工作逻辑**：四处改动，集中在 committer 后台线程的循环条件与状态判断：
1. `while (running.get() || completedRewrites.size() > 0 || inProgressCommits.size() > 0)` 改为 `!completedRewrites.isEmpty() || !inProgressCommits.isEmpty()`；
2. `if (completedRewrites.size() == 0 && inProgressCommits.size() == 0)` 改为 `completedRewrites.isEmpty() && inProgressCommits.isEmpty()`（这是 `== 0` 改 `isEmpty()` 的形式）；
3. `if (!running.get() && completedRewrites.size() > 0)` 改为 `!completedRewrites.isEmpty()`；
4. `writingComplete = !running.get() && completedRewrites.size() > 0` 改为 `!completedRewrites.isEmpty()`。这些判断决定后台提交线程何时阻塞、何时收尾提交剩余组，是 `RewriteDataFiles`/`RewritePositionDeletes` action 的并发提交调度核心。

### `core/src/main/java/org/apache/iceberg/actions/RewritePositionDeletesGroup.java`

**修改目的**：位置删除重写文件组构造时"任务列表非空"校验改用 `isEmpty`。

**工作逻辑**：`Preconditions.checkArgument(tasks.size() > 0, "Tasks must not be empty")` 改为 `!tasks.isEmpty()`。

### `core/src/main/java/org/apache/iceberg/io/DeleteWriteResult.java`

**修改目的**：删除写入结果中"是否引用了数据文件"判断改用 `isEmpty`。

**工作逻辑**：`referencedDataFiles != null && referencedDataFiles.size() > 0` 改为 `referencedDataFiles != null && !referencedDataFiles.isEmpty()`。`referencesDataFiles()` 用于提交时确定等值删除文件依赖哪些数据文件，影响提交顺序与依赖追踪。

### `core/src/main/java/org/apache/iceberg/util/PartitionUtil.java`

**修改目的**：分区工具中"分区类型是否有字段"判断改用 `isEmpty`。

**工作逻辑**：`partitionType.fields().size() > 0` 改为 `!partitionType.fields().isEmpty()`，决定是否向 `idToConstant` 注入 `_partition` 元数据列。

### `core/src/main/java/org/apache/iceberg/view/ViewMetadata.java`

**修改目的**：视图元数据 builder 中"是否有版本被添加"校验改用 `isEmpty`。

**工作逻辑**：`Preconditions.checkArgument(versions.size() > 0, "Invalid view: no versions were added")` 改为 `!versions.isEmpty()`，确保构建视图元数据时至少有一个视图版本。这是视图元数据一致性的前置校验。

## 小结

本提交作为 ISSUE #8810 清理批次的核心模块专项，将 `iceberg-core` 中快照提交、manifest 过滤、扫描规划、元数据/视图构建、并发提交服务等关键路径上的 25 处 `.size() > 0`/`.size() == 0` 统一替换为 `!isEmpty()`/`isEmpty()`，在不改变运行时行为的前提下统一了 Iceberg 表引擎心脏地带的代码风格，与 0046/0047 共同完成了全代码库的清理闭环。
