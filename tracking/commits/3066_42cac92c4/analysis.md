# 提交 3066：Flink: Fix equalityFieldColumns always null in IcebergSink (#14952)

## 提交信息

- **序号**：3066 / 4088
- **哈希**：42cac92c4847407b8b56f976b83a0602d80dce0b
- **短哈希**：42cac92c4
- **日期**：2026-01-06
- **作者**：GuoYu
- **提交说明**：Flink: Fix equalityFieldColumns always null in IcebergSink (#14952)
- **PR/Issue**：#14952

## 总体目的

本提交修复 Flink IcebergSink 中 `equalityFieldColumns` 字段始终为 `null` 的缺陷。问题根源在于 `IcebergSink` 类中将该字段声明为 `private final Set<String> equalityFieldColumns = null;`，直接以 `= null` 初始化，且构造函数没有接收该参数的入口。因此，即使用户通过 Builder 的 `equalityFieldColumns(List<String> columns)` 方法设置了等值字段列，这些列名也从未被传递到最终构造的 `IcebergSink` 实例中。

该字段在实际逻辑中并不参与等值删除的核心判断——核心逻辑使用的是 `equalityFieldIds`（由 `SinkUtil.checkAndGetEqualityFieldIds` 从列名解析得到的字段 ID 集合）。`equalityFieldColumns` 的用途仅限于日志和错误消息。具体来说，在 Builder 的 `append()` 方法中有一段校验逻辑：当分布模式为 `HASH` 且设置了等值字段时，要求每个分区字段对应的源列必须包含在等值字段集合中，否则抛出 `IllegalStateException`，其错误消息格式为 `"In 'hash' distribution mode with equality fields set, source column '%s' of partition field '%s' should be included in equality fields: '%s'"`，其中第三个 `%s` 引用的就是 `equalityFieldColumns`。由于该字段始终为 `null`，错误消息会显示 `'null'` 而非实际设置的列名（如 `'[id]'`），严重影响排障体验。

修复后，构造函数新增 `equalityFieldColumns` 参数，Builder 在构建实例时将列名列表转为 `Set<String>` 传入，字段注释也明确标注其仅用于日志/错误消息，实际逻辑应始终使用 `equalityFieldIds`。

## 如何达成设计目的

改动集中在 `flink/v2.1` 模块的 `IcebergSink.java`（主代码）和 `TestFlinkIcebergSinkV2DistributionMode.java`（测试）。核心思路是将 Builder 中已有的 `equalityFieldColumns` 列表通过构造函数传递到 `IcebergSink` 实例字段，并新增测试验证错误消息正确包含等值字段列名。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergSink.java` (+12/-3 lines)

**修改目的**：修复 `equalityFieldColumns` 字段始终为 null 的问题，使其在错误消息中正确显示用户设置的等值字段列名。

**工作逻辑**：
- 新增 import `org.apache.iceberg.relocated.com.google.common.collect.Sets`，用于将 List 转为 Set。
- 字段声明由 `private final Set<String> equalityFieldColumns = null;` 改为 `private final Set<String> equalityFieldColumns;`，并增加注释说明该字段仅用于日志/错误消息，实际逻辑应使用 `equalityFieldIds`。
- 构造函数参数列表末尾新增 `Set<String> equalityFieldColumns`，并在构造体中赋值 `this.equalityFieldColumns = equalityFieldColumns;`。
- Builder 的 `append()` 方法中，在构造 `IcebergSink` 之前新增 `Set<String> equalityFieldColumnsSet = equalityFieldColumns != null ? Sets.newHashSet(equalityFieldColumns) : null;`，将该 Set 作为最后一个参数传入构造函数。这里做 null 判断是为了在用户未设置等值字段时保持 null 语义。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2DistributionMode.java` (+33/-0 lines)

**修改目的**：新增测试验证修复后错误消息正确包含等值字段列名。

**工作逻辑**：
新增测试方法 `testHashDistributionWithPartitionNotInEqualityFields`，在分区表上设置 `DistributionMode.HASH`、`upsert(false)`、`equalityFieldColumns(ImmutableList.of("id"))`。由于分区字段 `data` 的源列不在等值字段 `[id]` 中，执行 `env::execute` 时应抛出 `IllegalStateException`，且消息包含 `"should be included in equality fields: '[id]'"`。修复前该消息会显示 `'null'`，测试无法通过。该测试同时覆盖 `isTableSchema` 为 true 和 false 两种路径。

## 总结

本提交修复了一个影响排障体验的缺陷：IcebergSink 中 `equalityFieldColumns` 字段因硬编码 `= null` 初始化且构造函数未传参，导致 hash 分布模式下的校验错误消息始终显示 `'null'` 而非实际等值字段列名。修复方式简单直接——打通 Builder 到构造函数的传参链路，并新增回归测试确保错误消息正确性。
