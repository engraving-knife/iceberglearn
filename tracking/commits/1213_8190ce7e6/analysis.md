# 提交 1213：API, Core: Add default value APIs and Avro implementation (#9502)

## 提交信息

- **序号**：1213 / 4088
- **哈希**：8190ce7e6b66656ccb283859cd14ecca97063230
- **短哈希**：8190ce7e6
- **日期**：2024-10-04（Fri Oct 4 14:56:33 2024 -0700）
- **作者**：Walaa Eldin Moustafa <wmoustafa@linkedin.com>
- **提交说明**：API, Core: Add default value APIs and Avro implementation (#9502)
- **PR/Issue**：#9502
- **共同作者**：Walaa Eldin Moustafa <wmoustaf@wmoustaf-mn2.linkedin.biz>、Ryan Blue <blue@apache.org>

## 总体目的

Iceberg 规范在 v3 引入列默认值（default value）支持，允许在 schema 演进新增字段时为该字段声明 `initial-default`（仅在该字段新增时用于回填历史数据）和 `write-default`（写入时未提供该字段则使用此默认值），从而实现 SQL 风格的默认值语义而无需重写既有数据文件。本提交在 Iceberg 的 Java API 与 Core 层落地该能力：

1. **API 层**：为 `Types.NestedField` 增加 `initialDefault()` 与 `writeDefault()` 访问器，并提供 Builder 模式以便构造带默认值的字段；同时新增 `Expressions.lit(T)` 工具方法，用于把任意 Java 对象转换为 `Literal`，从而对默认值进行类型校验与转换。
2. **Core 层**：在 Avro 读取器 `GenericAvroReader` 中，当读取端 schema 期望某字段但数据文件中缺失该字段时，若该字段声明了 `initialDefault`，则用该默认值填充，而不再回退为 `null`（或抛出"missing required field"）。
3. **测试**：新增 `TestReadDefaultValues`，覆盖所有 Iceberg 原子类型在"列缺失时使用默认值"和"显式值不被默认值覆盖"两种场景。

由于 `NestedField` 新增了两个 `Object` 字段，其序列化布局发生变化，故在 `.palantir/revapi.yml` 中登记为 1.4.0 的已接受 API 中断。

## 如何达成设计目的

- **类型校验**：默认值在 `NestedField` 构造时通过 `castDefault(Object, Type)` 进行校验与转换。对于嵌套类型（struct/list/map），默认值必须为 `null`，否则抛 `IllegalArgumentException`；对于原子类型，调用 `Expressions.lit(defaultValue).to(type).value()` 将 Java 对象转换为对应 Iceberg 类型的内部表示（例如 `String` → `CharSequence`、`Integer` → `Integer`、日期字符串 → `int` 偏移天数等）。
- **Builder 模式**：新增 `Types.NestedField.Builder`，提供 `withId`、`ofType`、`withDoc`、`withInitialDefault`、`withWriteDefault`、`build` 等方法，并支持 `Types.NestedField.from(field)` 从既有字段拷贝。同时提供 `Types.NestedField.required(name)` / `optional(name)` 工厂方法返回 Builder。旧的 `withFieldId(int)` 标记为 `@Deprecated`，将在 2.0.0 移除，引导用户迁移到 `Builder#withId(int)`。
- **Avro 读取回填**：`GenericAvroReader` 在构造 read plan 时，对于"期望存在但文件中缺失"的字段，优先级为：常量 → `field.initialDefault()` → `IS_DELETED` 元数据列 → `ROW_POSITION` 元数据列 → 可空字段填 `null` → 必填字段抛异常。这样历史数据文件中不存在的列，读取时会被默认值填充，实现前向兼容。
- **API 兼容性登记**：`.palantir/revapi.yml` 在 `1.4.0` 段下为 `iceberg-api` 增加 `java.class.defaultSerializationChanged` 条目，明确 `NestedField` 因新增字段导致默认序列化 UID 变化，属于"为添加默认值 API 而接受的中断"。

## 修改详情

### `.palantir/revapi.yml`

**修改目的**：登记 `NestedField` 序列化布局变化为可接受的中断。

**工作逻辑**：在 `1.4.0` 版本段下，为 `org.apache.iceberg:iceberg-api` 模块新增一条 `java.class.defaultSerializationChanged` 规则，old/new 均为 `class org.apache.iceberg.types.Types.NestedField`，justification 为 "Add default value APIs."。这告诉 revapi（API 兼容性检查工具）此变化是已知且可接受的，不会让构建失败。

### `api/src/main/java/org/apache/iceberg/expressions/Expressions.java`

**修改目的**：新增 `lit(T)` 静态工厂方法。

**工作逻辑**：新增公开静态方法 `public static <T> Literal<T> lit(T value)`，内部委托 `Literals.from(value)`。这是默认值类型转换的入口：`castDefault` 调用 `Expressions.lit(defaultValue).to(type).value()`，把 Java 原生对象（如 `String "2007-12-03"`）转换为 Iceberg 内部类型（如 `Integer` 日期偏移）。该方法同时也是一个面向用户的便捷 API，用于在表达式中构造字面量。

### `api/src/main/java/org/apache/iceberg/types/Types.java`

**修改目的**：为 `NestedField` 增加默认值字段、Builder 模式与访问器。

**工作逻辑**：

1. **字段扩展**：`NestedField` 新增两个 `private final Object initialDefault` 与 `writeDefault` 字段。私有构造函数签名扩展为接收这两个参数，并在构造时调用 `castDefault(initialDefault, type)` 与 `castDefault(writeDefault, type)` 进行校验转换后赋值。
2. **`castDefault(Object, Type)` 私有静态方法**：
   - 若 `type.isNestedType()` 且 `defaultValue != null`，抛异常（嵌套类型不允许非 null 默认值）。
   - 若 `defaultValue != null`，返回 `Expressions.lit(defaultValue).to(type).value()`。
   - 否则返回 `null`。
3. **静态工厂方法更新**：`optional`、`required`、`of` 等既有工厂方法全部改为向新构造函数传入 `null, null` 作为默认值，保持向后兼容。
4. **Builder 模式**：新增 `public static class Builder`，含 `isOptional`、`name`、`id`、`type`、`doc`、`initialDefault`、`writeDefault` 字段，提供链式 setter 与 `build()`。同时新增 `Types.NestedField.from(field)`（从既有字段拷贝）、`Types.NestedField.required(name)`、`Types.NestedField.optional(name)` 三个工厂方法返回 Builder。
5. **访问器**：新增 `public Object initialDefault()` 与 `public Object writeDefault()`。
6. **既有方法更新**：`asOptional()`、`asRequired()`、`withFieldId(int)` 在创建新 `NestedField` 时透传 `initialDefault` 与 `writeDefault`，保证转换不丢失默认值。
7. **废弃标记**：`withFieldId(int)` 添加 `@Deprecated` 与 Javadoc 注释，提示 2.0.0 移除，改用 `Builder#withId(int)`。

### `core/src/main/java/org/apache/iceberg/avro/GenericAvroReader.java`

**修改目的**：在 Avro 读取时为缺失字段回填 `initialDefault`。

**工作逻辑**：

1. 两个 `create` 工厂方法与构造函数的参数名从 `schema`/`readSchema` 重命名为 `expectedSchema`/`readSchema`，仅为可读性，无行为变化。
2. 在 `record(...)` 方法中处理"期望存在但文件中缺失"字段的逻辑里，新增一条分支：`else if (field.initialDefault() != null)` 时，向 readPlan 添加 `Pair.of(pos, ValueReaders.constant(field.initialDefault()))`，即用默认值常量填充该位置。该分支位于"显式常量"之后、"IS_DELETED 元数据列"之前，优先级合理。

### `core/src/test/java/org/apache/iceberg/avro/TestReadDefaultValues.java`（新文件）

**修改目的**：验证默认值读取行为。

**工作逻辑**：新增测试类，含一个 `TYPES_WITH_DEFAULTS` 二维数组，覆盖 Boolean、Integer、Long、Float、Double、Date、Time、Timestamp（带/不带时区）、String、UUID、Fixed、Binary、Decimal（含 scale=0）等所有原子类型，每项给出该类型的默认值 JSON 表示。

- `testDefaultAppliedWhenMissingColumn`：写入端 schema 只有 `written` 列，读取端 schema 额外包含 `defaulted` 列并设置 `initialDefault`。验证读取时 `defaulted` 列被填充为默认值。
- `testDefaultDoesNotOverrideExplicitValue`：写入端 schema 与读取端一致，`written_2` 列在写入时显式为 `null`。验证读取结果仍为 `null`，默认值不覆盖显式写入的值。

## 小结

- **成效**：Iceberg Java 实现落地了规范 v3 的列默认值能力。用户可通过 `Types.NestedField.optional(name).withId(..).ofType(..).withInitialDefault(..).build()` 声明默认值；Avro 读取器在文件缺少该列时自动回填默认值，实现 schema 演进的前向兼容。
- **影响范围**：API 模块新增 2 个字段、1 个 Builder、若干工厂与访问器方法、1 个公开 `Expressions.lit` 方法；Core 模块 Avro 读取器新增 1 个分支；新增 1 个测试类（166 行）。`NestedField` 序列化布局变化已登记。
- **回迁到 1.4.x 的注意事项**：
  - 此提交修改了 `NestedField` 的字段布局与构造函数签名，属于 **API 二进制不兼容**变更（虽已通过 revapi 登记为接受），回迁到 1.4.x 会破坏 1.4.x 既有的二进制兼容承诺。1.4.x 作为已发布维护分支，一般不引入此类 API 形状变更。
  - 默认值能力属于 v3 spec 特性，1.4.x 若不完整支持 v3 spec，单独回迁此提交意义有限且可能引入半成品。
  - 若 1.4.x 确有需求（例如需要读取 v3 表的默认值），需评估是否同时回迁后续的 spec v3 类型提升（提交 1219）等关联改动，并接受 `NestedField` 序列化变化对下游依赖的影响。**默认不建议回迁**。
