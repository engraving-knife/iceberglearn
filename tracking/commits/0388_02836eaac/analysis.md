# 提交 0388：Spark: Ensure partition stats files are considered for GC procedures (#9284)

## 提交信息

- **序号**：0388
- **哈希**：02836eaac8c8cd18f9998d0ae4411b09d3649e1a
- **短哈希**：02836eaac
- **日期**：2024-01-19（Fri Jan 19 00:40:53 2024 +0530）
- **作者**：Ajantha Bhat <ajanthabhat@gmail.com>
- **提交说明**：Spark: Ensure partition stats files are considered for GC procedures (#9284)
- **PR/Issue**：#9284

## 总体目的

这个提交修复了一个 Iceberg 在引入"分区统计文件（Partition Statistics Files）"功能后遗留的 GC（垃圾回收）盲区。Iceberg 表上存在两类统计文件：一类是较老的"表级统计文件"（`StatisticsFile`，存放 Puffin 格式的 blob 如 NDV/直方图等），另一类是较新引入的"分区统计文件"（`PartitionStatisticsFile`，存放各分区的统计信息）。这两类文件都活在 `metadata/` 目录下、都被记录在 TableMetadata 里、都通过 snapshotId 关联到具体快照。

GC 类过程（procedure）——`expire_snapshots` 与 `remove_orphan_files`——在做清理时需要枚举"仍然被引用的统计文件"，从而决定哪些可以安全删除、哪些不能动。修复前，`BaseSparkAction.statisticsFileDS` 只通过 `ReachableFileUtil.statisticsFilesLocations(table, predicate)` 枚举了表级 `StatisticsFile`，完全没考虑 `PartitionStatisticsFile`。这会导致两种事故：

1. **expire_snapshots 漏删**：过期快照对应的分区统计文件不会被算作"该快照的可清理文件"，因此即使快照已过期，分区统计文件也不会被删除，留下 metadata 与磁盘上的孤儿文件，长期累积成存储浪费。
2. **remove_orphan_files 误删**：remove_orphan_files 用 "所有 reachable 文件" 集合做白名单，凡是白名单外的就当孤儿删掉。由于白名单里没有分区统计文件，**仍然在被引用的**分区统计文件会被误判为孤儿并被删除，直接破坏表，下一次查询会因 stats 文件不存在而失败。

这两类后果严重程度不同：误删是数据可用性问题，漏删是存储效率问题。提交同时解决两者，让 GC 流程对两类统计文件一视同仁。

第二层意图是 API 整顿：旧的 `statisticsFilesLocations(Table, Predicate<StatisticsFile>)` 接受一个针对 `StatisticsFile` 的谓词，无法自然扩展到 `PartitionStatisticsFile`；与其加一个并列的 `partitionStatisticsFilesLocations`，不如引入一个统一的 `statisticsFilesLocationsForSnapshots(Table, Set<Long>)`，由它内部同时处理两类文件，调用方只需传 snapshotIds（或 null 表示全部）。旧 API 标 `@Deprecated`，计划 1.6.0 移除，给社区一个平滑迁移窗口。

## 如何达成设计目的

实现分两步。第一步在 `core` 模块新增统一枚举方法 `ReachableFileUtil.statisticsFilesLocationsForSnapshots(Table, Set<Long> snapshotIds)`：当 `snapshotIds == null` 时返回所有统计文件路径，否则按 snapshotId 过滤；同时遍历 `table.statisticsFiles()` 与 `table.partitionStatisticsFiles()`，把两类文件的路径合并到一个列表返回。`statisticsFilesLocations(Table)` 无参版改为内部调用新方法（传 null），保持兼容；带 `Predicate` 的旧方法标 `@Deprecated`。第二步把 Spark 3.3/3.4/3.5 三套 `BaseSparkAction.statisticsFileDS` 中的"组装谓词 + 调旧方法"逻辑替换为单行 `ReachableFileUtil.statisticsFilesLocationsForSnapshots(table, snapshotIds)`，于是 expire/remove_orphan 都自动覆盖了分区统计文件。最后在三个 Spark 版本里同步新增测试工具类 `ProcedureUtil` 与对应测试用例，固化新行为。

## 修改详情

### core/src/main/java/org/apache/iceberg/ReachableFileUtil.java

**修改目的**：在表级统计文件之外，把分区统计文件也纳入"可枚举的可达统计文件"，并提供按 snapshotId 过滤的统一入口。

**工作逻辑**：
- `statisticsFilesLocations(Table)` 文档从 "Returns locations of statistics files" 改为 "Returns locations of all statistics files"，强调"全部"；实现从 `statisticsFilesLocations(table, file -> true)` 改为 `statisticsFilesLocationsForSnapshots(table, null)`，即委托新方法。
- `statisticsFilesLocations(Table, Predicate<StatisticsFile>)` 加 `@Deprecated` 注解与 Javadoc，明确"since 1.5.0，1.6.0 移除，改用 `statisticsFilesLocationsForSnapshots(table, snapshotIds)`"。注意该旧方法**实现没变**——它仍只处理 `StatisticsFile`，不处理 `PartitionStatisticsFile`，这也是它要被废弃的原因：它的签名根本没法扩展到分区统计。
- 新增 `statisticsFilesLocationsForSnapshots(Table, Set<Long> snapshotIds)`：
  - 当 `snapshotIds == null`，两个谓词都返回 `true`（即"全部"）；
  - 否则两个谓词都按 `snapshotIds.contains(file.snapshotId())` 过滤；
  - 遍历 `table.statisticsFiles()`，过滤、`map(StatisticsFile::path)`、加入结果列表；
  - 遍历 `table.partitionStatisticsFiles()`，过滤、`map(PartitionStatisticsFile::path)`、加入同一结果列表；
  - 返回合并后的列表。

  关键设计是**两类文件共用同一个 snapshotIds 过滤逻辑、共用同一个返回列表**，这样调用方拿到的就是"该批快照引用的所有统计文件路径"，无论表级还是分区级。

### spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/actions/BaseSparkAction.java
### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/BaseSparkAction.java
### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/BaseSparkAction.java

**修改目的**：让 Spark 各版本的 `statisticsFileDS` 统一通过新方法枚举统计文件，自动覆盖分区统计文件。

**工作逻辑**（三个版本完全一致）：
- 删除 import：`java.util.function.Predicate` 与 `org.apache.iceberg.StatisticsFile`（这两个之前只为了构造旧 API 的谓词而引入）。
- `statisticsFileDS(Table table, Set<Long> snapshotIds)` 方法体从原来的"if null→true，else→contains"六行谓词构造 + 调用旧 API，简化为两行：
  ```java
  List<String> statisticsFiles =
      ReachableFileUtil.statisticsFilesLocationsForSnapshots(table, snapshotIds);
  return toFileInfoDS(statisticsFiles, STATISTICS_FILES);
  ```
  语义上 `snapshotIds == null` 仍表示"全部快照"，与新方法一致；非 null 时按 snapshotId 过滤。修复点在于：现在 `statisticsFiles` 这个列表里**同时包含**了分区统计文件路径，所以下游 `expire_snapshots`/`remove_orphan_files` 在构造"白名单"或"待删清单"时不会再漏掉它们。

### spark/v3.3/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/ProcedureUtil.java
### spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/ProcedureUtil.java
### spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/ProcedureUtil.java

**修改目的**：新增测试工具类，统一封装"构造分区统计文件"与"生成统计文件路径"两个测试常用操作，消除三个 Spark 版本测试里散落的重复 `statsFileLocation` 私有方法。

**工作逻辑**（三个文件完全一致）：
- `ProcedureUtil` 是包级工具类（构造器私有），提供两个 package-private 静态方法：
  - `writePartitionStatsFile(long snapshotId, String statsLocation, FileIO fileIO)`：通过 `fileIO.newOutputFile(statsLocation).create()` 创建一个空文件（仅占位），再用 `ImmutableGenericPartitionStatisticsFile.builder()` 构造一个 `PartitionStatisticsFile`，`snapshotId` 与 `path` 来自参数，`fileSizeInBytes` 写死 42L（测试不关心真实大小）。返回值用于后续 `table.updatePartitionStatistics().setPartitionStatistics(...).commit()`。
  - `statsFileLocation(String tableLocation)`：在 `tableLocation/metadata/` 下生成一个 `stats-file-<UUID>` 路径，去掉 `file:` 前缀以适配本地 `File` 断言。
- 把原先 `TestExpireSnapshotsProcedure` 里的私有 `statsFileLocation` 提到这个工具类，便于 expire/orphan 两套测试共用。

### spark/v3.3/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestExpireSnapshotsProcedure.java
### spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestExpireSnapshotsProcedure.java
### spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestExpireSnapshotsProcedure.java

**修改目的**：在 expire_snapshots procedure 上验证分区统计文件能被正确删除；并把测试中重复的 `statsFileLocation` 工具方法迁移到 `ProcedureUtil`。

**工作逻辑**（三个版本一致）：
- import 调整：删 `java.util.UUID`、`java.util.stream.Collectors`，新增 `org.apache.iceberg.PartitionStatisticsFile`。
- 原有 `testExpireSnapshotsWithStatisticFiles`（旧表级统计测试）里：`statsFileLocation(...)` 改为 `ProcedureUtil.statsFileLocation(...)`；删除"再断言 statisticsFile1 已从 TableMetadata 移除"的整段冗余检查（这段断言之前的语义已被"containsExactly(statisticsFile2.snapshotId())"覆盖），并把断言语措从 "should be present for" 改为更精确的 "should be present only for"。`writeStatsFile` 由 `private` 改为 `private static`，并删掉本地的 `statsFileLocation` 私有方法。
- 新增 `testExpireSnapshotsWithPartitionStatisticFiles`：
  1. 建表 + INSERT 一行（snapshot1），用 `ProcedureUtil.writePartitionStatsFile` 与 `ProcedureUtil.statsFileLocation` 构造 partitionStatsFile1 并 `table.updatePartitionStatistics().setPartitionStatistics(...).commit()` 提交；
  2. 再 INSERT 一行（snapshot2），刷新表，同样构造 partitionStatsFile2 并提交；
  3. `waitUntilAfter(currentSnapshot.timestampMillis())` 确保后续 expire 的时间阈值能覆盖两个快照；
  4. 调用 `system.expire_snapshots(older_than => now, table => ...)`，断言输出第 6 列（索引 5，"deleted partition statistics file count"）等于 1；
  5. 刷新表，断言 `table.partitionStatisticsFiles()` 仅剩 partitionStatisticsFile2（即 partitionStatisticsFile1 已从 TableMetadata 移除）；
  6. 文件层面断言 partitionStatsFileLocation1 不存在、partitionStatsFileLocation2 存在。

  这条测试直接验证了"修复后 expire_snapshots 会真的删掉过期快照对应的分区统计文件"。

### spark/v3.3/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRemoveOrphanFilesProcedure.java
### spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRemoveOrphanFilesProcedure.java
### spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRemoveOrphanFilesProcedure.java

**修改目的**：在 remove_orphan_files procedure 上验证分区统计文件在仍被引用时不被误删、在解除引用后能被识别为孤儿并删除。

**工作逻辑**（三个版本一致）：
- 新增 import `org.apache.iceberg.PartitionStatisticsFile`。
- 新增测试 `testRemoveOrphanFilesWithPartitionStatisticFiles`：
  1. CTAS 建表（format-version=2）`AS SELECT 10 int, 'abc' data`，拿到 `Table`；
  2. 用 `ProcedureUtil` 生成 partitionStatsLocation 与 `PartitionStatisticsFile`，通过 `commitPartitionStatsTxn(table, partitionStatisticsFile)` 把它注册到 TableMetadata（用 `table.newTransaction()` 包一层 `updatePartitionStatistics().setPartitionStatistics(...).commit()` 再 `commitTransaction()`，模拟独立事务提交）；
  3. `waitUntilAfter(now)` + `currentTimestamp` 让孤儿判定时间阈值足够新；
  4. 第一次调 `remove_orphan_files`：断言输出为空（即"无孤儿"），且分区统计文件**仍然存在**——这是修复前会失败的核心断言（修复前 partition stats 不在 reachable 白名单里，会被当成孤儿删掉）；
  5. 调 `removePartitionStatsTxn(table, partitionStatisticsFile)`：用事务把该 partitionStatisticsFile 从 TableMetadata 里移除（`removePartitionStatistics(snapshotId)`）；
  6. 第二次调 `remove_orphan_files`：断言输出恰好 1 条，且就是 `"file:" + partitionStatsLocation`；文件已被物理删除。
- 同时新增两个 `private static` 辅助方法：`removePartitionStatsTxn` 与 `commitPartitionStatsTxn`，分别封装"事务内 remove/set 分区统计"两个操作，便于测试复用。

  这条测试覆盖了 remove_orphan_files 对分区统计文件的两个关键状态：reachable（应保留）与 unreachable（应删除）。

## 小结

这是一个修补"新功能遗漏 GC 集成"的典型修复。模式上很清晰：
1. **核心层加统一枚举接口**：`ReachableFileUtil.statisticsFilesLocationsForSnapshots` 同时处理两类统计文件，按 snapshotId 过滤；
2. **调用层切换到新接口**：三个 Spark 版本的 `BaseSparkAction` 同步替换，所有依赖 `statisticsFileDS` 的 GC action 自动获得新行为；
3. **旧 API 标弃用**：给外部调用者一个迁移窗口，1.6.0 移除；
4. **测试双向覆盖**：expire_snapshots 验证"过期快照的分区统计会被删"，remove_orphan_files 验证"未解除引用的分区统计不会被误删、解除后会被删"。

影响面是数据安全级别的：修复前 remove_orphan_files 会误删仍然有效的分区统计文件，破坏表；expire_snapshots 会漏删，留下垃圾。提交同时把"分区统计文件"这一较新功能正式纳入 Iceberg 的 GC 与可达性框架，标志着该功能在工程上的成熟。
