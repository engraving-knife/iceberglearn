# 提交 3476：Core: Fix TestReplacePartitions using wrong table for validation (#15798)

## 提交信息

- **序号**：3476 / 4088
- **哈希**：31f36619da3413955a85f2712560ce0784e4484b
- **短哈希**：31f36619da
- **日期**：2026-03-27 18:35:34 -0700
- **作者**：Russell Spitzer
- **提交说明**：Core: Fix TestReplacePartitions using wrong table for validation (#15798)
- **PR/Issue**：#15798

## 总体目的

修复 `TestReplacePartitions` 测试类中存在的测试逻辑缺陷。在该测试类中有三个测试方法操作的是独立的非分区表（unpartitioned）或全 void 表（all-void table），但在调用 `commit()` 辅助方法和验证辅助方法（`validateSnapshot`、`validateManifestEntries`）时，错误地传入了分区表实例 `table` 而非实际操作的目标表。

这个 bug 的来源：
- `commit(table, ...)` 的错误是在 #6650 中引入的，当时把测试从直接调用 `toBranch().commit()` 重构为使用 `commit()` 辅助方法。
- 全 void 非分区表的验证调用（在 #14186 中加入）使用了默认重载方法，这些默认重载会通过错误的分区表 specs 来读取 manifests。

## 如何达成设计目的

1. 在 `TestBase` 中为 `validateSnapshot` 和 `validateManifestEntries` 增加接受 `Table` 参数的重载版本，使验证逻辑能够使用正确的表的 specs 来读取 manifest。
2. 将原有重载方法委托给新增加的 Table-accepting 重载方法，原方法默认传入 `table`。
3. 在 `TestReplacePartitions` 中修正三个受影响测试方法，传入正确的表实例。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestBase.java` (+32/-2 lines)

**修改目的**：为 `validateSnapshot` 和 `validateManifestEntries` 增加 Table-accepting 重载，使验证逻辑可以指定使用哪个表的 specs。

**工作逻辑**：
- 新增 `validateSnapshot(Table validationTable, Snapshot old, Snapshot snap, DataFile... newFiles)` 重载。
- 将原有 `validateSnapshot(Snapshot old, Snapshot snap, Long sequenceNumber, DataFile... newFiles)` 改为委托给新方法，默认传入 `table`。
- 新的核心实现 `validateSnapshot(Table validationTable, ...)` 中，读取 manifest 时改用 `validationTable.specs()` 而非 `table.specs()`，schemaId 校验也改用 `validationTable.schema().schemaId()`。
- 同样为 `validateManifestEntries` 增加 `Table validationTable` 参数的重载版本，读取 manifest 时改用 `validationTable.specs()`。
- 由于新参数 `validationTable` 与类成员 `table` 同类名隐藏（hidden field），添加了 `@SuppressWarnings("checkstyle:HiddenField")` 注解。

### `core/src/test/java/org/apache/iceberg/TestReplacePartitions.java` (+24/-8 lines)

**修改目的**：修正三个测试方法中错误地传入了 `table` 而非目标表的问题。

**工作逻辑**：
- `testReplaceWithUnpartitionedTable`：将 `commit(table, ...)` 改为 `commit(unpartitioned, ...)`，并将 `validateSnapshot` 和 `validateManifestEntries` 调用改为传入 `unpartitioned`。
- `testReplaceAndMergeWithUnpartitionedTable`：同样将 commit 和验证调用改为传入 `unpartitioned`。
- 对于 all-void 表的测试，将 `validateSnapshot` 和 `validateManifestEntries` 调用改为传入 `tableVoid`。

## 总结

这是一个测试缺陷修复提交。原测试虽然通过了，但实际验证的是错误的表实例，导致验证逻辑（如读取 manifest 的 specs、schemaId 校验）使用了错误的分区规范。修复方法是为验证辅助方法增加指定 Table 的重载，并在测试中传入正确的表实例。
