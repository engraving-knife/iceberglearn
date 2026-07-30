# 提交 1768：API: Move variant to API and add extract expression (#12304)

## 提交信息

- **序号**：1768 / 4088
- **哈希**：d4fe23a154d4324eeb6ef4753f50834dc4804bd7
- **短哈希**：d4fe23a15
- **日期**：2025-02-20 17:37:56 -0800
- **作者**：Ryan Blue
- **提交说明**：API: Move variant to API and add extract expression (#12304)
- **PR/Issue**：#12304

## 总体目的

本提交在 Iceberg API 模块中新增了 `extract` 表达式，用于从 Variant 类型字段中提取嵌套路径的值。Variant 是 Iceberg 正在引入的新数据类型，用于存储半结构化数据（类似 JSON 或 VARIANT 类型）。`extract` 表达式允许用户通过 JSONPath 风格的路径从 Variant 字段中提取特定字段的值，并指定提取值的类型。

此外，本提交还改进了表达式绑定（binding）机制，引入了 `producesNull()` 方法来更精确地判断一个表达式是否可能产生 null 值。这使得 `IS_NULL` 和 `NOT_NULL` 谓词的绑定逻辑更加准确——不再仅依赖字段是否为 `isRequired()`，而是综合考虑表达式本身的 null 产生可能性。同时，对 `STARTS_WITH`/`NOT_STARTS_WITH` 谓词添加了类型验证，确保它们只应用于字符串类型的表达式。

提交标题中的 "Move variant to API" 指的是将 Variant 类型相关的表达式支持添加到 API 模块，使表达式系统能够处理 Variant 类型字段。

## 如何达成设计目的

提交通过以下方式实现目标：

1. **新增 `extract` 表达式**：创建 `UnboundExtract` 和 `BoundExtract` 类，以及 `Expressions.extract()` 工厂方法，支持从 Variant 字段中按路径提取值。
2. **新增 `PathUtil` 工具类**：解析和验证 JSONPath 风格的路径字符串，确保路径格式合法。
3. **引入 `producesNull()` 方法**：在 `BoundTerm` 接口中新增默认方法，由 `BoundReference`、`BoundTransform` 等实现类覆盖，提供更精确的 null 判断。
4. **改进谓词绑定逻辑**：`UnboundPredicate` 中的 `IS_NULL`/`NOT_NULL` 绑定使用 `producesNull()` 代替字段 `isRequired()` 检查，并添加对 `UnknownType` 的特殊处理。`STARTS_WITH`/`NOT_STARTS_WITH` 添加类型验证。
5. **添加全面的测试**：新增 `TestPathParsing` 测试类和大量 `TestExpressionBinding` 测试用例。

## 修改详情

### `api/src/main/java/org/apache/iceberg/expressions/Expressions.java`（修改, +4 lines）

**修改目的**：添加 `extract` 工厂方法。

**工作逻辑**：新增静态方法 `extract(String name, String path, String type)`，创建 `UnboundExtract` 实例。参数 `name` 是字段名，`path` 是 JSONPath 风格的路径（如 `$.field.subfield`），`type` 是提取值的类型字符串。

### `api/src/main/java/org/apache/iceberg/expressions/UnboundExtract.java`（新增, +67 lines）

**修改目的**：创建未绑定的 extract 表达式。

**工作逻辑**：`UnboundExtract<T>` 实现 `UnboundTerm<T>` 接口，包含三个字段：
- `ref`：字段引用（`NamedReference`）
- `path`：提取路径
- `type`：提取值的类型（`Type.PrimitiveType`，通过 `Types.fromPrimitiveString(type)` 解析）

构造函数中调用 `PathUtil.parse(path)` 验证路径格式。`bind` 方法将引用绑定到结构类型，验证引用的字段类型必须是 `VariantType`，且提取类型不能是 `UnknownType`，然后创建 `BoundExtract` 实例。

### `api/src/main/java/org/apache/iceberg/expressions/BoundExtract.java`（新增, +75 lines）

**修改目的**：创建已绑定的 extract 表达式。

**工作逻辑**：`BoundExtract<T>` 实现 `BoundTerm<T>` 接口，包含已绑定的引用、路径和类型。提供 `path()` 和 `fullFieldName()` 方法（后者通过 `PathUtil.parse` 将路径转换为点分隔的字段名）。`eval` 方法抛出 `UnsupportedOperationException`，因为 extract 表达式的求值需要引擎层实现。`isEquivalentTo` 方法比较引用、路径和类型是否一致。

### `api/src/main/java/org/apache/iceberg/expressions/PathUtil.java`（新增, +64 lines）

**修改目的**：解析和验证 JSONPath 风格的路径字符串。

**工作逻辑**：`parse` 方法接受路径字符串并返回字段名列表。验证规则包括：
- 路径不能为 null
- 不支持方括号（`[`/`]`）、通配符（`*`）和递归下降（`..`）
- 路径必须以 `$`（根）开头
- 每个字段名必须符合 RFC 9535 的成员名称简写规范（字母/下划线开头，可包含字母/数字/下划线和 Unicode 字符）

路径以 `.` 分割，返回根之后的所有部分作为字段名列表。

### `api/src/main/java/org/apache/iceberg/expressions/BoundTerm.java`（修改, +5 lines）

**修改目的**：添加 `producesNull()` 默认方法。

**工作逻辑**：新增 `default boolean producesNull()` 方法，默认返回 `true`（保守假设可能产生 null）。子类可以覆盖此方法提供更精确的判断。

### `api/src/main/java/org/apache/iceberg/expressions/BoundReference.java`（修改, +5 lines）

**修改目的**：实现 `producesNull()` 方法。

**工作逻辑**：覆盖 `producesNull()` 返回 `field.isOptional()`，即字段是否为可选（可空）。必需字段的引用不会产生 null。

### `api/src/main/java/org/apache/iceberg/expressions/BoundTransform.java`（修改, +7 lines）

**修改目的**：实现 `producesNull()` 方法。

**工作逻辑**：覆盖 `producesNull()` 返回 `ref.producesNull() || !transform.preservesOrder()`。变换表达式在两种情况下可能产生 null：引用本身可空，或变换不保持顺序（非顺序保持的变换可能将非 null 输入映射为 null）。

### `api/src/main/java/org/apache/iceberg/expressions/UnboundPredicate.java`（修改, +11/-2 lines）

**修改目的**：改进 `IS_NULL`/`NOT_NULL` 绑定逻辑，添加 `STARTS_WITH`/`NOT_STARTS_WITH` 类型验证。

**工作逻辑**：

1. **IS_NULL**：将 `boundTerm.ref().field().isRequired()` 检查改为 `!boundTerm.producesNull()`。如果表达式不会产生 null，则 `IS_NULL` 总是为 false。新增对 `UnknownType` 的处理：如果类型是 Unknown，则 `IS_NULL` 总是为 true。

2. **NOT_NULL**：类似的修改。如果表达式不会产生 null，则 `NOT_NULL` 总是为 true。如果类型是 Unknown，则 `NOT_NULL` 总是为 false。

3. **STARTS_WITH/NOT_STARTS_WITH**：在 `bindLiteralOperation` 中添加类型验证，确保表达式类型必须是 `StringType`，否则抛出 `ValidationException`。

### `api/src/test/java/org/apache/iceberg/expressions/TestExpressionBinding.java`（修改, +210/-3 lines）

**修改目的**：为 extract 表达式和改进的 null 绑定逻辑添加测试。

**工作逻辑**：扩展测试结构类型，新增 Variant 类型字段 `var`、可选整数字段 `nullable` 和 Unknown 类型字段 `always_null`。新增测试覆盖：
- `IS_NULL`/`NOT_NULL` 对 Unknown 类型、可选字段、必需字段的行为
- 变换表达式的 `IS_NULL`/`NOT_NULL` 行为（顺序保持 vs 非顺序保持）
- `extract` 表达式的绑定和路径验证
- `STARTS_WITH`/`NOT_STARTS_WITH` 对非字符串类型的验证错误

### `api/src/test/java/org/apache/iceberg/expressions/TestPathParsing.java`（新增, +71 lines）

**修改目的**：为 `PathUtil` 的路径解析和验证添加测试。

**工作逻辑**：测试合法路径（如 `$.field`、`$.a.b.c`、`$.field_with_underscore`）和非法路径（如 null、不含 `$` 前缀、包含方括号/通配符/递归下降、非法字符等），验证解析结果和异常抛出。

## 小结

- **成效**：新增了 `extract` 表达式支持从 Variant 类型字段中提取嵌套值，改进了表达式系统的 null 判断逻辑和类型验证，为 Iceberg 的 Variant 类型支持奠定了 API 基础。
- **影响范围**：涉及 API 模块的表达式系统，新增 4 个类/方法，修改 4 个现有类，添加 2 个测试类。变更面向未来功能（Variant 类型），对现有功能的影响主要是 `IS_NULL`/`NOT_NULL` 绑定逻辑的改进。
- **回迁到 1.4.x 的注意事项**：不建议回迁。此提交引入了 Variant 类型相关的新功能，1.4.x 分支可能尚未支持 Variant 类型（`Types.VariantType` 可能不存在）。`producesNull()` 方法的引入虽然改进了 null 判断逻辑，但会改变 `IS_NULL`/`NOT_NULL` 的绑定行为，可能影响 1.4.x 分支现有的查询优化结果。如果 1.4.x 分支不需要 Variant 支持，此提交不应回迁。
