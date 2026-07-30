# 提交 3287：Core: populate manifest created/replaced/kept count when commit a snapshot (#15003)

## 提交信息

- **序号**：3287 / 4088
- **哈希**：c31dd921e1550c643a3ea1fce9d9573cc06e2607
- **短哈希**：c31dd921e1
- **日期**：2026-02-19
- **作者**：Hongyue/Steve Zhang
- **提交说明**：Core: populate manifest created/replaced/kept count when commit a snapshot (#15003)
- **PR/Issue**：#15003

## 总体目的

Iceberg 在每次提交快照（snapshot commit）时会生成一份 `SnapshotSummary`，其中已包含 `added-data-files`、`deleted-records`、`total-records` 等统计指标。对于"清单文件（manifest）"层面，仓库中早已定义了三个常量 `CREATED_MANIFESTS_COUNT`（"manifests-created"）、`KEPT_MANIFESTS_COUNT`（"manifests-kept"）、`REPLACED_MANIFESTS_COUNT`（"manifests-replaced"），并且 `CommitMetricsResult` 与其 JSON 解析器也已暴露对应的 `manifestsCreated()`/`manifestsKept()`/`manifestsReplaced()` 计数器接口。但这些指标此前只在一条特殊代码路径——`BaseRewriteManifests`（即 `rewriteManifests` 显式重写清单的动作）——中被填充；对于绝大多数日常提交（`FastAppend`、`MergeAppend`、`RowDelta`、`DeleteFiles`、`Overwrite` 等走 `SnapshotProducer`/`MergingSnapshotProducer` 的操作），快照摘要里根本不会出现这三项，导致用户和监控系统能看到文件级指标却看不到清单级指标，无法判断一次提交究竟新建了多少清单、保留了哪些旧清单、又重写（替换）了多少清单。

本提交的目的就是补齐这一缺口：在所有经过 `SnapshotProducer` 的常规提交路径中，自动计算并写入 created/kept/replaced 三项清单计数，使快照摘要与提交指标在所有提交类型下保持一致。这对于运维和性能分析尤为关键——例如 delete 操作会重写清单以标记被删文件、merge append 会通过 bin-packing 合并多个旧清单，这些都会产生"替换"清单的开销，过去无法从摘要中观测，现在可以统一度量。

设计上的一个难点是"替换"（replaced）的语义界定：一次提交可能既有过滤重写（`ManifestFilterManager`，为删除文件而重写清单），又有 bin-packing 合并（`ManifestMergeManager`，为压缩清单数量而合并），还可能包含内存中新生成的清单。提交需要把这几条路径上"被替换掉的旧清单"准确累加，并且要排除当前提交刚生成的内存清单（这些不算"被替换"，因为它们从未提交过）。作者通过在两个 manager 中引入 `AtomicInteger` 计数器，并在 `cleanUncommitted` 回滚未提交清单时做相应的 `decrementAndGet` 校正，最终在 `MergingSnapshotProducer` 中汇总四个 manager 的 replaced 计数之和。

## 如何达成设计目的

整体思路是在 `SnapshotProducer`（所有提交的基类）中新增一个受保护方法 `buildManifestCountSummary(manifests, replacedManifestsCount)`，它遍历最终提交的清单列表，按 `manifest.snapshotId()` 是否等于当前 `snapshotId()` 分类：相等记为 created，非空但不等记为 kept，空则不计（避免误把没有快照归属的清单算作 kept）。replaced 计数由调用方传入。`FastAppend` 在 `apply()` 中以 `replacedManifestsCount=0` 调用该方法（追加不会替换清单），`MergingSnapshotProducer` 则把 `filterManager`、`deleteFilterManager`、`mergeManager`、`deleteMergeManager` 四个 manager 的 `replacedManifestsCount()` 相加后传入。两个 manager 类各自维护 `AtomicInteger replacedManifestsCount`，在发生重写/合并时 `incrementAndGet`，在 `cleanUncommitted` 删除未提交副本时 `decrementAndGet`（回滚本次未生效的替换），并在 `invalidateFilteredCache` 时重置归零。涉及的文件集中在 `core/src/main/java/org/apache/iceberg/` 下的 `SnapshotProducer.java`、`FastAppend.java`、`MergingSnapshotProducer.java`、`ManifestFilterManager.java`、`ManifestMergeManager.java`，以及对应的一批测试。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java` (+28/-0 lines)

**修改目的**：新增统一的清单计数构建方法，供各提交类型复用。

**工作逻辑**：
新增 `buildManifestCountSummary(List<ManifestFile> manifests, int replacedManifestsCount)`。该方法遍历最终清单列表，依据 `snapshotId() == manifest.snapshotId()` 判定为 created，`null != manifest.snapshotId()`（且不等于当前快照）判定为 kept，`snapshotId()` 为 null 的清单既不算 created 也不算 kept（这是为避免把没有快照归属的内存清单误计入 kept 的关键判断）。随后用 `summaryBuilder.set(...)` 写入三个常量键。返回的 builder 由调用方合并进主摘要。注意 kept 的判定特意要求 `manifest.snapshotId() != null`，因为提交说明中提到"Only increment kept-manifest count if snapshot id is assigned to a manifest"——即只有明确属于某个历史快照的清单才算"保留"，没有快照归属的不算。

### `core/src/main/java/org/apache/iceberg/FastAppend.java` (+2/-0 lines)

**修改目的**：在快追加提交中填充清单计数。

**工作逻辑**：
`FastAppend.apply()` 在收集完清单（含历史快照的清单）后，调用 `summaryBuilder.merge(buildManifestCountSummary(manifests, 0))`。第二个参数为 0，因为快追加不删除文件、不合并清单，所以不会替换任何旧清单；created 即为本快照新写的清单数，kept 即为继承自上一快照的清单数。

### `core/src/main/java/org/apache/iceberg/MergingSnapshotProducer.java` (+14/-0 lines)

**修改目的**：在合并类提交（delete/overwrite/rowDelta/mergeAppend）中汇总并填充四路 replaced 计数。

**工作逻辑**：
在 `apply()` 中合并完 `mergeManager` 与 `deleteMergeManager` 的结果后，按注释列出的四个来源累加 `replacedManifestsCount`：①`filterManager`（数据清单为删文件而重写）、②`deleteFilterManager`（删除清单为删文件而重写）、③`mergeManager`（数据清单 bin-packing 合并）、④`deleteMergeManager`（删除清单 bin-packing 合并）。注释特别指出 `rewrittenAppendManifests` 是新清单副本而非替换，不计入。最终调用 `summaryBuilder.merge(buildManifestCountSummary(manifests, replacedManifestsCount))`。

### `core/src/main/java/org/apache/iceberg/ManifestFilterManager.java` (+24/-2 lines)

**修改目的**：跟踪因删除文件而被重写的清单数量。

**工作逻辑**：
新增字段 `AtomicInteger replacedManifestsCount`。在 `filterManifestWithDeletedFiles` 真正发生重写后 `incrementAndGet()`（即原清单被一个过滤后的新清单替换）。在 `cleanUncommitted` 中，当某个过滤副本未被提交（`!committed.contains(filtered)` 且 `!manifest.equals(filtered)`）时，说明这次替换实际未生效，做 `decrementAndGet()` 校正，并删除该副本文件。在 `invalidateFilteredCache` 中调用 `cleanUncommitted(EMPTY_SET)` 后将计数 `set(0)` 重置，保证下次提交从干净状态开始。新增 `replacedManifestsCount()` 访问器供上层汇总。

### `core/src/main/java/org/apache/iceberg/ManifestMergeManager.java` (+34/-3 lines)

**修改目的**：跟踪因 bin-packing 合并而被替换的旧清单数量。

**工作逻辑**：
新增字段 `AtomicInteger replacedManifestsCount`。在 `createManifest(bin)` 合并一组清单为一个新清单后，遍历 bin 中每个清单，仅当 `snapshotId() != m.snapshotId()`（即来自历史快照）时才 `incrementAndGet()`——内存中的新清单不算被替换。在 `cleanUncommitted` 中，当某个合并产物未被提交时，删除其文件并对 bin 中每个 `snapshotId() != m.snapshotId()` 的清单 `decrementAndGet()`，回滚未生效的替换计数。这里"只对历史快照清单计数"的设计很关键：一次提交中 bin-packing 既可能合并历史清单，也可能合并本提交新生成的清单，只有前者才算"替换"。新增 `replacedManifestsCount()` 访问器。

### `core/src/test/java/org/apache/iceberg/TestCommitReporting.java` (+18/-0 lines)

**修改目的**：验证提交指标（CommitMetrics）中的清单计数。

**工作逻辑**：
在数据 append、数据 delete、删除文件 append、删除文件 rewrite 等场景断言 `metrics.manifestsCreated()/manifestsKept()/manifestsReplaced()` 的值。例如 append 后 created=1/kept=0/replaced=0；delete 重写清单后 created=1/kept=0/replaced=1；rewrite 删除清单后 created=1/kept=0/replaced=1。

### `core/src/test/java/org/apache/iceberg/TestTransaction.java` (+12/-0 lines)

**修改目的**：验证事务提交的快照摘要含清单计数。

**工作逻辑**：
在事务 append 与 delete 后断言 `appendSnapshot.summary()` 为 created=1/kept=0/replaced=0，`deleteSnapshot.summary()` 为 created=1/kept=0/replaced=1（delete 重写了 append 的清单）。

### `core/src/test/java/org/apache/iceberg/TestSnapshotSummary.java` (+46/-31 lines)

**修改目的**：在多种提交场景下校验摘要包含三项清单计数。

**工作逻辑**：
多个测试方法将断言的摘要条目数从 11/12/14/16 相应增加到 14/15/17/19（每项增加 3 个键），并新增对 `CREATED_MANIFESTS_COUNT`/`KEPT_MANIFESTS_COUNT`/`REPLACED_MANIFESTS_COUNT` 值的断言。覆盖 append、delete、overwrite、position delete 单条/多条、delete 在多分区等场景，验证 created/kept/replaced 的不同组合（如多次 position delete 后 created=3/kept=0/replaced=2；含保留旧清单场景 created=2/kept=1/replaced=1）。

### `core/src/test/java/org/apache/iceberg/TestFastAppend.java` (+33/-4 lines)

**修改目的**：验证快追加的清单计数。

**工作逻辑**：
在 `appendManifest`、`appendFile`+`appendManifest` 组合、单分区/多分区 append 等场景断言 created/replaced/kept。例如同时 appendFile 与 appendManifest 时 created=2（两份新清单）；普通单清单 append 时 created=1/kept=0/replaced=0。

### `core/src/test/java/org/apache/iceberg/TestMergeAppend.java` (+43/-4 lines)

**修改目的**：验证合并追加中 bin-packing 合并产生的替换计数。

**工作逻辑**：
关键场景：第一快照 append 3 个清单，bin-packing 分两 bin，created=2/replaced=0/kept=0（首快照无历史可替换）；第二快照再 append 3 个新清单并与 snap1 的 2 个旧清单一起 bin-packing 合并，断言 created=3/replaced=2/kept=0（2 个旧清单被合并替换）。delete 场景断言 created=1/replaced=1/kept=0。还覆盖了带分区摘要的 append。

### `core/src/test/java/org/apache/iceberg/TestRowDelta.java` (+46/-2 lines)

**修改目的**：验证 RowDelta（同时增删行）的清单计数。

**工作逻辑**：
覆盖纯 delete（created=1/replaced=0）、纯 append（created=1/replaced=0）、overwrite（created=2/replaced=0）、先 append 再 addRows+removeRows（第二快照 created/replaced 据重写情况断言）等场景。

### `core/src/test/java/org/apache/iceberg/TestDeleteFiles.java` (+110/-2 lines)

**修改目的**：在多种 delete 场景下验证重写清单带来的 replaced 计数。

**工作逻辑**：
在单文件 delete、连续 delete、按行过滤 delete、多分区 delete 等场景断言：初始 append 为 created=1/replaced=0；每次 delete 重写清单后为 created=1/replaced=1/kept=0。这是 replaced 计数最主要的验证场景。

## 总结

本提交将原先仅在 `rewriteManifests` 动作中可用的 created/kept/replaced 清单计数扩展到所有常规快照提交路径，通过在 `SnapshotProducer` 基类提供统一的计数构建方法、并在 `ManifestFilterManager`/`ManifestMergeManager` 中以 `AtomicInteger` 精确跟踪"重写"与"合并"两条替换来源，使快照摘要与提交指标能够完整反映每次提交对清单结构的真实影响，显著提升了 Iceberg 在清单演进可观测性上的能力，对运维调优与监控具备实际价值。
