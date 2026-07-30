# 提交 0469：Spark 3.4: Support executor cache locality (#9658)

## 提交信息

- **序号**：0469
- **哈希**：b6c3f8f2ba9ae3e5c92a09496837239e27f79311
- **短哈希**：b6c3f8f2b
- **日期**：2024-02-05 17:08:00 -0800
- **作者**：Anton Okolnychyi <aokolnychyi@apple.com>
- **提交说明**：Spark 3.4: Support executor cache locality (#9658)
- **PR/Issue**：#9658

## 总体目的

本提交是提交 0468（Spark 3.5: Support executor cache locality, #9563）向 Spark 3.4 模块的平行移植，目的是让 Iceberg 的 Spark 3.4 集成也具备相同的"executor 缓存本地性"能力。Iceberg 项目针对每个受支持的 Spark 版本（3.3/3.4/3.5）维护独立的 `spark/v3.x/` 源码树，同一特性需要在每个版本分支上分别落地。由于 0468 已经在 `spark/v3.5/` 下完成了全部实现与测试，本提交把完全相同的设计原封不动地应用到 `spark/v3.4/` 下，使两个 Spark 版本的行为保持一致。

与 0468 相同，核心动机是：Iceberg 的 executor 端缓存（由 `spark.sql.iceberg.executor-cache.enabled` 控制，主要用于缓存 equality delete 文件）的命中率依赖"同一分区的数据是否被调度到同一 executor"。默认调度不保证这一点，导致缓存命中不稳定。本提交通过新开关 `spark.sql.iceberg.executor-cache.locality.enabled`（默认 false），在开启时为"分区表带删除文件"的 FileScanTask 通过分区哈希确定性映射到固定 executor，提升缓存复用率，改善 merge-on-read 性能。

与 0468 的唯一结构差异是：本提交不修改 `core/src/test/java/org/apache/iceberg/MockFileScanTask.java`。原因是 `MockFileScanTask` 位于共享的 `iceberg-core` 测试模块，0468 已经为它新增了带 `Schema`/`PartitionSpec`/`DeleteFile[]` 的构造函数，本提交可直接复用，无需重复修改。因此本提交改动 9 个文件（0468 改 10 个），新增 433 行/删除 42 行，比 0468 少一个文件、少 11 行新增（即 `MockFileScanTask` 那 11 行）。

## 如何达成设计目的

实现路径与 0468 完全一致，只是目标目录从 `spark/v3.5/` 换成 `spark/v3.4/`：在 `SparkSQLProperties` 声明 `EXECUTOR_CACHE_LOCALITY_ENABLED` 常量及默认值；在 `SparkReadConf` 增加两层门控的 `executorCacheLocalityEnabled()`；新增 `SparkPlanningUtil` 工具类（含 `fetchBlockLocations` 和 `assignExecutors`）；在 `SparkUtil` 增加 `executorLocations()` 获取活 executor 列表；改造 `SparkBatch`/`SparkMicroBatchStream`/`SparkInputPartition` 把 location 计算前置；补充 `TestSparkPlanningUtil` 和 `TestMergeOnReadDelete` 测试。所有方法签名、算法逻辑（含 `Math.floorMod` 取模、按 `specId` 缓存哈希函数、仅对分区表带删除文件任务分配等）与 0468 字节级一致。

## 修改详情

### spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMergeOnReadDelete.java

**修改目的**：新增端到端集成测试，验证开启 executor cache locality 后连续 `DELETE` 的功能正确性。

**工作逻辑**：与 0468 中的同名测试完全一致。新增 `testDeleteWithExecutorCacheLocality`：创建分区表，追加 4 批员工数据（hr/hardware），在 `EXECUTOR_CACHE_LOCALITY_ENABLED=true` 下连续执行 `DELETE FROM ... WHERE id = 1` 和 `WHERE id = 3`，断言剩余 `[(2,hardware), (2,hr), (4,hardware), (4,hr)]`。同时新增对 `SparkSQLProperties` 的 import。

### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkReadConf.java

**修改目的**：在读取配置层暴露 `executorCacheLocalityEnabled()`，做"executor cache 必须先开启"的两层门控。

**工作逻辑**：新增公共方法 `executorCacheLocalityEnabled()` 返回 `executorCacheEnabled() && executorCacheLocalityEnabledInternal()`。两个私有方法分别解析 `EXECUTOR_CACHE_ENABLED` 和 `EXECUTOR_CACHE_LOCALITY_ENABLED` 属性，均用 `confParser.booleanConf().sessionConf(...).defaultValue(...).parse()`。逻辑与 0468 完全相同。

### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkSQLProperties.java

**修改目的**：声明新的 SQL 属性常量。

**工作逻辑**：新增 `EXECUTOR_CACHE_LOCALITY_ENABLED = "spark.sql.iceberg.executor-cache.locality.enabled"` 和 `EXECUTOR_CACHE_LOCALITY_ENABLED_DEFAULT = false`。注意 Spark 3.4 的 `SparkSQLProperties` 文件行号上下文（93 行）与 3.5（86 行）略有差异，因为两个版本的属性累积历史不同，但新增内容一致。

### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkUtil.java

**修改目的**：新增 `executorLocations()` 在 driver 端获取所有活 executor 的 `ExecutorCacheTaskLocation` 字符串列表。

**工作逻辑**：新增方法链 `executorLocations()` → `fetchPeers(BlockManager)` → `toExecutorLocation(BlockManagerId)`，以及辅助 `toJavaList(Seq)`。通过 `SparkEnv.get().blockManager().master().getPeers(id)` 拿到 peer executor 的 `BlockManagerId` 列表，转为 `ExecutorCacheTaskLocation.apply(host, executorId).toString()`，排序后收集。import 新增 `SparkEnv`、`ExecutorCacheTaskLocation`、`BlockManager`、`BlockManagerId`、`BlockManagerMaster`、`scala.collection.JavaConverters`、`scala.collection.Seq`。与 0468 完全一致。

### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkBatch.java

**修改目的**：改造 `planInputPartitions`，引入 executor cache locality 分支，preferred locations 计算前置。

**工作逻辑**：新增字段 `executorCacheLocalityEnabled`；`planInputPartitions` 中先调用 `computePreferredLocations()` 得到 `String[][] locations`，再用 for 循环构造 `SparkInputPartition`（取代原 `Tasks.range(...).executeWith(...)` 并行构造），传入 `locations != null ? locations[index] : SparkPlanningUtil.NO_LOCATION_PREFERENCE`。新增 `computePreferredLocations()` 方法：`localityEnabled` 时走 `SparkPlanningUtil.fetchBlockLocations`；否则 `executorCacheLocalityEnabled` 时走 `SparkUtil.executorLocations()` + `SparkPlanningUtil.assignExecutors`；否则返回 null。移除 `Tasks`/`ThreadPools` import。与 0468 完全一致。

### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkInputPartition.java

**修改目的**：简化为只接收预先计算好的 `preferredLocations` 数组。

**工作逻辑**：字段 `preferredLocations` 改为构造器传入（仍 `transient`）；构造器参数从 `boolean localityPreferred` 改为 `String[] preferredLocations`，构造器内不再分支调用 `Util.blockLocations`/`HadoopInputFile.NO_LOCATION_PREFERENCE`，直接赋值；移除 `HadoopInputFile`/`Util` import。与 0468 完全一致。

### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkMicroBatchStream.java

**修改目的**：与 `SparkBatch` 同步改造，preferred locations 计算前置。

**工作逻辑**：`planInputPartitions` 先 `computePreferredLocations(combinedScanTasks)` 批量算 locations，再 for 循环构造 partition 传入。新增 `computePreferredLocations` 仅在 `localityPreferred` 时调用 `SparkPlanningUtil.fetchBlockLocations`，否则返回 null（micro batch 场景不接入 executor cache locality）。移除 `Tasks`/`ThreadPools` import。与 0468 完全一致。

### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkPlanningUtil.java（新增）

**修改目的**：提供 locality 计算的统一工具入口，含 block locations 获取（原有逻辑迁移）和 executor cache locality 分配（新逻辑）。

**工作逻辑**：新增 93 行工具类，包含：
- `NO_LOCATION_PREFERENCE = new String[0]` 常量。
- `fetchBlockLocations(FileIO, List<? extends ScanTaskGroup<?>>)`：并行调用 `Util.blockLocations` 填充 `String[][]`。
- `assignExecutors(List<? extends ScanTaskGroup<?>>, List<String> executorLocations)`：对每个 task group 调用 `assign`，按 `specId` 缓存 `JavaHash<StructLike>`。
- `assign(ScanTaskGroup, List<String>, Map)`：遍历任务，仅对**FileScanTask 且分区表且带删除文件**的任务，用 `partitionHash.hash(fileTask.partition())` + `Math.floorMod(hashCode, executorLocations.size())` 取 executor 索引，加入 location 列表。
- `partitionHash(PartitionSpec)`：`JavaHash.forType(spec.partitionType())`。

文件内容与 0468 的 `spark/v3.5/` 版本字节级一致。

### spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkPlanningUtil.java（新增）

**修改目的**：对 `SparkPlanningUtil.assignExecutors` 做单元测试。

**工作逻辑**：新增 213 行测试类，继承 `TestBaseWithCatalog`，用 `@TestTemplate`。定义 `SCHEMA`、`SPEC_1`（bucket+identity）、`SPEC_2`（identity）和 5 个 executor location。5 个测试用例：
1. `testFileScanTaskWithoutDeletes`：无删除文件 → locations 为空。
2. `testFileScanTaskWithDeletes`：带删除文件、混合 spec → locations 非空。
3. `testFileScanTaskWithUnpartitionedDeletes`：非分区表带删除文件 → locations 为空。
4. `testDataTask`：DataTask → locations 为空。
5. `testUnknownTasks`：未知任务类型 → locations 为空。

辅助方法用 Mockito mock `DataFile`/`DeleteFile` 的 `partition()`；内部类 `MockDataTask`、`UnknownScanTask`。内容与 0468 完全一致。

## 小结

本提交是 0468（Spark 3.5 executor cache locality）向 Spark 3.4 模块的平行移植，目的是让两个 Spark 版本具备一致的能力。改动 9 个文件（比 0468 少 `core/src/test/MockFileScanTask.java`，因该共享测试工具已在 0468 中修改，本提交直接复用）、433 行新增/42 行删除。所有方法签名、算法逻辑、测试用例与 0468 字节级一致，仅目标目录从 `spark/v3.5/` 换成 `spark/v3.4/`。特性语义不变：通过 `spark.sql.iceberg.executor-cache.locality.enabled`（默认 false）开启后，对分区表带删除文件的 FileScanTask 按分区哈希确定性映射到固定 executor，提升 executor 端删除文件缓存命中率，改善 merge-on-read 性能；对无删除文件、非分区表、DataTask、未知任务类型不分配，避免干扰默认调度。
