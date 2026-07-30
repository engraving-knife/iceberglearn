# 提交 3393：Core: Load snapshot after it has been committed to prevent accidental cleanup of files (#15511)

## 提交信息

- **序号**：3393 / 4088
- **哈希**：aa5894a139bca2967bf5cc01feef3a920fcc6ca5
- **短哈希**：aa5894a13
- **日期**：2026-03-16 12:23:32 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Load snapshot after it has been committed to prevent accidental cleanup of files (#15511)
- **PR/Issue**：#15511

## 总体目的

修复 `SnapshotProducer.commit()` 中提交后清理未提交文件时的一个潜在数据安全问题：在最终一致性（eventual consistency）存储系统中，提交成功后立即调用 `refresh()` 可能还看不到刚提交的 snapshot，导致清理逻辑误删属于已提交 snapshot 的文件。

背景：Iceberg 的 `SnapshotProducer` 在 commit 成功后会调用 `cleanUncommitted(...)` 清理未提交的 manifest 文件，并删除多次重试产生的多余 manifest list。原实现直接使用本地构建的 `stagedSnapshot`（`apply()` 产生的内存对象）的 manifest 列表作为"已提交"的判据来执行清理。问题在于：在多提交者并发或底层存储最终一致的情况下，某次 commit 尝试可能并未真正成功（而是另一次尝试成功了），此时用本地 staged snapshot 的 manifest 列表去清理，可能会误删真正成功提交的那个 snapshot 所包含的文件。本提交通过在清理前先 `refresh()` 并按 snapshotId 加载已持久化的 snapshot，确保只清理真正已提交 snapshot 之外的文件；如果 refresh 后仍加载不到该 snapshot，则跳过清理以保护数据安全。

## 如何达成设计目的

1. **改用 snapshotId 而非内存对象**：将 `AtomicReference<Snapshot> stagedSnapshot` 替换为 `AtomicLong newSnapshotId`，只记录最新尝试的 snapshot id。
2. **提交后 refresh 并按 id 加载**：commit 成功后调用 `ops.refresh()` 刷新表元数据，再用 `refresh().snapshot(newSnapshotId.get())` 加载该 snapshot。这样拿到的是从持久化元数据中读取的真实 snapshot，而非内存对象。
3. **加载成功才清理**：若 `saved != null`，则用 `saved.allManifests(ops.io())` 作为"已提交"集合执行 `cleanUncommitted`，并清理多余的 manifest list。
4. **加载失败跳过清理**：若 `saved == null`（refresh 后仍看不到该 snapshot，可能是最终一致性延迟），记录 warn 日志并跳过清理，避免误删。这是以"宁可留着垃圾文件也不误删数据"的安全优先策略。
5. **测试**：新增测试模拟 commit 后首次 refresh 返回陈旧元数据（不含新 snapshot）的场景，验证 commit 仍成功、文件不被误删，且后续 refresh 后 snapshot 可见。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java` (+30/-11 lines)

**修改目的**：将提交后清理逻辑从基于内存 staged snapshot 改为基于 refresh 后加载的持久化 snapshot，防止误删。

**工作逻辑**：
- **变量替换**：`AtomicReference<Snapshot> stagedSnapshot` → `AtomicLong newSnapshotId(-1L)`。在 `Tasks.run` 的 lambda 中，`stagedSnapshot.set(newSnapshot)` 改为 `newSnapshotId.set(newSnapshot.snapshotId())`。
- **清理逻辑重构**：
  - 原：直接用 `stagedSnapshot.get()` 作为 `committedSnapshot`，调用 `cleanUncommitted(Sets.newHashSet(committedSnapshot.allManifests(ops.io())))`，并清理不在 `committedSnapshot.manifestListLocation()` 中的 manifest list。
  - 新：先 `Snapshot saved = ops.refresh().snapshot(newSnapshotId.get())`。若 `saved != null` 且 `cleanupAfterCommit()`，则用 `saved.allManifests(ops.io())` 清理；并清理不在 `saved.manifestListLocation()` 中的 manifest list。若 `saved == null`，记录 warn 日志 `"Failed to load committed snapshot, skipping manifest clean-up"` 并跳过清理。
- 这确保了清理依据来自持久化元数据，而非可能未真正提交的内存对象。

### `core/src/test/java/org/apache/iceberg/TestSnapshotProducer.java` (+66 lines)

**修改目的**：新增测试验证 commit 后首次 refresh 返回陈旧元数据时，文件不会被误清理。

**工作逻辑**：
- `manifestNotCleanedUpWhenSnapshotNotLoadableAfterCommit`：通过自定义 `TestTableOperations`（`opsWithStaleRefreshAfterCommit`），让 commit 后第一次 `refresh()` 返回提交前的旧 `base` 元数据（不含新 snapshot），模拟最终一致性。
- 执行 `tableWithStaleRefresh.newAppend().appendFile(FILE_A).commit()`，验证 commit 成功。
- 再次 `refresh()` 后，`currentSnapshot()` 不为 null，且所有 manifest 文件仍存在于 metadata 目录中（未被清理删除）。
- `opsWithStaleRefreshAfterCommit` 通过在 `commit()` 中保存 `base`，并在下一次 `refresh()` 中返回它来实现陈旧行为，之后恢复正常 refresh。

### `core/src/test/java/org/apache/iceberg/TestTables.java` (+1/-1 lines)

**修改目的**：将 `TestTableOperations.current` 字段从 `private` 改为 `protected`，以便测试中的匿名子类访问。

**工作逻辑**：
- `private TableMetadata current = null;` → `protected TableMetadata current = null;`。这允许 `opsWithStaleRefreshAfterCommit` 中重写的 `refresh()` 直接操作 `this.current`。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (+1/-1 lines)

**修改目的**：调整 REST Catalog 测试中对 `adapter.execute(GET table)` 调用次数的断言。

**工作逻辑**：
- `Mockito.verify(adapter)` → `Mockito.verify(adapter, times(2))`。因为 `SnapshotProducer` 现在在 commit 后会额外调用一次 `ops.refresh()`，而 REST Catalog 的 refresh 会触发一次额外的 GET table 请求，所以从原来的 1 次变为 2 次。

## 总结

本提交修复了一个重要的数据安全问题：在最终一致性存储（如某些对象存储）上，`SnapshotProducer.commit()` 后的清理逻辑可能基于未真正提交的内存 snapshot 误删已提交文件。修复方案是在清理前先 `refresh()` 并按 snapshotId 加载持久化的 snapshot 作为清理依据，加载不到时跳过清理（安全优先）。这保证了即使在并发提交或存储最终一致性场景下，已提交数据也不会被误删。测试覆盖了关键的陈旧 refresh 场景。该改动会使 commit 后多一次 refresh 调用（在 REST Catalog 场景下多一次 HTTP 请求），这是为保证数据安全引入的合理开销。
