# 提交 0973：Core: Implement estimateRowCount for Files and Entries Metadata Tables (#10759)

## 提交信息

- **序号**：0973 / 4088
- **哈希**：24fb7341afccdd89eba73cab62c73249155cd6b4
- **短哈希**：24fb7341a
- **日期**：2024-07-24 14:00:26 -0600
- **作者**：Szehon Ho
- **提交说明**：Core: Implement estimateRowCount for Files and Entries Metadata Tables (#10759)
- **PR/Issue**：#10759

## 总体目的

Iceberg 的 `ScanTask` 接口定义了 `estimatedRowsCount()` 方法，用于返回某个扫描任务预期产出的行数估算值。该接口的默认实现固定返回 `100_000`（10 万行），这一数值对于普通数据表扫描任务而言只是个粗略占位。然而对于 Iceberg 的元数据表（metadata tables）——如 Files 表（`DataFilesTable`、`AllDataFilesTable`、`DeleteFilesTable`、`AllDeleteFilesTable`、`AllFilesTable`）和 Entries 表（`ManifestEntriesTable`、`AllEntriesTable`）——每个扫描任务实际上对应一个 manifest（清单文件），而 manifest 自身就记录了它所包含的文件数量统计（added/existing/deleted files count）。

使用固定的 10 万作为元数据表扫描任务的行数估算会严重偏离实际值：一个 manifest 可能只含几个文件，却会被估算为 10 万行。这会影响下游基于该估算的决策，例如查询引擎在分配资源、做自适应查询执行（AQE）或拆分任务时，会因估算失真而做出次优决策。

本提交为 Files 和 Entries 两类元数据表的扫描任务实现了 `estimatedRowsCount()`，使其返回基于 manifest 元数据的真实文件计数，从而提供准确的行数估算。

## 如何达成设计目的

Iceberg 元数据表的扫描任务以 manifest 为粒度划分：每个 `FileScanTask` 对应一个 manifest，扫描该 manifest 中的所有条目（每个条目对应一个数据/删除文件）。因此，一个扫描任务"预期产出的行数"就等于该 manifest 中文件条目的总数。

Manifest 文件在 Iceberg 的元数据中记录了三个计数：`addedFilesCount`（新增文件数）、`deletedFilesCount`（删除文件数）、`existingFilesCount`（已存在文件数）。三者之和即为该 manifest 包含的文件条目总数。

设计思路是在 `BaseFilesTable` 和 `BaseEntriesTable` 的内部扫描任务类中重写 `estimatedRowsCount()`，返回这三个计数之和。由于这些计数可能较大，实现中将每个计数显式转换为 `long` 再相加，避免 int 溢出风险。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseEntriesTable.java`

**修改目的**：为 Entries 元数据表（`ManifestEntriesTable`、`AllEntriesTable`）的扫描任务提供准确的行数估算。

**工作逻辑**：在 `BaseEntriesTable` 的内部扫描任务类中新增 `estimatedRowsCount()` 方法重写。该方法返回当前 manifest 的 `addedFilesCount` + `deletedFilesCount` + `existingFilesCount`，三者均先转为 `long` 再求和。该内部类持有 `manifest` 字段（`ManifestFile` 类型），可直接访问其计数 API。改动位于既有的 `manifest()` 访问方法之前。

```java
@Override
public long estimatedRowsCount() {
  return (long) manifest.addedFilesCount()
      + (long) manifest.deletedFilesCount()
      + (long) manifest.existingFilesCount();
}
```

### `core/src/main/java/org/apache/iceberg/BaseFilesTable.java`

**修改目的**：为 Files 元数据表（`DataFilesTable`、`AllDataFilesTable`、`DeleteFilesTable`、`AllDeleteFilesTable`、`AllFilesTable`）的扫描任务提供准确的行数估算。

**工作逻辑**：与 `BaseEntriesTable` 完全对称，在 `BaseFilesTable` 的内部扫描任务类中新增同样的 `estimatedRowsCount()` 重写，返回 manifest 三类文件计数之和。改动位于 `files(Schema)` 私有方法之前。

```java
@Override
public long estimatedRowsCount() {
  return (long) manifest.addedFilesCount()
      + (long) manifest.deletedFilesCount()
      + (long) manifest.existingFilesCount();
}
```

### `core/src/test/java/org/apache/iceberg/TestMetadataTableScans.java`

**修改目的**：为新增的 `estimatedRowsCount()` 实现添加单元测试覆盖。

**工作逻辑**：新增两个 `@TestTemplate` 测试方法和一个私有断言辅助方法：

- `testFilesTableEstimateSize()`：调用 `preparePartitionedTable(true)` 准备一个含 4 个文件的分区表，然后对 `DataFilesTable`、`AllDataFilesTable`、`AllFilesTable` 断言每个扫描任务的 `estimatedRowsCount()` 等于 4；当 formatVersion 为 2 时，还对 `DeleteFilesTable`、`AllDeleteFilesTable` 做同样断言。
- `testEntriesTableEstimateSize()`：对 `ManifestEntriesTable`、`AllEntriesTable` 断言估算行数为 4。
- `assertEstimatedRowCount(Table, int)`：辅助方法，对给定元数据表执行 `newScan().planFiles()`，遍历所有 `FileScanTask` 并断言每个任务的 `estimatedRowsCount()` 等于预期值；同时断言任务列表非空。

测试用例验证了 manifest 中只含一个文件计数为 4 的场景下，估算值准确返回 4，而非默认的 10 万。

## 小结

- **成效**：Files 和 Entries 元数据表的扫描任务现在能返回基于 manifest 文件计数的准确行数估算，取代原先固定的 10 万默认值，提升下游查询引擎在资源分配和自适应执行上的决策质量。
- **影响范围**：`core` 模块的 `BaseEntriesTable`、`BaseFilesTable` 两个元数据表基类及其对应测试。改动是新增方法重写，不改变既有扫描行为，仅影响调用 `estimatedRowsCount()` 的下游。
- **回迁到 1.4.x 的注意事项**：该改动是纯增强性的方法重写，不引入新的 API 或破坏性变更，适合回迁到 1.4.x。前提是 1.4.x 分支的 `BaseEntriesTable`/`BaseFilesTable` 内部结构（持有 `manifest` 字段的扫描任务类）与 main 分支一致；若 1.4.x 已有 `estimatedRowsCount` 接口默认方法则可直接重写。回迁风险低，无兼容性问题。
