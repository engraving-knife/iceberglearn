# 提交 0561：Flink 1.17 回迁——支持在建表与 ADD COLUMN 语法中为字段指定 COMMENT

## 提交信息

- **序号**：0561 / 4088
- **哈希**：1ae13b8bf70c51968d13d6aecdd1b4b69f7e12a0
- **短哈希**：1ae13b8bf
- **日期**：2024-03-05（AuthorDate 2024-03-05 23:32:50 +0800）
- **作者**：big face cat <731030576@qq.com>（Co-authored-by: huyuanfeng <huyuanfeng@huya.com>）
- **提交说明**：Flink:backport PR to 1.17 #9606 : Supports specifying comment for iceberg fields in create table and addcolumn syntax using flinksql (#9868)
- **PR/Issue**：#9868。本提交是 PR #9606（提交 `05f99b658`，标题"Flink: Supports specifying comment for iceberg fields in create table and addcolumn syntax using flinksql"）的 Flink 1.17 回迁版本，二者代码改动等价，仅目标目录由 `flink/v1.18` 改为 `flink/v1.17`。若按本仓库 tracking 顺序，原始 PR #9606 对应的序号应为 0560，本提交 0561 即为其 1.17 回port。

## 总体目的

本提交要为 Iceberg 的 **Flink 1.17 集成模块**补齐一项字段注释（COMMENT）能力：让用户在使用 Flink SQL 通过 Iceberg FlinkCatalog 建表（`CREATE TABLE`）或加列（`ALTER TABLE ... ADD COLUMN`）时，可以通过 Flink SQL 的 `COMMENT` 子句为字段附加注释，并将该注释真实写入 Iceberg Schema 的字段元数据中，使其在后续读取时也能被还原出来。

**背景动机**：

- Flink SQL 本身支持 `CREATE TABLE tl (id BIGINT COMMENT 'comment - id', ...)` 与 `ALTER TABLE tl ADD (col1 STRING COMMENT 'comment for col1')` 这样的 COMMENT 语法，但 Iceberg 的 FlinkCatalog 在 1.4.x 之前的实现中并未把字段注释传递给 Iceberg Schema，导致注释信息在建表时被丢弃。
- Iceberg Schema 的 `Types.NestedField` 原生支持 `doc` 字段（即字段文档/注释），因此具备承载注释的数据结构，只是 FlinkCatalog 的转换链路没有把 Flink 侧的 comment 透传过来。
- PR #9606（主分支，针对 `flink/v1.18`）已经修复了这个问题；由于 Iceberg 1.4.x 仍维护 `flink/v1.17` 这条独立的 Flink 集成分支，需要把这个能力以同样的方式回迁到 1.17 模块，保证两个 Flink 版本行为一致。

## 如何达成设计目的

设计思路是**改造 Flink Catalog 的 Schema 转换链路，使其读取并保留字段注释**，并在 `ADD COLUMN` 时把注释透传给 Iceberg 的 `SchemaUpdate`。核心实现路径如下：

