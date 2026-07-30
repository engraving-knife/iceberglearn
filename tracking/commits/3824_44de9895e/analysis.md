# 提交 3824：API, Core: Add CatalogObjectIdentifier (#16160)

## 提交信息

- **序号**：3824 / 4088
- **哈希**：44de9895e5c442e677d55588766fadf3be4b17e0
- **短哈希**：44de9895e
- **日期**：2026-06-04 15:04:09 -0700
- **作者**：Steven Zhen Wu <stevenz3wu@gmail.com>
- **提交说明**：API, Core: Add CatalogObjectIdentifier (#16160)
- **PR/Issue**：#16160

## 总体目的

本提交为 Iceberg catalog 模块新增一个通用的"catalog 对象标识符"类型 `CatalogObjectIdentifier`，用于在 REST catalog 协议中表示对 catalog 内任意对象（表、视图、命名空间等）的引用。在此之前，Iceberg 已有 `Namespace`（命名空间，层级数组）和 `TableIdentifier`（表标识符，命名空间 + 表名）两个类型，但缺少一个"不区分对象种类、仅表示层级路径"的通用标识符。

随着 REST catalog 协议扩展（如 register view、跨 catalog 迁移、统一对象引用等场景），需要一个能表示任意 catalog 对象层级位置的标识符。`CatalogObjectIdentifier` 在结构上与 `Namespace` 相似（都是有序的层级字符串数组），但语义不同：它明确表示"catalog 内的某个对象"，其对象种类由上下文（端点或配套的类型判别器）决定，而非由标识符结构本身决定。使用独立类型而非复用 `Namespace`，可以避免与未来可能引入的顶层 catalog 名混淆，并在 API 中清晰表达"任意对象"的意图。

本提交同时提供 JSON 序列化/反序列化支持（`CatalogObjectIdentifierParser`）以及 REST 模块的 Jackson 序列化器注册（`RESTSerializers`），使该类型可在 REST 通信中直接使用。

## 如何达成设计目的

设计上采用与 `Namespace`/`TableIdentifier` 一致的风格：
- `CatalogObjectIdentifier` 持有一个 `String[] levels`，提供 `of(String...)` 工厂、`levels()`/`level(int)`/`length()` 访问器，以及 `equals`/`hashCode`/`toString`（用 `.` 连接）。构造时校验非 null 数组、每个 level 非 null、不含 null 字节字符（与 `Namespace` 一致的安全约束）。
- `CatalogObjectIdentifierParser` 提供 `toJson`/`fromJson`，JSON 表示为字符串数组（如 `["accounting", "tax", "paid"]`），复用 `JsonUtil.getStringArray` 解析。
- `RESTSerializers` 注册 `CatalogObjectIdentifierSerializer`/`Deserializer`，使 Jackson 能自动处理该类型。
- 配套单元测试覆盖标识符构造校验与 JSON 往返。

## 修改详情

### `api/src/main/java/org/apache/iceberg/catalog/CatalogObjectIdentifier.java` (+96/-0 lines, new file)

**修改目的**：新增通用 catalog 对象标识符类型。

**工作逻辑**：
- 持有 `String[] levels`，`of(String...)` 工厂构造。
- 构造校验：数组非 null、每个 level 非 null（`checkNotNull`）、不含 null 字节字符 `\u0000`（`CONTAINS_NULL_CHARACTER` 正则谓词）。
- 访问器：`levels()`、`level(int pos)`、`length()`。
- `equals`/`hashCode` 基于 `Arrays.equals`/`Arrays.hashCode`。
- `toString` 用 `.` 连接各级（`DOT.join(levels)`）。
- Javadoc 说明：对象种类由上下文决定，结构上镜像 `Namespace` 但语义不同，独立命名避免与未来顶层 catalog 名混淆。

### `api/src/test/java/org/apache/iceberg/catalog/TestCatalogObjectIdentifier.java` (+101/-0 lines, new file)

**修改目的**：覆盖 `CatalogObjectIdentifier` 的构造与行为。

**工作逻辑**：
测试 `of` 工厂、`levels`/`level`/`length` 访问器、`equals`/`hashCode`/`toString`，以及校验异常（null 数组、null level、含 null 字节字符）。

### `core/src/main/java/org/apache/iceberg/catalog/CatalogObjectIdentifierParser.java` (+62/-0 lines, new file)

**修改目的**：提供 `CatalogObjectIdentifier` 的 JSON 序列化/反序列化。

**工作逻辑**：
- `toJson(identifier[, pretty])` 与 `toJson(identifier, JsonGenerator)`：将 levels 写为 JSON 数组：
```java
generator.writeArray(identifier.levels(), 0, identifier.length());
```
- `fromJson(String)` 与 `fromJson(JsonNode)`：用 `JsonUtil.getStringArray(node)` 解析数组并构造标识符。
- JSON 表示示例：`CatalogObjectIdentifier.of("accounting", "tax", "paid")` 序列化为 `["accounting", "tax", "paid"]`。

### `core/src/main/java/org/apache/iceberg/rest/RESTSerializers.java` (+24/-0 lines)

**修改目的**：在 REST 模块的 Jackson 序列化器注册表中注册 `CatalogObjectIdentifier` 的 Serializer/Deserializer。

**工作逻辑**：
- import `CatalogObjectIdentifier` 与 `CatalogObjectIdentifierParser`。
- 在 `RESTSerializers` 的 module 注册中添加：
```java
.addSerializer(CatalogObjectIdentifier.class, new CatalogObjectIdentifierSerializer())
.addDeserializer(CatalogObjectIdentifier.class, new CatalogObjectIdentifierDeserializer())
```
- 新增两个内部静态类 `CatalogObjectIdentifierSerializer`/`Deserializer`，分别委托给 `CatalogObjectIdentifierParser.toJson`/`fromJson`。

### `core/src/test/java/org/apache/iceberg/catalog/TestCatalogObjectIdentifierParser.java` (+101/-0 lines, new file)

**修改目的**：覆盖 JSON 序列化/反序列化的往返正确性与边界情况。

**工作逻辑**：
测试 `toJson`/`fromJson` 的往返、多层级、空数组、null/空字符串输入校验等。

## 总结

本提交为 Iceberg catalog 协议层新增了通用的 `CatalogObjectIdentifier` 类型及其 JSON/REST 序列化支持，为 REST catalog 协议中引用任意 catalog 对象（表、视图、命名空间等）提供了统一标识符。该类型结构上镜像 `Namespace` 但语义独立，避免类型混淆。配套测试覆盖构造校验与序列化往返。这是 REST catalog 协议演进的基础设施性工作，为后续如 register view、跨 catalog 迁移等特性铺路。
