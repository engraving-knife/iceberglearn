# 提交 3641：Spark: Support TimestampNTZ in SparkZOrderUDF (#15778)

## 提交信息

- **序号**：3641 / 4088
- **哈希**：2d54125734ddc9b9fb87db147ff255918108fa2c
- **短哈希**：2d5412573
- **日期**：2026-05-04 11:08:17 -0500
- **作者**：milleniax
- **提交说明**：Spark: Support TimestampNTZ in SparkZOrderUDF (#15778)
- **PR/Issue**：#15778

## 总体目的

这个提交为 Spark 的 Z-Order 排序 UDF（`SparkZOrderUDF`）添加对 `TimestampNTZType`（不带时区的时间戳）类型的支持。

Z-Order（空间填充曲线）排序是 Iceberg 数据文件重写（`RewriteDataFiles`）动作中用于优化数据布局的一种技术，可将多列数据按 Z-Order 排列以提升查询裁剪效率。此前 `SparkZOrderUDF` 支持多种类型（Boolean、Short、Integer、Long、Float、Double、String、Timestamp、Date 等），但不支持 `TimestampNTZType`。随着 Spark 3.4+ 引入 TimestampNTZ 类型且 Iceberg 已支持该类型，用户在含 TimestampNTZ 列的表上执行 Z-Order 排序时会因类型不支持而失败。本提交补齐了这一能力。

## 如何达成设计目的

在 `SparkZOrderUDF` 中新增一个专用的 `timestampNtzToOrderedBytesUDF` 方法，将 `LocalDateTime` 类型的 TimestampNTZ 值转换为有序字节数组，并在类型分派逻辑中加入对 `TimestampNTZType` 的处理分支。同时在 4 个 Spark 版本（3.4、3.5、4.0、4.1）的测试类中各新增一个测试用例验证功能。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/SparkZOrderUDF.java` (+28 lines)

**修改目的**：新增 TimestampNTZ 类型的 Z-Order 转换支持。

**工作逻辑**：
1. 新增 `timestampNtzToOrderedBytesUDF()` 方法，定义一个 UDF，接收 `LocalDateTime` 值：
   - 若为 null，返回 `PRIMITIVE_EMPTY`。
   - 否则通过 `DateTimeUtil.microsFromTimestamp(value)` 将 `LocalDateTime` 转换为微秒值（long），再通过 `ZOrderByteUtils.longToOrderedBytes` 转换为有序字节数组。
```java
private UserDefinedFunction timestampNtzToOrderedBytesUDF() {
  int position = inputCol;
  UserDefinedFunction udf =
      functions
          .udf(
              (LocalDateTime value) -> {
                if (value == null) {
                  return PRIMITIVE_EMPTY;
                }
                long micros = DateTimeUtil.microsFromTimestamp(value);
                return ZOrderByteUtils.longToOrderedBytes(
                        micros, inputBuffer(position, ZOrderByteUtils.PRIMITIVE_BUFFER_SIZE))
                    .array();
              },
              DataTypes.BinaryType)
          .withName("TIMESTAMP_NTZ_ORDERED_BYTES");
  this.inputCol++;
  increaseOutputSize(ZOrderByteUtils.PRIMITIVE_BUFFER_SIZE);
  return udf;
}
```
2. 在类型分派方法中新增分支：
```java
} else if (type instanceof TimestampNTZType) {
  return timestampNtzToOrderedBytesUDF().apply(column);
}
```

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/SparkZOrderUDF.java` (+28 lines)

**修改目的**：同上，为 Spark 3.5 添加相同支持。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/actions/SparkZOrderUDF.java` (+28 lines)

**修改目的**：同上，为 Spark 4.0 添加相同支持。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/actions/SparkZOrderUDF.java` (+28 lines)

**修改目的**：同上，为 Spark 4.1 添加相同支持。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (+19 lines)

**修改目的**：新增 TimestampNTZ 的 Z-Order UDF 测试。

**工作逻辑**：`testZOrderUDFWithTimestampNTZType` 测试构造一个 `timestamp_ntz '2025-01-01 12:00:00'` 列，应用 `SparkZOrderUDF.sortedLexicographically` 并传入 `DataTypes.TimestampNTZType`，断言结果为非空 BinaryType 字节数组。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (+19 lines)

**修改目的**：同上，为 Spark 3.5 添加测试。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (+17 lines)

**修改目的**：同上，为 Spark 4.0 添加测试。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (+17 lines)

**修改目的**：同上，为 Spark 4.1 添加测试。

## 总结

这个提交为 Spark Z-Order 排序 UDF 补齐了对 TimestampNTZ 类型的支持，使含该类型列的表也能执行 Z-Order 数据布局优化。实现上复用了现有的 `longToOrderedBytes` 机制，通过将 `LocalDateTime` 转换为微秒值后参与 Z-Order 编码，与 Timestamp 类型的处理思路一致。改动覆盖 Spark 3.4/3.5/4.0/4.1 四个版本，每个版本均包含实现和测试。
