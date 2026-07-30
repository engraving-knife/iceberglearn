# 提交 1700 7a8db16c4 分析

## 提交信息
- 哈希：7a8db16c41cc050c175e08509d81de1acb1eb1f2
- 日期：2025-02-07 17:07:31 -0800
- 作者：barronfuentes
- 消息：Core: Fix RewriteTablePath Incremental Replication (#12172)

## 总体目的

本提交修复 `RewriteTablePathUtil.rewriteManifestList` 在"增量复制"场景下的一个 bug：当使用 `startVersion` 进行增量重写时，目标表的 manifest list 应该包含快照中所有 manifest（既包括本次需要重写路径的，也包括已经在前一次增量中重写过、本次无需再重写的），但原代码只把"需要重写"的 manifest 写入新的 manifest list，导致增量复制后目标表的 manifest list 缺失已存在 manifest 的引用，破坏了快照完整性，查询时丢失数据。

具体场景：用户首次全量复制表后，源表又新增了一个快照（append 一些数据）。用户对源表执行增量 `rewriteTablePath`，指定 `startVersion` 为上次复制时目标表的 metadata 版本。源表新快照的 manifest list 同时引用了"旧 manifest"（路径已在前一次复制时改写为目标位置）和"新 manifest"（路径仍是源位置，本次需要重写）。原代码只处理新 manifest，把旧 manifest 从 manifest list 中丢弃，导致目标表新快照查询时找不到旧 manifest，数据丢失。

修复后：所有 manifest 都写入新的 manifest list（路径已改写为目标位置），但只有"需要重写"的 manifest 才加入 `toRewrite` 和 `copyPlan`，避免重复复制已存在的文件。

## 如何达成设计目的

核心改动是把"过滤 manifest 列表"与"决定哪些 manifest 需要物理复制"两个职责分离：
- 原代码：先用 `manifestsToRewrite.contains(mf.path())` 过滤出 `manifestFilesToRewrite` 子集，然后只对这个子集做路径改写、加入 manifest list、加入 toRewrite/copyPlan。
- 新代码：对所有 manifest 做路径改写并加入 manifest list（保证完整性），但仅对 `manifestsToRewrite.contains(file.path())` 为 true 的 manifest 加入 toRewrite/copyPlan（避免重复复制）。

这样 manifest list 始终包含快照的全部 manifest（路径改写后），而物理文件复制只针对真正需要重写的 manifest。

### 修改详情

#### core/src/main/java/org/apache/iceberg/RewriteTablePathUtil.java
共 15 行变更（9 删 6 增，净减 3 行，但逻辑更完整）。

1. 移除 `import java.util.stream.Collectors;`（不再需要流式收集）。

2. `rewriteManifestList` 方法（约 225 行）：
   - 原代码：
     ```java
     List<ManifestFile> manifestFiles = manifestFilesInSnapshot(io, snapshot);
     List<ManifestFile> manifestFilesToRewrite =
         manifestFiles.stream()
             .filter(mf -> manifestsToRewrite.contains(mf.path()))
             .collect(Collectors.toList());
     manifestFilesToRewrite.forEach(
         mf -> Preconditions.checkArgument(mf.path().startsWith(sourcePrefix), ...));
     ```
   - 新代码：
     ```java
     List<ManifestFile> manifestFiles = manifestFilesInSnapshot(io, snapshot);
     manifestFiles.forEach(
         mf -> Preconditions.checkArgument(mf.path().startsWith(sourcePrefix), ...));
     ```
   - 改动点：删除 `manifestFilesToRewrite` 子集的构造，`Preconditions.checkArgument` 直接对全部 `manifestFiles` 执行。注意：这意味着即便某个 manifest 不在 `manifestsToRewrite` 中，也会校验其路径以 sourcePrefix 开头——这在增量场景下是必要的，因为旧 manifest 的路径在源表中仍然以 sourcePrefix 开头（前一次复制只改写了目标表的 manifest list，源表本身的 manifest 路径未变）。

3. manifest list 写入循环（约 245 行）：
   - 原代码：
     ```java
     for (ManifestFile file : manifestFilesToRewrite) {
       ManifestFile newFile = file.copy();
       ((StructLike) newFile).set(0, newPath(newFile.path(), sourcePrefix, targetPrefix));
       writer.add(newFile);
       result.toRewrite().add(file);
       result.copyPlan().add(Pair.of(stagingPath(file.path(), stagingDir), newFile.path()));
     }
     ```
   - 新代码：
     ```java
     for (ManifestFile file : manifestFiles) {
       ManifestFile newFile = file.copy();
       ((StructLike) newFile).set(0, newPath(newFile.path(), sourcePrefix, targetPrefix));
       writer.add(newFile);

       if (manifestsToRewrite.contains(file.path())) {
         result.toRewrite().add(file);
         result.copyPlan().add(Pair.of(stagingPath(file.path(), stagingDir), newFile.path()));
       }
     }
     ```
   - 改动点：循环从遍历 `manifestFilesToRewrite` 改为遍历全部 `manifestFiles`；`writer.add(newFile)` 对所有 manifest 执行（保证 manifest list 完整）；`toRewrite`/`copyPlan` 的添加包裹在 `if (manifestsToRewrite.contains(file.path()))` 中，只对真正需要重写的 manifest 执行。

注：`((StructLike) newFile).set(0, newPath(...))` 这行对 manifest 的第一个字段（manifest path）做原地替换，是路径改写的核心。对所有 manifest 都执行此改写，确保目标 manifest list 中所有 manifest 路径都指向 targetPrefix。

#### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteTablePathsAction.java
共 50 行变更（全部新增），新增测试 `testIncrementalRewrite` 与辅助方法 `rowsSorted`。

`testIncrementalRewrite` 测试流程：
1. 创建源表，写入第一批数据 recordsA（1 行），断言源表有 1 行。
2. 第一次全量复制：对源表执行 `rewriteTablePath`（无 startVersion），`copyTableFiles(result)`，断言目标表有 1 行。
3. 写入第二批数据 recordsB（1 行）到源表，断言源表有 2 行。
4. 第二次增量复制：
   - `sourceTable.refresh()` 刷新源表元数据。
   - 加载目标表 `TABLES.load(targetTableLocation())`，取其当前 metadata 文件名作为 `startVersion`。
   - 对源表执行 `rewriteTablePath` 并 `.startVersion(startVersion)`，得到 `incrementalRewriteResult`。
   - `copyTableFiles(incrementalRewriteResult)`。
5. 断言：`rowsSorted(targetTableLocation(), "c1")` 与 `rowsSorted(location, "c1")` 相等，即目标表数据与源表一致（2 行，按 c1 排序后比较）。

`rowsSorted(String location, String sortCol)` 辅助方法：读取指定 location 的 iceberg 表，按 sortCol 排序后转为 `List<Object[]>`，用于稳定比较两表数据。

## 小结

本次修复正确区分了"manifest list 的逻辑完整性"（必须包含快照全部 manifest）与"物理文件复制"（只复制需要重写的 manifest），修复了增量复制场景下目标表数据丢失的严重 bug。新增测试 `testIncrementalRewrite` 端到端覆盖了"全量复制 + 增量复制"的完整流程。

回迁到 1.4.x 的注意事项：
1. 核心修改在 `RewriteTablePathUtil.rewriteManifestList`，逻辑改动较小且局部，回迁风险低。需确认 1.4.x 的 `rewriteManifestList` 方法结构与 main 一致。
2. 该修复与 1697（exclude deleted content file）同属 RewriteTablePathUtil 修复系列，且都改动了 `rewriteManifestList` 附近代码，回迁时注意合并顺序与冲突。建议先回迁 1697 再回迁 1700，或合并为一次回迁。
3. `Preconditions.checkArgument` 从只对 `manifestFilesToRewrite` 校验改为对全部 `manifestFiles` 校验，在增量场景下旧 manifest 路径仍以 sourcePrefix 开头所以校验通过；但若 1.4.x 的增量语义不同（例如 startVersion 指向目标表而非源表），需重新评估此校验是否会误报。
4. 测试 `testIncrementalRewrite` 依赖 `startVersion` API 与 `copyTableFiles`、`currentMetadata`、`fileName` 等辅助方法，回迁测试时需确认这些在 1.4.x 中存在。
5. 该 bug 影响所有使用增量 rewriteTablePath 的用户，建议优先回迁。
