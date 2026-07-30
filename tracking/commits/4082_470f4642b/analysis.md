# 提交 4082：Parquet: Fix variant BINARY upper bound to truncate up

## 提交信息

- **序号**：4082 / 4088
- **哈希**：470f4642bdda336bac5372c79c762a2654dfe3f3
- **短哈希**：470f4642b
- **日期**：2026-07-22 10:42:57 -0700
- **作者**：vishnu prakash
- **提交说明**：Parquet: Fix variant BINARY upper bound to truncate up (#16880)
- **PR/Issue**：#16880

## 总体目的

Iceberg 在存储 variant 类型的列统计（lower/upper bounds）时，为了控制统计值大小，会对 BINARY 和 STRING 类型的值做截断（默认截断到 16 字节）。截断需要区分下界和上界两种语义：

- **下界截断（truncateMin）**：直接取前 N 字节即可。因为前缀≤完整值（按字典序），所以截断后的值仍≤原值，作为下界是合法的；
- **上界截断（truncateMax）**：取前 N 字节后，还需要在末尾尝试 +1 进位，得到一个比原值大的最小前缀。例如 `0x0102` 截断到 2 字节上界是 `0x0103`。但如果截断部分全是 `0xFF`（无法再进位），则该值无法用有限前缀表示上界，返回 null（表示放弃上界）。

`ParquetVariantUtil` 在为 variant BINARY 类型计算上界时，错误地调用了 `BinaryUtil.truncateBinaryMin`（用于下界的截断），而不是 `truncateBinaryMax`。这导致 variant BINARY 列的 upper bound 实际上是原值的前 16 字节，**比真实最大值小**，从而破坏了上界的语义不变量（upper bound 必须 ≥ 实际最大值）。

这会引发严重的正确性问题：当查询条件如 `variant_col > X`（X 大于截断前缀但小于真实最大值）下推到 row group 级别过滤时，由于上界偏小，row group 可能被错误剪掉，导致应返回的行丢失。

本提交将这一行调用从 `truncateBinaryMin` 改为 `truncateBinaryMax`，使 variant BINARY 的上界截断与下界截断使用正确的方向。

## 如何达成设计目的

修改极小：把 `ParquetVariantUtil` 中 case BINARY 分支里调用的 `truncateBinaryMin` 替换为 `truncateBinaryMax`，与 `ParquetUtil` 中普通 BINARY 列上界的处理方式（`ParquetUtil.java` 第 385 行）保持一致。其余逻辑（返回 null 表示无有效上界）保持不变，因为 `truncateBinaryMax` 在全 0xFF 时也会返回 null。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetVariantUtil.java` (+1/-1 lines)

**修改目的**：修正 variant BINARY 上界截断的方向。

**工作逻辑**：

修改前：
```java
case BINARY:
  ByteBuffer truncatedBuffer =
      BinaryUtil.truncateBinaryMin((ByteBuffer) value.asPrimitive().get(), 16);
  return truncatedBuffer != null ? Variants.of(PhysicalType.BINARY, truncatedBuffer) : null;
```

修改后：
```java
case BINARY:
  ByteBuffer truncatedBuffer =
      BinaryUtil.truncateBinaryMax((ByteBuffer) value.asPrimitive().get(), 16);
  return truncatedBuffer != null ? Variants.of(PhysicalType.BINARY, truncatedBuffer) : null;
```

注意此处 `truncateBinaryMin/Max` 接收的是 `ByteBuffer`（不是 `Literal<ByteBuffer>`），是 `ParquetVariantUtil` 中针对裸 `ByteBuffer` 的重载入口。`truncateBinaryMax` 会在截断前缀末尾逐字节尝试 +1 进位以得到一个严格大于原值的前缀；若所有字节都是 `0xFF` 无法进位则返回 null，表示该值没有有限上界（此时调用方返回 null 让 metrics 不记录上界，符合 Iceberg 上界缺失语义）。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestVariantMetrics.java` (+50/-0 lines)

**修改目的**：覆盖修复后的截断行为，包括正常截断和全 0xFF 溢出两种场景。

**工作逻辑**：

新增两个测试：

1. **`testShreddedBinaryBoundsTruncation`**：
   - 构造 20 字节的 binary（`0x01, 0x02, ..., 0x14`），长度超过 16 字节截断阈值；
   - 写入 Parquet 并取 metrics；
   - 断言 lower bound 等于 `truncateBinaryMin(bytes, 16)`（前 16 字节）；
   - 断言 upper bound 等于 `truncateBinaryMax(bytes, 16)`（前 16 字节末尾 +1 进位后的值），验证上下界使用了不同方向的截断。

2. **`testShreddedBinaryUpperBoundOverflow`**：
   - 构造 20 字节全 `0xFF` 的 binary；
   - `truncateBinaryMax` 无法进位，应返回 null；
   - 断言 lower bound 仍等于 `truncateBinaryMin(bytes, 16)`（全 0xFF 前 16 字节）；
   - 断言 upper bound 为 null，验证溢出场景正确放弃上界而不是写入错误值。

两个测试通过 `writeParquet(...)` helper 写入 shredded variant，然后从 metrics 的 lower/upper bounds 中取出字段 ID 2 的值，反序列化为 Variant 后与预期 `VariantValue` 比较。

## 总结

一个虽小但关键的正确性修复：variant BINARY 列的 upper bound 此前误用 `truncateBinaryMin` 导致上界偏小，破坏了"上界 ≥ 实际最大值"的不变量，进而可能导致基于上界的行组过滤错误剪掉应返回的数据。修复改为 `truncateBinaryMax`，与普通 BINARY 列的处理对齐。配套测试覆盖了正常截断（验证上下界方向不同）和全 0xFF 溢出（验证上界正确返回 null）两个关键场景。
