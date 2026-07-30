# 提交 1945：Core: Add MetricsReporter for SnapshotManager (#12665)

## 提交信息

- **序号**：1945 / 4088
- **哈希**：e1f2cfdf2c3a0be73be83d1a807f5e4ddc59fc93
- **短哈希**：e1f2cfdf2
- **日期**：2025-04-01 16:43:16 +0200
- **作者**：wangyinsheng
- **提交说明**：Core: Add MetricsReporter for SnapshotManager (#12665)
- **PR/Issue**：#12665

## 总体目的

此提交修复 Iceberg 快照管理（SnapshotManager）操作不上报 metrics 的缺陷。Iceberg 的 `Table` 接口允许配置一个 `MetricsReporter`，用于在提交操作（如 append、rewrite、overwrite 等）完成后上报 commit metrics。`BaseTransaction` 已经支持通过构造器接收 `MetricsReporter`，并在其创建的所有操作对象（`MergeAppend`、`FastAppend`、`BaseRewriteFiles`、`CherryPickOperation` 等）上调用 `reportWith(reporter)`，使这些操作的提交 metrics 能被上报。

然而 `SnapshotManager`（通过 `table.manageSnapshots()` 创建，用于管理快照分支、标签、cherrypick 等操作）在创建 `BaseTransaction` 时并未传递表的 `MetricsReporter`，而是用了不带 reporter 的构造路径（reporter 为 null）。这导致通过 `SnapshotManager` 执行的操作（如 `cherrypick`、`createBranch` 等）不会触发 metrics 上报，即使用户在表上配置了 reporter 也无法收到这些操作的提交指标。

本提交将表持有的 `MetricsReporter` 从 `BaseTable.manageSnapshots()` 透传到 `SnapshotManager`，再透传到 `BaseTransaction`，使快照管理操作也能正确上报 metrics，与 `table.newAppend()` 等直接操作的行为一致。

## 如何达成设计目的

设计思路是沿着 `BaseTable → SnapshotManager → BaseTransaction` 的构造链透传 reporter：
1. `SnapshotManager` 构造器新增 `MetricsReporter` 参数，传给 `BaseTransaction` 的对应构造器（该构造器已存在并已在所有操作上调用 `reportWith`）。
2. `BaseTable.manageSnapshots()` 创建 `SnapshotManager` 时传入 `reporter`（表持有的 reporter 字段，已在表初始化时配置）。

这样 `SnapshotManager` 内部事务创建的 `CherryPickOperation` 等操作就会携带 reporter，提交时上报 metrics。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SnapshotManager.java` (修改, +6/-2 lines)

**修改目的**：接收并透传 MetricsReporter。

**工作逻辑**：
- 新增 import `org.apache.iceberg.metrics.MetricsReporter`。
- 构造器签名从 `SnapshotManager(String tableName, TableOperations ops)` 改为 `SnapshotManager(String tableName, TableOperations ops, MetricsReporter reporter)`。
- 创建 `BaseTransaction` 时调用带 reporter 的构造器：`new BaseTransaction(tableName, ops, BaseTransaction.TransactionType.SIMPLE, ops.refresh(), reporter)`（原调用缺少 `reporter` 参数）。

### `core/src/main/java/org/apache/iceberg/BaseTable.java` (修改, +1/-1 lines)

**修改目的**：将表持有的 reporter 传入 SnapshotManager。

**工作逻辑**：`manageSnapshots()` 方法中 `new SnapshotManager(name, ops)` 改为 `new SnapshotManager(name, ops, reporter)`，其中 `reporter` 是 `BaseTable` 持有的 `MetricsReporter` 字段。

### `core/src/test/java/org/apache/iceberg/TestSnapshotManager.java` (修改, +31 lines)

**修改目的**：验证 SnapshotManager 操作触发 metrics 上报。

**工作逻辑**：新增 `testMetricsReportingInSnapshotManager` 测试：
- 用 `TestTables.create` 创建表时传入一个自定义 reporter（lambda `report -> reportCounter.getAndIncrement()`），用 `AtomicInteger` 计数上报次数。
- `manageSnapshots().createBranch("branch").commit()` 后断言 `reportCounter == 1`（createBranch 触发一次上报）。
- `table.newAppend().toBranch("branch").commit()` 后断言 `reportCounter == 2`（append 触发一次上报）。
- `table.refresh()` 后获取 branch 快照，`manageSnapshots().cherrypick(snapshotId).commit()` 后断言 `reportCounter == 3`（cherrypick 触发一次上报）。

这验证了修复后 SnapshotManager 的 createBranch 与 cherrypick 操作都会通过 reporter 上报 metrics。

## 总结

本次提交修复 SnapshotManager 操作不上报 metrics 的缺陷：将表持有的 `MetricsReporter` 从 `BaseTable.manageSnapshots()` 经 `SnapshotManager` 透传到 `BaseTransaction`，使快照管理操作（如 createBranch、cherrypick）能正确上报提交 metrics，与 `table.newAppend()` 等直接操作行为一致。改动仅涉及构造器参数透传，`BaseTransaction` 中 `reportWith` 的调用链已存在。新增测试验证三类操作（createBranch、append、cherrypick）均触发 reporter。
