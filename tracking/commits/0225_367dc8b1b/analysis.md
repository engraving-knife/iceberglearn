# 提交 0225：Core: Add comment property to ViewProperties (#9181)

## 提交信息

- **序号**：0225 / 4088
- **哈希**：367dc8b1b94fd8cbae2b5e4da5d01225e717c49b
- **短哈希**：367dc8b1b
- **日期**：2023-12-05 17:27:42 -0800
- **作者**：Amogh Jahagirdar
- **提交说明**：Core: Add comment property to ViewProperties (#9181)
- **PR/Issue**：#9181

## 总体目的

这是一个面向视图（View）元数据的小型功能扩展，目的是为 `ViewProperties` 增加一个标准的 `comment` 属性常量，使视图元数据的属性集合中能够显式声明视图注释。

Iceberg 的表（Table）属性中早已存在 `comment` 这个标准属性，用于存储人类可读的表注释，并被各种工具（如 Catalog UI、元数据查看器）识别。随着视图（View）规范在 Iceberg 1.x 中逐步成熟，视图也需要一个等价的标准属性来携带视图级别的注释信息。在此之前 `ViewProperties` 仅定义了 `VERSION_HISTORY_SIZE` 与 `METADATA_COMPRESSION` 两类属性，缺少 `comment` 常量，调用方需要硬编码字符串 `"comment"` 才能读写视图注释。

本提交在 `ViewProperties` 中新增 `public static final String COMMENT = "comment";` 常量，与表属性保持命名一致，方便视图相关代码通过类型安全的方式引用该属性键。同时更新对应的解析测试与测试资源 JSON，确保视图元数据 JSON 解析能正确处理包含 `comment` 字段的属性映射。

## 如何达成设计目的

设计思路是：在 `ViewProperties` 中显式声明 `COMMENT` 常量，与现有 `METADATA_COMPRESSION` 等属性并列；随后在视图元数据解析器测试中验证包含 `comment` 键的属性映射能够被正确序列化与反序列化。改动涵盖一个产品代码文件（属性常量定义）、一个测试文件（断言更新）以及一个测试资源 JSON（数据更新），三者协同验证了新属性在端到端路径上的有效性。

## 修改详情

### `core/src/main/java/org/apache/iceberg/view/ViewProperties.java`

**修改目的**：新增 `comment` 属性常量，为视图注释提供类型安全的引用键。

**工作逻辑**：在已有属性常量之后新增一行：

```java
public static final String COMMENT = "comment";
```

该类此前只包含 `VERSION_HISTORY_SIZE`（含默认值常量）与 `METADATA_COMPRESSION`（含默认值常量）。`COMMENT` 仅定义键常量，无对应默认值常量（注释默认为空/不存在，符合"可选属性"语义）。这与表属性 `TableProperties` 中的处理方式保持一致——`comment` 是一个由调用方按需设置的字符串属性。

### `core/src/test/java/org/apache/iceberg/view/TestViewMetadataParser.java`

**修改目的**：在视图元数据解析测试中加入 `comment` 属性，验证解析器对包含该字段的属性映射的正确处理。

**工作逻辑**：在两处构造 `ViewMetadata` 的位置，将原属性映射 `ImmutableMap.of("some-key", "some-value")` 扩展为 `ImmutableMap.of("some-key", "some-value", ViewProperties.COMMENT, "some-comment")`。两处分别在序列化与反序列化（round-trip）测试路径上，确保新属性在 JSON 写出与读入两个方向上都被覆盖。使用 `ViewProperties.COMMENT` 常量引用键，而非硬编码字符串，呼应了产品代码新增的常量。

### `core/src/test/resources/org/apache/iceberg/view/ValidViewMetadata.json`

**修改目的**：更新测试用的合法视图元数据 JSON 资源，使其属性对象包含 `comment` 字段，与解析测试期望对齐。

**工作逻辑**：将 `properties` 由 `{"some-key": "some-value"}` 改为 `{"some-key": "some-value", "comment":  "some-comment"}`（注意 JSON 中 `comment` 与值之间有额外空格，属格式细节，不影响解析）。该资源文件被多个解析测试用作输入样本，加入 `comment` 字段后保证测试覆盖了真实场景中可能出现的视图注释属性。

## 小结

通过在 `ViewProperties` 中新增 `COMMENT` 常量并更新解析测试与样本 JSON，本提交为视图元数据引入了标准 `comment` 属性，与表属性的 `comment` 语义对齐，是 Iceberg 视图规范走向完善的一小步。
