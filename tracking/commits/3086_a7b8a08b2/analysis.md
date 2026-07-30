# 提交 3086：Spark 4.1: Fix spark 4.1 test for unlink table metadata's last-updated timestamp (#15004)

## 提交信息

- **序号**：3086 / 4088
- **哈希**：a7b8a08b2abb6125ffbe6ea61817e5826db61ab2
- **短哈希**：a7b8a08b2
- **日期**：2026-01-08
- **作者**：Hongyue/Steve Zhang
- **提交说明**：Spark 4.1: Fix spark 4.1 test for unlink table metadata's last-updated timestamp (#15004)
- **PR/Issue**：#15004

## 总体目的

该提交修复 Spark 4.1 模块中元数据表（metadata tables）测试关于"最后更新时间戳"（last-updated timestamp）的断言错误。问题的根源在于：测试原先使用 `currentSnapshot.timestampMillis()`（当前快照时间戳）来验证元数据表中记录的"最后更新时间"列，但实际上元数据表的 last-updated 时间应当取自 `tableMetadata.lastUpdatedMillis()`，即表元数据文件本身最后一次被更新的时间。

在 Iceberg 中，表的元数据（metadata）与快照（snapshot）是两个不同层次的概念。快照时间戳记录的是某次数据写入（commit）发生的时间，而元数据的 `lastUpdatedMillis` 记录的是元数据文件（metadata.json）最后一次被修改的时间。大多数情况下二者接近，但在某些操作（如 rollback 回滚、unlink table 等只修改元数据而不产生新快照的操作）中，二者会产生差异。当执行 rollback 等操作后，元数据文件被更新（`lastUpdatedMillis` 变化），但当前快照可能指向一个更早的快照，导致 `currentSnapshot.timestampMillis()` 与 `lastUpdatedMillis()` 不一致。

测试此前错误地用快照时间戳去断言元数据表中的"最后更新时间"列，在涉及 unlink table（解链表）或 rollback 场景下会失败。本提交将断言改为使用 `tableMetadata.lastUpdatedMillis()`，使测试语义与元数据表的实际定义保持一致。同时在 `TestIcebergSourceTablesBase` 中新增了一条断言，验证 rollback 后历史记录的时间戳确实等于元数据的 `lastUpdatedMillis` 且大于第一个快照的时间戳，进一步强化了对这一语义的正确性验证。

## 如何达成设计目的

通过修改两个测试文件中的断言逻辑达成修复：在 `TestMetadataTables` 中将两处 `currentSnapshot.timestampMillis()` 替换为 `tableMetadata.lastUpdatedMillis()`；在 `TestIcebergSourceTablesBase` 的 rollback 测试中新增对 `lastUpdatedMillis` 的显式断言，并引入 `HasTableOperations` 接口来获取底层 operations 中的当前元数据。

## 修改详情

### `spark/v4.1/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMetadataTables.java` (+4/-3 lines)

**修改目的**：修正元数据表中 last-updated 时间戳列的预期值来源。

**工作逻辑**：
文件中有两处修改，均将预期时间戳从 `currentSnapshot.timestampMillis()` 改为 `tableMetadata.lastUpdatedMillis()`。第一处位于验证"快照元数据表结果与最新快照条目匹配"的断言中，原代码使用 `currentSnapshot.timestampMillis() * 1000` 转换为 Java Timestamp，修改后使用 `tableMetadata.lastUpdatedMillis() * 1000`。第二处位于验证"结果应匹配最新快照条目"的断言中，同样将 `tableMetadata.currentSnapshot().timestampMillis()` 替换为 `tableMetadata.lastUpdatedMillis()`。这两处修改确保测试断言的"最后更新时间"与元数据表 `MetadataTableType.LAST_UPDATED` 实际返回的值语义一致。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/source/TestIcebergSourceTablesBase.java` (+5/-0 lines)

**修改目的**：新增 rollback 后元数据最后更新时间戳的断言验证。

**工作逻辑**：
在 rollback 测试方法中，于获取 `rollbackTimestamp` 之后新增了三行断言。首先引入了 `org.apache.iceberg.HasTableOperations` 接口的 import，然后通过 `((HasTableOperations) table).operations().current().lastUpdatedMillis()` 获取当前元数据的最后更新时间。断言验证 `rollbackTimestamp` 等于该 `lastUpdatedMillis` 值，且大于 `firstSnapshotTimestamp`。这验证了 rollback 操作会更新元数据文件（因此 `lastUpdatedMillis` 反映 rollback 时间），同时历史记录中的时间戳与元数据更新时间一致，且发生在第一个快照之后，符合时间顺序逻辑。

## 总结

该提交修复了 Spark 4.1 元数据表测试中关于 last-updated 时间戳的语义错误，将断言从快照时间戳改为元数据最后更新时间戳，确保在 unlink/rollback 等不产生新快照但更新元数据的场景下测试仍然正确。这是一个测试正确性修复，不影响生产代码逻辑，但对 Spark 4.1 适配的测试稳定性有重要意义。
