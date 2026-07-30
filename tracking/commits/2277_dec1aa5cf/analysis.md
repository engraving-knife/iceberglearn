# 提交 2277：Flink: Backports TableSchema migration to Flink 1.19 and 1.20 (#13392)

## 提交信息

- **序号**：2277 / 4088
- **哈希**：dec1aa5cf75d11ad6ef7592ead34c469a05d4122
- **短哈希**：dec1aa5cf
- **日期**：2025-06-26 15:54:44 +0200
- **作者**：Liam Bao
- **提交说明**：Flink: Backports TableSchema migration to Flink 1.19 and 1.20 (#13392)
- **PR/Issue**：#13392（backports #13072）

## 总体目的

本提交将 Flink Table API 中从已废弃的 `TableSchema` 迁移到新的 `ResolvedSchema` 的变更回移植到 Flink 1.19 和 1.20 模块。Flink 社区在新版本中废弃了 `org.apache.flink.table.api.TableSchema`，转而推荐使用 `org.apache.flink.table.catalog.ResolvedSchema` 以及 `org.apache.flink.table.api.Schema`（unresolved schema）。`TableSchema` 在 Flink 2.0 中被移除，因此 Iceberg 的 Flink 集成代码需要迁移以保持向前兼容。

这次迁移涉及面广（73 个文件，+3036/-1177 行），同时修改了 v1.19 和 v1.20 两个模块的对应代码。核心策略是：新增基于 `ResolvedSchema` 的 API 方法，将原有基于 `TableSchema` 的方法标记为 `@Deprecated`（将在 2.0.0 移除），并在关键路径（如 `IcebergTableSink.consumeDataStream`）中同时支持新旧两种 schema 表示，根据传入的非 null 字段选择对应路径。这种渐进式迁移保证了向后兼容性，不破坏现有用户的代码。

## 如何达成设计目的

- 在 `FlinkSchemaUtil` 中新增 `toResolvedSchema(Schema)`、`toResolvedSchema(RowType)`、`convert(Schema, ResolvedSchema)` 等方法，与原有 `TableSchema` 方法并行提供，旧方法标记 `@Deprecated`。
- 在 `FlinkCatalog` 中将 schema 比较改为基于 `getUnresolvedSchema()`，`CatalogTable` 构建改为使用新的 builder API（`CatalogTable.newBuilder().schema(Schema.newBuilder().fromResolvedSchema(...))`）。
- 在 `FlinkDynamicTableFactory` 中用 `ResolvedSchema` + `Column::isPhysical` 过滤替代 `TableSchemaUtils.getPhysicalSchema`。
- 在 `IcebergTableSink`、`FlinkSink.Builder`、`IcebergSink.Builder`、`IcebergSinkBuilder` 中新增 `resolvedSchema` 字段和对应 builder 方法，旧 `tableSchema` 字段保留并标记 `@Deprecated`。`consumeDataStream` 中根据 `resolvedSchema != null` 分流到新路径。
- 在 `IcebergTableSource`、`RowConverter`、`FlinkCompatibilityUtil` 中适配新 API，新增 `isPhysicalColumn(Schema.UnresolvedColumn)` 辅助方法。
- 大量测试文件同步更新为使用 `ResolvedSchema` API。

## 修改详情

### `FlinkSchemaUtil.java` (v1.20, +120/-12 lines)

**修改目的**：提供基于 `ResolvedSchema` 的 schema 转换方法，替代废弃的 `TableSchema` 方法。

**工作逻辑**：
- 新增 `convert(Schema baseSchema, ResolvedSchema flinkSchema)`：基于给定 schema 重新分配 field id 和 doc，并通过 `FlinkFixupTypes.fixup` 修复 Flink 无法表示的类型（如 UUID），处理主键。
- 新增 `toResolvedSchema(RowType)`：将 RowType 转为只含物理列的 `ResolvedSchema`。
- 新增 `toResolvedSchema(Schema)`：将 Iceberg Schema 转为 `ResolvedSchema`，包含主键（`UniqueConstraint`）处理，并从 Flink `DefaultSchemaResolver` 复制 `validatePrimaryKey` 逻辑（校验主键列存在、为物理列、非 null、无重复）。
- 原 `convert(TableSchema)`、`convert(Schema, TableSchema)`、`toSchema(RowType)`、`toSchema(Schema)` 标记 `@Deprecated since 1.10.0, will be removed in 2.0.0`。

### `FlinkCatalog.java` (v1.20, +18/-15 lines)

**修改目的**：迁移 catalog 的 schema 处理到新 API。

**工作逻辑**：`validateTableSchemaAndPartition` 由原先手动比较 `TableSchema` 的 columns/watermarkSpecs/primaryKey 改为直接比较 `ct1.getUnresolvedSchema()` 与 `ct2.getUnresolvedSchema()`。`toCatalogTableWithProps` 由 `new CatalogTableImpl(schema, partitionKeys, props, null)` 改为使用 `CatalogTable.newBuilder().schema(Schema.newBuilder().fromResolvedSchema(resolvedSchema).build()).partitionKeys(...).options(...).build()`。校验物理列时改用 `table.getUnresolvedSchema().getColumns()` 配合新的 `FlinkCompatibilityUtil.isPhysicalColumn`。

### `IcebergTableSink.java` (v1.20, +72/-23 lines)

**修改目的**：支持基于 `ResolvedSchema` 的 sink 构建，同时保留旧 `TableSchema` 路径。

**工作逻辑**：新增 `resolvedSchema` 字段和新构造函数 `IcebergTableSink(TableLoader, ResolvedSchema, ReadableConfig, Map)`，旧构造函数标记 `@Deprecated`。`consumeDataStream` 中：若 `resolvedSchema != null` 走新路径（用 `resolvedSchema.getPrimaryKey()` 获取 equality columns，调用 `IcebergSink/FlinkSink` 的 `.resolvedSchema(...)`）；否则走旧 `tableSchema` 路径。复制构造函数同时拷贝两个字段。

### `FlinkDynamicTableFactory.java` (v1.20, +14/-5 lines)

**修改目的**：source/sink 工厂改用 `ResolvedSchema`。

**工作逻辑**：用 `ResolvedSchema.of(resolvedCatalogTable.getResolvedSchema().getColumns().stream().filter(Column::isPhysical).collect(...))` 替代 `TableSchemaUtils.getPhysicalSchema`，创建 `IcebergTableSource`/`IcebergTableSink` 时传入 `ResolvedSchema`。

### `FlinkSink.java` / `IcebergSink.java` / `IcebergSinkBuilder.java` (v1.20)

**修改目的**：Builder 新增 `resolvedSchema` 配置项与对应方法，旧 `tableSchema` 标记废弃。

**工作逻辑**：Builder 新增 `resolvedSchema(ResolvedSchema)` 方法；新增 `forRow(DataStream<Row>, ResolvedSchema)` 入口方法。`toFlinkRowType` 新增 `ResolvedSchema` 重载。在计算 RowType 时优先使用 `resolvedSchema`（`resolvedSchema != null ? toFlinkRowType(schema, resolvedSchema) : toFlinkRowType(schema, tableSchema)`）。`IcebergSinkBuilder` 接口新增 `resolvedSchema` 和 `forRow(DataStream, ResolvedSchema, boolean)` 方法。

### `IcebergTableSource.java` / `RowConverter.java` / `FlinkCompatibilityUtil.java` (v1.20)

**修改目的**：source 端和工具类适配新 API。

**工作逻辑**：`IcebergTableSource` 字段改用 `ResolvedSchema`，`getProjectedSchema()` 返回 `ResolvedSchema`。`RowConverter` 使用 `FlinkSchemaUtil.toResolvedSchema(icebergSchema)` 获取列信息。`FlinkCompatibilityUtil` 新增 `isPhysicalColumn(Schema.UnresolvedColumn)` 方法（基于 `Schema.UnresolvedPhysicalColumn` instanceof 判断），旧 `isPhysicalColumn(TableColumn)` 标记废弃。

### 测试文件 (v1.19 + v1.20, 大量)

**修改目的**：将测试代码迁移到 `ResolvedSchema` API。

**工作逻辑**：涉及 `TestFlinkSchemaUtil`、`TestFlinkCatalogTable`、`TestFlinkFilters`、`TestFlinkIcebergSink*`、`TestIcebergSink*`、`TestIcebergSourceBounded*`、`BoundedTableFactory` 等约 40+ 测试/辅助文件，将 `TableSchema` 用法替换为 `ResolvedSchema`/`Schema` 对应 API。注：所有改动在 v1.19 和 v1.20 两个模块各重复一份。

## 总结

本提交是 Flink `TableSchema` → `ResolvedSchema` 迁移的大型回移植，涉及 73 个文件、净增约 1859 行代码。采用渐进式迁移策略——新增 `ResolvedSchema` API 并废弃旧 `TableSchema` API，在关键路径双轨支持，确保向后兼容。这使 Iceberg 的 Flink 1.19/1.20 集成为 Flink 2.0（移除 `TableSchema`）做好了准备，是技术债清理和向前兼容的重要工作。
