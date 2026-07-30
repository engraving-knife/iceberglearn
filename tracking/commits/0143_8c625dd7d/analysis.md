# 提交 0143：Core: Support replacing delete manifests (#9000)

## 提交信息

- **序号**：0143 / 4088
- **哈希**：8c625dd7d21e38235d2864e081c008ef11e0fd20
- **短哈希**：8c625dd7d
- **日期**：2023-11-09 14:54:32 -0800
- **作者**：Anton Okolnychyi
- **提交说明**：Core: Support replacing delete manifests (#9000)
- **PR/Issue**：#9000

## 总体目的

这个提交为 Iceberg 核心模块的 `rewriteManifests` 操作扩展了一项重要能力：支持替换"删除清单"（delete manifests），而不再只支持替换"数据清单"（data manifests）。在 Iceberg V2 表中，一个快照的 manifest list 同时包含数据清单（content=DATA，记录数据文件）和删除清单（content=DELETES，记录 equality/position delete 文件）。在此提交之前，`BaseRewriteManifests` 的 `apply()` 方法只处理数据清单：它从当前快照取 `dataManifests()`，对它们执行重写或保留，最后在结果列表末尾把"原封不动的删除清单"追加回去（`apply.addAll(base.currentSnapshot().deleteManifests(ops.io()))`）。这意味着用户无法通过 `deleteManifest()`/`addManifest()` API 来替换删除清单——即使调用了这些 API 指向一个删除清单，`apply()` 也只会在数据清单集合里校验和替换它，删除清单总是被原样保留。

本提交把这一限制解除。`apply()` 现在从当前快照取 `allManifests()`（数据 + 删除清单的并集）作为处理对象，移除了"末尾追加原删除清单"的逻辑，使得删除清单也进入统一的 keep/rewrite 流程。`performRewrite()` 中对每个 manifest 的判定从"仅看 predicate"改为"先看是否为删除清单（若是则原样保留，因为 clusterBy 重写只适用于数据文件），再看 predicate"。这样，用户就可以用 `deleteManifest(originalDeleteManifest).addManifest(newDeleteManifest)` 的方式手动替换删除清单，与替换数据清单的用法完全对称。

这个能力对 Iceberg 元数据管理很重要：随着表持续累积 delete 文件，删除清单也会膨胀或碎片化，需要像数据清单一样能被合并/拆分/替换以维持读取性能。此前只能任由删除清单增长，本提交补齐了这一缺口。

## 如何达成设计目的

整体设计思路是把"数据清单"与"删除清单"纳入统一的处理管线，而不是分别硬编码处理。具体改动分布在三处：

1. 在 `BaseRewriteManifests.apply()` 中把"当前 manifest 集合"的来源从 `dataManifests()` 改为 `allManifests()`，并移除末尾盲追加删除清单的代码，使删除清单也成为可被 `deleteManifest()`/`keepActiveManifests()`/`validateDeletedManifests()` 统一处理的对象。
2. 在 `performRewrite()` 中调整每个 manifest 的 keep/rewrite 判定：新增 `containsDeletes(manifest)` 检查（删除清单因不支持 clusterBy 重写而原样保留），并把谓词判定抽取为 `matchesPredicate(manifest)`，使逻辑清晰。
3. 在测试基础设施中把 `TableTestBase.manifestEntry` 泛型化（从只接受 `DataFile` 改为接受任何 `ContentFile<F>`），使其能构造删除清单条目；并在 `TestRewriteManifests` 中新增约 600 行测试，覆盖保留删除清单、仅替换删除清单、同时替换数据与删除清单、并发追加、并发删除文件移除、冲突场景、失败回滚等。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseRewriteManifests.java`

**修改目的**：让 `rewriteManifests` 操作能统一处理数据清单与删除清单，支持用户通过 API 替换删除清单。

**工作逻辑**：

1. **`apply()` 方法的 manifest 来源变更**：

   ```java
   // 修改前
   List<ManifestFile> currentManifests = base.currentSnapshot().dataManifests(ops.io());
   // 修改后
   List<ManifestFile> currentManifests = base.currentSnapshot().allManifests(ops.io());
   ```

   这使 `validateDeletedManifests()`、`requiresRewrite()`、`performRewrite()`/`keepActiveManifests()` 都同时看到数据清单与删除清单。`deleteManifest(originalDeleteManifest)` 调用所标记的删除清单，现在能被 `validateDeletedManifests` 在 `currentManifestSet` 中找到并校验通过。

2. **移除末尾盲追加删除清单**：

   ```java
   // 修改前
   Iterables.addAll(apply, newManifestsWithMetadata);
   apply.addAll(keptManifests);
   apply.addAll(base.currentSnapshot().deleteManifests(ops.io()));  // <-- 移除
   // 修改后
   Iterables.addAll(apply, newManifestsWithMetadata);
   apply.addAll(keptManifests);
   ```

   原来删除清单被无条件追加回去，会绕过 keep/rewrite 决策；现在删除清单要么作为 `keptManifests` 保留（未被 deleteManifest 标记且未被重写），要么被 `deleteManifest` 替换（从 keptManifests 中排除，由 addManifest 提供新清单）。这一行移除是功能扩展的关键。

3. **`performRewrite()` 中 keep/rewrite 判定变更**：

   ```java
   // 修改前
   if (predicate != null && !predicate.test(manifest)) {
     keptManifests.add(manifest);
   } else {
     rewrittenManifests.add(manifest);
     // ... 读取 data manifest 条目并按 clusterBy 重写
   }
   // 修改后
   if (containsDeletes(manifest) || !matchesPredicate(manifest)) {
     keptManifests.add(manifest);
   } else {
     rewrittenManifests.add(manifest);
     // ... 读取 data manifest 条目并按 clusterBy 重写
   }
   ```

   新增 `containsDeletes(manifest)` 短路检查：删除清单（`manifest.content() == ManifestContent.DELETES`）被直接加入 `keptManifests`，不会尝试用 `ManifestReader<DataFile>` 读取（那会出错，因为删除清单的条目类型是 `DeleteFile`）。这保证 clusterBy 重写仍只作用于数据清单，而删除清单在 clusterBy 路径下被原样保留。同时把谓词判定抽取为 `matchesPredicate(manifest)`，语义为 `predicate == null || predicate.test(manifest)`，与原逻辑等价但更清晰。

4. **新增两个私有方法**：

   ```java
   private boolean containsDeletes(ManifestFile manifest) {
     return manifest.content() == ManifestContent.DELETES;
   }

   private boolean matchesPredicate(ManifestFile manifest) {
     return predicate == null || predicate.test(manifest);
   }
   ```

   这两个方法把判定逻辑命名化，便于阅读与复用。

### `core/src/test/java/org/apache/iceberg/TableTestBase.java`

**修改目的**：把 `manifestEntry` 辅助方法泛型化，使其既能构造数据清单条目也能构造删除清单条目。

**工作逻辑**：原方法签名 `ManifestEntry<DataFile> manifestEntry(ManifestEntry.Status status, Long snapshotId, DataFile file)` 及其重载改为 `<F extends ContentFile<F>> ManifestEntry<F> manifestEntry(..., F file)`，内部 `GenericManifestEntry<DataFile>` 改为 `GenericManifestEntry<F>`。由于 `DataFile` 和 `DeleteFile` 都满足 `ContentFile<F>` 约束，测试代码现在可以用同一个辅助方法构造删除清单条目（传入 `FILE_A_DELETES` 等 `DeleteFile` 常量），而不必复制一份几乎相同的方法。这是支撑后续 `TestRewriteManifests` 新增删除清单测试的基础设施改动。

### `core/src/test/java/org/apache/iceberg/TestRewriteManifests.java`

**修改目的**：为"替换删除清单"这一新能力新增全面测试，覆盖正常替换、并发场景、冲突场景与失败回滚。

**工作逻辑**：新增以下测试方法与辅助方法（约 600 行新增）：

1. **`testRewriteDataManifestsPreservesDeletes`**：验证 clusterBy 重写数据清单时，删除清单被原样保留。先 append 数据文件、再 addDeletes 提交删除文件，然后 `rewriteManifests().clusterBy(file -> file.path().toString())`。断言数据清单被重写为 2 个（按文件路径聚簇），删除清单仍是原来的 1 个，且删除清单内的条目元信息（snapshot id、sequence number、文件、状态）不变。

2. **`testReplaceDeleteManifestsOnly`**：验证仅替换删除清单、保留数据清单。手动写出 2 个新删除清单（每个含 1 个 EXISTING 状态的 delete 文件），通过 `rewriteManifests().deleteManifest(originalDeleteManifest).addManifest(new1).addManifest(new2)` 提交。断言数据清单不变，删除清单被替换为 2 个，条目元信息正确。

3. **`testReplaceDataAndDeleteManifests`**：验证同时替换数据清单与删除清单。手动写出 2 个新数据清单 + 2 个新删除清单，通过链式 `deleteManifest`/`addManifest` 替换原数据清单与原删除清单。断言数据清单被替换为 2 个、删除清单被替换为 2 个，条目元信息正确。

4. **`testDeleteManifestReplacementConcurrentAppend`**：验证替换删除清单期间发生并发 append 时不冲突。开始 rewrite（替换删除清单），在 commit 前并发 `newFastAppend().appendFile(FILE_C).appendFile(FILE_D)`，然后 commit rewrite。断言 rewrite 成功，并发 append 的数据清单与替换后的删除清单共存。

5. **`testDeleteManifestReplacementConcurrentDeleteFileRemoval`**：验证替换删除清单期间发生并发删除文件移除（`newRewrite().deleteFile(FILE_B_DELETES)`）时不冲突。两次提交不同的删除文件集，开始替换第一个删除清单，并发 `newRewrite().deleteFile(FILE_B_DELETES)` 移除第二个删除清单中的文件，commit rewrite。断言 rewrite 成功，第一个删除清单被替换为 2 个，第二个删除清单被并发修改（FILE_B_DELETES 标记 DELETED）。

6. **`testDeleteManifestReplacementConflictingDeleteFileRemoval`**：验证冲突场景。开始替换某个删除清单，并发 `newRewrite().deleteFile(FILE_A_DELETES)` 修改同一个删除清单，commit rewrite 应失败并抛 `ValidationException`（"Manifest is missing"），因为被替换的删除清单已不在当前快照中。

7. **`testDeleteManifestReplacementFailure`**：验证 commit 失败时不删除新写入的 manifest。配置 `ops().failCommits(5)` 注入提交失败，执行替换删除清单，断言抛 `CommitFailedException`，且新写的删除清单文件仍存在于磁盘（未被清理）。

8. **辅助方法 `assertManifestCounts`** 与 **`sortedDataManifests`**：分别用于断言数据/删除清单数量与对数据清单按路径排序（使断言稳定）。

## 小结

本提交通过把 `BaseRewriteManifests.apply()` 的处理对象从 `dataManifests` 扩展为 `allManifests`、移除盲追加删除清单、在重写流程中识别并保留删除清单，使 `rewriteManifests` 操作支持替换删除清单，补齐了 Iceberg V2 表元数据优化中删除清单不可替换的关键缺口。