1. **改用 `ResolvedCatalogTable` / `ResolvedSchema` 而非未解析的 `CatalogBaseTable` / `TableSchema`**。Flink 的字段注释存储在 `Column.getComment()` 上，而 `Column` 只有在 `ResolvedSchema`（已解析 schema）中才能直接拿到。因此把 `FlinkCatalog.createIcebergTable` 与 `FlinkDynamicTableFactory.createTableLoader` 的入参类型从 `CatalogBaseTable` 收窄为 `ResolvedCatalogTable`，并要求调用方传入已解析的表（`Preconditions.checkArgument(table instanceof ResolvedCatalogTable, "table should be resolved")`）。这是整个改动的关键前提——只有在"已解析"形态下，每个字段才是 `Column` 对象，注释才能被访问到。
2. **在 `FlinkSchemaUtil` 中新增 `convert(ResolvedSchema)` 重载**，遍历 `ResolvedSchema.getColumns()`，对每个 `Column` 读取其 `getComment()`，若有注释则用 `DataTypes.FIELD(name, dataType, comment)` 构造带注释的 Field，再汇总成 `RowType` 走原有的 `FlinkTypeToType` 转换。由于带注释的 Field 在 Flink 内部会被 `DataTypes.ROW` 转换成 `RowType` 的字段，且 `RowType` 字段自带 `description` 属性，最终 Iceberg 的 `FlinkTypeToType` 访问器会把该 description 映射到 `Types.NestedField.doc`，从而完成注释从 Flink → Iceberg 的传递。
3. **保留主键（identifier field）处理逻辑**，把原先依赖 `TableSchema.getPrimaryKey()` 的 `freshIdentifierFieldIds(Schema, TableSchema)` 改造为接受 `List<String> primaryKeys` 的版本，使其同时服务于旧的 `convert(TableSchema)` 与新的 `convert(ResolvedSchema)` 两条路径，避免代码重复。
4. **在 `FlinkAlterTableUtil` 中透传 ADD COLUMN 的注释**：对 `TableChange.AddColumn`，从 `flinkColumn.getComment()` 取注释（`orElse(null)`），传入 `pendingUpdate.addColumn(name, type, comment)` / `addRequiredColumn(name, type, comment)`，使加列时注释同样能落到 Iceberg Schema。
5. **新增测试用例** `testCreateTableWithColumnComment` 验证建表带 COMMENT，并改造既有 `ALTER TABLE ADD` 测试验证加列带 COMMENT。

由于 `flink/v1.17` 与 `flink/v1.18` 在本提交涉及的类签名上完全一致，回迁版本与原始 PR #9606 的 diff 几乎逐行相同（仅目录前缀 `flink/v1.17` vs `flink/v1.18` 不同，以及 v1.18 版本多一处空行差异）。

## 修改详情

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/FlinkCatalog.java`

**修改目的**：把建表入口从接受未解析的 `CatalogBaseTable` 改为接受已解析的 `ResolvedCatalogTable`，以便通过 `getResolvedSchema()` 拿到带注释的 `Column` 列表。

**工作逻辑**：

- 新增 import `org.apache.flink.table.catalog.ResolvedCatalogTable`。
- 在 `createTable` 中把 `createIcebergTable(tablePath, table, ignoreIfExists)` 改为 `Preconditions.checkArgument(table instanceof ResolvedCatalogTable, "table should be resolved")` 后再 `createIcebergTable(tablePath, (ResolvedCatalogTable) table, ignoreIfExists)`。这是一道前置断言，确保进入 Iceberg 建表流程的表对象一定是已解析形态，否则字段注释无从获取。
- 把 `createIcebergTable` 的签名从 `void createIcebergTable(ObjectPath, CatalogBaseTable, boolean)` 改为 `void createIcebergTable(ObjectPath, ResolvedCatalogTable, boolean)`。
- 方法体内把 `FlinkSchemaUtil.convert(table.getSchema())` 改为 `FlinkSchemaUtil.convert(table.getResolvedSchema())`，从而走新的带注释转换路径。其余 `getPartitionKeys()`、`getOptions()` 等调用因 `ResolvedCatalogTable` 同样支持而无需改动。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/FlinkDynamicTableFactory.java`

**修改目的**：让动态表源/汇工厂在通过非 catalog 路径（即用户用 `'connector'='iceberg'` 在非 Iceberg catalog 中建表）创建表时，同样传入 `ResolvedCatalogTable`，保持类型一致并让注释透传。

**工作逻辑**：

