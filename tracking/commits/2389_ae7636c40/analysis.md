# 提交 2389：Flink: Dynamic Sink: Ensure parent for newly added struct is resolved from current schema (#13639)

## 提交信息

- **序号**：2389 / 4088
- **哈希**：ae7636c402bf25937df28f89789d88d8034bc071
- **短哈希**：ae7636c40
- **日期**：2025-07-23 14:53:23 +0200
- **作者**：Maximilian Michels
- **提交说明**：Flink: Dynamic Sink: Ensure parent for newly added struct is resolved from current schema (#13639)
- **PR/Issue**：#13639

## 总体目的

此提交修复了 Flink 动态 Sink 中 schema 演进（schema evolution）的一个 bug。当向表中添加嵌套 struct 字段时，`EvolveSchemaVisitor.addColumn` 方法错误地从 `targetSchema`（目标 schema）解析父字段名称，而不是从 `existingSchema`（当前 schema）解析。

问题的根源在于：`UpdateSchema` API 的 `addColumn` 操作需要引用当前 schema 中已存在的父字段。如果父字段是在同一次 schema 演进中刚添加的，那么从 targetSchema 查找父字段名称会导致查找失败或查找到错误的字段，因为 `UpdateSchema` API 是基于当前 schema 进行增量操作的。正确的做法是从 existingSchema 中查找父字段名称，因为 `UpdateSchema` API 在执行 addColumn 时所操作的基础就是当前已有的 schema。

这个 bug 在添加嵌套 struct（例如在已有的 struct 内部再添加一个子 struct）时会暴露出来，因为新添加的 struct 的父节点可能尚未在 `UpdateSchema` 的上下文中注册。

## 如何达成设计目的

设计思路非常直接：将 `addColumn` 方法中解析父字段名称的数据源从 `targetSchema` 改为 `existingSchema`。这符合 `UpdateSchema` API 的工作原理——它基于当前 schema 进行增量变更，因此父字段引用必须指向当前 schema 中实际存在的字段。

同时更新了测试用例 `testAddNestedStruct`，使其使用一个非空的 currentSchema（包含 `struct1.struct2` 结构），而不是空 schema，从而能够真实覆盖添加嵌套 struct 的场景。

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/EvolveSchemaVisitor.java` (+1/-1 lines)

**修改目的**：修复 `addColumn` 方法中父字段名称解析的数据源错误。

**工作逻辑**：在 `addColumn(int parentId, Types.NestedField field)` 方法中，将 `targetSchema.findColumnName(parentId)` 修改为 `existingSchema.findColumnName(parentId)`。`UpdateSchema` API 基于当前 schema 进行增量操作，因此 parentId 对应的字段名称必须在 existingSchema 中查找，而非 targetSchema。targetSchema 中的字段 ID 分配可能与 existingSchema 不同，尤其是在新添加 struct 的场景下。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestEvolveSchemaVisitor.java` (+4/-2 lines)

**修改目的**：增强测试用例以覆盖从非空 schema 添加嵌套 struct 的场景。

**工作逻辑**：`testAddNestedStruct` 测试原先使用空 schema（`new Schema()`）作为 currentSchema，无法有效测试父字段从 currentSchema 解析的逻辑。修改后引入了一个包含 `struct1.struct2` 嵌套结构的 currentSchema，并使用 `loadUpdateApi(currentSchema, 2)` 初始化 UpdateSchema API（起始字段 ID 为 2），然后验证向该结构添加更深层次嵌套字段时能正确演进到 targetSchema。

## 总结

这是一个精准的 bug 修复，解决了 Flink 动态 Sink 在 schema 演进过程中添加嵌套 struct 字段时的父字段解析错误。修复确保了 `UpdateSchema` API 的 `addColumn` 操作始终从当前 schema 解析父字段引用，符合增量 schema 演进的语义。测试用例也相应增强，使用非空 currentSchema 真实覆盖了该场景。
