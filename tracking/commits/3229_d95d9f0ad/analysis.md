# 提交 3229：Core, REST: Add support for overwrite in RegisterTableRequest (#15248)

## 提交信息

- **序号**：3229 / 4088
- **哈希**：d95d9f0ad8baacd1a61331be9c5e09587a89f1f2
- **短哈希**：d95d9f0ad
- **日期**：2026-02-09
- **作者**：Alexandre Dutra
- **提交说明**：Core, REST: Add support for overwrite in RegisterTableRequest (#15248)
- **PR/Issue**：#15248

## 总体目的

本提交为 Iceberg REST Catalog 的 `RegisterTableRequest` 请求新增 `overwrite` 布尔字段，允许客户端在注册表时指定是否覆盖已存在的表。`RegisterTableRequest` 是 REST Catalog API 中用于注册已有表的请求对象，注册操作将一个位于指定 metadata 位置的表注册到 catalog 中。此前注册请求仅包含表名（`name`）和元数据位置（`metadata-location`）两个字段，如果目标位置已存在同名表，注册操作可能会失败或行为不确定。

新增 `overwrite` 字段后，客户端可以在注册时显式声明是否允许覆盖已有表。该字段默认值为 `false`，保持向后兼容——不传该字段时行为不变。当设置为 `true` 时，服务器端应理解为客户端请求覆盖已有表。这一设计遵循了 Iceberg REST API 中常见的"默认安全、显式覆盖"原则。此修改涉及请求接口定义、JSON 序列化/反序列化解析器和对应的单元测试。

## 如何达成设计目的

通过在 `RegisterTableRequest` 接口中使用 Immutables 的 `@Value.Default` 注解添加 `overwrite()` 方法（默认返回 `false`），在 `RegisterTableRequestParser` 中增加对该字段的序列化（仅在值为 `true` 时写入 JSON）和反序列化（通过 `JsonUtil.getBoolOrNull` 读取，非空时设置到 builder）逻辑，并新增测试验证序列化往返（round-trip serde）的正确性。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/requests/RegisterTableRequest.java` (+5/-0 lines)

**修改目的**：在请求接口中定义 `overwrite` 字段及默认值。

**工作逻辑**：
使用 `@Value.Default` 注解添加 `default boolean overwrite()` 方法，返回 `false`。这是 Immutables 框架的惯用模式——接口中用 `@Value.Default` 标注的方法会在生成的 `ImmutableRegisterTableRequest` 中提供默认值，同时允许通过 builder 的 `.overwrite(boolean)` 方法覆盖。`validate()` 方法保持不变，因为 overwrite 是布尔值，无需校验逻辑。

### `core/src/main/java/org/apache/iceberg/rest/requests/RegisterTableRequestParser.java` (+18/-4 lines)

**修改目的**：实现 `overwrite` 字段的 JSON 序列化与反序列化。

**工作逻辑**：
新增常量 `OVERWRITE = "overwrite"` 对应 JSON 字段名。在序列化方法 `toJson` 中，当 `request.overwrite()` 为 `true` 时，通过 `gen.writeBooleanField(OVERWRITE, request.overwrite())` 写入 JSON 字段；为 `false` 时不写入，保持 JSON 简洁并确保向后兼容（旧客户端不发送此字段时反序列化结果仍为 `false`）。在反序列化方法 `fromJson` 中，使用 `JsonUtil.getBoolOrNull(OVERWRITE, json)` 读取字段值，非 `null` 时通过 `builder.overwrite(overwrite)` 设置，`null` 时（字段不存在）使用接口默认值 `false`。builder 由原来的链式直接构建改为先创建 builder 对象、再条件性设置字段、最后 build 的模式。

### `core/src/test/java/org/apache/iceberg/rest/requests/TestRegisterTableRequestParser.java` (+24/-0 lines)

**修改目的**：验证含 `overwrite` 字段的序列化往返正确性。

**工作逻辑**：
新增测试方法 `roundTripSerdeWithOverwrite`，构建一个 `overwrite(true)` 的 `RegisterTableRequest`，验证其序列化后的 JSON 包含 `"overwrite" : true` 字段，且反序列化后重新序列化的结果与原始 JSON 一致（round-trip serde）。测试覆盖了字段存在且为 `true` 的场景，确保序列化/反序列化逻辑的正确性。

## 总结

本提交为 `RegisterTableRequest` 新增了 `overwrite` 字段，使客户端可以在注册表时声明是否覆盖已有表，增强了 REST Catalog 注册操作的灵活性和安全性。设计上默认 `false` 保证向后兼容，仅在显式设置 `true` 时才写入 JSON，遵循了最小化传输原则。
