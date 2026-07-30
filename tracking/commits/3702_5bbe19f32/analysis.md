# 提交 3702：Flink: Nanosecond gaps in SortKeySerializer and ColumnStatsWatermarkExtractor (#16268)

## 提交信息

- **序号**：3702 / 4088
- **哈希**：5bbe19f32d63b39a2e49d245e659cf7ea2e5fb0c
- **短哈希**：5bbe19f32
- **日期**：2026-05-13 18:59:09 +0200
- **作者**：Talat UYARER
- **提交说明**：Flink: Nanosecond gaps in SortKeySerializer and ColumnStatsWatermarkExtractor (#16268)
- **PR/Issue**：#16268

## 总体目的

这个提交修复了 Flink 2.1 模块中 `SortKeySerializer` 和 `ColumnStatsWatermarkExtractor` 对纳秒时间戳（`TIMESTAMP_NANO`）类型支持缺失的问题。Iceberg 支持 `TIMESTAMP_NANO` 类型（纳秒精度的时间戳），但这两个 Flink 组件没有正确处理该类型，导致在序列化排序键和提取 watermark 时出现功能 gap。

具体问题：
1. `SortKeySerializer` 在序列化和反序列化排序键时，没有为 `TIMESTAMP_NANO` 类型添加 case 分支，导致该类型的排序键无法正确序列化
2. `ColumnStatsWatermarkExtractor` 在从列统计信息中提取 watermark 时，没有处理 `TIMESTAMP_NANO` 类型，可能导致 watermark 提取失败或结果不正确

## 如何达成设计目的

通过以下修改实现修复：
1. 在 `SortKeySerializer` 的序列化和反序列化 switch 语句中添加 `TIMESTAMP_NANO` case，与 `TIMESTAMP` 使用相同的处理逻辑（作为 long 处理）
2. 在 `ColumnStatsWatermarkExtractor` 中添加对 `TIMESTAMP_NANO` 类型的处理

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SortKeySerializer.java` (+2 lines)

**修改目的**：支持 TIMESTAMP_NANO 类型的序列化和反序列化。

**工作逻辑**：

在序列化方法中：
```java
case LONG:
case TIME:
case TIMESTAMP:
+case TIMESTAMP_NANO:
  target.writeLong(record.get(i, Long.class));
  break;
```

在反序列化方法中：
```java
case LONG:
case TIME:
case TIMESTAMP:
+case TIMESTAMP_NANO:
  reuse.set(i, source.readLong());
  break;
```

`TIMESTAMP_NANO` 类型在内部以 long 形式存储（纳秒时间戳），因此与 `TIMESTAMP` 使用相同的序列化逻辑——直接读写 long 值。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/reader/ColumnStatsWatermarkExtractor.java` (+15/-4 lines)

**修改目的**：支持从 TIMESTAMP_NANO 类型的列统计中提取 watermark。

**工作逻辑**：添加对 `TIMESTAMP_NANO` 类型的处理，使其能够从数据文件的列统计信息（lower_bound/upper_bound）中正确提取纳秒精度的时间戳作为 watermark。

### 测试文件 (+54/-2 lines)

**修改目的**：添加测试验证 TIMESTAMP_NANO 支持。

**工作逻辑**：在 `TestRowDataPartitionKey.java`、`TestSortKeySerializerPrimitives.java`、`TestColumnStatsWatermarkExtractor.java` 中添加针对 `TIMESTAMP_NANO` 类型的测试用例。

## 总结

这是一个功能补全提交，为 Flink 2.1 模块的 `SortKeySerializer` 和 `ColumnStatsWatermarkExtractor` 添加了对 `TIMESTAMP_NANO` 类型的支持。修复方式直接简洁——在现有的 `TIMESTAMP` case 旁边添加 `TIMESTAMP_NANO` case，复用相同的处理逻辑。这对于使用纳秒时间戳的 Flink + Iceberg 集成场景至关重要。
