# 提交 0096：Core: Reduce unnecessary add operations in deletedPaths set (#8868)

## 提交信息

- **序号**：0096 / 4088
- **哈希**：aa891acf20040d15e7ca59dc503adb3c1e4325b8
- **短哈希**：aa891acf2
- **日期**：2023-10-25
- **作者**：bknbkn
- **提交说明**：Core: Reduce unnecessary add operations in deletedPaths set (#8868)
- **PR/Issue**：#8868

## 总体目的

本提交针对 `ManifestFilterManager` 在执行 manifest 过滤时的一处冗余操作进行优化。

在 Iceberg 的写入/删除流程中，`ManifestFilterManager` 负责遍历 manifest 中的文件条目，根据删除条件（按路径、按分区或按 sequence number）判断哪些文件需要被标记为 DELETED。当某个文件被判定为删除时，会将其路径包装为 `CharSequenceWrapper` 加入 `deletedPaths` 集合，用于后续去重判断——若同一路径在多个 manifest 中重复出现，则计入 `duplicateDeleteCount` 而非重复加入 `deletedFiles`，从而保证 snapshot summary 中删除文件数的准确性。

优化前，`deletedPaths.add(wrapper)` 这一行位于 `if/else` 块之外，即无论该路径是否已在集合中，都会执行一次 `add` 操作。对于已在集合中的重复路径，`HashSet.add` 是一次无意义的哈希计算与比较——它返回 false 且不改变集合状态。虽然单次开销很小，但在 manifest 条目数量巨大、且重复删除比例较高的场景下（例如跨多个 manifest 的分区删除），这些冗余 `add` 调用累积起来仍是不必要的 CPU 开销。

本提交将 `deletedPaths.add(wrapper)` 移入 `else` 分支（即仅在新删除、非重复路径时执行），从而消除重复路径上的冗余 `add` 调用。这是一处典型的"减少不必要操作"的微优化，逻辑等价，行为不变，但减少了热路径上的无效工作。

## 如何达成设计目的

改动极其精简——仅 1 行位置的调整。整体思路是利用已有的分支结构：代码已经通过 `deletedPaths.contains(wrapper)` 区分了"重复路径"与"新路径"两种情况。既然重复路径已经存在于集合中，对其再调用 `add` 必然无效；因此只需把 `add` 调用从 `if/else` 之后的公共位置，下沉到 `else`（新路径）分支内部即可。这样重复路径走 `if` 分支只做计数告警，新路径走 `else` 分支同时加入 `deletedFiles` 与 `deletedPaths`，语义与之前完全一致。

## 修改详情

### `core/src/main/java/org/apache/iceberg/ManifestFilterManager.java`

**修改目的**：消除对 `deletedPaths` 集合的冗余 `add` 调用，使重复路径不再触发无效的集合写入。

**工作逻辑**：

修改位于 `ManifestFilterManager` 内 manifest 过滤循环中（约第 450 行附近）。被过滤条目在满足删除条件且 `allRowsMatch` 时，进入如下处理块：

修改前（简化）：

```java
CharSequenceWrapper wrapper = CharSequenceWrapper.wrap(entry.file().path());
if (deletedPaths.contains(wrapper)) {
    LOG.warn("Deleting a duplicate path from manifest {}: {}", manifest.path(), wrapper.get());
    duplicateDeleteCount += 1;
} else {
    // only add the file to deletes if it is a new delete
    deletedFiles.add(entry.file().copyWithoutStats());
}
deletedPaths.add(wrapper);   // 无条件执行，重复路径上为无效操作
```

修改后：

```java
CharSequenceWrapper wrapper = CharSequenceWrapper.wrap(entry.file().path());
if (deletedPaths.contains(wrapper)) {
    LOG.warn("Deleting a duplicate path from manifest {}: {}", manifest.path(), wrapper.get());
    duplicateDeleteCount += 1;
} else {
    // only add the file to deletes if it is a new delete
    deletedFiles.add(entry.file().copyWithoutStats());
    deletedPaths.add(wrapper);   // 仅对新路径执行
}
```

关键点：
- 语义保持不变。重复路径在修改前后都不会被重复加入 `deletedFiles`（由 `contains` 检查保证），`deletedPaths` 在修改前后最终状态也一致（`HashSet` 对已存在元素的 `add` 是幂等的）。
- 唯一变化是省去了重复路径上那次必然返回 false 的 `HashSet.add` 调用，避免了重复哈希计算。
- 该优化对 manifest 过滤这种高频遍历场景具有实际意义，尤其是当多个 manifest 引用相同数据文件路径时。

## 小结

本提交通过将 `deletedPaths.add` 下沉到非重复分支，消除了 manifest 过滤热路径中对已存在路径的冗余集合写入，是 Iceberg 写入路径上一处精确且行为等价的微优化。
