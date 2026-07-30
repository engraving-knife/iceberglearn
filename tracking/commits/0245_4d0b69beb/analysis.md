# 提交 0245：Flink: Fix IcebergSource tableloader lifecycle management in batch mode (#9173)

## 提交信息

- **序号**：0245 / 4088
- **哈希**：4d0b69beba104e6912f6e6850189121fcd23ef8a
- **短哈希**：4d0b69beb
- **日期**：2023-12-09 09:06:32 +0100
- **作者**：Mason Chen
- **提交说明**：Flink: Fix IcebergSource tableloader lifecycle management in batch mode (#9173)
- **PR/Issue**：#9173

## 总体目的

`IcebergSource` 是 Flink 新版 Source API（FLIP-27）下 Iceberg 的入口实现，它在 builder 阶段会加载一次表，并把 `TableLoader` 与 `Table` 一起持有以便在枚举器中复用。此前的实现里，`IcebergSource` 持有一个 `transient Table table` 字段，通过 `lazyTable()` 懒加载：首次调用时执行 `tableLoader.open()`，再 `try (TableLoader loader = tableLoader) { this.table = loader.loadTable(); }`——也就是在 try-with-resources 里把用户传入的 `tableLoader` 当作要关闭的资源关掉。

这带来了两个相互关联的问题。第一，`tableLoader` 是用户提供的对象，其生命周期应由用户管理，而 `lazyTable()` 会偷偷把它关闭，导致用户在 source 之外再使用同一个 `tableLoader` 时遇到"已被关闭"的异常，与用户的生命周期管理发生冲突。第二，被关闭的 `tableLoader` 后续仍被 `ContinuousSplitPlannerImpl`（流式模式的 split planner）使用，需要调用方在传入前先 `clone()`，而批式模式的 `planSplitsForBatch` 直接调用 `lazyTable()` 复用那个已被关闭 loader 加载出的 `Table` 实例——既混用了"用户 loader 的副作用"与"source 自己的资源"，又把流的处理逻辑不一致地套到批模式上。

本提交重新设计了 `IcebergSource` 中 `tableLoader` 的生命周期：source 只持有 `tableLoader` 与一个"资源无关"的 `tableName`（用于命名与日志），任何真正需要 IO 的地方（批式 split 规划、流式 split 规划）都通过 `tableLoader.clone()` 拿到独立副本并 `open()` 后使用、用完即关闭，绝不关闭用户原始 loader。同时把 `ContinuousSplitPlannerImpl` 改为内部自行 clone，调用方不再需要预先 clone。这是 Flink source 资源管理上的一个重要修正，使 Iceberg source 在批模式下也能正确、独立地管理 table loader 生命周期。

## 如何达成设计目的

整体设计思路是把"资源无关信息"与"需要 IO 的资源"分离：

- 用 `tableName`（`table.name()`）取代 `transient Table table` 字段，所有只需表名的地方（`name()`、`planningThreadName()`、`createReader` 的 metrics 名、restore 枚举器日志）都用 `tableName`，不再触碰 `tableLoader`。
- 批式 split 规划 `planSplitsForBatch` 改为 `try (TableLoader loader = tableLoader.clone()) { loader.open(); ... loader.loadTable() ... }`，独立 clone 副本用完即关，不影响用户的 loader。
- 流式模式 `createEnumerator` 中传给 `ContinuousSplitPlannerImpl` 的 `tableLoader` 不再预先 clone（注释明确说明 clone 已下沉到 planner 内部），由 `ContinuousSplitPlannerImpl` 构造方法自行 `clone()` + `open()` + `loadTable()`。
- 把 `Preconditions.checkNotNull` 校验从 builder 的 `checkRequired()` 移到 `IcebergSource` 构造方法，使不可变约束在对象构造时就成立。

## 修改详情

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/source/IcebergSource.java`

**修改目的**：重构 `IcebergSource` 的 tableLoader / table 生命周期，避免关闭用户的 loader。

**工作逻辑**：

- 字段调整：删除 `private transient Table table;`，新增 `private final String tableName;`。并给 `tableLoader` 字段加注释，明确"这个 loader 可以被关闭，且只应用于资源无关信息（如表名）；任何需要底层 IO 的场景（如 split 规划）必须用副本，避免与用户 loader 的生命周期冲突"。
- 构造方法：新增 `Preconditions.checkNotNull` 对 `tableLoader`、`readerFunction`、`assignerFactory`、`table` 的校验（原在 builder 的 `checkRequired` 里，且校验范围更窄）；不再 `this.table = table`，而是 `this.tableName = table.name()`，丢弃 `Table` 引用只保留资源无关的名称。
- `name()`：由 `"IcebergSource-" + lazyTable().name()` 改为 `"IcebergSource-" + tableName`。
- `planningThreadName()`：由 `lazyTable().name() + "-" + UUID.randomUUID()` 改为 `tableName + "-" + UUID.randomUUID()`。
- `planSplitsForBatch(String threadName)`：核心改动。原实现 `try { FlinkSplitPlanner.planIcebergSourceSplits(lazyTable(), scanContext, workerPool); } finally { workerPool.shutdown(); }`。新实现改为：

  ```java
  try (TableLoader loader = tableLoader.clone()) {
    loader.open();
    List<IcebergSourceSplit> splits =
        FlinkSplitPlanner.planIcebergSourceSplits(loader.loadTable(), scanContext, workerPool);
    LOG.info("Discovered {} splits from table {} during job initialization", splits.size(), tableName);
    return splits;
  } catch (IOException e) {
    throw new UncheckedIOException("Failed to close table loader", e);
  } finally {
    workerPool.shutdown();
  }
  ```

  即每次批式规划都独立 clone 一份 loader，open 后加载表、规划 split、用完在 try-with-resources 中关闭。这样不再依赖 `lazyTable()`，也不再误关用户的 loader。`catch (IOException)` 把 try-with-resources 抛出的关闭异常翻译为 `UncheckedIOException`。
- 删除 `lazyTable()` 方法：原方法承担"懒加载 + 关闭用户 loader"的副作用，重构后所有调用点都已改用 `tableName` 或 clone 副本，该方法整体移除。
- `createReader(SourceReaderContext)`：`new IcebergSourceReaderMetrics(readerContext.metricGroup(), lazyTable().name())` 改为 `... , tableName)`，避免在 reader 创建路径上触发 loader 副作用。
- `createEnumerator(SourceEnumeratorContext)`：restore 日志中的 `lazyTable().name()` 改为 `tableName`；流式分支构造 `ContinuousSplitPlannerImpl` 时由 `new ContinuousSplitPlannerImpl(tableLoader.clone(), scanContext, planningThreadName())` 改为 `new ContinuousSplitPlannerImpl(tableLoader, scanContext, planningThreadName())`——因为 clone 职责已下沉到 planner 内部（见下文），这里直接传原始 loader 即可，planner 会自行 clone 并管理。
- Builder 的 `build()`：删除 `checkRequired()` 调用（校验已移到 `IcebergSource` 构造方法），并删除 `checkRequired()` 私有方法本身。注释保留"Since builder already load the table, pass it to the source to avoid double loading"，说明 builder 仍会加载一次表用于取 `tableName`，但 source 不再持有 `Table` 引用。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/source/enumerator/ContinuousSplitPlannerImpl.java`

