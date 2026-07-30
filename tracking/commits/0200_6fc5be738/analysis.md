# 提交 0200：API, Core: Fix naming in fastForwardBranch/replaceBranch APIs (#9134)

## 提交信息

- **序号**：0200 / 4088
- **哈希**：6fc5be738d317eb1e8a5b525f5b8e40841dee678
- **短哈希**：6fc5be738
- **日期**：2023-11-27
- **作者**：Amogh Jahagirdar
- **提交说明**：API, Core: Fix naming in fastForwardBranch/replaceBranch APIs (#9134)
- **PR/Issue**：#9134

## 总体目的

这个提交修复了 `ManageSnapshots` 接口中 `fastForwardBranch` 和 `replaceBranch` 两个 API 的参数命名歧义问题。在此提交之前，这两个方法的参数名为 `(String name, String source)`，其中 `name` 表示"要被替换/快进的分支"（target），`source` 表示"作为来源的引用"。但 `name`/`source` 这对命名容易让调用方产生误解——难以直观判断哪个参数是会被修改的一方、哪个是参考来源，特别是在 `replaceBranch(name, source)` 这种"用 source 替换 name"的语义下，方向性不清晰。

提交将参数重命名为 `(String from, String to)`：`from` 表示"要更新的分支"，`to` 表示"目标引用/快进到的引用"。`from`→`to` 的方向性命名与 git 的 `fast-forward` 语义一致（把 from 推进到 to），更符合直觉。同时同步更新了 Javadoc、`SnapshotManager` 实现、`UpdateSnapshotReferencesOperation` 内部逻辑变量名（`name`/`source`/`sourceRef`/`refToUpdate` → `from`/`to`/`toRef`/`branchToUpdate`）以及相关测试方法名与断言消息。这是一次纯命名重构，不改变运行时行为，但显著提升了 API 的可读性与可维护性，对 Iceberg 表快照管理 API 的易用性有正面意义。

## 如何达成设计目的

整个改动是一次跨 api/core 模块的命名一致性重构：在 `api` 模块的 `ManageSnapshots` 接口修改方法签名与 Javadoc，在 `core` 模块的 `SnapshotManager` 实现类与 `UpdateSnapshotReferencesOperation` 操作类同步修改签名与内部变量名及错误消息，最后更新 `TestSnapshotManager` 中的测试方法名、断言消息并补充了此前缺失的 fast-forward 失败路径测试。

## 修改详情

### `api/src/main/java/org/apache/iceberg/ManageSnapshots.java`

**修改目的**：修改接口契约的参数命名与 Javadoc，使语义方向清晰。

**工作逻辑**：
- `replaceBranch(String name, String source)` → `replaceBranch(String from, String to)`；Javadoc 由"Replaces the branch with the given name to point to the source snapshot. The source branch will remain unchanged..."改为"Replaces the `from` branch to point to the `to` snapshot. The `to` will remain unchanged, and `from` branch will retain its retention properties."；参数说明 `@param name Branch to replace` / `@param source Source reference...` → `@param from Branch to replace` / `@param to The branch from should be replaced with`。
- `fastForwardBranch(String name, String source)` → `fastForwardBranch(String from, String to)`；Javadoc 同步把 target/source 表述改为 from/to，`@throws` 异常说明由"if the target branch is not an ancestor of source"改为"if `from` is not an ancestor of `to`"。

### `core/src/main/java/org/apache/iceberg/SnapshotManager.java`

**修改目的**：同步实现类的方法签名，使其与接口新命名一致。

**工作逻辑**：`replaceBranch(String name, String source)` → `replaceBranch(String from, String to)`，内部调用 `updateSnapshotReferencesOperation().replaceBranch(from, to)`；`fastForwardBranch(String name, String source)` → `fastForwardBranch(String from, String to)`，内部调用 `.fastForward(from, to)`。

### `core/src/main/java/org/apache/iceberg/UpdateSnapshotReferencesOperation.java`

**修改目的**：同步内部操作类的参数与局部变量命名，统一为 from/to 方向语义。

**工作逻辑**：
- `replaceBranch(String name, String source)` → `replaceBranch(String from, String to)`，`fastForward(String name, String source)` → `fastForward(String from, String to)`，二者都委托给私有 `replaceBranch(from, to, fastForward)`。
- 私有方法签名改为 `replaceBranch(String from, String to, boolean fastForward)`：局部变量 `refToUpdate` → `branchToUpdate`、`sourceRef` → `toRef`，对应从 `updatedRefs.get(name)` / `updatedRefs.get(source)` 改为 `updatedRefs.get(from)` / `updatedRefs.get(to)`。
- 校验消息同步：`"Target branch cannot be null"` → `"Branch to update cannot be null"`、`"Source ref cannot be null"` → `"Destination ref cannot be null"`、`"Target branch does not exist: %s"` → `"Branch to update does not exist: %s"`、`"Ref %s is a tag not a branch"` 的参数由 `name` 改为 `from`。
- 快进祖先校验 `SnapshotUtil.isAncestorOf(sourceRef.snapshotId(), refToUpdate.snapshotId(), ...)` 改为 `isAncestorOf(toRef.snapshotId(), branchToUpdate.snapshotId(), ...)`（即校验 from 是否为 to 的祖先），错误消息 `"Cannot fast-forward: %s is not an ancestor of %s"` 参数顺序由 `(name, source)` 改为 `(from, to)`。最终 `updatedRefs.put(name, updatedRef)` 改为 `updatedRefs.put(from, updatedRef)`。

### `core/src/test/java/org/apache/iceberg/TestSnapshotManager.java`

**修改目的**：同步测试方法名与断言消息，并补全此前缺失的 fast-forward 失败路径测试。

**工作逻辑**：
- `testReplaceBranchNonExistingTargetBranchFails` → `testReplaceBranchNonExistingBranchToUpdateFails`，断言消息 `"Target branch does not exist: non-existing"` → `"Branch to update does not exist: non-existing"`。
- `testReplaceBranchNonExistingSourceFails` → `testReplaceBranchNonExistingToBranchFails`。
- 新增 `testFastForwardBranchNonExistingFromBranchFails`：调用 `fastForwardBranch("non-existing", "other-branch")` 期望抛 `IllegalArgumentException` 且消息为 `"Branch to update does not exist: non-existing"`。
- 新增 `testFastForwardBranchNonExistingToFails`：先建 branch1 再调用 `fastForwardBranch("branch1", "non-existing")` 期望消息 `"Ref does not exist: non-existing"`，补齐了 to 引用不存在时的覆盖。
- `testFastForwardWhenTargetIsNotAncestorFails` → `testFastForwardWhenFromIsNotAncestorFails`。

## 小结

该提交通过把 `fastForwardBranch`/`replaceBranch` 的参数从语义模糊的 `name`/`source` 重命名为方向明确的 `from`/`to`，并同步 Javadoc、实现与测试，显著提升了 Iceberg 快照管理 API 的可读性与一致性。
