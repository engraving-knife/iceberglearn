# 提交 2295：spark 4.0 : SPJ : add hour to day reducer (#13166)

## 提交信息

- **序号**：2295 / 4088
- **哈希**：ff0904c3fde395403d13c56504832ecaa3fa8aeb
- **短哈希**：ff0904c3f
- **日期**：2025-06-30 17:13:20 -0700
- **作者**：Himadri Pal
- **提交说明**：spark 4.0 : SPJ : add hour to day reducer (#13166)
- **PR/Issue**：#13166

## 总体目的

本提交为 Spark 4.0 的存储分区连接（Storage Partitioned Joins, SPJ）添加了 hour 到 day 的 reducer。SPJ 是 Spark 的一项优化功能，当两个表使用兼容的分区策略时，可以避免不必要的 shuffle 操作。

在 Iceberg 中，表可以使用不同的分区变换函数，如 `hours(timestamp)` 和 `days(timestamp)`。当两个表分别使用 hour 和 day 分区进行连接时，Spark 需要知道如何将 hour 分区值"归约"为 day 分区值，以便正确识别分区兼容性并触发 SPJ 优化。此前已存在 day 到 year 的 reducer，但缺少 hour 到 day 的 reducer，导致这类连接场景无法受益于 SPJ 优化。

## 如何达成设计目的

设计遵循 Spark 的 `ReducibleFunction` 接口模式：

1. **在 `DateTimeUtil` 中添加 `hoursToDays` 工具方法**：将 hour 值（从 epoch 起的小时数）转换为 day 值（从 epoch 起的天数）。
2. **让 `DaysFunction` 实现 `ReducibleFunction` 接口**：声明 `days` 函数可以被其他分区函数归约，但其 `reducer` 方法返回 `null`（表示 days 函数本身不提供到其他函数的归约器）。
3. **让 `HoursFunction` 实现 `ReducibleFunction` 接口**：在 `reducer` 方法中检查对方函数是否为 `DaysFunction.BaseToDaysFunction`，如果是则返回 `HourToDaysReducer`。
4. **实现 `HourToDaysReducer`**：一个实现了 `Reducer<Integer, Integer>` 接口的可序列化类，调用 `DateTimeUtil.hoursToDays` 完成实际的归约转换。

## 修改详情

### `api/src/main/java/org/apache/iceberg/util/DateTimeUtil.java` (+5/-0 lines)

**修改目的**：添加 `hoursToDays` 工具方法，将 hour 值转换为 day 值。

**工作逻辑**：方法接收一个表示从 epoch 起小时数的整数，先将 epoch 时间加上对应的小时数得到 `LocalDateTime`，再提取 `LocalDate`，最后通过 `daysFromDate` 转换为从 epoch 起的天数。这种转换方式确保了时区一致性。

### `api/src/test/java/org/apache/iceberg/util/TestDateTimeUtil.java` (+8/-0 lines)

**修改目的**：为 `hoursToDays` 方法添加单元测试。

**工作逻辑**：测试使用一个具体的时间戳（2025-06-26T22:55:00.000001001），分别通过 `microsToDays` 和 `microsToHours` + `hoursToDays` 两条路径计算 day 值，验证两者结果一致，确保转换的正确性。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/functions/DaysFunction.java` (+8/-1 lines)

**修改目的**：让 `DaysFunction` 的基类实现 `ReducibleFunction` 接口。

**工作逻辑**：`BaseToDaysFunction` 从 `private` 改为 `protected`（以便 `HoursFunction` 中引用），并实现 `ReducibleFunction<Integer, Integer>` 接口。其 `reducer` 方法返回 `null`，表示 days 函数不主动提供到其他函数的归约器，但可以被其他函数（如 hours）识别为目标函数。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/functions/HoursFunction.java` (+22/-1 lines)

**修改目的**：让 `HoursFunction` 实现 `ReducibleFunction` 接口，并提供 hour 到 day 的 reducer。

**工作逻辑**：
- 新增抽象基类 `BaseToHourFunction`，实现 `ReducibleFunction<Integer, Integer>` 接口。在 `reducer` 方法中，检查对方函数是否为 `DaysFunction.BaseToDaysFunction`，如果是则返回 `HourToDaysReducer` 实例。
- `TimestampToHoursFunction` 和 `TimestampNtzToHoursFunction` 的父类从 `BaseScalarFunction` 改为 `BaseToHourFunction`。
- 新增 `HourToDaysReducer` 内部类，实现 `Reducer<Integer, Integer>` 和 `Serializable` 接口，`reduce` 方法调用 `DateTimeUtil.hoursToDays` 完成转换。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/sql/TestStoragePartitionedJoins.java` (+53/-0 lines)

**修改目的**：添加 hour 到 day 分区连接的 SPJ 优化测试。

**工作逻辑**：测试创建两个表：一个使用 `days(timestamp_col)` 分区，另一个使用 `hours(timestamp_col)` 分区。向两个表插入数据后，执行内连接查询，并验证：启用 SPJ 时 shuffle 次数为 1（优化生效），禁用 SPJ 时 shuffle 次数为 3（需要额外 shuffle）。这验证了 hour 到 day reducer 使得 SPJ 能够识别分区兼容性并优化连接。

## 总结

本提交为 Spark 4.0 的存储分区连接功能添加了 hour 到 day 的分区归约支持。这使得使用 hour 分区和 day 分区的表在进行连接时能够受益于 SPJ 优化，减少不必要的 shuffle 操作，提升查询性能。实现遵循了 Spark 的 `ReducibleFunction` 接口模式，与已有的 day 到 year reducer 保持一致的设计风格。
