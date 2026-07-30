# 提交 0709：Hive: turn off the stats gathering when iceberg.hive.keep.stats is false

## 提交信息
- **序号**：0709 / 4088
- **哈希**：866021d7d34f274349ce7de1f29d113395e7f28c
- **短哈希**：866021d7d
- **日期**：2024-04-23
- **作者**：Hanzhi Wang
- **提交说明**：Hive: turn off the stats gathering when iceberg.hive.keep.stats is false (#10148)
- **PR/Issue**：#10148

## 总体目的

Iceberg 通过 Hive Metastore（HMS）管理表元数据时，配置项 `iceberg.hive.keep.stats`（对应 `ConfigProperties.KEEP_HIVE_STATS`）控制是否在 Hive 侧保留统计信息。当该配置为 `false`（`keepHiveStats = false`）时，Iceberg 的意图是**不保留 Hive 统计信息**，因为 Iceberg 自身维护更准确的统计信息，Hive 的统计可能与 Iceberg 不一致而误导查询优化器。

然而，原实现仅在 `HiveTableOperations` 中移除了表参数 `COLUMN_STATS_ACCURATE`，这只表示"列级统计不准确"，但**并未阻止 Hive 引擎自身的自动统计收集功能**（`hive.stats.autogather`）。当 Hive 端开启 `hive.stats.autogather=true` 时，Hive 仍会在表操作（如创建表）时自动计算并写入 `TOTAL_SIZE`、`NUM_FILES` 等表级统计参数到 HMS，即便 Iceberg 已经声明不要保留统计。

这就产生了一个矛盾：用户明确设置 `iceberg.hive.keep.stats=false` 表明不希望保留 Hive 统计，但 Hive 引擎依然写入统计信息，导致：
1. 行为与用户预期不符。
2. HMS 中残留与 Iceberg 实际数据状态可能不一致的统计信息。
3. 在空表创建场景下，Hive 写入的 `NUM_FILES=1`、`TOTAL_SIZE` 等值具有误导性（Iceberg 表此时实际无数据文件）。

本次提交通过在表参数中显式设置 `DO_NOT_UPDATE_STATS=TRUE` 来告知 Hive 引擎跳过该表的统计收集，彻底关闭 `iceberg.hive.keep.stats=false` 时的 Hive 自动统计。

## 如何达成设计目的

Hive 的 `StatsSetupConst.DO_NOT_UPDATE_STATS` 是 Hive Metastore 识别的一个表参数标记。当其值为 `"true"` 时，Hive 引擎在执行表操作时会**跳过统计信息的自动收集与更新**，尊重外部系统对该表统计的管理权。

提交在 `HiveTableOperations` 已有的"移除 `COLUMN_STATS_ACCURATE`"逻辑旁边，追加一行 `put(DO_NOT_UPDATE_STATS, TRUE)`，与原有的统计清理逻辑协同：
- 移除 `COLUMN_STATS_ACCURATE`：声明现有列统计不准确，不应被信任。
- 设置 `DO_NOT_UPDATE_STATS=TRUE`：阻止 Hive 重新收集/写入新的统计信息。

两者配合，既清除了旧的不准确统计标记，又阻止了 Hive 写入新的统计，完整实现"不保留 Hive 统计"的语义。

## 修改详情

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveTableOperations.java`
**修改目的**：在 `keepHiveStats=false` 时，除移除 `COLUMN_STATS_ACCURATE` 外，额外设置 `DO_NOT_UPDATE_STATS=TRUE` 阻止 Hive 自动统计收集。

**工作逻辑**：在向 HMS 提交表更新前的参数处理段（`if (!keepHiveStats)` 分支内），新增：
```java
tbl.getParameters().put(StatsSetupConst.DO_NOT_UPDATE_STATS, StatsSetupConst.TRUE);
```
这行紧跟在原有的 `tbl.getParameters().remove(StatsSetupConst.COLUMN_STATS_ACCURATE);` 之后。由于新增了一个表参数，后续 `lock.ensureActive()` 提交到 HMS 的表对象会多携带 `DO_NOT_UPDATE_STATS=true` 参数。

### `mr/src/test/java/org/apache/iceberg/mr/hive/TestHiveIcebergStorageHandlerNoScan.java`
**修改目的**：更新已有测试中对 HMS 表参数数量的断言，以反映新增的 `DO_NOT_UPDATE_STATS` 参数。

**工作逻辑**：两处断言的 `hasSize` 从 14→15、17→18，各加 1，对应新增的 `DO_NOT_UPDATE_STATS` 参数。其余断言不变，验证原有参数仍存在。

### `mr/src/test/java/org/apache/iceberg/mr/hive/TestHiveIcebergWithHiveAutogatherEnable.java`（新增文件）
**修改目的**：新增专门的集成测试，验证在 `hive.stats.autogather=true` 的 Hive 环境下，`KEEP_HIVE_STATS` 的 true/false 取值对 HMS 表统计参数的实际影响。

**工作逻辑**：
- 使用 `ParameterizedTestExtension` 对每种 `FileFormat` × `HIVE_CATALOG` 组合参数化运行。
- `beforeClass()` 中通过 `HiveConf.ConfVars.HIVESTATSAUTOGATHER= "true"` 启用 Hive 自动统计收集，模拟问题环境。
- **`testHiveStatsAutogatherWhenCreateNewTable()`** 核心测试逻辑：
  1. **`KEEP_HIVE_STATS=false` 场景**：创建空表后断言 HMS 表参数中 `TOTAL_SIZE`、`NUM_FILES`、`DO_NOT_UPDATE_STATS` 均为 `null`。其中 `DO_NOT_UPDATE_STATS` 为 null 的断言值得注意——这表明 Iceberg 在写入 `DO_NOT_UPDATE_STATS=TRUE` 后，Hive 引擎识别到该标记并**主动移除了它**（Hive 的标准行为：处理完统计逻辑后清理该标记），同时因标记存在而跳过了 `TOTAL_SIZE`/`NUM_FILES` 的写入，从而三者均为 null，证明 Hive 自动统计被成功关闭。
  2. **`KEEP_HIVE_STATS=true` 场景**：创建空表后断言 `DO_NOT_UPDATE_STATS` 为 null（未设置该标记），`NUM_FILES="1"`、`TOTAL_SIZE` 非 null，证明 Hive 正常收集了统计信息。
- 测试特意使用空表（`ImmutableList.of()` 无数据记录），以排除 Iceberg 自身在插入数据时写入 `TOTAL_SIZE`/`NUM_FILES` 的干扰，确保观察到的统计值完全来自 Hive 引擎的 autogather。

## 小结
- **成效**：成功修复 `iceberg.hive.keep.stats=false` 时 Hive 仍自动收集统计的问题。通过 `DO_NOT_UPDATE_STATS=TRUE` 标记让 Hive 引擎跳过统计，行为与用户配置预期一致。新增的集成测试在真实的 Hive 环境下验证了两种配置场景，覆盖到位。
- **影响范围**：`hive-metastore` 模块的 `HiveTableOperations`（表元数据提交逻辑），以及 `mr` 模块的 Hive 集成测试。影响所有通过 HiveCatalog 管理 Iceberg 表且设置 `iceberg.hive.keep.stats=false` 的部署。
- **回迁到 1.4.x 的注意事项**：此为 Bug 修复，建议回迁。需确认 1.4.x 分支的 `HiveTableOperations` 中 `keepHiveStats` 分支结构与 main 一致。新增测试依赖 `ParameterizedTestExtension`、`HiveIcebergStorageHandlerTestUtils` 等 1.4.x 需存在的测试基础设施；`StatsSetupConst.DO_NOT_UPDATE_STATS` 和 `StatsSetupConst.TRUE` 来自 Hive 依赖，需确认 1.4.x 的 Hive 版本中这些常量存在（较老版本 Hive 可能常量名不同）。若 1.4.x 已有的 `TestHiveIcebergStorageHandlerNoScan` 测试断言数量与 main 改动前不同，需相应调整 `hasSize` 的预期值而非直接套用 +1。
