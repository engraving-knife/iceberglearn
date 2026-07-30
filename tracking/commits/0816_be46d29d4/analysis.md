# 提交 0816：Core, Parquet, Orc: Don't write column sizes when metrics mode is None (#10440)

## 提交信息

- **序号**：0816 / 4088
- **哈希**：be46d29d402e44ddc365381933c8770a98ce5fec
- **短哈希**：be46d29d4
- **日期**：2024-06-05 12:14:00 -0600
- **作者**：Amogh Jahagirdar <amogh@tabular.io>
- **提交说明**：Core, Parquet, Orc: Don't write column sizes when metrics mode is None (#10440)
- **PR/Issue**：#10440

## 总体目的

本提交修复了一个 metrics 收集不一致的 bug：当用户配置 `write.metadata.metrics.default = none`（即针对某列关闭 metrics 收集）时，Iceberg 仍然会在 manifest 中记录该列的 column sizes（列大小/字节数），而其他 metrics（如 value counts、bounds 等）则被正确跳过。

这导致了一个语义上的不一致：`metrics mode = none` 本应表示"不为该列收集任何 metrics"，但 column sizes 却仍然被写入。该 PR 通过在 Parquet 和 ORC 两个文件格式模块中，将 column sizes 的写入操作移到 metrics mode 检查之后（即遇到 `None` mode 时 `continue` 跳过），确保 `None` mode 下完全不写入任何 column metrics，包括 column sizes。

这是一个跨模块（Core 测试、Parquet、ORC）的一致性修复，确保两种主流列式格式在 metrics mode 为 None 时行为统一。

## 如何达成设计目的

修复思路非常直接：在遍历列统计信息的循环中，原来 column sizes 的记录发生在 metrics mode 检查**之前**，导致无论 mode 是什么都会记录 column sizes。修复方式是将 column sizes 的记录语句移到 metrics mode 检查**之后**，这样当 mode 为 `None` 时会先执行 `continue` 跳过本次循环，从而不记录 column sizes。

具体每处修改的目的：

1. **Parquet (`ParquetUtil.java`)**：将 `increment(columnSizes, fieldId, column.getTotalSize())` 这一行从 metrics mode 检查之前移到之后，使其与 valueCounts、bounds 等其他 metrics 的处理位置一致。

2. **ORC (`OrcMetrics.java`)**：同理，将 `columnSizes.put(fieldId, colStat.getBytesOnDisk())` 从 metrics mode 检查之前移到之后。ORC 的代码结构稍有不同（使用 `put` 而非 `increment`，因为 ORC 的列统计直接提供 bytesOnDisk），但修复逻辑一致。

3. **测试 (`TestMetrics.java`)**：更新测试断言以反映新的预期行为：
   - 对于 `metrics mode = none` 的场景：原来断言 `doesNotContainValue(null)`（即允许有值但不能是 null），改为断言 `isEmpty()`（即完全不应有任何 entry），因为现在 column sizes 完全不会被写入。
   - 对于其他 metrics mode（如 `counts`、`full` 等）的场景：增加 `assertThat(metrics.columnSizes()).isNotEmpty()` 断言，确保非 None mode 下 column sizes 仍然被正常记录，防止修复过度（即防止把所有 mode 下的 column sizes 都去掉）。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetUtil.java`

**修改目的**：修复 Parquet 格式在 metrics mode 为 None 时仍然记录 column sizes 的 bug。

**工作逻辑**：
原代码顺序为：
```java
increment(columnSizes, fieldId, column.getTotalSize());  // 先记录 column size

MetricsMode metricsMode = MetricsUtil.metricsMode(fileSchema, metricsConfig, fieldId);
if (metricsMode == MetricsModes.None.get()) {
  continue;  // 然后才检查 mode，但 column size 已经记录了
}
```

修复后顺序为：
```java
MetricsMode metricsMode = MetricsUtil.metricsMode(fileSchema, metricsConfig, fieldId);
if (metricsMode == MetricsModes.None.get()) {
  continue;  // 先检查 mode，None 则跳过
}

increment(columnSizes, fieldId, column.getTotalSize());  // 再记录 column size
```

这样当 mode 为 None 时，`continue` 会跳过后面的所有 metrics 收集逻辑，包括 column sizes。

### `orc/src/main/java/org/apache/iceberg/orc/OrcMetrics.java`

**修改目的**：修复 ORC 格式在 metrics mode 为 None 时仍然记录 column sizes 的 bug，与 Parquet 修复保持一致。

**工作逻辑**：
与 Parquet 修复相同的模式，将 `columnSizes.put(fieldId, colStat.getBytesOnDisk())` 从 metrics mode 检查之前移到之后。注意 ORC 使用 `put` 而非 `increment`，因为 ORC 的 `ColumnStatistics` 直接提供 `getBytesOnDisk()`，每个 fieldId 只需设置一次。

### `core/src/test/java/org/apache/iceberg/TestMetrics.java`

**修改目的**：更新测试断言以验证修复后的正确行为，并防止回归。

**工作逻辑**：
- 第一处修改（line 564）：针对 `metrics mode = none` 场景，将 `assertThat(metrics.columnSizes()).doesNotContainValue(null)` 改为 `assertThat(metrics.columnSizes()).isEmpty()`。这是核心断言变更——原来期望 column sizes map 中可能有值但不含 null，现在期望它完全为空。
- 后续 4 处修改（line 587, 609, 647, 672）：针对 `counts`、`full` 等 non-None mode 场景，在原有 `doesNotContainValue(null)` 断言基础上，新增 `assertThat(metrics.columnSizes()).isNotEmpty()` 断言。这是保护性断言，确保修复没有意外破坏非 None mode 下的 column sizes 收集功能。

## 小结

- **成效**：修复后，当 `metrics mode = none` 时，Iceberg 不再写入 column sizes，与其他 metrics（counts、bounds）的行为保持一致，实现了 metrics mode 语义上的完全一致性。Parquet 和 ORC 两种格式行为统一。
- **影响范围**：影响所有使用 `write.metadata.metrics.default = none` 或针对特定列配置 `metrics mode = none` 的用户。修复后，这些列的 manifest 中将不再包含 column sizes 信息。对依赖 column sizes 进行下游处理（如某些查询优化）的用户，在 None mode 下将不再可用——但这符合 None mode 的语义预期。属于行为变更型 bug 修复。
- **回迁注意事项**：
  1. 这是一个低风险的 bug 修复，回迁相对简单，只需调整两行代码的位置。
  2. 需同时回迁 Parquet 和 ORC 两个模块的修改，确保一致性。
  3. 测试断言的变更需要一并回迁，特别是 `isEmpty()` 断言验证了核心修复行为。
  4. 注意该修复改变了 None mode 下的行为（从"写入 column sizes"变为"不写入"），如果有下游系统依赖旧行为（虽然不太合理），需要评估影响。
  5. 回迁时检查是否还有其他文件格式模块（如 Avro）存在类似的代码顺序问题，虽然本 PR 未涉及，但可能存在一致性问题。
