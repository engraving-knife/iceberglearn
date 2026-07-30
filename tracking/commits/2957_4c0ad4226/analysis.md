# 提交 2957：Core: Align ContentFile enum serialization with REST Spec (#14739)

## 提交信息

- **序号**：2957 / 4088
- **哈希**：4c0ad422624eb7e2b3b196a4ea77ee8ad83a69e9
- **短哈希**：4c0ad4226
- **日期**：2025-12-04
- **作者**：Drew Gallardo
- **提交说明**：Core: Align ContentFile enum serialization with REST Spec (#14739)
- **PR/Issue**：#14739

## 总体目的

Iceberg 的 REST Catalog 规范定义了文件元数据中 `content` 与 `file-format` 字段应使用小写 kebab-case 字符串（如 `"data"`、`"position-deletes"`、`"equality-deletes"`、`"parquet"`、`"metadata"`）。然而在 1.10 及之前的 Java 实现中，`ContentFileParser` 在序列化时直接使用 Java 枚举的 `name()`，产生的是大写带下划线的值（如 `"DATA"`、`"POSITION_DELETES"`、`"PARQUET"`），与 REST 规范不一致。这会导致用旧版本 Java 客户端与严格遵循规范的 REST 服务端（或其他语言实现）交互时出现字段值不匹配的问题。

本提交从 1.11 起将序列化输出改为规范要求的小写 kebab-case 形式，同时在反序列化端保持对旧大写格式的向后兼容，确保升级过程中不会破坏与已有数据/服务的互操作性。注释中明确标注了 "Since 1.11" 作为版本变更说明。

## 如何达成设计目的

核心改动集中在 `ContentFileParser`：序列化时对 `FileContent.name()` 做 `toLowerCase` + `replace('_', '-')` 转换，对 `FileFormat.name()` 做 `toLowerCase` 转换；反序列化时新增 `fileContentFromJson` 方法，优先匹配新的小写 kebab-case 字符串，匹配失败时回退到旧的 `FileContent.valueOf()` 以兼容 1.10 及之前格式的输入。`FileFormat.fromString()` 本身已能处理大小写，故格式反序列化无需额外改动。测试方面，所有相关测试用例的 JSON 字面量统一改为新格式，并新增了非法值报错、大写格式向后兼容、枚举往返序列化三个测试。

## 修改详情

### `core/src/main/java/org/apache/iceberg/ContentFileParser.java` (+26/-3 lines)

**修改目的**：将 `content` 与 `file-format` 字段的序列化输出对齐 REST 规范的小写 kebab-case 格式，并保持反序列化的向后兼容。

**工作逻辑**：
新增三个字符串常量 `CONTENT_DATA = "data"`、`CONTENT_POSITION_DELETES = "position-deletes"`、`CONTENT_EQUALITY_DELETES = "equality-deletes"`，用于反序列化时的匹配。序列化端（`toJson`）将 `contentFile.content().name().toLowerCase(Locale.ENGLISH).replace('_', '-')` 写入 `content` 字段（如 `POSITION_DELETES` → `position-deletes`），将 `contentFile.format().name().toLowerCase(Locale.ENGLISH)` 写入 `file-format` 字段（如 `PARQUET` → `parquet`）。使用 `Locale.ENGLISH` 避免在土耳其语等 locale 下出现大小写转换异常。

反序列化端（`fromJson`）将原先的 `FileContent.valueOf(...)` 替换为调用新方法 `fileContentFromJson`。该方法用 `switch` 匹配三个新格式字符串常量；若不匹配，则回退尝试 `FileContent.valueOf(content)` 以兼容 1.10 及之前的大写枚举名格式，回退失败时抛出带原始值的 `IllegalArgumentException`（消息为 `"Invalid file content value: '%s'"`），比原先 `valueOf` 抛出的原始异常信息更清晰。

### `core/src/test/java/org/apache/iceberg/TestContentFileParser.java` (+82/-28 lines)

**修改目的**：更新所有测试 JSON 字面量为新格式，并新增覆盖边界场景的测试。

**工作逻辑**：
将 `dataFileJsonWithRequiredOnly`、`dataFileJsonWithAllOptional`、`deleteFileJsonWithRequiredOnly`、`deleteFileJsonWithAllOptional`、`deleteFileWithDataRefJson`、`dvJson` 等辅助方法中的 `"content":"DATA"` / `"POSITION_DELETES"` / `"EQUALITY_DELETES"` 统一改为 `"data"` / `"position-deletes"` / `"equality-deletes"`，`"file-format":"PARQUET"` / `"METADATA"` / `"PUFFIN"` 改为 `"parquet"` / `"metadata"` / `"puffin"`。

新增三个测试：
- `testInvalidContentType`：传入 `"content":"invalid-content"`，断言抛出 `IllegalArgumentException` 且消息为 `"Invalid file content value: 'invalid-content'"`。
- `testUppercaseFileFormat`：传入大写 `"file-format":"PARQUET"`，验证仍能正确解析为 `FileFormat.PARQUET`，确认格式反序列化的向后兼容。
- `testEnumContentTypeSerialization`（参数化）：分别用旧枚举名 `DATA`、`POSITION_DELETES`、`EQUALITY_DELETES` 作为输入 JSON 的 `content` 值，验证反序列化成功（向后兼容），且重新序列化后输出为新格式 `"data"` / `"position-deletes"` / `"equality-deletes"`（确认往返一致性）。

### `core/src/test/java/org/apache/iceberg/TestDataTaskParser.java` (+6/-6 lines)

**修改目的**：同步更新 DataTask 测试中的 JSON 字面量为新格式。

**工作逻辑**：将三处 `"content":"DATA"` 改为 `"content":"data"`，`"file-format":"METADATA"` 改为 `"file-format":"metadata"`。这些是 `StaticDataTask` 元数据文件的序列化测试场景。

### `core/src/test/java/org/apache/iceberg/TestFileScanTaskParser.java` (+12/-12 lines)

**修改目的**：同步更新 FileScanTask 测试中的 JSON 字面量为新格式。

**工作逻辑**：三个测试方法中的 `data-file` 与 `delete-files` 字段，将 `"content":"DATA"` / `"POSITION_DELETES"` / `"EQUALITY_DELETES"` 改为小写 kebab-case，`"file-format":"PARQUET"` 改为 `"parquet"`。

### `core/src/test/java/org/apache/iceberg/rest/responses/TestFetchPlanningResultResponseParser.java` (+6/-6 lines)

**修改目的**：同步更新 FetchPlanningResult 响应测试中的 JSON 字面量。

**工作逻辑**：将 `delete-files` 与 `file-scan-tasks` 中的 `content` 和 `file-format` 值统一改为小写格式。

### `core/src/test/java/org/apache/iceberg/rest/responses/TestFetchScanTasksResponseParser.java` (+6/-6 lines)

**修改目的**：同步更新 FetchScanTasks 响应测试中的 JSON 字面量。

**工作逻辑**：同上，将 `content` 与 `file-format` 值改为小写 kebab-case 格式。

### `core/src/test/java/org/apache/iceberg/rest/responses/TestPlanTableScanResponseParser.java` (+24/-24 lines)

**修改目的**：同步更新 PlanTableScan 响应测试中的 JSON 字面量。

**工作逻辑**：该文件改动量最大，涉及紧凑格式与美化格式（带缩进换行的 `expectedJson`）两种 JSON 字面量，将所有 `content` 值（`DATA`、`POSITION_DELETES`、`EQUALITY_DELETES`）与 `file-format` 值（`PARQUET`）统一改为小写格式。

## 总结

本提交使 Java 实现的 ContentFile 序列化输出与 Iceberg REST Catalog 规范保持一致，从 1.11 起输出小写 kebab-case 的 `content` 与 `file-format` 值。反序列化端通过优先匹配新格式、回退旧枚举名的方式实现了平滑的向后兼容，确保升级期间与旧数据/旧服务的互操作不受影响。测试全面覆盖了新格式、旧格式兼容、非法值报错与往返一致性，保证了改动的正确性与健壮性。
