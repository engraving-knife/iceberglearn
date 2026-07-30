# 提交 2976：Flink: Dynamic Sink: Add support for dropping columns (#14728)

## 提交信息

- **序号**：2976 / 4088
- **哈希**：a739cb3db89ad94a3525443cef9357ccb1897d3f
- **短哈希**：a739cb3db
- **日期**：2025-12-08
- **作者**：Maximilian Michels
- **提交说明**：Flink: Dynamic Sink: Add support for dropping columns (#14728)
- **PR/Issue**：#14728

## 总体目的

Iceberg 的 Flink Dynamic Sink（动态 sink）可以在写入时根据输入 schema 自动演化目标表 schema，从而支持流式数据源字段变更的"按需演进"。此前支持的演化操作仅包括新增列、放宽列类型（widening）、把必填列改为可选，但明确不支持删除列。原因是删除列会带来迟到的（late）或乱序（out-of-order）数据风险：一旦列被删除，已经写入的旧数据仍按历史 schema 保留，但后续到达的包含该列的记录无法再被正确处理，且回退（重新加回同名列）会被 Iceberg 视作全新列，旧数据无法被新列查询到。

该提交的目的在于：为用户提供一个"可选启用"的删除列能力。默认情况下行为不变（保留列、仅把必填列改为可选），当用户显式 opt-in 时，Dynamic Sink 会把当前表 schema 中存在、但输入 schema 中不存在的列删除掉，从而实现严格的输入/表 schema 一一对应。这对于上游确实下掉了某些字段、希望表结构随之收敛的流水线非常有用，但作者也在文档与注释中明确强调了删除列的副作用与不可恢复性，因此把开关默认关闭。

同时，文档对"支持/不支持"两节结构进行了重组，将"删除列"从"不支持"列表移到"支持（默认禁用）"列表，并详细解释了删除列后即便字段重新出现，Iceberg 也会按全新字段 ID 处理，导致旧数据无法被新列查询引用这一关键约束。

## 如何达成设计目的

整体思路是在 Dynamic Sink 链路上贯穿一个新的布尔开关 `dropUnusedColumns`：从用户入口 `DynamicIcebergSink.Builder`，经过 `DynamicRecordProcessor`、`DynamicTableUpdateOperator`（异步更新路径）一直传递到底层 `TableUpdater`、`TableMetadataCache` 以及两个核心 visitor（`CompareSchemasVisitor`、`EvolveSchemaVisitor`）。比较 schema 时根据该开关判断"输入缺失的列"是否需要触发 schema 更新；执行 schema 演化时根据该开关决定是调用 `UpdateSchema#makeColumnOptional` 还是 `UpdateSchema#deleteColumn`。这样既保持原有默认行为完全兼容，又在 opt-in 时让整条链路一致地以删除语义工作。

涉及的主要文件集中在 `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/` 下的 7 个生产代码文件和 6 个对应测试文件，外加 `docs/docs/flink-writes.md` 文档。

## 修改详情

### `docs/docs/flink-writes.md` (+17/-9 lines)

**修改目的**：更新 Dynamic Sink 文档，把删除列从"不支持"调整为"默认禁用但可启用"，并说明其风险。

**工作逻辑**：
将"Supported schema updates"改为子标题 `#### Supported schema updates`，加入 `Dropping columns (disabled by default)` 一项；新增一段说明：默认禁用是为了避免迟到/乱序数据带来的恢复困难，用户可显式 opt-in；并解释一旦列被删除，虽然旧 schema 仍可写入，但常规查询无法引用该列，若字段再次出现会被当作全新列（除名字外与旧列无任何关联，旧数据无法被新列查询到）。"Unsupported schema updates"改为 `##### Unsupported schema updates`，仅保留"重命名列"。在 Builder 配置表中新增一行 `dropUnusedColumns(boolean enabled)`。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/CompareSchemasVisitor.java` (+12/-6 lines)

**修改目的**：让 schema 比较器在 `dropUnusedColumns` 启用时，对输入 schema 中缺失的列（包括可选列）也判定为需要 schema 更新。

**工作逻辑**：
构造器与静态 `visit` 方法新增 `dropUnusedColumns` 参数。关键改动在 `struct` 方法中遍历 table schema 字段时的判断：原来是 `tableField.isRequired() && struct.field(tableField.name()) == null`，即只有必填列缺失才触发更新（可选列缺失会被当作 DATA_CONVERSION 处理）；现在改为 `struct.field(tableField.name()) == null && (tableField.isRequired() || dropUnusedColumns)`。这意味着启用删除时，任何在表中存在但输入中缺失的字段都会触发 `SCHEMA_UPDATE_NEEDED`，为后续真正执行删除动作打开通路。同时加了 `@SuppressWarnings("CyclomaticComplexity")` 抑制圈复杂度告警。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicIcebergSink.java` (+19/-0 lines)

**修改目的**：在 Builder 上暴露 `dropUnusedColumns(boolean)` 用户入口，并把开关透传给下游算子。

**工作逻辑**：
Builder 新增 `private boolean dropUnusedColumns = false` 字段，默认关闭；新增 `dropUnusedColumns(boolean newDropUnusedColumns)` 方法，注释中详细复述了文档中关于删除列副作用的说明。在 `append()`/构建算子链时，分别把这个值传给 `DynamicTableUpdateOperator` 构造器（异步更新路径）以及（在 immediate 模式下）经 `DynamicRecordProcessor` 传给 `TableUpdater` 与 `TableMetadataCache`。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecordProcessor.java` (+5/-2 lines)

**修改目的**：透传 `dropUnusedColumns` 给 immediate 模式下的 `TableUpdater` 与 `TableMetadataCache.schema(...)` 调用。

**工作逻辑**：
新增字段与构造器参数 `dropUnusedColumns`；在 immediate 模式下构造 `TableUpdater` 时传入该值；在查询 `tableCache.schema(data.tableIdentifier(), data.schema(), dropUnusedColumns)` 时把该值传给缓存，确保 schema 比较按用户配置的删除语义进行。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicTableUpdateOperator.java` (+5/-1 lines)

**修改目的**：在异步（集中式）更新路径的算子中携带 `dropUnusedColumns` 并传给内部 `TableUpdater`。

**工作逻辑**：
新增构造器参数 `dropUnusedColumns` 字段；在 `open()` 中构造 `TableUpdater` 时除了传入新的 `TableMetadataCache` 和 `catalog` 外，也传入 `dropUnusedColumns`，使异步路径同样按删除语义执行 schema 演化。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/EvolveSchemaVisitor.java` (+36/-11 lines)

**修改目的**：核心实现——在 schema 演化阶段，根据 `dropUnusedColumns` 决定对"表中存在但目标 schema 缺失"的列是改为可选还是删除。

**工作逻辑**：
新增 `TableIdentifier identifier` 字段（用于日志）和 `dropUnusedColumns` 字段，构造器与静态 `visit` 方法同步扩展。在 `struct` 方法中遍历 partner（existing）结构字段时，对于 `struct.field(existingField.name()) == null` 的字段：若 `dropUnusedColumns` 为 true，调用 `this.api.deleteColumn(columnName)` 并通过 `LOG.debug("{}: Dropping column: {}", identifier.name(), columnName)` 记录日志；否则保留原逻辑——仅在 `existingField.isRequired()` 时调用 `makeColumnOptional`。类注释同步更新：把"不支持删除列"改为"启用 `dropUnusedColumns` 时支持删除列"，并说明默认行为是把未使用列标记为可选以防迟到/乱序数据问题，启用删除时则改为移除以实现严格的一一对应。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/TableMetadataCache.java` (+5/-5 lines)

**修改目的**：让缓存的 schema 查询接口接收并使用 `dropUnusedColumns`，保证缓存命中判定与用户配置一致。

**工作逻辑**：
公开方法 `schema(TableIdentifier, Schema)` 改为 `schema(TableIdentifier, Schema, boolean dropUnusedColumns)`，私有重载方法也新增该参数。在比较输入 schema 与缓存中所有历史 table schema 时，把 `CompareSchemasVisitor.visit(input, tableSchema.getValue(), true, dropUnusedColumns)` 传入该值；在需要刷新缓存后重试时同样把该值传给递归调用 `schema(identifier, input, false, dropUnusedColumns)`。这样启用删除时，"输入缺失列"会被正确判为 `SCHEMA_UPDATE_NEEDED` 而非命中旧 schema。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/TableUpdater.java` (+12/-5 lines)

**修改目的**：在更新器中接收 `dropUnusedColumns`，并把它传给 `TableMetadataCache.schema(...)` 与 `CompareSchemasVisitor.visit(...)`、`EvolveSchemaVisitor.visit(...)`。

**工作逻辑**：
新增字段与构造器参数 `dropUnusedColumns`。`findOrCreateSchema` 中调用 `cache.schema(identifier, schema, dropUnusedColumns)`；在比较与演化阶段调用 `CompareSchemasVisitor.visit(schema, tableSchema, true, dropUnusedColumns)` 和 `EvolveSchemaVisitor.visit(identifier, updateApi, tableSchema, schema, dropUnusedColumns)`；提交后重新查询缓存以及捕获 `CommitFailedException` 后的回退查询也都传入该值。这样保证整条更新链路在删除语义上的一致性。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestCompareSchemasVisitor.java` (+62/-0 lines)

**修改目的**：为 `CompareSchemasVisitor` 在启用/未启用 `dropUnusedColumns` 时的行为补充单测。

**工作逻辑**：
新增常量 `DROP_COLUMNS`/`PRESERVE_COLUMNS` 提升可读性。新增四个测试：`testDropUnusedColumnsEnabled`（表有额外可选列时启用删除应判为 SCHEMA_UPDATE_NEEDED）、`testDropUnusedColumnsWithRequiredField`（表有额外必填列时同样触发更新）、`testDropUnusedColumnsWhenInputHasMoreFields`（输入比表多列时触发更新，验证删除不影响新增方向）、`testDropUnusedColumnsInNestedStruct`（嵌套 struct 中表含额外字段时，启用删除判为 SCHEMA_UPDATE_NEEDED，未启用时判为 DATA_CONVERSION_NEEDED，验证开关对嵌套结构的影响）。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicIcebergSink.java` (+49/-0 lines)

**修改目的**：端到端验证启用 `dropUnusedColumns(true)` 后的列删除与重新添加行为。

**工作逻辑**：
`testOptInDropUnusedColumns` 构造两条记录：第一条用 schema2（去掉 `extra` 列）触发删除，第二条用 schema1（重新包含 `extra`）触发重新添加；执行后断言表 schema 只剩 `id` 和 `data` 两列（`extra` 字段查询为 null），且能读到 2 条记录。验证了文档中"删除后重新出现会被当作新列"的行为在端到端写入流程中的表现。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicTableUpdateOperator.java` (+82/-0 lines)

**修改目的**：验证 `DynamicTableUpdateOperator` 在默认（保留）与启用删除两种模式下的实际表结构变化。

**工作逻辑**：
既有两处构造调用补上 `false` 参数。新增两个测试：`testDynamicTableUpdateOperatorPreserveUnusedColumns` 验证默认模式下表多出的 `data` 列被改为可选（`isOptional()` 为 true），列数仍为 2；`testDynamicTableUpdateOperatorDropUnusedColumns` 验证启用删除后 `data` 列被实际删除，表只剩 1 列（`id`），`findField("data")` 为 null。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestEvolveSchemaVisitor.java` (+107/-17 lines)

**修改目的**：适配 `EvolveSchemaVisitor.visit` 新签名，并新增删除/保留列的单测。

**工作逻辑**：
新增 `TableIdentifier TABLE`、`DROP_COLUMNS`、`PRESERVE_COLUMNS` 常量；所有既有 `visit` 调用改为带 `TABLE` 和 `PRESERVE_COLUMNS` 的新签名（保持既有行为不变）。新增 `testDropUnusedColumns`：表有 `a`、`b{nested1,nested2}`、`c`，目标仅保留 `a` 和 `b{nested2}`，启用删除后 `updateApi.apply()` 应与目标 schema 完全一致（含嵌套字段删除）。新增 `testPreserveUnusedColumns`：目标仅保留 `a`，未启用删除时 `apply()` 应与原 schema 完全一致（即不删不改）。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestTableMetadataCache.java` (+16/-9 lines)

**修改目的**：适配 `cache.schema(...)` 新签名（新增 `boolean dropUnusedColumns` 参数）。

**工作逻辑**：
所有 `cache.schema(tableIdentifier, ...)` 调用补上 `false` 参数（测试场景默认保留列语义），以及 `new TableUpdater(cache, catalog)` 补上 `false`。不改变测试断言，仅保持签名兼容。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestTableUpdater.java` (+37/-8 lines)

**修改目的**：适配新签名并新增 `dropUnusedColumns` 端到端测试。

**工作逻辑**：
既有 `new TableUpdater(cache, catalog)` 全部改为 `new TableUpdater(cache, catalog, false)`，`cache.schema(...)` 补 `false` 参数。新增 `testDropUnusedColumns`：表用 SCHEMA2 创建（含 `extra`），用 SCHEMA1（无 `extra`）+ `dropUnusedColumns=true` 调用 `tableUpdater.update(...)`，断言返回 `SAME`、表只剩 `id` 和 `data`、`extra` 字段为 null。

## 总结

该提交为 Flink Dynamic Sink 增加了一个默认关闭、用户可显式开启的"删除未使用列"能力，使表的 schema 演化能从"仅新增/放宽/放宽必填"扩展到"按输入严格收敛"。核心价值在于满足上游字段下线场景下表结构需要同步收敛的需求，同时通过默认禁用、文档警示与端到端测试充分提示删除列的不可恢复性与对迟到/乱序数据的风险。改动贯穿 Builder、Processor、UpdateOperator、TableUpdater、TableMetadataCache 与两个 Visitor，设计一致、向后兼容，并配有较为完整的单元与集成测试覆盖。
