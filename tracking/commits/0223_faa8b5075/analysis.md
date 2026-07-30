# 提交 0223：Core: Fix logic for determining set of committed files in BaseTransaction when there are no new snapshots (#9221)

## 提交信息

- **序号**：0223 / 4088
- **哈希**：faa8b5075cb70d1cebea54700e39f038c623f08e
- **短哈希**：faa8b5075
- **日期**：2023-12-05 13:13:33 -0800
- **作者**：Amogh Jahagirdar
- **提交说明**：Core: Fix logic for determining set of committed files in BaseTransaction when there are no new snapshots (#9221)
- **PR/Issue**：#9221

## 总体目的

这是一个针对 `BaseTransaction` 提交后清理逻辑的实质性修复，解决了一个会在"无新快照"场景下错误删除已提交文件的缺陷。

`BaseTransaction` 在事务提交成功后会执行清理（`cleanUpOnCommitSuccess` 相关逻辑）：删除那些在事务过程中被标记为删除的"未提交文件"（`deletedFiles`）。为了防止误删那些虽然在某次操作中被删除、但在另一次成功提交中被复用的清单文件，清理逻辑会先调用 `committedFiles(ops, newSnapshots)` 计算出"本次事务真正提交的文件集合"，并在删除 `deletedFiles` 时跳过任何属于该集合的文件。

问题在于 `committedFiles` 方法的返回值语义被错误地重载：它在两种情况下都返回 `null`：
1. 当 `snapshotIds` 为空（即事务没有产生任何新快照）——这是正常情况；
2. 当某个快照的元数据无法从 `ops.current().snapshot(snapshotId)` 加载——这是异常情况。

而调用方的判断逻辑是 `if (committedFiles == null || !committedFiles.contains(path))`，意为"如果 `committedFiles` 为 null 或者 path 不在已提交集合中，则删除"。这意味着 `null` 被解释为"无法确定已提交集合，所以全部删除"。

这两者叠加产生了一个严重的语义冲突：在情况 1（无新快照，正常）下，`committedFiles` 返回 `null`，调用方据此将 `deletedFiles` 中的所有文件全部删除——但此时并没有任何新提交，所以 `deletedFiles` 实际上对应的可能正是已存在的、被前序操作引用的文件，全部删除是危险的。正确的语义应当是：无新快照意味着没有任何已提交文件，`committedFiles` 应为空集合（而非 `null`），从而让所有 `deletedFiles` 都被删除——这恰恰是事务已经把它们标记为删除的合理结果；而只有真正无法加载元数据时才应返回 `null`，表示"无法确定，跳过清理以保安全"。

本提交通过区分这两种语义来修复该缺陷：`committedFiles` 在 `snapshotIds` 为空时返回 `ImmutableSet.of()`（空集合，明确表示"没有已提交文件"），仅在元数据加载失败时返回 `null`；调用方相应地：当 `committedFiles != null` 时按集合正常判断删除，当为 `null` 时跳过清理并打印警告。

## 如何达成设计目的

设计思路是消除 `null` 返回值的歧义：让"无新快照"（正常、明确的空集合语义）和"无法加载元数据"（异常、不可推断语义）走两条不同的路径。改动分两处协同进行：

1. 修改 `committedFiles(...)` 的返回值：`snapshotIds.isEmpty()` 时由 `return null` 改为 `return ImmutableSet.of()`，并为该方法添加注释说明 `null` 的含义已经收敛为"无法从提供的快照确定已提交文件集合"。
2. 重构调用方的清理逻辑：将 `if (committedFiles == null || !committedFiles.contains(path))` 改为先判空再遍历——只有 `committedFiles != null` 时才执行删除，且删除条件简化为 `!committedFiles.contains(path)`；为 `null` 时跳过清理并记录警告日志。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseTransaction.java`

**修改目的**：修正 `committedFiles` 的返回值语义，并相应调整调用方清理逻辑，避免在"无新快照"场景下误删文件、在"元数据加载失败"场景下错误地全删而非跳过。

**工作逻辑**：

改动一：新增 `ImmutableSet` 的导入。

```java
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
```

改动二：调用方清理逻辑重构。原逻辑：

```java
Set<String> committedFiles = committedFiles(ops, newSnapshots);
// delete all of the files that were deleted in the most recent set of operation commits
Tasks.foreach(deletedFiles)
    .suppressFailureWhenFinished()
    .onFailure((file, exc) -> LOG.warn("Failed to delete uncommitted file: {}", file, exc))
    .run(
        path -> {
          if (committedFiles == null || !committedFiles.contains(path)) {
            ops.io().deleteFile(path);
          }
        });
```

新逻辑：

```java
Set<String> committedFiles = committedFiles(ops, newSnapshots);
if (committedFiles != null) {
  // delete all of the files that were deleted in the most recent set of operation commits
  Tasks.foreach(deletedFiles)
      .suppressFailureWhenFinished()
      .onFailure((file, exc) -> LOG.warn("Failed to delete uncommitted file: {}", file, exc))
      .run(
          path -> {
            if (!committedFiles.contains(path)) {
              ops.io().deleteFile(path);
            }
          });
} else {
  LOG.warn("Failed to load metadata for a committed snapshot, skipping clean-up");
}
```

关键行为变化：
- 旧逻辑中 `committedFiles == null` 会让 `if` 条件恒真，导致所有 `deletedFiles` 被删除；
- 新逻辑中 `committedFiles == null` 表示无法确定已提交集合（仅元数据加载失败场景），此时跳过整个清理并打警告，避免误删；
- `committedFiles != null`（包括空集合）时，仅删除不在已提交集合中的文件，与"无新快照 → 空集合 → 全部删除"的期望一致，也与"有新快照 → 仅删除未引用文件"的期望一致。

改动三：`committedFiles` 方法返回值调整与注释。新增注释：

```java
// committedFiles returns null whenever the set of committed files
// cannot be determined from the provided snapshots
```

并将空快照分支：

```java
if (snapshotIds.isEmpty()) {
  return null;
}
```

改为：

```java
if (snapshotIds.isEmpty()) {
  return ImmutableSet.of();
}
```

这样 `null` 只在 `snap == null`（元数据加载失败）分支返回，与调用方的 `else` 分支（跳过清理）语义对齐。

## 小结

通过将 `committedFiles` 在"无新快照"时返回空集合而非 `null`，并把 `null` 严格收敛为"元数据加载失败"语义，本提交修复了 `BaseTransaction` 提交后清理逻辑中两种情况被错误合并导致的潜在误删风险，使清理行为在正常与异常路径上都符合预期。
