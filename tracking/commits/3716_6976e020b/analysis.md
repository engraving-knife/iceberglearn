# 提交 3716：Spark: Backport #16088 to Spark 3.4, 3.5, 4.0 (#16344)

## 提交信息

- **序号**：3716 / 4088
- **哈希**：6976e020b894f6a6777704df2b8c4458cb291ae9
- **短哈希**：6976e020b
- **日期**：2026-05-14 19:35:48 -0700
- **作者**：Karuppayya
- **提交说明**：Spark: Backport #16088 to Spark 3.4, 3.5, 4.0 (#16344)
- **PR/Issue**：#16344（原始 PR #16088）

## 总体目的

本提交将 PR #16088 的改动回移植到 Spark 3.4、3.5 和 4.0 三个版本分支，为 Iceberg 的 Spark 集成增加对自适应 split size（adaptive split size）的可配置性。原 PR 主要解决在 Spark 读取 Iceberg 表时无法通过 SQL 会话配置（session conf）覆盖自适应 split size 行为的问题，同时引入了一个新的可调参数 `splitParallelism` 用于控制自适应 split 调整时的并行度。

具体来说，原先的 `adaptiveSplitSizeEnabled()` 配置只能从表属性读取，无法通过 Spark SQL 会话级别进行动态调整，使得用户在不同作业之间切换策略不够灵活。此外，自适应 split 调整所使用的并行度直接复用了 `parallelism()` 方法的值（即 `max(spark.default.parallelism, spark.sql.shuffle.partitions)`），用户无法针对 split 调整单独指定并行度。

## 如何达成设计目的

设计上通过三处协同改动实现：在 `SparkSQLProperties` 中新增两个会话配置键，在 `SparkReadConf` 中让 `adaptiveSplitSizeEnabled()` 优先读取会话配置并新增 `splitParallelism()` 方法，最后在 `SparkScan.adjustSplitSize()` 中改用新的 `splitParallelism()` 方法并补充日志输出。为了保障配置健壮性，新增了 `splitParallelism()` 的参数校验（必须大于 0），并配套新增 `TestSparkReadConf` 测试类覆盖默认值、会话覆盖和非法值场景。改动同时作用于 3.4、3.5、4.0 三个 Spark 版本分支。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkReadConf.java` (+13/-0 lines)

**修改目的**：让自适应 split size 开关支持会话配置覆盖，并新增专用的 split 并行度配置方法。

**工作逻辑**：
- `adaptiveSplitSizeEnabled()` 通过 `confParser.booleanConf().sessionConf(...)` 链式调用新增了 `SparkSQLProperties.READ_ADAPTIVE_SPLIT_SIZE_ENABLED` 作为会话级配置源，使其优先级高于表属性。
- 新增 `splitParallelism()` 方法，读取会话配置 `READ_ADAPTIVE_SPLIT_SIZE_PARALLELISM`，未设置时回退到 `parallelism()`。使用 `Preconditions.checkArgument(parallelism > 0, ...)` 对非法值进行校验，确保下游计算不会因 0 或负并行度出错。

```java
public int splitParallelism() {
  int parallelism =
      confParser
          .intConf()
          .sessionConf(SparkSQLProperties.READ_ADAPTIVE_SPLIT_SIZE_PARALLELISM)
          .defaultValue(parallelism())
          .parse();
  Preconditions.checkArgument(parallelism > 0, "Split parallelism must be > 0: %s", parallelism);
  return parallelism;
}
```

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkSQLProperties.java` (+9/-0 lines)

**修改目的**：声明两个新的 Spark SQL 会话配置键。

**工作逻辑**：
- `READ_ADAPTIVE_SPLIT_SIZE_ENABLED`：`spark.sql.iceberg.read.adaptive-split-size.enabled`，用于控制自适应 split sizing 是否启用。
- `READ_ADAPTIVE_SPLIT_SIZE_PARALLELISM`：`spark.sql.iceberg.read.adaptive-split-size.parallelism`，覆盖自适应 split sizing 所用的并行度，未设置时默认使用 `max(spark.default.parallelism, spark.sql.shuffle.partitions)`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkScan.java` (+11/-2 lines)

**修改目的**：在 `adjustSplitSize()` 中改用新的 `splitParallelism()`，并补充调试日志。

**工作逻辑**：
原先 `adjustSplitSize` 调用 `readConf.parallelism()` 取得并行度，现在改为 `readConf.splitParallelism()`。当调整后的 split size 与原值不同时，输出 DEBUG 日志记录旧值、新值、表名与并行度，便于排查与性能调优。

```java
int parallelism = readConf.splitParallelism();
long adjustedSplitSize = TableScanUtil.adjustSplitSize(scanSize, parallelism, splitSize);
if (adjustedSplitSize != splitSize) {
  LOG.debug(
      "Adjusted split size from {} to {} for table {} with parallelism {}",
      splitSize, adjustedSplitSize, table().name(), parallelism);
}
return adjustedSplitSize;
```

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/TestSparkReadConf.java` (+95/-0 lines, 新增文件)

**修改目的**：为新的 `splitParallelism()` 配置方法添加单元测试覆盖。

**工作逻辑**：
新建 `TestSparkReadConf` 测试类，包含 4 个测试用例：
- `testSplitParallelismDefault`：验证未设置会话配置时 `splitParallelism()` 等于 `parallelism()`。
- `testSplitParallelismSessionConf`：验证会话配置 `READ_ADAPTIVE_SPLIT_SIZE_PARALLELISM=42` 能正确覆盖默认值。
- `testSplitParallelismRejectsZero`：验证传入 0 时抛出 `IllegalArgumentException` 并包含 "Split parallelism must be > 0"。
- `testSplitParallelismRejectsNegative`：验证传入 -5 时同样抛出异常。

### Spark 3.5 与 4.0 同名文件 (+13/-0、+9/-0、+11/-2、+95/-0 lines each)

**修改目的**：将上述所有改动同步到 Spark 3.5 与 4.0 版本分支，保持多版本行为一致。

**工作逻辑**：与 3.4 版本完全相同的改动，分别位于 `spark/v3.5/spark/...` 与 `spark/v4.0/spark/...` 目录下。

## 总结

本提交通过回移植 PR #16088，使 Spark 3.4/3.5/4.0 用户均能通过会话级 SQL 配置控制自适应 split size 的开关与并行度，弥补了原先仅能通过表属性控制的局限，并增加了参数校验与日志输出以提高健壮性与可观测性。配套的测试用例覆盖了默认、覆盖与非法值场景，保证了配置逻辑的正确性。这是一项面向生产调优场景的实用性增强。
