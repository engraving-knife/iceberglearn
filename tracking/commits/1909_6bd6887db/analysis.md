# 提交 1909：Core: Add update event for rewrite manifests (#12627)

## 提交信息

- **序号**：1909 / 4088
- **哈希**：6bd6887db1f90674ca5e20e88cc95c5f92dcb050
- **短哈希**：6bd6887db
- **日期**：2025-03-24 10:52:21 +0100
- **作者**：Bryan Keller
- **提交说明**：Core: Add update event for rewrite manifests (#12627)
- **PR/Issue**：#12627

## 总体目的

Iceberg 表在提交（commit）后会通过 `SnapshotProducer` 触发一个 "update event"（更新事件），供监听器（listener）/上报器（reporter）感知这次快照变更。常见的提交操作（append、overwrite 等）都由 `SnapshotProducer` 提供 `updateEvent()` 默认实现，返回一个 `CreateSnapshotEvent`。

但 `BaseRewriteManifests`（重写清单操作）此前没有覆盖 `updateEvent()`。它继承自 `SnapshotProducer<RewriteManifests>`，会走父类默认实现；但父类的默认实现需要表名等参数才能构造事件，而 `BaseRewriteManifests` 之前构造时并未传入 `tableName`，导致重写清单操作的 update event 缺少正确的表名等元信息，监听器收不到与其它操作一致的事件。

本提交为 `BaseRewriteManifests` 显式覆盖 `updateEvent()`，返回一个包含表名、操作类型、快照 ID、序列号与快照摘要的 `CreateSnapshotEvent`，并相应在构造器中引入 `tableName` 参数，从 `BaseTable` 与 `BaseTransaction` 两处入口注入表名。同时修正 `TestCommitReporting` 中针对重写清单的断言，使其与新行为一致（操作类型为 `replace`，快照/序列号为 3）。

## 如何达成设计目的

1. 给 `BaseRewriteManifests` 增加 `tableName` 字段，构造器签名改为 `(String tableName, TableOperations ops)`。
2. 覆盖 `updateEvent()`：取出当前刚提交的 snapshot，构造 `CreateSnapshotEvent(tableName, operation(), snapshotId, sequenceNumber, snapshot.summary())`。
3. 在 `BaseTable.rewriteManifests()` 与 `BaseTransaction.rewriteManifests()` 两处把表名（`name` / `tableName`）传入新构造器。
4. 修正测试：原来断言 `operation="append"`、`snapshotId=2`、`sequenceNumber=2` 以及若干 `addedDataFiles/addedRecords/addedFilesSizeInBytes` 指标，现在改为 `operation="replace"`、`snapshotId=3`、`sequenceNumber=3`，并移除重写清单场景下不成立的 added 指标断言。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseRewriteManifests.java` (修改, +13/-1 lines)

**修改目的**：为重写清单操作提供正确的 update event，并引入 tableName 字段。

**工作逻辑**：

新增 import `CreateSnapshotEvent`，新增字段 `private final String tableName;`，构造器首参数为 `tableName` 并赋值。

新增方法：

```java
@Override
public Object updateEvent() {
  long snapshotId = snapshotId();
  Snapshot snapshot = ops().current().snapshot(snapshotId);
  long sequenceNumber = snapshot.sequenceNumber();
  return new CreateSnapshotEvent(
      tableName, operation(), snapshotId, sequenceNumber, snapshot.summary());
}
```

通过 `snapshotId()` 拿到本次刚提交的快照 ID，再从当前表的快照里取出 `sequenceNumber` 与 `summary`，组装成 `CreateSnapshotEvent`，使监听器能拿到与 append/overwrite 一致结构的事件。

### `core/src/main/java/org/apache/iceberg/BaseTable.java` (修改, +1/-1 lines)

**修改目的**：把表名传给 `BaseRewriteManifests` 构造器。

**工作逻辑**：`rewriteManifests()` 由 `new BaseRewriteManifests(ops)` 改为 `new BaseRewriteManifests(name, ops)`，其中 `name` 为 `BaseTable` 的表名字段。

### `core/src/main/java/org/apache/iceberg/BaseTransaction.java` (修改, +2/-1 lines)

**修改目的**：在事务上下文中同样注入表名。

**工作逻辑**：`rewriteManifests()` 内 `new BaseRewriteManifests(transactionOps)` 改为 `new BaseRewriteManifests(tableName, transactionOps)`，`tableName` 来自事务对象。

### `core/src/test/java/org/apache/iceberg/TestCommitReporting.java` (修改, +3/-8 lines)

**修改目的**：更新重写清单场景下的 commit report 断言以匹配新事件。

**工作逻辑**：原断言 `operation="append"`、`snapshotId=2L`、`sequenceNumber=2L` 改为 `operation="replace"`、`snapshotId=3L`、`sequenceNumber=3L`；删除 `addedDataFiles/addedRecords/addedFilesSizeInBytes` 三项指标断言。原因：重写清单是 `replace` 操作（用新清单替换旧清单），不产生 added 数据文件指标，且重写后的快照是第 3 个快照。

## 总结

本提交补齐了 `BaseRewriteManifests` 缺失的 `updateEvent()` 实现，使重写清单操作也能向监听器/上报器发出结构正确的 `CreateSnapshotEvent`。通过在构造器注入表名，保证事件携带正确的表名；同时修正既有测试以反映正确的操作类型（`replace`）与快照序列号。
