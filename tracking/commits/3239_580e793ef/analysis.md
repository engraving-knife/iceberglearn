# 提交 3239：Core, Spark: Rename RequiresRemoteScanPlanning to SupportsDistributedScanPlanning (#15184)

## 提交信息

- **序号**：3239 / 4088
- **哈希**：580e793ef220404236177935e6ee94d1ccfe4668
- **短哈希**：580e793ef
- **日期**：2026-02-11
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core, Spark: Rename RequiresRemoteScanPlanning to SupportsDistributedScanPlanning (#15184)
- **PR/Issue**：#15184

## 总体目的

本提交对 Iceberg 的分布式扫描计划（distributed scan planning）机制进行了重构和语义改进。此前，`RequiresRemoteScanPlanning` 是一个空的标记接口（marker interface），仅由 `RESTTable` 实现。它的作用是告诉 Spark 读取层："这张表的扫描计划必须由远端（REST 服务）完成，不要尝试在 Spark 侧做分布式计划"。在 `SparkScanBuilder.newBatchScan()` 中，逻辑是分两步的：先检查 table 是否是 `RequiresRemoteScanPlanning`（若是则直接调用 `table.newBatchScan()` 走表自己的扫描），再检查是否是 `BaseTable` 且配置允许分布式计划（若是则用 `SparkDistributedDataScan`）。

这种设计有几个问题：第一，"Requires"（要求）的命名暗示了强制性约束，但实际上该接口仅是一个标记，语义不够精确；第二，分布式计划是否启用的决策逻辑分散在两处（`SparkScanBuilder` 的 instanceof 检查 + `SparkReadConf.distributedPlanningEnabled()` 的配置检查），不够内聚；第三，`BaseTable` 本身没有实现该接口，导致 `SparkScanBuilder` 需要分别对 `RequiresRemoteScanPlanning` 和 `BaseTable` 做两次 instanceof 判断。

本提交将接口重命名为 `SupportsDistributedScanPlanning`，将其从空标记接口升级为带默认方法的接口（`default boolean allowDistributedPlanning()` 返回 `true`），让 `BaseTable` 实现该接口（默认允许分布式计划），`RESTTable` 覆盖 `allowDistributedPlanning()` 返回 `false`（REST 表自行做远端计划，不允许 Spark 侧分布式计划）。同时将分布式计划是否启用的判断统一收敛到 `SparkReadConf.distributedPlanningEnabled()` 中，简化了 `SparkScanBuilder` 的分支逻辑。

## 如何达成设计目的

整体思路分三步：(1) 将 `RequiresRemoteScanPlanning` 重命名为 `SupportsDistributedScanPlanning`，并添加 `allowDistributedPlanning()` 默认方法；(2) 让 `BaseTable` 实现该接口（继承默认的 `true`），`RESTTable` 覆盖为 `false`；(3) 在各 Spark 版本的 `SparkReadConf.distributedPlanningEnabled()` 中增加对 `SupportsDistributedScanPlanning` 的 instanceof 检查和 `allowDistributedPlanning()` 判断，使该方法的返回值同时反映"表是否支持/允许"和"配置是否启用"两个维度；相应地简化 `SparkScanBuilder.newBatchScan()`，移除原来的双 instanceof 分支，统一由 `distributedPlanningEnabled()` 决定。改动覆盖 core 模块和 spark v3.4/v3.5/v4.0/v4.1 四个版本模块。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SupportsDistributedScanPlanning.java`（由 `RequiresRemoteScanPlanning.java` 重命名）(+5/-2 lines)

**修改目的**：重命名接口并添加 `allowDistributedPlanning()` 默认方法。

**工作逻辑**：原 `RequiresRemoteScanPlanning` 是空接口 `public interface RequiresRemoteScanPlanning {}`。新接口 `SupportsDistributedScanPlanning` 添加了 `default boolean allowDistributedPlanning() { return true; }`。默认返回 `true` 表示实现该接口的表默认允许分布式扫描计划，但子类可以覆盖返回 `false` 来禁用。Javadoc 也从"indicate whether a Table requires remote scan planning"改为"indicate whether a Table supports distributed scan planning"。

### `core/src/main/java/org/apache/iceberg/BaseTable.java` (+2/-1 lines)

**修改目的**：让 `BaseTable` 实现 `SupportsDistributedScanPlanning` 接口。

**工作逻辑**：`BaseTable` 的 implements 列表增加 `SupportsDistributedScanPlanning`。由于接口的 `allowDistributedPlanning()` 默认返回 `true`，`BaseTable` 无需额外实现任何方法即表示"支持且允许分布式扫描计划"。这是合理的，因为 `BaseTable` 是基于本地元数据的常规表，Spark 可以对其进行分布式扫描计划。

### `core/src/main/java/org/apache/iceberg/rest/RESTTable.java` (+7/-2 lines)

**修改目的**：`RESTTable` 改为实现 `SupportsDistributedScanPlanning` 并覆盖 `allowDistributedPlanning()` 返回 `false`。

**工作逻辑**：`RESTTable` 原 `implements RequiresRemoteScanPlanning` 改为 `implements SupportsDistributedScanPlanning`，并新增 `@Override public boolean allowDistributedPlanning() { return false; }`。`RESTTable` 继承自 `BaseTable`，但 REST 表的扫描计划由远端 REST 服务完成，不应由 Spark 侧做分布式计划，因此返回 `false`。此前这个"跳过分布式计划"的语义是通过标记接口 + `SparkScanBuilder` 中的 instanceof 分支实现的，现在改为通过 `allowDistributedPlanning()` 方法表达，更加显式和灵活。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkReadConf.java` (+4/-1 lines)

**修改目的**：在 `distributedPlanningEnabled()` 中加入表级支持判断。

**工作逻辑**：原实现 `return dataPlanningMode() != LOCAL || deletePlanningMode() != LOCAL;` 仅检查配置中的计划模式是否非 LOCAL。新实现 `return table instanceof SupportsDistributedScanPlanning distributed && distributed.allowDistributedPlanning() && (dataPlanningMode() != LOCAL || deletePlanningMode() != LOCAL);` 使用 Java 16+ 的 pattern instanceof 绑定变量 `distributed`，先检查表是否实现 `SupportsDistributedScanPlanning` 接口，再调用 `allowDistributedPlanning()` 确认表允许分布式计划，最后才检查配置中的计划模式。三个条件短路求值，任一不满足则返回 `false`。新增 `import org.apache.iceberg.SupportsDistributedScanPlanning;`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java` (+1/-4 lines)

**修改目的**：简化 `newBatchScan()` 的分支逻辑。

**工作逻辑**：原实现有三个分支：`if (table instanceof RequiresRemoteScanPlanning)` → 走 `table.newBatchScan()`；`else if (table instanceof BaseTable && readConf.distributedPlanningEnabled())` → 走 `SparkDistributedDataScan`；`else` → 走 `table.newBatchScan()`。新实现简化为两个分支：`if (readConf.distributedPlanningEnabled())` → 走 `SparkDistributedDataScan`；`else` → 走 `table.newBatchScan()`。原来需要 `SparkScanBuilder` 直接检查 `RequiresRemoteScanPlanning` 标记接口来决定是否跳过分布式计划，现在这个判断已被吸收进 `distributedPlanningEnabled()`（通过 `allowDistributedPlanning()` 返回 `false` 的 RESTTable 会令该方法返回 `false`），因此 `SparkScanBuilder` 只需依赖 `distributedPlanningEnabled()` 一个入口即可。移除了 `RequiresRemoteScanPlanning` 的 import。

### `spark/v3.5`、`spark/v4.0`、`spark/v4.1` 下对应的 `SparkReadConf.java` 和 `SparkScanBuilder.java`（各 +4/-1 和 +1/-4 lines）

**修改目的**：与 v3.4 完全相同的改动，在四个 Spark 版本模块中同步应用。

**工作逻辑**：四个 Spark 版本（v3.4、v3.5、v4.0、v4.1）的 `SparkReadConf.java` 和 `SparkScanBuilder.java` 改动内容一致：`SparkReadConf.distributedPlanningEnabled()` 加入 `SupportsDistributedScanPlanning` instanceof 检查和 `allowDistributedPlanning()` 判断；`SparkScanBuilder.newBatchScan()` 简化为单一 `distributedPlanningEnabled()` 判断。这是 Iceberg 项目为同时支持多个 Spark 版本而维护并行代码的常规做法。

## 总结

本提交是一次有意义的接口设计重构：将 `RequiresRemoteScanPlanning` 标记接口升级为 `SupportsDistributedScanPlanning` 带方法接口，引入 `allowDistributedPlanning()` 让表能显式表达是否允许分布式扫描计划。通过让 `BaseTable` 默认允许、`RESTTable` 覆盖为不允许，并将判断逻辑统一收敛到 `SparkReadConf.distributedPlanningEnabled()`，简化了 `SparkScanBuilder` 的分支逻辑，使决策入口从两处变为一处。命名从"Requires"改为"Supports"也更准确地反映了接口的语义（表是否"支持"分布式计划，而非"要求"远端计划）。改动在四个 Spark 版本模块中同步应用，保持了一致性。
