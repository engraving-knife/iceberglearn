# 提交 3815：Core: v4 table metadata location should be optional (#16572)

## 提交信息

- **序号**：3815 / 4088
- **哈希**：687c58fc7ca80a595c39c64bfe9bace023fcd39d
- **短哈希**：687c58fc7
- **日期**：2026-06-01 13:27:40 -0700
- **作者**：Alex Stephen <1325798+rambleraptor@users.noreply.github.com>
- **提交说明**：Core: v4 table metadata location should be optional (#16572)
- **PR/Issue**：#16572
- **协作者**：Anoop Johnson

## 总体目的

本提交实现 Iceberg 表格式 v4 的一个规范变更：在 v4 表元数据中，`location`（表位置）字段由"必需"改为"可选"。在 v1-v3 表元数据中，`location` 一直是必需字段，因为表的文件路径、manifest 路径等都依赖表位置作为前缀。然而在 v4 中，为了支持更灵活的部署场景（例如由 catalog 完全控制文件布局、表无固定根位置等），规范将 location 设为可选。

同时，本提交引入一个配套约束：当 v4 元数据中提供 location 时，它必须是绝对路径（即包含 scheme，如 `s3://`、`file://`），以避免相对路径带来的歧义。这是 v4 location 可选化后的必要护栏——既然 location 不再强制存在，那么一旦存在就应当是自描述的绝对位置，防止依赖隐式当前工作目录的相对路径。

此外，为了让测试代码能在 location 必须为绝对路径的新约束下继续工作，本提交还顺手增强了 `Files.localOutput` 使其能处理 `file:` 前缀的路径，并将多个模块测试中原本使用相对路径 location 的地方改为带 scheme 的绝对路径（如 `file://location`），以及把 `new File(table.location())` 改为 `new File(URI.create(table.location()))` 以正确解析带 scheme 的路径。

## 如何达成设计目的

设计上分三层：第一层在 `TableMetadata` 构造时根据 `formatVersion` 判断 location 是否可选——v4 及以上可选，v1-v3 仍必需；并且当 v4 提供 location 时校验其必须含 scheme（绝对路径）。第二层在 `TableMetadataParser` 序列化/反序列化时，序列化遇到 null location 则跳过该字段（不写 JSON null），反序列化时 v4 用 `getStringOrNull` 而 v1-v3 用必填的 `getString`。第三层是测试与工具适配：将 `LocationUtil.hasScheme` 从 private 提升为 public 供校验使用，增强 `Files.localOutput` 处理 `file:` 前缀，并把各模块测试中的相对路径 location 改为绝对路径。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableMetadata.java` (+11/-0 lines)

**修改目的**：在表元数据构造时按版本校验 location 的必需性与绝对性。

**工作逻辑**：
新增常量 `MIN_FORMAT_VERSION_OPTIONAL_LOCATION = 4`，并在构造校验中加入两条规则：
```java
boolean locationOptional = formatVersion >= MIN_FORMAT_VERSION_OPTIONAL_LOCATION;
Preconditions.checkArgument(
    locationOptional || location != null,
    "Table location is required in format v%s", formatVersion);
Preconditions.checkArgument(
    !locationOptional || location == null || LocationUtil.hasScheme(location),
    "Invalid table location in format v%s, must be absolute: %s", formatVersion, location);
```
即 v1-v3 必须有 location；v4 及以上 location 可为 null，但若提供则必须带 scheme。

### `core/src/main/java/org/apache/iceberg/TableMetadataParser.java` (+6/-2 lines)

**修改目的**：让序列化与反序列化支持可选 location。

**工作逻辑**：
- 序列化时，location 为 null 则跳过字段（不写 JSON null）：
```java
if (metadata.location() != null) {
  generator.writeStringField(LOCATION, metadata.location());
}
```
- 反序列化时，v4 及以上用 `getStringOrNull`，v1-v3 仍用必填的 `getString`：
```java
String location =
    formatVersion >= TableMetadata.MIN_FORMAT_VERSION_OPTIONAL_LOCATION
        ? JsonUtil.getStringOrNull(LOCATION, node)
        : JsonUtil.getString(LOCATION, node);
```

### `core/src/main/java/org/apache/iceberg/util/LocationUtil.java` (+1/-1 lines)

**修改目的**：将 `hasScheme` 方法提升为 public，供 `TableMetadata` 校验使用。

**工作逻辑**：
将 `private static boolean hasScheme(String location)` 改为 `public static boolean hasScheme(String location)`，方法逻辑不变（按 RFC 3986 检测 scheme 前缀）。

### `api/src/main/java/org/apache/iceberg/Files.java` (+3/-0 lines)

**修改目的**：让 `localOutput` 能处理带 `file:` 前缀的路径，便于测试使用绝对 `file://` location。

**工作逻辑**：
在 `localOutput(String)` 开头增加对 `file:` 前缀的剥离：
```java
if (file.startsWith("file:")) {
  return localOutput(new File(file.replaceFirst("file:", "")));
}
```

### `core/src/test/java/org/apache/iceberg/TestTableMetadata.java` (+78/-3 lines)

**修改目的**：覆盖 v1-v3 location 必需、v4 location 可选、v4 location 必须绝对等行为。

**工作逻辑**：
新增四个参数化/普通测试：
- `testLocationRequiredBeforeV4`：v1-v3 用 null location 构造抛 "Table location is required"。
- `testParserRequiresLocationBeforeV4`：从 JSON 中删除 location 字段后反序列化 v1-v3 抛错。
- `testLocationOptionalInV4`：v4 用 null location 构造成功，序列化 JSON 不含 `"location"`，往返成功。
- `testV4LocationMustBeAbsolute`：v4 用相对路径 `relative/path` 构造抛 "Invalid table location in format v4, must be absolute"。

### `core/src/test/java/org/apache/iceberg/TestRemoveSnapshots.java` (+5/-5 lines)

**修改目的**：适配 v4 location 必须绝对的新约束，把 `new File(table.location())` 改为 `new File(URI.create(table.location()))`。

**工作逻辑**：因为 table.location() 现在带 scheme（如 `file:...`），直接 `new File(...)` 会把 scheme 当成路径一部分，需用 `URI.create` 解析。共 4 处改动。

### `core/src/test/java/org/apache/iceberg/TestSequenceNumberForV2Table.java` (+6/-2 lines)

**修改目的**：适配 location 绝对路径约束。

### `core/src/test/java/org/apache/iceberg/TestTables.java` (+5/-3 lines)

**修改目的**：测试工具类适配 location 绝对路径约束。

### `core/src/test/java/org/apache/iceberg/rest/responses/TestLoadTableResponseParser.java` (+2/-2 lines)

**修改目的**：REST 响应解析测试中 location 改为绝对路径（`file://location`），并同步预期 JSON 字符串。

### `core/src/test/java/org/apache/iceberg/util/TestHashWriter.java` (+1/-1 lines)

**修改目的**：适配 location 绝对路径。

### `flink/v1.20/.../TestIcebergCommitter.java`、`flink/v2.0/.../TestIcebergCommitter.java`、`flink/v2.1/.../TestIcebergCommitter.java`（各 +4/-2 lines）

**修改目的**：三个 Flink 版本的 committer 测试适配 location 绝对路径约束。

### `hive/src/test/java/org/apache/iceberg/hive/TestHiveCatalog.java` (+1/-1 lines)

**修改目的**：Hive catalog 测试适配 location 绝对路径约束。

### `nessie/src/test/java/org/apache/iceberg/nessie/TestNessieIcebergClient.java` (+1/-1 lines)

**修改目的**：Nessie catalog 测试适配 location 绝对路径约束。

### `spark/v3.5/.../TestRemoveOrphanFilesAction.java`、`spark/v4.0/...`、`spark/v4.1/...`（各 +5/-5 lines）

**修改目的**：三个 Spark 版本的 orphan files 清理测试适配 location 绝对路径约束。

### `spark/v3.5/.../source/TestTables.java`、`spark/v4.0/...`、`spark/v4.1/...`（各 +4/-4 lines）

**修改目的**：三个 Spark 版本的测试工具类适配 location 绝对路径约束。

## 总结

本提交是 Iceberg v4 表格式演进的一步：将 `location` 从必需字段改为可选，并要求 v4 中提供的 location 必须是绝对路径。这一变更使 v4 表能支持由 catalog 完全控制文件布局的场景，增强了格式的灵活性。改动覆盖核心元数据类、解析器、工具类以及跨 Flink、Spark、Hive、Nessie 多模块的测试适配，体现了 v4 规范变更的广泛影响。配套的绝对路径校验为可选 location 提供了必要的护栏，避免相对路径引入的歧义。
