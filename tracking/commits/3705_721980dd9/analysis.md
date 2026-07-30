# 提交 3705：Spark: Disable min/max aggregation push down for string under any mode (#16320)

## 提交信息

- **序号**：3705 / 4088
- **哈希**：721980dd9c43e0d2daed9213430242443472a9c9
- **短哈希**：721980dd9
- **日期**：2026-05-13 15:12:28 -0700
- **作者**：Dong Wang
- **提交说明**：Spark: Disable min/max aggregation push down for string under any mode (#16320)
- **PR/Issue**：#16320

## 总体目的

这个提交修改了 Spark 的聚合下推逻辑，在所有模式下都禁用字符串类型的 min/max 聚合下推。此前，仅在 `MetricsModes.Truncate` 模式下禁用字符串的 min/max 下推，因为在截断模式下 lower_bounds 和 upper_bounds 可能被截断，无法保证 min/max 的正确性。

但问题在于：即使当前模式不是 Truncate（例如 `MetricsModes.Full` 或 `MetricsModes.None`），数据文件中的 lower_bounds 和 upper_bounds 可能是在之前的某个时间点以 Truncate 模式写入的。这意味着即使当前配置不截断，历史数据的统计信息可能已经被截断过。如果在这种情况下下推 min/max 聚合，可能基于截断后的统计信息得出错误的结果。

因此，修复方案是：无论当前模式是什么，只要列类型是 STRING，就禁用 min/max 聚合下推，因为无法确定数据文件的统计信息是否曾被截断。

## 如何达成设计目的

通过修改 `SparkScanBuilder` 中的聚合下推判断逻辑，将字符串类型的 min/max 禁用条件从仅在 Truncate 模式下扩展到所有模式。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java` (+10/-9 lines)

**修改目的**：在所有模式下禁用字符串 min/max 下推。

**工作逻辑**：

修改前（仅在 Truncate 模式下禁用）：
```java
} else if (mode instanceof MetricsModes.Truncate) {
  // lower_bounds and upper_bounds may be truncated, so disable push down
  if (aggregate.type().typeId() == Type.TypeID.STRING) {
    if (aggregate.op() == Expression.Operation.MAX
        || aggregate.op() == Expression.Operation.MIN) {
      LOG.info("Skipping aggregate pushdown: ...", colName);
      return false;
    }
  }
}
```

修改后（所有模式下禁用）：
```java
} else if (aggregate.type().typeId() == Type.TypeID.STRING) {
  // lower_bounds and upper_bounds may have been truncated before, so disable push down
  // regardless of the current mode
  if (aggregate.op() == Expression.Operation.MAX
      || aggregate.op() == Expression.Operation.MIN) {
    LOG.info("Skipping aggregate pushdown: ...", colName);
    return false;
  }
}
```

关键变化是将字符串类型的检查从 `Truncate` 模式的条件分支中提取出来，使其成为独立的条件分支。这样无论当前 metrics mode 是什么（None、Truncate、Full），只要列类型是 STRING 且操作是 MIN 或 MAX，就会跳过下推。

### 其他 Spark 版本模块

同样的修改应用到 `spark/v3.4`、`spark/v4.0`、`spark/v4.1` 模块。

### 测试文件 (各 +10/-10 lines)

**修改目的**：更新测试以反映新的行为。

**工作逻辑**：更新 `TestAggregatePushDown.java` 中相关测试用例的预期，因为字符串 min/max 下推现在在所有模式下都被禁用。

## 总结

这是一个正确性修复提交，将字符串类型 min/max 聚合下推的禁用范围从仅 Truncate 模式扩展到所有模式。修复的核心洞察是：数据文件的统计信息可能在历史某时刻被截断过，即使当前模式不截断也无法保证统计信息的完整性。这一修复避免基于可能被截断的统计信息得出错误的 min/max 结果。
