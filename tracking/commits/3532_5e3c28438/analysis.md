# 提交 3532：API: Relax partition name check when source column is dropped (#15967)

## 提交信息

- **序号**：3532 / 4088
- **哈希**：5e3c28438570f0d526616e44d1d41f57aa49f585
- **短哈希**：5e3c28438
- **日期**：2026-04-14 15:34:12 -0700
- **作者**：Russell Spitzer
- **提交说明**：API: Relax partition name check when source column is dropped (#15967)
- **PR/Issue**：#15967

## 总体目的

Iceberg 的分区规范（`PartitionSpec`）在构建时会校验分区字段名与 schema 字段名之间的冲突。对于 identity 转换的分区字段，规则是：如果 schema 中存在同名字段，那么该 schema 字段的 id 必须与分区字段的 sourceId 一致（即分区字段就是从该 schema 字段派生的）。

问题场景：用户先删除一个用作 identity 分区源的列（`DROP COLUMN category`），此时表的当前 schema 中不再有 `category` 列，但历史 partition spec 中仍保留指向已删除列 id 的分区字段记录（sourceId 指向一个已不存在的列）。随后用户想重新添加一个同名列（`ADD COLUMN category string`），这会给 `category` 分配一个新的 field id。但在重新构建/校验 partition spec 时，旧的分区字段 sourceId 指向的列已不存在，而 schema 中却有一个同名的 `category` 字段（但 id 不同），于是校验报错 "Cannot create identity partition sourced from different field in schema: category"，阻止用户重新添加同名列。

实际上这种「历史 spec 中 sourceId 指向的列已被删除」的情况是合法的历史遗留，不应阻止用户用同名列重建分区。本提交在 source 列已不存在时跳过 identity 名称配对校验，允许同名列重新添加。

## 如何达成设计目的

修改 `PartitionSpec` 构建时的校验逻辑：在 identity 分区字段校验中，先判断 `schema.findField(sourceColumnId) != null`（即 source 列在当前 schema 中是否存在）。如果 source 列已被删除（不存在），则跳过 "partition name 必须与 source column 匹配" 的校验，因为此时无法做有意义的配对。如果 source 列仍存在，则保持原有的严格校验。

同时对非 identity 转换的校验逻辑做了微调，把 `sourceColumnId == null` 的分支提前，结构更清晰。

## 修改详情

### `api/src/main/java/org/apache/iceberg/PartitionSpec.java` (+12/-11 lines)

**修改目的**：在 source 列被删除时跳过 identity 名称配对校验。

**工作逻辑**：
原逻辑：
```java
if (sourceColumnId != null) {
  // identity: 允许冲突但要求同源
  Preconditions.checkArgument(
      schemaField == null || schemaField.fieldId() == sourceColumnId, ...);
} else {
  // 非 identity: 不允许名字冲突
  Preconditions.checkArgument(schemaField == null, ...);
}
```
新逻辑：
```java
if (sourceColumnId == null) {
  // 非 identity: 不允许名字冲突
  Preconditions.checkArgument(schemaField == null, ...);
} else {
  boolean sourceFieldExists = schema.findField(sourceColumnId) != null;
  // identity: 仅当 source 列仍存在时才做名称配对校验
  if (sourceFieldExists) {
    Preconditions.checkArgument(
        schemaField == null || schemaField.fieldId() == sourceColumnId, ...);
  }
  // source 列已删除（历史 spec）→ 跳过校验
}
```
关键新增是 `boolean sourceFieldExists = schema.findField(sourceColumnId) != null;`，只有 source 列仍存在时才执行 identity 名称配对校验。

### `api/src/test/java/org/apache/iceberg/TestPartitionSpecValidation.java` (+16/-0 lines)

**修改目的**：API 层单元测试验证 source 列被删除后可重用同名列。

**工作逻辑**：
```java
@Test
public void testStalePartitionSourceIdWithReusedColumnName() {
  int newFieldId = 2;
  int droppedFieldId = 1;
  Schema schema =
      new Schema(NestedField.required(newFieldId, "category", Types.StringType.get()));
  PartitionSpec spec =
      PartitionSpec.builderFor(schema)
          .withSpecId(0)
          .add(droppedFieldId, 1000, "category", Transforms.alwaysNull())
          .build();
  assertThat(spec.fields()).hasSize(1);
  assertThat(spec.fields().get(0).sourceId()).isEqualTo(droppedFieldId);
  assertThat(spec.fields().get(0).name()).isEqualTo("category");
}
```
构造一个 schema，其中 `category` 列的 field id=2，但分区字段 sourceId=1（已删除的旧 id），分区名也是 `category`。修复前会抛异常，修复后构建成功。

### `spark/v4.1/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestAlterTablePartitionFields.java` (+13/-0 lines)

**修改目的**：Spark 扩展端到端测试验证「删除分区字段 → 删除列 → 重新添加同名列」流程。

**工作逻辑**：
```java
@TestTemplate
public void testReaddColumnAfterIdentityPartitionDrop() {
  createTable("id bigint NOT NULL, category string, data string", "category");

  sql("ALTER TABLE %s DROP PARTITION FIELD category", tableName);
  sql("ALTER TABLE %s DROP COLUMN category", tableName);
  sql("ALTER TABLE %s ADD COLUMN category string", tableName);

  sql("INSERT INTO %s (id, category, data) VALUES (1, 'books', 'a')", tableName);
  assertThat(sql("SELECT id, category, data FROM %s ORDER BY id", tableName))
      .containsExactly(row(1L, "books", "a"));
}
```
完整覆盖用户场景：建表（category 作为 identity 分区）→ 删除分区字段 → 删除列 → 重新添加同名列 → 插入查询验证。

## 总结

本提交修复了当 identity 分区的 source 列被删除后，用户无法重新添加同名列的问题。通过在 `PartitionSpec` 校验时判断 source 列是否仍存在于 schema，若已删除则跳过 identity 名称配对校验，允许历史 spec 与同名列共存。改动配有 API 单元测试和 Spark 扩展端到端测试，覆盖了完整的「删分区→删列→加同名列」用户流程。
