# 提交 2021：API: Use normalized JSON path to identify Variant fields. (#12835)

## 提交信息

- **序号**：2021 / 4088
- **哈希**：89e499f997c2a5abcc22ac58b6872e1c807c6db3
- **短哈希**：89e499f99
- **日期**：2025-04-21 15:16:20 -0600
- **作者**：Ryan Blue
- **提交说明**：API: Use normalized JSON path to identify Variant fields. (#12835)
- **PR/Issue**：#12835

## 总体目的

这个提交将 Variant 字段的标识方式从点分路径（dot-separated path，如 `user.name`）改为规范化的 JSON path 格式（如 `$['user']['name']`），与提交 2020 中规范定义的 Variant 上下界存储格式保持一致。

Variant 类型内部结构灵活（类似 JSON），查询时需要通过路径表达式来引用 Variant 对象中的特定字段。此前 `BoundExtract` 使用 `Joiner.on(".").join(PathUtil.parse(path))` 生成点分路径作为字段名，用于在 Variant 上下界映射中查找。但提交 2020 的规范规定 Variant 上下界使用规范化的 JSON path（RFC 9535 格式）作为键，因此需要统一路径格式。

本提交在 `PathUtil` 中新增了 `toNormalizedPath` 方法和 RFC 9535 转义逻辑，并将 `BoundExtract` 中的路径存储改为规范化格式，使 `InclusiveMetricsEvaluator` 等评估器能正确匹配 Variant 上下界。

## 如何达成设计目的

1. 在 `PathUtil` 中新增 `toNormalizedPath(Iterable<String> fields)` 方法，将字段名列表转换为规范化 JSON path（如 `$['field1']['field2']`）。
2. 新增 `rfc9535escape` 方法处理字段名中需要转义的特殊字符（控制字符、引号、反斜杠等），遵循 RFC 9535 JSONPath 规范。
3. 修改 `BoundExtract`：移除 `fullFieldName`（点分格式），将 `path` 直接存储为规范化 JSON path 格式。
4. 修改 `InclusiveMetricsEvaluator`：使用 `bound.path()`（规范化格式）替代 `bound.fullFieldName()`（点分格式）来查找 Variant 上下界。

## 修改详情

### `api/src/main/java/org/apache/iceberg/expressions/PathUtil.java` (修改, +60/-2 lines)

**修改目的**：新增规范化 JSON path 生成和 RFC 9535 转义功能。

**工作逻辑**：

1. **`toNormalizedPath(Iterable<String> fields)`**：将字段名列表转换为规范化 JSON path 字符串。格式为 `$` + 每个字段名经 `rfc9535escape` 处理后包裹在 `['...']` 中。例如 `["user", "name"]` → `$['user']['name']`。

2. **`rfc9535escape(String name)`**：对字段名中的特殊字符进行 RFC 9535 转义。使用正则 `RFC9535_REQUIRES_ESCAPE` 匹配需要转义的字符（控制字符、引号、反斜杠等），通过 `RFC9535_ESCAPE_REPLACEMENTS` 映射查找替换值。

3. **`buildReplacementMap()`**：构建转义映射表，包含：
   - 特殊转义：`\b`→`\\b`、`\t`→`\\t`、`\f`→`\\f`、`\n`→`\\n`、`\r`→`\\r`、`'`→`\\'`、`\`→`\\\\`
   - 控制字符（0x00-0x1F）：除特殊转义字符外，使用 `\\uXXXX` 格式

4. 将类从 package-private 改为 `public`，以便其他模块使用。

### `api/src/main/java/org/apache/iceberg/expressions/BoundExtract.java` (修改, +4/-7 lines)

**修改目的**：使用规范化 JSON path 替代点分路径。

**工作逻辑**：
移除 `fullFieldName` 字段和 `fullFieldName()` 方法，将 `path` 字段改为存储 `PathUtil.toNormalizedPath(PathUtil.parse(path))` 的结果。移除 `Joiner` 的 import。

### `api/src/main/java/org/apache/iceberg/expressions/InclusiveMetricsEvaluator.java` (修改, +3/-4 lines)

**修改目的**：使用规范化路径查找 Variant 上下界。

**工作逻辑**：
在 `lowerBound` 和 `upperBound` 方法中，将 `bound.fullFieldName()` 改为 `bound.path()`，因为 `path` 现在已经是规范化 JSON path 格式，与 Variant 上下界映射的键格式一致。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetVariantUtil.java` (修改, +2/-1 lines)

**修改目的**：适配路径格式变更。

### 测试文件修改

`TestPathUtil.java`（从 `TestPathParsing.java` 重命名）、`TestExpressionBinding.java`、`TestInclusiveMetricsEvaluatorWithExtract.java`、`TestVariantMetrics.java` 同步更新以测试新的路径格式和转义逻辑。

## 总结

本提交将 Variant 字段标识从点分路径改为 RFC 9535 规范化 JSON path 格式，与规范中定义的 Variant 上下界存储格式统一。核心实现是在 `PathUtil` 中新增 `toNormalizedPath` 和 `rfc9535escape` 方法，处理字段名中的特殊字符转义，确保 Variant 字段路径在表达式绑定和指标评估中的一致性。
