# 提交 2619：Flink: Fix flaky tests for Iceberg sink and improve assert description (#14044)

## 提交信息

- **序号**：2619 / 4088
- **哈希**：a5f6f449b31dc5cf3da2d8e5e192250b2008f1a5
- **短哈希**：a5f6f449b
- **日期**：2025-09-11 11:23:27 +0200
- **作者**：Maximilian Michels
- **提交说明**：Flink: Fix flaky tests for Iceberg sink and improve assert description
- **PR/Issue**：#14044

## 总体目的

Iceberg 的 Flink sink 测试存在 flaky（不稳定）问题。Iceberg sink 依赖 Flink 的 checkpoint 机制来提交数据——只有在 checkpoint 成功时，写入的数据才会被提交到 Iceberg 表。测试中若数据源与 checkpoint 协调不当，可能出现数据未被提交（表无快照）的情况，导致断言失败。

本提交从两方面修复 flaky 测试：
1. **替换数据源实现**：将部分测试中使用的 `env.fromCollection(...)` 替换为 `env.addSource(createBoundedSource(...), ...)`。`fromCollection` 是 Flink 的内置有限源，但其与 checkpoint 的交互在某些场景下不够稳定；而 `createBoundedSource` 是测试中已有的自定义 Source，能更好地与 checkpoint 机制配合（该文件其他测试方法已在用）。统一使用 `createBoundedSource` 可减少因数据源导致的 checkpoint 时序问题。
2. **改进断言描述**：当表无快照（snapshot 为 null）时断言期望数据为空，原先缺少描述信息，失败时难以定位原因。改进后增加描述，提示"如果期望非空，则很可能是 Flink job 没有 checkpoint"，帮助快速诊断 flaky 失败。

## 如何达成设计目的

1. 在 `SimpleDataUtil.assertTableRecords` 等辅助方法中，为"无快照时断言期望为空"增加 `.as(...)` 描述，说明无快照意味着数据未提交，最可能是 checkpoint 未发生。
2. 在两个多源 union 测试（`TestFlinkIcebergSinkExtended` 与 `TestIcebergSink`）中，将 left/right 两个数据流从 `env.fromCollection` 改为 `env.addSource(createBoundedSource(...))`，与该文件其他测试保持一致，确保数据源与 checkpoint 正确协作。

## 修改详情

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/SimpleDataUtil.java` (+6/-1 lines)

**修改目的**：改进无快照时断言的诊断信息。

**工作逻辑**：在 `latestSnapshot(table, branch)` 返回 null 时，原先直接 `assertThat(expected).isEmpty()`；改为 `assertThat(expected).as("No snapshot for table '%s', assuming expected data is empty. If that's not the case, the Flink job most likely did not checkpoint.", table.name()).isEmpty()`。当断言失败时，该描述会显示在错误信息中，明确提示无快照通常意味着 Flink job 未完成 checkpoint，数据未提交，从而帮助定位 flaky 失败的根因。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkExtended.java` (+2/-2 lines)

**修改目的**：用自定义 bounded source 替换 fromCollection，修复 flaky。

**工作逻辑**：在多源 union 测试中，left 与 right 两个 `DataStream<Row>` 的创建从 `env.fromCollection(leftRows, ROW_TYPE_INFO)` 改为 `env.addSource(createBoundedSource(leftRows), ROW_TYPE_INFO)`（right 同理）。`createBoundedSource` 是该测试类已有的自定义 Source 实现，与 Flink checkpoint 机制配合更稳定，文件中其他测试方法（行 207、231）已采用此方式，此处统一以消除 fromCollection 带来的不稳定性。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergSink.java` (+2/-2 lines)

**修改目的**：同上，用 createBoundedSource 替换 fromCollection。

**工作逻辑**：与 `TestFlinkIcebergSinkExtended` 完全相同的改动——left/right 数据流从 `env.fromCollection` 改为 `env.addSource(createBoundedSource(...))`。

## 总结

本提交修复了 Flink Iceberg sink 测试的 flaky 问题：将不稳定的 `fromCollection` 数据源替换为与 checkpoint 配合更好的自定义 `createBoundedSource`，并为无快照断言增加诊断描述。改动聚焦于测试稳定性与可诊断性，不影响生产代码。后续提交 2619 会将此修复 backport 到其他 Flink 版本。
