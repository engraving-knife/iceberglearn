# 提交 0468：Spark 3.5: Support executor cache locality (#9563)

## 提交信息

- **序号**：0468
- **哈希**：c745ac3b6a3b2f24ae5170aa18f9eeec7cf3cbc7
- **短哈希**：c745ac3b6
- **日期**：2024-02-05 10:33:51 -0800
- **作者**：Anton Okolnychyi <aokolnychyi@apple.com>
- **提交说明**：Spark 3.5: Support executor cache locality (#9563)
- **PR/Issue**：#9563

## 总体目的

本提交为 Iceberg 的 Spark 3.5 集成新增了"executor 缓存本地性"（executor cache locality）能力，核心动机是提升带删除文件（delete files）的 merge-on-read 场景下 executor 端缓存的命中率。Iceberg 此前已经引入了 executor 端缓存（由 `spark.sql.iceberg.executor-cache.enabled` 等属性控制，用于在 executor 上缓存 equality delete 文件等），但缓存是否命中高度依赖"同一分区的数据是否被调度到同一个 executor"。在默认调度下，Spark 不保证同一分区的多次扫描任务落在同一 executor 上，导致 executor 缓存命中率不稳定，删除文件被反复从远端存储拉取，拖慢 merge-on-read 性能。

本提交通过一个新的配置开关 `spark.sql.iceberg.executor-cache.locality.enabled`（默认 `false`，保持向后兼容），在开启时让 Iceberg 在 plan 输入分区时为每个 task group 计算一个"优先位置"（preferred location）列表，把带删除文件的分区任务通过分区哈希确定性地映射到固定的 executor 上。这样，针对同一分区的连续 `DELETE`/`MERGE` 操作会倾向于把任务调度到同一个 executor，使其本地缓存的删除文件能被复用，从而减少远端 IO、提升整体吞吐。

需要注意的是，这套机制只对"分区表且任务包含删除文件"的 `FileScanTask` 生效——因为只有这种场景下 executor 缓存才有意义（删除文件相对数据文件通常更小且会被反复读取）。对于无删除文件的任务、非分区表、`DataTask` 或未知任务类型，`assignExecutors` 会返回空数组（即无位置偏好），不影响默认调度。此外，本提交还把原本散落在 `SparkBatch`/`SparkMicroBatchStream` 中的 block location 获取逻辑抽取到新的 `SparkPlanningUtil` 工具类中，统一了 locality 计算入口，为后续扩展（如本提交新增的 executor cache locality）提供了干净的扩展点。

## 如何达成设计目的

实现路径分几步：第一，在 `SparkSQLProperties` 中声明新属性 `EXECUTOR_CACHE_LOCALITY_ENABLED` 及其默认值 `false`，并在 `SparkReadConf` 中通过 `executorCacheLocalityEnabled()` 方法做"executor cache 开启且 locality 开启"两层门控；第二，新增 `SparkPlanningUtil` 工具类，把原有的 `Util.blockLocations` 调用收敛为 `fetchBlockLocations`，并新增 `assignExecutors` 方法实现"分区哈希 → executor 索引"的确定性映射；第三，在 `SparkUtil` 中新增 `executorLocations()`，通过 Spark 的 `BlockManagerMaster.getPeers` 拿到当前所有 executor 的 `ExecutorCacheTaskLocation` 字符串；第四，改造 `SparkBatch` 与 `SparkMicroBatchStream` 的 `planInputPartitions`，把原来在构造 `SparkInputPartition` 时同步计算 preferred locations 的逻辑改为先批量计算 `String[][] locations` 再传入，并让 `SparkInputPartition` 直接接收 `preferredLocations` 数组而非 `boolean localityPreferred`；最后补充 `MockFileScanTask` 的新构造函数和 `TestSparkPlanningUtil`、`TestMergeOnReadDelete` 的测试覆盖。

## 修改详情

### core/src/test/java/org/apache/iceberg/MockFileScanTask.java

**修改目的**：为 `MockFileScanTask` 增加两个新构造函数，支持在测试中传入 `Schema` 和 `PartitionSpec`（以及 delete files），以便 `TestSparkPlanningUtil` 能构造带分区信息的 mock 任务来验证 `assignExecutors` 的分区哈希逻辑。

**工作逻辑**：新增两个构造函数。第一个 `MockFileScanTask(DataFile file, Schema schema, PartitionSpec spec)` 调用父类 `BaseFileScanTask` 构造器，把 schema 和 spec 序列化为 JSON 字符串传入（与原有构造器一致的方式），`length` 设为 `file.fileSizeInBytes()`。第二个 `MockFileScanTask(DataFile file, DeleteFile[] deleteFiles, Schema schema, PartitionSpec spec)` 额外接收 delete files 数组，同样序列化 schema/spec 为 JSON。这两个构造函数让测试可以分别构造"无删除文件"和"有删除文件"的 mock 任务，覆盖 `assignExecutors` 的不同分支。

### spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMergeOnReadDelete.java

**修改目的**：新增端到端集成测试 `testDeleteWithExecutorCacheLocality`，验证在开启 executor cache locality 后连续两次 `DELETE` 仍能得到正确结果。

**工作逻辑**：测试先创建并初始化分区表，按 `dep` 分区追加 4 批员工数据（hr/hardware 各两批，id 1-4）。然后在 `SparkSQLProperties.EXECUTOR_CACHE_LOCALITY_ENABLED=true` 的配置下，连续执行 `DELETE FROM ... WHERE id = 1` 和 `DELETE FROM ... WHERE id = 3`，最后断言剩余行为 `[(2,hardware), (2,hr), (4,hardware), (4,hr)]`。该测试主要验证开启 locality 后功能正确性不被破坏（不直接断言缓存命中，因为那是性能维度），保证分区到 executor 的确定性映射不会导致数据错乱或丢任务。

### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkReadConf.java

**修改目的**：在读取配置层暴露 `executorCacheLocalityEnabled()`，并做"executor cache 必须先开启"的门控。

**工作逻辑**：新增公共方法 `executorCacheLocalityEnabled()`，返回 `executorCacheEnabled() && executorCacheLocalityEnabledInternal()`——即只有当 executor cache 本身开启（`spark.sql.iceberg.executor-cache.enabled` 为 true）且 locality 开关也开启时才返回 true。两个私有方法分别用 `confParser.booleanConf().sessionConf(...).defaultValue(...).parse()` 解析对应属性。这种两层门控设计避免了"只开 locality 不开 cache"这种无意义配置状态。

### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkSQLProperties.java

**修改目的**：声明新的 SQL 属性常量。

**工作逻辑**：新增两个常量：
- `EXECUTOR_CACHE_LOCALITY_ENABLED = "spark.sql.iceberg.executor-cache.locality.enabled"`
- `EXECUTOR_CACHE_LOCALITY_ENABLED_DEFAULT = false`

默认 false 保持向后兼容，用户需要显式开启。

### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkUtil.java

**修改目的**：新增 `executorLocations()` 方法，用于在 driver 端获取当前所有活 executor 的 `ExecutorCacheTaskLocation` 字符串列表。

**工作逻辑**：新增方法链：
- `executorLocations()`：先通过 `SparkEnv.get().blockManager()` 拿到 driver 的 BlockManager，调用 `fetchPeers` 获取所有 peer executor 的 `BlockManagerId` 列表，再对每个 id 调用 `toExecutorLocation` 转成 `ExecutorCacheTaskLocation.apply(id.host(), id.executorId()).toString()` 形式的字符串，最后排序后收集为 List。排序保证结果确定性（对后续 `assignExecutors` 的哈希取模稳定性重要，因为 executor 列表顺序变化会导致同一分区映射到不同 executor）。
- `fetchPeers(BlockManager blockManager)`：通过 `blockManager.master().getPeers(id)` 获取 peers，`getPeers` 返回 Scala `Seq`，需转 Java List。
- `toJavaList(Seq<T> seq)`：用 `JavaConverters.seqAsJavaListConverter` 做 Scala Seq → Java List 转换。
- `toExecutorLocation(BlockManagerId id)`：构造 Spark 的 `ExecutorCacheTaskLocation` 字符串，这是 Spark 调度器识别的 preferred location 格式。

import 块相应新增 `SparkEnv`、`ExecutorCacheTaskLocation`、`BlockManager`、`BlockManagerId`、`BlockManagerMaster`、`scala.collection.JavaConverters`、`scala.collection.Seq`。

### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkBatch.java

**修改目的**：改造 `planInputPartitions`，引入 executor cache locality 分支，并把 preferred locations 计算前置为批量调用。

**工作逻辑**：
1. 新增字段 `executorCacheLocalityEnabled`，在构造器中从 `readConf.executorCacheLocalityEnabled()` 读取。
2. `planInputPartitions` 中先调用 `computePreferredLocations()` 得到 `String[][] locations`（可能为 null），然后用普通 for 循环（取代原来基于 `Tasks.range(...).executeWith(...)` 的并行化构造）创建 `SparkInputPartition`，每个 partition 传入 `locations != null ? locations[index] : SparkPlanningUtil.NO_LOCATION_PREFERENCE`。
3. 新增私有方法 `computePreferredLocations()`：
   - 若 `localityEnabled`（原有的 block locality，基于 HDFS block 位置）：调用 `SparkPlanningUtil.fetchBlockLocations(table.io(), taskGroups)` 返回基于文件块位置的二维数组。
   - 否则若 `executorCacheLocalityEnabled`：调用 `SparkUtil.executorLocations()` 拿到 executor 列表，若非空则调用 `SparkPlanningUtil.assignExecutors(taskGroups, executorLocations)` 返回基于分区哈希的二维数组。
   - 否则返回 `null`（表示无偏好，由调用方回退到 `NO_LOCATION_PREFERENCE`）。

值得注意：原来 `SparkBatch` 用 `Tasks.range(...).executeWith(localityEnabled ? ThreadPools.getWorkerPool() : null)` 并行构造 `InputPartition`，是因为构造时要在 `SparkInputPartition` 内部同步调用 `Util.blockLocations`（涉及 HDFS RPC，较慢）。改造后 preferred locations 已在外部批量计算完毕（`fetchBlockLocations` 内部仍并行），构造 `SparkInputPartition` 只是把数组赋值，无需并行，因此改回简单 for 循环。同时移除了对 `Tasks` 和 `ThreadPools` 的 import（已迁移到 `SparkPlanningUtil`）。

### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkInputPartition.java

**修改目的**：简化 `SparkInputPartition`，让其只接收预先计算好的 `preferredLocations` 数组，不再内部决定是否计算 locations。

**工作逻辑**：
- 字段 `preferredLocations` 从 `transient` 后置计算改为构造器传入（仍标记 `transient`，因为它是不可序列化的 host 字符串数组，由 driver 计算后传给 Spark 调度器使用，无需随 partition 序列化到 executor）。
- 构造器参数从 `boolean localityPreferred` 改为 `String[] preferredLocations`，构造器体内不再根据 `localityPreferred` 分支调用 `Util.blockLocations` 或回退到 `HadoopInputFile.NO_LOCATION_PREFERENCE`，直接赋值。
- 移除了对 `HadoopInputFile` 和 `Util` 的 import。

这种改造把 location 计算职责从 partition 自身剥离到上层（`SparkBatch`/`SparkMicroBatchStream` + `SparkPlanningUtil`），让 `SparkInputPartition` 回归为纯粹的数据持有对象，单一职责更清晰。

### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkMicroBatchStream.java

**修改目的**：与 `SparkBatch` 同步改造，把 preferred locations 计算前置，使用 `SparkPlanningUtil.fetchBlockLocations`。

**工作逻辑**：micro batch 流式读取场景下，`planInputPartitions` 改造方式与 `SparkBatch` 一致：先 `computePreferredLocations(combinedScanTasks)` 批量算 locations，再用 for 循环构造 `SparkInputPartition` 传入 `locations[index]` 或 `NO_LOCATION_PREFERENCE`。新增私有方法 `computePreferredLocations` 仅在 `localityPreferred` 为 true 时调用 `SparkPlanningUtil.fetchBlockLocations`，否则返回 null。注意 micro batch 场景**未**接入 executor cache locality 分支——因为流式场景的 executor 缓存复用语义与批处理不同，本提交仅在 `SparkBatch` 中启用 executor cache locality。同时移除了对 `Tasks`、`ThreadPools` 的 import。

### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkPlanningUtil.java（新增）

**修改目的**：提供 locality 计算的统一工具入口，包含 block locations 获取（原有逻辑迁移）和 executor cache locality 分配（新逻辑）。

**工作逻辑**：新增包级可见的工具类，包含：
- `NO_LOCATION_PREFERENCE = new String[0]`：空数组常量，表示无位置偏好，取代原来对 `HadoopInputFile.NO_LOCATION_PREFERENCE` 的依赖。
- `fetchBlockLocations(FileIO io, List<? extends ScanTaskGroup<?>> taskGroups)`：把原来在 `SparkBatch`/`SparkMicroBatchStream` 中内联的"并行调用 `Util.blockLocations` 获取每个 task group 的文件块位置"逻辑提取出来。用 `Tasks.range(...).executeWith(ThreadPools.getWorkerPool())` 并行填充 `String[][] locations`。
- `assignExecutors(List<? extends ScanTaskGroup<?>> taskGroups, List<String> executorLocations)`：executor cache locality 的核心。对每个 task group 调用 `assign` 计算其 preferred locations。维护一个 `Map<Integer, JavaHash<StructLike>> partitionHashes`，按 `specId` 缓存分区哈希函数（避免对每个任务重复构造）。
- `assign(ScanTaskGroup, List<String> executorLocations, Map partitionHashes)`：遍历 task group 中的任务，仅对**是 FileScanTask**、**所属 spec 是分区表**、**且任务带删除文件（`!fileTask.deletes().isEmpty()`）**的任务做分配：用 `specId` 对应的 `JavaHash<StructLike>` 对 `fileTask.partition()` 求哈希，再 `Math.floorMod(partitionHashCode, executorLocations.size())` 得到 executor 索引，把对应 executor location 加入列表。最终返回数组。这意味着：一个 task group 的 preferred locations 是其所有"带删除文件的分区任务"对应的 executor 集合；无删除文件、非分区表、DataTask、未知任务类型都不产生 location。
- `partitionHash(PartitionSpec spec)`：用 `JavaHash.forType(spec.partitionType())` 构造分区类型的哈希函数。

设计要点：用 `Math.floorMod` 而非 `%` 是为了处理负哈希（Java 的 `hashCode` 可能为负），保证索引非负。按 `specId` 缓存哈希函数是因为同一查询可能涉及多个 partition spec（演进过的表），不同 spec 的分区类型不同，哈希函数也不同。只对带删除文件的任务分配，是因为 executor cache 主要缓存的就是删除文件，无删除文件时缓存无意义。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkPlanningUtil.java（新增）

**修改目的**：对 `SparkPlanningUtil.assignExecutors` 做单元测试，覆盖各类任务的边界条件。

**工作逻辑**：新增 213 行测试类，继承 `TestBaseWithCatalog`，用 `@TestTemplate`（JUnit 5 参数化）。定义两个 spec：`SPEC_1`（bucket(16)+identity）和 `SPEC_2`（identity），以及 5 个模拟 executor location。共 5 个测试用例：

1. `testFileScanTaskWithoutDeletes`：3 个 SPEC_1 下的 FileScanTask 无删除文件 → 期望 `locations[0]` 为空。验证"无删除文件不分配"。
2. `testFileScanTaskWithDeletes`：3 个带删除文件的 FileScanTask，分属 SPEC_1（分区 `Row.of("k2", null)`，2 个文件）和 SPEC_2（分区 `Row.of("k1")`，1 个文件）→ 期望 `locations[0].length >= 1`。验证"有删除文件会分配，且能处理不同分区类型/大小"。
3. `testFileScanTaskWithUnpartitionedDeletes`：两个 task group，分别用 `Row.of()` 和 `null` 分区的非分区 spec + 删除文件 → 期望两个 group 的 locations 都为空。验证"非分区表即使有删除文件也不分配"。
4. `testDataTask`：3 个 `MockDataTask`（自定义实现 `DataTask` 接口）→ 期望 locations 为空。验证"DataTask 不分配"。
5. `testUnknownTasks`：2 个 `UnknownScanTask`（空实现 `ScanTask`）→ 期望 locations 为空。验证"未知任务类型不分配"。

辅助方法 `mockDataFile`/`mockDeleteFiles`/`mockDeleteFile` 用 Mockito mock 出 `DataFile`/`DeleteFile`，仅 stub `partition()` 方法。内部类 `MockDataTask` 继承 `MockFileScanTask` 并实现 `DataTask`，`UnknownScanTask` 是空 `ScanTask` 实现。

## 小结

本提交为 Iceberg Spark 3.5 集成引入了 executor cache locality 特性，通过新配置 `spark.sql.iceberg.executor-cache.locality.enabled`（默认 false）开启后，在 plan 阶段把"分区表带删除文件"的 FileScanTask 通过分区哈希确定性映射到固定 executor，提升 executor 端删除文件缓存的命中率，改善 merge-on-read 性能。改动共 10 个文件、444 行新增/42 行删除：新增 `SparkPlanningUtil` 工具类统一 locality 计算入口（含原有 block locations 迁移和新增 executor 分配逻辑），新增 `SparkUtil.executorLocations()` 获取活 executor 列表，改造 `SparkBatch`/`SparkMicroBatchStream`/`SparkInputPartition` 把 location 计算前置并简化 partition 构造，新增配置与门控逻辑，并补充了 `TestSparkPlanningUtil` 单元测试和 `TestMergeOnReadDelete` 端到端测试。设计上仅对分区表带删除文件的任务生效，避免对无缓存收益场景产生调度干扰，且默认关闭保持向后兼容。
