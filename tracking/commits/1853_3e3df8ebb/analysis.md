# 提交 1853：Core: Fix default and initial value handling on table creation (#12520)

## 提交信息

- **序号**：1853 / 4088
- **哈希**：3e3df8ebb4e6f11727e731809149d2a3c66726be
- **短哈希**：3e3df8ebb
- **日期**：2025-03-14 14:20:19 +0100
- **作者**：pvary
- **提交说明**：Core: Fix default and initial value handling on table creation (#12520)
- **PR/Issue**：#12520

## 总体目的

Iceberg v3 spec 引入了列级默认值（`initial-default` 与 `write-default`），允许在 schema 字段上声明初始默认值和写入默认值。`Types.NestedField` 因此新增了 `initialDefaultLiteral`、`writeDefaultLiteral` 字段，并提供了 builder 风格的 `Types.NestedField.from(field).withId(...).ofType(...).withDoc(...).withInitialDefault(...).withWriteDefault(...).build()` 来构造字段（保留所有属性）。

但在 schema 处理工具类 `AssignFreshIds`、`AssignIds`、`ReassignDoc` 中，重建 `NestedField` 时走的是老式分支：

```java
if (field.isOptional()) {
  newFields.add(Types.NestedField.optional(newIds.get(i), field.name(), type, field.doc()));
} else {
  newFields.add(Types.NestedField.required(newIds.get(i), field.name(), type, field.doc()));
}
```

`Types.NestedField.optional(id, name, type, doc)` 和 `required(id, name, type, doc)` 这两个工厂方法只接受 id/name/type/doc 四个参数，**不会携带 `initialDefault` 与 `writeDefault`**。结果建表时如果触发了 ID 重分配（`assignFreshIds`/`assignIds`）或文档重赋值（`reassignDoc`），列的默认值会被静默丢弃。这在 v3 表上是一个数据正确性 bug——用户声明的 `initial-default`/`write-default` 在建表后消失。

本提交把三处重建逻辑统一改为 `Types.NestedField.from(field).withId(...).ofType(...).build()`（或 `.withDoc(...)`），借助 `from(field)` 拷贝原字段的所有属性（含默认值），再覆盖需要变更的属性（id/type/doc），从而完整保留 `initialDefault`/`writeDefault`。

## 如何达成设计目的

核心思路是放弃手工按 optional/required 分支调用工厂方法，改用 `Types.NestedField.from(field)` 这个 builder 入口——它会拷贝原 `NestedField` 的全部状态（optional/required、id、name、type、doc、initialDefault、writeDefault），再通过链式 `.withId(...)`/`.ofType(...)`/`.withDoc(...)` 覆盖需要变更的属性。这样无论后续给 `NestedField` 增加什么新属性，这三个 schema 处理 visitor 都能自动保留，避免同类 bug 反复出现。

同时新增测试：
- `TestTypeUtil` 新增 `testAssignIds`、`testAssignFreshIds`、`testReassignDoc` 三个单测，构造带 `initialDefault`/`writeDefault` 的字段，跑对应工具方法，断言默认值被保留。
- `CatalogTests` 新增 `testCreateTableWithDefaultColumnValue`，端到端验证通过 catalog 建表后 schema 的默认值仍存在。
- Spark 3.4/3.5 的 `ScanTestBase` 在建表时检测 schema 是否含默认值，若有则把 `format-version` 设为 3（默认值是 v3 特性），避免在 v2 表上误用 v3 特性。

## 修改详情

### `api/src/main/java/org/apache/iceberg/types/AssignFreshIds.java` (修改, +1/-5 lines)

**修改目的**：建表时分配全新字段 ID 的 visitor，修复丢失列默认值。

**工作逻辑**：`struct` 方法中重建字段时，把 `if (field.isOptional()) optional(...) else required(...)` 分支替换为 `Types.NestedField.from(field).withId(newIds.get(i)).ofType(type).build()`。`from(field)` 拷贝原字段全部属性，`.withId(...)` 覆盖新 ID，`.ofType(type)` 覆盖类型（可能被 visitor 递归改写），`.build()` 产出保留默认值的新字段。

### `api/src/main/java/org/apache/iceberg/types/AssignIds.java` (修改, +1/-5 lines)

**修改目的**：按自定义映射分配字段 ID 的 visitor，同样修复丢失默认值。

**工作逻辑**：与 `AssignFreshIds` 完全相同的替换——`Types.NestedField.from(field).withId(newIds.get(i)).ofType(type).build()`。

### `api/src/main/java/org/apache/iceberg/types/ReassignDoc.java` (修改, +2/-7 lines)

**修改目的**：从另一个 schema 拷贝字段文档的 visitor，修复丢失默认值。

**工作逻辑**：`Types.NestedField.required(fieldId, field.name(), types.get(i), docField.doc())`/`optional(...)` 分支替换为 `Types.NestedField.from(field).ofType(types.get(i)).withDoc(docField.doc()).build()`。注意这里 `.withId(...)` 不需要——id 保持原样；`.ofType(...)` 覆盖类型；`.withDoc(docField.doc())` 用源 schema 的文档覆盖。

### `api/src/test/java/org/apache/iceberg/types/TestTypeUtil.java` (修改, +102 lines)

**修改目的**：为三个 visitor 新增默认值保留的单测。

**工作逻辑**：三个测试 `testAssignIds`/`testAssignFreshIds`/`testReassignDoc` 都构造含 `withInitialDefault(Literal.of(23)).withWriteDefault(Literal.of(34))` 的字段 `c`，跑对应工具方法后断言结果 schema 的字段 `c` 仍带 `initialDefault=23`、`writeDefault=34`。用 `Types.NestedField.required("c").withId(1).ofType(...).withInitialDefault(...).withWriteDefault(...).build()` 这种 builder 风格构造期望 schema，与 `required(id, name, type)` 工厂方法形成对照。

### `core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java` (修改, +33 lines)

**修改目的**：端到端验证 catalog 建表后列默认值保留。

**工作逻辑**：`testCreateTableWithDefaultColumnValue` 构造 `colWithDefault` 字段（`withWriteDefault(Literal.of(10)).withInitialDefault(Literal.of(12))`），用 `buildTable(...).withLocation(...).withProperty(FORMAT_VERSION, "3").create()` 建表（v3 表），断言 `catalog.loadTable(ident).schema().asStruct()` 与原 schema 一致（默认值未丢）。新增 `import org.apache.iceberg.expressions.Literal;`。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/ScanTestBase.java` (修改, +11/-2 lines)

**修改目的**：Spark 3.4 扫描测试基类建表时按需设置 v3 format-version。

**工作逻辑**：检测 `writeSchema.columns()` 中是否存在字段带 `initialDefaultLiteral != null` 或 `writeDefaultLiteral != null`；若有，建表时 `tableProperties = ImmutableMap.of(TableProperties.FORMAT_VERSION, "3")`，否则空 map。把 `tables.create(writeSchema, PartitionSpec.unpartitioned(), location.toString())` 改为 `tables.create(writeSchema, PartitionSpec.unpartitioned(), tableProperties, location.toString())`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/ScanTestBase.java` (修改, +11/-2 lines)

**修改目的**：与 3.4 相同的 v3 format-version 自适应。

**工作逻辑**：与 3.4 完全相同的改动。

## 小结

- **成效**：修复了 `AssignFreshIds`/`AssignIds`/`ReassignDoc` 三个 schema 处理 visitor 在重建字段时丢失 `initial-default`/`write-default` 的 bug，保证 v3 表建表后列默认值不丢失。改用 `NestedField.from(field)` builder 模式后，未来新增字段属性也能自动保留。
- **影响范围**：api 模块 3 个 visitor 类（+4/-17），测试 4 个文件（+146/-2）。属于数据正确性修复，影响所有建表路径（任何 catalog 建表都会经过 `assignFreshIds`）。
- **回迁到 1.4.x 的注意事项**：**强依赖 v3 spec 支持**。`initialDefault`/`writeDefault` 与 `NestedField.from(field)` builder、`withInitialDefault`/`withWriteDefault` 是 v3 spec 引入的 api。若 1.4.x 已支持 v3 表与这些 api，建议回迁（修复正确性 bug）；若 1.4.x 尚未引入 v3 默认值特性，则本提交无意义，不应回迁。需先确认 1.4.x 的 `Types.NestedField` 是否有 `from`/`withInitialDefault` 等方法。
