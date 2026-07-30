# 提交 2729：API: Detect whether required fields nested within optionals can produce nulls

## 提交信息

- **序号**：2729 / 4088
- **哈希**：a473b1c6e03f43afa2006fb7e8fc4d2ee969b751
- **短哈希**：a473b1c6e
- **日期**：2025-10-10 16:56:33 -0700
- **作者**：Eduard Tudenhoefner
- **提交说明**：API: Detect whether required fields nested within optionals can produce nulls
- **PR/Issue**：#14270

## 总体目的

在 Iceberg 的数据模型中，Schema 可以包含嵌套结构（struct）。一个字段可以是 required（必填）或 optional（可选）。当一个 required 字段嵌套在一个 optional 字段内部时，虽然该字段本身是 required 的，但由于其父字段是 optional 的，如果父字段为 null，则该 required 字段实际上也可能产生 null 值。

例如，考虑 schema `optional_address: struct(required_street: string)`。`required_street` 虽然标记为 required，但由于 `optional_address` 是 optional 的，当 `optional_address` 为 null 时，`required_street` 实际上也是 null。这意味着 `IS_NULL(required_street)` 可能返回 true，`NOT_NULL(required_street)` 可能返回 false。

此前 PR #13804 通过在 `Accessor` 接口中添加 `hasOptionalFieldInPath()` 方法来解决这个问题——在每个 Accessor 中追踪其访问路径上是否存在 optional 字段。然而，这个方法存在设计问题：它将"是否可能产生 null"的信息硬编码到 Accessor 中，使得 Accessor 类变得复杂（每个 PositionAccessor 都需要额外的布尔字段和构造参数），且这个信息与 Accessor 的核心职责（提供字段访问能力）不相关。

此提交部分回退了 PR #13804 的 Accessor API 变更，改用 Schema visitor 在表达式绑定阶段动态检测父字段是否为 optional。具体来说，`BoundReference.producesNull()` 简化为只检查字段本身是否 optional，而 IS_NULL/NOT_NULL 的优化逻辑则通过 `TypeUtil.ancestorFields()` 方法检查所有祖先字段是否都是 required。

## 如何达成设计目的

主要设计思路：
1. **移除 Accessor 中的 `hasOptionalFieldInPath()` 方法**：简化所有 Accessor 实现，不再需要追踪路径上的 optional 字段
2. **简化 `BoundReference.producesNull()`**：改为只检查字段本身是否 optional
3. **新增 `TypeUtil.ancestorFields()` 方法**：通过 Schema visitor 查找字段的所有祖先字段
4. **在 `UnboundPredicate.bindUnaryOperation()` 中使用祖先信息**：对于 IS_NULL/NOT_NULL 操作，检查字段的所有祖先是否都是 required，只有当字段本身和所有祖先都是 required 时，才能确定 IS_NULL 永远为 false / NOT_NULL 永远为 true

## 修改详情

### `api/src/main/java/org/apache/iceberg/Accessor.java` (+0/-5 lines)

**修改目的**：移除 `hasOptionalFieldInPath()` 默认方法。

**工作逻辑**：删除 `Accessor` 接口中的 `hasOptionalFieldInPath()` 默认方法（返回 false）。该方法是 PR #13804 引入的，用于判断字段访问路径上是否存在 optional 字段。

### `api/src/main/java/org/apache/iceberg/Accessors.java` (+12/-38 lines)

**修改目的**：移除所有 Accessor 实现类中的 `hasOptionalFieldInPath()` 方法和相关字段。

**工作逻辑**：
- 从 `PositionAccessor`、`Position2Accessor`、`Position3Accessor`、`WrappedPositionAccessor` 中移除 `hasOptionalFieldInPath` 字段、方法覆盖和构造参数
- 简化 `newAccessor` 工厂方法，移除 `isOptional` 参数（但保留 `WrappedPositionAccessor` 用于处理 null 层级的逻辑不变）
- `Position2Accessor` 和 `Position3Accessor` 的构造函数不再接收 `isOptional` 参数

### `api/src/main/java/org/apache/iceberg/expressions/BoundReference.java` (+2/-4 lines)

**修改目的**：简化 `producesNull()` 方法。

**工作逻辑**：将 `producesNull()` 从 `return accessor.hasOptionalFieldInPath();`（检查整个路径）简化为 `return field.isOptional();`（只检查字段本身是否 optional）。祖先字段的 optional 性检查移到了 `UnboundPredicate` 中。

### `api/src/main/java/org/apache/iceberg/expressions/UnboundPredicate.java` (+16/-1 lines)

**修改目的**：在 IS_NULL/NOT_NULL 绑定时检查祖先字段的 required 性。

