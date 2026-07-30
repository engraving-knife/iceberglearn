# 提交 3137：Flink: Backport: Dynamic Sink: Add case-insensitive field matching (#15089)

## 提交信息

- **序号**：3137 / 4088
- **哈希**：8e255cd9114c4b35fb6b2d6a9e2ec0c2137b2f61
- **短哈希**：8e255cd91
- **日期**：2026-01-20
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport: Dynamic Sink: Add case-insensitive field matching (#15089)
- **PR/Issue**：#15089（回移自 #14729）

## 总体目的

本提交是 main 分支 PR #14729 的回移，目标分支为 Flink 1.4.x 维护分支。它要解决的问题是：Flink 动态 Iceberg Sink（`DynamicIcebergSink`）在做 schema 比对与 schema evolution 时，此前对字段名的匹配默认且仅支持大小写敏感，导致上游流式数据中字段名仅大小写不同（如 `id` 与 `ID`）时，sink 会把它们当作不同字段，从而不断为同一逻辑列新增列、触发不必要的 schema 演进，甚至与现有表结构冲突。在很多数据管道里，源系统字段大小写并不稳定（数据库迁移、不同上游约定），用户希望 sink 能按大小写不敏感的方式把 `id`/`ID`/`Id` 对齐到同一列。

进一步看 diff，原先 `CompareSchemasVisitor` 的实现存在内部不一致：其静态 `visit(...)` 已经接收 `caseSensitive` 参数并把它传给了 `PartnerIdByNameAccessors`，但访问器内部的字段查找却混用了直接 `struct.field(name)`（恒为大小写敏感）与 `caseInsensitiveField(name)`，且 `struct(...)` 方法里比对字段名用的是 `equals`（大小写敏感），并不会按 `caseSensitive` 切换；同时 `EvolveSchemaVisitor` 完全没有把 `caseSensitive` 透传给 `UpdateSchema` API 与字段查找逻辑。本提交统一了这条链路上所有字段查找入口，使其真正按 `caseSensitive` 标志行为一致。

由于这是回移，改动同时覆盖 `flink/v1.20` 与 `flink/v2.0` 两个 Flink 版本目录，且两个版本的生产与测试代码改动逐字相同。

## 如何达成设计目的

整体思路是沿动态 sink 的数据通路把一个新的 `caseSensitive` 布尔值从顶层 `DynamicIcebergSink.Builder` 一路下传到实际执行字段比对/演进的组件：`DynamicRecordProcessor`、`DynamicTableUpdateOperator`、`TableMetadataCache`、`TableUpdater`、`CompareSchemasVisitor`、`EvolveSchemaVisitor`，并在每个组件里真正依据该标志选择 `struct.field(name)`（敏感）或 `struct.caseInsensitiveField(name)`（不敏感），同时把 `UpdateSchema.caseSensitive(...)` 一起设置。`caseSensitive` 默认为 `true`，保持向后兼容。同时为各组件补齐了覆盖大小写敏感/不敏感两种模式的单元测试与端到端测试。

## 修改详情

> 以下按 v1.20 目录描述；`flink/v2.0` 对应文件改动与 v1.20 完全相同（仅路径前缀不同），不重复展开。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicIcebergSink.java` (+15/-5 lines)

**修改目的**：在 sink 顶层 Builder 暴露 `caseSensitive` 选项并下传给算子。

**工作逻辑**：
Builder 新增字段 `private boolean caseSensitive = true;`（默认大小写敏感，兼容旧行为）与公开方法 `caseSensitive(boolean newCaseSensitive)`。在构建 `DynamicRecordProcessor` 与 `DynamicTableUpdateOperator` 时，把 `caseSensitive` 与 `dropUnusedColumns` 一起传入（同时调整了参数顺序，把二者放到末尾）。这使得用户可以通过 `DynamicIcebergSink.forInput(...).caseSensitive(false).append()` 启用大小写不敏感匹配。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecordProcessor.java` (+15/-5 lines)

**修改目的**：接收 `caseSensitive` 并在初始化缓存与 updater 时透传。

**工作逻辑**：
构造函数新增 `caseSensitive` 参数（与 `dropUnusedColumns` 一起移到参数列表末尾）。`open()` 中构造 `TableMetadataCache` 时传入 `caseSensitive`、`dropUnusedColumns`；构造 `TableUpdater` 时同样传入。`processElement` 中调用 `tableCache.schema(...)` 时去掉了原先每次传 `dropUnusedColumns` 的参数（因为缓存内部已持有该标志），简化为 `tableCache.schema(data.tableIdentifier(), data.schema())`。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicTableUpdateOperator.java` (+13/-4 lines)

**修改目的**：异步表更新算子同样接收并透传 `caseSensitive`。

**工作逻辑**：
构造函数新增 `caseSensitive` 参数，`open()` 中构造 `TableMetadataCache` 与 `TableUpdater` 时透传，行为与 `DynamicRecordProcessor` 对称，保证无论是立即更新路径还是异步更新路径都使用一致的匹配策略。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/CompareSchemasVisitor.java` (+30/-17 lines)

**修改目的**：统一并真正实现按 `caseSensitive` 切换的字段查找。

**工作逻辑**：
- `CompareSchemasVisitor` 新增 `caseSensitive` 字段并在构造函数中赋值；静态 `visit(...)` 把 `caseSensitive` 同时传给 visitor 与 `PartnerIdByNameAccessors`。
- `struct(...)` 方法原先用 `struct.field(tableField.name())` 判断表字段是否存在于输入 schema，现改为 `getFieldFromStruct(tableField.name(), struct, caseSensitive)`；字段名逐一比对原先固定用 `equals`，改为按 `caseSensitive` 选择 `equals` 或 `equalsIgnoreCase`，从而决定是否返回 `DATA_CONVERSION_NEEDED`。
- 新增工具方法 `getFieldFromStruct(String, Types.StructType, boolean)`，集中封装 `caseSensitive ? struct.field(name) : struct.caseInsensitiveField(name)`，并被 visitor 自身与 `EvolveSchemaVisitor`、`PartnerIdByNameAccessors` 共用。
- `PartnerIdByNameAccessors` 的 `caseSensitive` 现在通过构造函数显式传入（删除了原先仅包内可见的私有双参构造），字段查找统一改用 `getFieldFromStruct`，消除了之前“访问器大小写不敏感但 visitor 内部敏感”的不一致。
- 旧的 `visit(Schema, Schema)` 双参重载被标记 `@Deprecated` 并加 `@VisibleForTesting`，保留向后兼容。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/EvolveSchemaVisitor.java` (+18/-6 lines)

**修改目的**：让 schema 演进（加列/改列/删列）也遵循 `caseSensitive`。

**工作逻辑**：
构造函数新增 `caseSensitive` 参数，并在构造时调用 `api.caseSensitive(caseSensitive)` 把 Iceberg `UpdateSchema` API 也设置为对应的匹配模式（这影响 `UpdateSchema` 内部按名查找列的行为）。`struct(...)` 中遍历 target 字段时，对 partner 结构的字段查找改用 `CompareSchemasVisitor.getFieldFromStruct(targetField.name(), partnerStruct, caseSensitive)`；遍历 existing 字段判断是否需要删除时，也按 `caseSensitive` 选择 `struct.field` 或 `struct.caseInsensitiveField`，避免在大小写不敏感模式下误删同名列。同时把 `this.existingSchema.findColumnName(...)` 的一处冗余 `this.` 改为 `targetSchema.findColumnName(...)`（与上下文一致，属于顺手修正）。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/TableMetadataCache.java` (+27/-8 lines)

**修改目的**：缓存持有 `caseSensitive`/`dropUnusedColumns`，简化对外接口。

**工作逻辑**：
两个构造函数（生产用与 `@VisibleForTesting` 带 `Clock` 的）都新增 `caseSensitive`、`dropUnusedColumns` 参数并保存为字段。公开方法 `schema(TableIdentifier, Schema)` 不再接收 `dropUnusedColumns`（删除了旧签名），内部私有 `schema(...)` 同样去掉该参数，改用保存的字段。在比对输入 schema 与缓存的表 schema 时，调用 `CompareSchemasVisitor.visit(input, tableSchema.getValue(), caseSensitive, dropUnusedColumns)`，把真实的匹配模式传进去。这样调用方无需每次重复传参，且保证整个缓存生命周期内匹配策略一致。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/TableUpdater.java` (+12/-6 lines)

**修改目的**：updater 持有 `caseSensitive` 并在比对/演进时使用。

**工作逻辑**：
构造函数新增 `caseSensitive` 字段。`findOrCreateSchema(...)` 中调用 `cache.schema(identifier, schema)` 不再传 `dropUnusedColumns`；当需要重新比对或触发演进时，分别用 `CompareSchemasVisitor.visit(schema, tableSchema, caseSensitive, dropUnusedColumns)` 与 `EvolveSchemaVisitor.visit(identifier, updateApi, tableSchema, schema, caseSensitive, dropUnusedColumns)`，commit 失败后的重试路径同样使用缓存内 `cache.schema(identifier, schema)`。这保证新建表、演进表、并发冲突重试三条路径都按同一 `caseSensitive` 策略执行。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestCompareSchemasVisitor.java` (+109/-34 lines)

**修改目的**：为 schema 比对 visitor 补充大小写敏感/不敏感用例。

**工作逻辑**：
新增 `testCaseInsensitiveFieldMatching`、`testCaseSensitiveFieldMatchingDefault`、`testCaseInsensitiveNestedStruct`、`testCaseInsensitiveWithMoreColumns` 等测试，覆盖平铺字段、嵌套结构、字段数量不一致等场景下 `caseSensitive` 取 `true`/`false` 时 `Result`（`SAME`/`DATA_CONVERSION_NEEDED`/`SCHEMA_UPDATE_NEEDED` 等）的正确性，并对既有用例调整为通过新的多参 `visit(...)` 入口调用。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicIcebergSink.java` (+85/-0 lines)

**修改目的**：端到端验证大小写匹配对实际写入与 schema 演进的影响。

**工作逻辑**：
- `testCaseInsensitiveSchemaMatching`：用 `id`/`data`、`ID`/`DATA`、`Id`/`Data` 三种大小写 schema 的数据写入同一表 `t1`，开启 `.caseSensitive(false)`，执行后 `verifyResults(rows)` 通过，说明三者被对齐到同一组列。
- `testCaseSensitiveSchemaMatchingCreatesNewFields`：同样写 `id`/`data` 与 `ID`/`DATA`，但开启 `.caseSensitive(true)`，断言结果表有 4 列（`id`、`ID`、`data`、`DATA` 各自独立），验证默认行为未变且大小写不同确会新增列。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicTableUpdateOperator.java` (+63/-19 lines)

**修改目的**：为异步表更新算子补充大小写匹配测试，并适配新的构造函数签名。

**工作逻辑**：
既有用例调整为传入 `caseSensitive` 参数构造算子；新增用例验证在大小写不敏感模式下，算子更新表 schema 时不会为仅大小写不同的字段重复加列。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestEvolveSchemaVisitor.java` (+119/-35 lines)

**修改目的**：覆盖 `EvolveSchemaVisitor` 在两种匹配模式下的加列/删列/类型变更行为。

**工作逻辑**：
新增多个测试验证 `caseSensitive(false)` 时大小写不同字段被视为同列（不会重复 add，也不会误删），以及 `caseSensitive(true)` 时它们被视为不同列从而触发 addColumn；既有用例改用新的多参 `visit(...)` 入口。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestTableMetadataCache.java` (+78/-26 lines)

**修改目的**：验证缓存按 `caseSensitive` 缓存与比对 schema。

**工作逻辑**：
适配新的 `TableMetadataCache` 构造函数与 `schema(...)` 签名；新增用例验证在大小写不敏感模式下，仅大小写不同的输入 schema 命中缓存的 `SAME` 结果，避免不必要的 schema 刷新与演进。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestTableUpdater.java` (+79/-30 lines)

**修改目的**：验证 `TableUpdater` 在两种模式下创建/演进表的正确性。

**工作逻辑**：
适配新构造函数；新增用例验证大小写不敏感模式下不会因大小写差异触发 `SCHEMA_UPDATE_NEEDED`，而敏感模式下会触发真实的 schema 演进或新列创建。

### `flink/v2.0/...` 下同名 13 个文件

**修改目的**：在 Flink 2.0 版本目录同步全部上述生产与测试改动。

**工作逻辑**：
与 `flink/v1.20` 逐字相同（仅路径前缀由 `flink/v1.20` 变为 `flink/v2.0`），确保两个维护版本具备一致的大写/小写匹配能力与测试覆盖。

## 总结

该回移提交为 Flink 动态 Iceberg Sink 引入了可配置的字段名大小写匹配能力，沿整条数据通路（Builder → 处理器/更新算子 → 缓存 → updater → schema 比对/演进 visitor）统一传递并真正实现 `caseSensitive` 行为，修复了原先访问器与 visitor 内部行为不一致的问题，默认保持大小写敏感以兼容旧用法，并通过 v1.20/v2.0 双版本的大量单元与端到端测试锁定了两种模式的语义。
