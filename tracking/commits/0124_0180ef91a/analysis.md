# 提交 0124：Core: Scan only live entries in partitions table (#8969)

## 提交信息

- **序号**：0124 / 4088
- **哈希**：0180ef91a733c3b6fc3c3ec8ad1d0231a5a6265e
- **短哈希**：0180ef91a
- **日期**：2023-11-01
- **作者**：Hongyue/Steve Zhang
- **提交说明**：Core: Scan only live entries in partitions table (#8969)
- **PR/Issue**：#8969

## 总体目的

Iceberg 的 `PartitionsTable` 是一个元数据表，用于向用户/引擎暴露每个分区当前的聚合统计（数据记录数、数据文件数、数据文件总字节数、position/equality 删除记录数与文件数、最近更新时间与 snapshot id 等）。其底层实现会遍历当前 snapshot 关联的所有 manifest，读取每条 `ManifestEntry`，按分区聚合。

问题在于：`PartitionsTable` 在读取 manifest 条目时使用的是 `ManifestReader.entries()`，该方法返回 manifest 中的**所有**条目，包括状态为 `DELETED` 的条目（即在该 snapshot 中被删除的文件）。`ManifestEntry.Status` 有三种：`EXISTING`（既有）、`ADDED`（新增）、`DELETED`（删除）；`entries()` 三种都返回，而 `liveEntries()` 仅返回 `status != DELETED` 的条目（即 ADDED 与 EXISTING）。

这导致一个明显的正确性缺陷：当一次删除操作把某个分区的全部文件都删掉（典型的"分区对齐删除"，例如 `DELETE FROM t WHERE dep = 'hr'`）后，对应 manifest 中会出现这些文件的 `DELETED` 条目。`PartitionsTable` 仍会把它们计入聚合，于是 `.partitions` 元数据表里依然显示这个已经没有存活文件的分区，且统计数字把已删除文件也算进去。用户看到的是"分区还在、文件还在"，与实际数据状态不符，会误导维护决策与可观测性。

本提交把 `PartitionsTable.readEntries` 中的 `.entries()` 改为 `.liveEntries()`，使分区聚合只计入当前 snapshot 下仍存活的文件条目，从而让 `.partitions` 表正确反映分区真实存活状态——分区对齐删除后该分区不再出现，行级删除则不影响分区数量。同时在 Spark 3.2/3.3/3.4/3.5 四个版本的 `TestDelete` 中新增 `testDeleteWithPartitionedTable` 测试覆盖这两种删除场景下 `.partitions` 表的预期行为。

## 如何达成设计目的

改动非常聚焦：

1. **Core 改动（1 行）**：在 `PartitionsTable.readEntries` 中，把 `ManifestFiles.open(...).select(...).entries()` 改为 `.liveEntries()`。这样 `planEntries` → `readEntries` 链路返回的迭代器只包含非 DELETED 条目，后续 `partitions(...)` 聚合方法基于这些存活条目累计 `Partition.update(file, snapshot)`，自然就不会再为只有已删除文件的分区产生输出。
2. **测试改动**：在四个 Spark 版本的 `TestDelete` 中新增同名测试 `testDeleteWithPartitionedTable`，构造 `hr` 与 `hardware` 两个分区，先做行级删除 `DELETE FROM %s WHERE id = 1`（断言 `.partitions` 仍有 2 个分区），再做分区对齐删除 `DELETE FROM %s WHERE dep = 'hr'`（断言 `.partitions` 只剩 1 个分区），用回归测试锁定期望行为。

整体设计思路是"在正确的层级过滤"：分区聚合的输入应当只包含存活文件，因此在读取 manifest 条目时就用 `liveEntries()` 过滤，而不是在聚合逻辑里再判断 status，既保持了聚合逻辑的简洁，也避免了把已删除文件短暂计入后再剔除的复杂处理。

## 修改详情

### `core/src/main/java/org/apache/iceberg/PartitionsTable.java`

**修改目的**：让 `PartitionsTable` 在扫描 manifest 时只读取存活（非 DELETED）条目，确保 `.partitions` 元数据表正确反映当前 snapshot 的分区存活状态与统计。

**工作逻辑**：

改动位于 `readEntries(ManifestFile manifest, StaticTableScan scan)` 方法。该方法负责打开单个 manifest、选择扫描列（不带统计列以节省内存）、返回条目迭代器供上层 `planEntries` 通过 `ParallelIterable` 并行聚合。改动前：

```java
return CloseableIterable.transform(
    ManifestFiles.open(manifest, table.io(), table.specs())
        .caseSensitive(scan.isCaseSensitive())
        .select(scanColumns(manifest.content())) // don't select stats columns
        .entries(),
    t -> (ManifestEntry<? extends ContentFile<?>>) t.copyWithoutStats());
```

改动后把 `.entries()` 替换为 `.liveEntries()`：

```java
return CloseableIterable.transform(
    ManifestFiles.open(manifest, table.io(), table.specs())
        .caseSensitive(scan.isCaseSensitive())
        .select(scanColumns(manifest.content())) // don't select stats columns
        .liveEntries(),
    t -> (ManifestEntry<? extends ContentFile<?>>) t.copyWithoutStats());
```

依据 `ManifestReader` 的实现，`liveEntries()` 等价于 `entries(true)`，内部通过 `filterLiveEntries` 调用 `isLiveEntry` 过滤掉 `status() == ManifestEntry.Status.DELETED` 的条目，只保留 `ADDED` 与 `EXISTING`。因此 `partitions(Table, StaticTableScan)` 方法在遍历 `planEntries(scan)` 时遇到的全部是存活条目，`PartitionMap.get(partition).update(file, snapshot)` 只会为仍有存活文件的分区累加统计；某分区文件全部被删除后，该分区不会再有任何存活条目，自然不会出现在 `partitions.all()` 的结果中。后续 `StaticDataTask.of(...)` 据此构建的 `.partitions` 表行集合也就只包含真实存活的分区。

防御性拷贝 `t.copyWithoutStats()` 不变，仍然去掉统计列以降低内存占用；`scanColumns(manifest.content())` 依据 manifest 是数据还是删除类型选择不同列集，行为也不变。

### `spark/v3.2/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestDelete.java`

### `spark/v3.3/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestDelete.java`

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestDelete.java`

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestDelete.java`

**修改目的**：为四个 Spark 版本新增回归测试 `testDeleteWithPartitionedTable`，验证修复后 `.partitions` 表在行级删除与分区对齐删除下的正确分区数量。

**工作逻辑**：四个版本的测试逻辑一致（仅 v3.4/v3.5 用 `append(tableName, new Employee(...))` 写入，v3.2/v3.3 用 `sql("INSERT INTO TABLE ...")`，对齐各版本基类的辅助方法差异）。测试步骤：

1. `createAndInitPartitionedTable()` 建立按 `dep` 分区的表。
2. 写入两条数据各落在 `hr`、`hardware` 分区：`(1,'hr'),(3,'hr')` 与 `(1,'hardware'),(2,'hardware')`。
3. **行级删除** `DELETE FROM %s WHERE id = 1`：从两个分区各删一条，但两个分区仍有存活文件。断言 `SELECT * FROM %s ORDER BY id` 返回 `(2,'hardware'),(3,'hr')`；并查询 `tableName + ".partitions"` 断言大小为 2（"row level delete does not reduce number of partition"）。
4. **分区对齐删除** `DELETE FROM %s WHERE dep = 'hr'`：把整个 `hr` 分区的文件全部删除。断言 `SELECT * FROM %s ORDER BY id` 只剩 `(2,'hardware')`；并断言 `.partitions` 大小为 1（"partition aligned delete results in 1 partition"）。

第 4 步是关键回归点：在修复前（使用 `entries()`），`hr` 分区的 `DELETED` 条目仍会被聚合，`.partitions` 会错误地返回 2；修复后（使用 `liveEntries()`），`hr` 分区无存活条目，`.partitions` 正确返回 1。测试用 `Assert.assertEquals` 配合描述性消息锁住该预期。

## 小结

本提交修复 `PartitionsTable` 误将已删除 manifest 条目计入分区聚合的缺陷，通过把 manifest 读取从 `.entries()` 改为 `.liveEntries()`，使 `.partitions` 元数据表在分区对齐删除后正确地不再显示已无存活文件的分区，并通过四个 Spark 版本的回归测试锁定行为，提升了 Iceberg 元数据表的可观测准确性。
