# 提交 0084：Core: Make view metadata properties optional in JSON parser (#8723)

## 提交信息

- **序号**：0084 / 4088
- **哈希**：94edb0e12176775c506a548bf5394183fe8626f9
- **短哈希**：94edb0e12
- **日期**：2023-10-20
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Make view metadata properties optional in JSON parser (#8723)
- **PR/Issue**：#8723

## 总体目的

这个提交修复了 View（视图）元数据 JSON 序列化/反序列化中 `properties`（属性映射）字段的健壮性问题：使 `properties` 在 JSON 解析与写入时都变为可选，与 Iceberg Table 元数据处理 `properties` 的既有约定保持一致。

**背景与动机**：在此提交之前，[`ViewMetadataParser`](../../../../core/src/main/java/org/apache/iceberg/view/ViewMetadataParser.java) 在两个方向上对 `properties` 都做了强制处理：

1. **写入方向**：`write()` 方法无条件调用 `JsonUtil.writeStringMap(PROPERTIES, metadata.properties(), gen)`，即使 `properties` 为空 Map 也会写出 `"properties" : {}` 字段。
2. **读取方向**：`fromJson()` 方法无条件调用 `JsonUtil.getStringMap(PROPERTIES, json)`，而 `JsonUtil.getStringMap` 在 JSON 节点不存在该键时会抛异常。这意味着任何省略了 `properties` 字段的视图元数据 JSON 文件都无法被解析。

这与 Iceberg 的整体设计惯例不符：Table 元数据的 `properties` 一直是可选的，且空 Map 在序列化时通常被省略以保持元数据文件简洁。对于视图而言，很多视图根本不设置任何属性，强制要求 `properties` 字段既增加了无意义的输出，又降低了对第三方/旧格式元数据的兼容性。

**修复内容**：写入时，若 `properties` 为空则不写出该字段；读取时，若 JSON 中不存在 `PROPERTIES` 键则回退为空 Map（`ImmutableMap.of()`）。这使 `properties` 成为真正的可选字段，提升了视图元数据解析的健壮性，并让空属性的视图元数据 JSON 更简洁。这对 View 规范的互操作性很重要——不同引擎写入的视图元数据即使省略 properties 也能被 Iceberg 正确读取。

## 如何达成设计目的

整体设计思路是对称地处理读写两端：写时按需省略，读时容错回退。改动集中在 [`ViewMetadataParser`](../../../../core/src/main/java/org/apache/iceberg/view/ViewMetadataParser.java) 的 `write` 与 `fromJson` 两个方法，并通过新增一个完整的 round-trip 序列化测试验证空 properties 时的写-读一致性。

## 修改详情

### `core/src/main/java/org/apache/iceberg/view/ViewMetadataParser.java`

**修改目的**：使 `properties` 字段在 JSON 写入与读取时都变为可选。

**工作逻辑**：

- 新增 `import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;`。
- **写入侧**：原 `JsonUtil.writeStringMap(PROPERTIES, metadata.properties(), gen);` 被包裹在 `if (!metadata.properties().isEmpty()) { ... }` 中。这样空 properties 的视图元数据 JSON 中将不再出现 `"properties" : {}` 字段，输出更简洁，也与 Table 元数据序列化风格一致。
- **读取侧**：原 `Map<String, String> properties = JsonUtil.getStringMap(PROPERTIES, json);` 改为三元表达式 `Map<String, String> properties = json.has(PROPERTIES) ? JsonUtil.getStringMap(PROPERTIES, json) : ImmutableMap.of();`。即先用 `json.has(PROPERTIES)` 判断键是否存在，存在则正常读取，不存在则回退为不可变空 Map。这避免了对省略 properties 字段的 JSON 抛出解析异常。

### `core/src/test/java/org/apache/iceberg/view/TestViewMetadataParser.java`

**修改目的**：新增 `roundTripSerdeWithoutProperties` 测试，验证空 properties 视图元数据的序列化-反序列化一致性。

**工作逻辑**：测试构造一个未设置任何 properties 的 `ViewMetadata`（assignUUID、setLocation、addSchema、addVersion、setCurrentVersionId），调用 `ViewMetadataParser.toJson(viewMetadata, true)` 序列化为 JSON。期望 JSON 串中不包含 `properties` 字段（验证写入侧按需省略）。随后用 `ViewMetadataParser.fromJson(json)` 反序列化，并用 `usingRecursiveComparison().ignoringFieldsOfTypes(Schema.class).ignoringFields("changes")` 与原对象比对相等（验证读取侧回退为空 Map 后与原对象等价）。该测试同时覆盖了写入省略与读取容错两个方向，是本次修复的回归保护。

## 小结

这个提交让 View 元数据的 `properties` 字段在 JSON 序列化与反序列化中都变为可选，与 Table 元数据的处理惯例对齐，提升了解析健壮性与跨引擎互操作性。