**工作逻辑**：
1. `bindUnaryOperation` 方法新增 `StructType struct` 参数
2. 对于 IS_NULL 操作：当 `!boundTerm.producesNull()` 且 `allAncestorFieldsAreRequired(struct, fieldId)` 同时满足时，返回 `Expressions.alwaysFalse()`（该字段不可能为 null）
3. 对于 NOT_NULL 操作：当 `!boundTerm.producesNull()` 且 `allAncestorFieldsAreRequired(struct, fieldId)` 同时满足时，返回 `Expressions.alwaysTrue()`（该字段一定不为 null）
4. 新增 `allAncestorFieldsAreRequired(StructType, int)` 私有方法：调用 `TypeUtil.ancestorFields()` 获取所有祖先字段，检查是否全部都是 required

### `api/src/main/java/org/apache/iceberg/types/TypeUtil.java` (+24/-0 lines)

**修改目的**：新增 `ancestorFields()` 方法，查找字段的所有祖先字段。

**工作逻辑**：`ancestorFields(Schema schema, int fieldId)` 方法使用 `TypeUtil.indexParents()` 获取字段 ID 到父 ID 的映射，然后从目标字段开始向上遍历父链，收集所有祖先的 `NestedField`。如果字段不是嵌套字段（没有父字段），返回空列表。

### `api/src/test/java/org/apache/iceberg/expressions/TestBoundReference.java` (-108 lines, 删除文件)

**修改目的**：删除已过时的 `producesNull()` 测试文件。

**工作逻辑**：该文件测试了 `BoundReference.producesNull()` 基于 Accessor 的 `hasOptionalFieldInPath()` 行为。由于该机制已被移除，测试不再适用。相关测试逻辑迁移到了 `TestExpressionBinding` 中，以端到端的方式测试 IS_NULL/NOT_NULL 表达式的绑定结果。

### `api/src/test/java/org/apache/iceberg/expressions/TestExpressionBinding.java` (+88/-0 lines)

**修改目的**：添加嵌套结构下 IS_NULL/NOT_NULL 表达式绑定的测试。

**工作逻辑**：新增 `nullCasesWithNestedStructs` 参数化测试，构建不同深度的嵌套 schema（1-5 层），每层的字段可以是 required 或 optional。对于每种组合，验证 `isNull("path.to.leaf")` 和 `notNull("path.to.leaf")` 绑定后的表达式类型：
- 当所有字段都是 required 时，IS_NULL 返回 `alwaysFalse()`，NOT_NULL 返回 `alwaysTrue()`
- 当任何字段是 optional 时，IS_NULL 返回 `IsNull` 谓词，NOT_NULL 返回 `NotNull` 谓词

### `api/src/test/java/org/apache/iceberg/expressions/TestInclusiveMetricsEvaluator.java` (+109/-1 lines)

**修改目的**：添加嵌套结构下 IS_NULL/NOT_NULL 指标评估的测试。

**工作逻辑**：
1. 新增 `NESTED_SCHEMA`：包含一个 required struct（`required_address`）和一个 optional struct（`optional_address`），每个 struct 内部各有一个 required 和 optional 字段
2. 新增 `FILE_6` 测试数据文件，包含 null 值计数
3. 修复 `FILE_5` 的文件名错误（从 "file_4.avro" 改为 "file_5.avro"）
4. `testIsNullInNestedStruct` 测试：验证 IS_NULL 在嵌套结构中的指标评估行为——required 字段的 IS_NULL 返回 false（不读取），optional 字段或嵌套在 optional 中的 required 字段的 IS_NULL 返回 true（需要读取）
5. `testNotNullInNestedStruct` 测试：验证 NOT_NULL 的对称行为

### `api/src/test/java/org/apache/iceberg/types/TestTypeUtil.java` (+96/-0 lines)

**修改目的**：测试 `TypeUtil.ancestorFields()` 方法。

**工作逻辑**：添加三个测试方法：
1. `ancestorFieldsInEmptySchema`：空 schema 返回空列表
2. `ancestorFieldsInNonNestedSchema`：非嵌套字段返回空列表
3. `ancestorFieldsInNestedSchema`：复杂嵌套 schema（包含 struct、map、list 嵌套），验证所有字段的祖先链正确

## 总结

此提交重新设计了"required 字段嵌套在 optional 字段内可能产生 null"的检测机制。相比 PR #13804 在 Accessor 层面追踪 optional 路径，新方案将检测逻辑移到了表达式绑定阶段，使用 `TypeUtil.ancestorFields()` Schema visitor 动态检查祖先字段。这简化了 Accessor API，使其职责更单一，同时正确处理了 IS_NULL/NOT_NULL 在嵌套结构中的优化。与序号 2652 的提交处理的是同一个问题（required nested fields within optionals can produce null），但此提交提供了更优雅的实现方式。
