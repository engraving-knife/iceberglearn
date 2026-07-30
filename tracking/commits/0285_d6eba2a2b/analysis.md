# 提交 0285：Core: Fix missing files from transaction retries with conflicting manifest merges (#9230)

## 提交信息

- **序号**：0285 / 4088
- **哈希**：d6eba2a2b6ecf76dc11910d88febca6c4833338d
- **短哈希**：d6eba2a2b
- **日期**：2023-12-18
- **作者**：Jason
- **提交说明**：Core: Fix missing files from transaction retries with conflicting manifest merges (#9230)
- **PR/Issue**：#9230

## 总体目的

Iceberg 的写操作（`AppendFiles`/`RowDelta` 等）在内部通过 `SnapshotProducer` 提交快照。当并发提交冲突时，`SnapshotProducer` 会重试：重新 `apply()` 计算新的快照元数据，再尝试提交。为了不在每次 `apply()` 时都重新写一遍 manifest 文件，写操作会把"由本次操作新生成的 manifest"缓存到字段里（`FastAppend.newManifests`、`MergingSnapshotProducer.cachedNewDataManifests`），下次 `apply()` 命中缓存就跳过重写。这个缓存只在 manifest 内容未变化时有效。

问题出在 `cleanUncommitted(Set<ManifestFile> committed)` 的实现上。当一次 `apply()` 触发了 manifest 合并（merge）时，原本新生成的 manifest（例如由 `newDataFiles`/`newFiles` 写出的 `M_D`）会被合并成一个新的 manifest `M_merged`，最终被提交的只有 `M_merged`，而 `M_D` 不在 committed 集合里。`cleanUncommitted` 会把 `M_D` 从磁盘删除，这是正确的。但旧代码同时把缓存 `cachedNewDataManifests`/`newManifests` 过滤为"只含已提交项"的列表——由于 `M_D` 未提交，过滤后缓存变成了空列表（不是 null）。当下一次提交因并发冲突触发重试、再次调用 `apply()` 时，`prepareNewDataManifests()`/`writeNewManifests()` 看到 `hasNewFiles/hasNewDataFiles == false`（首次 apply 后被置为 false）且缓存非 null（空列表），于是直接返回空缓存，跳过从 `newFiles`/`newDataFiles` 重新生成 manifest 的步骤。结果新数据文件（如 `FILE_D`）从重试的快照里消失了，造成数据丢失。

具体到事务场景，`Transactions.newTransaction(...)` 创建一个事务，事务内的 `append.apply()` + `append.commit()` 会先把待提交状态缓存起来；如果在 `transaction.commitTransaction()` 之前有别的提交抢先改变了表元数据，事务重试时就会命中上述 bug，导致事务里 `appendFile(FILE_D)` 加的文件在最终快照里不存在。本次提交的目标是修复这一数据正确性 bug，确保重试时被合并/清理掉的 manifest 缓存能被正确失效，从而从原始 `newFiles`/`newDataFiles` 重新生成。

## 如何达成设计目的

修复思路是把 `cleanUncommitted` 中"过滤缓存为只含已提交项的列表"改为"只要发生过任何删除，就把整个缓存置为 null"。这样重试时 `writeNewManifests()`/`newDataFilesAsManifests()` 看到 `newManifests == null` / `cachedNewDataManifests == null`，会走"重新写 manifest"分支，从仍然保存着新增文件的 `newFiles`/`newDataFiles` 列表重新生成 manifest，确保重试后的快照包含全部新增文件。改动同时应用到 `FastAppend`（普通 fast append 路径）与 `MergingSnapshotProducer`（带 merge 的 append/overwrite/delete 路径），因为两者有相同的缓存模式与相同的潜在 bug——即使 FastAppend 不做 merge，提交失败重试时 `cleanUncommitted` 也会把全部 `newManifests` 删除，旧逻辑同样会把缓存置为空列表而非 null，导致重试丢失文件。

## 修改详情

### `core/src/main/java/org/apache/iceberg/FastAppend.java`

**修改目的**：修复 `FastAppend.cleanUncommitted` 在删除未提交 manifest 后错误地保留空缓存列表、导致重试时丢失新增文件的问题。

**工作逻辑**：
- 原实现：
  ```java
  List<ManifestFile> committedNewManifests = Lists.newArrayList();
  for (ManifestFile manifest : newManifests) {
      if (committed.contains(manifest)) {
          committedNewManifests.add(manifest);
      } else {
          deleteFile(manifest.path());
      }
  }
  this.newManifests = committedNewManifests;
  ```
  即把缓存过滤为"只含已提交项"的列表，若 `M_D` 被删除则缓存变为空列表（非 null）。
- 新实现：
  ```java
  boolean hasDeletes = false;
  for (ManifestFile manifest : newManifests) {
      if (!committed.contains(manifest)) {
          deleteFile(manifest.path());
          hasDeletes = true;
      }
  }
  if (hasDeletes) {
      this.newManifests = null;
  }
  ```
  只要发生过任何删除，就把 `this.newManifests` 置为 null，强制下次 `writeNewManifests()` 走重写分支。若没有任何删除（即全部 manifest 都已提交），则保持缓存不变，保留原优化。
- 配合 `writeNewManifests()` 的逻辑：
  ```java
  if (hasNewFiles && newManifests != null) {
      newManifests.forEach(file -> deleteFile(file.path()));
      newManifests = null;
  }
  if (newManifests == null && !newFiles.isEmpty()) {
      // 从 newFiles 重新写 manifest
      this.newManifests = writer.toManifestFiles();
      hasNewFiles = false;
  }
  return newManifests;
  ```
  修复后，重试时 `newManifests == null` 且 `newFiles` 非空（新增文件列表在重试间不会清空），于是从 `newFiles` 重新写出 manifest，新增文件不再丢失。`hasNewFiles` 在首次 apply 后是 false，但因为 `newManifests == null`，第二分支仍然会触发——这是修复能生效的关键。

### `core/src/main/java/org/apache/iceberg/MergingSnapshotProducer.java`

**修改目的**：修复 `MergingSnapshotProducer.cleanUncommittedAppends` 中针对 `cachedNewDataManifests` 的相同缓存失效问题。这是测试中通过 `transaction.newAppend()`（MergeAppend）触发的主路径。

**工作逻辑**：
- 原实现与 `FastAppend` 类似：把 `cachedNewDataManifests` 过滤为"只含已提交项"的列表。当 apply 触发 manifest merge 时，原始 `M_D` 被合并为 `M_merged`，`M_D` 不在 committed 集合里被删除，缓存被错误地置为空列表。
- 新实现：
  ```java
  boolean hasDeletes = false;
  for (ManifestFile manifest : cachedNewDataManifests) {
      if (!committed.contains(manifest)) {
          deleteFile(manifest.path());
          hasDeletes = true;
      }
  }
  if (hasDeletes) {
      this.cachedNewDataManifests = null;
  }
  ```
  任何删除都把缓存置 null，强制下次 `newDataFilesAsManifests()` 从 `newDataFiles` 重写。
- 配合 `newDataFilesAsManifests()` 的逻辑：
  ```java
  if (hasNewDataFiles && cachedNewDataManifests != null) {
      cachedNewDataManifests.forEach(file -> deleteFile(file.path()));
      cachedNewDataManifests = null;
  }
  if (cachedNewDataManifests == null) {
      // 从 newDataFiles 重新写 manifest
      this.cachedNewDataManifests = writer.toManifestFiles();
      this.hasNewDataFiles = false;
  }
  return cachedNewDataManifests;
  ```
  修复后重试时 `cachedNewDataManifests == null`，从 `newDataFiles` 重新生成 manifest，merge 重新执行，新增文件不再丢失。
- 注意：紧随其后的 `cachedNewDeleteManifests` 清理逻辑（用 `ListIterator.remove()`）未改动——delete manifests 的缓存清理没有相同的"过滤为空列表"模式，所以不在本次修复范围内。

### `core/src/test/java/org/apache/iceberg/TestTransaction.java`

**修改目的**：新增 `testTransactionRecommit` 回归测试，覆盖事务重试 + manifest merge 场景下文件不丢失。

**工作逻辑**：
- 设置 `MANIFEST_MIN_MERGE_COUNT = 3`，使下一次产生 3 个 manifest 的提交会触发 merge。
- 连续两次 `table.newFastAppend().appendFile(FILE_A/B).commit()`，让表已有 2 个 manifest。
- 开启一个事务 `Transactions.newTransaction(table.name(), table.ops())`，在事务内 `newAppend().appendFile(FILE_D)`（MergeAppend）。
- 调用 `append.apply()` 得到 pending snapshot，断言其只有 1 个 manifest（即 merge 已发生，3 个 manifest 被合并为 1 个 `M_merged`）。
- 调用 `append.commit()` 把 pending 状态暂存到事务（此时 merge 已把 `M_D` 删除）。
- 在事务外并发提交 `table.newAppend().appendFile(FILE_C).commit()`，使表元数据变化，强制事务在最终提交时重试。断言这次并发提交后表也只有 1 个 manifest（同样发生 merge）。
- 调用 `transaction.commitTransaction()`，触发事务重试。
- 断言最终表的扫描结果包含全部 4 个文件 `FILE_A/B/C/D` 的路径——特别是 `FILE_D` 没有在重试中丢失。
- 断言最终表有 2 个 manifest（事务重试后再次 merge 产生 1 个，加上并发提交的 1 个，共 2 个）。
- 如果没有修复，重试时 `cachedNewDataManifests` 是空列表而非 null，`newDataFilesAsManifests()` 返回空，merge 不会包含 `FILE_D`，最终扫描结果会缺少 `FILE_D`，断言失败。

## 小结

本次提交通过把 `FastAppend.cleanUncommitted` 与 `MergingSnapshotProducer.cleanUncommittedAppends` 中"过滤缓存为已提交列表"的行为改为"发生任何删除即将缓存置 null"，修复了事务/提交重试时 manifest 缓存被错误保留为空列表、导致 `writeNewManifests()`/`newDataFilesAsManifests()` 跳过从 `newFiles`/`newDataFiles` 重写 manifest、最终快照丢失新增文件的数据正确性 bug，并通过 `testTransactionRecommit` 在 manifest merge + 并发冲突重试场景下覆盖了修复。
