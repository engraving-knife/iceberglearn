# 提交 1105：Flink: Backport PR #10179 to Flink 1.20 for v2 sink (#11011)

## 提交信息

- **序号**：1105 / 4088
- **哈希**：e6f8ab9950267c18734ba0cb2ce29867790e24fe
- **短哈希**：e6f8ab995
- **日期**：2024-08-26（Mon Aug 26 15:57:57 2024 -0700）
- **作者**：Rodrigo <rmenesespinillos@apple.com>
- **提交说明**：Flink: Backport PR #10179 to Flink 1.20 for v2 sink (#11011)
- **PR/Issue**：#11011，回迁源 PR #10179（即提交 1104）
- **影响模块**：flink v1.20 sink

## 总体目的

提交 1104 把基于 Sink V2 抽象的全新 `IcebergSink` 引入了 `flink/v1.19` 目录。Iceberg 同时维护多个 Flink 版本目录（v1.18/v1.19/v1.20 等），新特性需要在所有受支持版本同步落地。本提交把 #10179 的全部改动**机械回迁到 `flink/v1.20` 目录**，让 Flink 1.20 用户也能用上新的 Sink V2 `IcebergSink`。

## 如何达成设计目的

回迁策略非常直接：把 v1.19 上的 23 个文件改动按相同 diff 应用到 `flink/v1.20/flink/src/...` 对应路径。文件清单、行数变更（4129+/88-）、各文件内部改动逻辑与提交 1104 **完全一致**，仅以下两点不同：

1. **路径前缀**：所有文件路径从 `flink/v1.19/...` 换成 `flink/v1.20/...`。
2. **行号偏移**：因 v1.20 的基础文件与 v1.19 略有差异（例如 v1.20 的 `IcebergStreamWriter` 基类仍是 `AbstractStreamOperator<WriteResult>`，**没有**提交 1103 引入的 `FlinkWriteResult` 改造；`IcebergFilesCommitter`、`TestIcebergFilesCommitter` 也有若干行号偏移），diff 的 `@@` hunk 头行号不同，但语义改动一致。

具体差异点（来自 diff 对比）：

- `IcebergStreamWriter.java`：v1.19 的 hunk 上下文是 `extends AbstractStreamOperator<FlinkWriteResult>`，v1.20 是 `extends AbstractStreamOperator<WriteResult>`——说明 **v1.20 在本提交时点尚未引入 1103 的 FlinkWriteResult 包装**，仍直接 emit `WriteResult`。本提交对 v1.20 的 `IcebergStreamWriter` 只改了 `toString` 字段名（snake_case → camelCase），与 v1.19 的 1104 改动一致。
- `IcebergFilesCommitter.java`、`TestIcebergFilesCommitter.java`：仅 hunk 行号偏移（v1.20 行号比 v1.19 略小）。
- 其余文件改动完全一致。

## 修改详情

下表列出 v1.20 下的 23 个文件改动，每个文件的修改目的与工作逻辑**与提交 1104 完全相同**（详见提交 1104 的 analysis.md），此处仅列出文件清单与改动概要，避免重复：

### 主代码（13 个文件）

| 文件 | 类型 | 改动概要 |
| --- | --- | --- |
| `IcebergSink.java` | 新增 742 行 | Sink V2 入口类与 Builder，实现 `Sink<RowData>` + 4 个 Supports* 钩子；`addPreWriteTopology`/`addPreCommitTopology`/`addPostCommitTopology` 拼装作业图；Builder 提供 `forRowData`/`forRow`/`builderFor` 与全部配置方法 |
| `IcebergSinkWriter.java` | 新增 113 行 | `CommittingSinkWriter<RowData, WriteResult>` 实现，`write` 调 `writer.write`，`prepareCommit` 调 `writer.complete()` 取 `WriteResult` 后重建 writer |
| `IcebergWriteAggregator.java` | 新增 127 行 | 并行度=1 的聚合算子，`processElement` 收集 `WriteResult`，`prepareSnapshotPreBarrier` 写 `DeltaManifests` 包装成 `IcebergCommittable` emit |
| `IcebergCommitter.java` | 新增 311 行 | `Committer<IcebergCommittable>` 实现，按 checkpointId 排序，调 `SinkUtil.getMaxCommittedCheckpointId` 跳过已提交，未提交部分对每个 checkpoint 独立 `newRowDelta`/`newAppend`/`newReplacePartitions` |
| `IcebergCommittable.java` | 新增 95 行 | committable 值对象，含 `manifest`/`jobId`/`operatorId`/`checkpointId` |
| `IcebergCommittableSerializer.java` | 新增 68 行 | `SimpleVersionedSerializer<IcebergCommittable>`，VERSION=1 |
| `WriteResultSerializer.java` | 新增 61 行 | `SimpleVersionedSerializer<WriteResult>`，用 Flink `InstantiationUtil` 序列化 |
| `SinkUtil.java` | 新增 94 行 | 抽出共用方法 `checkAndGetEqualityFieldIds` 与 `getMaxCommittedCheckpointId`，常量 `FLINK_JOB_ID`/`OPERATOR_ID`/`MAX_COMMITTED_CHECKPOINT_ID` |
| `FlinkManifestUtil.java` | 修改 | 新增静态方法 `deleteCommittedManifests(Table, manifests, jobId, ckptId)`，失败仅 warn 不抛错；加 logger |
| `FlinkSink.java` | 修改 | `checkAndGetEqualityFieldIds()` 改调 `SinkUtil.checkAndGetEqualityFieldIds(table, equalityFieldColumns)` |
| `IcebergFilesCommitter.java` | 修改 | 移除 `getMaxCommittedCheckpointId` 与 `deleteCommittedManifests`，改调 `SinkUtil`/`FlinkManifestUtil`；移除多余 import |
| `IcebergStreamWriter.java` | 修改 | 仅 `toString` 字段名 snake_case → camelCase。**注意**：v1.20 的 IcebergStreamWriter 基类仍是 `AbstractStreamOperator<WriteResult>`，未引入 v1.19 的 `FlinkWriteResult` |
| `ManifestOutputFileFactory.java` | 修改 | `FLINK_MANIFEST_LOCATION` 加 `@VisibleForTesting` |

