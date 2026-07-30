# 提交 3620：Flink: SQL: Make Dynamic sink options to be configurable in SQL (#15780)

## 提交信息

- **序号**：3620 / 4088
- **哈希**：1bdbed7a5ddd3981dd958b8a6e8124ca56c5a0e4
- **短哈希**：1bdbed7a5
- **日期**：2026-04-30 14:03:59 +0200
- **作者**：Swapna Marru
- **提交说明**：Flink: SQL: Make Dynamic sink options to be configurable in SQL (#15780)
- **PR/Issue**：#15780

## 总体目的

这个提交为 Flink 的 Dynamic Iceberg Sink（动态 Iceberg Sink）添加了通过 SQL 和 Flink 配置来设置 sink 选项的能力。

Dynamic Iceberg Sink 是 Iceberg Flink 集成中的一个特性，允许在运行时动态地写入多个 Iceberg 表。之前，这些 sink 的配置选项（如缓存大小、缓存刷新间隔、是否立即更新表等）只能通过 Java API（Builder 方法）设置，无法通过 SQL 语句或 Flink 配置文件来配置。

这个提交将所有 Dynamic Sink 的配置选项提取为标准的 Flink `ConfigOption`，使其可以通过以下方式配置：
1. SQL 的 `OPTIONS` 子句（写入选项）
2. Flink 配置文件（`flink-conf.yaml`）
3. Java API（Builder 方法，向后兼容）

配置优先级：写入选项 > Flink 配置 > 默认值。

## 如何达成设计目的

1. 新增 `FlinkDynamicSinkOptions` 类，定义所有配置选项为 Flink `ConfigOption`。
2. 新增 `FlinkDynamicSinkConf` 类，使用 `FlinkConfParser` 解析配置，支持多级配置优先级。
3. 重构 `DynamicIcebergSink.Builder`，将配置存储到 `writeOptions` map 中而非独立字段。
4. 新增 `DynamicRecordWithConfig` 类，将配置与记录一起传递给算子。
5. 更新相关算子类以使用新的配置类。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/FlinkDynamicSinkOptions.java` (+71 lines, new)

**修改目的**：定义 Dynamic Sink 的 Flink 配置选项。

**工作逻辑**：
定义了 6 个 `ConfigOption`：
- `CACHE_MAX_SIZE`（默认 100）：缓存最大大小。
- `IMMEDIATE_TABLE_UPDATE`（默认 false）：是否立即应用表更新。
- `DROP_UNUSED_COLUMNS`（默认 false）：是否丢弃未使用的列。
- `CACHE_REFRESH_MS`（默认 1000）：缓存刷新间隔（毫秒）。
- `INPUT_SCHEMAS_PER_TABLE_CACHE_MAX_SIZE`（默认 10）：每表输入 schema 缓存最大大小。
- `CASE_SENSITIVE`（默认 true）：是否大小写敏感。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/FlinkDynamicSinkConf.java` (+102 lines, new)

**修改目的**：配置解析类，支持多级配置优先级。

**工作逻辑**：
使用 `FlinkConfParser` 解析配置，优先级为：写入选项 > Flink 配置 > 默认值。为每个配置选项提供读取方法，如 `cacheMaxSize()`、`immediateTableUpdate()` 等。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicIcebergSink.java` (+35/-36 lines)

**修改目的**：重构 Builder 以使用统一的配置机制。

**工作逻辑**：
1. 移除了 6 个独立的配置字段（`immediateUpdate`、`dropUnusedColumns` 等）。
2. Builder 方法现在将配置写入 `writeOptions` map 而非独立字段：
```java
public Builder<T> immediateTableUpdate(boolean newImmediateUpdate) {
  writeOptions.put(
      FlinkDynamicSinkOptions.IMMEDIATE_TABLE_UPDATE.key(),
      Boolean.toString(newImmediateUpdate));
  return this;
}
```
3. 在 `append()` 方法中创建 `FlinkDynamicSinkConf` 从统一配置中读取值。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecordWithConfig.java` (+94 lines, new)

**修改目的**：将配置与记录一起传递。

**工作逻辑**：
包装 `DynamicRecord` 和 sink 配置，使算子能够访问配置。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecordProcessor.java` (+20/-16 lines)

**修改目的**：使用新的配置类。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicTableUpdateOperator.java` (+12/-7 lines)

**修改目的**：使用新的配置类。

### 其他文件

- `FlinkConfParser.java` (+1/-1)：小幅调整。
- `DynamicRecord.java` (+4/-0)：新增方法。
- `HashKeyGenerator.java` (+3/-2)：适配变更。
- 测试文件：新增和更新测试。

## 总结

这个提交将 Flink Dynamic Iceberg Sink 的配置选项从仅限 Java API 扩展为可通过 SQL 和 Flink 配置文件设置。通过定义标准的 Flink `ConfigOption` 和配置解析类，用户可以更灵活地配置 Dynamic Sink 的行为，支持多级配置优先级。Java API 保持向后兼容，配置值现在统一通过 writeOptions 传递。
