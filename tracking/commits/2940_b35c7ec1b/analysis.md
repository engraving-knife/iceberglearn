# 提交 2940：Core: Fix NAN_VALUE_COUNTS serialization for ContentFile (#14721)

## 提交信息

- **序号**：2940 / 4088
- **哈希**：b35c7ec1b03e3897da68960cd556d635b2f5ae54
- **短哈希**：b35c7ec1b
- **日期**：2025-12-01
- **作者**：Huaxin Gao
- **提交说明**：Core: Fix NAN_VALUE_COUNTS serialization for ContentFile
- **PR/Issue**：#14721

## 总体目的

这是一个 bug 修复提交。`ContentFileParser` 在把 `ContentFile` 序列化为 JSON 时，对 `NAN_VALUE_COUNTS`（`nan-value-counts`）字段的写入判断条件存在复制粘贴错误：判空用的是 `contentFile.nullValueCounts()` 而非 `contentFile.nanValueCounts()`，但真正写入 JSON 的数据却来自 `contentFile.nanValueCounts()`。两者本应是同一个字段的判空和取值，结果错配了。

`metricsToJson` 方法（`core/src/main/java/org/apache/iceberg/ContentFileParser.java` 第 226 行起）按 `record-count`、`column-sizes`、`value-counts`、`null-value-counts`、`nan-value-counts`、`lower-bounds`、`upper-bounds` 的顺序写每个 metrics 字段。对每个可选字段，先判空再写。原代码的写法如下：

```java
if (contentFile.nullValueCounts() != null) {
  generator.writeFieldName(NAN_VALUE_COUNTS);
  SingleValueParser.toJson(
      DataFile.NAN_VALUE_COUNTS.type(), contentFile.nanValueCounts(), generator);
}
```

这段错误条件会导致两类数据正确性问题：

1. **NaN 统计丢失**：当某个 `ContentFile` 有 `nanValueCounts` 但没有 `nullValueCounts`（即 `nullValueCounts() == null` 而 `nanValueCounts() != null`）时，整个 `NAN_VALUE_COUNTS` 字段会被跳过，导致 NaN 值计数在序列化中丢失，反序列化后无法恢复，影响统计信息的完整性。
2. **潜在 NPE 或错误输出**：当 `nullValueCounts() != null` 但 `nanValueCounts() == null` 时，会进入分支调用 `SingleValueParser.toJson(..., contentFile.nanValueCounts(), generator)`，向解析器传入 null map，可能在序列化阶段触发空指针异常或写出错误的 JSON 结构。

NAN 值统计主要用于浮点列上 NaN 出现次数的统计，对查询优化（如某些表达式下跳过含 NaN 的文件）有实际意义，因此该 bug 不只是输出格式问题，而是会真实影响 metrics 的正确性与下游读取决策。

## 如何达成设计目的

修复方式非常直接：把第 246 行的判断条件从 `contentFile.nullValueCounts() != null` 改成 `contentFile.nanValueCounts() != null`，使判空与取值指向同一个字段。同时在 `TestContentFileParser` 中新增一个针对"仅有 nanValueCounts 而无 nullValueCounts"的回归用例，验证修复后 nan 字段被正确写出、null 字段不被误写、并保证反序列化 round-trip 一致。

## 修改详情

### `core/src/main/java/org/apache/iceberg/ContentFileParser.java` (+1/-1 lines)

**修改目的**：将 `NAN_VALUE_COUNTS` 字段的写入判空条件从错误的 `nullValueCounts` 修正为正确的 `nanValueCounts`。

**工作逻辑**：
在 `metricsToJson` 方法中（第 246 行附近），按 metrics 字段顺序逐个判空写入。修改后的关键片段为：

```java
if (contentFile.nanValueCounts() != null) {
  generator.writeFieldName(NAN_VALUE_COUNTS);
  SingleValueParser.toJson(
      DataFile.NAN_VALUE_COUNTS.type(), contentFile.nanValueCounts(), generator);
}
```

这与上方 `NULL_VALUE_COUNTS` 块（判 `nullValueCounts()`、取 `nullValueCounts()`）以及 `VALUE_COUNTS`、`COLUMN_SIZES` 等块的写法保持一致：每个可选 metrics 字段都"判谁写谁"。修复后，反序列化侧 `fromJson` 中 `if (jsonNode.has(NAN_VALUE_COUNTS))` 的逻辑才能与序列化侧对齐——只有当原始 `nanValueCounts` 非空时才写出该字段、读回时再恢复，确保 round-trip 正确。

### `core/src/test/java/org/apache/iceberg/TestContentFileParser.java` (+39/-0 lines)

**修改目的**：为该 bug 新增回归测试，确保仅含 NaN 统计的 `DataFile` 序列化/反序列化行为正确。

**工作逻辑**：
新增两个方法：

1. 测试方法 `testNanCountsOnlyWritesNanValueCounts()`：构造一个只有 `nanValueCounts`、`nullValueCounts` 故意置 null 的 `DataFile`，调用 `ContentFileParser.toJson(...)` 序列化为字符串，断言：
   - JSON 中包含 `"nan-value-counts"` 字段（验证修复后该字段能被写出，而不是因 `nullValueCounts==null` 被错误跳过）；
   - JSON 中**不**包含 `"null-value-counts"` 字段（验证不会因 `nanValueCounts!=null` 误触发 null 字段写出）；
   - 再用 `ContentFileParser.fromJson(...)` 反序列化回来，断言结果是 `DataFile` 实例，并通过 `assertContentFileEquals` 验证字段一一对应，保证 round-trip 一致。

2. 辅助工厂方法 `dataFileWithOnlyNanCounts(PartitionSpec spec)`：构造一个 `Metrics(recordCount=1, ..., nullValueCounts=null, nanValueCounts=ImmutableMap.of(3, 0L), ...)` 的数据文件，路径 `/path/to/data-nan-only.parquet`，并在分区表中追加分区路径。这个构造精确覆盖了原 bug 的触发条件——`nullValueCounts == null && nanValueCounts != null`，正是修复前会丢失 NaN 字段的场景。

测试还复用了已有的 `assertContentFileEquals` 比对工具方法，确保 `recordCount`、`columnSizes`、`valueCounts`、`nullValueCounts`、`nanValueCounts`、`lowerBounds`、`upperBounds` 全部对齐，是一个比较完整的字段级回归覆盖。

## 总结

本次提交修复了 `ContentFileParser` 中 `NAN_VALUE_COUNTS` 字段序列化时因复制粘贴导致的判空条件错配 bug——把 `nullValueCounts != null` 修正为 `nanValueCounts != null`。该 bug 在"有 NaN 统计但无 null 统计"的文件上会丢失 NaN 字段、在反向场景下可能触发空指针。修复同步补充了针对性的 round-trip 回归测试，巩固了 metrics 序列化的正确性与完整性，对依赖 NaN 值统计做查询优化的下游有实际价值。
