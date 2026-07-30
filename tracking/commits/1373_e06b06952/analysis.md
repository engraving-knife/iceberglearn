# 提交 1373：Core, Flink, Spark: Test DVs with format-version=3 (#11485)

## 提交信息

- **序号**：1373 / 4088
- **哈希**：e06b069529be3d3d389b156646e751de3753feb0
- **短哈希**：e06b06952
- **日期**：2024-11-13（Wed Nov 13 16:42:06 2024 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core, Flink, Spark: Test DVs with format-version=3 (#11485)
- **PR/Issue**：#11485

## 总体目的

Iceberg 在 format-version=3 中引入了 Deletion Vectors（DV）作为新的删除文件形式：DV 以 Puffin 文件格式存储，与 v2 中基于 Parquet/ORC 的位置删除（position delete）和等值删除（equality delete）在文件结构、尺寸语义和读取路径上都有差异。在此提交之前，大量 Core/Flink/Spark 的 actions 与元数据表测试只在 format-version=2 下运行，意味着 DV 路径（写入、扫描、rewrite、orphan 清理、expire 等）几乎没有被测试覆盖，存在隐性回归风险。

本提交的目标是让既有测试矩阵同时覆盖 v2 和 v3，使 DV 路径与 position-delete 路径在相同的 action 行为下都被验证。为此需要做两件事：

1. 调整一处生产代码：BaseContentScanTask.length() 原本返回 file.fileSizeInBytes()，对 DV 文件而言这不正确——DV 的"内容大小"（contentSizeInBytes）才是 split/规划时应使用的尺寸。改为调用 ScanTaskUtil.contentSizeInBytes(file)，让 v3 下 DV 任务的长度计算与 v2 一致语义。
2. 改造测试基础设施：为 FileHelpers 增加 formatVersion 参数重载，在 v3 下用 BaseDVFileWriter（Puffin）写删除文件，v2 下继续用 PositionDeleteWriter（Parquet）；将 Flink/Spark 多个 action 测试参数化，新增 formatVersion ∈ {2, 3} 维度，并在涉及删除的用例中按版本写 DV 或 position delete。

## 如何达成设计目的

生产代码侧（仅 2 处，最小改动）：
- BaseContentScanTask.length() 改用 ScanTaskUtil.contentSizeInBytes(file)。该工具由 PR #11446（提交 ec269ee3e）引入，对 DATA 文件返回 fileSizeInBytes()，对 DELETE 文件则判断是否为 DV（Puffin 格式）：是 DV 返回 contentSizeInBytes()，否则返回 fileSizeInBytes()。这样扫描任务在 split 时对 DV 使用内容尺寸，避免按整个 Puffin 文件大小高估任务长度。
- BaseFileScanTask.SplitScanTask.merge() 仅新增注释，说明为何直接传 deletesSizeBytes 而非在合并时重新计算（保持每次合并不重复计算，最终合并完成后再统一计算一次）。

测试侧（主体）：
- 在 FileHelpers 中为 writeDeleteFile 和 writePosDeleteFile 增加 int formatVersion 重载。formatVersion >= 3 时构造 OutputFileFactory（Puffin 格式）和 BaseDVFileWriter，逐条调用 closeableWriter.delete(path, row, spec, partition) 写 DV；否则走原有 PositionDeleteWriter 路径。原无参版本委托到 formatVersion=2，保持向后兼容。
- 将 Flink TestRewriteDataFilesAction、Spark TestRemoveOrphanFilesAction/TestRemoveOrphanFilesAction3/TestRewriteDataFilesAction/TestRewriteManifestsAction/TestExpireSnapshotsAction/TestRemoveDanglingDeleteAction/TestDeleteReachableFilesAction 等测试类用 @ExtendWith(ParameterizedTestExtension.class) 参数化，新增 @Parameter int formatVersion 与 @Parameters(name = "formatVersion = {0}") 返回 Arrays.asList(2, 3)，建表时通过 TableProperties.FORMAT_VERSION 属性传入版本。
- 在写删除文件的测试点用 if (formatVersion >= 3) { writeDV(...) } else { 原位置删除 } 分支生成对应类型的删除文件。
- 对仅适用于 v2+ 的用例加 assumeThat(formatVersion).isGreaterThanOrEqualTo(2)；对当前在 DV 下已知的失败用例（如 testWapFilesAreKept）加 assumeThat(formatVersion).as("currently fails with DVs").isEqualTo(2) 并附 TODO 注释，标记已知问题暂不阻塞。
- TestMetadataTableFilters/TestMetadataTableScans 在参数列表中补 3，把 formatVersion == 2 的断言改为 >= 2，并通过新增 posDelete(Table, DataFile) 辅助方法按版本生成 DV 或 position delete（用 FileGenerationUtil.generateDV / generatePositionDeleteFile）。

## 修改详情

### core/src/main/java/org/apache/iceberg/BaseContentScanTask.java

修改目的：让扫描任务长度对 DV 文件使用内容尺寸。

工作逻辑：新增 import org.apache.iceberg.util.ScanTaskUtil;，并将 length() 由 return file.fileSizeInBytes(); 改为 return ScanTaskUtil.contentSizeInBytes(file);。这是本提交唯一的行为性生产代码改动，确保 v3 下 DV 任务的 split/估算更准确。

### core/src/main/java/org/apache/iceberg/BaseFileScanTask.java

修改目的：补充注释说明合并逻辑。

工作逻辑：在 SplitScanTask.merge() 内 return new SplitScanTask(offset, len + that.length(), fileScanTask, deletesSizeBytes); 之前新增两行注释，说明这里不调用 deletesSizeBytes() 是为了让删除尺寸只在合并完成后计算一次，而非每次合并都重算。无代码行为变化。

### data/src/test/java/org/apache/iceberg/data/FileHelpers.java

修改目的：提供按 format-version 写 DV 或 position delete 的测试辅助。

工作逻辑：
- 新增 writeDeleteFile(table, out, deletes, formatVersion) 与 writeDeleteFile(table, out, partition, deletes, formatVersion) 重载。formatVersion >= 3 时：构造 OutputFileFactory.builderFor(table, 1, 1).format(FileFormat.PUFFIN).build()，创建 BaseDVFileWriter，对每条 (path, pos) 调用 closeableWriter.delete(path.toString(), pos, table.spec(), partition)，最后返回 Iterables.getOnlyElement(writer.result().deleteFiles()) 与 referencedDataFiles()。否则走原 PositionDeleteWriter 路径。原无 formatVersion 的重载委托到 v2。
- 同样为 writePosDeleteFile 增加 formatVersion 重载，v3+ 用 BaseDVFileWriter 写 PositionDelete 列表，v2 走原路径。

### core/src/test/java/org/apache/iceberg/TestMetadataTableFilters.java

修改目的：扩展元数据表过滤测试到 v3。

工作逻辑：在 parameters() 中为每个 MetadataTableType 补一行 new Object[] {3, ...}；把 if (formatVersion == 2) 改为 if (formatVersion >= 2)；把 testPartitionSpecEvolutionRemovalV2/testPartitionSpecEvolutionAdditiveV2 的 assumeThat(formatVersion).isEqualTo(2) 改为 isGreaterThanOrEqualTo(2)，并把后者重命名为 ...V2AndAbove；新增私有方法 posDelete(Table, DataFile) 按 formatVersion >= 3 调用 FileGenerationUtil.generateDV 或 generatePositionDeleteFile，替换原本手工构造 DeleteFile 的样板代码。

### core/src/test/java/org/apache/iceberg/TestMetadataTableScans.java

修改目的：让删除文件相关断言在 v3 也生效。

工作逻辑：将 if (formatVersion == 2) 改为 if (formatVersion >= 2)，使 DeleteFilesTable/AllDeleteFilesTable 的估算行数断言在 v3 下也执行。

### flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/actions/TestRewriteDataFilesAction.java

修改目的：Flink rewrite 测试参数化到 v2/v3。

工作逻辑：新增 @Parameter(index = 3) int formatVersion，@Parameters 在原 format 循环外再套一层 for (int version : Arrays.asList(2, 3))，建表 SQL 改为带 'format-version' 表属性；其余测试方法在建表/写删除时按版本走 DV 或 position delete。

### Spark actions 测试（spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/）

涉及 TestRemoveOrphanFilesAction.java、TestRemoveOrphanFilesAction3.java、TestRewriteDataFilesAction.java、TestRewriteManifestsAction.java、TestExpireSnapshotsAction.java、TestRemoveDanglingDeleteAction.java、TestDeleteReachableFilesAction.java。

修改目的：让 Spark actions 在 v2/v3 双版本下运行，覆盖 DV 路径。

工作逻辑（共性）：
- 加 @ExtendWith(ParameterizedTestExtension.class)，新增 @Parameter int formatVersion 与 @Parameters(name = "formatVersion = {0}") 返回 Arrays.asList(2, 3)。
- 建表时把 Maps.newHashMap() 替换为 ImmutableMap.of(TableProperties.FORMAT_VERSION, String.valueOf(formatVersion))（或在原有 props 上 putAll(properties)）。
- 把 @Test 改为 @TestTemplate。
- 在需要写删除文件的地方用 if (formatVersion >= 3) { writeDV(...) } else { 原位置删除 }。
- TestRewriteDataFilesAction 新增私有方法 writeDV(Table, StructLike partition, String dataFileLocation, int positionsToDelete)：构造 Puffin OutputFileFactory 与 BaseDVFileWriter，循环 closeableWriter.delete(path, row, table.spec(), partition)，返回 writer.result().deleteFiles()。
- 对仅 v2+ 的用例加 assumeThat(formatVersion).isGreaterThanOrEqualTo(2)。
- TestRemoveOrphanFilesAction.testWapFilesAreKept 加 assumeThat(formatVersion).as("currently fails with DVs").isEqualTo(2) 并附 TODO 注释，标记 DV 与 WAP 分支交互的已知问题（DV 会删除 WAP 暂存分支中的数据），暂不在 v3 下强制通过。

## 小结

- 成效：Core/Flink/Spark 大量 action 与元数据表测试现已在 format-version=2 和 =3 双版本下运行，DV（Puffin）写入与读取路径获得与 position delete 等价的测试覆盖；同时修复了 BaseContentScanTask.length() 对 DV 文件尺寸计算不准确的问题，使 v3 下扫描任务 split 更合理。
- 影响范围：生产代码仅 2 处（1 处行为改动 + 1 处注释），其余全部为测试改造。无对外 API 变更。运行时行为变化仅体现在 v3 表的扫描任务长度估算上（更准确）。
- 回迁到 1.4.x 的注意事项：不建议直接回迁，理由与前置条件如下：
  1. 强依赖前置提交：本提交使用 ScanTaskUtil.contentSizeInBytes(file) 与 DeleteFile.contentSizeInBytes() 方法，这些由 PR #11446（提交 ec269ee3e）引入，1.4.x 分支当前没有该工具类与 DeleteFile.content/offset 字段。若要回迁本提交的 BaseContentScanTask.length() 改动，必须先回迁 #11446，否则编译失败。
  2. 测试基础设施依赖：测试中用到的 BaseDVFileWriter、DVFileWriter、FileGenerationUtil.generateDV 等 DV 写入工具链也依赖 v3 DV 功能整体回迁情况。若 1.4.x 不打算支持 v3/DV，则本提交的测试改造无意义。
  3. 价值判断：若 1.4.x 维护分支已决定支持 v3（即已回迁 DV 相关特性），则本提交（连同 #11446）应一并回迁，以保证 v3 下扫描任务尺寸正确性与测试覆盖；若 1.4.x 不支持 v3，则无需回迁。建议先确认 1.4.x 的 v3 支持范围再决定。
  4. 已知问题：testWapFilesAreKept 在 DV 下会失败（TODO 标注），回迁时需注意此用例在 v3 下被 assumeThat 跳过，不要误以为是回归。
