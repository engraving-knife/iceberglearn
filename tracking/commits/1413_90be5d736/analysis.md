# 提交 1413：Core: Optimize MergingSnapshotProducer to use referenced manifests to determine if manifest needs to be rewritten (#11131)

## 提交信息

- **序号**：1413 / 4088
- **哈希**：90be5d7360bc7ff274e7d00cb7259afbf78f223b
- **短哈希**：90be5d736
- **日期**：2024-11-21（Thu Nov 21 08:23:53 2024 -0700）
- **作者**：Amogh Jahagirdar <amoghj@apache.org>
- **提交说明**：Core: Optimize MergingSnapshotProducer to use referenced manifests to determine if manifest needs to be rewritten (#11131)
- **PR/Issue**：#11131

## 总体目的

在 Iceberg 的 `MergingSnapshotProducer`（负责 `OverwriteFiles`、`RowDelta` 等需要合并快照的操作）流程中，需要决定当前快照的每个 manifest 是否需要被重写（即读取后过滤掉已删除文件再写回）。原有实现只能基于分区统计信息、表达式匹配、删除文件分区集合等"启发式"判断 manifest 是否"可能包含"被删除的文件，无法精确判断某个 manifest 是否真的命中删除操作。这导致在大规模表中执行部分文件替换（如 `ReplaceDeleteFiles`）时，即使大部分 manifest 与本次删除无关，仍可能被读取并扫描，造成不必要的 I/O 与 CPU 开销。

本提交利用 `ContentFile.manifestLocation()`（manifest 列表中被删除文件所引用的 manifest 路径）这一信息，构建"被引用的 manifest 集合"作为可信判定来源：当所有删除操作均通过具体的 `DeleteFile` 对象进行（而非通过表达式、分区或路径删除）时，可以直接根据"该 manifest 是否在被引用集合中"来决定是否需要扫描重写，从而跳过与本次提交无关的 manifest，大幅减少开销。

## 如何达成设计目的

核心思路是：当用户通过 `rowDelta.removeDeletes(deleteFile)` 等以具体 `ContentFile` 对象为入参的 API 删除文件时，这些 `ContentFile` 对象在 Iceberg 内部会携带其所属 manifest 的 `manifestLocation`。`ManifestFilterManager` 在收集这些删除文件时，同步记录它们所属的 manifest 路径到 `manifestsWithDeletes` 集合，并将 `allDeletesReferenceManifests` 标志保持为 `true`。一旦发生基于表达式、分区或路径的删除（无法精确归因到具体 manifest），则把该标志置为 `false`，回退到原有启发式判断。

在过滤阶段，新增 `canTrustManifestReferences` 判定：当 `allDeletesReferenceManifests == true` 且当前所有 manifest 路径包含 `manifestsWithDeletes` 集合时（即被引用的 manifest 仍在当前 manifest 列表中），即可信任引用集合。此时 `canContainDeletedFiles` 直接以 `manifestsWithDeletes.contains(manifest.path())` 给出确定答案，无需再做分区统计或表达式评估；`manifestHasDeletedFiles` 也可直接返回 `true`，无需扫描 manifest 内容判断。

该优化同时保留了原有回退路径，保证在不可信任时行为与之前完全一致。

## 修改详情

### `core/src/main/java/org/apache/iceberg/ManifestFilterManager.java`

**修改目的**：在 manifest 过滤管理器中引入"被引用 manifest"集合与可信任标志，优化 manifest 是否需要重写的判定。

**工作逻辑**：

- 新增字段：
  - `Set<String> manifestsWithDeletes`：记录所有被删除文件所引用的 manifest 路径。
  - `boolean allDeletesReferenceManifests`：标志当前所有删除是否都能精确归因到具体 manifest。初始为 `true`。

- 修改各删除入口：
  - `delete(F file)`：若 `file.manifestLocation()` 非 null，则加入 `manifestsWithDeletes`；否则（路径为 null，说明文件未关联 manifest）将 `allDeletesReferenceManifests` 置为 `false`。
  - `delete(Expression expr)`、`dropPartition(...)`、`delete(CharSequence path)`：这些方式无法精确归因到 manifest，一律将 `allDeletesReferenceManifests` 置为 `false`。

- 修改 `filterManifests`：在并行过滤前调用 `canTrustManifestReferences(manifests)` 求出本次是否可信任引用集合，并把结果传入 `filterManifest`。

- 新增 `canTrustManifestReferences(List<ManifestFile> manifests)`：当 `allDeletesReferenceManifests` 为 `true` 且当前 manifest 路径集合包含 `manifestsWithDeletes` 时返回 `true`。注释说明：若某个没有 live files 的 manifest 不在被信任的引用集合中，意味着它没有需要删除的条目，无需重写。

- 重构 `filterManifest(Schema, ManifestFile, boolean trustManifestReferences)`：先查缓存；然后调用 `canContainDeletedFiles(manifest, trustManifestReferences)`，若返回 `false` 直接复用原 manifest。把原先散落一处的 `hasLiveFiles || canContainDeletedFiles` 判断整合进新方法。

- 重构 `canContainDeletedFiles(manifest, trustManifestReferences)`：
  - 若 `hasNoLiveFiles(manifest)` 返回 `false`。
  - 若 `trustManifestReferences`，直接返回 `manifestsWithDeletes.contains(manifest.path())`。
  - 否则回退到原有 `canContainDroppedFiles || canContainExpressionDeletes || canContainDroppedPartitions` 判断（注意原先还有 `canContainDropBySeq` 一项，新版未单独保留，可能由上游 `minSequenceNumber` 路径处理）。

- 拆分出 `hasNoLiveFiles`、`canContainExpressionDeletes`、`canContainDroppedPartitions`、`canContainDroppedFiles` 等独立小方法，提升可读性。

- 修改 `manifestHasDeletedFiles(evaluator, manifest, reader)`：方法签名加入 `ManifestFile` 参数；若 `manifestsWithDeletes.contains(manifest.path())` 直接返回 `true`，跳过对 manifest 内容的扫描判断。

### `core/src/jmh/java/org/apache/iceberg/ReplaceDeleteFilesBenchmark.java`

**修改目的**：扩展基准测试以度量部分替换场景下的性能提升。

**工作逻辑**：
- 把待替换的删除文件从 `deleteFiles` 重命名为 `deleteFilesToReplace`。
- `@Param` 文件数由 `2500000` 改为 `2000000`；新增 `@Param({"5", "25", "50", "100"}) percentDeleteFilesReplaced`，控制替换比例。
- `replaceDeleteFiles()` 基准方法加入 `validateFromSnapshot` 与提交后 `rollbackTo`，模拟真实"替换并回滚"的多次提交场景，以触发 manifest 重写路径。
- `initFiles()` 按 `percentDeleteFilesReplaced` 比例选取待替换文件，并从 manifest 中读取这些 `DeleteFile` 对象（保留 `manifestLocation` 信息），从而精确触发优化路径。这正对应优化生效的前提：通过 `ContentFile` 对象（而非路径）调用 `removeDeletes`。

### `core/src/test/java/org/apache/iceberg/TestRowDelta.java`

**修改目的**：补充针对新优化路径的单元测试，并修正既有用例以匹配新行为。

**工作逻辑**：
- 修改 `testDeleteDataFileWithDeleteFile`：在初始 RowDelta 中追加 `fileBDeletes()`，使删除 manifest 同时包含两个删除文件；后续用 `removeDeletes(FILE_B_DELETES)` 替换原先的 `newDelete().deleteFile("no-such-file")`，让"将 FILE_A_DELETES 老化掉"的场景通过真实删除路径触发，验证 manifest 行为。
- 新增 `testRewrittenDeleteFilesReadFromManifest`：从已提交的 delete manifest 中读出 `DeleteFile`，再用其调用 `removeDeletes` 替换为新删除文件，验证 manifest 重写结果符合预期（数据 manifest 与 delete manifest 的序列号、文件、状态都对齐）。
- 新增 `testConcurrentManifestRewriteWithDeleteFileRemoval`：构造一个并发场景——先发起 `removeDeletes` 提交，期间另一个 `RewriteManifests` 重写 delete manifest，再提交 `removeDeletes`。验证在新优化下，被重写后的新 manifest 仍能正确识别已删除文件，不会因为引用的旧 manifest 不存在而漏处理。

### `core/src/test/java/org/apache/iceberg/TestTransaction.java`

**修改目的**：覆盖事务提交失败重试时，结合 `RewriteManifests` 的并发场景。

**工作逻辑**：
- 新增 `testRowDeltaWithConcurrentManifestRewrite`：在事务内发起 `removeDeletes`，先读取 delete manifest 中条目以保证引用集合被填充；外部提交 `RewriteManifests` 重写 delete manifest；最后 `transaction.commitTransaction()` 触发重试。验证最终 delete manifest 中两个删除文件均标记为 `DELETED`。
- 新增 `testOverwriteWithConcurrentManifestRewrite`：对应 `OverwriteFiles` 的并发 manifest 重写场景，覆盖数据文件而非删除文件路径。从 data manifest 读取 `DataFile` 后调用 `deleteFile`，再在事务重试中验证最终 manifest 中对应文件被标记 `DELETED`。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestExpireSnapshotsProcedure.java` 与 `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestExpireSnapshotsProcedure.java`

**修改目的**：调整 expire snapshots 流程中的"使文件进入 DELETED 状态"步骤，使其通过 `removeDeletes` 触发而非依赖 INSERT 覆盖。

**工作逻辑**：原先通过 `sql("INSERT INTO TABLE %s VALUES (5, 'e')")` 让旧删除文件被新数据"无效化"进入 DELETED，这种方式不够直接且依赖触发条件。改为通过 `table.newRowDelta().removeDeletes(deleteFile).commit()`，直接基于读出的 `DeleteFile` 对象移除，更精确地反映优化路径覆盖的真实使用方式。同时把 `deleteFile.path()` 与 `location()` 的使用统一为通过 `DeleteFile` 句柄访问。

## 小结

- **成效**：在通过 `ContentFile` 对象进行删除的场景下，`ManifestFilterManager` 可精确跳过与本次提交无关的 manifest，避免不必要的 manifest 读取与扫描，显著降低大规模表上部分文件替换的开销；同时保留原有启发式路径以兼容表达式/分区/路径删除。
- **影响范围**：核心 `ManifestFilterManager` 一处生产代码重构；JMH 基准、core 单元测试、Spark 3.4/3.5 扩展测试同步更新以覆盖新路径。共 7 文件、+390/-77 行。
- **回迁到 1.4.x 的注意事项**：本提交属于性能优化且不改变对外 API 与文件格式，回迁风险较低，但 **需要谨慎评估两点**：（1）`ContentFile.manifestLocation()` 在 1.4.x 中是否已可用且语义一致，若 1.4.x 早期版本中该字段未填充，优化将无法生效甚至误判；（2）核心 `canContainDeletedFiles` 重构涉及多个判断路径的拆分与 `canContainDropBySeq` 的去除，回迁时需确认 1.4.x 中是否存在依赖原 `canContainDropBySeq`（DELETE manifest 的 `minSequenceNumber < minSequenceNumber`）判断的代码路径，避免遗漏导致数据 manifest 误重写或漏重写。建议连同配套测试（`TestRowDelta`、`TestTransaction`、Spark 扩展测试）一并回迁以保证回归覆盖。
