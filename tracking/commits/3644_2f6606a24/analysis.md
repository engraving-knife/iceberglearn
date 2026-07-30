# 提交 3644：Flink: Backport: Dynamic sink options to be configurable in SQL (#16209)

## 提交信息

- **序号**：3644 / 4088
- **哈希**：2f6606a247e2b16be46ca6c02fc4cfc2e17691e6
- **短哈希**：2f6606a24
- **日期**：2026-05-05 18:38:22 +0200
- **作者**：Swapna Marru
- **提交说明**：Flink: Backport: Dynamic sink options to be configurable in SQL (#16209)
- **PR/Issue**：#16209（backport #15780）

## 总体目的

这个提交是将 PR #15780（即本系列第 3620 号提交）从 Flink 2.1 backport 到 Flink 1.20 和 Flink 2.0 的版本。

该改动为 Flink 的 Dynamic Iceberg Sink（动态 Iceberg Sink）添加了通过 SQL 和 Flink 配置来设置 sink 选项的能力。此前，Dynamic Sink 的配置选项（如缓存大小、缓存刷新间隔、是否立即更新表等）只能通过 Java API（Builder 方法）设置，无法通过 SQL 语句或 Flink 配置文件配置。此次 backport 让 Flink 1.20 和 2.0 用户也能享受这一改进。

配置优先级：写入选项 > Flink 配置 > 默认值。

## 如何达成设计目的

与原 PR #15780 完全一致的方案，应用于 Flink 1.20 和 2.0 两个版本：
1. 新增 `FlinkDynamicSinkOptions` 类，定义所有配置选项为 Flink `ConfigOption`。
2. 新增 `FlinkDynamicSinkConf` 类，使用 `FlinkConfParser` 解析配置，支持多级配置优先级。
3. 重构 `DynamicIcebergSink.Builder`，将配置存储到 `writeOptions` map 中而非独立字段。
4. 新增 `DynamicRecordWithConfig` 类，将配置与记录一起传递给算子。
5. 更新相关算子类以使用新的配置类。

## 修改详情

以下文件在 `flink/v1.20/flink/src/...` 和 `flink/v2.0/flink/src/...` 两个目录下各有一份相同的改动。

### `FlinkDynamicSinkOptions.java` (+71 lines, new)

**修改目的**：定义 Dynamic Sink 的 Flink 配置选项。

**工作逻辑**：定义了 6 个 `ConfigOption`：`CACHE_MAX_SIZE`（默认 100）、`IMMEDIATE_TABLE_UPDATE`（默认 false）、`DROP_UNUSED_COLUMNS`（默认 false）、`CACHE_REFRESH_MS`（默认 1000）、`INPUT_SCHEMAS_PER_TABLE_CACHE_MAX_SIZE`（默认 10）、`CASE_SENSITIVE`（默认 true）。

### `FlinkDynamicSinkConf.java` (+102 lines, new)

**修改目的**：配置解析类，支持多级配置优先级。

**工作逻辑**：使用 `FlinkConfParser` 解析配置，优先级为：写入选项 > Flink 配置 > 默认值。为每个配置选项提供读取方法。

### `DynamicIcebergSink.java` (+35/-36 lines)

**修改目的**：重构 Builder 以使用统一的配置机制。

**工作逻辑**：移除 6 个独立配置字段，Builder 方法改为将配置写入 `writeOptions` map，在 `append()` 方法中创建 `FlinkDynamicSinkConf` 读取值。

### `DynamicRecordWithConfig.java` (+94 lines, new)

**修改目的**：将配置与记录一起传递。

**工作逻辑**：包装 `DynamicRecord` 和 sink 配置，使算子能够访问配置。

### `DynamicRecordProcessor.java` (+20/-16 lines)

**修改目的**：使用新的配置类。

### `DynamicTableUpdateOperator.java` (+12/-7 lines)

**修改目的**：使用新的配置类。

### `DynamicRecord.java` (+4 lines)

**修改目的**：新增方法以支持配置传递。

### `HashKeyGenerator.java` (+3/-2 lines)

**修改目的**：适配配置变更。

### `FlinkConfParser.java` (+1/-1 line)

**修改目的**：小幅调整。

### 测试文件

- `TestDynamicIcebergSink.java`：扩展测试覆盖 SQL/配置文件设置选项。
- `TestDynamicRecordWithConfig.java` (+120 lines, new)：新增配置传递测试。
- `TestDynamicTableUpdateOperator.java`：调整测试适配新配置类。
- `TestHashKeyGenerator.java`：扩展测试。

## 总结

这个提交是 PR #15780 的 backport，将 Flink Dynamic Iceberg Sink 的配置选项从仅限 Java API 扩展为可通过 SQL 和 Flink 配置文件设置的功能，应用到 Flink 1.20 和 2.0 两个版本。通过定义标准的 Flink `ConfigOption` 和配置解析类，用户可以更灵活地配置 Dynamic Sink 的行为，支持多级配置优先级。Java API 保持向后兼容。这使得较早版本的 Flink 用户也能使用 SQL 配置 Dynamic Sink。
