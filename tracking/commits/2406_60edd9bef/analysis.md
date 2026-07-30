# 提交 2406：Flink: Dynamic Sink: Ensure parent for newly added struct is resolved from current schema (#13656)

## 提交信息

- **序号**：2406 / 4088
- **哈希**：60edd9befaa0eb10e833328ecae1814fdf16a69d
- **短哈希**：60edd9bef
- **日期**：2025-07-24 15:14:39 +0200
- **作者**：Maximilian Michels
- **提交说明**：Flink: Dynamic Sink: Ensure parent for newly added struct is resolved from current schema (#13656)
- **PR/Issue**：#13656（backports #13639）

## 总体目的

此提交将 PR #13639（提交 2389）backport 到 Flink 1.19 和 1.20 分支。核心修复是**在 Flink 动态 Sink 的 schema 演进中，确保添加嵌套 struct 字段时父字段名称从当前 schema（existingSchema）而非目标 schema（targetSchema）解析**。

原 bug 在于 `EvolveSchemaVisitor.addColumn` 方法使用 `targetSchema.findColumnName(parentId)` 查找父字段名称，但 `UpdateSchema` API 是基于当前 schema 进行增量操作的，parentId 对应的字段必须在 existingSchema 中查找。此 bug 在添加嵌套 struct 时暴露——新添加的 struct 的父节点可能尚未在 `UpdateSchema` 的上下文中注册。

此提交的代码变更与 2389（flink/v2.0）完全一致，但同时应用到 flink/v1.19 和 flink/v1.20 两个版本。

## 如何达成设计目的

与 2389 完全一致的设计：

1. **修复数据源**：将 `addColumn` 方法中 `targetSchema.findColumnName(parentId)` 改为 `existingSchema.findColumnName(parentId)`。
2. **测试增强**：`testAddNestedStruct` 测试使用非空 currentSchema（包含 `struct1.struct2` 结构）替代空 schema，真实覆盖添加嵌套 struct 的场景。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/EvolveSchemaVisitor.java` (+1/-1 lines)

**修改目的**：修复 addColumn 方法中父字段名称解析的数据源。

**工作逻辑**：将 `targetSchema.findColumnName(parentId)` 修改为 `existingSchema.findColumnName(parentId)`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestEvolveSchemaVisitor.java` (+4/-2 lines)

**修改目的**：增强测试用例以覆盖从非空 schema 添加嵌套 struct。

**工作逻辑**：`testAddNestedStruct` 引入包含 `struct1.struct2` 嵌套结构的 currentSchema，使用 `loadUpdateApi(currentSchema, 2)` 初始化，验证向该结构添加更深层次嵌套字段时能正确演进到 targetSchema。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/EvolveSchemaVisitor.java` (+1/-1 lines)

**修改目的**：Flink 1.20 的相同修复。

**工作逻辑**：与 1.19 相同的修复。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestEvolveSchemaVisitor.java` (+4/-2 lines)

**修改目的**：Flink 1.20 的相同测试增强。

**工作逻辑**：与 1.19 相同的测试修改。

## 总结

此提交是 2389 在 Flink 1.19 和 1.20 分支上的对应 backport，代码变更完全一致。修复了动态 Sink schema 演进中添加嵌套 struct 字段时的父字段解析 bug，确保 `UpdateSchema` API 的 `addColumn` 操作从当前 schema 解析父字段引用。与 2389（Flink 2.0）一起，覆盖了所有维护中的 Flink 版本。
