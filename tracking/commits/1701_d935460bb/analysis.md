# 提交 1701 d935460bb 分析

## 提交信息
- 哈希：d935460bbed5da0ac205a67912b70fa682ee84df
- 日期：2025-02-08 00:49:15 -0800
- 作者：Hongyue/Steve Zhang
- 消息：Spark 3.5: Support Statistics Files in RewriteTablePath (#11929)

## 总体目的

本提交为 `RewriteTablePath` 动作补充对表统计文件（statistics files）的路径重写支持。此前 `RewriteTablePathSparkAction` 在检测到表含 statistics files 时会直接抛出 "Statistic files are not supported yet." 异常，阻止用户对带统计的表做路径重写。本提交实现了 statistics files 路径的改写与物理复制，让带统计的表也能完整迁移。

注意：本次只支持"表级统计文件（statisticsFiles）"，对"分区统计文件（partitionStatisticsFiles）"仍未支持，校验消息相应改为 "Partition statistics files are not supported yet."，留作后续 TODO。

Iceberg 的 statistics files 存储表的统计信息（如 NDV、分布等），路径记录在 TableMetadata 中。路径重写时需要：1）改写 TableMetadata 中 statisticsFiles 的 path 字段；2）把物理统计文件从源位置复制到目标位置（通过 copyPlan）。

## 如何达成设计目的

分两层实现：
- **Core 层（RewriteTablePathUtil）**：在 `replacePaths` 构建新 TableMetadata 时，对 `metadata.statisticsFiles()` 调用新增的 `updatePathInStatisticsFiles` 方法，把每个 StatisticsFile 的 path 用 `newPath(path, sourcePrefix, targetPrefix)` 改写，构造新的 `GenericStatisticsFile`（其他字段 snapshotId/fileSizeInBytes/fileFooterSizeInBytes/blobMetadata 不变）。`partitionStatisticsFiles` 暂不处理（保留 TODO 注释）。
- **Spark 层（RewriteTablePathSparkAction）**：把 statistics files 纳入 copyPlan，让物理复制阶段把它们从源位置（经 stagingDir 中转）复制到目标位置。具体是在 `rewriteVersionFile` 方法中，对每个 version file 对应的 metadata，除了把 version file 本身加入 copyPlan，还调用新增的 `statsFileCopyPlan` 计算统计文件的 copyPlan 条目并一起加入。

### 修改详情

#### core/src/main/java/org/apache/iceberg/RewriteTablePathUtil.java
共 19 行变更。

1. 新增 `import java.util.stream.Collectors;`（注意：1700 提交移除了此 import，本提交重新加回，因为新增方法用到）。

2. `replacePaths` 方法（约 128 行，构建新 TableMetadata 的 buildFrom 链）：
   - 原代码：`// TODO: update statistic file paths` 后直接 `metadata.statisticsFiles(),`
   - 新代码：`updatePathInStatisticsFiles(metadata.statisticsFiles(), sourcePrefix, targetPrefix),` 紧接 `// TODO: update partition statistics file paths` 后 `metadata.partitionStatisticsFiles(),`
   - 改动点：statisticsFiles 改为调用新方法做路径改写；partitionStatisticsFiles 保持原样但 TODO 注释移到它上面。

3. 新增私有静态方法 `updatePathInStatisticsFiles`（约 161 行）：
   ```java
   private static List<StatisticsFile> updatePathInStatisticsFiles(
       List<StatisticsFile> statisticsFiles, String sourcePrefix, String targetPrefix) {
     return statisticsFiles.stream()
         .map(existing -> new GenericStatisticsFile(
             existing.snapshotId(),
             newPath(existing.path(), sourcePrefix, targetPrefix),
             existing.fileSizeInBytes(),
             existing.fileFooterSizeInBytes(),
             existing.blobMetadata()))
         .collect(Collectors.toList());
   }
   ```
   - 对每个 StatisticsFile 构造一个新的 GenericStatisticsFile，仅 path 字段改写，其余字段（snapshotId、fileSizeInBytes、fileFooterSizeInBytes、blobMetadata 列表）原样保留。
   - `newPath` 是 RewriteTablePathUtil 中已有的公共静态方法（1708 提交也会用），返回 `maybeAppendFileSeparator(targetPrefix) + relativize(path, sourcePrefix)`。

#### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java
共 47 行变更。

1. 新增 `import org.apache.iceberg.StatisticsFile;`。

2. 前置校验（约 273 行，`execute` 或类似入口方法）：
   - 原代码：
     ```java
     Preconditions.checkArgument(
         endMetadata.statisticsFiles() == null || endMetadata.statisticsFiles().isEmpty(),
         "Statistic files are not supported yet.");
     ```
   - 新代码：
     ```java
     Preconditions.checkArgument(
         endMetadata.partitionStatisticsFiles() == null
             || endMetadata.partitionStatisticsFiles().isEmpty(),
         "Partition statistics files are not supported yet.");
     ```
   - 改动点：校验对象从 statisticsFiles 改为 partitionStatisticsFiles；消息从 "Statistic files are not supported yet." 改为 "Partition statistics files are not supported yet."。statisticsFiles 不再被拦截，进入正常处理流程。

3. `rewriteVersionFiles` 方法（约 343 行）：
   - 两处 `result.copyPlan().add(rewriteVersionFile(...))` 改为 `result.copyPlan().addAll(rewriteVersionFile(...))`。
   - 原因：`rewriteVersionFile` 的返回类型从 `Pair<String, String>` 改为 `Set<Pair<String, String>>`（一个 version file 现在可能对应多条 copyPlan 条目：version file 本身 + 该 version 引用的所有 statistics files）。

4. `rewriteVersionFile` 方法签名与实现重写（约 359 行）：
   - 原签名：`private Pair<String, String> rewriteVersionFile(TableMetadata metadata, String versionFilePath)`
   - 新签名：`private Set<Pair<String, String>> rewriteVersionFile(TableMetadata metadata, String versionFilePath)`
   - 新实现工作逻辑：
     1. 创建 `Set<Pair<String, String>> result = Sets.newHashSet();`
     2. 计算 stagingPath，调用 `RewriteTablePathUtil.replacePaths(metadata, sourcePrefix, targetPrefix)` 得到 `newTableMetadata`（其中 statisticsFiles 已被改写路径），`TableMetadataParser.overwrite(newTableMetadata, ...)` 写入 staging。
     3. 把 version file 本身的 copyPlan 条目（stagingPath → 目标路径）加入 result。
     4. 调用 `statsFileCopyPlan(metadata.statisticsFiles(), newTableMetadata.statisticsFiles())` 计算统计文件的 copyPlan，addAll 到 result。
     5. 返回 result。
   - 关键点：`newTableMetadata` 的 statisticsFiles 路径已经是目标路径（由 `replacePaths` 改写），而原 `metadata.statisticsFiles()` 路径是源路径，因此 `statsFileCopyPlan` 用 `before.path()`（源路径）经 stagingDir 中转、`after.path()`（目标路径）作为目的地。

5. 新增私有方法 `statsFileCopyPlan`（约 380 行）：
   ```java
   private Set<Pair<String, String>> statsFileCopyPlan(
       List<StatisticsFile> beforeStats, List<StatisticsFile> afterStats) {
     Set<Pair<String, String>> result = Sets.newHashSet();
     if (beforeStats.isEmpty()) {
       return result;
     }
     Preconditions.checkArgument(
         beforeStats.size() == afterStats.size(),
         "Before and after path rewrite, statistic files count should be same");
     for (int i = 0; i < beforeStats.size(); i++) {
       StatisticsFile before = beforeStats.get(i);
       StatisticsFile after = afterStats.get(i);
       Preconditions.checkArgument(
           before.fileSizeInBytes() == after.fileSizeInBytes(),
           "Before and after path rewrite, statistic file size should be same");
       result.add(
           Pair.of(RewriteTablePathUtil.stagingPath(before.path(), stagingDir), after.path()));
     }
     return result;
   }
   ```
   - 工作逻辑：遍历改写前后的 statistics files（按索引对应），校验数量与文件大小一致（路径改写不应改变文件大小），为每个文件生成 copyPlan 条目（源路径经 stagingDir 中转 → 目标路径）。
   - 注意：这里假设 `beforeStats` 与 `afterStats` 的顺序一致且一一对应。由于 `updatePathInStatisticsFiles` 用 stream.map 保持了原顺序，这个假设成立。

#### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteTablePathsAction.java
共 65 行变更。

1. import 调整：移除 `GenericStatisticsFile`、`ImmutableList`；新增 `ImmutableGenericPartitionStatisticsFile`。

2. 原 `testStatisticFile` 测试改名为 `testPartitionStatisticFile`，并改为构造分区统计文件（用 `ImmutableGenericPartitionStatisticsFile` + `setPartitionStatistics`），断言消息改为 "Partition statistics files are not supported yet"。该测试现在验证的是"分区统计文件仍被拦截"。

3. 新增测试 `testTableWithManyStatisticFiles`：
   - 创建 v2 表，循环 10 次：每次 `sql("insert into ... values (...)")` 插入一行，`sourceTable.refresh()`，`actions().computeTableStats(sourceTable).execute()` 计算统计。
   - 断言 `sourceTable.statisticsFiles().size() == 10`。
   - 执行 `rewriteTablePath`，调用 `checkFileNum(iterations*2+1, iterations, iterations, iterations, iterations*6+1, result)`：
     - version file 数 = 21（10 次插入 + 10 次 computeTableStats 各产生一个 version + 1 个初始 = 21）。实际 10 次循环每次 insert 产生 1 个 version、每次 computeTableStats 产生 1 个 version，共 20，加初始 1 共 21，即 iterations*2+1。
     - manifest list 数 = 10（10 次 insert）。
     - manifest 数 = 10。
     - statistics file 数 = 10。
     - 总文件数 = 61（21 version + 10 manifest list + 10 manifest + 10 stats + 10 data file = 61，即 iterations*6+1）。

4. `checkFileNum` 方法新增重载：
   - 原 4 参数版本（versionFileCount, manifestListCount, manifestFileCount, totalCount）改为委托给新的 5 参数版本，statisticsFileCount 传 0。
   - 新 5 参数版本（versionFileCount, manifestListCount, manifestFileCount, statisticsFileCount, totalCount）增加对 `.stats` 后缀文件的计数断言：
     ```java
     assertThat(filesToMove.stream().filter(f -> f.endsWith(".stats")).count())
         .withFailMessage("Wrong rebuilt Statistic file count")
         .isEqualTo(statisticsFileCount);
     ```

## 小结

本次提交为 RewriteTablePath 补齐了表级统计文件的路径重写与物理复制能力，移除了"Statistic files are not supported yet"的硬拦截。Core 层负责 metadata 中路径字段的改写，Spark 层负责把统计文件纳入 copyPlan 完成物理复制。新增测试 `testTableWithManyStatisticFiles` 通过 10 次插入+统计计算的场景端到端验证了文件计数。分区统计文件仍未支持，保留了校验与 TODO。

回迁到 1.4.x 的注意事项：
1. Core 修改（`updatePathInStatisticsFiles` 与 `replacePaths` 调用）独立且局部，回迁风险低。依赖 `GenericStatisticsFile` 的构造函数签名（snapshotId, path, fileSizeInBytes, fileFooterSizeInBytes, blobMetadata），确认 1.4.x 一致。
2. Spark 修改依赖 `RewriteTablePathUtil.replacePaths` 已处理 statisticsFiles（即 Core 修改必须先回迁）。
3. `rewriteVersionFile` 返回类型从 `Pair` 改为 `Set<Pair>` 是破坏性变更，调用方 `rewriteVersionFiles` 的 `copyPlan().add` 改为 `addAll` 必须同步回迁。
4. `statsFileCopyPlan` 假设 before/after 列表顺序一致且一一对应，依赖 `updatePathInStatisticsFiles` 用 stream.map 保持顺序。若 1.4.x 的 statisticsFiles 实现顺序不同，需重新确认。
5. 测试 `testTableWithManyStatisticFiles` 依赖 `actions().computeTableStats(sourceTable).execute()` 与 `.stats` 文件后缀，回迁时确认 1.4.x 的统计文件命名规则一致。
6. 该提交与 1697、1700 同属 RewriteTablePathUtil 修复系列，回迁时注意 `replacePaths`/`rewriteManifestList` 附近的合并冲突，建议按 1697 → 1700 → 1701 顺序回迁。
7. 分区统计文件（partitionStatisticsFiles）的支持是后续工作，回迁时不要误把 partitionStatisticsFiles 的校验也去掉。
