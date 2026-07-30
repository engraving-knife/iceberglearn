# 提交 1727：API, Core: Support default values in UpdateSchema (#12211)

## 提交信息

- **序号**：1727 / 4088
- **哈希**：602c35a31833f8448734781d71d949ec59abe45b
- **短哈希**：602c35a31
- **日期**：2025-02-13 11:37:14 -0800
- **作者**：Ryan Blue
- **提交说明**：API, Core: Support default values in UpdateSchema (#12211)
- **PR/Issue**：#12211

## 总体目的

在 Iceberg 的模式更新 API（`UpdateSchema`）中支持为新增列设置默认值。这是 Iceberg v3 规范中"列默认值"功能在 API 和 Core 层面的实现。

Iceberg v3 规范引入了两种默认值：
- `initial-default`：列添加时已有行的默认值，用于读取旧数据文件时填充新列的值。
- `write-default`：写入时如果未提供值则使用的默认值。

此前 `UpdateSchema` API 的 `addColumn` 和 `addRequiredColumn` 方法不支持指定默认值，新增的必需列（required column）只能通过 `allowIncompatibleChanges()` 强制添加，这会导致读取旧数据文件时出现问题（旧文件中没有该列的数据）。通过支持默认值，新增必需列不再是不兼容变更——旧数据文件中的行会使用默认值填充新列。

同时，此提交还重构了 `Types.NestedField` 的默认值存储方式，从 `Object` 改为 `Literal<?>`，使默认值具有类型信息，便于类型安全的处理和序列化。

## 如何达成设计目的

通过以下多个层面的修改来实现目标：

1. **API 层（UpdateSchema）**：新增带 `Literal<?> defaultValue` 参数的 `addColumn` 和 `addRequiredColumn` 重载方法，以及 `updateColumnDefault` 方法。通过方法重载保持向后兼容。
2. **类型层（Types.NestedField）**：重构 `NestedField` 的默认值存储从 `Object` 改为 `Literal<?>`，新增 `Builder` 方法和 `initialDefaultLiteral()`/`writeDefaultLiteral()` 方法。
3. **实现层（SchemaUpdate）**：更新 `internalAddColumn` 方法支持默认值，重构添加列的内部数据结构（从 `Multimap<Integer, NestedField>` 改为 `Multimap<Integer, Integer>`），更新 `ApplyChanges` 访问器以适配新数据结构。
4. **表达式层（Expressions）**：增加对 NaN 字面量的校验，防止将 NaN 作为普通字面量使用。
5. **兼容性改进**：新增必需列时如果有默认值则不视为不兼容变更；将列从可选改为必需时如果是带默认值的新增列则不视为不兼容变更。

## 修改详情

### `api/src/main/java/org/apache/iceberg/UpdateSchema.java`（修改, +288/-72 lines）

**修改目的**：在 API 层添加默认值支持。

**工作逻辑**：
- 新增多个 `addColumn` 重载方法，支持 `Literal<?> defaultValue` 参数：`addColumn(name, type, defaultValue)`, `addColumn(name, type, doc, defaultValue)`, `addColumn(parent, name, type, defaultValue)`, `addColumn(parent, name, type, doc, defaultValue)`。
- 同样新增 `addRequiredColumn` 的重载方法带 `defaultValue` 参数。
- 新增 `updateColumnDefault(String name, Literal<?> newDefault)` 方法，用于更新已有列的默认值。
- 原有不带默认值的方法改为 default 方法，通过委托调用新方法并传入 `null` 默认值保持向后兼容。
- 最终的抽象方法签名变为带 5 个参数（含 parent, name, type, doc, defaultValue），默认抛出 `UnsupportedOperationException("Default values are not supported")`。
- 更新文档注释，说明添加必需列时建议使用默认值以避免不兼容变更。

### `api/src/main/java/org/apache/iceberg/types/Types.java`（修改, +79/-30 lines）

**修改目的**：重构 NestedField 的默认值存储为类型安全的 `Literal<?>`。

**工作逻辑**：
- `NestedField` 的 `initialDefault` 和 `writeDefault` 字段类型从 `Object` 改为 `Literal<?>`。
- 新增 `builder()` 静态工厂方法，创建无参 Builder（不再强制在构造时指定 isOptional 和 name）。
- Builder 新增方法：`asRequired()`, `asOptional()`, `isOptional(boolean)`, `withName(String)`。
- 新增 `withInitialDefault(Literal<?>)` 和 `withWriteDefault(Literal<?>)` 方法，原 `Object` 参数版本标记为 `@Deprecated`。
- `castDefault` 方法改为接受 `Literal<?>` 参数，直接调用 `defaultValue.to(type)` 进行类型转换。
- 新增 `initialDefaultLiteral()` 和 `writeDefaultLiteral()` 方法返回 `Literal<?>`，原 `initialDefault()` 和 `writeDefault()` 方法改为通过 `literal.value()` 返回 `Object`。

### `api/src/main/java/org/apache/iceberg/expressions/Expressions.java`（修改, +4 lines）

**修改目的**：防止 NaN 被用作普通字面量。

**工作逻辑**：在 `predicates` 方法（创建谓词的内部方法）中增加校验，如果字面量值是 NaN 则抛出 `IllegalArgumentException`，提示使用 `isNaN` 或 `notNaN` 代替。这防止了默认值被设为 NaN 值。

### `core/src/main/java/org/apache/iceberg/SchemaUpdate.java`（修改, +135/-87 lines）

**修改目的**：在 SchemaUpdate 实现中支持默认值，并重构内部数据结构。

**工作逻辑**：
- 将 `adds`（`Multimap<Integer, Types.NestedField>`）改为 `parentToAddedIds`（`Multimap<Integer, Integer>`），仅存储父 ID 到新增字段 ID 的映射，实际字段存储在 `updates` Map 中。
- `internalAddColumn` 方法新增 `Literal<?> defaultValue` 参数，使用 `Types.NestedField.builder()` 构建新字段并设置 `withInitialDefault(defaultValue)` 和 `withWriteDefault(defaultValue)`。
- 新增校验：添加必需列时如果没有默认值且未调用 `allowIncompatibleChanges()`，则抛出异常。
- `internalUpdateColumnRequirement` 方法中，新增 `isDefaultedAdd` 检查：如果是带默认值的新增列，将可选改为必需不视为不兼容变更。
- 新增 `findForUpdate(String name)` 方法，统一查找已有字段和待添加字段。
- `ApplyChanges` 内部类适配新的 `parentToAddedIds` 数据结构，通过 `updates::get` 获取实际字段。
- `apply()` 方法、`updateMapping()` 方法、`updateNameMapping()` 方法等适配新数据结构。
- 重命名操作使用 `Types.NestedField.from(field).withName(newName).build()` 替代手动构造字段，保留默认值等信息。

### `core/src/main/java/org/apache/iceberg/SchemaParser.java`（修改, +6/-4 lines）

**修改目的**：适配 `Literal<?>` 类型的默认值序列化。

**工作逻辑**：更新默认值序列化逻辑，使用 `Literal` 类型的方法替代 `Object` 类型的方法。

### `core/src/main/java/org/apache/iceberg/mapping/MappingUtil.java`（修改, +8/-4 lines）

**修改目的**：适配新的 `parentToAddedIds` 数据结构。

**工作逻辑**：更新 `update` 方法签名，从 `Multimap<Integer, Types.NestedField>` 改为 `Multimap<Integer, Integer>`，通过 `updates::get` 获取实际字段。

### `core/src/main/java/org/apache/iceberg/schema/UnionByNameVisitor.java`（修改, +8/-3 lines）

**修改目的**：适配 `NestedField.Builder` 的新 API。

**工作逻辑**：使用 `Types.NestedField.from()` 和 builder 方法替代直接构造，保留默认值信息。

### `core/src/test/java/org/apache/iceberg/TestSchemaUpdate.java`（修改, +268/-0 lines）

**修改目的**：测试新增的默认值功能。

**工作逻辑**：新增大量测试用例，覆盖：添加带默认值的列、添加带默认值的必需列、更新列默认值、带默认值的必需列不触发不兼容变更等场景。

### `core/src/test/java/org/apache/iceberg/TestSchemaUnionByFieldName.java`（修改, +50/-0 lines）

**修改目的**：测试 UnionByName 在新 Builder API 下的行为。

**工作逻辑**：新增测试用例确保 schema union by field name 在新 API 下正常工作。

### `flink/src/test/java/org/apache/iceberg/flink/TestFlinkCatalogTable.java`（修改, -11 lines, 3处）

**修改目的**：移除过时的测试断言。

**工作逻辑**：从三处测试变体中移除了不再适用的断言（共 11 行），适配新的默认值行为。

## 小结

- **成效**：在 `UpdateSchema` API 中完整支持了列默认值功能，包括添加列时设置默认值、更新列默认值、带默认值的必需列不视为不兼容变更等。重构了 `NestedField` 的默认值存储为类型安全的 `Literal<?>`，提升了类型安全性。这是 Iceberg v3 规范中列默认值功能的关键实现。
- **影响范围**：影响 API 层（UpdateSchema 接口）、Core 层（SchemaUpdate 实现、Types.NestedField、SchemaParser、MappingUtil）、Flink 测试。涉及 API 变更（新增方法、废弃旧方法），但通过 default 方法保持了向后兼容。
- **回迁到 1.4.x 的注意事项**：这是一个较大的功能性变更，涉及 API 层面的接口扩展和类型重构。回迁到 1.4.x 需要评估：1.4.x 是否需要支持 v3 的列默认值功能；1.4.x 的 `Types.NestedField` 和 `SchemaUpdate` 代码结构是否与 main 分支兼容。由于涉及 `Literal<?>` 类型重构（从 `Object` 到 `Literal<?>`），可能影响序列化、SchemaParser 等多个组件，回迁需要同步处理所有相关文件。如果 1.4.x 不计划支持 v3 的默认值功能，不建议回迁。如果需要回迁，建议作为一组关联变更整体回迁，避免部分回迁导致不一致。
