# 提交 1397：Core: Inherited classes from SnapshotProducer has TableOperations redundantly as member (#11578)

## 提交信息

- **序号**：1397 / 4088
- **哈希**：e71e3cb1954fec66dc8915da2854e8fe02d08197
- **短哈希**：e71e3cb19
- **日期**：2024-11-19（Tue Nov 19 14:23:55 2024 +0100）
- **作者**：gaborkaszab <gaborkaszab@cloudera.com>
- **提交说明**：Core: Inherited classes from SnapshotProducer has TableOperations redundantly as member (#11578)
- **PR/Issue**：#11578

## 总体目的

`SnapshotProducer<ThisT>` 是 Iceberg Core 中所有"产生快照"操作（append、overwrite、delete、rewrite manifests 等）的抽象基类。它本身已经持有一个 `protected TableOperations ops` 成员（在构造时传入），子类可以直接通过 `ops` 字段访问。

然而 `SnapshotProducer` 的几个子类——`BaseRewriteManifests`、`FastAppend`、`MergingSnapshotProducer`——在自己的类里又各自声明了一个 `private final TableOperations ops`，并在构造函数里把同一个引用再赋一遍。这导致：

1. **冗余**：同一个 `TableOperations` 实例在父子类中存了两份，子类的 `this.ops` 与父类的 `super.ops` 是同一个对象，但通过两个不同字段访问。
2. **可维护性差**：未来如果父类决定改变 `ops` 的获取方式（例如改为动态查询、或在某个生命周期阶段切换），子类的本地副本不会同步，造成隐含 bug。
3. **可读性差**：阅读代码时容易困惑：为什么子类要重新声明一个同名字段？是否有特殊语义？

本提交清理这种冗余：在父类 `SnapshotProducer` 暴露一个 `protected TableOperations ops()` 访问器方法，子类删除自己的 `ops` 成员，把所有 `this.ops.xxx()` 调用改为 `ops().xxx()`，统一通过父类的访问器获取。

## 如何达成设计目的

1. **在 `SnapshotProducer` 新增 `protected TableOperations ops()`**：返回父类已有的 `ops` 字段。这样子类无需自己持有 `ops`，通过继承的 `ops()` 方法即可访问。

2. **在 `BaseRewriteManifests`、`FastAppend`、`MergingSnapshotProducer` 中**：
   - 删除 `private final TableOperations ops;` 字段声明。
   - 在构造函数中删除 `this.ops = ops;` 赋值（仍调用 `super(ops)` 把 ops 传给父类）。
   - 把方法体中所有 `ops.current()`、`ops.io()`、`ops.refresh()` 等调用改为 `ops().current()`、`ops().io()`、`ops().refresh()`。

3. **不改变任何运行时行为**：`ops()` 返回的就是父类字段 `ops`，与子类原来持有的 `ops` 是同一个对象引用，所以语义零变化。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java`

**修改目的**：暴露 `ops()` 访问器。

**工作逻辑**（位于第 159 行附近，在 `self()` 方法之后）：

```java
protected TableOperations ops() {
  return ops;
}
```

`ops` 是 `SnapshotProducer` 已有的 `private final TableOperations ops` 字段（构造时传入）。`ops()` 方法直接返回它。`protected` 范围使子类可访问。

### `core/src/main/java/org/apache/iceberg/BaseRewriteManifests.java`

**修改目的**：删除冗余 `ops` 成员，改用 `ops()`。

**工作逻辑**：
- 删除字段：`private final TableOperations ops;`
- 构造函数：
  ```java
  BaseRewriteManifests(TableOperations ops) {
    super(ops);
-   this.ops = ops;
-   this.specsById = ops.current().specsById();
+   this.specsById = ops().current().specsById();
    this.manifestTargetSizeBytes =
-       ops.current()
+       ops()
+           .current()
            .propertyAsLong(MANIFEST_TARGET_SIZE_BYTES, MANIFEST_TARGET_SIZE_BYTES_DEFAULT);
  }
  ```
- `copyManifest`：`ops.current()` → `ops().current()`，`ops.io()` → `ops().io()`。
- `apply`：`base.currentSnapshot().allManifests(ops.io())` → `ops().io()`。
- `apply` 中读取 manifest：`ManifestFiles.read(manifest, ops.io(), ops.current().specsById())` → `ops().io()` / `ops().current().specsById()`。

### `core/src/main/java/org/apache/iceberg/FastAppend.java`

**修改目的**：删除冗余 `ops` 成员，改用 `ops()`。

**工作逻辑**：
- 删除字段：`private final TableOperations ops;`
- 构造函数：
  ```java
  FastAppend(String tableName, TableOperations ops) {
    super(ops);
    this.tableName = tableName;
-   this.ops = ops;
-   this.spec = ops.current().spec();
+   this.spec = ops().current().spec();
  }
  ```
- `summary()`：`ops.current()` → `ops().current()`。
- `copyManifest`：`ops.current()` / `ops.io()` → `ops().current()` / `ops().io()`。
- `apply`：`snapshot.allManifests(ops.io())` → `ops().io()`。
- `updateEvent`：`ops.current().snapshot(snapshotId)` → `ops().current().snapshot(snapshotId)`。

### `core/src/main/java/org/apache/iceberg/MergingSnapshotProducer.java`

**修改目的**：删除冗余 `ops` 成员，改用 `ops()`；该类改动最多（47 处），因为 `MergingSnapshotProducer` 是 `OverwriteDataFiles`、`DeleteFiles`、`ReplacePartitions`、`RowDelta` 等多个操作的中坚力量，对 `ops` 的引用遍布各类辅助方法。

**工作逻辑**：
- 删除字段：`private final TableOperations ops;`
- 构造函数：删除 `this.ops = ops;`，保留 `super(ops)`。
- 在以下方法中把 `ops.current()` / `ops.io()` / `ops.refresh()` 改为 `ops().current()` / `ops().io()` / `ops().refresh()`：
  - `spec(int specId)`：`ops.current().spec(specId)` → `ops().current().spec(specId)`。
  - `formatVersion()`：`ops.current().formatVersion()` → `ops().current().formatVersion()`。
  - `copyManifest`：`ops.current()` / `ops.io()` → `ops().current()` / `ops().io()`。
  - `apply`：`new ManifestGroup(ops.io(), ...)` → `ops().io()`。
  - `DeleteFileIndex` 相关多个方法：`DeleteFileIndex.builderFor(ops.io(), ...)`、`ops.current().specsById()` → `ops().io()` / `ops().current().specsById()`。
  - `validateAddedDVs`：`ManifestFiles.readDeleteManifest(manifest, ops.io(), ops.current().specsById())` → `ops().io()` / `ops().current().specsById()`。
  - `groupForSnapshot`（数据 manifest 与删除 manifest 收集）：`currentSnapshot.dataManifests(ops.io())` / `currentSnapshot.deleteManifests(ops.io())` → `ops().io()`。
  - `summary()`：`ops.current()` → `ops().current()`。
  - `apply` 中过滤 manifest 与删除 manifest：`snapshot.dataManifests(ops.io())` / `snapshot.deleteManifests(ops.io())` → `ops().io()`。
  - `updateEvent`：`ops.refresh().snapshot(snapshotId)` → `ops().refresh().snapshot(snapshotId)`。
  - `apply` 中新删除 manifest 写入：`ops.current().spec(specId)` → `ops().current().spec(specId)`。
  - 内部类 `DataFileFilterManager`、`DeleteFileFilterManager` 构造与 `spec(int specId)`：`super(ops.current().specsById(), ...)`、`ops.current().spec(specId)` → `ops().current().specsById()` / `ops().current().spec(specId)`。

## 小结

- **成效**：消除 `SnapshotProducer` 子类（`BaseRewriteManifests`、`FastAppend`、`MergingSnapshotProducer`）中冗余的 `TableOperations ops` 成员字段，统一通过父类暴露的 `ops()` 访问器获取，代码更内聚、更易维护，无运行时行为变化。
- **影响范围**：4 个文件、41 处新增、40 处删除；纯重构，无逻辑变更。
- **回迁到 1.4.x 的注意事项**：
  - 这是一个纯重构、无功能变化的清理，回迁价值在于让 1.4.x 与 main 代码结构保持一致，减少未来 cherry-pick 时的冲突。
  - 回迁风险极低：所有改动都是同义替换（`ops` → `ops()`，返回同一对象）。
  - 如果 1.4.x 后续计划从 main cherry-pick 涉及 `SnapshotProducer` 子类的功能修复或改进，预先回迁此重构可减少冲突；否则可作为低优先级清理延后。
  - 需整体回迁 4 个文件，不能只回迁部分，否则 `ops()` 在父类不存在会导致子类编译失败。
