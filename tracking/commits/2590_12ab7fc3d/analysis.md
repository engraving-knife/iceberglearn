# 提交 2590：Parquet: Bump Parquet-Java to 1.16.0 and use logical annotation for variant type (#13941)

## 提交信息

- **序号**：2590 / 4088
- **哈希**：12ab7fc3d6d53534da02decdab99133853b36dfd
- **短哈希**：12ab7fc3d
- **日期**：2025-09-03 06:54:53 -0700
- **作者**：Fokko Driesprong
- **提交说明**：Parquet: Bump Parquet-Java to 1.16.0 and use logical annotation for variant type (#13941)
- **PR/Issue**：#13941

## 总体目的

本次提交包含两个相互关联的改动：将 Parquet-Java 依赖从 1.15.2 升级到 1.16.0，并利用新版本中引入的 Variant 逻辑类型注解（LogicalTypeAnnotation）来正确标识 Variant 类型。

背景是 Iceberg 正在推进 Variant 类型（半结构化数据类型）的支持。在 Parquet-Java 1.16.0 之前，Parquet 并没有原生的 Variant 逻辑类型注解，Iceberg 只能通过自身的 schema 信息来识别 Variant 列。Parquet-Java 1.16.0 正式引入了 `LogicalTypeAnnotation.variantType()`，使得 Variant 类型可以在 Parquet 文件层面被标准化地标注，便于不同引擎和工具正确识别和处理 Variant 数据。

这次升级还顺带修复了一些测试中的文件大小估算问题，以适配新版本 Parquet 可能带来的元数据变化。

## 如何达成设计目的

1. 在 `gradle/libs.versions.toml` 中将 parquet 版本号从 1.15.2 升到 1.16.0。
2. 在 `Variant` 接口中定义常量 `VARIANT_SPEC_VERSION = 1`，用于在写 Parquet 逻辑类型注解时传递 Variant 规范版本。
3. 在 `TypeToMessageType` 中，为 Variant 类型生成的 Parquet group 类型添加 `LogicalTypeAnnotation.variantType()` 注解，使写入的 Parquet 文件携带 Variant 逻辑类型信息。
4. 在 `TypeWithSchemaVisitor` 和 `ParquetWithSparkSchemaVisitor` 中，读取时除了依赖 Iceberg schema 判断 Variant 类型外，也检查 Parquet 的 `VariantLogicalTypeAnnotation`，保证即使没有 Iceberg schema 信息也能识别 Variant。
5. 调整 Spark 各版本的 `TestRewriteDataFilesAction` 测试中的目标文件大小阈值，以适应新版本带来的尺寸变化。

## 修改详情

### `api/src/main/java/org/apache/iceberg/variants/Variant.java` (+4/-0 lines)

**修改目的**：定义 Variant 规范版本常量。

**工作逻辑**：新增 `VARIANT_SPEC_VERSION = (byte) 1` 常量，作为当前 Variant 规范的版本号。该常量会在写入 Parquet 逻辑类型注解时使用，确保 Variant 数据的版本信息被记录到 Parquet 文件中，便于未来版本演进。

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Parquet-Java 版本。

**工作逻辑**：将 `parquet = "1.15.2"` 改为 `parquet = "1.16.0"`。新版本提供了 Variant 逻辑类型注解支持。

### `parquet/src/main/java/org/apache/iceberg/parquet/TypeToMessageType.java` (+3/-0 lines)

**修改目的**：在写入 Parquet 时为 Variant 类型添加逻辑类型注解。

**工作逻辑**：在将 Iceberg 类型转换为 Parquet MessageType 时，对于 Variant 类型的 group，通过 `Types.buildGroup(repetition).as(LogicalTypeAnnotation.variantType(Variant.VARIANT_SPEC_VERSION))` 添加 Variant 逻辑类型注解。这样写入的 Parquet 文件中会明确标注 Variant 类型，无论是 shredded（分片）还是非 shredded 模式都添加了注解。

### `parquet/src/main/java/org/apache/iceberg/parquet/TypeWithSchemaVisitor.java` (+2/-1 lines)

**修改目的**：读取时识别 Parquet 的 Variant 逻辑类型注解。

**工作逻辑**：在 visitor 分发逻辑中，原来仅通过 `iType.isVariantType()` 判断 Variant 类型，现在增加了对 `LogicalTypeAnnotation.VariantLogicalTypeAnnotation` 的检查。这样即使没有 Iceberg schema 信息（iType 为 null），也能通过 Parquet 自身的逻辑类型注解识别 Variant 列并正确访问。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/data/ParquetWithSparkSchemaVisitor.java` (+10/-5 lines)

**修改目的**：在 Spark 4.0 的 Parquet schema visitor 中正确处理 Variant 逻辑类型。

**工作逻辑**：原来对 Variant 的判断仅依赖 Spark 的 `VariantType`，并留有 TODO 注释表示等 Parquet 添加 VARIANT 类型后再用逻辑类型注解判断。现在实现了该 TODO：通过 `LogicalTypeAnnotation.variantType(Variant.VARIANT_SPEC_VERSION).equals(annotation) || sType instanceof VariantType` 双重判断。同时启用了之前被注释掉的 `Preconditions.checkArgument` 校验，确保 Spark 类型确实是 VariantType。注释说明同时检查两种来源是因为有些引擎（如 Spark）产生 VariantType 时不带 Parquet 注解。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (+1/-1 lines)

**修改目的**：调整测试中的目标文件大小阈值。

**工作逻辑**：将 V3 格式的额外文件大小余量从 11000 调到 12000，非 V3 从 1001 调到 1100，以适配新版本 Parquet 写入时可能产生的元数据大小变化，避免测试因文件大小阈值过小而失败。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (+1/-1 lines)

**修改目的**：同 v3.4，调整测试文件大小阈值。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (+9/-3 lines)

**修改目的**：调整测试文件大小阈值并改进文件计数方法。

**工作逻辑**：除了同样的文件大小阈值调整外，还将 `shouldHaveFiles` 方法从使用 `Iterables.size(table.newScan().planFiles())` 改为使用 `StreamSupport.stream(...).collect(Collectors.toList())` 收集到 List 后取 size。这样可以在调试时更方便地查看实际文件列表，同时也避免了潜在的大文件集迭代问题。

## 总结

本次提交是 Variant 类型支持的重要一步，通过升级 Parquet-Java 到 1.16.0 并利用其原生 Variant 逻辑类型注解，使 Iceberg 写出的 Parquet 文件在文件格式层面标准化地标识 Variant 类型。这提升了跨引擎互操作性，让非 Iceberg 工具也能正确识别 Variant 列。同时读取侧的兼容性处理保证了即使数据源不带注解也能正常工作。
