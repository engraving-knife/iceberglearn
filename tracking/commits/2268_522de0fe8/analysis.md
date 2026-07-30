# 提交 2268：Flink: Migrate Flink TableSchema to Schema/ResolvedSchema (#13072)

## 提交信息

- **序号**：2268 / 4088
- **哈希**：522de0fe839df2f22f4472241bce632995adc01d
- **短哈希**：522de0fe8
- **日期**：2025-06-25 10:34:09 +0200
- **作者**：Liam Bao
- **提交说明**：Flink: Migrate Flink TableSchema to Schema/ResolvedSchema
- **PR/Issue**：#13072

## 总体目的

本提交将 Iceberg Flink 模块中使用的已弃用（deprecated）的 `org.apache.flink.table.legacy.api.TableSchema` 迁移到新的 `org.apache.flink.table.api.Schema` 和 `org.apache.flink.table.catalog.ResolvedSchema` API。在 Flink 的 API 演进中，`TableSchema` 已被标记为弃用（位于 `legacy` 包中），取而代之的是 `Schema`（未解析的 schema）和 `ResolvedSchema`（已解析的 schema，包含已计算的列类型、主键、水印等信息）。

这一迁移是必要的 API 现代化工作，确保 Iceberg Flink 集成与 Flink 的最新 API 保持一致，避免使用弃用 API 带来的兼容性风险。迁移涉及 Flink 集成的多个方面：Catalog 操作、动态表工厂、Schema 转换工具、Source/Sink 构建等。

## 如何达成设计目的

- 在 `FlinkSchemaUtil` 中新增基于 `ResolvedSchema` 的转换方法，同时保留旧的 `TableSchema` 方法但标记为 `@Deprecated`。
- 在 `FlinkCatalog` 中将所有使用 `TableSchema` 的地方替换为 `ResolvedSchema` 或 `Schema`。
- 在 `FlinkDynamicTableFactory`、`IcebergTableSink`、`FlinkSink`、`IcebergSink`、`IcebergSource`、`IcebergTableSource` 等类中进行相应迁移。
- 新增 `FlinkCompatibilityUtil` 中的辅助方法以兼容 API 差异。
- 更新所有相关测试以使用新的 API。

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/FlinkSchemaUtil.java` (修改, +158/-XX lines)

**修改目的**：提供基于 ResolvedSchema 的 Schema 转换方法。

**工作逻辑**：
1. 新增 `convert(Schema baseSchema, ResolvedSchema flinkSchema)` 方法，将 Flink ResolvedSchema 转换为 Iceberg Schema，使用基础 schema 的 field ID 进行重新分配（`TypeUtil.reassignIds` 和 `TypeUtil.reassignDoc`），处理 Flink 无法表示的类型（如 UUID），并支持主键到标识字段的转换。
2. 新增 `toResolvedSchema(RowType rowType)` 方法，将 Iceberg RowType 转换为 Flink ResolvedSchema，替代原有的 `toSchema(RowType)` 方法。
3. 将原有的 `convert(TableSchema)`、`convert(Schema, TableSchema)`、`toSchema(RowType)` 方法标记为 `@Deprecated`，注明将在 2.0.0 移除。
4. 优化 `convert(ResolvedSchema)` 方法中主键处理的代码，使用 `Optional.map().orElse()` 简化逻辑。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/FlinkCatalog.java` (修改, +36/-XX lines)

**修改目的**：将 Catalog 操作中的 TableSchema 替换为 ResolvedSchema/Schema。

**工作逻辑**：
1. `createTable()` 方法中使用 `table.getResolvedSchema()` 获取 schema，分区键从 `table.getPartitionKeys()` 获取。
2. `validateTableSchemaAndPartition()` 方法简化为使用 `ct1.getUnresolvedSchema()` 和 `ct2.getUnresolvedSchema()` 的直接比较，替代原来手动比较列、主键和水印规范的复杂逻辑。
3. `toCatalogTableWithProps()` 方法使用 `FlinkSchemaUtil.toResolvedSchema()` 和 `Schema.newBuilder().fromResolvedSchema()` 构建 CatalogTable，替代原有的 `FlinkSchemaUtil.toSchema()` 和 `schema.toSchema()`。
4. 移除对 `org.apache.flink.table.legacy.api.TableSchema` 的 import，替换为 `ResolvedSchema` 和 `org.apache.flink.table.api.Schema`。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/FlinkDynamicTableFactory.java` (修改, +23/-XX lines)

**修改目的**：将动态表工厂中的 TableSchema 使用迁移到新 API。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/IcebergTableSink.java` (修改, +115/-XX lines)

**修改目的**：将 Table Sink 中的 TableSchema 使用迁移到 ResolvedSchema。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkSink.java` (修改, +62/-XX lines)

**修改目的**：将 FlinkSink 中的 schema 处理从 TableSchema 迁移到 ResolvedSchema。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergSink.java` (修改, +72/-XX lines)

**修改目的**：将 IcebergSink 中的 schema 处理迁移到新 API。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergSinkBuilder.java` (修改, +20/0 lines)

**修改目的**：新增基于 ResolvedSchema 的 Builder 方法。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/source/IcebergSource.java` (修改, +18/-XX lines)

**修改目的**：将 Source 中的 schema 处理迁移到新 API。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/source/IcebergTableSource.java` (修改, +27/-XX lines)

**修改目的**：将 Table Source 中的 TableSchema 使用迁移到 ResolvedSchema。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/source/reader/RowConverter.java` (修改, +12/-XX lines)

**修改目的**：适配 RowConverter 中的 schema 类型变更。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/util/FlinkCompatibilityUtil.java` (修改, +9/0 lines)

**修改目的**：新增兼容性辅助方法支持新 API 迁移。

### `LICENSE` (修改, +1/0 lines)

**修改目的**：更新 LICENSE 文件以反映依赖变更。

### 测试文件 (多个文件修改)

包括 `TestFlinkSchemaUtil.java`（+226/-XX lines）、`TestFlinkCatalogTable.java`、`TestFlinkFilters.java`、`TestFlinkTableSink.java`、`TestFlinkIcebergSink.java` 等大量测试文件的适配更新，将测试中的 TableSchema 使用改为 ResolvedSchema/Schema。

## 总结

本提交是一个大型 API 迁移提交，将 Iceberg Flink 模块从已弃用的 `TableSchema` 全面迁移到 Flink 新的 `Schema`/`ResolvedSchema` API。迁移涉及约 20 个源文件和大量测试文件，覆盖 Catalog、Source、Sink、Schema 转换等所有 Flink 集成组件。旧 API 方法被保留但标记为 `@Deprecated`（计划在 2.0.0 移除），确保向后兼容。这是 Iceberg Flink 集成现代化的重要一步，确保与 Flink API 演进保持同步。
