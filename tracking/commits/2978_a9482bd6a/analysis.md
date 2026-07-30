# 提交 2978：Flink: Backport: Dynamic Sink: Add support for dropping columns (#14799)

## 提交信息

- **序号**：2978 / 4088
- **哈希**：a9482bd6a51965648e3b856f130445b0490d339e
- **短哈希**：a9482bd6a
- **日期**：2025-12-08
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport: Dynamic Sink: Add support for dropping columns (#14799)
- **PR/Issue**：#14799（回移自 #14728，即本序列序号 2976 的提交）

## 总体目的

这是一次 backport（回移）类提交，将本序列序号 2976（PR #14728）引入的"Flink Dynamic Sink 支持删除未使用列"功能回移到仍在维护的旧 Flink 版本分支。原 PR #14728 仅修改了 `flink/v2.1/flink/` 目录下的代码；本 backport 把完全相同的功能与逻辑应用到 `flink/v1.20/flink/` 与 `flink/v2.0/flink/` 两个版本目录下，使得使用 Flink 1.20 与 2.0 的用户也能用上 `dropUnusedColumns` 开关。

回移的原因很直接：Dynamic Sink 是 Iceberg Flink 集成中较新的能力，社区同时在维护多个 Flink 版本（1.20、2.0、2.1）的并行模块，新功能需要在所有受支持的版本上保持一致体验，避免用户因 Flink 版本差异而无法使用该能力。提交说明中明确写出 `Backport: Flink: Dynamic Sink: Add support for dropping columns (#14728)`，并在 PR #14799 中执行回移。注意本 backport 仅包含代码与测试改动，不包含 `docs/docs/flink-writes.md` 的文档改动——因为文档在仓库中是单一的、跨版本共享的，已在原 PR #14728 中更新过，无需重复修改。

## 如何达成设计目的

整体思路与原 PR 完全一致：在 Dynamic Sink 链路上贯穿一个新的布尔开关 `dropUnusedColumns`，从 `DynamicIcebergSink.Builder` 经过 `DynamicRecordProcessor`、`DynamicTableUpdateOperator` 传递到 `TableUpdater`、`TableMetadataCache` 与两个核心 visitor（`CompareSchemasVisitor`、`EvolveSchemaVisitor`）。比较 schema 时根据该开关判断"输入缺失的列"是否触发 schema 更新；执行 schema 演化时根据该开关决定调用 `makeColumnOptional` 还是 `deleteColumn`。差别仅在于改动覆盖的目录从 `flink/v2.1/flink/` 扩展到 `flink/v1.20/flink/` 与 `flink/v2.0/flink/`，每个版本的 7 个生产代码文件和 6 个测试文件都做了同样的修改。

## 修改详情

下面按文件说明。由于 v1.20 与 v2.0 两个版本目录下同名文件的改动内容完全相同（且与序号 2976 中 v2.1 的改动一致），这里对每个文件只描述一次，并标注其同时出现在两个版本目录下。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/CompareSchemasVisitor.java` 与 `flink/v2.0/flink/.../CompareSchemasVisitor.java` (+12/-6 each)

**修改目的**：让 schema 比较器在 `dropUnusedColumns` 启用时，对输入 schema 缺失的列（含可选列）也判定为需要 schema 更新。

**工作逻辑**：
构造器与静态 `visit` 方法新增 `dropUnusedColumns` 参数。`struct` 方法中的判断由 `tableField.isRequired() && struct.field(tableField.name()) == null` 改为 `struct.field(tableField.name()) == null && (tableField.isRequired() || dropUnusedColumns)`，使启用删除时任何缺失字段都触发 `SCHEMA_UPDATE_NEEDED`。同时加 `@SuppressWarnings("CyclomaticComplexity")`。

### `flink/v1.20/flink/.../DynamicIcebergSink.java` 与 `flink/v2.0/flink/.../DynamicIcebergSink.java` (+19/-0 each)

**修改目的**：在 Builder 上暴露 `dropUnusedColumns(boolean)` 用户入口并透传给下游算子。

**工作逻辑**：
Builder 新增 `private boolean dropUnusedColumns = false` 字段（默认关闭）与 `dropUnusedColumns(boolean)` 方法（含详细副作用说明注释）；在 `append()`/构建算子链时把该值传给 `DynamicTableUpdateOperator`，并在 immediate 模式下经 `DynamicRecordProcessor` 传给 `TableUpdater` 与 `TableMetadataCache`。

### `flink/v1.20/flink/.../DynamicRecordProcessor.java` 与 `flink/v2.0/flink/.../DynamicRecordProcessor.java` (+5/-2 each)

**修改目的**：透传 `dropUnusedColumns` 给 immediate 模式下的 `TableUpdater` 与 `TableMetadataCache.schema(...)`。

**工作逻辑**：
新增构造器参数与字段；immediate 模式下构造 `TableUpdater` 时传入该值；查询 `tableCache.schema(...)` 时也传入该值。

### `flink/v1.20/flink/.../DynamicTableUpdateOperator.java` 与 `flink/v2.0/flink/.../DynamicTableUpdateOperator.java` (+5/-1 each)

**修改目的**：在异步（集中式）更新路径算子中携带 `dropUnusedColumns` 并传给内部 `TableUpdater`。

**工作逻辑**：
新增构造器参数与字段；`open()` 中构造 `TableUpdater` 时传入该值。

### `flink/v1.20/flink/.../EvolveSchemaVisitor.java` 与 `flink/v2.0/flink/.../EvolveSchemaVisitor.java` (+36/-11 each)

**修改目的**：核心实现——根据 `dropUnusedColumns` 决定对"表中存在但目标 schema 缺失"的列是改为可选还是删除。

**工作逻辑**：
新增 `TableIdentifier identifier`（用于日志）与 `dropUnusedColumns` 字段，构造器与静态 `visit` 方法同步扩展。`struct` 方法中对 `struct.field(existingField.name()) == null` 的字段：若 `dropUnusedColumns` 为 true，调用 `this.api.deleteColumn(columnName)` 并 `LOG.debug` 记录；否则保留原逻辑（仅在 `existingField.isRequired()` 时 `makeColumnOptional`）。类注释同步更新。

### `flink/v1.20/flink/.../TableMetadataCache.java` 与 `flink/v2.0/flink/.../TableMetadataCache.java` (+5/-5 each)

**修改目的**：让缓存 schema 查询接口接收并使用 `dropUnusedColumns`。

**工作逻辑**：
公开 `schema(...)` 方法与私有重载方法新增 `dropUnusedColumns` 参数；比较输入与缓存历史 schema 时调用 `CompareSchemasVisitor.visit(..., dropUnusedColumns)`，递归刷新重试时也传入该值。

### `flink/v1.20/flink/.../TableUpdater.java` 与 `flink/v2.0/flink/.../TableUpdater.java` (+12/-5 each)

**修改目的**：在更新器中接收 `dropUnusedColumns` 并传给 cache/visitor。

**工作逻辑**：
新增字段与构造器参数；`findOrCreateSchema` 中 `cache.schema(...)`、`CompareSchemasVisitor.visit(...)`、`EvolveSchemaVisitor.visit(...)` 以及提交后/异常回退后的 cache 查询都传入该值。

### `flink/v1.20/flink/.../TestCompareSchemasVisitor.java` 与 `flink/v2.0/flink/.../TestCompareSchemasVisitor.java` (+62/-0 each)

**修改目的**：为 `CompareSchemasVisitor` 在启用/未启用删除时的行为补单测。

**工作逻辑**：
新增 `DROP_COLUMNS`/`PRESERVE_COLUMNS` 常量与四个测试：`testDropUnusedColumnsEnabled`、`testDropUnusedColumnsWithRequiredField`、`testDropUnusedColumnsWhenInputHasMoreFields`、`testDropUnusedColumnsInNestedStruct`（验证嵌套 struct 下启用删除判为 `SCHEMA_UPDATE_NEEDED`，未启用判为 `DATA_CONVERSION_NEEDED`）。

### `flink/v1.20/flink/.../TestDynamicIcebergSink.java` 与 `flink/v2.0/flink/.../TestDynamicIcebergSink.java` (+49/-0 each)

**修改目的**：端到端验证启用 `dropUnusedColumns(true)` 后的列删除与重新添加行为。

**工作逻辑**：
`testOptInDropUnusedColumns` 构造两条记录（先删除 `extra`，再用 schema1 重新添加），执行后断言表只剩 `id`、`data` 两列且能读到 2 条记录。

### `flink/v1.20/flink/.../TestDynamicTableUpdateOperator.java` 与 `flink/v2.0/flink/.../TestDynamicTableUpdateOperator.java` (+82/-0 each)

**修改目的**：验证 `DynamicTableUpdateOperator` 在默认与启用删除两种模式下的表结构变化。

**工作逻辑**：
既有构造调用补 `false` 参数；新增 `testDynamicTableUpdateOperatorPreserveUnusedColumns`（默认模式下 `data` 列改可选，列数仍为 2）与 `testDynamicTableUpdateOperatorDropUnusedColumns`（启用删除后 `data` 列被删，表只剩 1 列）。

### `flink/v1.20/flink/.../TestEvolveSchemaVisitor.java` 与 `flink/v2.0/flink/.../TestEvolveSchemaVisitor.java` (+107/-17 each)

**修改目的**：适配 `EvolveSchemaVisitor.visit` 新签名并新增删除/保留列单测。

**工作逻辑**：
新增 `TABLE`、`DROP_COLUMNS`、`PRESERVE_COLUMNS` 常量；所有既有 `visit` 调用改为带 `TABLE` 与 `PRESERVE_COLUMNS` 的新签名。新增 `testDropUnusedColumns`（嵌套字段删除后 `apply()` 与目标 schema 完全一致）与 `testPreserveUnusedColumns`（未启用删除时 `apply()` 与原 schema 完全一致）。

### `flink/v1.20/flink/.../TestTableMetadataCache.java` 与 `flink/v2.0/flink/.../TestTableMetadataCache.java` (+16/-9 each)

**修改目的**：适配 `cache.schema(...)` 新签名。

**工作逻辑**：
所有 `cache.schema(...)` 调用补 `false` 参数，`new TableUpdater(cache, catalog)` 补 `false`，不改变断言。

### `flink/v1.20/flink/.../TestTableUpdater.java` 与 `flink/v2.0/flink/.../TestTableUpdater.java` (+37/-8 each)

**修改目的**：适配新签名并新增 `dropUnusedColumns` 端到端测试。

**工作逻辑**：
既有 `new TableUpdater(cache, catalog)` 改为 `new TableUpdater(cache, catalog, false)`，`cache.schema(...)` 补 `false`；新增 `testDropUnusedColumns` 验证启用删除后表 `extra` 列被实际删除。

## 总结

该提交是序号 2976（PR #14728）的 backport，将"Flink Dynamic Sink 支持删除未使用列"功能原样回移到 `flink/v1.20/flink/` 与 `flink/v2.0/flink/` 两个版本目录，使旧版本 Flink 用户同样能用上 `dropUnusedColumns` 开关。改动内容与原 PR 完全一致（每个版本 7 个生产代码文件 + 6 个测试文件），仅省略了文档改动（文档已在原 PR 中更新且为跨版本共享）。核心价值在于保持多个维护版本之间的功能一致性，避免版本碎片化。
