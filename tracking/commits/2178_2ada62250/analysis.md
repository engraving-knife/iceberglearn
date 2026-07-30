# 提交 2178：Core: Fix incremental compute of partition stats for various edge cases (#13163)

## 提交信息

- **序号**：2178 / 4088
- **哈希**：2ada622509ad1e57c9df70016875c8bbf119cb95
- **短哈希**：2ada62250
- **日期**：2025-05-29 16:50:49 +0530
- **作者**：Ajantha Bhat
- **提交说明**：Core: Fix incremental compute of partition stats for various edge cases (#13163)
- **PR/Issue**：#13163

## 总体目的

此提交修复了分区统计（partition stats）增量计算中的多个边缘情况 bug。分区统计是 Iceberg 中用于跟踪每个分区的数据文件数量、记录数量等统计信息的功能。增量计算通过比较当前快照与上一次统计的快照之间的差异来高效更新统计信息。原实现存在几个问题：1）增量计算时依赖最新快照的 manifest 列表来过滤，但 DELETED 类型的 manifest 条目不会传递到后续快照中，导致删除操作的数据无法正确反映；2）当统计文件属于不同快照引用（如分支或标签）时，原代码抛出异常而非回退到全量计算；3）使用 `Files.localInput` 读取统计文件，在非本地文件系统上会失败。此提交解决了这些问题。

## 如何达成设计目的

- 重构 `computeStatsDiff` 方法，改为遍历两个快照之间的所有快照，收集每个快照自己添加的 manifest，而非仅从最新快照过滤
- 重构 `computeStats` 方法签名，直接接收 manifest 列表而非快照和谓词
- 当统计文件在当前快照链中不可访问时（如属于分支），返回 null 触发全量计算，而非抛出异常
- 将 `Files.localInput` 替换为 `table.io().newInputFile()` 以支持远程文件系统
- 添加测试用例覆盖 CopyOnWrite 删除场景和分支场景

## 修改详情

### `core/src/main/java/org/apache/iceberg/PartitionStatsHandler.java` (修改, +24/-20 lines)

**修改目的**：修复增量计算的多个边缘情况。

**工作逻辑**：
- 移除 `Set`、`Predicate`、`Sets` 的 import，添加 `StreamSupport` 的 import
- `computeAndWriteStatsFile` 中无统计文件时，改为直接调用 `computeStats(table, snapshot.allManifests(table.io()), false)` 而非使用谓词 `file -> true`
- `readPartitionStatsFile` 调用从 `Files.localInput(previousStatsFile.path())` 改为 `table.io().newInputFile(previousStatsFile.path())`
- `latestStatsFile` 方法中，找不到有效快照的统计文件时，从抛出 `RuntimeException` 改为返回 null，触发调用方的全量计算回退
- `computeStatsDiff` 方法重构：不再使用 `ancestorIdsBetween` 获取快照 ID 集合再用谓词过滤，而是使用 `ancestorsBetween` 获取快照列表，然后对每个快照收集其自己添加的 manifest（通过 `file.snapshotId().equals(snapshot.snapshotId())` 过滤），解决了 DELETED manifest 不传递的问题
- `computeStats` 方法签名从 `(Table, Snapshot, Predicate<ManifestFile>, boolean)` 改为 `(Table, List<ManifestFile>, boolean)`，直接接收 manifest 列表

### `core/src/test/java/org/apache/iceberg/PartitionStatsHandlerTestBase.java` (修改, +88/-4 lines)

**修改目的**：添加边缘情况测试并修复测试中的文件读取方式。

**工作逻辑**：
- 将多处 `Files.localInput(statisticsFile.path())` 改为 `testTable.io().newInputFile(statisticsFile.path())`
- 添加 `testCopyOnWriteDelete` 测试：验证删除所有文件后，分区统计中的记录数和文件数正确递减为零
- 添加 `testLatestStatsFileWithBranch` 测试：验证当统计文件属于主分支而查询分支快照时，`latestStatsFile` 返回 null（触发全量计算回退）

### `orc/src/test/java/org/apache/iceberg/TestOrcPartitionStatsHandler.java` (修改, +14/-0 lines)

**修改目的**：为 ORC 格式覆盖新添加的测试方法。

**工作逻辑**：由于 ORC 格式不支持写入分区统计文件，重写 `testLatestStatsFileWithBranch` 和 `testCopyOnWriteDelete` 方法，验证它们会抛出 `UnsupportedOperationException`。

## 总结

此提交修复了分区统计增量计算中的三个关键问题：1）DELETED manifest 不传递导致删除操作统计不正确；2）分支/标签场景下统计文件不可访问时抛异常而非回退全量计算；3）使用 localInput 限制了远程文件系统支持。重构了 `computeStatsDiff` 和 `computeStats` 方法，并添加了全面的测试覆盖。
