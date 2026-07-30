# 提交 1535 1b5886d0e 分析

## 提交信息
- 哈希：1b5886d0e6a97043259b8cda18d146feb5a552a4
- 日期：2024-12-25（Wed Dec 25 00:24:22 2024 +0900）
- 作者：Yuya Ebihara <ebyhry@gmail.com>
- 消息：Core: Don't clear snapshotLog in `TableMetadata.removeRef` (#11779)

## 总体目的

`TableMetadata` 中维护着 `snapshotLog`（快照日志/历史），它是一组 `HistoryEntry`（时间戳 + 快照 ID）的列表，记录了"哪个快照在何时成为当前快照"，是表的历史时间线，供 `history()`、time travel 等功能使用。

`Builder.removeRef(String name)` 用于移除一个快照引用（branch 或 tag）。当移除的是主分支 `main`（`SnapshotRef.MAIN_BRANCH`）时，原实现除了把 `currentSnapshotId` 置为 -1（表示无当前快照）外，还额外执行了 `snapshotLog.clear()`，把整条历史时间线清空。

这一行为会带来问题：`TableMetadata.buildReplacement(...)` 在构建"替换表"的新元数据时会调用 `removeRef(SnapshotRef.MAIN_BRANCH)`（见 TableMetadata.java:1028），作为替换流程的一部分来移除旧主分支引用。由于 `removeRef` 顺带清空了 `snapshotLog`，导致 `replaceTableTransaction` / `replaceTable` 这类"替换表"操作会把表的历史时间线全部丢弃——替换前累积的 history entry 全部丢失，用户无法再通过 time travel 回溯替换前的快照时间线。

本提交移除 `snapshotLog.clear()` 这一行，使 `removeRef(main)` 仅清除当前快照指针，但保留历史日志，从而让"替换表"操作保留替换前的 snapshot 历史。这与 `removeRef` 的语义更一致：移除一个引用不应等同于抹除整张表的历史。

## 如何达成设计目的

核心改动只有删除一行 `snapshotLog.clear()`。为保证不引入回归并锁定预期行为，新增两个测试：一个针对 `TableMetadata` 单元层（验证 `removeRef(MAIN_BRANCH)` 后 snapshotLog 仍保留），一个针对 catalog 层（验证 `replaceTableTransaction` 后 snapshotLog 跨替换连续累积）。

### 修改详情

#### `core/src/main/java/org/apache/iceberg/TableMetadata.java`

**修改目的**：让 `Builder.removeRef` 在移除主分支时不再清空 snapshotLog。

**工作逻辑**：

修改前（约 1283 行）：
```java
public Builder removeRef(String name) {
  if (SnapshotRef.MAIN_BRANCH.equals(name)) {
    this.currentSnapshotId = -1;
    snapshotLog.clear();   // <-- 被删除的行
  }
  SnapshotRef ref = refs.remove(name);
  ...
}
```

修改后：
```java
public Builder removeRef(String name) {
  if (SnapshotRef.MAIN_BRANCH.equals(name)) {
    this.currentSnapshotId = -1;
  }
  SnapshotRef ref = refs.remove(name);
  ...
}
```

仅保留 `currentSnapshotId = -1`（表示主分支被移除后无当前快照），不再动 `snapshotLog`。`snapshotLog` 是独立于 `refs` 的历史记录字段，记录的是"时间线"而非"引用状态"，移除引用不应影响历史。注意 `buildReplacement` 调用 `removeRef(MAIN_BRANCH)` 后仍会通过后续 `build()` 阶段追加新的 history entry（替换后产生的新快照），因此替换后 snapshotLog 会同时包含替换前的旧 entry 和替换后的新 entry，形成连续时间线。

#### `core/src/test/java/org/apache/iceberg/TestTableMetadata.java`

**修改目的**：单元层验证 `removeRef(MAIN_BRANCH)` 不清空 snapshotLog。

**工作逻辑**：新增 `removeRefKeepsSnapshotLog()` 测试，读取 `TableMetadataV2Valid.json`（含 2 个快照、snapshotLog 长度为 2），对其执行 `buildFrom(metadata).removeRef(SnapshotRef.MAIN_BRANCH).build()`，断言：
- `currentSnapshot()` 为 null（主分支移除后无当前快照，符合预期）
- `snapshots()` 仍为 2 个且与原 metadata 一致
- `snapshotLog()` 仍为 2 个且与原 metadata 一致（关键：不再被清空）

#### `core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java`

**修改目的**：catalog 层验证 `replaceTableTransaction` 跨替换保留 snapshotLog。

**工作逻辑**：新增 `testReplaceTableKeepsSnapshotLog()` 测试，流程：
1. 创建表并执行一次 append（snapshotLog 长度=1，记下 entry `snapshotBeforeReplace`）
2. 通过 `catalog.newReplaceTableTransaction(...)` 替换表，并在事务内执行一次 append，提交事务
3. `table.refresh()` 后取 snapshotLog，断言长度=2，且按顺序为 `[snapshotBeforeReplace, snapshotAfterReplace]`，且两个 entry 不相等

这直接复现了原 bug 场景（替换表后旧 history entry 丢失），修复后该测试通过。新增 `import org.apache.iceberg.HistoryEntry;` 以使用历史条目类型。

## 小结

- **成效**：修复了 `TableMetadata.Builder.removeRef(main)` 误清 `snapshotLog` 导致"替换表"操作丢失历史时间线的 bug。替换表（`replaceTable`/`replaceTableTransaction`）现在能保留替换前的 snapshot 历史，time travel 与 `history()` 在替换后仍可回溯旧时间线。
- **影响范围**：核心元数据逻辑 1 行删除 + 2 个测试新增。改动影响所有 catalog 的 `replaceTable` 行为，是用户可感知的语义修正。
- **回迁到 1.4.x 的注意事项**：这是一处**真正的 bug 修复**，影响表的元数据正确性（替换表后历史丢失）。如果 1.4.x 用户使用 `replaceTableTransaction` / `replaceTable`，会受此 bug 影响。**建议回迁**到 1.4.x，特别是如果 1.4.x 后续还会发布维护版本。回迁时需同时带回 3 个文件的改动（主代码 + 2 个测试），并确认 1.4.x 的 `buildReplacement` 调用链与 main 一致（同样通过 `removeRef(MAIN_BRANCH)`）。
