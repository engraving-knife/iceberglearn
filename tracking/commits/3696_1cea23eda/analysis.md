# 提交 3696：Core: Fix row ID assignment for EXISTING entry during a manifest merge (#16263)

## 提交信息

- **序号**：3696 / 4088
- **哈希**：1cea23eda51c9b9ddcfb88dd499b1fd14f3bf3b3
- **短哈希**：1cea23eda
- **日期**：2026-05-12 14:57:47 -0600
- **作者**：Amogh Jahagirdar
- **提交说明**：Core: Fix row ID assignment for EXISTING entry during a manifest merge (#16263)
- **PR/Issue**：#16263

## 总体目的

这个提交修复了在 manifest 合并过程中，EXISTING 条目的 row ID 分配 bug。Iceberg 的 row lineage 功能通过为每行数据分配唯一的全局 row ID 来跟踪数据血缘。当写入新数据时，新生成的 manifest 会记录每个数据文件的 `first_row_id`。

问题出现在 manifest 合并场景：当一个 manifest 被合并到另一个 manifest 时，其中的 EXISTING 条目（已存在于之前快照中的文件）可能已经在当前快照中获得了 row ID 分配。但在合并过程中，如果这些 EXISTING 条目被当作"未提交"（uncommitted）处理，row ID 分配逻辑会错误地清除它们的 `first_row_id`，导致数据血缘信息丢失。

具体来说，原代码中 `ManifestReader` 的 `idAssigner` 在 `firstRowId` 为 null 时，会将所有条目的 `first_row_id` 设置为 null。但对于未提交 manifest 中的 EXISTING 条目（这些条目会在后续合并中被处理），应该保留其原始的 `first_row_id`，而不是清除它。

## 如何达成设计目的

通过以下修改实现修复：
1. 在 `ManifestReader` 中新增 `isCommitted` 标志，区分已提交和未提交的 manifest
2. 修改 `idAssigner` 逻辑，对未提交 manifest 的条目保留原始 row ID
3. 在 `ManifestMergeManager` 中，根据 manifest 的 snapshotId 判断是否为已提交
4. 在 `ManifestFiles.read` 中添加 `isCommitted` 参数重载

## 修改详情

### `core/src/main/java/org/apache/iceberg/ManifestReader.java` (+18/-3 lines)

**修改目的**：修改 row ID 分配逻辑，区分已提交和未提交 manifest。

**工作逻辑**：

新增 `isCommitted` 字段和构造函数参数。修改 `idAssigner` 方法：

```java
private static <F extends ContentFile<F>> Function<ManifestEntry<F>, ManifestEntry<F>> idAssigner(
    Long firstRowId, boolean isCommitted) {
  if (firstRowId != null) {
    // 正常的 row ID 分配逻辑
    return new Function<>() { ... };
  } else if (!isCommitted) {
    // Preserve firstRowId for entries in uncommitted manifests, including EXISTING entries
    return Function.identity();
  } else {
    // committed manifest with null manifest-level firstRowId (pre-v3 upgrade path)
    // defensively set the first row ID for every entry to be null
    return entry -> { ... };
  }
}
```

关键修复：当 manifest 的 `firstRowId` 为 null 且 manifest 未提交时，使用 `Function.identity()` 保留条目原始的 `first_row_id`，而不是清除它。这样 EXISTING 条目在合并时能保留正确的 row ID。

### `core/src/main/java/org/apache/iceberg/ManifestFiles.java` (+9 lines)

**修改目的**：添加带 `isCommitted` 参数的 read 方法重载。

**工作逻辑**：

新增包级可见的 `read` 方法重载，将 `isCommitted` 参数传递给 `ManifestReader` 构造函数。原有的 public `read` 方法委托给新方法，默认 `isCommitted=true`。

### `core/src/main/java/org/apache/iceberg/ManifestMergeManager.java` (+6/-1 lines)

**修改目的**：在合并时判断 manifest 是否已提交。

**工作逻辑**：

```java
protected ManifestReader<F> newManifestReader(ManifestFile manifest, boolean isCommitted) {
  return newManifestReader(manifest);
}

// 在 merge 方法中：
boolean isCommitted =
    manifest.snapshotId() != null && snapshotId() != manifest.snapshotId();
try (ManifestReader<F> reader = newManifestReader(manifest, isCommitted)) {
```

判断逻辑：如果 manifest 的 snapshotId 不为 null 且不等于当前快照 ID，则该 manifest 来自之前的快照（已提交）；否则是当前快照新写入的 manifest（未提交）。

### `core/src/main/java/org/apache/iceberg/MergingSnapshotProducer.java` (+7/-1 lines)

**修改目的**：覆盖新的 newManifestReader 方法。

**工作逻辑**：

在内部类中覆盖带 `isCommitted` 参数的 `newManifestReader` 方法，调用 `ManifestFiles.read` 并传递 `isCommitted` 参数。

### 测试文件 (+36/+27 lines)

**修改目的**：添加测试验证修复。

## 总结

这是一个重要的数据正确性修复提交，解决了 manifest 合并过程中 EXISTING 条目 row ID 被错误清除的问题。通过引入 `isCommitted` 标志区分已提交和未提交 manifest，确保未提交 manifest 中的条目保留原始 row ID，避免数据血缘信息丢失。这一修复对于 Iceberg row lineage 功能的正确性至关重要。
