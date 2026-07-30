# 提交 0212：Core: Expired Snapshot files in a transaction should be deleted. (#9183)

## 提交信息

- **序号**：0212 / 4088
- **哈希**：99843f03efecc3f5ef7d3e1d32aae5eff22cb315
- **短哈希**：99843f03e
- **日期**：2023-12-04 14:46:37 -0800
- **作者**：Andrew Sherman
- **提交说明**：Core: Expired Snapshot files in a transaction should be deleted. (#9183)
- **PR/Issue**：#9183

## 总体目的

本提交修复 Iceberg 事务（`Transaction`）提交阶段对"过期快照（expired snapshot）关联文件清理"的一个回归 bug，导致在事务内调用 `expireSnapshots` 而该事务又没有新提交任何数据文件时，本应被删除的 manifest list 文件（snap-*.avro）被错误地保留下来，从而形成元数据泄漏。

回归源头是先前合入的 #6634。#6634 的初衷是：当一个事务同时提交了新文件并过期了旧快照时，要避免把"已经在这次事务里被新 snapshot 引用的 manifest list"误删——因为事务可能被整体重试，删除已提交文件会让重试失败。为实现这一点，#6634 在 `BaseTransaction` 提交成功后调用 `committedFiles(ops, newSnapshots)` 获取"本次事务新提交的文件集合"，然后**仅当** `committedFiles != null` 时才执行清理，并在清理时跳过 `committedFiles` 中的路径。

问题在于：当一次事务只做过期、没有产生任何新 snapshot（即 `newSnapshots` 为空）时，`committedFiles(...)` 返回的是**空集合**而非 null——但 #6634 的实现里把"返回 null（加载失败）"作为唯一跳过清理的条件，反而把"空集合"这条路径也错误地纳入了 if 分支并最终因没有任何东西被排除而看似没问题……实际更直接的回归是：当 `committedFiles` 为 null（无法加载已提交 snapshot 的元数据）时，#6634 完全跳过了清理，连那些纯粹由过期产生的待删 manifest list 也不删了；以及更核心地——`committedFiles` 为空集时，原始逻辑里 `if (committedFiles != null)` 走入分支，但对每个 deletedFile 都执行 `if (!committedFiles.contains(path))`，这本应删掉所有 deleted 文件，可配合 #6634 引入的"先排除已提交文件"路径，在某些情况下（尤其纯过期、无新提交）会进入 `LOG.warn("Failed to load metadata for a committed snapshot, skipping clean-up")` 分支而整体跳过删除。无论哪种触发，最终结果都是 manifest list 在 metadata 目录下持续累积，长期造成存储浪费与潜在读放大。

本提交把逻辑修正为：无论 `committedFiles` 是否为 null 都进入清理流程，仅当 `committedFiles != null && committedFiles.contains(path)` 时跳过删除；若 `committedFiles == null`（加载失败）则照常删除所有 deletedFiles——因为加载失败时没有可靠依据去保护任何文件，而 deletedFiles 是本次事务明确要删除的，删除它们是安全的。这恢复了"事务提交时清理过期快照 manifest list"的基本语义。

## 如何达成设计目的

设计思路非常直接：把"是否进入清理流程"与"是否跳过某个具体文件"两个判断解耦。原代码把 `committedFiles == null` 当作跳过整个清理的条件，新代码把 `committedFiles == null` 当作"无法精确判断、保守地全部删除"的条件——因为 deletedFiles 中的文件本来就是这次事务操作要删除的，不删反而是回归。同时新增了三个测试断言，验证纯过期场景下 manifest list 数量从 3→2（TestRemoveSnapshots）和 2→1（TestSequenceNumberForV2Table），以锁定该回归不再重现。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseTransaction.java`

**修改目的**：修复事务提交后清理 `deletedFiles` 时因 `committedFiles == null` 而整体跳过删除的回归，确保过期快照的 manifest list 在事务提交时被正确删除。

**工作逻辑**：

修改位于提交成功后的 cleanup 块（`commit` 流程的尾部）。

原逻辑：

```java
Set<String> committedFiles = committedFiles(ops, newSnapshots);
if (committedFiles != null) {
  Tasks.foreach(deletedFiles)
      .suppressFailureWhenFinished()
      .onFailure((file, exc) -> LOG.warn("Failed to delete uncommitted file: {}", file, exc))
      .run(path -> {
        if (!committedFiles.contains(path)) {
          ops.io().deleteFile(path);
        }
      });
} else {
  LOG.warn("Failed to load metadata for a committed snapshot, skipping clean-up");
}
```

新逻辑：

```java
Set<String> committedFiles = committedFiles(ops, newSnapshots);
// delete all of the files that were deleted in the most recent set of operation commits
Tasks.foreach(deletedFiles)
    .suppressFailureWhenFinished()
    .onFailure((file, exc) -> LOG.warn("Failed to delete uncommitted file: {}", file, exc))
    .run(path -> {
      if (committedFiles == null || !committedFiles.contains(path)) {
        ops.io().deleteFile(path);
      }
    });
```

关键行为变化：

1. **始终执行清理**：移除 `if (committedFiles != null)` 外层判断，无论 `committedFiles` 是否为 null，都会遍历 `deletedFiles` 并尝试删除，不再有"skipping clean-up"分支。这保证纯过期、无新提交的事务也能把过期 snapshot 的 manifest list 删掉。

2. **null 的语义反转**：原先 `committedFiles == null` 表示"加载失败 → 什么也不删"；新代码表示"加载失败 → 全部删除"。这是合理的——`deletedFiles` 是事务内明确标记要删的文件，加载已提交元数据失败并不能成为保留它们的依据；而且加载失败时根本无法判断哪个文件"已被提交"需要保护，保留它们只会造成泄漏。若加载成功，则用 `committedFiles.contains(path)` 跳过那些被新 snapshot 引用、必须保留的文件，保留 #6634 防止重试时误删已提交文件的初衷。

注释也从"clean up the data files that were deleted by each operation. first, get the list of committed manifests to ensure that no committed manifest is deleted. A manifest could be deleted in one successful operation commit, but reused in another successful commit of that operation if the whole transaction is retried."调整为简化的"delete all of the files that were deleted in the most recent set of operation commits"，但仍保留上方对 `committedFiles` 的计算。

### `core/src/test/java/org/apache/iceberg/TableTestBase.java`

**修改目的**：新增测试辅助方法 `listManifestLists(String tableDirToList)`，用于在测试中列出 metadata 目录下以 `snap` 开头、扩展名为 `.avro` 的 manifest list 文件，供后续断言使用。

**工作逻辑**：与已有的 `listManifestFiles` 类似，但匹配前缀 `snap` 而非 `*` manifest 文件前缀。manifest list 是 snapshot 的 manifest list 文件（`snap-<snapshotId>-<commitId>.avro`），与 manifest 文件（`*-m0.avro`）区分开。

### `core/src/test/java/org/apache/iceberg/TestRemoveSnapshots.java`

**修改目的**：扩展 `expireOlderThan + retainLast` 在事务内的测试，断言 manifest list 文件数量随快照过期而减少，从而锁定本修复。

**工作逻辑**：在保留最后 2 个快照的事务前后分别断言 `listManifestLists(table.location()).size()`——事务前为 3，事务后为 2（被删的快照对应的 manifest list 被删除）。这直接验证了修复的核心场景：事务内纯过期，manifest list 必须被删。

### `core/src/test/java/org/apache/iceberg/TestSequenceNumberForV2Table.java`

**修改目的**：在 V2 表序列号测试中补加 manifest list 数量断言，验证事务内 `expireSnapshotId` 同样会删除对应 manifest list。

**工作逻辑**：依次断言——第一次 append 后有 1 个 manifest list；第二次 append 后有 2 个；在事务内 `expireSnapshotId(commitId1)` 提交后应只剩 1 个 manifest list（"Should be 1 manifest list as 1 was deleted"）。这条断言是修复的直接回归测试：在 #6634 已合入但本修复未应用的代码上会失败。

## 小结

本提交通过解耦"是否清理"与"是否跳过单个文件"两个判断，修复了 #6634 引入的回归，确保事务内过期快照的 manifest list 在没有新提交时也能被正确删除，避免元数据泄漏。
