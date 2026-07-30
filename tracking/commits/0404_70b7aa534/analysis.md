# 提交 0404：API, Core, Spark: Change behavior of fastForward/replace to create the from branch if it does not exist (#9196)

## 提交信息

- **序号**：0404
- **哈希**：70b7aa534b2c79ccd7b6c0e0fd1be980772bb20a
- **短哈希**：70b7aa534
- **日期**：2024-01-22（AuthorDate 与 CommitDate 均为 2024-01-22 14:56:43 -0800）
- **作者**：Amogh Jahagirdar <amogh@tabular.io>
- **提交说明**：API, Core, Spark: Change behavior of fastForward/replace to create the from branch if it does not exist (#9196)
- **PR/Issue**：#9196

## 总体目的

这是一个跨 API/Core/Spark 三层的行为变更提交，改变了 `fastForwardBranch` 和 `replaceBranch` 两个快照管理操作在"源分支（from）不存在"时的语义。变更前的行为是：如果 `from` 分支不存在，直接抛 `IllegalArgumentException("Branch to update does not exist: ...")`。变更后的行为是：如果 `from` 分支不存在，就把它创建出来（指向 `to` 的快照），相当于把"快进/替换一个不存在的分支"等同于"基于 to 创建该分支"。

这个变更背后是对分支管理易用性的考量。在 Iceberg 的 branch/tag 模型里，用户经常希望"把某个分支快进/重置到另一个分支的位置"。当目标 from 分支还没建时，旧逻辑要求用户先 `createBranch` 再 `fastForward`/`replace`，两步操作既啰嗦又在并发场景下不原子。新语义让"快进/替换"具备"upsert"语义——存在就更新，不存在就创建——这与 Git 中 `git branch -f <branch> <ref>`（branch 不存在则创建）的直觉一致，显著降低调用方的心智负担，也让 Spark 的 `system.fast_forward` procedure 能更顺畅地用于"把 main 快进到某 feature 分支"这类常见工作流。

需要强调的是这是"行为不兼容"变更（原本抛异常的调用路径现在会成功），所以提交同时更新了 API javadoc、Core 实现、Core 测试、Spark procedure 实现与 Spark 测试，确保三层语义统一。

## 如何达成设计目的

实现路径分层清晰：(1) API 层只改 `ManageSnapshots` 接口的 javadoc，把"if the from branch does not exist, it will be created with default retention properties"写进 `replaceBranch` 和 `fastForwardBranch` 的契约；(2) Core 层在 `UpdateSnapshotReferencesOperation` 的快进/替换共用逻辑里，把"`branchToUpdate != null` 否则抛异常"改成"`branchToUpdate == null` 则委托 `createBranch(from, toRef.snapshotId())`"；(3) Spark 层的 `FastForwardBranchProcedure` 因为原来在 procedure 内部就预检了 source 分支存在性并构造返回行，需要同步移除预检、并把返回行里的"快进前 snapshot id"改为可空（分支新建时无前序快照）；(4) 各层测试把"期望抛异常"的用例改写为"期望成功创建分支"的用例。

## 修改详情

### api/src/main/java/org/apache/iceberg/ManageSnapshots.java

**修改目的**：把"from 不存在则创建"的新行为写进接口契约。

**工作逻辑**：`replaceBranch(String from, String to)` 与 `fastForwardBranch(String from, String to)` 两个方法的 javadoc 各加一句："If the from branch does not exist, it will be created with default retention properties."。注意只改文档不改签名——接口方法本身没变，变的是实现承诺的后置条件。新建分支使用"默认 retention properties"（即不携带任何 snapshot retention / max-ref-age 等策略），这与显式 `createBranch` 的默认行为对齐。

### core/src/main/java/org/apache/iceberg/UpdateSnapshotReferencesOperation.java

**修改目的**：实现新的 upsert 语义，这是本次行为变更的核心。

**工作逻辑**：原代码（fastForward 与 replace 共用的入口）：
```java
SnapshotRef branchToUpdate = updatedRefs.get(from);
SnapshotRef toRef = updatedRefs.get(to);
Preconditions.checkArgument(branchToUpdate != null, "Branch to update does not exist: %s", from);
Preconditions.checkArgument(toRef != null, "Ref does not exist: %s", to);
// ... 后续校验 isBranch、isAncestor 等
```
改为：
```java
SnapshotRef branchToUpdate = updatedRefs.get(from);
SnapshotRef toRef = updatedRefs.get(to);
Preconditions.checkArgument(toRef != null, "Ref does not exist: %s", to);
if (branchToUpdate == null) {
  return createBranch(from, toRef.snapshotId());
}
Preconditions.checkArgument(branchToUpdate.isBranch(), "Ref %s is a tag not a branch", from);
// ... 后续逻辑不变
```

关键点有三：(a) `to` 仍必须存在（不存在照常抛异常），这合理——快进/替换必须有一个真实的目标快照可指；(b) `from` 不存在时直接 `return createBranch(from, toRef.snapshotId())`，提前返回，跳过所有"分支已存在"才需要的校验（isBranch、祖先关系等）；(c) `createBranch` 是同操作上已有的方法，复用它保证了新建分支走的是和显式 createBranch 完全一致的代码路径（包括默认 retention 设置、ref 写入 updatedRefs 等）。

### core/src/test/java/org/apache/iceberg/TestSnapshotManager.java

**修改目的**：把 Core 层"from 不存在抛异常"的测试改写为"from 不存在则创建"的测试。

**工作逻辑**：
- 删除 `testReplaceBranchNonExistingBranchToUpdateFails`（断言抛 `IllegalArgumentException("Branch to update does not exist: non-existing")`），因为该行为已不存在。
- 把 `testFastForwardBranchNonExistingFromBranchFails` 改写为 `testFastForwardBranchNonExistingFromBranchCreatesTheBranch`：先 append FILE_A 拿到 snapshotId，创建 branch1 指向该快照，再 `fastForwardBranch("new-branch", "branch1")`，断言 `new-branch` 是 branch 且 snapshotId 等于原 snapshotId。
- 新增 `testReplaceBranchNonExistingFromBranchCreatesTheBranch`：同样套路，但走 `replaceBranch("new-branch", "branch1")`，断言分支被创建且指向正确快照。
- 保留 `testReplaceBranchNonExistingToBranchFails`（to 不存在仍应抛异常）。

### spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestFastForwardBranchProcedure.java

**修改目的**：同步更新 Spark procedure 的端到端测试以反映新语义。

**工作逻辑**：
- 把 `testFastForwardNonExistingBranchCases` 拆/改为 `testFastForwardNonExistingToRefFails`：删除其中"branch 不存在抛异常"的断言段，只保留"to ref 不存在抛异常"的断言（向表插入一条记录以使 to=main 存在后再断言 to=non_existing 抛错）。
- 重写 `testFastForwardBranches` 末段：把原本手动拆解 `List<Object[]>` 输出、逐字段断言的写法，换成 `.containsExactly(row(branch1, branch1Snapshot.snapshotId(), branch2Snapshot.snapshotId()))` 的精简写法（依赖 row 工具方法）。
- 新增 `testFastForwardNonExistingFromMainCreatesBranch`：创建表、建 branch1、向 `tableName.branch_branch1` 插两条数据得到 branch1Snapshot；然后 `CALL system.fast_forward(table, main, branch1)`，断言输出 `containsExactly(row(main, null, branch1Snapshot.snapshotId()))`——注意 main 在插入前不存在（表刚建无分支引用），所以快进前 snapshot id 为 null，验证了"from 不存在则创建"且"返回行 before 列可为 null"。再对非 main 分支 branch2 重复一次同样的 fast_forward(branch2, branch1)，断言 `containsExactly(row(branch2, null, branch1Snapshot.snapshotId()))`，确保非 main 分支也享受同一语义。

### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/FastForwardBranchProcedure.java

**修改目的**：让 Spark procedure 不再预检 source 分支存在性，并正确处理"before snapshot id 为 null"的返回行。

**工作逻辑**：原 `call` 方法：
```java
String source = args.getString(1);
String target = args.getString(2);
return modifyIcebergTable(tableIdent, table -> {
  Snapshot currentSnapshot = table.snapshot(source);
  Preconditions.checkArgument(currentSnapshot != null, "Branch to fast-forward does not exist: %s", source);
  table.manageSnapshots().fastForwardBranch(source, target).commit();
  long latest = table.snapshot(source).snapshotId();
  InternalRow outputRow = newInternalRow(UTF8String.fromString(source), currentSnapshot.snapshotId(), latest);
  return new InternalRow[] {outputRow};
});
```
改为：
```java
String from = args.getString(1);
String to = args.getString(2);
return modifyIcebergTable(tableIdent, table -> {
  Long snapshotBefore = table.snapshot(from) != null ? table.snapshot(from).snapshotId() : null;
  table.manageSnapshots().fastForwardBranch(from, to).commit();
  long snapshotAfter = table.snapshot(from).snapshotId();
  InternalRow outputRow = newInternalRow(UTF8String.fromString(from), snapshotBefore, snapshotAfter);
  return new InternalRow[] {outputRow};
});
```

要点：(a) 移除了 `Preconditions.checkArgument(currentSnapshot != null, ...)` 预检——这是关键，因为现在 from 不存在是合法输入；(b) 把变量名从 `source/target` 改为 `from/to`，与 API 层 `fastForwardBranch(from, to)` 命名对齐，减少概念错位；(c) `snapshotBefore` 改为 `Long`（装箱），from 不存在时取 null，提交后再读 `table.snapshot(from).snapshotId()` 得到 after（此时分支必然已存在，因为 core 层会创建）；(d) 删除了不再需要的 `Snapshot`、`Preconditions` import。返回行结构 `(branch_name, before_snapshot_id, after_snapshot_id)` 不变，但 before 列从"必非 null"变为"可 null"——这是调用方需要感知的契约放宽。

## 小结

本提交把 `fastForward`/`replace` 从"strict update"语义升级为"upsert"语义，是一个面向易用性的破坏性行为变更。它的价值在于：(1) 消除了"先 createBranch 再 fastForward"的两步操作，让分支工作流更接近 Git 直觉；(2) 复用 `createBranch` 实现保证新建路径与显式创建完全一致，避免逻辑分叉；(3) 跨 API/Core/Spark 三层同步契约、实现、测试，体现了 Iceberg 对"行为变更必须三层一致"的工程纪律。需要使用者注意的是：原本依赖"from 不存在抛异常"来兜底的代码（例如用异常做条件分支的程序）在升级含此提交的版本后会静默改变行为；同时 Spark `system.fast_forward` 返回行的 `before_snapshot_id` 列可能为 null，下游消费该 procedure 输出的 SQL/程序需能容忍 null。对 1.4.x 用户而言，这是一个升级时必须列入 release notes 评审的行为不兼容点。