- 调整 import：移除 `CatalogBaseTable`、`CatalogTable`，新增 `ResolvedCatalogTable`。
- `createDynamicTableSource` 与 `createDynamicTableSink` 中把 `CatalogTable catalogTable = context.getCatalogTable()` 改为 `ResolvedCatalogTable resolvedCatalogTable = context.getCatalogTable()`（Flink 1.17 的 `Context.getCatalogTable()` 返回值本就是 `ResolvedCatalogTable`，这里只是把变量类型对齐到实际类型）。
- 后续 `getOptions()`、`getSchema()`（经 `TableSchemaUtils.getPhysicalSchema`）等调用改用 `resolvedCatalogTable`。
- `createTableLoader` 的入参类型从 `CatalogBaseTable` 改为 `ResolvedCatalogTable`，对应把内部调用 `flinkCatalog.createIcebergTable(objectPath, catalogBaseTable, true)` 改为 `flinkCatalog.createIcebergTable(objectPath, resolvedCatalogTable, true)`。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/FlinkSchemaUtil.java`

**修改目的**：核心改造——新增 `convert(ResolvedSchema)` 重载以在转换过程中保留字段注释；并把 identifier field 处理逻辑重构为可复用的 `freshIdentifierFieldIds(Schema, List<String>)`。

**工作逻辑**：

1. **旧 `convert(TableSchema)` 标记 `@Deprecated`**，仍保留以兼容旧调用方。其内部主键处理改为：若 `schema.getPrimaryKey().isPresent()` 则调用新的 `freshIdentifierFieldIds(icebergSchema, schema.getPrimaryKey().get().getColumns())`，否则直接返回 `icebergSchema`（不再无条件调用）。
2. **新增 `convert(ResolvedSchema flinkSchema)`**：
   - 通过 `flinkSchema.getColumns()` 拿到 `List<Column>`。
   - 遍历每个 `Column`，若 `column.getComment().isPresent()` 则用 `DataTypes.FIELD(name, dataType, comment)` 构造带注释字段，否则用 `DataTypes.FIELD(name, dataType)`。这段逻辑注释说明"copy from `org.apache.flink.table.api.Schema#toRowDataType`"，即模仿 Flink 内部把 `Column` 还原为 `RowDataType` 的方式，从而保留注释信息。
   - 用 `DataTypes.ROW(fields).notNull().getLogicalType()` 得到 `RowType`，校验其为 `RowType`，再走 `FlinkTypeToType` 转换成 Iceberg `Type`，最后构造 `Schema`。
   - 主键处理同样：若存在主键则 `freshIdentifierFieldIds(icebergSchema, flinkSchema.getPrimaryKey().get().getColumns())`，否则直接返回。
3. **`freshIdentifierFieldIds` 重构**：从 `freshIdentifierFieldIds(Schema, TableSchema)` 改为 `freshIdentifierFieldIds(Schema, List<String> primaryKeys)`。新版本遍历 `primaryKeys`，对每个主键列名在 schema 中 `findField`，收集 `fieldId` 装入 `identifierFieldIds` 集合，最后用 `new Schema(schemaId, asStruct().fields(), identifierFieldIds)` 构造带 identifier 的 Schema。把入参从 `TableSchema` 解耦成 `List<String>` 使其能同时被 `convert(TableSchema)` 与 `convert(ResolvedSchema)` 复用，消除重复。
4. **`toFlinkSchema` 反向转换路径**：原先末尾无条件调用 `freshIdentifierFieldIds(fixedSchema, flinkSchema)`，改为先判断 `flinkSchema.getPrimaryKey().isPresent()`：有主键才调用 `freshIdentifierFieldIds(fixedSchema, flinkSchema.getPrimaryKey().get().getColumns())`，无主键直接返回 `fixedSchema`。这与新 `convert` 的处理保持一致，避免在无主键时多余构造。

