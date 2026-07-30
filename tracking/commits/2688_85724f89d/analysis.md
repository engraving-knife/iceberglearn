# 提交 2688：Flink: Adds uid-suffix write option to prevent operator UID hash collisions (#14063)

## 提交信息

- **序号**：2688 / 4088
- **哈希**：85724f89d77c84c8424151a4f6e11292d09deea9
- **短哈希**：85724f89d
- **日期**：2025-09-26 14:22:17 +0200
- **作者**：Rodrigo
- **提交说明**：Flink: Adds uid-suffix write option to prevent operator UID hash collisions (#14063)
- **PR/Issue**：#14063

## 总体目的

本提交解决了 Flink IcebergSink 在特定场景下的 operator UID 哈希冲突问题，并将 `uid-suffix` 从 Builder 专有配置提升为标准的写入选项（write option），使其可通过 SQL OPTIONS 提示设置。

**问题背景**：在 Flink 中，当使用 Statement Set（语句集）执行多个 INSERT 语句向同一张 Iceberg 表写入时，会创建多个 IcebergSink 实例。这些 sink 的 operator UID 默认基于表名生成，导致不同分支 DAG 中的 operator UID 相同，产生哈希冲突。这会影响 Flink 的 savepoint/checkpoint 兼容性和 operator 去重优化，可能导致作业恢复失败或行为异常。

**解决方案**：引入 `uid-suffix` 写入选项，允许用户为每个 sink 实例指定不同的 UID 后缀，使生成的 operator UID 唯一。关键改进是将 `uidSuffix` 从 Builder 的私有字段改为通过 `writeOptions` 配置流传递的标准选项，这样 SQL 用户（无法直接访问 Builder API）也能通过 `/*+ OPTIONS('uid-suffix'='xxx') */` 提示设置后缀。

## 如何达成设计目的

整体思路是"配置路径统一化"：
1. 在 `FlinkWriteOptions` 中声明 `UID_SUFFIX` 配置选项（key 为 `uid-suffix`，默认空字符串）。
2. 在 `FlinkWriteConf` 中新增 `uidSuffix()` 方法，通过标准的配置解析器从 writeOptions 读取该选项。
3. 重构 `IcebergSink.Builder`：将 `uidSuffix` 从独立字段改为写入 `writeOptions` map，`build()` 时通过 `flinkWriteConf.uidSuffix()` 读取。
4. 在 Flink 配置文档中记录该选项。

这样 `uid-suffix` 既能通过 Builder API 的 `uidSuffix()` 方法设置，也能通过 SQL 的 OPTIONS 提示设置，两条路径统一到同一个配置项。

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/FlinkWriteOptions.java` (+4/-0 lines)

**修改目的**：声明 `uid-suffix` 配置选项。

**工作逻辑**：新增 `UID_SUFFIX` 静态常量，类型为 `ConfigOption<String>`，key 为 `"uid-suffix"`，stringType，默认值为空字符串 `""`。注释说明该选项指定底层 IcebergSink 使用的 uid 后缀。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/FlinkWriteConf.java` (+8/-0 lines)

**修改目的**：提供从配置解析 uid 后缀的能力。

**工作逻辑**：新增 `uidSuffix()` 方法，通过 `confParser.stringConf()` 链式配置：`.option(FlinkWriteOptions.UID_SUFFIX.key())` 指定选项 key，`.defaultValue(FlinkWriteOptions.UID_SUFFIX.defaultValue())` 设置默认值，`.parse()` 解析返回。这与项目中其他写入配置的解析模式一致。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergSink.java` (+8/-4 lines)

**修改目的**：将 uidSuffix 从 Builder 字段改为配置选项驱动的模式。

**工作逻辑**：
- 移除 Builder 中的 `private String uidSuffix = ""` 字段。
- `Builder.uidSuffix(String newSuffix)` 方法不再赋值给字段，而是 `writeOptions.put(FlinkWriteOptions.UID_SUFFIX.key(), newSuffix)`，将后缀写入 writeOptions map。
- `build()` 方法中，原先传 `uidSuffix` 字段改为传 `flinkWriteConf.uidSuffix()`——通过配置解析获取，这样无论是 Builder API 还是 SQL OPTIONS 设置的值都能被读到。
- `append()` 方法中，原先用 `defaultSuffix(uidSuffix, table.name())` 改为 `defaultSuffix(sink.uidSuffix, table.name())`——从已构建的 sink 实例的 `uidSuffix` 字段读取（该字段在 IcebergSink 构造函数中由 build() 传入的值设置）。

### `docs/docs/flink-configuration.md` (+1/-0 lines)

**修改目的**：在 Flink 配置文档中记录 uid-suffix 选项。

**工作逻辑**：在写入选项表格中新增一行：`uid-suffix`，来源为"As per table property"（按表属性），描述为"覆盖此表底层 IcebergSink 使用的 uid 后缀"。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/TestFlinkTableSink.java` (+55/-0 lines)

**修改目的**：验证多 sink 写同一表时 uid-suffix 防冲突的能力。

**工作逻辑**：新增 `testIcebergSinkDifferentDAG()` 测试：
- 仅在 V2 sink 模式下运行（`assumeThat(useV2Sink).isTrue()`）。
- 禁用 Flink 的 sink 复用优化（`table.optimizer.reuse-sink-enabled = false`），强制创建两个独立的 IcebergSink 实例。
- 创建两个临时源表 `sourceTable` 和 `sourceTable1`，各含相同的 4 行测试数据。
- 使用 `EXECUTE STATEMENT SET` 执行两个 INSERT 语句，都写入同一张 Iceberg 表，但分别通过 `/*+ OPTIONS('uid-suffix'='source1') */` 和 `/*+ OPTIONS('uid-suffix'='source2') */` 指定不同的 uid 后缀。
- 断言表中最终有 4 条记录（注意：两个源表数据相同，但断言期望只有 4 条而非 8 条——这可能是因为测试场景设计为验证不冲突地执行，而非数据去重；实际行为取决于表的主键/分区配置）。

## 总结

本提交解决了 Flink IcebergSink 在 Statement Set 多 INSERT 写同一表场景下的 operator UID 冲突问题。核心改进是将 `uid-suffix` 从 Builder 专有字段提升为标准写入选项，使其可通过 SQL OPTIONS 提示设置，打通了 SQL 用户的配置路径。改动涉及配置声明、配置解析、Builder 重构和文档更新，并附带了端到端测试验证多 sink 场景的正确性。这对于 Flink SQL 用户在复杂 DAG 中使用 IcebergSink 具有实际价值。
