# 提交 1732：API: Deprecate NestedType.of in favor of builder (#12227)

## 提交信息

- **序号**：1732 / 4088
- **哈希**：b78d36a758728e51e35cc8b7b49d0aa91f97aacd
- **短哈希**：b78d36a75
- **日期**：2025-02-14 11:13:43 +0100
- **作者**：Ryan Blue
- **提交说明**：API: Deprecate NestedType.of in favor of builder (#12227)
- **PR/Issue**：#12227

## 总体目的

`Types.NestedField.of()` 是一组静态工厂方法，用于创建 `NestedField` 实例（Iceberg schema 中的字段定义）。这些方法接受位置参数（如 `of(int id, boolean isOptional, String name, Type type)`），当参数较多时容易出错，且不支持灵活地设置可选属性（如 `doc`、初始值、默认值等）。

Iceberg 项目正在推进 API 现代化，引入了 `NestedField.builder()` 构建器模式，提供更清晰、更可扩展的字段构建方式。本提交的目标是将旧的 `NestedField.of()` 静态工厂方法标记为 `@Deprecated`（计划在 2.0.0 版本移除），同时将项目内部所有使用 `of()` 方法的代码迁移到使用 builder 模式，以消除内部的 deprecation 警告并为外部用户提供迁移示例。

## 如何达成设计目的

提交分两个层面完成迁移：

1. **API 层面**：在 `Types.NestedField` 中为两个 `of()` 重载方法添加 `@Deprecated` 注解和 Javadoc，指引用户使用 `builder()` 方法替代。

2. **内部代码迁移**：将 api、core、flink、kafka-connect、orc 和 spark（v3.3/v3.4/v3.5）模块中所有调用 `NestedField.of()` 的代码改为使用 builder 模式。迁移策略包括：
   - 使用 `NestedField.builder().withId(id).isOptional(isOptional).withName(name).ofType(type).build()` 替代完整的 `of()` 调用。
   - 使用 `NestedField.optional(name).withId(id).ofType(type).withDoc(doc).build()` 等更简洁的变体。
   - 在 ORC 模块的 switch-case 场景中，先创建 builder 再根据类型设置 `ofType`，最后统一 `build()`，避免了原来需要为每个分支创建完整 `NestedField` 的重复代码。

## 修改详情

### `api/src/main/java/org/apache/iceberg/types/Types.java`（修改, +12 lines）

**修改目的**：将 `NestedField.of()` 的两个重载方法标记为废弃。

**工作逻辑**：为 `of(int id, boolean isOptional, String name, Type type)` 和 `of(int id, boolean isOptional, String name, Type type, String doc)` 两个方法各添加了 `@Deprecated` 注解和 Javadoc 注释 `@deprecated will be removed in 2.0.0; use {@link #builder()} instead.`。

### `core/src/main/java/org/apache/iceberg/MetricsUtil.java`（修改, +14/-13 lines）

**修改目的**：将 `MetricsUtil` 中构建可读指标 schema 的代码从 `of()` 迁移到 builder 模式。

**工作逻辑**：原来使用 `Types.NestedField.of(nextId, true, colName, structType, doc)` 一次性构建字段。迁移后改为 `Types.NestedField.optional(colName).withId(nextId).ofType(structType).withDoc(doc).build()`，使用 `optional(name)` 快捷方法指定可选字段名，再链式设置 ID、类型和文档。

### `orc/src/main/java/org/apache/iceberg/orc/OrcToIcebergVisitor.java`（修改, +38/-31 lines）

**修改目的**：将 ORC schema 转换为 Iceberg schema 的 visitor 代码迁移到 builder 模式。

**工作逻辑**：
- 对于 struct、list、map 类型字段，将 `of(id, isOptional, name, type)` 改为 `builder().withId(id).isOptional(isOptional).withName(name).ofType(type).build()`。
- 对于 primitive 类型字段，重构了 switch-case 结构：先创建 `NestedField.Builder` 并设置公共属性（id、optional、name），再在各个 case 分支中只调用 `builder.ofType(...)`，最后统一 `build()`。这消除了原来每个 case 分支都需要重复 `of(id, isOptional, name, type)` 的冗余代码。

### `core/src/test/java/org/apache/iceberg/TestSchema.java`（修改, +4/-3 lines）

**修改目的**：将测试代码中的 `of()` 调用迁移到 builder 模式。

### `core/src/test/java/org/apache/iceberg/types/TestTypeUtil.java`（修改, +5/-4 lines）

**修改目的**：将 `TestTypeUtil` 测试中的 `of()` 调用迁移到 builder 模式。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SortKeySerializer.java`（修改, +4/-3 lines）

**修改目的**：将 Flink 模块的 `SortKeySerializer` 中的 `of()` 调用迁移到 builder。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SortKeyUtil.java`（修改, +7/-4 lines）

**修改目的**：将 Flink 模块的 `SortKeyUtil` 中的 `of()` 调用迁移到 builder。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/SchemaUtils.java`（修改, +8/-4 lines）

**修改目的**：将 Kafka Connect 模块的 `SchemaUtils` 中的 `of()` 调用迁移到 builder。

### 其余测试文件（多模块, 共约 14 个文件）

涉及 `TestDeleteFiles.java`、`TestSchemaParser.java`、`TestReadDefaultValues.java`、`TestCreateTableRequest.java`、`DataTest.java`、`TestBuildOrcProjection.java`、以及 Spark v3.3/v3.4/v3.5 的 `AvroDataTest.java` 等。这些文件均将 `NestedField.of()` 调用迁移为 builder 模式，修改模式与上述一致。

## 小结

- **成效**：成功将 `NestedField.of()` 标记为 `@Deprecated`，并将项目内部 18 个文件中的所有调用迁移到 builder 模式。这不仅消除了内部 deprecation 警告，还改善了代码可读性（尤其在 ORC visitor 的 switch-case 中消除了大量重复代码），并为外部用户提供了迁移参考。
- **影响范围**：涉及 api、core、flink（v1.20）、kafka-connect、orc 和 spark（v3.3/v3.4/v3.5）多个模块。API 变更（添加 `@Deprecated`）是源码兼容的，不影响已有外部代码的编译和运行。
- **回迁到 1.4.x 的注意事项**：此提交是 API 现代化的一部分，回迁需要确认 1.4.x 分支中 `NestedField.builder()` 方法已存在（builder 模式应该是此前引入的）。如果 builder API 已存在，则可以安全回迁。需注意 1.4.x 可能不支持 Flink v1.20 模块，需跳过对应文件。建议回迁以保持 API 一致性。
