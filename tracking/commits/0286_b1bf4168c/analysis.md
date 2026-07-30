# 提交 0286：Flink: port #9173 to v1.16 and v1.18 (#9334)

## 提交信息

- **序号**：0286 / 4088
- **哈希**：b1bf4168c481fe44b98ac6e425d7b15c02b44464
- **短哈希**：b1bf4168c
- **日期**：2023-12-19 07:19:03 +0100
- **作者**：Mason Chen
- **提交说明**：Flink: port #9173 to v1.16 and v1.18 (#9334)
- **PR/Issue**：#9334（对应原始 PR #9173）

## 总体目的

本提交是 PR #9173（"Flink: Fix IcebergSource tableloader lifecycle management in batch mode"）向 Flink v1.16 与 v1.18 两个老分支的回溯移植。原始 PR 已先合入主分支（对应 v1.19+），但 Iceberg 对 Flink 多个版本并行维护，老分支同样存在该生命周期管理缺陷，需要同步修复。

问题的核心在于 `IcebergSource` 中对 `TableLoader` 与 `Table` 的生命周期管理不当。旧实现里 `IcebergSource` 持有一个 `transient Table table` 字段，并通过 `lazyTable()` 方法在首次访问时惰性加载：它先调用 `tableLoader.open()`，再以 try-with-resources 的方式使用 `tableLoader`（注意这里直接用了 `tableLoader` 而非副本），随后把加载到的 `Table` 缓存到 `this.table`。这种写法存在两个严重问题：第一，try-with-resources 会把用户传入的 `tableLoader` 直接关闭，而该 `tableLoader` 是用户提供的、其生命周期应由用户掌控，源内部不应越权关闭；第二，被关闭后的 `tableLoader` 在后续流式枚举器（`ContinuousIcebergEnumerator`）创建 `ContinuousSplitPlannerImpl` 时又再次被传入并使用，造成"使用已关闭资源"的隐患。虽然批模式下能侥幸跑通，但严格意义上资源生命周期是错乱的。

本提交的动机是把上述修复同步到 v1.16 和 v1.18，使这两个长期支持分支上的 Flink 集成也具备正确的资源管理语义，避免后续在维护或下游项目引用时出现资源泄漏或重复关闭导致的异常。

## 如何达成设计目的

整体设计思路是：`IcebergSource` 不再持有可变的 `Table` 引用，仅保留一个从 `Table.name()` 取得的 `String tableName`，用于所有"与资源无关"的信息展示（如 source 名、线程名、日志中的表名）。所有真正需要 `Table` 对象（即需要使用底层 IO）的场合，都改为现场克隆 `tableLoader`、打开、加载 `Table`、用完即关的局部模式。

具体到两条路径：批模式切分规划（`planSplitsForBatch`）改为在 try-with-resources 中克隆并打开 `tableLoader`，加载出的 `Table` 仅用于本次规划，方法返回时副本即被关闭；流式模式切分规划则把原始 `tableLoader`（而非其克隆）传给 `ContinuousSplitPlannerImpl`，由后者在自己的构造器内部完成 `clone()` + `open()` + `loadTable()`，并在 `close()` 时关闭自己持有的副本，从而避免 `IcebergSource` 与 `ContinuousSplitPlannerImpl` 双方都尝试关闭同一个 loader 的冲突。此外，原本在 Builder 中做的必填校验 `checkRequired()` 被移到 `IcebergSource` 构造器中并补全了 `readerFunction`、`table` 等校验，使校验时机更贴近使用点。

## 修改详情

### `flink/v1.16/flink/src/main/java/org/apache/iceberg/flink/source/IcebergSource.java`

**修改目的**：修复 v1.16 Flink 源中 `TableLoader` 与 `Table` 的生命周期管理，避免关闭用户提供的 loader 以及使用已关闭的 loader。

**工作逻辑**：

