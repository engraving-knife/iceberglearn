# 提交 3616：Spark 4.0: Backport Aync Micro Batch Planner Feature (#15876)

## 提交信息

- **序号**：3616 / 4088
- **哈希**：df00c156d41deddd0dc979f2867ad6f81cb0e246
- **短哈希**：df00c156d
- **日期**：2026-04-29 07:44:17 -0700
- **作者**：Ruijing Li
- **提交说明**：Spark 4.0: Backport Aync Micro Batch Planner Feature (#15876)
- **PR/Issue**：#15876

## 总体目的

这个提交将异步 Micro Batch Planner 功能反向移植到 Spark 4.0 版本，与提交 3615（Spark 3.5 的 backport）是平行的操作。

Iceberg 同时维护多个 Spark 版本，异步 Micro Batch Planner 功能需要同步到所有支持的版本。提交 3615 首先将功能 backport 到 Spark 3.5，本提交将相同的功能应用到 Spark 4.0。

## 如何达成设计目的

将 Spark 3.5 中的异步 Micro Batch Planner 实现原样应用到 Spark 4.0 的对应文件中。涉及 12 个文件，改动内容与提交 3615 完全一致。

## 修改详情

### 配置文件

- `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkReadConf.java` (+33/-0 lines)：新增 4 个异步规划配置读取方法。
- `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkReadOptions.java` (+15/-0 lines)：定义异步规划选项常量。
- `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkSQLProperties.java` (+5/-0 lines)：定义 session 级 SQL 属性。

### 新增规划器类

- `AsyncSparkMicroBatchPlanner.java` (+543 lines, new)：异步规划器实现，后台线程提前规划 micro batch。
- `BaseSparkMicroBatchPlanner.java` (+151 lines, new)：共享规划逻辑基类。
- `SyncSparkMicroBatchPlanner.java` (+249 lines, new)：同步规划器，保持原有行为。
- `SparkMicroBatchPlanner.java` (+47 lines, new)：规划器接口。
- `MicroBatchUtils.java` (+69 lines, new)：工具类。

### 重构文件

- `SparkMicroBatchStream.java` (+25/-303 lines)：将规划逻辑提取到独立 Planner 类，根据配置选择同步或异步规划器。

### 测试文件

- `TestAsyncSparkMicroBatchPlanner.java` (+61 lines, new)：异步规划器测试。
- `TestMicroBatchPlanningUtils.java` (+100 lines, new)：工具类测试。
- `TestStructuredStreamingRead3.java` (+286/-0 lines)：扩展流式读取测试。

## 总结

这个提交是提交 3615 的平行操作，将异步 Micro Batch Planner 功能同步到 Spark 4.0 版本。通过后台线程异步规划 micro batch，减少流式处理延迟。重构将规划逻辑提取到独立的 Planner 类层次中，新增配置选项使性能可调。默认不启用，保持向后兼容。
