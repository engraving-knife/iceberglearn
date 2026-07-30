# 提交 0294：Core: Fix missing delete files from transaction (#9354)

## 提交信息

- **序号**：0294 / 4088
- **哈希**：c340915b054b8b1ae1e03a2a5f0214a0d8853c7a
- **短哈希**：c340915b0
- **日期**：2023-12-21 08:27:39 +0100
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：Core: Fix missing delete files from transaction (#9354)
- **PR/Issue**：#9354

## 总体目的

本提交修复 `MergingSnapshotProducer.cleanUncommittedAppends` 方法在清理未提交的 delete manifest 缓存时存在的一个数据丢失缺陷——在提交失败重试场景下，部分 delete 文件会从事务中"消失"。要理解这个缺陷，需要先看清 `cachedNewDeleteManifests` 缓存的生命周期及其与 `newDeleteFilesAsManifests()` 重建逻辑的耦合关系。

`MergingSnapshotProducer` 是 Iceberg 中所有"合并型"快照操作（overwrite、delete、merge 等）的基类。它在一次事务中维护两类新写入的 manifest 缓存：`cachedNewDataManifests`（数据文件 manifest）与 `cachedNewDeleteManifests`（删除文件 manifest，一个 `LinkedList`）。缓存由 `newDeleteFilesAsManifests()` 构建：当 `hasNewDeleteFiles` 为真且缓存非空时，先删掉旧缓存文件再清空；当缓存为空时，从 `newDeleteFilesBySpec`（待写入的 delete 文件）重新生成 manifest 并填入缓存。关键在于**重建的触发条件是 `cachedNewDeleteManifests.isEmpty()`**——只有缓存完全为空时才会从原始 delete 文件重写 manifest。

缺陷出在提交失败后的清理逻辑。`cleanUncommittedAppends(Set<ManifestFile> committed)` 负责在提交（无论成功失败）后删除未被提交的缓存 manifest 文件。原来的 delete manifest 清理代码用 `ListIterator.remove()` **逐个移除**未提交的 manifest，但保留已提交的那些在缓存列表里。考虑这样一个重试场景：第一次 `apply()` 生成了缓存 `[M1, M2]`，合并管理器把 M2 合并掉、M1 原样透传，于是 `committed` 包含 M1 但不含 M2；提交失败触发清理，原代码删除 M2 文件并从缓存移除 M2，但**保留了 M1**；随后重试再次调用 `apply()` → `newDeleteFilesAsManifests()`，此时 `hasNewDeleteFiles` 已在首次构建后被置为 false，而 `cachedNewDeleteManifests` 非空（还剩 M1），于是既不进入"删旧重建"分支也不进入"空缓存重建"分支，直接返回 `[M1]`。结果是：原本写在 M2 里的 delete 文件既没有文件（M2 已被删）、也没有被重写到新 manifest（缓存未重建），**这些 delete 文件就从事务里丢失了**。这正是 PR 标题"missing delete files from transaction"所指的症状。

对比数据 manifest 的清理逻辑可以发现 delete manifest 的写法是异常的：数据 manifest 那段在检测到任何未提交项被删后，直接 `this.cachedNewDataManifests = null`（置空整个缓存），从而在重试时强制 `newDataFilesAsManifests()` 走重建分支、把所有数据文件重新写成 manifest。delete manifest 却采用了"逐个移除"的半更新策略，与重建条件（要求整表为空）不匹配，留下了"半清空"的危险中间态。本提交的目的就是把 delete manifest 的清理对齐到数据 manifest 的模式：只要有任何未提交项被删除，就清空整个缓存，强制下次重试时完整重建，杜绝 delete 文件丢失。

## 如何达成设计目的

修复思路是**让 delete manifest 的清理行为与数据 manifest 完全对齐**：遍历缓存找出未提交项并删除其文件，用一个布尔标志记录是否发生过删除；若发生过，则 `cachedNewDeleteManifests.clear()` 清空整个列表，而不是逐个 `remove`。这样在重试时 `newDeleteFilesAsManifests()` 会看到空缓存，进入重建分支，从 `newDeleteFilesBySpec` 重新写出全部 delete manifest——包括上次被"透传保留"的那些——确保没有任何 delete 文件丢失。同时移除不再使用的 `ListIterator` 导入，并为方法补上 `@SuppressWarnings("checkstyle:CyclomaticComplexity")` 以容纳因新增分支带来的圈复杂度。

## 修改详情

### `core/src/main/java/org/apache/iceberg/MergingSnapshotProducer.java`

**修改目的**：修复 `cleanUncommittedAppends` 中 delete manifest 缓存的半清空导致重试时丢失 delete 文件的缺陷，使其行为与数据 manifest 清理逻辑一致。

**工作逻辑**：

1. **移除 `ListIterator` 导入**

```java
-import java.util.ListIterator;
```

原代码用 `ListIterator` 遍历并逐个 `remove`，新代码改用增强 for 循环 + 整表 `clear()`，不再需要该导入。

2. **新增 `@SuppressWarnings("checkstyle:CyclomaticComplexity")` 注解**

```java
+  @SuppressWarnings("checkstyle:CyclomaticComplexity")
   private void cleanUncommittedAppends(Set<ManifestFile> committed) {
```

方法内已有数据 manifest、delete manifest、rewrittenAppendManifests、appendManifests 多段清理分支，本身圈复杂度就接近上限；本次把 delete 段从 while+ListIterator 改为 for+if+if，分支数略增，预先抑制 checkstyle 以保持构建通过。

3. **重写 delete manifest 清理段（核心修复）**

原代码：

```java
ListIterator<ManifestFile> deleteManifestsIterator = cachedNewDeleteManifests.listIterator();
while (deleteManifestsIterator.hasNext()) {
  ManifestFile deleteManifest = deleteManifestsIterator.next();
  if (!committed.contains(deleteManifest)) {
    deleteFile(deleteManifest.path());
    deleteManifestsIterator.remove();
  }
}
```

新代码：

```java
boolean hasDeleteDeletes = false;
for (ManifestFile cachedNewDeleteManifest : cachedNewDeleteManifests) {
  if (!committed.contains(cachedNewDeleteManifest)) {
    deleteFile(cachedNewDeleteManifest.path());
    hasDeleteDeletes = true;
  }
}

if (hasDeleteDeletes) {
  this.cachedNewDeleteManifests.clear();
}
```

逐点分析改动原理：

- **遍历方式**：从 `ListIterator` 改为增强 for 循环。原写法需要在遍历中 `remove`，故必须用 `ListIterator`；新写法不在遍历中修改列表，改为遍历后统一 `clear()`，因此普通 for 循环即可。

- **删除策略**：原代码对未提交项 `deleteFile(path)` 后立即 `deleteManifestsIterator.remove()`，只移除该项、保留已提交项；新代码同样对未提交项 `deleteFile(path)`，但用 `hasDeleteDeletes` 标志记录"是否发生过删除"，遍历结束后若标志为真则 `cachedNewDeleteManifests.clear()` 清空**整个列表**。这是修复的核心：清空整表后，重试时 `newDeleteFilesAsManifests()` 的 `cachedNewDeleteManifests.isEmpty()` 条件为真，进入重建分支，从 `newDeleteFilesBySpec` 重新写出全部 delete manifest，保证那些原先被"透传保留"的 manifest 对应的 delete 文件也被重新写入，不再丢失。

- **与数据 manifest 段的对齐**：新代码与同方法内数据 manifest 清理段完全同构——数据段用 `hasDeletes` 标志 + `this.cachedNewDataManifests = null`，delete 段用 `hasDeleteDeletes` 标志 + `this.cachedNewDeleteManifests.clear()`。两者语义一致：只要发生过未提交清理，就使缓存"失效"，强制下次重建。这种对称性既消除了原有缺陷，也降低了后续维护时因两段逻辑不一致而再次出错的风险。

- **无删除时的保留语义**：当 `hasDeleteDeletes` 为 false（所有缓存 delete manifest 都已提交）时，不清空缓存，保持原有行为——这对应提交成功且全部缓存 manifest 都被透传提交的场景，此时无需重建，保留缓存可避免不必要的重写。

## 小结

本提交修复了一个在提交失败重试时可能导致 delete 文件丢失的严重缺陷。根因是 `cleanUncommittedAppends` 对 delete manifest 缓存采用了"逐个移除未提交项"的半清空策略，与 `newDeleteFilesAsManifests()` 要求"整表为空才重建"的条件不匹配，使得重试时缓存处于非空但内容不完整的中间态，跳过了重建，丢失了被删 manifest 中的 delete 文件。修复手法是把 delete 段对齐到数据段已验证正确的模式——检测到任何未提交删除就清空整表，强制下次完整重建。改动小而精准，与既有数据 manifest 逻辑形成对称，是一次高质量的 bug 修复。
