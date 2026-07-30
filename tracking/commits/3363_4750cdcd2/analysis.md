# 提交 3363：API, CORE, Flink, Spark: Deprecate Snapshot.changes Methods with SnapshotChange Utility (#15241)

## 提交信息

- **序号**：3363 / 4088
- **哈希**：4750cdcd2d16e09d0a1731d5bc0a5ccd9ca253d3
- **短哈希**：4750cdcd2
- **日期**：2026-03-09
- **作者**：Russell Spitzer
- **提交说明**：API, CORE, Flink, Spark: Deprecate Snapshot.changes Methods with SnapshotChange Utility (#15241)
- **PR/Issue**：#15241

## 总体目的

该提交旨在用一个新的 `SnapshotChanges` 工具类替代 `Snapshot` 接口上现有的文件变更查询方法（`addedDataFiles`、`removedDataFiles`、`addedDeleteFiles`、`removedDeleteFiles`），并将原方法标记为 `@Deprecated`（计划在 2.0.0 移除）。

原有设计存在一个关键性能问题：`Snapshot` 接口上的这四个方法每次调用都会重新读取 manifest 文件并遍历条目来计算文件变更。当同一个快照需要查询多种变更（例如同时需要新增的数据文件和新增的删除文件）时，每次调用都独立扫描 manifest，导致重复的 I/O 和计算开销。此外，这些方法分散在接口上，缺乏统一的缓存机制。

新的 `SnapshotChanges` 工具类采用构建器模式（`SnapshotChanges.builderFor(table)`），在一个实例中缓存所有四种文件变更的查询结果。首次查询某类变更时读取 manifest 并缓存到内部列表，后续查询（包括同一对象上的其他变更类型查询）可直接复用已缓存的数据。例如 `CherryPickOperation` 中同时需要 `addedDataFiles` 和 `removedDataFiles`，使用 `SnapshotChanges` 后 manifest 只需读取一次。该类还支持通过 `executeWith(ExecutorService)` 配置并行 manifest 读取，默认为单线程。

本次改动范围广泛，涉及 API 层（标记弃用）、Core 层（新增工具类及内部调用方迁移）、Flink 层（v1.20/v2.0/v2.1 的 `TableChange` 和 `MonitorSource`）、Spark 层（v3.4/v3.5/v4.0/v4.1 的 `SparkMicroBatchStream`），以及大量测试文件迁移，共 56 个文件。

## 如何达成设计目的

整体设计分四步：

1. **API 层**：在 `Snapshot` 接口的四个变更查询方法上添加 `@Deprecated` 注解和 Javadoc，指向 `SnapshotChanges#builderFor(Table)`。
2. **Core 层**：新增 `SnapshotChanges` 类，内部持有快照、FileIO、分区规格映射和可选 ExecutorService，通过懒加载 + 缓存机制提供四种变更查询。提供 `builderFor(Table)`（基于表当前快照）和包级 `builderFor(Snapshot, FileIO, Map)` 两种构建入口，Builder 支持覆盖快照和设置并行执行器。
3. **内部调用方迁移**：将 `CherryPickOperation`、`MicroBatches`、Flink 的 `TableChange`/`MonitorSource`、Spark 的 `SparkMicroBatchStream` 等生产代码从调用 `Snapshot` 上的方法改为使用 `SnapshotChanges`。
4. **测试迁移**：将 Core/Flink/Spark 各版本的测试中对 `snapshot.addedDataFiles(io)` 等的调用替换为 `SnapshotChanges.builderFor(table).snapshot(snapshot).build().addedDataFiles()`，并新增 `TestSnapshotChanges` 专门验证新工具类的缓存行为。

## 修改详情

### `api/src/main/java/org/apache/iceberg/Snapshot.java` (+8/-0 lines)

**修改目的**：将四个文件变更查询方法标记为弃用。

**工作逻辑**：
为 `addedDataFiles(FileIO)`、`removedDataFiles(FileIO)`、`addedDeleteFiles(FileIO)`、`removedDeleteFiles(FileIO)` 四个方法分别添加 `@Deprecated` 注解和 Javadoc `@deprecated will be removed in 2.0.0; use SnapshotChanges#builderFor(Table) instead`。方法体未改动。

### `core/src/main/java/org/apache/iceberg/SnapshotChanges.java` (+271/-0 lines, 新文件)

**修改目的**：提供带缓存的快照文件变更查询工具类。

**工作逻辑**：
- 核心字段：`Snapshot snapshot`、`FileIO io`、`Map<Integer, PartitionSpec> specsById`、`ExecutorService executorService`，以及四个缓存列表（`addedDataFiles`、`removedDataFiles`、`addedDeleteFiles`、`removedDeleteFiles`，初始为 null）。
- 构建入口：`builderFor(Table table)` 基于 `table.currentSnapshot()`/`table.io()`/`table.specs()`；包级 `builderFor(Snapshot, FileIO, Map)` 用于指定快照。
- 查询方法：`addedDataFiles()`、`removedDataFiles()`、`addedDeleteFiles()`、`removedDeleteFiles()`。每个方法在缓存为 null 时触发对应的 `cacheDataFileChanges()` 或 `cacheDeleteFileChanges()`，之后返回缓存列表。多次调用返回同一引用（`isSameAs`）。
- `cacheDataFileChanges()`：过滤 `snapshotId` 匹配当前快照的数据 manifest，通过 `readDataManifest` 读取条目并按 `ManifestEntry.Status`（ADDED/DELETED）分桶，EXISTING 被过滤。ADDED 文件调用 `copy()`（保留统计），DELETED 调用 `copyWithoutStats()`（节省内存）。使用 `iterate()` 方法根据是否配置 ExecutorService 选择 `ParallelIterable`（并行）或 `CloseableIterable.concat`（串行）。
- `cacheDeleteFileChanges()`：逻辑同上，针对删除文件 manifest。
- `Builder`：支持 `snapshot(Snapshot)` 覆盖默认快照、`executeWith(ExecutorService)` 设置并行执行器、`build()` 构建实例。

### `core/src/main/java/org/apache/iceberg/CherryPickOperation.java` (+9/-4 lines)

**修改目的**：将 cherry-pick 操作中的文件变更查询迁移到 `SnapshotChanges`。

**工作逻辑**：
- 移除 `specsById` 字段及其初始化（改用 `current.specsById()`）。
- 在 `apply()` 中构建 `SnapshotChanges changes = SnapshotChanges.builderFor(cherrypickSnapshot, ops().io(), current.specsById()).build()`。
- 将三处 `cherrypickSnapshot.addedDataFiles(io)` 和一处 `cherrypickSnapshot.removedDataFiles(io)` 替换为 `changes.addedDataFiles()` 和 `changes.removedDataFiles()`。由于同一 `changes` 对象被复用，manifest 只读取一次。

### `core/src/main/java/org/apache/iceberg/MicroBatches.java` (+2/-1 lines)

**修改目的**：迁移微批生成中的新增文件计数。

**工作逻辑**：
将 `Iterables.size(snapshot.addedDataFiles(io))` 替换为 `Iterables.size(SnapshotChanges.builderFor(snapshot, io, specsById).build().addedDataFiles())`。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TableChange.java` (+8/-3 lines)

**修改目的**：将 Flink 维护事件的文件变更采集迁移到 `SnapshotChanges`。

**工作逻辑**：
- 构造函数从 `TableChange(Snapshot snapshot, FileIO io)` 改为 `TableChange(Snapshot snapshot, Table table)`，内部调用 `SnapshotChanges.builderFor(table).snapshot(snapshot).build()` 并通过私有构造函数 `TableChange(SnapshotChanges changes)` 取 `changes.addedDataFiles()` 和 `changes.addedDeleteFiles()`。这样一次 manifest 读取即可同时获取数据文件和删除文件变更。
- import 从 `FileIO` 改为 `SnapshotChanges` + `Table`。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/MonitorSource.java` (+1/-1 lines)

**修改目的**：适配 `TableChange` 构造签名变更。

**工作逻辑**：
将 `new TableChange(snapshot, table.io())` 改为 `new TableChange(snapshot, table)`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkMicroBatchStream.java` (+3/-1 lines)

**修改目的**：迁移 Spark 微批流的新增文件计数。

**工作逻辑**：
在 `latestFilesCount` 中，将 `Iterables.size(snapshot.addedDataFiles(table.io()))` 替换为 `Iterables.size(SnapshotChanges.builderFor(table).snapshot(snapshot).build().addedDataFiles())`。

### `core/src/test/java/org/apache/iceberg/TestSnapshotChanges.java` (+149/-0 lines, 新文件)

**修改目的**：专门测试 `SnapshotChanges` 的查询正确性和缓存行为。

**工作逻辑**：
包含三个测试：
- `testAddedDataFiles`：验证新增数据文件查询返回正确文件。
- `testRemovedDataFiles`：验证删除数据文件查询正确，且两次调用返回同一引用（缓存生效）。
- `testSnapshotChangesCaching`：验证先查 `removedDataFiles` 再查时缓存命中（`isSameAs`）。

### Core 测试文件迁移

- `core/src/test/java/org/apache/iceberg/TestDeleteFiles.java` (+2/-1)：`delete2.removedDataFiles(FILE_IO)` → `SnapshotChanges.builderFor(table).snapshot(delete2).build().removedDataFiles()`。
- `core/src/test/java/org/apache/iceberg/TestRemoveSnapshots.java` (+14/-7)：4 处 `i.addedDataFiles(table.io())` → `SnapshotChanges.builderFor(table).snapshot(i).build().addedDataFiles()`。
- `core/src/test/java/org/apache/iceberg/TestSnapshot.java` (+13/-8)：多处迁移，使用同一 `changes` 对象复用缓存。
- `core/src/test/java/org/apache/iceberg/TestSnapshotManager.java` (+6/-3)：4 个方法调用合并为创建一个 `changes` 对象后分别查询。
- `core/src/test/java/org/apache/iceberg/TestSnapshotSelection.java` (+2/-1)：迁移新增文件查询。
- `core/src/test/java/org/apache/iceberg/TestWapWorkflow.java` (+68/-9)：11 处 `addedDataFiles` 调用迁移为 `SnapshotChanges.builderFor(table).snapshot(base.currentSnapshot()).build().addedDataFiles()`。
- `core/src/test/java/org/apache/iceberg/hadoop/TestCatalogUtilDropTable.java` (+12/-6)：`dataLocations` 方法签名从 `(Set<Snapshot>, FileIO)` 改为 `(Set<Snapshot>, Table)`，内部使用 `SnapshotChanges` 和 `Streams.stream`。

### Spark 测试文件迁移（v3.4/v3.5/v4.0/v4.1 各版本相同模式）

每个 Spark 版本下以下 6 个测试文件做相同迁移：
- `spark-extensions/.../TestBranchDDL.java` (+8/-5)：将 `addedDataFiles`/`removedDataFiles` 迁移为 `SnapshotChanges`，但 `addedDeleteFiles`/`removedDeleteFiles` 暂保留旧 API（部分迁移）。
- `spark-extensions/.../TestMerge.java` (+2/-1)：迁移新增文件查询。
- `spark-extensions/.../TestUpdate.java` (+3/-2)：迁移新增/删除文件查询。
- `spark/source/SparkMicroBatchStream.java`（见上文主代码）。
- `spark/actions/TestRewriteDataFilesAction.java` (+2/-1)：迁移新增文件流式处理。
- `spark/source/TestStructuredStreamingRead3.java` (+2/-1)：迁移流式读取中的新增文件查询。

4 个 Spark 版本共 24 个测试文件 + 4 个主代码文件。

### Flink 测试文件迁移（v1.20/v2.0/v2.1 各版本相同模式）

每个 Flink 版本下以下 5 个测试文件做相同迁移：
- `maintenance/operator/MonitorSource.java`（见上文主代码）。
- `maintenance/operator/TableChange.java`（见上文主代码）。
- `TestFlinkTableSinkExtended.java` (+11/-6)：将过滤快照和收集新增文件的 `snapshot.addedDataFiles(table.io())` 迁移为 `SnapshotChanges`。
- `sink/TestFlinkIcebergSinkDistributionMode.java` (+55/-19)：多处迁移。
- `sink/TestFlinkIcebergSinkRangeDistributionBucketing.java` (+12/-5)：迁移。
- `sink/TestFlinkIcebergSinkV2DistributionMode.java` (+55/-19)：迁移。
- `sink/dynamic/TestDynamicIcebergSinkPerf.java` (+3/-1)：迁移。

3 个 Flink 版本共约 21 个文件。

## 总结

本次提交引入 `SnapshotChanges` 工具类，通过懒加载 + 缓存机制解决了 `Snapshot` 接口上文件变更查询方法每次调用都重复读取 manifest 的性能问题。将原方法标记为 `@Deprecated`（2.0.0 移除），并将跨 API/Core/Flink/Spark 四层、覆盖 v1.20–v4.1 多个版本的生产代码和测试全面迁移到新工具类。这是 Iceberg 在 API 演进和性能优化方面的重要一步，既保持了向后兼容（弃用而非删除），又为后续 2.0 清理和性能提升铺平道路。
