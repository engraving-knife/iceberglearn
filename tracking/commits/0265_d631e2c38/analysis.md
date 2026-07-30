# 提交 0265：Core, Spark: Avoid manifest copies when importing data to V2 tables (#8962)

## 提交信息

- **序号**：0265 / 4088
- **哈希**：d631e2c3858b29c79acf00406a851590031acabd
- **短哈希**：d631e2c38
- **日期**：2023-12-13 08:49:15 +0100
- **作者**：Anton Okolnychyi
- **提交说明**：Core, Spark: Avoid manifest copies when importing data to V2 tables (#8962)
- **PR/Issue**：#8962

## 总体目的

Iceberg 在通过 `appendManifest`（如 Spark `add_files` 存储过程导入外部数据文件）向表中追加已有 manifest 时，需要决定是直接复用该 manifest，还是把它"重写"一遍以便给所有 manifest entry 统一打上本次提交的 snapshot ID。重写 manifest 会产生一次完整的 manifest 文件拷贝（读旧 manifest + 写新 manifest），在导入大量数据文件时是明显的 I/O 与元数据开销。

Iceberg 的 snapshot ID inheritance 机制就是为避免这种拷贝而设计的：当 inheritance 启用时，manifest entry 可以不携带 snapshot ID（`snapshotId == null`），在读取阶段再按需补全，这样 manifest 就能被直接复用。然而在 V2 表（`format-version=2`）上存在一个历史遗留问题：`FastAppend` 和 `MergingSnapshotProducer` 各自持有一个 `snapshotIdInheritanceEnabled` 字段，该字段**只**读取表属性 `compatibility.snapshot-id-inheritance.enabled`，**不**感知 format version。虽然 `SnapshotProducer` 基类中已经引入了 `canInheritSnapshotId()` 方法（封装 `formatVersion > 1 || snapshotIdInheritanceEnabled` 的语义，V2 表默认启用 inheritance），但 `FastAppend` 和 `MergingSnapshotProducer` 仍用自己那个只看属性的字段做判断，导致 V2 表在导入数据时仍然会重写 manifest——这与 V2 表的设计意图相悖。

本提交的目的就是消除这一不一致：让 `FastAppend` 和 `MergingSnapshotProducer` 改用基类的 `canInheritSnapshotId()` 方法，从而使 V2 表在 `appendManifest` 时直接复用 manifest，避免不必要的 manifest 拷贝。配套地，Spark 侧的 `SparkTableUtil`（`add_files` 存储过程底层实现）也需要调整 manifest 删除策略——V2 表上 manifest 被直接复用为表元数据的一部分，提交后不能被删除；`AddFilesProcedure` 的 `changed_partition_count` 输出列改为可空，因为 V2 表在 inheritance 启用时不计算该指标。

## 如何达成设计目的

整体设计思路是"统一 snapshot ID inheritance 的判断入口"。核心改动是把 `FastAppend` 和 `MergingSnapshotProducer` 中各自维护的 `snapshotIdInheritanceEnabled` 字段（仅看表属性）替换为对基类 `SnapshotProducer.canInheritSnapshotId()` 方法的调用（看 `formatVersion > 1 || snapshotIdInheritanceEnabled`）。这样 V2 表（`format-version=2`）在 `appendManifest` 时，`canInheritSnapshotId()` 返回 `true`，且如果传入的 manifest `snapshotId() == null`，就直接把 manifest 加入新快照，不再走 `copyManifest` 重写路径。Spark 侧的 `SparkTableUtil.importUnpartitionedSparkTable` / `importSparkPartitions` 等方法在 commit 后清理原始 manifest 时，增加 `formatVersion == 1` 条件——只有 V1 表且未启用 inheritance 时才删除（因为只有这种情况下 manifest 被重写过，原始 manifest 是临时的）；V2 表上 manifest 已被直接复用为表元数据，不能删。`AddFilesProcedure` 的输出 schema 中 `changed_partition_count` 改为 nullable，因为 V2 表的快照 summary 中该字段可能不存在（inheritance 启用时不计算分区变更计数）。测试侧大量增加按 `formatVersion` 分支的断言，验证 V1 重写、V2 直接复用的行为差异。

## 修改详情

### `api/src/main/java/org/apache/iceberg/AppendFiles.java`

**修改目的**：更新 `appendManifest` 的 Javadoc，准确反映 V2 表默认直接复用 manifest 的新行为。

**工作逻辑**：原文档说"By default, the manifest will be rewritten to assign all entries this update's snapshot ID"，新文档改为"The manifest will be used directly if snapshot ID inheritance is enabled (all tables with the format version > 1 or if the inheritance is enabled explicitly via table properties). Otherwise, the manifest will be rewritten..."。同时把后续关于 manifest 生命周期的说明按"rewritten"与"used directly"两种情况重新组织，让调用方清楚：直接复用时 manifest 成为表元数据一部分不可手动删除；重写时原始 manifest 由调用方管理。

### `core/src/main/java/org/apache/iceberg/FastAppend.java`

**修改目的**：让 `FastAppend` 使用基类的 `canInheritSnapshotId()` 而非自己的 `snapshotIdInheritanceEnabled` 字段，使 V2 表在 `appendManifest` 时直接复用 manifest。

**工作逻辑**：

- 删除 `private final boolean snapshotIdInheritanceEnabled` 字段及其在构造方法中的初始化（原先通过 `ops.current().propertyAsBoolean(SNAPSHOT_ID_INHERITANCE_ENABLED, SNAPSHOT_ID_INHERITANCE_ENABLED_DEFAULT)` 赋值，只看表属性），同时删除对应的两个 static import。
- `appendManifest(ManifestFile)` 中把 `if (snapshotIdInheritanceEnabled && manifest.snapshotId() == null)` 改为 `if (canInheritSnapshotId() && manifest.snapshotId() == null)`。`canInheritSnapshotId()` 是从父类 `SnapshotProducer` 继承的 `protected` 方法，返回 `formatVersion > 1 || snapshotIdInheritanceEnabled`。这样 V2 表（`format-version=2`）即使没有显式设置 `compatibility.snapshot-id-inheritance.enabled=true`，也会走"直接复用 manifest"分支（`appendManifests.add(manifest)`），而非"重写 manifest"分支（`rewrittenAppendManifests.add(copyManifest(manifest))`）。

### `core/src/main/java/org/apache/iceberg/MergingSnapshotProducer.java`

**修改目的**：与 `FastAppend` 对齐，让 `MergingSnapshotProducer` 也使用 `canInheritSnapshotId()`，使 V2 表在 `add(ManifestFile)` 时直接复用 manifest。

**工作逻辑**：

- 删除 `private final boolean snapshotIdInheritanceEnabled` 字段及其在构造方法中的初始化，同时删除对应的两个 static import。
- `add(ManifestFile manifest)` 中把 `if (snapshotIdInheritanceEnabled && manifest.snapshotId() == null)` 改为 `if (canInheritSnapshotId() && manifest.snapshotId() == null)`。该方法被 `MergeAppend`、`OverwriteData`、`RowDelta` 等多个继承自 `MergingSnapshotProducer` 的写操作使用，改动覆盖面广。

### `core/src/test/java/org/apache/iceberg/TestFastAppend.java`

**修改目的**：验证 V1 表重写 manifest、V2 表直接复用 manifest 的行为差异。

**工作逻辑**：

- 多处增加按 `formatVersion` 分支的断言。例如 `testAppendManifest` 中，提交后取 `snap.allManifests(FILE_IO)` 的唯一 manifest，断言：V1 表时 `committedManifest.path()` 不等于原始 `manifest.path()`（被重写）；V2 表时二者相等（直接复用）。
- `testAppendManifestCleanup`（commit 失败后清理）：V1 表时断言新 manifest 文件不存在（被清理）；V2 表时断言新 manifest 文件仍存在（因为是原始 manifest，不是临时拷贝，不应被清理）。
- `testAppendManifestWithRetry`、`testEmptyAppend` 等用例同步增加路径相等性断言。
- `testAppendManifestWithSnapshotIdInheritance` 中直接断言 `committedManifest.path()` 等于 `manifest.path()`。

### `core/src/test/java/org/apache/iceberg/TestMergeAppend.java`

**修改目的**：与 `TestFastAppend` 类似，验证 `MergeAppend`（基于 `MergingSnapshotProducer`）在 V1/V2 下的 manifest 行为差异。

**工作逻辑**：

- `testAppendMinMergeCount`、`testAppendUnpartitioned`、`testMergedAppendRewriteManifests`、`testAppendManifestWithRewrite`、`testAppendManifestCleanup` 等多处增加按 `formatVersion` 分支的断言：V1 表时 manifest path 不等（重写），V2 表时 path 相等（直接复用）。
- `testAppendManifestWithSnapshotIdInheritance` 中显式断言 `manifestFile.path()` 等于 `manifest.path()`。
- 在涉及多轮提交的 `testMergedAppendRewriteManifests` 中，第二轮提交前重新生成 manifest（`writeManifestWithName("FILE_A_S2", FILE_A)` 等），因为旧 manifest 可能在第一轮被 compaction 合并掉，避免路径比较失效。
- `testAppendManifestCleanup` 中 V1 表断言 manifest 被清理，V2 表断言 manifest 仍存在。

### `core/src/test/java/org/apache/iceberg/TestTransaction.java`

**修改目的**：把一个假设 manifest 被重写的测试限定为 V1 专用。

**工作逻辑**：`testTransactionRetryAndAppendManifests` 重命名为 `testTransactionRetryAndAppendManifestsWithoutSnapshotIdInheritance`，并加 `Assumptions.assumeThat(formatVersion).isEqualTo(1)` 跳过 V2 表。因为该测试假设 append manifest 会被重写（V1 行为），在 V2 表上 manifest 不被重写，测试前提不成立。

### `spark/v3.2/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java`、`spark/v3.3/.../SparkTableUtil.java`、`spark/v3.4/.../SparkTableUtil.java`、`spark/v3.5/.../SparkTableUtil.java`

**修改目的**：调整 `add_files` 导入后原始 manifest 的删除策略，V2 表上不删除（manifest 已被直接复用为表元数据）。

**工作逻辑**：

- 在 `importUnpartitionedSparkTable` / `importSparkPartitions` 等方法的 commit 后清理逻辑中，新增 `TableOperations ops = ((HasTableOperations) targetTable).operations(); int formatVersion = ops.current().formatVersion();`。
- 把原来的 `if (!snapshotIdInheritanceEnabled) { deleteManifests(...); }` 改为 `if (formatVersion == 1 && !snapshotIdInheritanceEnabled) { deleteManifests(...); }`。也就是说：
  - V1 表且未启用 inheritance：manifest 被重写，原始 manifest 是临时的，删除。
  - V1 表且启用 inheritance：manifest 被直接复用，不删。
  - V2 表（不论属性）：manifest 被直接复用，不删。
- 逻辑与 core 侧 `canInheritSnapshotId()` 的语义对齐：只有 `canInheritSnapshotId()` 为 false（即 V1 表且未启用 inheritance）时才需要删除原始 manifest。

### `spark/v3.3/.../procedures/AddFilesProcedure.java`、`spark/v3.4/.../AddFilesProcedure.java`、`spark/v3.5/.../AddFilesProcedure.java`

**修改目的**：把 `changed_partition_count` 输出列改为可空，匹配 V2 表的 summary 行为。

**工作逻辑**：

- 输出 schema 中 `changed_partition_count` 字段的 `nullable` 从 `false` 改为 `true`。
- `toOutputRows(Snapshot snapshot)` 重构：把原来 `Long.parseLong(summary.getOrDefault(SnapshotSummary.CHANGED_PARTITION_COUNT_PROP, "0"))` 改为调用新方法 `changedPartitionCount(summary)`，后者用 `PropertyUtil.propertyAsNullableLong(stats, SnapshotSummary.CHANGED_PARTITION_COUNT_PROP)` 读取——属性不存在时返回 `null` 而非 `0L`。
- 新增 `addedFilesCount(Map<String, String> stats)`（用 `PropertyUtil.propertyAsLong(..., 0L)`）与 `changedPartitionCount(Map<String, String> stats)`（用 `propertyAsNullableLong`）两个私有方法，统一属性读取。
- 这是因为 V2 表在 inheritance 启用时（即默认情况），`appendManifest` 走直接复用路径，不会计算 `changed-partition-count` 并写入 summary；V1 表则会计算。所以该列在 V2 表上可能为 null。

### `spark/v3.3/spark-extensions/src/test/java/.../TestAddFilesProcedure.java`、`spark/v3.4/.../TestAddFilesProcedure.java`、`spark/v3.5/.../TestAddFilesProcedure.java`

**修改目的**：让 `add_files` 存储过程测试覆盖 V1 与 V2 两种 format version，验证 manifest 复用与 `changed_partition_count` 可空行为。

**工作逻辑**：

- `@Parameters` 参数化方法扩展：原来每个 catalog config 一组参数，现在加第 4 个参数 `formatVersion`。Hive catalog 用 V1，Hadoop 与 Spark catalog 用 V2，从而在同一套测试中覆盖两种格式版本。
- 构造方法增加 `int formatVersion` 参数与字段。
- 新增 `createIcebergTable(String schema)` 与 `createIcebergTable(String schema, String partitioning)` 辅助方法：在 `CREATE TABLE` 时附加 `TBLPROPERTIES ('format-version' '<formatVersion>')`，确保表按预期版本创建。
- 新增 `assertOutput(List<Object[]> result, long expectedAddedFilesCount, long expectedChangedPartitionCount)` 辅助方法：断言 `added_files_count` 等于期望值；对于 `changed_partition_count`，V1 表断言等于期望值，V2 表断言 `isIn(expectedChangedPartitionCount, null)`（即要么是期望值，要么是 null，因为 V2 表可能不计算该指标）。
- 所有测试用例中的 `sql(createIceberg, tableName)` 替换为 `createIcebergTable(...)` 调用，`assertEquals(..., row(2L, 1L))` 替换为 `assertOutput(result, 2L, 1L)`。
- 新增 import：`assertThat`、`Iterables`、`SparkCatalogConfig`、`Parameters`。

### `docs/spark-procedures.md`

**修改目的**：更新 `add_files` 存储过程文档，反映 `changed_partition_count` 在 V2 表上为 NULL 而非 0。

**工作逻辑**：

- `changed_partition_count` 描述从"The number of partitioned changed by this command"改为"The number of partitioned changed by this command (if known)"。
- 提示框内容从"changed_partition_count will be 0 when..."改为"changed_partition_count will be NULL when table property `compatibility.snapshot-id-inheritance.enabled` is set to true or if the table format version is > 1."。

## 小结

本提交通过让 `FastAppend` 和 `MergingSnapshotProducer` 改用基类的 `canInheritSnapshotId()` 方法（封装 `formatVersion > 1 || snapshotIdInheritanceEnabled` 语义），使 V2 表在通过 `appendManifest` 导入数据时直接复用 manifest 而不产生 manifest 拷贝，并同步调整 Spark `add_files` 存储过程的 manifest 清理策略与 `changed_partition_count` 输出列的可空性，显著降低了 V2 表数据导入的 I/O 与元数据开销，是 Iceberg V2 表写入路径性能优化的重要一环。
