# 提交 0166：Core: Enable column statistics filtering after planning (#8803)

## 提交信息

- **序号**：0166 / 4088
- **哈希**：6ec3de390d3fa6e797c6975b1eaaea41719db0fe
- **短哈希**：6ec3de390
- **日期**：2023-11-14
- **作者**：pvary
- **提交说明**：Core: Enable column statistics filtering after planning (#8803)
- **PR/Issue**：#8803

## 总体目的

在 Iceberg 1.4.x 之前的实现中，`Scan.includeColumnStats()` 是一个“全有或全无”的开关：调用后扫描返回的数据文件（`DataFile`/`DeleteFile`）会携带全部列的统计信息（value count、null value count、nan value count、column size、lower bounds、upper bounds）。这些统计信息在引擎侧主要用于行级过滤（如动态过滤、运行时裁剪），但绝大多数查询只会用到其中很少的几列，而 manifest 文件本身虽然只读取了所需列，但整份 manifest 加载到内存的对象上仍带着全量统计，造成扫描末端不必要的内存与序列化开销，尤其对 Flink 流式增量扫描这种长期持 split 的场景影响明显。

这个提交引入了“按列保留统计”的能力：用户/引擎可以指定一个列名集合，扫描在 planFiles 之后的 copy 阶段只保留这些列对应的 lower bounds、upper bounds、value counts、null value counts、nan value counts、column sizes，其它列的统计信息直接丢弃。这样既保留了原有“全部保留”和“全部丢弃”两种模式的语义，又增加了“按需保留”的第三种模式，让统计信息的传输与序列化成本与实际用到的列数成正比，而非与表的总列数成正比。

从 Iceberg 演进角度看，这是数据扫描 API 在精细化控制上的一个重要增强，与 select 投影下推到 manifest 的能力形成对称——select 控制“读哪些数据列”，includeColumnStats(columns) 控制“保留哪些列的统计”，两者共同把扫描末端对象体积压到最小。对于 Flink SQL 动态表、跨进程 enumerator/source 传递 split 的场景收益尤其明显（split 序列化体积显著下降）。

## 如何达成设计目的

整体设计思路是：在扫描 API 层新增 `includeColumnStats(Collection<String>)` 重载；在 `TableScanContext` 中新增 `columnsToKeepStats`（列 ID 集合）状态字段，与 `returnColumnStats` 共同决定统计保留策略；在 `ManifestGroup` 中把 `columnsToKeepStats` 透传到每个 task 的 `TaskContext`，并在 copy `ContentFile` 时调用新的 `ContentFile.copyWithStats(Set<Integer>)` 方法按列裁剪；具体裁剪逻辑由新工具类 `ContentFileUtil.copy(...)` 统一封装，根据 `withStats` 与 `requestedColumnIds` 三个组合（全无、全有、按列）选择 `copyWithoutStats()` / `copy()` / `copyWithStats(ids)`。`BaseFile` 的拷贝构造从 `(toCopy, fullCopy)` 改为 `(toCopy, copyStats, requestedColumnIds)`，引入 `SerializableMap.filteredCopyOf` 只拷贝指定 key 的子集。Flink 侧在 `ScanContext` 与 `FlinkSplitPlanner` 中透传新的 `includeStatsForColumns` 字段，并在持续增量扫描测试中新增覆盖三种模式的用例。

## 修改详情

### `.palantir/revapi.yml`

**修改目的**：声明本次 API/ABI 变更可被接受。

**工作逻辑**：新增一条 1.4.0 版本的接受项，针对 `org.apache.iceberg.util.SerializableMap.serialVersionUID` 的 `java.field.serialVersionUIDChanged` 变化，说明序列化版本号变化在内部不被认为是破坏性变更（理由是“Serialization is not be used”）。

### `api/src/main/java/org/apache/iceberg/BatchScanAdapter.java`

**修改目的**：让 `BatchScanAdapter`（API 层把 `Scan` 适配为 `BatchScan` 的包装器）支持新的按列统计接口。

**工作逻辑**：新增 `includeColumnStats(Collection<String> requestedColumns)` 方法，委托给被包装的 `scan.includeColumnStats(requestedColumns)`，保持 `BatchScanAdapter` 透传语义。

### `api/src/main/java/org/apache/iceberg/ContentFile.java`

**修改目的**：在 `ContentFile` 接口层暴露按列保留统计的拷贝能力。

**工作逻辑**：新增默认方法 `copyWithStats(Set<Integer> requestedColumnIds)`，默认抛 `UnsupportedOperationException` 并提示子类未实现。方法说明里明确：拷贝时只保留 `requestedColumnIds` 对应列的 lower bounds、upper bounds、value counts、null value counts、nan value counts。这是本提交的核心 API 增量，所有具体 `ContentFile` 实现类需覆写。

### `api/src/main/java/org/apache/iceberg/Scan.java`

**修改目的**：在 `Scan` 接口层新增按列请求统计的入口。

**工作逻辑**：新增默认方法 `includeColumnStats(Collection<String> requestedColumns)`，默认抛 `UnsupportedOperationException`。文档说明：列统计包括 value count、null value count、lower bounds、upper bounds。这与既有的 `includeColumnStats()`（全量）形成对照。

### `api/src/test/java/org/apache/iceberg/TestHelpers.java`

**修改目的**：让测试用的 `TestDataFile` 实现 `copyWithStats` 以保持测试代码可编译通过。

**工作逻辑**：在内部测试桩 `TestDataFile` 中实现 `copyWithStats(Set<Integer>)` 直接返回 `this`，避免接口新方法破坏既有测试。

### `core/src/main/java/org/apache/iceberg/BaseDistributedDataScan.java`

**修改目的**：让分布式扫描在拷贝数据文件时使用新的统一拷贝工具。

**工作逻辑**：把原 `dataFile.copy(shouldReturnColumnStats())` 替换为 `copy(dataFile)`，新增私有方法 `<F extends ContentFile<F>> F copy(F file)`，内部委托 `ContentFileUtil.copy(file, shouldReturnColumnStats(), columnsToKeepStats())`。这里把“是否拷贝统计”与“保留哪些列”两参数合并到一个工具调用中。

### `core/src/main/java/org/apache/iceberg/BaseFile.java`

**修改目的**：在 `BaseFile`（`GenericDataFile`/`GenericDeleteFile` 共同父类）层实现按列保留统计的具体拷贝逻辑。

**工作逻辑**：拷贝构造签名由 `BaseFile(BaseFile<F> toCopy, boolean fullCopy)` 改为 `BaseFile(BaseFile<F> toCopy, boolean copyStats, Set<Integer> requestedColumnIds)`。`copyStats=true` 分支不再无脑 `SerializableMap.copyOf(...)` 全量拷贝，而是改为调用新增的私有 `copyMap(...)`/`copyByteBufferMap(...)`：当 `requestedColumnIds == null` 时全量拷贝（向后兼容），非 null 时调用 `SerializableMap.filteredCopyOf(map, keys)` 只保留指定列 ID 的统计项。这保证了 5 类统计 map（`columnSizes`、`valueCounts`、`nullValueCounts`、`nanValueCounts`、`lowerBounds`、`upperBounds`）一致地按列裁剪。

### `core/src/main/java/org/apache/iceberg/BaseIncrementalAppendScan.java`、`core/src/main/java/org/apache/iceberg/BaseIncrementalChangelogScan.java`、`core/src/main/java/org/apache/iceberg/IncrementalDataTableScan.java`

**修改目的**：让增量扫描/changelog 扫描也透传“保留哪些列的统计”到 `ManifestGroup`。

**工作逻辑**：在构造 `ManifestGroup` 的链式调用末尾追加 `.columnsToKeepStats(columnsToKeepStats())`。`columnsToKeepStats()` 来自 `BaseScan` 新增的 protected 方法，从 `TableScanContext` 取出列 ID 集合。

### `core/src/main/java/org/apache/iceberg/BaseScan.java`

**修改目的**：在 `BaseScan` 层实现 `includeColumnStats(Collection<String>)` 并暴露 `columnsToKeepStats()`。

**工作逻辑**：新增 protected 方法 `columnsToKeepStats()` 直接返回 `context().columnsToKeepStats()`。新增 `includeColumnStats(Collection<String> requestedColumns)` 实现：用 `schema.findField(c).fieldId()` 把列名解析为列 ID，收集到 `Set<Integer>`，然后调用 `context.shouldReturnColumnStats(true).columnsToKeepStats(ids)` 生成新的 refined scan。注意这里同时设置了 `shouldReturnColumnStats(true)`，符合“按列保留隐含开启统计”的语义。

### `core/src/main/java/org/apache/iceberg/DataScan.java`、`core/src/main/java/org/apache/iceberg/DataTableScan.java`

**修改目的**：让主数据扫描在 planFiles 时把“保留哪些列的统计”传给 `ManifestGroup`。

**工作逻辑**：与增量扫描一致，在 `ManifestGroup` 链上追加 `.columnsToKeepStats(columnsToKeepStats())`。

### `core/src/main/java/org/apache/iceberg/GenericDataFile.java`、`core/src/main/java/org/apache/iceberg/GenericDeleteFile.java`

**修改目的**：在 `GenericDataFile`/`GenericDeleteFile`（`ContentFile` 的具体实现）上实现 `copyWithStats(Set<Integer>)`。

**工作逻辑**：拷贝构造从 `(toCopy, fullCopy)` 改为 `(toCopy, copyStats, requestedColumnIds)`，转交给 `BaseFile` 新构造。`copyWithoutStats()` 调用 `new GenericXxxFile(this, false, null)`（null 表示全量语义，但因 copyStats=false 不会走统计拷贝路径）；`copy()` 调用 `new GenericXxxFile(this, true, null)`（null = 全量统计）；新增 `copyWithStats(Set<Integer> requestedColumnIds)` 调用 `new GenericXxxFile(this, true, requestedColumnIds)`，把按列裁剪委托给 `BaseFile.copyMap`/`filteredCopyOf`。

### `core/src/main/java/org/apache/iceberg/ManifestGroup.java`

**修改目的**：在 `ManifestGroup` 中接收、存储并最终在 task 上下文里使用 `columnsToKeepStats`。

**工作逻辑**：新增字段 `private Set<Integer> columnsToKeepStats;` 与链式 setter `columnsToKeepStats(Set<Integer>)`（null 时存 null，否则 `Sets.newHashSet(...)` 拷贝一份避免外部修改）。在 `TaskContext` 构造里新增 `columnsToKeepStats` 参数与字段，并新增 `columnsToKeepStats()` 访问器。原先 planFiles 中 `entry.file().copy(ctx.shouldKeepStats())` 替换为 `ContentFileUtil.copy(entry.file(), ctx.shouldKeepStats(), ctx.columnsToKeepStats())`，统一由工具类根据三参数决定走哪条拷贝路径。这是把“按列保留”真正落到拷贝动作的关键点。

### `core/src/main/java/org/apache/iceberg/TableScanContext.java`

**修改目的**：在扫描上下文里持久化 `columnsToKeepStats`，并约束其语义。

**工作逻辑**：新增抽象方法 `@Nullable public abstract Set<Integer> columnsToKeepStats();`。新增 builder 风格的 `columnsToKeepStats(Set<Integer>)` 方法，用 `Preconditions.checkState(returnColumnStats(), "Cannot select columns to keep stats when column stats are not returned")` 强制：必须先开启统计返回，才能指定保留哪些列。这避免“统计未开启但指定列”这种无意义组合。

### `core/src/main/java/org/apache/iceberg/V1Metadata.java`、`core/src/main/java/org/apache/iceberg/V2Metadata.java`

**修改目的**：让 manifest reader 内部使用的包装类（`V1Metadata`/`V2Metadata` 中的 `IndexedDataFile`/`GenericFile` wrapper）适配新接口。

**工作逻辑**：`V1Metadata` 的包装类新增 `copyWithStats(Set<Integer>)` 直接委托给 `wrapped.copyWithStats(...)`；`V2Metadata` 的 `IndexedDataFile` wrapper 由于不允许整体拷贝（拷贝会丢失 indexed 性质），新增 `copyWithStats` 抛 `UnsupportedOperationException("Cannot copy IndexedDataFile wrapper")`，与既有 `copy()`/`copyWithoutStats()` 行为一致。

### `core/src/main/java/org/apache/iceberg/util/ContentFileUtil.java`（新文件）

**修改目的**：提供统一的按统计策略拷贝 `ContentFile` 的工具入口，集中三种组合的分支逻辑。

**工作逻辑**：新文件，定义 `ContentFileUtil.copy(F file, boolean withStats, Set<Integer> requestedColumnIds)` 静态方法：`withStats=false` → `file.copyWithoutStats()`；`withStats=true && requestedColumnIds==null` → `file.copy()`（全量）；`withStats=true && requestedColumnIds!=null` → `file.copyWithStats(ids)`（按列）。这是把“是否要统计 + 是否按列”两参数融合为单次调用的入口，被 `BaseDistributedDataScan` 和 `ManifestGroup` 共用，避免分支逻辑在多处重复。

### `core/src/main/java/org/apache/iceberg/util/SerializableMap.java`

**修改目的**：为 `SerializableMap` 增加“按键过滤拷贝”的能力，并修正序列化版本号。

**工作逻辑**：显式声明 `serialVersionUID = -3377238354349859240L`（之前依赖默认值，引发 revapi 告警）。新增私有构造 `SerializableMap(Map<K,V> map, Set<K> keys)`：用 `Maps.newHashMapWithExpectedSize(keys.size())` 创建目标 map，遍历 `keys`，仅当原 map 含该 key 时拷贝。新增静态工厂 `filteredCopyOf(Map<K,V> map, Set<K> keys)`：`map == null` 返回 null，否则构造过滤 map。`BaseFile.copyMap`/`copyByteBufferMap` 正是利用此方法实现按列裁剪。

### `core/src/test/java/org/apache/iceberg/TestScanDataFileColumns.java`

**修改目的**：覆盖新的按列保留统计行为，并扩展既有用例的统计列数断言。

**工作逻辑**：把测试表的 metrics 从单列（列 ID 1）扩展到双列（列 ID 1、2），并把 `testColumnStatsLoading` 改为断言每个统计 map 大小为 2（验证全量保留）。新增 `testColumnStatsPartial`：用 `table.newScan().includeColumnStats(ImmutableSet.of("id")).planFiles()`，断言返回的每个 `FileScanTask` 的 valueCounts/nullValueCounts/lowerBounds/upperBounds/columnSizes 大小均为 1，证明只有指定列的统计被保留。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/source/FlinkSplitPlanner.java`、`flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/source/ScanContext.java`

**修改目的**：把按列统计能力下推到 Flink source 的扫描上下文与切分计划。

**工作逻辑**：`ScanContext` 新增字段 `private final Collection<String> includeStatsForColumns;` 与访问器 `includeStatsForColumns()`，builder 新增 `includeColumnStats(Collection<String>)` 重载（与既有 boolean 重载同名重载），并在 `copy()` 与 `copyWithoutAssigning()` 中复制该字段。`FlinkSplitPlanner` 中在 `context.includeColumnStats()` 已经调过的基础上，再判断 `context.includeStatsForColumns() != null` 时调用 `refinedScan.includeColumnStats(context.includeStatsForColumns())` 下推到核心扫描。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/enumerator/TestContinuousSplitPlannerImpl.java`

**修改目的**：在 Flink 持续增量扫描场景下验证三种统计保留模式。

**工作逻辑**：把 `verifyOneCycle` 返回值从 `IcebergEnumeratorPosition` 改为内部新类 `CycleResult`（同时持有 position 与 split），便于在循环里校验 split 上的统计。新增三个用例：`testTableScanNoStats`（统计关闭，所有统计 map 为 null）、`testTableScanAllStats`（统计全开，每类大小为 3）、`testTableScanSingleStat`（按列 `ImmutableSet.of("data")`，每类大小为 1）。新增辅助 `verifyStatCount(IcebergSourceSplit split, int expected)` 集中断言六类统计 map 的 null/size。这三个用例把核心 API 在 Flink streaming 链路上的端到端行为覆盖完整。

## 小结

这个提交通过在 `Scan`/`ContentFile` 接口层新增“按列请求统计”能力、在 `TableScanContext`/`ManifestGroup` 透传列 ID 集合、并在 `BaseFile` 拷贝路径上用 `SerializableMap.filteredCopyOf` 真正按列裁剪统计，把扫描末端对象的统计信息体积从“全有或全无”精细化到“按需保留”，是 Iceberg 扫描 API 在投影/裁剪维度上与 `select` 对称演进的重要一步，对 Flink 流式 split 序列化与跨引擎统计传递的性能优化具有实际意义。
