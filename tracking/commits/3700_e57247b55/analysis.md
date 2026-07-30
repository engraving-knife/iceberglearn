# 提交 3700：Spark 3.4: Backport Async Micro Batch Planner to 3.4 (#16311)

## 提交信息

- **序号**：3700 / 4088
- **哈希**：e57247b55a737cfc3702949181b3a2a3d10174b2
- **短哈希**：e57247b55
- **日期**：2026-05-12 20:29:53 -0700
- **作者**：Kevin Liu
- **提交说明**：Spark 3.4: Backport Async Micro Batch Planner to 3.4 (#16311)
- **PR/Issue**：#16311

## 总体目的

这个提交是 PR #15992 向 Spark 3.4 模块的回移植。它将结构化流式处理的 Micro Batch 规划逻辑重构为支持同步和异步两种模式，引入了异步 Micro Batch Planner，提升流式处理的吞吐量。

此前 Spark 3.4 的 `SparkMicroBatchStream` 将所有规划逻辑内联在单个类中，且仅支持同步规划。当处理大量文件或复杂扫描时，同步规划可能成为吞吐量瓶颈。异步规划允许规划操作在后台执行，与数据读取并行，提升整体性能。

此回移植将 v3.4 的 `SparkMicroBatchStream` 整体替换为 v3.5 的版本（post-#15992），因为 v3.4 存在结构性偏差。重构将规划逻辑提取到独立的 planner 类中。

## 如何达成设计目的

通过以下方式实现回移植：
1. 新建 `BaseSparkMicroBatchPlanner` 抽象基类，包含通用规划逻辑
2. 新建 `SyncSparkMicroBatchPlanner` 和 `AsyncSparkMicroBatchPlanner` 两个实现
3. 新建 `MicroBatchUtils` 工具类
4. 重构 `SparkMicroBatchStream` 使用新的 planner 类
5. 添加配置选项控制同步/异步模式
6. 添加测试覆盖两种模式

## 修改详情

### 新建 Planner 类文件

**修改目的**：提取规划逻辑到独立类。

**工作逻辑**：

- `BaseSparkMicroBatchPlanner.java`（+151 lines）：抽象基类，包含通用的 micro batch 规划逻辑，如确定读取范围、查找最新快照等。
- `SyncSparkMicroBatchPlanner.java`（+249 lines）：同步规划实现，按顺序执行所有规划步骤。
- `AsyncSparkMicroBatchPlanner.java`（+543 lines）：异步规划实现，使用 CompletableFuture 等异步机制并行执行规划步骤。
- `MicroBatchUtils.java`（+69 lines）：micro batch 相关工具方法。
- `SparkMicroBatchPlanner.java`（+47 lines）：planner 工厂/入口类，根据配置选择同步或异步 planner。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkMicroBatchStream.java` (+50/-303 lines)

**修改目的**：重构为使用新的 planner 类。

**工作逻辑**：将内联的规划逻辑替换为委托给 planner 类。提交说明指出，该文件被整体替换为 v3.5 post-#15992 版本，因为 v3.4 存在结构性偏差，且此文件中没有 v3.4 独有的功能。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkReadConf.java` (+33 lines) / `SparkReadOptions.java` (+15 lines) / `SparkSQLProperties.java` (+5 lines)

**修改目的**：添加异步规划配置选项。

**工作逻辑**：新增配置项控制是否启用异步 micro batch 规划。

### 测试文件

**修改目的**：添加同步/异步规划测试覆盖。

**工作逻辑**：新建 `TestAsyncSparkMicroBatchPlanner.java`（+61 lines）、`TestMicroBatchPlanningUtils.java`（+100 lines），更新 `TestStructuredStreamingRead3.java`（+309 lines，整体替换为 v3.5 版本，添加参数化同步/异步覆盖，仅将 `SparkCatalogConfig.SPARK_SESSION` 改为 `SparkCatalogConfig.SPARK` 适配 v3.4）。

## 总结

这是一个大型 Spark 3.4 回移植提交，引入异步 Micro Batch Planner 支持流式处理的异步规划。重构将规划逻辑从 `SparkMicroBatchStream` 提取到独立的 planner 类层次中，支持同步和异步两种模式。这是建立在 PR #16307（SerializableFileIOWithSize 回移植）之上的回移植，共涉及约 1900 行代码变更，是本批次中规模最大的提交之一。
