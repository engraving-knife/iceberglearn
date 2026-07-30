# 提交 0560：Flink: Supports specifying comment for iceberg fields in create table and addcolumn syntax using flinksql

## 提交信息

- **序号**：0560 / 4088
- **哈希**：05f99b658189e277bbf32a814ee489cf59add3fa
- **短哈希**：05f99b658
- **日期**：2024-03-05 10:29:50 +0800
- **作者**：big face cat <731030576@qq.com>
- **提交说明**：Flink: Supports specifying comment for iceberg fields in create table and addcolumn syntax using flinksql (#9606)
- **PR/Issue**：#9606
- **共同作者**：huyuanfeng <huyuanfeng@huya.com>

## 总体目的

让 Flink SQL 的 `CREATE TABLE` 与 `ALTER TABLE ... ADD COLUMN` 语法中为列指定 `COMMENT '...'` 时，该注释能正确写入 Iceberg 表 schema（作为 `Types.NestedField.doc` 持久化），而不是在转换过程中被丢弃。

此前 Flink Iceberg 集成存在两个问题：

1. **建表丢注释**：`FlinkCatalog.createIcebergTable` 内部调用 `FlinkSchemaUtil.convert(table.getSchema())`，而 `convert(TableSchema)` 实现走 `schema.toRowDataType().getLogicalType()`。`TableSchema.toRowDataType()` 在转换时不会保留列上的 `comment`，导致建表时 SQL 中写的 `COMMENT '...'` 被丢弃。
2. **加列丢注释**：`FlinkAlterTableUtil.applySchemaChanges` 在处理 `TableChange.AddColumn` 时，调用 `pendingUpdate.addColumn(name, type)` / `addRequiredColumn(name, type)`，没把 `Column.getComment()` 传给 Iceberg 的 `UpdateSchema`，导致 `ALTER TABLE ADD COLUMN` 时注释也被丢弃。

本提交通过切换到 Flink 较新的 `ResolvedSchema`/`ResolvedCatalogTable` API（这些 API 暴露了带注释的 `Column` 对象），并在类型转换与 schema 更新时显式传递注释，让 Flink SQL 注释能完整流到 Iceberg schema 中。

## 如何达成设计目的

整体设计思路是"切换到 ResolvedSchema API 以保留列注释 + 在所有丢注释的转换点显式传递注释"。具体路径：

1. **新增 `FlinkSchemaUtil.convert(ResolvedSchema)` 重载**：手动遍历 `ResolvedSchema.getColumns()`，对每个 `Column` 用 `DataTypes.FIELD(name, dataType, comment)` 构造带注释的 `Field`，再拼成 `DataTypes.ROW(...)` 转出 `LogicalType`，这样注释信息会随 `FlinkTypeToType` 转换进入 Iceberg `Types.NestedField.doc`。
2. **废弃旧 `convert(TableSchema)`**：标记 `@Deprecated`，但保留以兼容旧调用方。内部逻辑也重构为复用新的 `freshIdentifierFieldIds(Schema, List<String>)` 签名。
3. **重构 `freshIdentifierFieldIds`**：参数从 `Schema iSchema, TableSchema schema` 改为 `Schema icebergSchema, List<String> primaryKeys`，使其与 `TableSchema` 解耦，新旧两个 `convert` 方法都能复用。
4. **`FlinkCatalog.createIcebergTable` 切换到 `ResolvedCatalogTable`**：要求入参是 `ResolvedCatalogTable`，调用 `table.getResolvedSchema()` 走新的 `convert(ResolvedSchema)`，从而在建表路径保留注释。
5. **`FlinkDynamicTableFactory` 全面切换到 `ResolvedCatalogTable`**：源表/汇表创建路径以及 `createTableLoader` 都改用 `ResolvedCatalogTable`，与 `FlinkCatalog` 入参类型对齐。
6. **`FlinkAlterTableUtil.applySchemaChanges` 传递注释**：在 `TableChange.AddColumn` 分支中，调用 `pendingUpdate.addColumn(name, type, comment)` / `addRequiredColumn(name, type, comment)`，comment 取自 `flinkColumn.getComment().orElse(null)`，让加列路径也保留注释。
7. **测试覆盖**：新增 `testCreateTableWithColumnComment` 验证建表注释；改造既有 `ALTER TABLE ADD` 测试，在 `col1` 上加 `COMMENT 'comment for col1'` 并断言 schema 中 `col1` 的 doc 等于该注释。

## 修改详情

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/FlinkSchemaUtil.java`

**修改目的**：新增 `convert(ResolvedSchema)` 重载以保留列注释，重构 `freshIdentifierFieldIds` 与 `convert(Schema, TableSchema)` 复用新签名，废弃旧 `convert(TableSchema)`。

**工作逻辑**：

新增的 `convert(ResolvedSchema flinkSchema)` 方法核心实现：

```java
public static Schema convert(ResolvedSchema flinkSchema) {
  List<Column> tableColumns = flinkSchema.getColumns();
  // copy from org.apache.flink.table.api.Schema#toRowDataType
  DataTypes.Field[] fields =
      tableColumns.stream()
          .map(
              column -> {
                if (column.getComment().isPresent()) {
                  return DataTypes.FIELD(
                      column.getName(), column.getDataType(), column.getComment().get());
                } else {
                  return DataTypes.FIELD(column.getName(), column.getDataType());
                }
              })
          .toArray(DataTypes.Field[]::new);

  LogicalType schemaType = DataTypes.ROW(fields).notNull().getLogicalType();
  Preconditions.checkArgument(
      schemaType instanceof RowType, "Schema logical type should be row type.");

  RowType root = (RowType) schemaType;
  Type converted = root.accept(new FlinkTypeToType(root));
  Schema icebergSchema = new Schema(converted.asStructType().fields());
  if (flinkSchema.getPrimaryKey().isPresent()) {
    return freshIdentifierFieldIds(icebergSchema, flinkSchema.getPrimaryKey().get().getColumns());
  } else {
    return icebergSchema;
  }
}
```

关键点：

1. **手动构造 `DataTypes.FIELD`**：Flink 的 `DataTypes.FIELD(name, dataType, comment)` 三参重载会把 comment 作为字段元数据保留在 `LogicalType` 内（最终落在 `RowType.RowField.getDescription()`）。这一步是注释能流到 Iceberg 的关键——旧的 `TableSchema.toRowDataType()` 不会做这件事。
2. **`DataTypes.ROW(fields).notNull()`**：用字段数组构造行类型，`.notNull()` 与 Flink 内部 `Schema#toRowDataType` 行为对齐（注释里写明"copy from org.apache.flink.table.api.Schema#toRowDataType"）。
3. **`root.accept(new FlinkTypeToType(root))`**：复用既有 Flink→Iceberg 类型转换器。`FlinkTypeToType` 在访问 `RowField` 时会读取其 description 并写入 `Types.NestedField.doc`，从而完成注释从 Flink 到 Iceberg 的传递。
4. **主键处理**：调用重构后的 `freshIdentifierFieldIds(icebergSchema, primaryKeys)` 设置 Iceberg 的 identifier field ids。

旧 `convert(TableSchema)` 改动：

```java
@Deprecated
public static Schema convert(TableSchema schema) {
  LogicalType schemaType = schema.toRowDataType().getLogicalType();
  Preconditions.checkArgument(
      schemaType instanceof RowType, "Schema logical type should be row type.");

  RowType root = (RowType) schemaType;
  Type converted = root.accept(new FlinkTypeToType(root));

  Schema icebergSchema = new Schema(converted.asStructType().fields());
  if (schema.getPrimaryKey().isPresent()) {
    return freshIdentifierFieldIds(icebergSchema, schema.getPrimaryKey().get().getColumns());
  } else {
    return icebergSchema;
  }
}
```

改动：加 `@Deprecated` 注解；变量名从 `iSchema` 改为 `icebergSchema`；错误消息文案"RowType"改为"row type"（与新版一致）；主键处理逻辑外提为对 `freshIdentifierFieldIds` 的条件调用，与新重载保持一致。

`freshIdentifierFieldIds` 重构：

```java
private static Schema freshIdentifierFieldIds(Schema icebergSchema, List<String> primaryKeys) {
  Set<Integer> identifierFieldIds = Sets.newHashSet();
  for (String primaryKey : primaryKeys) {
    Types.NestedField field = icebergSchema.findField(primaryKey);
    Preconditions.checkNotNull(
        field,
        "Cannot find field ID for the primary key column %s in schema %s",
        primaryKey,
        icebergSchema);
    identifierFieldIds.add(field.fieldId());
  }
  return new Schema(
      icebergSchema.schemaId(), icebergSchema.asStruct().fields(), identifierFieldIds);
}
```

改动：参数从 `(Schema iSchema, TableSchema schema)` 改为 `(Schema icebergSchema, List<String> primaryKeys)`。原来方法内部判断 `schema.getPrimaryKey().isPresent()` 并遍历，现在外部判断后直接传 `List<String> primaryKeys`，方法只负责把主键列名解析为 field id 并构造带 identifier field ids 的 Schema。这样两个 `convert` 方法（一个用 `TableSchema.getPrimaryKey()`，一个用 `ResolvedSchema.getPrimaryKey()`）都能复用此方法。

`convert(Schema baseSchema, TableSchema flinkSchema)` 改动：方法尾部原本直接 `return freshIdentifierFieldIds(fixedSchema, flinkSchema);`，现在改为：

```java
if (flinkSchema.getPrimaryKey().isPresent()) {
  return freshIdentifierFieldIds(fixedSchema, flinkSchema.getPrimaryKey().get().getColumns());
} else {
  return fixedSchema;
}
```

与新签名配合，避免在无主键时也走 identifier field ids 设置逻辑。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/FlinkCatalog.java`

**修改目的**：让建表路径走 `ResolvedCatalogTable` 与新的 `convert(ResolvedSchema)`，保留列注释。

**工作逻辑**：

`createTable` 方法改动：

```java
Preconditions.checkArgument(table instanceof ResolvedCatalogTable, "table should be resolved");
createIcebergTable(tablePath, (ResolvedCatalogTable) table, ignoreIfExists);
```

新增 import `org.apache.flink.table.catalog.ResolvedCatalogTable`，并要求上层（Flink Planner）传入的 `CatalogBaseTable` 实际类型必须是 `ResolvedCatalogTable`，否则抛 `IllegalArgumentException`。Flink 在执行 CREATE TABLE 时会先把 unresolved 的 catalog table 经 planner 解析为 resolved 形式，因此这一断言在正常流程下总是成立。

`createIcebergTable` 方法签名与实现改动：

```java
void createIcebergTable(ObjectPath tablePath, ResolvedCatalogTable table, boolean ignoreIfExists)
    throws CatalogException, TableAlreadyExistException {
  validateFlinkTable(table);

  Schema icebergSchema = FlinkSchemaUtil.convert(table.getResolvedSchema());
  PartitionSpec spec = toPartitionSpec(((CatalogTable) table).getPartitionKeys(), icebergSchema);
  // ...
}
```

关键变化：

1. 入参类型从 `CatalogBaseTable` 改为 `ResolvedCatalogTable`，调用 `table.getResolvedSchema()` 拿到 `ResolvedSchema`（带注释的列信息）。
2. 调用 `FlinkSchemaUtil.convert(table.getResolvedSchema())` 走新重载，注释得以保留。
3. `PartitionSpec` 那行去掉了原本的尾部空行，是次要风格调整。

由于 `ResolvedCatalogTable` 实现了 `CatalogTable`，所以 `((CatalogTable) table).getPartitionKeys()` 仍可正常工作。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/FlinkDynamicTableFactory.java`

**修改目的**：把 source/sink 动态表工厂中所有 `CatalogTable` 引用切换为 `ResolvedCatalogTable`，与 `FlinkCatalog.createIcebergTable` 入参类型对齐，保证通过 factory 路径自动建表时也能携带注释。

**工作逻辑**：

import 调整：移除 `CatalogBaseTable`、`CatalogTable`，新增 `ResolvedCatalogTable`。

`createDynamicTableSource` 改动：

```java
ResolvedCatalogTable resolvedCatalogTable = context.getCatalogTable();
Map<String, String> tableProps = resolvedCatalogTable.getOptions();
TableSchema tableSchema = TableSchemaUtils.getPhysicalSchema(resolvedCatalogTable.getSchema());
// ...
tableLoader = createTableLoader(resolvedCatalogTable, tableProps, ...);
```

`createDynamicTableSink` 同样改动。`createTableLoader` 签名从 `(CatalogBaseTable, ...)` 改为 `(ResolvedCatalogTable, ...)`，内部调用 `flinkCatalog.createIcebergTable(objectPath, resolvedCatalogTable, true)` 与新签名对齐。

`context.getCatalogTable()` 在 Flink 1.18 的 `DynamicTableFactory.Context` 接口中返回类型就是 `ResolvedCatalogTable`，所以这个改动只是把变量声明类型对齐到接口实际返回类型，消除不必要的向下转型。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/util/FlinkAlterTableUtil.java`

**修改目的**：让 `ALTER TABLE ADD COLUMN` 路径把列注释传给 Iceberg `UpdateSchema`。

**工作逻辑**：

`applySchemaChanges` 方法中 `TableChange.AddColumn` 分支改动：

```java
Type icebergType = FlinkSchemaUtil.convert(flinkColumn.getDataType().getLogicalType());
if (flinkColumn.getDataType().getLogicalType().isNullable()) {
  pendingUpdate.addColumn(
      flinkColumn.getName(), icebergType, flinkColumn.getComment().orElse(null));
} else {
  pendingUpdate.addRequiredColumn(
      flinkColumn.getName(), icebergType, flinkColumn.getComment().orElse(null));
}
```

关键变化：在 `pendingUpdate.addColumn` 与 `pendingUpdate.addRequiredColumn` 调用中追加第三个参数 `flinkColumn.getComment().orElse(null)`。Iceberg 的 `UpdateSchema.addColumn(String name, Type type, String doc)` 与 `addRequiredColumn(String name, Type type, String doc)` 重载会把 doc 设置到对应 `Types.NestedField.doc`，从而把注释持久化到 schema。

`flinkColumn` 的类型是 `org.apache.flink.table.catalog.Column`，其 `getComment()` 返回 `Optional<String>`，所以用 `.orElse(null)` 在无注释时传 null（Iceberg 端会把 null doc 视为无注释，与旧行为一致）。

`TableChange.AddColumn.getColumn()` 返回的 `Column` 在 Flink 1.18 中是 resolved column（携带注释），所以这一改动在 `ALTER TABLE ADD COLUMN ... COMMENT '...'` 流程下能拿到注释。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/TestFlinkCatalogTable.java`

**修改目的**：验证建表与加列路径都能正确保留列注释。

**工作逻辑**：

新增测试 `testCreateTableWithColumnComment`：

```java
@TestTemplate
public void testCreateTableWithColumnComment() {
  sql("CREATE TABLE tl(id BIGINT COMMENT 'comment - id', data STRING COMMENT 'comment - data')");

  Table table = table("tl");
  assertThat(table.schema().asStruct())
      .isEqualTo(
          new Schema(
              Types.NestedField.optional(1, "id", Types.LongType.get(), "comment - id"),
              Types.NestedField.optional(2, "data", Types.StringType.get(), "comment - data"))
              .asStruct());
}
```

通过 `COMMENT '...'` 子句建表，然后读回 Iceberg `Table.schema()`，断言每个 `Types.NestedField` 的 doc 等于注释字符串。`Types.NestedField.optional(id, name, type, doc)` 四参构造就是带 doc 的形式，验证 doc 被正确写入。

改造既有加列测试：

```java
sql("ALTER TABLE tl ADD (col1 STRING COMMENT 'comment for col1', col2 BIGINT)");
// ...
Types.NestedField.optional(3, "col1", Types.StringType.get(), "comment for col1"),
```

原本 `col1` 没注释，现在加上 `COMMENT 'comment for col1'` 并在断言 schema 中期望 `col1` 的 doc 为该字符串。`col2` 仍无注释，验证混合场景（部分列有注释、部分没有）。

## 小结

- 本提交是 Flink Iceberg 集成的功能补全，5 个文件 95 增 39 删，让 Flink SQL 的 `COMMENT` 子句在 `CREATE TABLE` 与 `ALTER TABLE ADD COLUMN` 两条路径上都能正确流到 Iceberg schema 的 `doc` 字段。
- 影响范围：仅 Flink 1.18 集成模块。用户在 Flink SQL 中为列写注释后，注释会持久化到 Iceberg 表元数据中，可被其他引擎（Spark、Trino 等）读取，提升跨引擎的列文档可发现性。
- 设计上的取舍：
  1. 切换到 `ResolvedSchema`/`ResolvedCatalogTable` 是 Flink 推荐的较新 API，能保留列元数据；旧 `TableSchema` API 被标记 `@Deprecated` 但保留兼容。
  2. `freshIdentifierFieldIds` 重构为接受 `List<String> primaryKeys` 而非 `TableSchema`，实现新旧 `convert` 方法共享逻辑，避免代码重复。
  3. 在 `FlinkCatalog.createTable` 加 `Preconditions.checkArgument(table instanceof ResolvedCatalogTable, ...)` 显式断言，早失败而非 ClassCastException。
- 回迁到 1.4.x 的注意事项：
  1. 1.4.x 若已升级到 Flink 1.18 集成模块，可直接套用此 patch；若仍维护 Flink 1.17/1.16/1.15 模块，需确认这些版本的 `DynamicTableFactory.Context.getCatalogTable()` 是否已返回 `ResolvedCatalogTable`，以及 `ResolvedSchema`/`Column.getComment()` API 是否可用（Flink 1.15+ 已具备）。
  2. `FlinkSchemaUtil.convert(ResolvedSchema)` 中手动构造 `DataTypes.FIELD` 的写法依赖 `DataTypes.FIELD(String, DataType, String)` 三参重载，需确认目标 Flink 版本有此 API。
  3. `UpdateSchema.addColumn(name, type, doc)` 与 `addRequiredColumn(name, type, doc)` 三参重载在 Iceberg 1.4.x 中已存在，无兼容问题。
  4. 该改动不改变无注释场景下的行为（comment 为 null 时与旧逻辑等价），回迁风险低，但建议回迁后跑一遍 `TestFlinkCatalogTable` 验证。
