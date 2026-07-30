# 提交 3423：Spark: Add named constant for `NO_ADVISORY_PARTITION_SIZE` (#15681)

## 提交信息

- **序号**：3423 / 4088
- **哈希**：a16baffcd3135617a778faebd9b5a8edd1e98435
- **短哈希**：a16baffcd3
- **日期**：2026-03-20 04:28:05 -0700
- **作者**：jbewing
- **提交说明**：Spark: Add named constant for `NO_ADVISORY_PARTITION_SIZE` (#15681)
- **PR/Issue**：#15681

## 总体目的

为 `SparkWriteRequirements` 中的魔术数字 `0`（表示无建议分区大小）添加命名常量 `NO_ADVISORY_PARTITION_SIZE`，提升代码可读性。同时将 `advisoryPartitionSize` 的校验逻辑从 `advisoryPartitionSize()` getter 方法移到构造函数中，确保字段值在构造时就正确，而非每次调用 getter 时重新计算。

## 如何达成设计目的

1. 新增 `private static final long NO_ADVISORY_PARTITION_SIZE = 0` 常量
2. 在构造函数中执行校验：当 distribution 为 `UnspecifiedDistribution` 时，`advisoryPartitionSize` 设为 `NO_ADVISORY_PARTITION_SIZE`
3. `advisoryPartitionSize()` getter 方法直接返回字段值，不再每次检查
4. 在 Spark 3.5、4.0、4.1 三个版本中应用相同改动

## 修改详情

### `spark/v{3.5,4.0,4.1}/spark/src/main/java/org/apache/iceberg/spark/SparkWriteRequirements.java` (+13/-4 lines each)

**修改目的**：添加命名常量并优化校验逻辑位置。

**工作逻辑**：
- 新增常量 `NO_ADVISORY_PARTITION_SIZE = 0`
- `EMPTY` 常量使用 `NO_ADVISORY_PARTITION_SIZE` 替代魔术数字
- 构造函数中：
  ```java
  this.advisoryPartitionSize =
      distribution instanceof UnspecifiedDistribution
          ? NO_ADVISORY_PARTITION_SIZE
          : advisoryPartitionSize;
  ```
- getter 方法简化为 `return advisoryPartitionSize;`

## 总结

本提交为 `SparkWriteRequirements` 添加了 `NO_ADVISORY_PARTITION_SIZE` 命名常量替代魔术数字 0，并将 advisoryPartitionSize 的校验逻辑从 getter 移到构造函数中，确保字段值在构造时就正确。改动应用到 Spark 3.5、4.0、4.1 三个版本。
