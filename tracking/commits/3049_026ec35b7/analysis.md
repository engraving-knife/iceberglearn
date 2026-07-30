# 提交 3049：Core: Small cleanup in MergingSnapshotProducer cleanUncommittedAppends (#14923)

## 提交信息

- **序号**：3049 / 4088
- **哈希**：026ec35b7969539b535af03944fae53d059cd233
- **短哈希**：026ec35b7
- **日期**：2025-12-23
- **作者**：Amogh Jahagirdar
- **提交说明**：Core: Small cleanup in MergingSnapshotProducer cleanUncommittedAppends (#14923)
- **PR/Issue**：#14923

## 总体目的

Iceberg 在提交快照（commit）时，对于未被最终提交采用的 manifest 文件需要做“未提交清理（cleanUncommitted）”——即删除那些生成了但没进入新快照的 manifest，避免产生孤儿文件。这一逻辑分散在多个 `SnapshotProducer` 子类中（`MergingSnapshotProducer`、`FastAppend`、`BaseRewriteManifests`），它们各自手写了几乎相同的循环：遍历 manifest 集合，凡是不在 `committed` 集合中的就调用 `deleteFile(manifest.path())`，并在删除发生后视情况清空本地缓存列表。

这种重复带来两个问题：一是代码冗余、可读性差，每处都要维护 `hasDeletes` 标志位和 `clear()` 逻辑；二是容易出现细微不一致（例如 `BaseRewriteManifests.reset()` 里既调用了 `cleanUncommitted` 又额外手动 `newManifests.clear()`，而 `FastAppend` 把 clear 逻辑内联在循环后）。此提交的目的就是抽取一个统一的 `deleteUncommitted` 辅助方法到基类 `SnapshotProducer`，让所有子类复用，消除重复并统一“删除未提交 manifest + 可选清空列表”的行为。同时顺带移除了 `MergingSnapshotProducer` 中一处重复的 `Preconditions.checkArgument` 校验（`case 3` 与 `case 4` 校验体完全相同）。

## 如何达成设计目的

在基类 `SnapshotProducer` 中新增 `protected deleteUncommitted(Collection<ManifestFile>, Set<ManifestFile> committed, boolean clearManifests)`，封装“遍历-删除-按需清空”三步。随后 `FastAppend`、`BaseRewriteManifests`、`MergingSnapshotProducer` 三处的 `cleanUncommitted`/`cleanUncommittedAppends` 改为调用该辅助方法，传入不同的 `clearManifests` 标志（自身拥有的待提交 manifest 缓存用 `true` 清空，仅做删除的 rewritten/append manifest 用 `false` 不清空）。`MergingSnapshotProducer` 还把 `cleanUncommittedAppends` 与 `cleanUncommitted` 的方法顺序做了调整，使调用者在前、被复用逻辑更清晰。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java` (+16/-0 lines)

**修改目的**：在基类提供统一的未提交 manifest 删除辅助方法。

**工作逻辑**：新增 `protected void deleteUncommitted(Collection<ManifestFile> manifests, Set<ManifestFile> committed, boolean clearManifests)`。逻辑为：遍历 `manifests`，凡不在 `committed` 中的调用 `deleteFile(manifest.path())` 并置 `anyDeleted = true`；若 `clearManifests && anyDeleted` 则调用 `manifests.clear()`。`clearManifests` 参数区分两种语义——对于子类自己持有并缓存的“新 manifest 列表”，删除后应清空缓存以避免重复使用；对于只是“借来删除”的 rewritten/append 列表则不清空。这一封装统一了原先散落各处的 `hasDeletes` 标志位写法。

### `core/src/main/java/org/apache/iceberg/FastAppend.java` (+2/-13 lines)

**修改目的**：用 `deleteUncommitted` 替换两段重复的手写循环。

**工作逻辑**：
- `cleanUncommitted` 中对 `newManifests` 的处理：原先手写循环判断 `!committed.contains` → `deleteFile` → 记 `hasDeletes` → 最后 `if (hasDeletes) newManifests.clear()`，现替换为 `deleteUncommitted(newManifests, committed, true /* clear manifests */)`。
- 对 `rewrittenAppendManifests` 的处理：原先手写循环仅删除不清空，现替换为 `deleteUncommitted(rewrittenAppendManifests, committed, false)`。
- 移除了 `@SuppressWarnings` 中已不需要的逻辑及本地变量。

### `core/src/main/java/org/apache/iceberg/BaseRewriteManifests.java` (+4/-13 lines)

**修改目的**：统一 `reset()` 与 `cleanUncommitted` 的清理调用，消除重复的私有 `cleanUncommitted` 方法。

**工作逻辑**：
- `reset()` 原先调用私有 `cleanUncommitted(newManifests, ImmutableSet.of())` 后又单独 `newManifests.clear()`，现改为 `deleteUncommitted(newManifests, ImmutableSet.of(), true /* clear new manifests */)`，把删除与清空合为一步，去掉多余的 `newManifests.clear()`。
- `cleanUncommitted` 覆写原先调用私有 `cleanUncommitted(...)`，现改为 `deleteUncommitted(newManifests, committed, false)` 与 `deleteUncommitted(rewrittenAddedManifests, committed, false)`。
- 删除了私有的 `cleanUncommitted(Iterable, Set)` 方法（其循环体已被基类方法取代）。

### `core/src/main/java/org/apache/iceberg/MergingSnapshotProducer.java` (+7/-50 lines)

**修改目的**：精简 `cleanUncommittedAppends`，移除重复校验，并调整方法顺序。

**工作逻辑**：
- `cleanUncommittedAppends` 原先包含四段几乎相同的手写循环（针对 `cachedNewDataManifests`、`cachedNewDeleteManifests`、`rewrittenAppendManifests`、`appendManifests`），每段都带 `hasDeletes`/`hasDeleteDeletes` 标志位与条件 `clear()`。现全部替换为 `deleteUncommitted(..., true)`（前两者清空缓存）或 `deleteUncommitted(..., false)`（后两者仅删除）。`appendManifests` 分支保留了 `if (!committed.isEmpty())` 的前置条件（仅当提交成功、表拥有这些 manifest 时才清理未使用的），语义不变。
- 移除了 `@SuppressWarnings("checkstyle:CyclomaticComplexity")`，因为方法已足够简单不再触发圈复杂度阈值。
- 把 `cleanUncommitted` 覆写方法移到 `cleanUncommittedAppends` 之后（仅顺序调整，便于阅读）。
- 额外清理：`validateFiles`（或类似校验方法）的 `switch` 中，`case 3:` 原先有与 `case 4:` 完全相同的 `Preconditions.checkArgument(... "Must use DVs for position deletes in V%s" ...)`，现删除 `case 3` 的独立方法体与 `break`，使其 fall-through 到 `case 4`，消除重复校验。该校验要求格式版本 V3/V4 的 position delete 必须使用 DV（deletion vector），逻辑等价。

## 总结

此提交是一次纯重构性的代码清理，核心价值在于把“删除未提交 manifest 并可选清空缓存”这一散落三处的重复模式收敛到基类 `SnapshotProducer.deleteUncommitted`，统一行为、降低维护成本，并顺带移除了 `MergingSnapshotProducer` 中重复的 DV 校验分支。不改变任何运行时语义，无功能性影响，但显著改善了核心提交链路的可读性与一致性，降低未来引入 bug 的风险。
