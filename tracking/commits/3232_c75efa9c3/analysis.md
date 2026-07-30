# 提交 3232：Core: Fix metadata table scans with useRef by preserving metadata schema (#15276)

## 提交信息

- **序号**：3232 / 4088
- **哈希**：c75efa9c32d85bc1c804a3909a28048fc70c6833
- **短哈希**：c75efa9c3
- **日期**：2026-02-10
- **作者**：Rui Li
- **提交说明**：Core: Fix metadata table scans with useRef by preserving metadata schema (#15276)
- **PR/Issue**：#15276

## 总体目的

本提交修复了元数据表（metadata table）在使用 `useRef` 指定分支或标签引用时扫描结果错误的问题。Iceberg 的元数据表（如 `ManifestEntriesTable`、`DataFilesTable`、`ManifestsTable`、`FilesTable`、`DeleteFilesTable` 等）是用于暴露表内部元数据的虚拟表，它们有自己独立的 schema 定义（即 `tableSchema()`），不同于底层数据表的 schema。

`SnapshotScan.useRef(String name)` 方法用于将扫描切换到指定分支或标签对应的快照。原实现中，`useRef` 始终调用 `SnapshotUtil.schemaFor(table(), name)` 获取引用对应快照的 schema 并传递给 `newRefinedScan`。然而对于元数据表而言，`schemaFor` 返回的是数据表在该快照时的 schema，而非元数据表自身的 schema。这导致元数据表扫描使用错误的 schema 来读取数据，可能造成字段映射错误、过滤条件失效或查询结果不正确。

具体来说，当元数据表扫描使用 `useRef("branch")` 时，应当仍然使用元数据表自身的 schema（通过 `tableSchema()` 获取），而非数据表的快照 schema。`useSnapshotSchema()` 方法用于判断是否应使用快照 schema——对于普通数据表扫描返回 true，对于元数据表扫描返回 false。修复后的逻辑是：仅当 `useSnapshotSchema()` 为 true 时才使用 `SnapshotUtil.schemaFor()`，否则保留 `tableSchema()`。

## 如何达成设计目的

通过在 `SnapshotScan.useRef()` 方法中增加条件判断：当 `useSnapshotSchema()` 返回 true 时使用 `SnapshotUtil.schemaFor(table(), name)`（保留原有行为），否则使用 `tableSchema()`（元数据表的自身 schema）。修改仅涉及一行核心逻辑的变更。同时在 `TestMetadataTableScans` 中新增测试验证多种元数据表在分支引用下的扫描正确性。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SnapshotScan.java` (+3/-1 lines)

**修改目的**：根据扫描类型选择正确的 schema。

**工作逻辑**：
原代码 `Schema newSchema = SnapshotUtil.schemaFor(table(), name)` 始终使用引用快照的 schema。修改为 `Schema newSchema = useSnapshotSchema() ? SnapshotUtil.schemaFor(table(), name) : tableSchema()`。`useSnapshotSchema()` 是 `SnapshotScan` 的方法，对于元数据表扫描（如 `ManifestEntriesTable` 的扫描）返回 false，对于普通数据表扫描返回 true。此修改确保元数据表在 `useRef` 时不会错误地切换到数据表的快照 schema，而是保留元数据表自身的 schema 定义。

### `core/src/test/java/org/apache/iceberg/TestMetadataTableScans.java` (+76/-0 lines)

**修改目的**：验证元数据表在分支引用下的扫描正确性。

**工作逻辑**：
新增 `testMetadataTableScansOnBranch` 测试：创建快照追加 FILE_A，创建 `testBranch` 分支，再追加 FILE_B。分支上应仅有 FILE_A 对应的条目（1 个），main 上应有 2 个条目。测试覆盖 `ManifestEntriesTable`（含过滤条件验证）、`DataFilesTable`、`ManifestsTable`、`FilesTable` 四种元数据表，分别验证分支和 main 上的行数。新增 `testDeleteFilesTableScanOnBranch` 测试：验证 `DeleteFilesTable` 在分支上的删除文件数量正确（分支上 1 个，main 上 2 个），仅限 V2 表。新增辅助方法 `rowCount(TableScan)` 通过遍历扫描任务和数据行计算行数。

## 总结

本提交通过一行核心逻辑修复，使元数据表在 `useRef` 时正确保留自身 schema 而非错误切换到数据表快照 schema。修复覆盖了所有元数据表类型，并通过全面的测试验证了分支引用下各元数据表扫描结果的正确性。这是一个影响面精准但修复关键的 bug fix。
