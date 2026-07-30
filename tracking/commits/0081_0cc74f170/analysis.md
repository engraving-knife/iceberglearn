# 提交 0081：Core: Add sort_order_id to SCAN_COLUMNS to address null sort order ID after planned data files (#8873)

## 提交信息

- **序号**：0081 / 4088
- **哈希**：0cc74f1709fc0127cfb1e2c7d6df36d32e3bb06b
- **短哈希**：0cc74f170
- **日期**：2023-10-20
- **作者**：rice
- **提交说明**：Core: Add sort_order_id to SCAN_COLUMNS to address null sort order ID after planned data files (#8873)
- **PR/Issue**：#8873

## 总体目的

这个提交修复了一个数据扫描阶段的字段丢失缺陷：当通过 `TableScan` 规划数据文件（planFiles）后，得到的 `FileScanTask` 中数据文件的 `sortOrderId()` 会返回 `null`，即使该文件在写入时明确指定了排序规则（SortOrder）。

问题的根因在于 [`BaseScan`](../../../../core/src/main/java/org/apache/iceberg/BaseScan.java) 中定义的 `SCAN_COLUMNS` 列投影集合。该集合列出了扫描规划阶段从 manifest 文件中必读的列名，被 [`ManifestReader.select()`](../../../../core/src/main/java/org/apache/iceberg/ManifestReader.java) 用来做列裁剪，以减少 IO。然而 `SCAN_COLUMNS` 此前包含了 `snapshot_id`、`file_path`、`file_ordinal`、`file_format`、`block_size_in_bytes`、`file_size_in_bytes`、`record_count`、`partition`、`key_metadata`、`split_offsets`，却遗漏了 `sort_order_id`。

由于列裁剪时未包含 `sort_order_id`，manifest 读取器在反序列化 [`GenericDataFile`](../../../../core/src/main/java/org/apache/iceberg/GenericDataFile.java) 时不会读取该字段，导致 [`BaseFile.sortOrderId`](../../../../core/src/main/java/org/apache/iceberg/BaseFile.java) 保持其默认值 `null`。这会直接影响依赖排序规则 ID 的下游逻辑，例如写入合并（compaction）、排序优化、以及引擎侧判断文件是否已按预期排序等场景，可能造成错误地认为文件"未排序"。

修复方式极为简洁：在 `SCAN_COLUMNS` 列表中追加 `"sort_order_id"`，使该列在每次数据扫描规划时都被读取，从而保证 `fileScanTask.file().sortOrderId()` 能正确反映文件实际所属的排序规则。这对 Iceberg 排序规则（SortOrder）能力在扫描链路上的端到端可用性具有重要意义——确保排序元数据不会在 manifest 读取的列投影环节丢失。

## 如何达成设计目的

整体设计思路是定位到 manifest 读取列投影的"单一事实来源"`SCAN_COLUMNS`，在其中补齐缺失的 `sort_order_id` 列。由于 `SCAN_COLUMNS` 会被 [`BaseScan.scanColumns()`](../../../../core/src/main/java/org/apache/iceberg/BaseScan.java) 根据是否需要列统计派生出 `SCAN_WITH_STATS_COLUMNS`，并被 [`DataTableScan`](../../../../core/src/main/java/org/apache/iceberg/DataTableScan.java) 等扫描实现传递给 `ManifestReader` 做列裁剪，因此只需在一处补列即可让所有数据扫描路径都读到该字段。同时新增一个回归测试，构造带排序规则的文件并验证扫描后 `sortOrderId()` 为期望值（1），锁住该行为。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseScan.java`

**修改目的**：将 `sort_order_id` 加入数据扫描的 manifest 必读列集合，使扫描规划阶段能读到文件的排序规则 ID。

**工作逻辑**：在 `SCAN_COLUMNS` 这个 `ImmutableList.of(...)` 的末尾追加 `"sort_order_id"`。该常量是数据文件扫描时 manifest 必读列（不含列统计）的权威定义，被 `scanColumns()` 方法根据 `context.returnColumnStats()` 选择 `SCAN_COLUMNS` 或 `SCAN_WITH_STATS_COLUMNS`（后者由前者与 `STATS_COLUMNS` 拼接而成）返回。修改后，无论是普通扫描还是带统计扫描，`sort_order_id` 都会被包含进投影列，`ManifestReader` 在读取 manifest 时会反序列化该字段，[`BaseFile.sortOrderId`](../../../../core/src/main/java/org/apache/iceberg/BaseFile.java) 不再为 `null`。

### `core/src/test/java/org/apache/iceberg/ScanTestBase.java`

**修改目的**：新增回归测试 `testDataFileSorted`，验证带排序规则的数据文件在扫描规划后能正确返回 `sortOrderId`。

**工作逻辑**：测试构造一个未分区表，通过 `DataFiles.builder(...).withSortOrder(SortOrder.builderFor(table.schema()).asc("a", NullOrder.NULLS_FIRST).build())` 创建一个带有自定义排序规则（orderId=1）的数据文件，并用 `newFastAppend()` 提交。随后执行 `table.newScan().planFiles()`，遍历 `FileScanTask` 并断言 `fileScanTask.file().sortOrderId()` 等于 `1`。该测试在修复前会失败（得到 `null`），修复后通过，从而锁定了"扫描规划后排序规则 ID 不丢失"这一行为。

## 小结

这个提交通过一行常量补列修复了扫描规划阶段丢失 `sort_order_id` 的缺陷，确保 Iceberg 排序规则元数据在 manifest 列投影链路上端到端可用。