- 字段调整：删除 `private transient Table table`，新增 `private final String tableName`。`tableLoader` 字段保留并新增注释说明"该 loader 可以被关闭，仅可用于资源无关的信息（如表名）；需要底层 IO 的场景必须使用其克隆副本"。
- 构造器：把 Builder 中的 `checkRequired()` 校验逻辑移到构造器，并扩展为对 `tableLoader`、`readerFunction`、`assignerFactory`、`table` 四个必填项的 `Preconditions.checkNotNull` 校验；同时把 `this.table = table` 替换为 `this.tableName = table.name()`，只保留表名字符串而不持有 Table 对象。
- `name()` 与 `planningThreadName()`：把原来依赖 `lazyTable().name()` 的调用改为直接使用 `tableName`，避免触发惰性加载。
- `planSplitsForBatch(String)`：把 `try { ... } finally { workerPool.shutdown(); }` 改为 `try (TableLoader loader = tableLoader.clone()) { loader.open(); ... } catch (IOException e) { throw new UncheckedIOException(...) } finally { workerPool.shutdown(); }`，在 try-with-resources 中克隆并打开 loader，使用 `loader.loadTable()` 调用 `FlinkSplitPlanner.planIcebergSourceSplits`，方法返回时副本被自动关闭，原始 `tableLoader` 不再被关闭。日志里的表名也改用 `tableName`。
- 删除 `lazyTable()` 私有方法：该方法原本负责惰性加载并缓存 `Table`，且会错误地以 try-with-resources 关闭用户传入的 `tableLoader`，现已无存在必要。
- `createReader(SourceReaderContext)`：构造 `IcebergSourceReaderMetrics` 时表名改用 `tableName`。
- `createEnumerator(EnumeratorState)`：恢复状态日志中的表名改用 `tableName`；流式分支构造 `ContinuousSplitPlannerImpl` 时，参数由 `tableLoader.clone()` 改为 `tableLoader`，把克隆职责下放到 `ContinuousSplitPlannerImpl` 构造器内部，避免 `IcebergSource` 与 `ContinuousSplitPlannerImpl` 双方都尝试关闭同一个克隆副本。
- Builder：删除 `checkRequired()` 方法及其在 `build()` 中的调用，校验已迁移到 `IcebergSource` 构造器。

### `flink/v1.16/flink/src/main/java/org/apache/iceberg/flink/source/enumerator/ContinuousSplitPlannerImpl.java`

**修改目的**：让 `ContinuousSplitPlannerImpl` 自行克隆并管理传入的 `TableLoader`，与 `IcebergSource` 不再共享同一个可关闭实例。

**工作逻辑**：

构造器中把 `this.tableLoader = tableLoader;` 改为 `this.tableLoader = tableLoader.clone();`，随后 `this.tableLoader.open()` 与 `this.table = this.tableLoader.loadTable()` 均基于克隆副本进行。同时把加载语句改为 `this.table = this.tableLoader.loadTable();`（使用克隆后的字段），保证后续 `close()` 关闭的是自己持有的副本，与外部传入的 loader 完全解耦。构造器 Javadoc 也同步更新为 `@param tableLoader A cloned tableLoader.`（注：实际语义是该参数会被克隆，调用方传入原 loader 即可）。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/source/IcebergSource.java`

**修改目的**：与 v1.16 同步，修复 v1.18 Flink 源中的 `TableLoader` 生命周期管理。

**工作逻辑**：与 v1.16 同名文件的修改完全一致，包括字段替换、构造器校验、`name()`/`planningThreadName()`/`createReader()`/`createEnumerator()` 改用 `tableName`、`planSplitsForBatch` 改为克隆副本、删除 `lazyTable()`、Builder 删除 `checkRequired()`。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/source/enumerator/ContinuousSplitPlannerImpl.java`

**修改目的**：与 v1.16 同步，让 v1.18 的 `ContinuousSplitPlannerImpl` 自行克隆 `TableLoader`。

**工作逻辑**：与 v1.16 同名文件的修改完全一致，构造器内对 `tableLoader.clone()` 后再 `open()` 与 `loadTable()`。

## 小结

本提交通过把 PR #9173 的修复回溯到 Flink v1.16 和 v1.18 两个老分支，消除了 `IcebergSource` 错误关闭用户传入 `TableLoader`、并在后续流式规划中复用已关闭 loader 的生命周期缺陷。核心做法是：源内部只保留资源无关的表名字符串，凡需要 `Table` 对象的场合一律克隆 loader、用完即关；同时把 `ContinuousSplitPlannerImpl` 的克隆职责收敛到其自身构造器，理清了 `IcebergSource` 与 `ContinuousSplitPlannerImpl` 之间的资源所有权边界。