### 测试代码（10 个文件）

| 文件 | 类型 | 改动概要 |
| --- | --- | --- |
| `TestIcebergSink.java` | 新增 436 行 | 新 sink 端到端参数化测试，继承 `TestFlinkIcebergSinkBase` |
| `TestIcebergSinkV2.java` | 新增 267 行 | V2 表场景测试，`@Timeout(60)`，复用 `TestFlinkIcebergSinkV2Base` |
| `TestIcebergSinkBranch.java` | 新增 120 行 | branch 写入测试（V1 表） |
| `TestIcebergSinkV2Branch.java` | 新增 119 行 | branch 写入测试（V2 表） |
| `TestIcebergCommitter.java` | 新增 1445 行 | `IcebergCommitter` 单元测试，覆盖 commit 顺序、跳过已提交、恢复、空 commit 阈值、replacePartitions、branch |
| `SinkTestUtil.java` | 新增 62 行 | 测试工具：`transformsToStreamElement`/`extractAndAssertCommittableSummary`/`extractAndAssertCommittableWithLineage` |
| `TestFlinkIcebergSinkV2Base.java` | 修改 | `public class` → `class`，`protected` → 包私有，便于新 sink 测试复用 |
| `TestFlinkIcebergSinkV2Branch.java` | 修改 | `private CATALOG_EXTENSION` → `static`（跟随 base 类） |
| `TestFlinkManifest.java` | 修改 | 移除 `import static ...FLINK_MANIFEST_LOCATION`，改为全限定 `ManifestOutputFileFactory.FLINK_MANIFEST_LOCATION` |
| `TestIcebergFilesCommitter.java` | 修改 | `IcebergFilesCommitter.getMaxCommittedCheckpointId` → `SinkUtil.getMaxCommittedCheckpointId`；`operatorID.toHexString()` → `operatorID.toString()`；注释格式整理 |

## 小结

- **成效**：v1.20 用户获得与 v1.19 完全对等的 Sink V2 `IcebergSink` 实现，新特性在两个 Flink 版本同步落地，便于后续维护与功能演进（如增量 compaction）。
- **影响范围**：仅 `flink/v1.20` 目录。新增公共 API 类与 v1.19 一致；旧 `FlinkSink` 仍保留可用。
- **回迁到 1.4.x 的注意事项**：
  - 与提交 1104 同样，这是大特性同步回迁，**不建议**作为 hotfix 回迁到 1.4.x 维护分支。1.4.x 通常对应 Flink 1.17/1.18，可能不具备完整的 Sink V2 抽象。
  - 注意一个时序问题：**v1.20 在本提交时点没有提交 1103 的 `FlinkWriteResult` 改造**（v1.20 的 `IcebergStreamWriter` 仍直接 emit `WriteResult`，类签名是 `AbstractStreamOperator<WriteResult>`）。如果 1.4.x 基于 v1.20 路径回迁整套 Sink V2，需评估是否同时需要 1103 的修复才能保证 V2 表 + upsert 在 aborted checkpoint 场景下的数据正确性。回迁时建议把 1103 + 1104 + 1105 视为一个整体。
  - 多 flink 版本目录需逐目录同步，cherry-pick 时注意各版本基础代码差异（行号、是否已有 FlinkWriteResult 等）。
  - 公共 API 引入需在 release notes 中声明 `@Experimental`。
