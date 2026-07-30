# 提交 2258：spark 4.0: SPJ: add bucket reducer using gcd (#13167)

## 提交信息

- **序号**：2258 / 4088
- **哈希**：c41fd6457fe8e747cbf7a938a5ca0cc67f1779ee
- **短哈希**：c41fd6457
- **日期**：2025-06-19 16:10:57 -0700
- **作者**：Himadri Pal
- **提交说明**：spark 4.0: SPJ: add bucket reducer using gcd
- **PR/Issue**：#13167

## 总体目的

本提交为 Spark 4.0 的存储分区连接（Storage Partitioned Joins, SPJ）添加了基于最大公约数（GCD）的桶缩减器（bucket reducer）。在 Iceberg 中，表可以使用 bucket 分桶策略对数据进行分区。当两个表使用不同数量的桶进行连接时，如果桶数存在公约数关系，可以通过缩减桶编号使连接在更粗粒度上对齐，从而避免不必要的数据重分布。

例如，表 A 有 8 个桶，表 B 有 4 个桶，它们的 GCD 为 4。通过将表 A 的桶编号 `bucketNo % 4`，可以将表 A 的 8 个桶映射到 4 个逻辑桶组，与表 B 的 4 个桶一一对应，使得存储分区连接成为可能。这是一种优化，能够在不移动数据的情况下利用已有的分桶布局进行连接操作。

Spark 4.0 引入了 `ReducibleFunction` 接口，允许函数声明其自身的缩减逻辑。本提交使 Iceberg 的 `BucketFunction` 实现该接口，在 Spark 执行 SPJ 时提供桶缩减能力。

## 如何达成设计目的

- 使 `BucketBase` 类实现 Spark 4.0 的 `ReducibleFunction<Integer, Integer>` 接口。
- 实现 `reducer()` 方法，计算两个桶函数的桶数的 GCD，如果 GCD 大于 1 且不等于当前桶数，则返回一个 `BucketReducer` 实例。
- 新增 `BucketReducer` 内部类，实现 `Reducer<Integer, Integer>` 接口，通过 `bucketNo % commonDivisor` 实现桶编号缩减。
- 新增 `gcd()` 辅助方法使用 `BigInteger` 计算最大公约数。
- 新增测试用例验证不同桶数组合下的 SPJ 行为。

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/functions/BucketFunction.java` (修改, +38/-1 lines)

**修改目的**：使 BucketFunction 支持 ReducibleFunction 接口，提供 GCD 桶缩减能力。

**工作逻辑**：
1. `BucketBase` 类声明实现 `ReducibleFunction<Integer, Integer>` 接口。
2. 新增 `gcd(int num1, int num2)` 方法，使用 `BigInteger.valueOf(num1).gcd(BigInteger.valueOf(num2)).intValue()` 计算两个桶数的最大公约数。
3. 实现 `reducer(int thisNumBuckets, ReducibleFunction<?, ?> otherBucketFunction, int otherNumBuckets)` 方法：首先检查对方函数是否也是 `BucketBase` 实例，然后计算两者桶数的 GCD。如果 GCD > 1 且 GCD != thisNumBuckets（即当前桶数可以被缩减），则返回 `new BucketReducer(commonDivisor)`；否则返回 null 表示不可缩减。
4. 新增 `BucketReducer` 静态内部类，实现 `Reducer<Integer, Integer>` 和 `Serializable` 接口。`reduce(Integer bucketNo)` 方法返回 `bucketNo % this.commonDivisor`，将桶编号缩减到公约数范围内。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/sql/TestStoragePartitionedJoins.java` (修改, +146/0 lines)

**修改目的**：验证 GCD 桶缩减在 SPJ 场景下的正确性。

**工作逻辑**：新增多个测试用例，覆盖不同桶数组合的连接场景，包括：桶数有公约数的情况（如 8 桶 vs 4 桶）、桶数相同的情况、桶数互质的情况等。验证在这些场景下 SPJ 是否能正确利用桶缩减进行连接，以及结果数据是否正确。

## 总结

本提交为 Spark 4.0 的 Iceberg BucketFunction 添加了 GCD 桶缩减能力，通过实现 `ReducibleFunction` 接口使得存储分区连接（SPJ）能够在不同桶数的表之间利用公约数进行桶对齐优化。这是一种性能优化，避免了不必要的数据 shuffle。核心逻辑简洁——计算桶数 GCD 后通过取模缩减桶编号，但需要与 Spark 4.0 的 ReducibleFunction 机制正确集成。
