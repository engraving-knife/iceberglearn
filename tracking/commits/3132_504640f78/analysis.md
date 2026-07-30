# 提交 3132：Flink: Dynamic Sink: Add case-insensitive field matching (#14729)

## 提交信息

- **序号**：3132 / 4088
- **哈希**：504640f78bfde7338666d3451ac43fadbbc5627a
- **短哈希**：504640f78
- **日期**：2026-01-19
- **作者**：Maximilian Michels
- **提交说明**：Flink: Dynamic Sink: Add case-insensitive field matching (#14729)
- **PR/Issue**：#14729

## 总体目的

本提交为 Flink 的 Iceberg 动态 Sink（Dynamic Sink，即 `flink.sink.dynamic` 包下支持运行时自动建表与 schema 演进的 sink）增加大小写不敏感的字段名匹配能力，使流入数据中字段名大小写与目标 Iceberg 表不完全一致时仍能正确匹配、比较与演进 schema。

背景在于：动态 Sink 在写入时会比较输入数据 schema 与已有表 schema，决定是直接写入、做数据转换，还是触发 schema 演进（新增/删除/修改列）。此前字段名匹配是硬编码大小写敏感的——`CompareSchemasVisitor` 用 `struct.field(name)` 查找字段，并用 `String.equals` 比较字段名。然而在许多实际场景中，上游系统（如不同数据库、Kafka 源、大写/小写命名约定的系统）产生的字段名大小写可能与 Iceberg 表中已存在列的大小写不同，例如输入为 `userId` 而表中为 `userid`。在纯大小写差异下，旧逻辑会判定字段不存在而触发不必要的列新增，导致重复列或 schema 演进失败。

本提交引入 `caseSensitive` 开关（默认仍为 `true` 保持向后兼容），允许用户通过 `DynamicIcebergSink.Builder.caseSensitive(false)` 切换为大小写不敏感模式。在该模式下，字段查找改用 `struct.caseInsensitiveField(name)`，字段名比较改用 `equalsIgnoreCase`，schema 演进 API 也通过 `UpdateSchema.caseSensitive(false)` 以大小写不敏感方式应用变更，从而在整个比较-演进链路上保持一致行为。

## 如何达成设计目的

整体思路是从 Sink Builder 顶层引入 `caseSensitive` 参数，沿构造链路向下传递到 `DynamicRecordProcessor`、`DynamicTableUpdateOperator`、`TableMetadataCache`、`TableUpdater`，最终在 `CompareSchemasVisitor`（schema 比较）与 `EvolveSchemaVisitor`（schema 演进）两个核心 visitor 中实际生效。同时把原本散落在方法签名里的 `dropUnusedColumns` 收拢为 `TableMetadataCache` 的构造参数，简化调用面。涉及 7 个主源文件与 6 个测试文件。

## 修改详情

### `CompareSchemasVisitor.java` (+约25/-15 lines)

**修改目的**：在 schema 比较逻辑中支持大小写不敏感的字段名查找与比较。

**工作逻辑**：
新增 `caseSensitive` 字段并在构造函数传入。`struct` 比较阶段：检查表字段是否在输入 struct 中存在时，由 `struct.field(name)` 改为统一调用新增的 `getFieldFromStruct(fieldName, struct, caseSensitive)`——该静态方法在 `caseSensitive` 为真时调用 `struct.field(fieldName)`，否则调用 `struct.caseInsensitiveField(fieldName)`。字段名逐位比较时，由 `!fieldName.equals(tableFieldName)` 改为根据模式选择 `equals` 或 `equalsIgnoreCase`。`PartnerIdByNameAccessors` 内部类原先有一个公开构造（默认 caseSensitive=true）与一个 private 构造（可指定），现合并为单一公开构造 `PartnerIdByNameAccessors(tableSchema, caseSensitive)`，并复用 `getFieldFromStruct` 查找字段。原无参 `visit(dataSchema, tableSchema)` 被标记 `@Deprecated` + `@VisibleForTesting` 仅供测试使用。

### `EvolveSchemaVisitor.java` (+约15/-7 lines)

**修改目的**：在 schema 演进逻辑中支持大小写不敏感的列查找与变更应用。

**工作逻辑**：
新增 `caseSensitive` 字段，构造时对 `UpdateSchema` 调用 `api.caseSensitive(caseSensitive)`，使 Iceberg 的 schema 更新 API 也以相应模式解析列名。遍历目标字段查找伙伴字段时改用 `CompareSchemasVisitor.getFieldFromStruct(targetField.name(), partnerStruct, caseSensitive)`。遍历既有字段判断是否在目标 schema 中被删除时，按模式选择 `struct.field` 或 `struct.caseInsensitiveField`。还修正了一处 `columnName` 来源：新增列分支改为从 `targetSchema`（局部变量）而非 `this.targetSchema` 取列名。

### `TableMetadataCache.java` (+约20/-12 lines)

**修改目的**：将 `caseSensitive` 与 `dropUnusedColumns` 收为缓存成员，简化 `schema()` 签名。

**工作逻辑**：
新增 `caseSensitive`、`dropUnusedColumns` 两个成员字段，构造函数（含 `@VisibleForTesting` 的带 Clock 版本）增加这两个参数。`schema(identifier, input)` 与私有 `schema(identifier, input, allowRefresh)` 不再接收 `dropUnusedColumns` 参数，而是用成员字段；在比较输入 schema 与缓存表 schema 时，把原先硬编码的 `CompareSchemasVisitor.visit(input, tableSchema, true, dropUnusedColumns)` 中的 `true` 替换为成员 `caseSensitive`。

### `TableUpdater.java` (+约10/-8 lines)

**修改目的**：把 `caseSensitive` 透传给 schema 比较与演进。

**工作逻辑**：
构造函数增加 `caseSensitive` 参数并存为成员。`findOrCreateSchema` 中调用 `cache.schema(identifier, schema)`（去掉 dropUnusedColumns 参数），并在直接比较时用 `CompareSchemasVisitor.visit(schema, tableSchema, caseSensitive, dropUnusedColumns)`；触发演进时 `EvolveSchemaVisitor.visit(..., caseSensitive, dropUnusedColumns)`，提交失败重试的 `cache.schema(identifier, schema)` 同样简化。

### `DynamicIcebergSink.java` (+约15/-5 lines)

**修改目的**：在 Builder 暴露 `caseSensitive` 选项并向下传递。

**工作逻辑**：
Builder 新增 `private boolean caseSensitive = true` 成员与 `caseSensitive(boolean newCaseSensitive)` 公开方法（默认大小写敏感，保持向后兼容）。在创建 `DynamicRecordProcessor` 与 `DynamicTableUpdateOperator` 时把 `caseSensitive` 与 `dropUnusedColumns` 一并传入（同时调整参数顺序）。

### `DynamicRecordProcessor.java` (+约12/-6 lines)

**修改目的**：接收并向下传递 `caseSensitive`，构造缓存与 updater 时使用。

**工作逻辑**：
构造函数增加 `caseSensitive` 参数并存为成员，调整参数顺序。`open` 中构造 `TableMetadataCache` 时传入 `caseSensitive` 与 `dropUnusedColumns`；构造 `TableUpdater` 时传入 `caseSensitive`。`processElement` 中调用 `tableCache.schema(...)` 去掉 dropUnusedColumns 参数。

### `DynamicTableUpdateOperator.java` (+约10/-5 lines)

**修改目的**：接收并向下传递 `caseSensitive`。

**工作逻辑**：
构造函数增加 `caseSensitive` 参数并存为成员，调整参数顺序。`open` 中构造 `TableMetadataCache` 与 `TableUpdater` 时均传入 `caseSensitive` 与 `dropUnusedColumns`。

### 测试文件（6 个，共 +约400 lines）

**修改目的**：为大小写不敏感匹配新增覆盖测试。

**工作逻辑**：
`TestCompareSchemasVisitor` 新增 `testCaseInsensitiveFieldMatching`、`testCaseSensitiveFieldMatchingDefault`、`testCaseInsensitiveNestedStruct`、`testCaseInsensitiveWithMoreColumns`，验证在不同大小写下 struct 比较返回正确结果（SAME/CONVERSION_NEEDED 等）。`TestEvolveSchemaVisitor`、`TestTableMetadataCache`、`TestTableUpdater`、`TestDynamicTableUpdateOperator` 各自新增大小写不敏感场景测试，验证列查找、缓存匹配、schema 演进在 `caseSensitive(false)` 下行为正确。`TestDynamicIcebergSink` 新增 `testCaseInsensitiveSchemaMatching` 与 `testCaseSensitiveSchemaMatchingCreatesNewFields`，端到端验证 Builder 设置 `caseSensitive(false)` 时大小写差异字段被正确匹配而非新增重复列。

## 总结

本提交为 Flink 动态 Sink 引入可配置的大小写不敏感字段名匹配，通过从 Builder 顶层贯穿到 `CompareSchemasVisitor`/`EvolveSchemaVisitor` 的 `caseSensitive` 开关，使输入数据与目标表在仅有大小写差异时能正确匹配与演进 schema，避免误增重复列；默认保持大小写敏感以向后兼容，并配套补全了端到端与单元测试覆盖，提升了动态 Sink 对异构上游数据源的兼容性。
