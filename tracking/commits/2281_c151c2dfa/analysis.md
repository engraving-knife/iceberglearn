# 提交 2281：Flink: Backport optimised RowData evolution to Flink 1.19 / 1.20 (#13401)

## 提交信息

- **序号**：2281 / 4088
- **哈希**：c151c2dfa6b7a98b0c2076e7a4d729e7373edd84
- **短哈希**：c151c2dfa
- **日期**：2025-06-27 18:04:47 +0200
- **作者**：aiborodin
- **提交说明**：Flink: Backport optimised RowData evolution to Flink 1.19 / 1.20 (#13401)
- **PR/Issue**：#13401（backports #13340，即提交 2278）

## 总体目的

本提交将提交 2278（PR #13340，"Dynamic Iceberg Sink: Optimise RowData evolution"）从 Flink 2.0 回移植到 Flink 1.19 和 1.20 模块。动态 Iceberg Sink 支持 schema 演进的多表写入，当输入 schema 与目标表 schema 不一致时需要转换 RowData。原实现在 `RowDataEvolver` 中每次转换都重新比对 schema，性能较差。

回移植的优化通过引入预编译的 `DataConverter` 和 LRU 缓存 schema 比对结果，将"每次转换都重新比对"优化为"按输入 schema 缓存预编译转换器，转换时直接应用"，显著提升动态 Sink 的写入性能。同时 schema 相同时使用 identity 转换器实现零开销。这使得 Flink 1.19/1.20 用户也能受益于这一性能优化。

## 如何达成设计目的

与提交 2278 的设计完全一致，在 `flink/v1.19` 和 `flink/v1.20` 两个模块中重复应用相同改动：

- 新增 `DataConverter` 接口：预编译的数据转换器，支持类型加宽、字段缺失补 null、字段重排，含 RowDataConverter/ArrayConverter/MapConverter 内部类。
- 新增 `LRUCache` 类：带驱逐监听的 LRU 缓存。
- 重构 `TableMetadataCache`：新增 `ResolvedSchemaInfo` 记录类（含预编译 `recordConverter`），每个表维护 `LRUCache<Schema, ResolvedSchemaInfo>` 缓存不同输入 schema 的转换器。
- 重构 `DynamicRecordProcessor` 和 `DynamicTableUpdateOperator`：使用 `ResolvedSchemaInfo` 和 `DataConverter`，统一调用 `recordConverter.convert()`。
- `DynamicIcebergSink` 新增 `inputSchemasPerTableCacheMaxSize` 配置项（默认 10）。
- 删除 `RowDataEvolver`（190 行）。

## 修改详情

### `flink/v1.19` 和 `flink/v1.20` 下的 `sink/dynamic/` 模块 (各 13 文件，合计 +1214/-678 lines)

**修改目的**：将 RowData 演进优化回移植到 Flink 1.19/1.20。

**工作逻辑**：两个模块的改动内容与提交 2278（Flink 2.0）完全相同，包括：
- `DataConverter.java`（新增, +235）：预编译转换器接口及 RowDataConverter/ArrayConverter/MapConverter 实现。
- `LRUCache.java`（新增, +60）：基于 LinkedHashMap 的 LRU 缓存。
- `TableMetadataCache.java`（重构）：`ResolvedSchemaInfo` 替代 `SchemaInfo`，按输入 schema 缓存转换器。
- `DynamicRecordProcessor.java`：使用 `recordConverter.convert()` 替代 `RowDataEvolver.convert()`。
- `DynamicTableUpdateOperator.java`：同上，使用 `ResolvedSchemaInfo` 和 `DataConverter`。
- `DynamicIcebergSink.java`：新增 `inputSchemasPerTableCacheMaxSize` builder 选项。
- `RowDataEvolver.java`（删除, -190）：被 DataConverter 替代。
- 测试文件：新增 `TestLRUCache.java`，`TestRowDataEvolver` 重命名为 `TestRowDataConverter`，适配 `TestTableMetadataCache`、`TestTableUpdater`、`TestDynamicTableUpdateOperator`。

## 总结

本提交是提交 2278 的回移植，将动态 Iceberg Sink 的 RowData 演进性能优化（预编译 DataConverter + LRU 缓存）同步到 Flink 1.19 和 1.20 模块。改动内容与 2278 完全一致，在两个旧版 Flink 模块中各重复一份（共 26 个文件、净增约 536 行）。这使得使用 Flink 1.19/1.20 的用户也能获得动态 Sink 写入的性能提升。
