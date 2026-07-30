# 提交 3284：Spark 4.1: Use scan filter for conflict detection (#15365)

## 提交信息

- **序号**：3284 / 4088
- **哈希**：881417433a881fbe832f338c31e4d6278625e018
- **短哈希**：881417433
- **日期**：2026-02-18
- **作者**：Anton Okolnychyi
- **提交说明**：Spark 4.1: Use scan filter for conflict detection (#15365)
- **PR/Issue**：#15365

## 总体目的

本提交将 Spark 4.1 写入提交阶段的冲突检测过滤器（conflict detection filter）构造逻辑从各写入类上移到 `SparkScan` 基类，消除重复代码并统一命名。在此之前，`SparkWrite` 与 `SparkPositionDeltaWrite` 在提交时都需要根据扫描的过滤表达式列表构造一个合并的 `Expression` 作为冲突检测过滤器，各自实现了一个私有方法：遍历 `scan.filterExpressions()` 列表，用 `Expressions.and` 逐个归约，初始值为 `Expressions.alwaysTrue()`。两处实现完全相同，属于重复代码。同时 `SparkScan` 中字段名 `filterExpressions` 与 `BaseSparkScanBuilder`（#15360 引入）中的 `filters` 命名不一致，访问方法 `filterExpressions()` 也与基类的 `filters()`/`filter()` 不统一。

本提交在 `SparkScan` 中新增 `filter()` 方法（将 filters 列表归约为合取表达式），将字段与方法重命名为 `filters`/`filters()` 以与 `BaseSparkScanBuilder` 对齐，并在 `SparkWrite`、`SparkPositionDeltaWrite` 中直接调用 `scan.filter()` 替代各自的私有方法。这样冲突检测过滤器的构造逻辑只保留在 `SparkScan` 一处，写入类更简洁，命名也更一致。

## 如何达成设计目的

在 `SparkScan` 中将字段 `filterExpressions` 重命名为 `filters`、方法 `filterExpressions()` 重命名为 `filters()`，新增 `filter()` 方法返回归约后的合取表达式。删除 `SparkWrite.conflictDetectionFilter()` 与 `SparkPositionDeltaWrite.conflictDetectionFilter(SparkBatchQueryScan)` 两个私有方法，将调用点改为 `scan.filter()`。同步更新测试工具类 `PlanUtils` 中的方法名引用。改动涉及 v4.1 下四个文件。

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkScan.java` (+11/-6 lines)

**修改目的**：统一过滤器字段命名并提供归约后的 `filter()` 方法。

**工作逻辑**：
字段 `private final List<Expression> filterExpressions` 重命名为 `filters`，构造方法中赋值 `this.filters = filters != null ? filters : Collections.emptyList()`。方法 `filterExpressions()` 重命名为 `filters()`。新增 `protected Expression filter()` 方法，实现为 `filters.stream().reduce(Expressions.alwaysTrue(), Expressions::and)`，即把过滤表达式列表归约为一个合取表达式，列表为空时返回 `alwaysTrue()`。`filtersDesc()` 中 `Spark3Util.describe(filterExpressions)` 改为 `Spark3Util.describe(filters)`。`estimateStatistics()` 中 `filterExpressions.isEmpty()` 改为 `filters.isEmpty()`。新增 `Expressions` 的 import。该 `filter()` 方法与 `BaseSparkScanBuilder.filter()` 行为一致，使扫描对象与扫描构建器对外提供统一的过滤器访问接口。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkWrite.java` (+2/-16 lines)

**修改目的**：用 `scan.filter()` 替代重复的冲突检测过滤器构造逻辑。

**工作逻辑**：
删除私有方法 `conflictDetectionFilter()`，该方法原实现为从 `scan.filterExpressions()` 取表达式列表，遍历并用 `Expressions.and` 归约为单个过滤器。在覆盖写入提交路径（`overwrite` 与 copy-on-write `commit`）中，两处 `Expression conflictDetectionFilter = conflictDetectionFilter();` 改为 `Expression conflictDetectionFilter = scan.filter();`，随后仍调用 `overwriteFiles.conflictDetectionFilter(conflictDetectionFilter)` 设置到 Iceberg 的 `OverwriteFiles` 操作上。移除不再使用的 `List` 与 `Expressions` 相关 import。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeltaWrite.java` (+1/-12 lines)

**修改目的**：用 `scan.filter()` 替代重复的冲突检测过滤器构造逻辑。

**工作逻辑**：
删除私有方法 `conflictDetectionFilter(SparkBatchQueryScan queryScan)`，该方法原实现与 `SparkWrite` 中的完全相同——从 `queryScan.filterExpressions()` 取列表并归约。在 row delta 提交路径中，`Expression conflictDetectionFilter = conflictDetectionFilter(scan);` 改为 `Expression conflictDetectionFilter = scan.filter();`，随后调用 `rowDelta.conflictDetectionFilter(conflictDetectionFilter)`。移除不再使用的 `Expressions` import。

### `spark/v4.1/spark-extensions/src/test/java/org/apache/iceberg/spark/source/PlanUtils.java` (+1/-1 lines)

**修改目的**：适配扫描方法重命名。

**工作逻辑**：
测试工具方法中 `batchQueryScan.filterExpressions().stream()` 改为 `batchQueryScan.filters().stream()`，与 `SparkScan` 的新方法名保持一致。

## 总结

本提交将冲突检测过滤器的归约逻辑从 `SparkWrite` 与 `SparkPositionDeltaWrite` 上移到 `SparkScan.filter()`，消除了两处重复实现，并将 `SparkScan` 的字段与方法命名与 `BaseSparkScanBuilder` 统一为 `filters`/`filters()`/`filter()`，使扫描构建器与扫描对象对外提供一致的过滤器访问接口，代码更简洁且更易维护。
