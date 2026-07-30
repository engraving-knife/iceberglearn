# 提交 3938：Core: Migrate switch statements to switch expressions (#16881)

## 提交信息

- **序号**：3938 / 4088
- **哈希**：534c3fd9427b7037b5fa683d51b4dd5fc255c472
- **短哈希**：534c3fd94
- **日期**：2026-06-24 08:54:00 +0200
- **作者**：Neelesh Salian
- **提交说明**：Core: Migrate switch statements to switch expressions (#16881)
- **PR/Issue**：#16881

## 总体目的

这次提交将 Iceberg 核心模块中大量的传统 `switch` 语句（switch statements）迁移为 Java 14+ 引入的 `switch` 表达式（switch expressions）。switch 表达式是一种更简洁、更安全的语法形式，使用 `case L ->` 箭头标签替代传统的 `case L:` 冒号标签，具有以下优势：

1. **无 fall-through**：箭头标签不会 fall-through 到下一个 case，避免了因遗漏 `break` 导致的常见 bug。
2. **表达式形式**：switch 可以作为表达式直接返回值，减少中间变量赋值。
3. **穷尽性检查**：当 switch 表达式赋值给变量时，编译器会检查是否覆盖所有可能的枚举值（配合 `default` 或 `yield`）。
4. **代码更紧凑**：减少了样板代码（break 语句、临时变量），使逻辑更清晰。

此次迁移涉及 core 模块中 84 个文件，总计减少约 870 行代码（1766 新增 / 2636 删除），是一次大规模的代码现代化重构。

## 如何达成设计目的

通过机械化的语法转换，将每个文件中的传统 switch 语句转换为 switch 表达式。转换模式通常是：
- 将 `case X: ... break; return V;` 转换为 `case X -> V;`
- 将 `case X: ... break;` 转换为 `case X -> { ... }`
- 将赋值式 switch（`switch(x) { case A: y = 1; break; ... }`）转换为 `y = switch(x) { case A -> 1; ... }`

由于这是纯语法重构，不改变任何运行时行为，因此不涉及测试逻辑的修改（测试文件中如果有 switch 也会被转换）。

## 修改详情

### 84 个 core 模块文件 (+1766/-2636 lines)

**修改目的**：将传统 switch 语句迁移为 switch 表达式。

**工作逻辑**：
涉及的文件遍布 core 模块的各个子包，包括：
- **核心数据结构**：`BaseFile.java`、`GenericManifestFile.java`、`ManifestFiles.java`、`ManifestLists.java`、`PartitionData.java`、`TrackedFileStruct.java`、`TrackingStruct.java` 等。
- **Schema/类型处理**：`SchemaParser.java`、`SingleValueParser.java`、`MetadataUpdateParser.java`、`UpdateRequirementParser.java`、`ExpressionParser.java` 等。
- **元数据版本**：`V1Metadata.java`、`V2Metadata.java`、`V3Metadata.java`、`V4Metadata.java`。
- **Avro 模块**：`Avro.java`、`AvroSchemaUtil.java`、`AvroSchemaVisitor.java`、`GenericAvroReader.java`、`InternalReader.java`、`ValueReaders.java` 等。
- **REST 模块**：`CatalogHandlers.java`、`ErrorHandlers.java`、`RESTTableScan.java`、`OAuth2Util.java` 等。
- **其他**：`DeletionVectorStruct.java`、`SnapshotSummary.java`、`PuffinFormat.java`、`PrimitiveWrapper.java` 等。

每个文件中的 switch 语句都被转换为箭头标签形式的 switch 表达式，减少了 break 语句和临时变量。

## 总结

这次提交是一次大规模的代码现代化重构，将 core 模块 84 个文件中的传统 switch 语句迁移为 Java 14+ 的 switch 表达式。通过消除 fall-through 风险和样板代码，提升了代码的安全性和可读性。由于是纯语法转换，不改变运行时行为。注意：该提交随后在 #16953 中被回退（提交 3942），可能是因为在 1.4.x 分支上引发了问题或需要进一步验证。
