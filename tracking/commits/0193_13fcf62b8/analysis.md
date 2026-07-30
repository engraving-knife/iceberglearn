# 提交 0193：Spark: Fix Fast forward before/after snapshot output for non-main branches (#8854)

## 提交信息

- **序号**：0193 / 4088
- **哈希**：13fcf62b871dfec1b5ff09c1df35a5075cec328a
- **短哈希**：13fcf62b8
- **日期**：2023-11-23 10:12:26 -0800
- **作者**：Amogh Jahagirdar
- **提交说明**：Spark: Fix Fast forward before/after snapshot output for non-main branches (#8854)
- **PR/Issue**：#8854

## 总体目的

这个提交修复 Spark 中 `system.fast_forward` 存储过程在非 main 分支场景下的两个问题：输出结果错误，以及对不存在的分支缺少校验。

`fast_forward` 过程的作用是把某个分支（source）快进到另一个分支（target）的快照。过程会返回一行三列结果：`source_branch`、`before_snapshot`、`after_snapshot`，分别表示被快进的分支名、快进前的快照 id、快进后的快照 id。

修复前的实现有两个缺陷：

1. **`before`/`after` 快照含义错误且对非 main 分支会返回错误的值**。原代码用 `table.currentSnapshot().snapshotId()` 取"快进前"快照，又用快进后的 `table.currentSnapshot().snapshotId()` 取"快进后"快照。但 `currentSnapshot()` 永远返回表当前（main 分支）快照，与被快进的 source 分支无关。当 source 不是 main 时，`before` 取到的是 main 的快照而非 source 的快照，`after` 同样取的是 main 的快照而非 source 快进后的快照，结果完全误导用户。

2. **缺少对不存在分支的校验**。若 source 分支不存在，原代码直接调用 `table.currentSnapshot()`（在空表上）会 NPE，或对不存在的 ref 调用 `fastForwardBranch` 抛出含糊错误，用户体验差。

修复后：`before` 取 source 分支快进前的快照，`after` 取 source 分支快进后的快照；并在快进前校验 source 分支存在，给出清晰的错误信息。这对 Iceberg 分支管理（branch manipulation）功能的正确性与可用性是重要修复，确保 `fast_forward` 过程在任意分支间都能正确工作并返回准确元数据。

## 如何达成设计目的

核心改动在 `FastForwardBranchProcedure.java`：不再用 `table.currentSnapshot()`，改为用 `table.snapshot(source)` 获取 source 分支对应的快照；在快进前用 `Preconditions.checkArgument` 校验该快照非空（即 source 分支存在），否则抛 "Branch to fast-forward does not exist: %s"；快进后再次 `table.snapshot(source).snapshotId()` 取 source 分支的新快照 id 作为 `after`。测试侧新增两个用例覆盖：非 main 分支间的快进输出正确性，以及对不存在分支的校验错误信息。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/FastForwardBranchProcedure.java`

**修改目的**：修正快进过程返回的 before/after 快照取值，并增加对 source 分支存在的校验。

**工作逻辑**：
原代码：
```java
long currentRef = table.currentSnapshot().snapshotId();
table.manageSnapshots().fastForwardBranch(source, target).commit();
long updatedRef = table.currentSnapshot().snapshotId();
InternalRow outputRow = newInternalRow(UTF8String.fromString(source), currentRef, updatedRef);
```
改为：
```java
Snapshot currentSnapshot = table.snapshot(source);
Preconditions.checkArgument(
    currentSnapshot != null, "Branch to fast-forward does not exist: %s", source);
table.manageSnapshots().fastForwardBranch(source, target).commit();
long latest = table.snapshot(source).snapshotId();
InternalRow outputRow =
    newInternalRow(UTF8String.fromString(source), currentSnapshot.snapshotId(), latest);
```
关键变化：
- `before` 由 `table.currentSnapshot()` 改为 `table.snapshot(source)`，即取 source 分支快进前的快照，对任意分支都正确。
- `after` 由 `table.currentSnapshot()` 改为 `table.snapshot(source)`（快进后重新读取），即取 source 分支快进后的快照。
- 新增 `Preconditions.checkArgument(currentSnapshot != null, ...)`，当 source 分支不存在时抛 `IllegalArgumentException("Branch to fast-forward does not exist: <source>")`，给出清晰错误。
- 新增 `import org.apache.iceberg.Snapshot` 与 `import org.apache.iceberg.relocated.com.google.common.base.Preconditions`。

注意：`target` 分支不存在的校验由 `manageSnapshots().fastForwardBranch(source, target)` 自身完成（抛 "Ref does not exist: <target>"），测试也验证了这一点。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestFastForwardBranchProcedure.java`

**修改目的**：补充覆盖非 main 分支快进与不存在分支校验的测试。

**工作逻辑**：新增两个测试方法：

1. `testFastForwardNonExistingBranchCases`：建表后分别测试 source 不存在（`fast_forward(table, "non_existing_branch", MAIN)`，期望 "Branch to fast-forward does not exist: non_existing_branch"）与 target 不存在（`fast_forward(table, MAIN, "non_existing_branch")`，期望 "Ref does not exist: non_existing_branch"）两种错误路径。

2. `testFastForwardNonMain`：建表并插入数据，创建 `branch1`（基于 main 当前快照），向 `branch1` 写入数据得到 `branch1Snapshot`；再以 `branch1` 的快照 id 创建 `branch2`，向 `branch2` 写入数据得到 `branch2Snapshot`。然后调用 `fast_forward(table, branch1, branch2)`，断言返回行的三列分别为：`branch1`、`branch1Snapshot.snapshotId()`（before）、`branch2Snapshot.snapshotId()`（after）。这正是修复前会返回错误值的场景，现在能精确验证 before/after 取自 source 分支而非 main。

## 小结

修复 `fast_forward` 存储过程在非 main 分支间快进时返回错误的 before/after 快照 id 的问题，并补充对不存在分支的清晰校验，使分支快进过程的输出与错误处理在所有分支场景下都正确可靠。
