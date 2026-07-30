# 提交 3108：Core, Data, Spark: Use partition stats scan API in tests (#14996)

## 提交信息

- **序号**：3108 / 4088
- **哈希**：42c7f475d2cde9c53fb6578af24c64d407c9b6b1
- **短哈希**：42c7f475d
- **日期**：2026-01-13
- **作者**：gaborkaszab
- **提交说明**：Core, Data, Spark: Use partition stats scan API in tests (#14996)
- **PR/Issue**：#14996

## 总体目的

本提交是一次测试层面的重构，目的是让分区统计（partition statistics）相关测试不再直接调用内部读取方法 `PartitionStatsHandler.readPartitionStatsFile(...)`，而是改用面向用户的公共扫描 API `table.newPartitionStatisticsScan().scan()`。此前测试为了校验写入的分区统计，需要手动构造 schema（`PartitionStatsHandler.schema(partitionType, 2)`）、用 `Files.localInput(statisticsFile.path())` 打开底层文件、再以内部类型 `PartitionStats` 读取记录。这种方式绕过了表的快照语义，直接读取物理文件，既耦合了内部实现细节（schema 列数、文件路径），也没有真正测试用户实际会调用的扫描入口。

更重要的是，公共扫描 API 是快照感知的（`useSnapshot(snapshotId)`），统计文件必须通过 `updatePartitionStatistics().setPartitionStatistics(...).commit()` 关联到某个快照后才能被扫描到。因此本提交在 `PartitionStatsHandlerTestBase` 等测试中补上了"先 append 一个 dummy 数据文件产生快照、再用真实快照 id 写统计文件、最后用 scan API 读取"的完整链路，使测试覆盖的正是用户真实使用路径，而不是一个只存在于测试里的旁路读取。同时把原本散落在 `PartitionStatisticsScanTestBase` 与 `PartitionStatsHandlerTestBase` 中的两份 `isEqual` 比较辅助方法统一上移到父类 `PartitionStatisticsTestBase`，消除重复代码，并因为统一改用公共类型 `PartitionStatistics` 而不再需要 `PartitionStats` 与 `PartitionStatistics` 混合比较的私有重载。

## 如何达成设计目的

整体思路是把所有"读分区统计"的测试代码统一替换为 `table.newPartitionStatisticsScan()...scan()`，类型从内部的 `PartitionStats`/`PartitionStatisticsFile` 切换为公共的 `PartitionStatistics`，并删除不再需要的 schema 构造与 `Files.localInput` 调用。涉及文件覆盖 core 测试基类、data 模块 JMH 基准测试，以及 Spark v3.4/v3.5/v4.0/v4.1 四个版本的 `TestComputePartitionStatsAction`、`TestRewriteDataFilesProcedure`、`TestRewriteManifestsProcedure` 测试。各 Spark 版本的改动模式完全一致，属于跨版本同步。

## 修改详情

### `core/src/test/java/org/apache/iceberg/PartitionStatisticsScanTestBase.java` (+0/-26 lines)

**修改目的**：移除本类中的 `isEqual` 辅助方法，统一上移到父类。

**工作逻辑**：删除了 `protected static boolean isEqual(Comparator<StructLike>, PartitionStatistics, PartitionStatistics)` 方法及其 `import java.util.Objects`。该方法逐字段比较两条 `PartitionStatistics`（specId、各 count、文件大小、删除记录数、lastUpdatedAt 等），现由父类 `PartitionStatisticsTestBase` 提供同一实现，避免重复。

### `core/src/test/java/org/apache/iceberg/PartitionStatisticsTestBase.java` (+27/-0 lines)

**修改目的**：在父类中提供统一的 `isEqual` 比较方法。

**工作逻辑**：新增 `import java.util.Comparator` 与 `Objects`，并把上述 `isEqual(Comparator<StructLike>, PartitionStatistics, PartitionStatistics)` 方法添加到该基类。这样所有继承自 `PartitionStatisticsTestBase` 的测试（包括 `PartitionStatisticsScanTestBase`、`PartitionStatsHandlerTestBase`）都能共享同一份字段比较逻辑。

### `core/src/test/java/org/apache/iceberg/PartitionStatsHandlerTestBase.java` (+59/-59 lines)

**修改目的**：用公共 scan API 替代内部 `readPartitionStatsFile`，并补齐快照链路。

**工作逻辑**：
- 三处读取统计的代码块改为：先 `testTable.newAppend().appendFile(dataFile).commit()` 产生快照，拿到 `snapshotId`；再用 `updatePartitionStatistics().setPartitionStatistics(PartitionStatsHandler.writePartitionStatsFile(testTable, snapshotId, dataSchema, expected)).commit()` 把统计文件关联到该快照；最后用 `testTable.newPartitionStatisticsScan().useSnapshot(snapshotId).scan()` 读取，返回类型由 `List<PartitionStats>` 改为 `List<PartitionStatistics>`。
- 另一处用 `computeAndWriteStatsFile` 的场景同样改为先 `updatePartitionStatistics().setPartitionStatistics(...).commit()` 再 `testTable.newPartitionStatisticsScan().scan()` 读取，并据此校验压缩/删除前后 `dataRecordCount`/`dataFileCount` 是否归零。
- 删除了本类私有的 `isEqual(Comparator, PartitionStats, PartitionStatistics)` 重载（它此前要混合比较内部类型 `PartitionStats` 与公共类型 `PartitionStatistics`），现在统一用从父类继承的、基于 `PartitionStatistics` 的 `isEqual`。

### `data/src/jmh/java/org/apache/iceberg/PartitionStatsHandlerBenchmark.java` (+8/-7 lines)

**修改目的**：JMH 基准测试改用 scan API 读取统计。

**工作逻辑**：`benchmarkPartitionStats` 中把 `PartitionStatsHandler.computeAndWriteStatsFile(table)` 的结果通过 `updatePartitionStatistics().setPartitionStatistics(...).commit()` 提交，再用 `table.newPartitionStatisticsScan().scan()` 读取，类型从 `List<PartitionStats>` 改为 `List<PartitionStatistics>`，删除了对 `PartitionStatsHandler.schema(...)` 与 `Files.localInput(...)` 的依赖，使基准测试度量的是公共 API 路径。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestComputePartitionStatsAction.java` (+20/-27 lines)

**修改目的**：Spark 3.4 的计算分区统计 action 测试改用 scan API 校验结果。

**工作逻辑**：`validatePartitionStats` 方法签名由 `(PartitionStatisticsFile result, Schema recordSchema, Tuple... expectedValues)` 改为 `(Table table, long snapshotId, Tuple... expectedValues)`；内部读取由 `PartitionStatsHandler.readPartitionStatsFile(recordSchema, Files.localInput(result.path()))` 改为 `table.newPartitionStatisticsScan().useSnapshot(snapshotId).scan()`；`extracting` 的方法引用从 `PartitionStats::xxx` 全部改为 `PartitionStatistics::xxx`；调用处相应传入 `table` 与 `statisticsFile.snapshotId()`，并删除不再使用的 `dataSchema` 局部变量与相关 import（`Files`、`PartitionStats`、`Schema`、`PartitionStatsHandler`）。

### `spark/v3.5/spark-extensions/.../TestRewriteDataFilesProcedure.java` (+24/-19 lines)

**修改目的**：Spark 3.5 压缩数据文件过程测试改用 scan API 读取压缩前后的分区统计。

**工作逻辑**：压缩前/后都用 `table.updatePartitionStatistics().setPartitionStatistics(PartitionStatsHandler.computeAndWriteStatsFile(table)).commit()` 提交统计，再用 `table.newPartitionStatisticsScan().scan()` 读取为 `List<PartitionStatistics>`；比较循环中变量类型由 `PartitionStats` 改为 `PartitionStatistics`；删除 `Files`、`PartitionStatisticsFile`、`PartitionStats`、`Partitioning`、`Schema` 等不再使用的 import。压缩后不再显式传 `currentSnapshot().snapshotId()` 给 `computeAndWriteStatsFile`（改用默认重载）。

### `spark/v3.5/spark-extensions/.../TestRewriteManifestsProcedure.java` (+21/-22 lines)

**修改目的**：Spark 3.5 重写 manifests 过程测试改用 scan API 读取前后的分区统计。

**工作逻辑**：与 `TestRewriteDataFilesProcedure` 完全对称——`statsBeforeRewrite`/`statsAfterRewrite` 改用 `table.newPartitionStatisticsScan().scan()` 读取为 `List<PartitionStatistics>`，统计提交改用 `updatePartitionStatistics().setPartitionStatistics(...).commit()` 链式写法，比较变量类型改为 `PartitionStatistics`，清理冗余 import。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestComputePartitionStatsAction.java` (+23/-27 lines)

**修改目的**：Spark 3.5 计算分区统计 action 测试改用 scan API。

**工作逻辑**：与 v3.4 同名文件改动一致——`validatePartitionStats` 改为接收 `Table` 与 `snapshotId`，用 `table.newPartitionStatisticsScan().useSnapshot(snapshotId).scan()` 读取，方法引用改为 `PartitionStatistics::xxx`，清理 import。

### `spark/v4.0/spark-extensions/.../TestRewriteDataFilesProcedure.java` (+24/-19 lines)

**修改目的**：Spark 4.0 压缩数据文件过程测试改用 scan API。

**工作逻辑**：与 v3.5 同名文件改动完全一致。

### `spark/v4.0/spark-extensions/.../TestRewriteManifestsProcedure.java` (+21/-22 lines)

**修改目的**：Spark 4.0 重写 manifests 过程测试改用 scan API。

**工作逻辑**：与 v3.5 同名文件改动完全一致。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/actions/TestComputePartitionStatsAction.java` (+23/-27 lines)

**修改目的**：Spark 4.0 计算分区统计 action 测试改用 scan API。

**工作逻辑**：与 v3.4/v3.5 同名文件改动一致。

### `spark/v4.1/spark-extensions/.../TestRewriteDataFilesProcedure.java` (+24/-19 lines)

**修改目的**：Spark 4.1 压缩数据文件过程测试改用 scan API。

**工作逻辑**：与 v3.5/v4.0 同名文件改动完全一致。

### `spark/v4.1/spark-extensions/.../TestRewriteManifestsProcedure.java` (+21/-22 lines)

**修改目的**：Spark 4.1 重写 manifests 过程测试改用 scan API。

**工作逻辑**：与 v3.5/v4.0 同名文件改动完全一致。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/actions/TestComputePartitionStatsAction.java` (+23/-27 lines)

**修改目的**：Spark 4.1 计算分区统计 action 测试改用 scan API。

**工作逻辑**：与其它版本同名文件改动一致。

## 总结

本提交把分区统计相关测试从"直接读取物理统计文件（`PartitionStatsHandler.readPartitionStatsFile` + 手工 schema）"统一迁移到公共扫描 API `table.newPartitionStatisticsScan()...scan()`，类型从内部 `PartitionStats` 切换为公共 `PartitionStatistics`，并补齐了"append 数据文件产生快照→提交统计文件→快照感知扫描"的真实链路；同时把 `isEqual` 比较辅助方法上移到父类消除重复。其核心价值在于让测试覆盖用户实际使用的 API 路径、解耦对内部实现的依赖，并保证 core/data 与四个 Spark 版本的测试行为一致。
