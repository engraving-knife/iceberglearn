# 提交 2807：Spark: Fix Z-order UDF to correctly handle DateType (#14108)

## 提交信息

- **序号**：2807 / 4088
- **哈希**：bd1b8900ad17a0625349aec3f73c87e79ad2af66
- **短哈希**：bd1b8900a
- **日期**：2025-10-30 14:00:44 -0700
- **作者**：Ron Kapoor
- **提交说明**：Spark: Fix Z-order UDF to correctly handle DateType (#14108)
- **PR/Issue**：#14108

## 总体目的

本提交修复了 Spark Z-order UDF 对 DateType 处理不正确的问题。

Z-order（Z 曲线排序）是 Iceberg 数据文件重写（Rewrite Data Files）中使用的一种空间填充曲线排序策略，用于将多列数据按 Z-order 排列以提高查询效率。`SparkZOrderUDF` 负责将各列值转换为有序字节数组，以便进行 Z-order 编码。

对于 DateType（日期类型），之前的代码直接将日期列 cast 为 LongType，然后使用 `longToOrderedBytesUDF` 转换为有序字节。然而，在 Spark 4.0 中，直接将 DateType cast 为 LongType 可能不会产生预期的整数值（日期自纪元以来的天数），导致 Z-order 排序结果不正确。

修复方案是使用 Spark 的 `functions.unix_date()` 函数将日期列显式转换为 Unix 日期值（自 1970-01-01 起的天数），然后再 cast 为 LongType，确保获得正确的整数值用于 Z-order 编码。

## 如何达成设计目的

将 `SparkZOrderUDF` 中 DateType 分支的 `column.cast(DataTypes.LongType)` 替换为 `functions.unix_date(column).cast(DataTypes.LongType)`，通过 `unix_date` 函数正确地将日期转换为天数整数值。

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/actions/SparkZOrderUDF.java` (+1/-1 lines)

**修改目的**：修复 DateType 的 Z-order 字节转换。

**工作逻辑**：在 `sortedLexicographically` 方法的类型判断分支中，DateType 分支原来是 `longToOrderedBytesUDF().apply(column.cast(DataTypes.LongType))`，修改为 `longToOrderedBytesUDF().apply(functions.unix_date(column).cast(DataTypes.LongType))`。`unix_date` 函数返回日期自纪元以来的天数（INT 类型），再 cast 为 LongType 以匹配 `longToOrderedBytesUDF` 的输入要求。这避免了直接 cast DateType 为 LongType 可能产生的意外行为。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (+19/-0 lines)

**修改目的**：添加 DateType Z-order 转换的测试。

**工作逻辑**：新增 `testZOrderUDFWithDateType` 测试：创建一个包含日期 `DATE '2025-01-01'` 的 DataFrame，使用 `SparkZOrderUDF.sortedLexicographically` 对日期列进行 Z-order 编码。验证结果列类型为 BinaryType，且结果字节数组非空非 null。这确保 DateType 的 Z-order 转换不会抛出异常并产生有效输出。

## 总结

本提交修复了 Spark 4.0 中 Z-order UDF 对 DateType 的不正确处理。原代码直接将 DateType cast 为 LongType，可能导致 Z-order 排序结果不正确。修复使用 `functions.unix_date()` 显式将日期转换为 Unix 天数后再 cast 为 LongType，确保 Z-order 编码的正确性。添加了对应的测试验证 DateType 的 Z-order 转换功能。
