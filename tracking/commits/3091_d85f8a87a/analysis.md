# 提交 3091：Kafka Connect: validate table uuid on commit (#14979)

## 提交信息

- **序号**：3091 / 4088
- **哈希**：d85f8a87a8b9c497c9235199cc3787b8814cc270
- **短哈希**：d85f8a87a
- **日期**：2026-01-09
- **作者**：Daniel Weeks
- **提交说明**：Kafka Connect: validate table uuid on commit (#14979)
- **PR/Issue**：#14979

## 总体目的

该提交为 Iceberg Kafka Connect 连接器添加了表 UUID 校验机制，用于在提交（commit）时检测目标表是否在写入过程中被替换（table replace）。Kafka Connect 的 Iceberg Sink 采用 Coordinator-Worker 架构：Worker 负责将 Kafka 记录写入 Iceberg 数据文件并生成 `DataWritten` 事件（携带 `TableReference`），Coordinator 负责收集这些事件并执行实际的 Iceberg 表提交。

在分布式 Kafka Connect 场景中，Worker 在写入数据文件时获取表的 UUID，然后通过事件将 `TableReference`（含表标识和 UUID）发送给 Coordinator。如果在 Worker 写入和 Coordinator 提交之间，目标表被替换（例如通过 `ALTER TABLE REPLACE` 或重建表操作），表的 UUID 会发生变化。如果 Coordinator 不校验 UUID，可能会将旧表的数据文件提交到新表中，导致数据不一致或元数据损坏。

此前 `TableReference` 只包含 `catalog`、`namespace`、`name` 三个字段，不携带 UUID 信息，Coordinator 无法检测表是否被替换。本提交在 `TableReference` 中新增 `uuid` 字段，在 Worker 端创建写入器时从表中读取 UUID 并存入 `TableReference`，在 Coordinator 端提交前比较 `table.uuid()` 与 `tableReference.uuid()`，若不匹配则跳过该表的提交并记录警告日志。

该提交还重构了 `IcebergWriterResult` 和 `IcebergWriter` 的相关接口，使 `TableReference`（含 UUID）在整个写入-提交链路中传递，而非仅传递表名字符串。

## 如何达成设计目的

整体改动涉及 events 模块和 connect 主模块两部分。在 events 模块中扩展 `TableReference` Avro schema 增加 `table_uuid` 字段，提供新的带 UUID 的工厂方法，保留旧方法但标记 `@Deprecated`。在 connect 模块中，`IcebergWriterFactory` 在创建 writer 时读取 `table.uuid()` 并构造 `TableReference`，通过 `IcebergWriter` → `IcebergWriterResult` → `Worker` → `Coordinator` 链路传递。Coordinator 在 `commitToTable` 方法中增加 UUID 比对逻辑。

## 修改详情

### `kafka-connect/kafka-connect-events/src/main/java/org/apache/iceberg/connect/events/TableReference.java` (+48/-4 lines)

**修改目的**：在 TableReference 中新增 UUID 字段及 Avro schema 支持。

**工作逻辑**：
新增 `private UUID uuid` 字段和 `TABLE_UUID = 10_604` 字段序号常量。在 `ICEBERG_SCHEMA`（StructType）中新增 `NestedField.optional(TABLE_UUID, "table_uuid", UUIDType.get())` 字段，使 Avro schema 包含 UUID 字段（optional，保证向后兼容）。新增 `of(String catalog, TableIdentifier tableIdentifier, UUID tableUuid)` 工厂方法，旧的无 UUID 工厂方法 `of(String catalog, TableIdentifier)` 标记 `@Deprecated`（since 1.11.0，将在 1.12.0 移除），内部调用新构造函数传入 null。旧的公开构造函数 `TableReference(String catalog, List<String> namespace, String name)` 也标记 `@Deprecated`，新增私有四参数构造函数。新增 `uuid()` 访问方法。在 `put` 和 `get`（IndexedRecord 接口实现）中增加 `TABLE_UUID` case 分支，支持 Avro 序列化/反序列化时读写 UUID 字段。`equals` 和 `hashCode` 也纳入 uuid 字段。

### `kafka-connect/kafka-connect-events/src/test/java/org/apache/iceberg/connect/events/TestEventSerialization.java` (+4/-2 lines)

**修改目的**：适配测试中使用新的 TableReference 工厂方法。

**工作逻辑**：
两处 `new TableReference("catalog", Collections.singletonList("db"), "tbl")` 替换为 `TableReference.of("catalog", TableIdentifier.of("db", "tbl"), UUID.randomUUID())`，使用新的带 UUID 工厂方法。移除 `Collections` import，新增 `TableIdentifier` import。测试现在验证包含 UUID 的 TableReference 的序列化往返。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/Coordinator.java` (+9/-0 lines)

**修改目的**：在提交前校验表 UUID。

**工作逻辑**：
在 `commitToTable` 方法中，获取到 `table` 对象后（通过 `loadTable`），新增 UUID 比对逻辑：`if (!Objects.equals(table.uuid(), tableReference.uuid()))`，若不匹配则记录 WARN 日志 "Skipping commits to table {} due to target table mismatch. Expected: {} Received: {}"。注意此处仅记录日志，未添加 `return` 语句（该遗漏在后续提交 #15011 中修复）。方法上添加 `@SuppressWarnings("checkstyle:CyclomaticComplexity")` 注解以抑制圈复杂度告警。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/Worker.java` (+2/-3 lines)

**修改目的**：使用 IcebergWriterResult 中的 TableReference 替代重新构造。

**工作逻辑**：
移除 `TableReference` import。在构造 `DataWritten` 事件时，从 `writeResult.tableReference()` 获取 TableReference（含 UUID），替代原来通过 `TableReference.of(config.catalogName(), writeResult.tableIdentifier())` 重新构造。这样 Worker 发出的事件携带了 Writer 端记录的表 UUID。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/IcebergWriter.java` (+6/-6 lines)

**修改目的**：将 tableName 替换为 TableReference，传递 UUID 信息。

**工作逻辑**：
字段 `private final String tableName` 改为 `private final TableReference tableReference`。构造函数参数从 `String tableName` 改为 `TableReference tableReference`。`initNewWriter` 中 `RecordUtils.createTableWriter(table, tableName, config)` 改为 `RecordUtils.createTableWriter(table, tableReference, config)`。在 `complete()` 方法中构造 `IcebergWriterResult` 时，从 `TableIdentifier.parse(tableName)` 改为直接传入 `tableReference`。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/IcebergWriterFactory.java` (+11/-1 lines)

**修改目的**：在创建 Writer 时获取表 UUID 并构造 TableReference。

**工作逻辑**：
在 `createWriter` 方法中，获取 `table` 后读取 `UUID tableUuid = table.uuid()`。若 UUID 为 null，记录 WARN 日志警告可能影响提交协调。然后使用 `TableReference.of(catalog.name(), identifier, tableUuid)` 构造含 UUID 的 TableReference，传给 `new IcebergWriter(table, tableReference, config)`。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/IcebergWriterResult.java` (+30/-2 lines)

**修改目的**：用 TableReference 替代 TableIdentifier，保留旧接口兼容。

**工作逻辑**：
字段 `private final TableIdentifier tableIdentifier` 改为 `private final TableReference tableReference`。新增以 `TableReference` 为参数的公开构造函数。旧的以 `TableIdentifier` 为参数的构造函数标记 `@Deprecated`（since 1.11.0），内部通过 `TableReference.of("unknown", tableIdentifier)` 转换（catalog 设为 "unknown" 因为旧接口不携带 catalog 信息）。新增 `tableReference()` 访问方法。旧的 `tableIdentifier()` 方法标记 `@Deprecated`，内部委托 `tableReference.identifier()` 返回。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/RecordUtils.java` (+4/-1 lines)

**修改目的**：适配 createTableWriter 方法签名变更。

**工作逻辑**：
`createTableWriter` 方法参数从 `String tableName` 改为 `TableReference tableReference`。方法内部获取 ID 列配置时，从 `config.tableConfig(tableName).idColumns()` 改为 `config.tableConfig(tableReference.identifier().name()).idColumns()`，通过 TableReference 获取表名。

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/channel/TestCoordinator.java` (+3/-1 lines)

**修改目的**：适配测试中使用新的 TableReference 工厂方法。

**工作逻辑**：
`new TableReference("catalog", ImmutableList.of("db"), "tbl")` 替换为 `TableReference.of("catalog", TableIdentifier.of("db", "tbl"), UUID.randomUUID())`，新增 `TableIdentifier` import。

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/data/TestSinkWriter.java` (+3/-3 lines)

**修改目的**：适配 IcebergWriterResult 接口变更。

**工作逻辑**：
三处 `writerResult.tableIdentifier()` 替换为 `writerResult.tableReference().identifier()`，通过新的 TableReference 访问路径获取 TableIdentifier。

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/data/WriterTestBase.java` (+6/-1 lines)

**修改目的**：适配 createTableWriter 方法签名变更。

**工作逻辑**：
构造 `TableReference tableReference = TableReference.of("test_catalog", TableIdentifier.of("name"), UUID.randomUUID())`，然后传给 `RecordUtils.createTableWriter(table, tableReference, config)`，替代原来的 `RecordUtils.createTableWriter(table, "name", config)`。新增 `UUID`、`TableIdentifier`、`TableReference` 的 import。

## 总结

该提交为 Kafka Connect Iceberg Sink 引入了表 UUID 校验机制，通过在 `TableReference` 中增加 UUID 字段并在 Coordinator 提交前比对此 UUID 与当前表 UUID，检测写入过程中表是否被替换，防止将旧表数据提交到新表导致的数据不一致。改动贯穿 events 模块（Avro schema 扩展）和 connect 模块（Writer/Factory/Result/Coordinator 链路），同时通过 `@Deprecated` 机制保持向后兼容。需要注意 Coordinator 中的 UUID 校验逻辑存在遗漏 `return` 语句的缺陷（在后续提交 #15011 中修复）。
