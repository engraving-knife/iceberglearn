# 提交 1104：Introduces the new IcebergSink based on the new V2 Flink Sink Abstraction (#10179)

## 提交信息

- **序号**：1104 / 4088
- **哈希**：bea364c36f30239ec0198a32de423ca1e484c9b5
- **短哈希**：bea364c36
- **日期**：2024-08-26（Mon Aug 26 13:28:46 2024 -0700）
- **作者**：Rodrigo <rmenesespinillos@apple.com>，合作者 Liwei Li、Kyle Bendickson、Peter Vary
- **提交说明**：Introduces the new IcebergSink based on the new V2 Flink Sink Abstraction (#10179)
- **PR/Issue**：#10179
- **影响模块**：flink v1.19 sink（这是一次大改动，新增 4129 行，删除 88 行，跨 23 个文件）

## 总体目的

Iceberg 长期以来在 Flink 端只有基于旧 `SinkFunction` 抽象的 `FlinkSink` 实现，其 `IcebergStreamWriter` + `IcebergFilesCommitter` 的两算子拓扑虽能用，但与 Flink 1.14+ 推出的 Sink V2 抽象（`Sink`、`SinkWriter`、`Committer`、`SupportsPreWriteTopology`、`SupportsPreCommitTopology`、`SupportsPostCommitTopology`）不兼容，无法享受 Sink V2 带来的统一 checkpoint 协议、post-commit 拓扑（用于后续小文件合并等扩展）、Flink 框架自动管理的 uid 等好处。

本提交在保留旧 `FlinkSink`（`@Deprecated` 不在此提交处理）的同时，新增一套完整的基于 Sink V2 的 `IcebergSink` 实现，目标是让 Iceberg Flink 集成逐步迁移到 Sink V2，为后续功能（如增量 compaction）打下基础。

## 如何达成设计目的

利用 Sink V2 的钩子接口拼装出如下作业图（来自 `IcebergSink` 类 javadoc）：

```
   Map → writer 1 ┐                                    ┌→ committer 1 → post commit 1
                   ├─→ IcebergWriteAggregator (P=1) ──┤
   Map → writer 2 ┘                                    └→ committer 2 → post commit 2
                                                       (commit 只在 committer 1)
```

- **`SupportsPreWriteTopology`**：`addPreWriteTopology` 根据 `DistributionMode`（NONE/HASH/RANGE）做 redistribute；RANGE 暂未支持，会回退。
- **`SinkWriter`**：`IcebergSinkWriter` 实现 `CommittingSinkWriter<RowData, WriteResult>`，内部用 `RowDataTaskWriterFactory` 写数据/删除文件，`prepareCommit()` 时 `writer.complete()` 取出 `WriteResult`，重新创建 writer 继续写下个 checkpoint。
- **`SupportsPreCommitTopology`**：`addPreCommitTopology` 把所有 writer 的 `CommittableMessage<WriteResult>` `.global()` 汇聚到并行度=1 的 `IcebergWriteAggregator`，aggregator 在 `prepareSnapshotPreBarrier` 时把所有 WriteResult 合并写成一个 `DeltaManifests`（manifest 文件），包装成 `IcebergCommittable` 发给下游 committer；`global()` 再一次汇聚确保只发到 committer subtask 0。
- **`SupportsCommitter`**：`IcebergCommitter` 实现 `Committer<IcebergCommittable>`，`commit()` 收到一批 `CommitRequest<IcebergCommittable>`，按 checkpointId 排序，调 `SinkUtil.getMaxCommittedCheckpointId` 找出已提交 checkpoint，对未提交的部分逐个 checkpoint 调 `table.newRowDelta()`/`table.newAppend()`/`table.newReplacePartitions()` 提交（**V2 + delete 文件场景下，每个 checkpoint 独立 commit，保证 equality delete 跨 checkpoint 正确生效**——与提交 1103 思路一致）。
- **`SupportsPostCommitTopology`**：`addPostCommitTopology` 留空，TODO 增量 compaction。
- **`IcebergCommittable`**：值对象，含 `byte[] manifest`（序列化的 `DeltaManifests`）+ `jobId` + `operatorId` + `checkpointId`，用于跨 operator 传递与幂等识别。
- **`IcebergCommittableSerializer`**：`SimpleVersionedSerializer<IcebergCommittable>`，VERSION=1，写 UTF jobId/operatorId + long checkpointId + int manifestLen + bytes。
- **`WriteResultSerializer`**：`SimpleVersionedSerializer<WriteResult>`，用 Flink `InstantiationUtil` 序列化 `WriteResult`。
- **`SinkUtil`**：抽出原本散在 `FlinkSink`/`IcebergFilesCommitter` 的共用方法 `checkAndGetEqualityFieldIds` 与 `getMaxCommittedCheckpointId`，让新 sink 与旧 sink 共用同一份"找最大已提交 checkpoint"逻辑。
- **`FlinkManifestUtil.deleteCommittedManifests`**：从 `IcebergFilesCommitter` 抽出来的静态方法，commit 成功后清理临时 manifest 文件，失败仅 warn 不抛错。

Builder 设计上对齐旧 `FlinkSink.Builder`：`forRowData`/`forRow`/`builderFor(mapper, outputType)` 三种入口；`table()`/`tableLoader()`/`set`/`setAll`/`tableSchema`/`overwrite`/`flinkConf`/`distributionMode`/`writeParallelism`/`upsert`/`equalityFieldColumns`/`uidSuffix`/`snapshotProperties`/`setSnapshotProperty`/`toBranch` 等配置方法；`append()` 触发构建并 `sinkTo(sink)`。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergSink.java`（新增 742 行）

**修改目的**：Sink V2 入口类与 Builder。

**工作逻辑**：

- 类签名实现 `Sink<RowData>`、`SupportsPreWriteTopology<RowData>`、`SupportsCommitter<IcebergCommittable>`、`SupportsPreCommitTopology<WriteResult, IcebergCommittable>`、`SupportsPostCommitTopology<IcebergCommittable>`，标 `@Experimental`。
- 字段含 `tableLoader`、`table`、`snapshotProperties`、`uidSuffix`、`sinkId`(UUID)、`writeProperties`、`flinkRowType`、`tableSupplier`、`flinkWriteConf`、`equalityFieldIds`、`upsertMode`、`dataFileFormat`、`targetDataFileSize`、`branch`、`overwriteMode`、`workerPoolSize`。
- `createWriter(InitContext)`：构造 `RowDataTaskWriterFactory` + `IcebergStreamWriterMetrics`，返回 `new IcebergSinkWriter(...)`，传入 subtaskId/attemptNumber。
- `createCommitter(CommitterInitContext)`：返回 `new IcebergCommitter(tableLoader, branch, snapshotProperties, overwriteMode, workerPoolSize, sinkId, metrics)`。
- `getCommittableSerializer()`：返回 `new IcebergCommittableSerializer()`。
- `getWriteResultSerializer()`：返回 `new WriteResultSerializer()`。
- `addPostCommitTopology`：空实现（TODO compaction）。
- `addPreWriteTopology`：调 `distributeDataStream(input)`，根据 distribution mode 走不同 keyBy/partitionCustom 分发；HASH 模式下若分区表使用 `BucketPartitioner`（hasOneBucketField），否则用 `PartitionKeySelector`；有 equality fields 时按 `EqualityFieldKeySelector` 分发；RANGE 模式当前回退为 NONE/equality。
- `addPreCommitTopology`：`.global().transform(preCommitAggregatorUid, typeInformation, new IcebergWriteAggregator(tableLoader)).uid(...).setParallelism(1).setMaxParallelism(1).global()`，强制聚合在单 subtask 完成。
- 内部 `Builder` 类提供上文列出的配置方法。`build()` 中：
  - 校验 `inputCreator` 与 `tableLoader`；
  - 通过 `checkAndGetTable` 加载或复用 `SerializableTable`；
  - 构造 `FlinkWriteConf`；
  - 处理 `tableRefreshInterval`（决定 `CachingTableSupplier` vs 直接 supplier）；
  - 调 `SinkUtil.checkAndGetEqualityFieldIds` 算 equalityFieldIds；
  - upsert 模式下校验 overwrite 关闭、equality 字段非空、分区字段是 equality 字段子集；
  - 调 `writeProperties(table, format, conf)` 拼写文件压缩属性；
  - 调 `toFlinkRowType(schema, tableSchema)` 决定 Flink RowType。
- `append()`：调 `build()` → `inputCreator.apply(suffix)` → `rowDataInput.sinkTo(sink).uid(suffix).name(suffix)`，若设了 `writeParallelism` 则 `setParallelism`。
- 静态工厂 `forRowData`/`forRow`/`builderFor` 与旧 `FlinkSink` 对齐，便于迁移。
- `uidSuffix` javadoc 详尽说明：对已有作业首次设置 `uidSuffix` 会改变 operator uid，恢复时需 `--allowNonRestoredState`，可能导致最后一次 commit 状态丢失。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergSinkWriter.java`（新增 113 行）

**修改目的**：Sink V2 的 writer 实现。

**工作逻辑**：实现 `CommittingSinkWriter<RowData, WriteResult>`。构造时 `taskWriterFactory.initialize(subTaskId, attemptId)` + `taskWriterFactory.create()`。`write` 直接 `writer.write(element)`。`flush(boolean endOfInput)` 空实现（Sink V2 的 flush 由框架在 checkpoint 时调 `prepareCommit`）。`prepareCommit` 调 `writer.complete()` 取 `WriteResult`，重新 `taskWriterFactory.create()`，更新 metrics，返回 `Lists.newArrayList(result)`。`close` 关 writer。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergWriteAggregator.java`（新增 127 行）

**修改目的**：聚合多个 writer 的 `WriteResult` 为单个 `IcebergCommittable`。

**工作逻辑**：继承 `AbstractStreamOperator<CommittableMessage<IcebergCommittable>>` 实现 `OneInputStreamOperator<CommittableMessage<WriteResult>, CommittableMessage<IcebergCommittable>>`。

- `open()`：开 tableLoader，从 environment 拿 `flinkJobId`/`operatorId`/`subTaskId`（断言必须为 0）/`attemptId`，加载 table，构造 `ManifestOutputFileFactory`。
- `processElement`：从 `CommittableWithLineage<WriteResult>` 取出 `WriteResult` 加入 `results` 集合。
- `prepareSnapshotPreBarrier(checkpointId)`：把 `results` 写成 `DeltaManifests` manifest，包装成 `IcebergCommittable`；先 emit 一个 `CommittableSummary`（subtask=0, numSubtasks=1, checkpointId, 1 committable, 1, 0），再 emit `CommittableWithLineage(committable, checkpointId, 0)`；清空 `results`。
- `finish()`：调 `prepareSnapshotPreBarrier(Long.MAX_VALUE)` 处理 bounded 作业 endInput。
- `writeToManifest(writeResults, checkpointId)`：空则返回 `EMPTY_MANIFEST_DATA`；否则 `WriteResult.builder().addAll(writeResults).build()` + `FlinkManifestUtil.writeCompletedFiles(...)` + `SimpleVersionedSerialization.writeVersionAndSerialize(DeltaManifestsSerializer.INSTANCE, deltaManifests)`。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergCommitter.java`（新增 311 行）

**修改目的**：Sink V2 的 committer 实现。

**工作逻辑**：

- 实现 `Committer<IcebergCommittable>`。构造时开 tableLoader、加载 table、读 `flink.max-continuous-empty-commits`（默认 10）、起 workerPool。
- `commit(commitRequests)`：按 checkpointId 排进 `NavigableMap`，取 last 的 `jobId/operatorId` 调 `SinkUtil.getMaxCommittedCheckpointId` 找已提交 checkpoint；对 ≤ 已提交的 `signalAlreadyCommitted`；对 > 已提交的 `commitPendingRequests`。
- `commitPendingRequests`：遍历每个 `(ckptId, request)`，反序列化 manifest 为 `DeltaManifests`，调 `FlinkManifestUtil.readCompletedFiles` 还原 `WriteResult`，组装 `NavigableMap<Long, WriteResult> pendingResults`，记录所有 manifest 文件路径；调 `commitPendingResult`；最后 `FlinkManifestUtil.deleteCommittedManifests` 清理。
- `commitPendingResult`：连续空 commit 计数，超阈值才真正提交；`replacePartitions=true` 调 `replacePartitions`（不允许 delete files），否则 `commitDeltaTxn`。
- `commitDeltaTxn`：**关键**：若没有 delete files，合并所有 pendingResults 数据文件成一次 `table.newAppend()`；若有 delete files，则**对每个 checkpoint 各做一次 `table.newRowDelta()`**，确保每个 checkpoint 独立 sequence number，equality delete 才能跨 checkpoint 正确生效（与提交 1103 同一思路）。注释明确解释：合并会导致 delete 语义错误。
- `commitOperation`：把 `flink.max-committed-checkpoint-id`/`flink.job-id`/`flink.operator-id` 写入 snapshot summary，`toBranch(branch)`，commit，记录耗时与 metrics。
- `replacePartitions`：动态分区覆盖，用 `table.newReplacePartitions()`，禁止 delete files，禁止 referencedDataFiles。
- `close`：关 tableLoader。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergCommittable.java`（新增 95 行）

**修改目的**：committable 值对象。

**工作逻辑**：`Serializable`，持有 `byte[] manifest`、`String jobId`、`String operatorId`、`long checkpointId`，提供 getter，实现 `equals/hashCode/toString`（manifest 用 `Arrays.equals/hashCode`）。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergCommittableSerializer.java`（新增 68 行）

**修改目的**：committable 序列化器。

**工作逻辑**：`SimpleVersionedSerializer<IcebergCommittable>`，VERSION=1。序列化：UTF jobId + UTF operatorId + long checkpointId + int manifestLen + manifest bytes。反序列化按版本号分支，v1 时按相同顺序读出。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/WriteResultSerializer.java`（新增 61 行）

**修改目的**：`WriteResult` 序列化器，用于 Sink V2 框架在 writer 与 aggregator 间传递。

**工作逻辑**：`SimpleVersionedSerializer<WriteResult>`，VERSION=1。直接用 Flink `InstantiationUtil.serializeObject` 序列化整个 `WriteResult` 对象；反序列化用 `IcebergCommittableSerializer.class.getClassLoader()` 作为 classloader。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/SinkUtil.java`（新增 94 行）

**修改目的**：抽出旧实现中共用方法。

**工作逻辑**：

- `checkAndGetEqualityFieldIds(Table, List<String>)`：从 `table.schema().identifierFieldIds()` 起步；若用户给了 `equalityFieldColumns`，按列名查 fieldId，覆盖默认 identifier 集合；若与 schema identifier 不一致 warn 但仍用用户配置。
- `getMaxCommittedCheckpointId(Table, flinkJobId, operatorId, branch)`：从最新 snapshot 沿 parent 链向前找，找到第一个 summary 中 `flink.job-id` 匹配且 `flink.operator-id` 匹配（或为 null）的 snapshot，读其 `flink.max-committed-checkpoint-id`；找不到返回 -1。
- 常量：`FLINK_JOB_ID`、`OPERATOR_ID`、`MAX_COMMITTED_CHECKPOINT_ID`、`INITIAL_CHECKPOINT_ID`。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkManifestUtil.java`（修改）

**修改目的**：抽出 `deleteCommittedManifests` 静态方法供新旧 sink 共用。

**工作逻辑**：新增 `static void deleteCommittedManifests(Table table, List<ManifestFile> manifests, String newFlinkJobId, long checkpointId)`，逐个 `table.io().deleteFile(manifest.path())`，失败仅 `LOG.warn` 不抛异常；新增 SLF4J logger；`MoreObjects.toStringHelper` 加 `tableName` 字段。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergFilesCommitter.java`（修改）

**修改目的**：移除已抽出的方法，改调 `SinkUtil` 与 `FlinkManifestUtil`。

**工作逻辑**：

- 移除 `getMaxCommittedCheckpointId` 静态方法（迁到 `SinkUtil`）。
- 移除 `deleteCommittedManifests` 私有方法（迁到 `FlinkManifestUtil`），原调用点改为 `FlinkManifestUtil.deleteCommittedManifests(table, manifests, newFlinkJobId, checkpointId)`。
- initializeState 中对 restoredFlinkJobId 的查询改调 `SinkUtil.getMaxCommittedCheckpointId`。
- 删除已不再用到的 import：`Snapshot`、`MoreObjects`。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkSink.java`（修改）

**修改目的**：复用 `SinkUtil.checkAndGetEqualityFieldIds`，去掉本地版本。

**工作逻辑**：把 `checkAndGetEqualityFieldIds()` 本地调用改为 `SinkUtil.checkAndGetEqualityFieldIds(table, equalityFieldColumns)`。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergStreamWriter.java`（修改）

**修改目的**：toString 字段名规范化。

**工作逻辑**：`toString` 中 `table_name`/`subtask_id`/`attempt_id` 改为驼峰 `tableName`/`subTaskId`/`attemptId`，与 `IcebergSinkWriter` 风格一致。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/ManifestOutputFileFactory.java`（修改）

**修改目的**：暴露常量供测试用。

**工作逻辑**：`FLINK_MANIFEST_LOCATION` 加 `@VisibleForTesting` 注解（同时整理为同一行格式）。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergSink.java`（新增 436 行）

**修改目的**：新 `IcebergSink` 的端到端测试，参数化（FileFormat、Parallelism、Partitioned、DistributionMode），继承 `TestFlinkIcebergSinkBase`。覆盖基本写入、distribution mode、parallelism 等场景。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergSinkV2.java`（新增 267 行）

**修改目的**：新 sink 的 V2 表场景测试，参数化 + MiniClusterExtension，覆盖 upsert、changelog、branch 等场景，复用 `TestFlinkIcebergSinkV2Base` 的测试方法。`@Timeout(60)` 防止挂死。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergSinkBranch.java`（新增 120 行）与 `TestIcebergSinkV2Branch.java`（新增 119 行）

**修改目的**：branch 写入测试，分别对应 V1/V2 表格式。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergCommitter.java`（新增 1445 行）

**修改目的**：`IcebergCommitter` 的单元测试，使用 `OneInputStreamOperatorTestHarness` 直接驱动 committer operator，覆盖 commit 顺序、已提交 checkpoint 跳过、恢复、空 commit 阈值、replacePartitions、branch 等大量场景。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/SinkTestUtil.java`（新增 62 行）

**修改目的**：测试工具类。

**工作逻辑**：`transformsToStreamElement` 把 harness 输出转 `StreamElement`；`extractAndAssertCommittableSummary` / `extractAndAssertCommittableWithLineage` 从输出中提取并校验 committable 元信息。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2Base.java`（修改）

**修改目的**：放宽访问修饰符以便新 sink 测试复用。

**工作逻辑**：`public class` 改 `class`（包私有），所有 `protected` 字段/方法改为包私有 `static`/普通，让同包的 `TestIcebergSinkV2`、`TestIcebergSinkV2Branch` 继承复用。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2Branch.java`（修改）

**修改目的**：跟随 base 类访问修饰符调整，`CATALOG_EXTENSION` 由 `private` 改 `static`（包私有）。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkManifest.java`（修改）

**修改目的**：清理 import 风格。

**工作逻辑**：移除顶部 `import static ...FLINK_MANIFEST_LOCATION`，改为引用处 `ManifestOutputFileFactory.FLINK_MANIFEST_LOCATION` 全限定静态访问。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergFilesCommitter.java`（修改）

**修改目的**：跟随主代码 refactor。

**工作逻辑**：`IcebergFilesCommitter.getMaxCommittedCheckpointId` 改调 `SinkUtil.getMaxCommittedCheckpointId`；`operatorID.toHexString()` 改为 `operatorID.toString()`（与 sink V2 写入 summary 时的格式一致，保证读回比对正确）；若干注释格式整理。

## 小结

- **成效**：Flink 集成新增一套完整的基于 Sink V2 抽象的 `IcebergSink`，含 writer/aggregator/committer 全链路与完整 Builder API；同时把 `getMaxCommittedCheckpointId`、`checkAndGetEqualityFieldIds`、`deleteCommittedManifests` 抽到 `SinkUtil`/`FlinkManifestUtil` 让新旧 sink 共用。V2 表 + delete 文件场景下，每个 checkpoint 独立 commit 以保证 equality delete 语义正确。配套测试覆盖单测 + 端到端。
- **影响范围**：仅 flink v1.19 sink 模块（v1.20 在下一个提交 1105 单独 backport）。新增了多个公共 API 类（`IcebergSink` 标 `@Experimental`，`IcebergSink.Builder` 等），但旧 `FlinkSink` 仍保留可用，用户可渐进迁移。
- **回迁到 1.4.x 的注意事项**：
  - 这是一个大特性引入，**不建议**作为 hotfix 回迁到 1.4.x 维护分支。1.4.x 通常对应较旧的 Flink 版本（1.17/1.18），不一定有完整的 Sink V2 抽象（`SupportsPreCommitTopology`/`SupportsPostCommitTopology` 等需要 Flink 1.19+）。
  - 即使 1.4.x 的 Flink 版本支持 Sink V2，回迁也需同时拉取 `IcebergSink`、`IcebergSinkWriter`、`IcebergWriteAggregator`、`IcebergCommitter`、`IcebergCommittable`、`IcebergCommittableSerializer`、`WriteResultSerializer`、`SinkUtil`、`FlinkManifestUtil.deleteCommittedManifests` 以及 `FlinkSink`/`IcebergFilesCommitter`/`ManifestOutputFileFactory` 的 refactor 改动，外加大量测试；cherry-pick 时跨多个 flink 版本目录（v1.17/v1.18/v1.19/v1.20）需要逐目录处理。
  - 公共 API 引入需在 release notes 中声明。
  - 若 1.4.x 用户需要 V2 表 + equality delete 的正确性修复，可考虑只回迁提交 1103（aborted checkpoint 重复数据修复），不回迁整套 Sink V2。
  - 注意 `TestFlinkIcebergSinkV2Base` 访问修饰符从 `public` 改为包私有，若 1.4.x 已有外部测试继承该类需评估影响（通常测试不在公开 API 范畴）。