注释传递的本质机制：Flink `DataTypes.FIELD(name, dataType, comment)` 会在生成的 `RowType` 字段上设置 `description`，而 Iceberg 的 `FlinkTypeToType` 在访问 `RowType` 字段时会读取该 `description` 并写入 `Types.NestedField.doc`，因此注释最终落到 Iceberg Schema 的字段文档上。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/util/FlinkAlterTableUtil.java`

**修改目的**：让 `ALTER TABLE ... ADD COLUMN` 语法在加列时把字段注释透传给 Iceberg 的 `SchemaUpdate`。

**工作逻辑**：在 `TableChange.AddColumn` 分支中，原本只传 `(name, type)`：

```java
pendingUpdate.addColumn(flinkColumn.getName(), icebergType);
pendingUpdate.addRequiredColumn(flinkColumn.getName(), icebergType);
```

改为额外传入从 `flinkColumn.getComment().orElse(null)` 取得的注释：

```java
pendingUpdate.addColumn(
    flinkColumn.getName(), icebergType, flinkColumn.getComment().orElse(null));
pendingUpdate.addRequiredColumn(
    flinkColumn.getName(), icebergType, flinkColumn.getComment().orElse(null));
```

`addColumn`/`addRequiredColumn` 的三参重载是 Iceberg `SchemaUpdate` 已有的 API（第三参为 `doc`），当 `comment` 为 null 时等价于不带注释，兼容无 COMMENT 的旧用法。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/TestFlinkCatalogTable.java`

**修改目的**：验证建表与加列两种场景下注释都能正确写入 Iceberg Schema。

**工作逻辑**：

- 新增 `testCreateTableWithColumnComment`：执行 `CREATE TABLE tl(id BIGINT COMMENT 'comment - id', data STRING COMMENT 'comment - data')`，断言 `table.schema().asStruct()` 等于期望的 Schema，其中两个字段分别带 `doc = "comment - id"` 与 `doc = "comment - data"`（通过 `Types.NestedField.optional(id, name, type, doc)` 构造）。
- 改造既有加列测试：把 `ALTER TABLE tl ADD (col1 STRING, col2 BIGINT)` 改为 `ALTER TABLE tl ADD (col1 STRING COMMENT 'comment for col1', col2 BIGINT)`，断言中 `col1` 字段构造加上 `"comment for col1"` 作为 doc，验证加列注释透传。

## 小结

本提交为 Iceberg 1.4.x 的 Flink 1.17 集成模块补齐了字段 COMMENT 能力，使其与主分支（PR #9606，flink/v1.18）行为一致。改动涵盖 5 个文件、+95/-38 行，核心是：

1. 把建表/动态表工厂的表对象类型收窄为 `ResolvedCatalogTable`，强制使用已解析 schema；
2. 在 `FlinkSchemaUtil` 新增 `convert(ResolvedSchema)` 重载，通过 `Column.getComment()` + `DataTypes.FIELD(..., comment)` 把注释注入 `RowType` 字段 description，再经 `FlinkTypeToType` 落到 `Types.NestedField.doc`；
3. 重构 `freshIdentifierFieldIds` 为基于 `List<String>` 的可复用方法；
4. 在 `FlinkAlterTableUtil` 透传 ADD COLUMN 注释到 `SchemaUpdate.addColumn/addRequiredColumn` 的 doc 参数。

**回迁到 1.4.x 的注意事项**：

- 该提交本身就是回迁版本（到 flink/v1.17），1.4.x 若同时维护 v1.17 与 v1.18 两个 Flink 模块，需确认两条分支都已应用对应改动（本提交覆盖 v1.17，原始 PR #9606 覆盖 v1.18）。
- 改动把 `createIcebergTable` 的入参类型从 `CatalogBaseTable` 改为 `ResolvedCatalogTable`，属于方法签名变更，外部若有自定义子类覆盖该方法需同步调整。
- 新增了 `Preconditions.checkArgument(table instanceof ResolvedCatalogTable, ...)` 断言，调用方必须传入已解析的表对象，否则会抛 `IllegalArgumentException`，这是一项行为收紧。
- 旧 `convert(TableSchema)` 仅被标记 `@Deprecated` 而未删除，保持向后兼容。