**修改目的**：把 `tableLoader` 的 clone + open 职责下沉到 planner 内部，使调用方无需预先 clone。

**工作逻辑**：

构造方法由 `this.tableLoader = tableLoader; this.tableLoader.open(); this.table = tableLoader.loadTable();` 改为 `this.tableLoader = tableLoader.clone(); this.tableLoader.open(); this.table = this.tableLoader.loadTable();`。即不再直接 open / loadTable 传入的 loader，而是先 `clone()` 一份再 open + loadTable。这样 planner 持有的是自己的副本，其 open/close 生命周期与用户 loader 解耦——`ContinuousSplitPlannerImpl` 内部其他地方（如 `close` / `planSplits`）仍复用 `this.tableLoader` 与 `this.table`，但作用在独立副本上。这与 `IcebergSource.createEnumerator` 不再预先 clone 的改动配套：source 把原始 loader 传给 planner，planner 自己 clone，职责清晰。

## 小结

本提交通过把 `IcebergSource` 对 `Table` 的持有替换为资源无关的 `tableName`、并在所有需要 IO 的路径上用 `tableLoader.clone()` 独立管理副本，修复了批模式下 source 误关用户 `tableLoader` 的生命周期冲突，同时把 clone 职责下沉到 `ContinuousSplitPlannerImpl`，使 source 与 planner 的资源边界清晰一致。
