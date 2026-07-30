# 提交 1743：API: Reject unknown type for required fields and validate defaults (#12302)

## 提交信息

- **序号**：1743 / 4088
- **哈希**：93ef86112a238b584e2171b11c40cfefcdd8731d
- **短哈希**：93ef86112
- **日期**：2025-02-18 08:21:02 -0800
- **作者**：Ryan Blue
- **提交说明**：API: Reject unknown type for required fields and validate defaults (#12302)
- **PR/Issue**：#12302

## 总体目的

Iceberg 正在引入新的类型系统支持，包括 `UnknownType`（未知类型）和 `VariantType`（变体类型）。`UnknownType` 用于表示尚未确定类型的字段——这种字段的实际类型可能在后续 schema 演进中确定。由于 `UnknownType` 的值未知，它不能作为必填（required）字段的类型，因为必填字段必须有明确的非 null 值。

此前，`NestedField` 的构建器没有校验 `UnknownType` 是否被用于必填字段，这可能导致创建出不合法的 schema（必填字段类型为 UnknownType，但无法提供默认值）。此外，当为字段设置默认值时，`defaultValue.to(type)` 方法在某些类型转换失败时会返回 `null`，但原代码没有检查这种情况，导致默认值被静默忽略。

本提交的目标是：
1. 在 `NestedField` 构建器中拒绝为必填字段使用 `UnknownType`。
2. 在设置默认值时校验类型转换结果不为 null，转换失败时抛出明确的异常。

## 如何达成设计目的

提交通过以下修改达成目标：

1. **必填字段类型校验**：在 `NestedField` 构建器的构造方法中，添加 `Preconditions.checkArgument` 校验，当 `isOptional` 为 `false`（即必填字段）且类型为 `UnknownType` 时，抛出 `IllegalArgumentException`。

2. **默认值转换校验**：在 `defaultType` 方法中，将 `defaultValue.to(type)` 的结果保存到变量 `typedDefault`，然后校验其不为 null。如果为 null（表示类型转换失败），抛出带清晰信息的 `IllegalArgumentException`。

3. **测试适配**：将现有测试中用于必填字段的 `UnknownType` 改为可选字段，并新增测试覆盖新增的校验逻辑。

## 修改详情

### `api/src/main/java/org/apache/iceberg/types/Types.java`（修改, +6/-1 lines）

**修改目的**：在 `NestedField` 构建器中添加 UnknownType 必填字段校验和默认值转换校验。

**工作逻辑**：
1. 在构造方法中，`Preconditions.checkNotNull` 校验之后，新增：
```java
Preconditions.checkArgument(
    isOptional || !type.equals(UnknownType.get()),
    "Cannot create required field with unknown type: %s", name);
```
这确保必填字段（`isOptional == false`）不能使用 `UnknownType`。

2. 在 `defaultType` 方法中，原来的 `return defaultValue.to(type)` 改为：
```java
Literal<?> typedDefault = defaultValue.to(type);
Preconditions.checkArgument(
    typedDefault != null, "Cannot cast default value to %s: %s", type, defaultValue);
return typedDefault;
```
这确保当默认值的类型转换失败（返回 null）时，抛出带清晰信息的异常。

### `api/src/test/java/org/apache/iceberg/TestPartitionSpecValidation.java`（修改, +1/-1 lines）

**修改目的**：将测试 schema 中的 `UnknownType` 字段从必填改为可选。

**工作逻辑**：将 `NestedField.required(8, "u", Types.UnknownType.get())` 改为 `NestedField.optional(8, "u", Types.UnknownType.get())`，以适配新增的必填字段不能使用 UnknownType 的校验。

### `api/src/test/java/org/apache/iceberg/TestSchema.java`（修改, +52/-2 lines）

**修改目的**：新增针对 UnknownType 的兼容性测试，并从通用类型测试列表中移除 UnknownType。

**工作逻辑**：
1. 从 `SUPPORTED_TYPES` 列表中移除 `Types.UnknownType.get()`，因为 UnknownType 需要单独测试（不能用于必填字段）。
2. 新增 `testUnknownSupport` 测试方法，构建一个包含 UnknownType 字段（全部为 optional）的 schema，验证：
   - 在 format version 2 下不兼容（UnknownType 需要 v3 支持），抛出 `IllegalStateException`，错误信息列出所有不兼容的字段路径。
   - 在 format version 3 下兼容，不抛出异常。

### `core/src/test/java/org/apache/iceberg/TestSchemaUpdate.java`（修改, +43 lines）

**修改目的**：测试 schema 更新中添加 UnknownType 字段的各种场景。

**工作逻辑**：新增三个测试方法：
1. `testAddUnknown`：验证可以添加一个 optional 的 UnknownType 字段，结果 schema 符合预期。
2. `testAddUnknownNonNullDefault`：验证为 UnknownType 字段设置非 null 默认值时抛出 `IllegalArgumentException`，消息为 `"Cannot cast default value to unknown: \"string!\""`。
3. `testAddRequiredUnknown`：验证添加 required 的 UnknownType 字段时抛出 `IllegalArgumentException`，消息为 `"Cannot create required field with unknown type: unk"`。

## 小结

- **成效**：成功在 `NestedField` 构建器中添加了两项校验：(1) 必填字段不能使用 UnknownType；(2) 默认值类型转换失败时抛出明确异常而非静默返回 null。这防止了不合法 schema 的创建，并提供了清晰的错误信息。
- **影响范围**：涉及 api 模块的 `Types.NestedField` 构建器和多个测试文件。所有使用 `NestedField.builder()` 创建必填字段且类型为 UnknownType 的代码将抛出异常，需要改为可选字段。
- **回迁到 1.4.x 的注意事项**：此提交是 UnknownType 类型支持的一部分，回迁需要确认 1.4.x 分支已引入 `UnknownType` 类型和 `NestedField.builder()` 构建器。如果 1.4.x 不支持 UnknownType，则不应回迁。如果支持，建议回迁以确保 schema 校验的一致性。注意此提交与提交 1745（Add variant type support）和 1746（Fix CI: Update tests with UnknownType）有关联。
