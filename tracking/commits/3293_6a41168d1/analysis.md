# 提交 3293：Core: Avoid exceptions for accessing schema for metadata tables in SnapshotUtil (#15387)

## 提交信息

- **序号**：3293 / 4088
- **哈希**：6a41168d1aede8597eff057d91681ccd0eafbec1
- **短哈希**：6a41168d1
- **日期**：2026-02-20
- **作者**：Anton Okolnychyi
- **提交说明**：Core: Avoid exceptions for accessing schema for metadata tables in SnapshotUtil (#15387)
- **PR/Issue**：#15387

## 总体目的

`SnapshotUtil.schemaFor(Table table, long snapshotId)` 是 Iceberg Core 中广泛使用的工具方法，用于"按快照 ID 取该快照对应的表 schema"。其原始实现逻辑是：先用 `table.snapshot(snapshotId)` 取快照，再取 `snapshot.schemaId()`，然后用 `table.schemas().get(schemaId)` 在表的 schema 历史中查找对应 schema；若 `schemaId` 为 null（旧快照）则回退到 `table.schema()`。

问题在于：当传入的 `table` 是元数据表（`BaseMetadataTable`，如 `snapshots`/`files`/`history` 等元数据表实例）时，这套逻辑会出错。元数据表的 `snapshot(snapshotId)` 通常委托给底层数据表，返回的快照其 `schemaId()` 是数据表的 schema ID；但 `table.schemas()` 在元数据表上返回的是元数据表自身的 schema 集合，并不包含数据表历史 schema，于是 `table.schemas().get(schemaId)` 返回 null，随即触发 `Preconditions.checkState(schema != null, "Cannot find schema with schema id %s", schemaId)` 抛出 `IllegalStateException`。

元数据表是支持时间旅行的（可以对元数据表执行 `version-as-of`/`timestamp-as-of` 查询），而 Spark 4.1 的扫描路径（`SparkTable`、`SparkScan`、`SparkScanBuilder`、`SparkPartitioningAwareScan` 等）在构造时会调用 `SnapshotUtil.schemaFor(table, branch)` 或 `schemaFor(table, snapshotId, null)` 来获取快照 schema 用于元数据列校验。当用户对元数据表做时间旅行查询时，就会命中上述异常。本提交的目的就是消除这一异常：元数据表的 schema 是固定的（由元数据表类型决定，不随快照变化），因此对元数据表调用 `schemaFor` 时应直接返回 `table.schema()`，跳过基于快照 schemaId 的查找。

## 如何达成设计目的

在 `schemaFor(Table table, long snapshotId)` 方法开头加一个前置判断：若 `table instanceof BaseMetadataTable`，直接返回 `table.schema()`；否则走原有的快照 schemaId 查找流程。由于所有其它重载（`schemaFor(table, Long snapshotId, Long timestampMillis)`、`schemaFor(table, String ref)`）最终都委托到这个 `schemaFor(table, long)`，一处修复即可覆盖全部入口。同时在方法 javadoc 中补充说明"元数据表可能支持时间旅行但不继承快照 schema"，并新增三个测试覆盖正常快照 schema 演进、非法快照 ID 抛错、元数据表时间旅行取 schema 的场景。涉及 `core/src/main/java/org/apache/iceberg/util/SnapshotUtil.java` 与 `core/src/test/java/org/apache/iceberg/util/TestSnapshotUtil.java`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/util/SnapshotUtil.java` (+8/-0 lines)

**修改目的**：让 `schemaFor` 对元数据表直接返回其固定 schema，避免按快照 schemaId 查找时抛异常。

**工作逻辑**：
新增 `import org.apache.iceberg.BaseMetadataTable;`。在 `schemaFor(Table table, long snapshotId)` 方法体最前面加入：
```java
if (table instanceof BaseMetadataTable) {
  return table.schema();
}
```
元数据表的 schema 由其类型（如 `MetadataTableType.SNAPSHOTS`）决定，与具体快照无关，因此直接返回 `table.schema()` 即可，跳过后续 `table.snapshot(snapshotId)` → `snapshot.schemaId()` → `table.schemas().get(schemaId)` 的查找链，避免在元数据表的 schema 集合中找不到数据表 schemaId 而抛 `IllegalStateException`。同时更新 javadoc，增加说明："Note that metadata tables may support time travel but don't inherit the snapshot schema, unlike normal data scans."

### `core/src/test/java/org/apache/iceberg/util/TestSnapshotUtil.java` (+69/-0 lines)

**修改目的**：覆盖 `schemaFor` 在快照演进、非法快照、元数据表三种场景下的行为。

**工作逻辑**：
新增三个测试方法：
- `schemaForSnapshotId()`：先建表（id/data 两列）并 append 得到 firstSnapshotId，断言 `schemaFor(table, firstSnapshotId)` 等于初始 schema；再 `updateSchema` 加列 zip 并 append 得到 secondSnapshotId，断言 `schemaFor(table, firstSnapshotId)` 仍为初始 schema、`schemaFor(table, secondSnapshotId)` 为含 zip 的新 schema。验证快照级 schema 隔离正确。
- `schemaForSnapshotIdInvalidSnapshot()`：用不存在的 snapshotId（999999）断言 `schemaFor` 抛 `IllegalArgumentException` 且消息含 "Cannot find snapshot with ID 999999"，验证非法快照仍按原逻辑报错（不受元数据表分支影响）。
- `schemaForSnapshotIdMetadataTable()`：核心回归测试。在 firstSnapshotId 后再加列并 append 得到 secondSnapshotId；用 `MetadataTableUtils.createMetadataTableInstance(table, MetadataTableType.SNAPSHOTS)` 创建 `snapshots` 元数据表实例，断言 `schemaFor(snapshotsTable, secondSnapshotId)` 与 `schemaFor(snapshotsTable, firstSnapshotId)` 都等于 `snapshotsTable.schema()`——即无论传哪个快照 ID，元数据表都返回自身固定 schema，不再抛异常。这正是本提交修复的行为。
- 同时新增 `assertThatThrownBy`、`MetadataTableType`、`MetadataTableUtils`、`Table` 等导入以支撑上述测试。

## 总结

本提交修复了 `SnapshotUtil.schemaFor` 在元数据表上按快照 ID 取 schema 时因 schemaId 查找失败而抛 `IllegalStateException` 的问题，通过在方法入口对 `BaseMetadataTable` 直接返回其固定 schema 解决，使元数据表的时间旅行查询在 Spark 等引擎的扫描路径中不再异常。修复覆盖所有 `schemaFor` 重载入口，并配以快照演进、非法快照、元数据表三类测试，对保障元数据表时间旅行可用性具备实际价值。
