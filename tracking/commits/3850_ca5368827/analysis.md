# 提交 3850：Parquet: Avoid intermediate BigInteger in int and long decimal readers (#16722)

## 提交信息

- **序号**：3850 / 4088
- **哈希**：ca53688270fa4f23bf23ca6676b4f9cb13d025b3
- **短哈希**：ca5368827
- **日期**：2026-06-09 18:29:30 +0200
- **作者**：Vova Kolmakov
- **提交说明**：Parquet: Avoid intermediate BigInteger in int and long decimal readers (#16722)
- **PR/Issue**：#16722

## 总体目的

本提交是 Parquet 读取路径的一个微优化，避免了在读取 int 和 long 类型的 Decimal 值时创建中间 `BigInteger` 对象。

Iceberg 的 Parquet 读取器中有两个 Decimal 读取器：`IntegerDecimalReader`（用于精度适合 int 的 decimal）和 `LongDecimalReader`（用于精度适合 long 的 decimal）。它们分别从 Parquet 列读取 `int` 和 `long` 值，然后转换为 `BigDecimal`。

在优化前，转换路径是：`int/long` → `BigInteger.valueOf()` → `new BigDecimal(BigInteger, scale)`，即先创建一个中间 `BigInteger` 对象，再用它创建 `BigDecimal`。这会产生一个不必要的临时对象，增加 GC 压力。

`BigDecimal` 提供了直接的 `BigDecimal.valueOf(long, int)` 静态工厂方法，可以一步完成转换，避免中间 `BigInteger` 对象的创建。

## 如何达成设计目的

将 `new BigDecimal(BigInteger.valueOf(column.nextInteger()), scale)` 替换为 `BigDecimal.valueOf(column.nextInteger(), scale)`，利用 `BigDecimal.valueOf(long, int)` 直接从 long 值和 scale 创建 BigDecimal，跳过中间的 BigInteger 分配。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetValueReaders.java` (+2/-2 lines)

**修改目的**：避免中间 BigInteger 对象。

**工作逻辑**：

`IntegerDecimalReader.read()`:
```java
// 修改前：
return new BigDecimal(BigInteger.valueOf(column.nextInteger()), scale);
// 修改后：
return BigDecimal.valueOf(column.nextInteger(), scale);
```

`LongDecimalReader.read()`:
```java
// 修改前：
return new BigDecimal(BigInteger.valueOf(column.nextLong()), scale);
// 修改后：
return BigDecimal.valueOf(column.nextLong(), scale);
```

## 总结

这是一个极简但有效的微优化，将 Decimal 读取路径从两步转换（`int/long` → `BigInteger` → `BigDecimal`）简化为一步（`int/long` → `BigDecimal`），消除了中间 `BigInteger` 对象的分配。在大规模读取 Decimal 列的场景下，这能减少 GC 压力并略微提升读取吞吐量。修改风险极低，因为 `BigDecimal.valueOf(long, int)` 与原逻辑在数学上等价。
