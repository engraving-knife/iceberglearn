# 提交 3349：Spark 4.1: Migrate to new version framework in DSv2 (#15240)

## 提交信息

- **序号**：3349 / 4088
- **哈希**：30232d3e727c17e522cae8ce5ae054512f048333
- **短哈希**：30232d3e7
- **日期**：2026-03-06 20:47:16 +0100
- **作者**：Anton Okolnychyi
- **提交说明**：Spark 4.1: Migrate to new version framework in DSv2 (#15240)
- **PR/Issue**：#15240

## 总体目的

Spark 4.1 的 DataSource V2（DSv2）框架引入了新的"version framework"用于时间旅行（time travel）与分支选择。在此前的 Iceberg Spark 集成中，时间旅行和分支选择是通过多种隐式渠道传递的：既可以通过表标识符的后缀选择器（如 `tbl.snapshot_id_123`、`tbl.at_timestamp_123`、`tbl.tag_xxx`、`tbl.branch_xxx`），也可以通过 DataSource 选项 `snapshot-id` / `as-of-timestamp` / `tag` / `branch` 传入，还可以由 `SparkReadConf` 在运行时再解析。这种分散的做法有几个根本性问题：

1. **快照不确定**：表对象在整个查询生命周期里可能被多次刷新，schema / snapshot 可能漂移，导致同一查询中多次访问同一表时读到不一致的状态，缓存失效也难以正确处理。
2. **职责混杂**：`SparkScanBuilder` 既要做选项解析、又要在 `build()` 里根据是否设置了 snapshotId / startSnapshotId 等分支决定构造哪种 scan（batch / incremental / changelog / merge-on-read / copy-on-write），逻辑高度耦合、圈复杂度极高。
3. **与 Spark 新框架不兼容**：Spark 4.1 的 DSv2 把时间旅行显式化——`TableCatalog.loadTable(Identifier, version)` 和 `loadTable(Identifier, long timestampMicros)` 直接由 Spark 调用 `VERSION AS OF` / `TIMESTAMP AS OF` 时触发，旧的"通过选项/标识符后缀"机制变成 deprecated。Iceberg 必须迁移到这一新机制才能正确支持 Spark 4.1。

本提交是一次大规模重构，核心设计是**在表加载时就把 snapshot / branch / schema / timeTravel 钉死（pin）在 `SparkTable` 实例上**，之后所有 scan 构造都直接使用这个已确定的状态，不再依赖运行时从选项中解析。同时把不同 scan 类型的构造职责拆分到独立的 Builder / Scan 类中，移除对旧式选项时间旅行的支持（标记为 legacy 并拒绝使用），并通过新增的分析器规则 `ResolveBranch` 在 Spark 分析阶段确定读写分支。

## 如何达成设计目的

整体设计围绕"加载即定型"展开：

1. **引入 `TimeTravel` sealed interface**（`AsOfVersion` / `AsOfTimestamp` 两个 record）作为时间旅行规格的统一抽象，`SparkCatalog.loadTable(ident, version/timestamp)` 直接构造它并交给 `SparkTable.create`。
2. **重构 `SparkTable`**：持有 `schema`、`snapshot`、`branch`、`timeTravel` 不可变字段，去掉 `refreshEagerly` 与 `snapshotId` 选项解析；不再实现 `SupportsMetadataColumns`（迁移到 `BaseSparkTable` 基类体系）；`equals/hashCode` 用真实状态字段（schema id、snapshotId、branch、timeTravel）保证 Spark 缓存能正确区分不同时间旅行版本的表。
3. **重构 `SparkScanBuilder`**：构造时即接收已 pin 的 `snapshot` / `branch` / `timeTravel`，把 incremental / batch / copy-on-write 三条路径拆成独立方法，changelog 路径整体移到新的 `SparkChangelogScanBuilder`。
4. **拆分 Scan 类**：把原 `SparkBatchQueryScan` 中的运行时过滤逻辑抽到新的 `SparkRuntimeFilterableScan` 基类，新增 `SparkIncrementalAppendScan`，让 batch / incremental / changelog 三类 scan 各自只关心自己的等价性与描述。
5. **新增分析器规则 `ResolveBranch`**：由于当前 DSv2 在初始加载时拿不到全部选项（如 WAP 分支选项），分支最终确定被迫放到一个 post-hoc 分析规则里完成；`ResolveBranch` 处理行级写、批量写、scan 三类计划，必要时调用 `copyWithBranch` 并把分支选择器写回 identifier 以保证后续刷新指向正确分支（注释中引用 SPARK-55842 表示未来 Spark 会提供原生机制）。
6. **清理旧机制**：`SparkReadOptions` 把 `snapshot-id` / `as-of-timestamp` / `tag` 重命名为 `LEGACY_*` 并通过 `Spark3Util.validateNoLegacyTimeTravel` 拒绝使用，引导用户改用 Spark 原生 `VERSION AS OF` / `TIMESTAMP AS OF`；`SparkReadConf` 删除 `snapshotId()` / `asOfTimestamp()` / `branch()` / `tag()`，新增 `incrementalAppendScanBoundaries()`；`SparkCatalog` 移除 `cacheEnabled` 字段与 `REWRITE` 选择器；`IcebergSource` 重写标识符解析逻辑。
7. **更新测试**：删除围绕旧机制设计的 `TestCopyOnWrite{Delete,Merge,Update}`，新增 `TestRepeatedTableAccess`、`TestCachedTableRefresh`、`TestTempViewRefresh`、`TestIncrementallyConstructedQueries` 等聚焦"已 pin 表状态在重复访问 / 缓存 / 视图 / 增量构造查询下行为正确"的测试，并调整大量既有测试以适配新签名。

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/TimeTravel.java` (+64 lines，新增)

**修改目的**：引入时间旅行规格的统一抽象。

**工作逻辑**：sealed interface `TimeTravel` permits `AsOfVersion` / `AsOfTimestamp`。`AsOfVersion(String version)` 表示按版本（snapshot id / branch / tag）回溯，提供 `isSnapshotId()` 判断 version 是否可解析为 long；`AsOfTimestamp(long timestampMicros)` 表示按微秒时间戳回溯，提供 `timestampMillis()`（Iceberg 快照时间戳用毫秒）。工厂方法 `version(...)` / `timestampMicros(...)` / `timestampMillis(...)` 屏蔽构造细节。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkTable.java` (+约 +290/-260)

**修改目的**：把表状态钉死在加载时刻，移除运行时选项解析与 metadata columns 实现。

**工作逻辑**：`SparkTable` 改为继承 `BaseSparkTable`，持有 `schema` / `snapshot` / `branch` / `timeTravel`。构造全部私有化，对外提供 `SparkTable(Table)`、静态工厂 `create(Table, String branch)` 与 `create(Table, TimeTravel)`：后者按 `AsOfVersion.isSnapshotId()` 决定走 snapshot id 路径还是按 `refs()` 解析 branch/tag；`AsOfTimestamp` 通过 `SnapshotUtil.snapshotIdAsOfTime` 解析 snapshot id。`newScanBuilder` 直接用已 pin 的字段构造 `SparkScanBuilder`，`newWriteBuilder` / `newRowLevelOperationBuilder` / `deleteWhere` 均校验 `timeTravel == null`（不允许对时间旅行表写）。`equals/hashCode` 改为按 `name + uuid + schemaId + snapshotId + branch + timeTravel` 比较，使 Spark 缓存能区分不同时间旅行版本。`determineLatestSnapshot` 处理 branch 未创建时回退到 currentSnapshot。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java` (+约 +90/-150)

**修改目的**：把 `loadTable(ident, version/timestamp)` 对接到新 `TimeTravel`，统一标识符后缀解析。

**工作逻辑**：`loadTable(Identifier, String version)` 与 `loadTable(Identifier, long timestampMicros)` 分别构造 `TimeTravel.version(...)` / `TimeTravel.timestampMicros(...)` 后委托给私有 `load(ident, TimeTravel)`。`load` 内部按标识符名称匹配 `changes`（changelog）、`branch_xxx`、以及经 `parseTimeTravelSelector` 解析的 `at_timestamp_xxx` / `snapshot_id_xxx` / `tag_xxx`，并校验"不能同时用选择器和 Spark 时间旅行规格"。`loadPath` 同样重构为基于 `TimeTravel` 与 branch 的二选一逻辑，移除 `cacheEnabled` 字段与 `REWRITE` 选择器，异常改为抛 Spark `NoSuchTableException` 以符合 DSv2 契约。新增 `parseTimeTravelSelector(String)` 把旧式后缀映射成 `TimeTravel`。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java` (+约 +120/-290)

**修改目的**：构造时即 pin 快照，按 scan 类型拆分构造路径。

**工作逻辑**：构造函数接收 `Snapshot snapshot`、`String branch`、`TimeTravel timeTravel`，并在构造时通过 `Spark3Util.containsIncrementalOptions` 判定是否增量扫描并解析 `startSnapshotId`/`endSnapshotId`（经 `readConf().incrementalAppendScanBoundaries()`），同时调用 `validateNoLegacyTimeTravel` 与 `SparkTableUtil.validateReadBranch`。`build()` 改为按是否 `startSnapshotId != null` 分发到 `buildIncrementalAppendScan` 或 `buildBatchScan`；新增 `buildCopyOnWriteScan`、`buildIcebergScanWithStats`、`buildIcebergIncrementalAppendScan`、`buildIcebergBatchScan(projection, ignoreResiduals, withStats)`。`buildIcebergBatchScan` 通过 `shouldPinSnapshot()`（主表或支持时间旅行的 metadata 表）决定是否需要快照，并 `Preconditions.checkState` 校验解析出的 scan 快照与传入 `snapshot` 一致以强制一致性。changelog 构造整体移除，改由 `SparkChangelogScanBuilder` 承担。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkChangelogScanBuilder.java` (+151 lines，新增)

**修改目的**：把 changelog scan 的构造逻辑独立成单独 builder。

**工作逻辑**：从原 `SparkScanBuilder.buildChangelogScan` 抽出，处理 `startSnapshotId/endSnapshotId` 与 `startTimestamp/endTimestamp` 的互斥与解析（`getStartSnapshotId` / `getEndSnapshotId`），支持空 changelog scan，最终构造 `SparkChangelogScan`。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkRuntimeFilterableScan.java` (+200 lines，新增)

**修改目的**：把运行时分区过滤（`SupportsRuntimeV2Filtering`）抽到可复用基类。

**工作逻辑**：抽出 `filterAttributes()`（按投影中的分区字段构造 `NamedReference`）、`filter(Predicate[])`（用 `Evaluator` 按分区过滤 task）、`rewritableDeletes(forDVs)` 与 `shouldRewrite`（删除文件重写判定）等通用逻辑，供 `SparkBatchQueryScan`、`SparkIncrementalAppendScan` 继承。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkIncrementalAppendScan.java` (+95 lines，新增)

**修改目的**：增量 append scan 的独立实现。

**工作逻辑**：继承 `SparkRuntimeFilterableScan`，持有 `startSnapshotId` / `endSnapshotId`，重写 `equals/hashCode/description` 以反映增量扫描边界。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkBatchQueryScan.java` (+约 +20/-200)

**修改目的**：精简为仅持有已 pin 的 `snapshot` / `branch`。

**工作逻辑**：继承 `SparkRuntimeFilterableScan`（取代原 `SparkPartitioningAwareScan` + `SupportsRuntimeV2Filtering`），构造函数增加 `Schema` / `Snapshot` / `String branch` 参数，移除原先从 `readConf` 读取 `snapshotId/startSnapshotId/endSnapshotId/asOfTimestamp/tag` 的字段，`snapshotId()` 改为返回 `snapshot.snapshotId()`。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkReadConf.java` (+约 +30/-20)

**修改目的**：移除旧式时间旅行读取方法，新增增量边界解析。

**工作逻辑**：删除 `snapshotId()` / `asOfTimestamp()` / `branch()` / `tag()`；新增 `incrementalAppendScanBoundaries()` 返回 `Pair<Long, Long>`，校验 `startTimestamp/endTimestamp` 不允许用于增量扫描、且 `startSnapshotId` 必须存在。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkReadOptions.java` (+约 +10/-10)

**修改目的**：把旧式时间旅行选项标记为 legacy。

**工作逻辑**：`SNAPSHOT_ID` / `AS_OF_TIMESTAMP` / `TAG` 改名为 `LEGACY_SNAPSHOT_ID` / `LEGACY_AS_OF_TIMESTAMP` / `LEGACY_TAG`，保留 `BRANCH`、`START_SNAPSHOT_ID` / `END_SNAPSHOT_ID` / `START_TIMESTAMP` / `END_TIMESTAMP`。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/Spark3Util.java` (+约 +30/-15)

**修改目的**：新增 legacy 时间旅行校验、增量选项判定与 `tableCatalog()` 访问器。

**工作逻辑**：新增 `containsIncrementalOptions(CaseInsensitiveStringMap)`（检测是否设置 start/end snapshot id）、`validateNoLegacyTimeTravel(options)`（拒绝 `LEGACY_SNAPSHOT_ID` / `LEGACY_AS_OF_TIMESTAMP` / `LEGACY_TAG`，提示改用 Spark 原生 `VERSION AS OF` / `TIMESTAMP AS OF`）、`CatalogAndIdentifier.tableCatalog()`（断言 catalog 为 `TableCatalog` 并返回），并把 `baseTableUUID` 由包级改为 `public`。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/IcebergSource.java` (+约 +60/-70)

**修改目的**：重写基于 `TableCatalog` 的标识符解析，简化分支选择器处理。

**工作逻辑**：`getTable` 委托给新 `loadTable`；`catalogAndIdentifier` 调用 `validateNoLegacyTimeTravel`，仅保留 `branch` 作为选择器（移除 snapshotId / asOfTimestamp / tag 处理），用 `CatalogAndIdentifier.tableCatalog()` 直接加载；区分 path（`/`）走默认 catalog + `PathIdentifier`、否则走 `resolveIdentifier`，并对非 Iceberg 的 session catalog 回退到默认 Iceberg catalog。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/BaseSparkScanBuilder.java` (+约 +5/-15)

**修改目的**：移除 branch 重载，简化构造。

**工作逻辑**：删除带 `branch` 的构造重载，统一用 `new SparkReadConf(spark, table, options)`（无 branch）。

### `core/src/main/java/org/apache/iceberg/BaseMetadataTable.java` (+16 lines)

**修改目的**：声明 metadata 表是否支持时间旅行。

**工作逻辑**：新增 `TIME_TRAVEL_TABLE_TYPES` 集合（`ENTRIES` / `FILES` / `DATA_FILES` / `DELETE_FILES` / `MANIFESTS` / `PARTITIONS` / `POSITION_DELETES`）与 `supportsTimeTravel()` 方法，供 `SparkScanBuilder.shouldPinSnapshot()` 判断是否需要对 metadata 表 pin 快照。

### `spark/v4.1/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/ResolveBranch.scala` (+119 lines，新增)

**修改目的**：在分析阶段确定读写分支。

**工作逻辑**：post-hoc 解析规则，处理三类计划：行级写（`IcebergRowLevelWrite` 抽取 `SparkTable` + operation + options，按 `determineWriteBranch` 用 `copyWithBranch` 重建 `RowLevelOperationTable` 并替换 target/query）；批量写（`V2WriteCommand`，对 `DataSourceV2Relation` 中的 `SparkTable` 按选项确定写分支并 `copyWithBranch`）；scan（对 `DataSourceV2Relation` 按 `determineReadBranch` 确定读分支并 `copyWithBranch`，同时把 `branch_xxx` 写回 identifier 以保证后续刷新指向正确分支）。注释引用 SPARK-55842，说明这是临时方案，未来 Spark 会原生支持。

### `spark/v4.1/spark-extensions/src/main/scala/org/apache/iceberg/spark/extensions/IcebergSparkSessionExtensions.scala` (+2 lines)

**修改目的**：注册 `ResolveBranch` 规则。

**工作逻辑**：`extensions.injectPostHocResolutionRule { spark => ResolveBranch(spark) }`。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java` (+约 +25/-5)

**修改目的**：提供读写分支校验与 `SparkTable` 重载。

**工作逻辑**：新增 `validateReadBranch` / `validateWriteBranch`（内部 `validateBranch` 要求 branch 与选项一致或启用 Iceberg extensions），新增 `determineWriteBranch(spark, SparkTable, options)` 与 `determineReadBranch(spark, SparkTable, options)` 重载；`loadTable(snapshotId)` 改用 `TimeTravel.version` + `SparkTable.create`，`loadMetadataTable` 改用 `new SparkTable(metadataTable)`；`wapEnabled` 收窄为 `private`。

### 其余较小改动

- **`SparkSessionCatalog.java`** (+约 +2/-2)：适配 `loadTable` 签名变化。
- **`SparkTableUtil.java`** 之外，`SparkWrite.java` / `SparkWriteBuilder.java` / `SparkCopyOnWriteOperation.java` / `SparkCopyOnWriteScan.java` / `SparkPositionDeltaWrite.java` / `SparkPositionDeltaWriteBuilder.java` / `SparkRewriteWriteBuilder.java` / `SparkRowLevelOperationBuilder.java` / `SparkScan.java` / `SparkStagedScan.java` / `SparkStagedScanBuilder.java` / `StagedSparkTable.java` / `SparkChangelogScan.java` / `SparkChangelogTable.java` / `SparkPartitioningAwareScan.java` / `SparkWriteConf.java` 等：均为适配新 `SparkTable` 构造（无 `refreshEagerly`、改用已 pin 的 `snapshot`/`branch`）、移除 branch 选项透传等小幅调整。`SparkChangelogTable` 改用 `SparkChangelogScanBuilder` 并去掉 `refreshEagerly`。
- **测试调整**：新增 `TestRepeatedTableAccess`、`TestCachedTableRefresh`、`TestTempViewRefresh`、`TestIncrementallyConstructedQueries`，验证 pin 表状态在重复访问、缓存、视图、增量构造查询下的正确性；删除围绕旧机制的 `TestCopyOnWriteDelete/Merge/Update`；`ExtensionsTestBase` 新增 33 行辅助；其余如 `TestSnapshotSelection`、`TestDataSourceOptions`、`TestFilteredScan`、`TestSparkCatalogCacheExpiration`、`TestSparkScan`、`TestSelect`、`TestRemoveOrphanFilesAction` 等按新签名/选项名调整。

## 总结

本提交是 Spark 4.1 集成的一次关键重构，把时间旅行与分支选择从"分散在选项与标识符后缀中、运行时解析"迁移到"加载即 pin、由 Spark 原生 version framework 驱动"。核心价值有三：让表状态在查询全生命周期内一致（修复缓存与重复访问的正确性）；让 scan 构造职责清晰分离（changelog/incremental/batch 各自独立 builder 与 scan 类）；让 Iceberg 与 Spark 4.1 的 DSv2 新框架对齐，弃用 legacy 选项并提示用户迁移到原生 `VERSION/TIMESTAMP AS OF`。同时通过 `ResolveBranch` 分析规则临时补足当前 DSv2 无法在初始加载拿到全部选项的缺口，为后续原生机制（SPARK-55842）落地铺路。
