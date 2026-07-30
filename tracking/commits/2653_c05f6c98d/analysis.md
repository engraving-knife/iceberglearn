# 提交 2653：API: required nested fields within optional structs can produce null (#13804)

## 提交信息

- **序号**：2653 / 4088
- **哈希**：c05f6c98d66a5a05ec5dca0b7a1c28036187317d
- **短哈希**：c05f6c98d
- **日期**：2025-09-19 08:53:17 +0200
- **作者**：Dejan Gvozdenac
- **提交说明**：API: required nested fields within optional structs can produce null (#13804)
- **PR/Issue**：#13804

## 总体目的

本提交修复了一个在嵌套结构体（nested struct）场景下的表达式求值缺陷。问题在于：当一个 required（非空）字段位于一个 optional（可为空）的父级 struct 内时，对该字段的表达式求值实际上可能产生 null 值——因为父级 struct 本身可以为 null，此时访问其内部字段自然得到 null。

修复前的行为是：`BoundReference.producesNull()` 仅检查字段本身的 `isOptional()` 属性。如果一个叶子字段是 required 的，即使它的某个祖先 struct 是 optional 的，`producesNull()` 也会错误地返回 `false`。这会导致基于此判断的过滤逻辑（如 Parquet 的 `isNull`/`notNull` 过滤器）产生错误结果——例如，对 `struct_not_null.int_field`（其中 int_field 是 required）执行 `isNull` 过滤时，由于 struct 本身为 null，该字段实际应为 null，但旧逻辑会认为"required 字段永远不为 null"而跳过该行，导致查询结果不正确。

本提交通过在 Accessor 链中追踪"访问路径上是否存在 optional 字段"，使 `producesNull()` 能够正确反映嵌套 struct 为 null 的情况。

## 如何达成设计目的

整体设计是在字段访问器（Accessor）层面记录访问路径上是否存在 optional 字段，然后在 `BoundReference.producesNull()` 中利用该信息：

1. **Accessor 接口**：新增 `hasOptionalFieldInPath()` 默认方法，返回当前字段或其任何祖先在访问路径中是否为 optional。
2. **Accessor 实现类**：为 `PositionAccessor`、`Position2Accessor`、`Position3Accessor`、`WrappedPositionAccessor` 都增加 `hasOptionalFieldInPath` 字段，在构造时将当前层是否 optional 与被包装 accessor 的结果做"或"运算。
3. **Accessors 工厂方法**：在构建 accessor 时传入字段是否 optional 的信息（`field.isOptional()`）。
4. **BoundReference**：将 `producesNull()` 从仅检查 `field.isOptional()` 改为检查 `accessor.hasOptionalFieldInPath()`。
5. **测试**：新增 `TestBoundReference` 参数化测试覆盖各种嵌套组合，并更新 Parquet Bloom 过滤器和 Spark SQL 测试以验证修复效果。

## 修改详情

### `api/src/main/java/org/apache/iceberg/Accessor.java` (+5/-0 lines)

**修改目的**：在 Accessor 接口中新增 `hasOptionalFieldInPath()` 方法。

**工作逻辑**：新增默认方法 `hasOptionalFieldInPath()`，默认返回 `false`。该方法返回 true 表示当前字段或访问路径中的任何祖先字段是 optional 的。注释说明："Returns true if the current field or any ancestor in the access path is optional."

### `api/src/main/java/org/apache/iceberg/Accessors.java` (+40/-10 lines)

**修改目的**：为所有 Accessor 实现类添加 optional 路径追踪能力。

**工作逻辑**：
- 为 `PositionAccessor`、`Position2Accessor`、`Position3Accessor`、`WrappedPositionAccessor` 四个实现类各增加 `hasOptionalFieldInPath` 布尔字段。
- 每个类的构造器新增 `boolean isOptional` 参数。对于多层 accessor（Position2Accessor、Position3Accessor、WrappedPositionAccessor），`hasOptionalFieldInPath` 的值为当前层 `isOptional` 与被包装 accessor 的 `hasOptionalFieldInPath()` 的逻辑或。
- 每个类实现 `hasOptionalFieldInPath()` 方法返回该字段。
- 工厂方法 `newAccessor` 的签名调整：`newAccessor(int pos, Type type)` 改为 `newAccessor(int pos, boolean isOptional, Type type)`；`newAccessor(int pos, boolean isOptional, Accessor accessor)` 内部调用各实现类构造器时传入 `isOptional`。
- 在 `buildAccessor` 中构建叶子 accessor 时，调用 `newAccessor(i, field.isOptional(), field.type())`，将字段的 optional 属性传入。

### `api/src/main/java/org/apache/iceberg/expressions/BoundReference.java` (+3/-1 lines)

**修改目的**：修正 `producesNull()` 的判断逻辑。

**工作逻辑**：将 `return field.isOptional();` 改为 `return accessor.hasOptionalFieldInPath();`，并添加注释说明："A leaf required field can evaluate to null if it is optional itself or any ancestor on the path is optional."

### `api/src/test/java/org/apache/iceberg/expressions/TestBoundReference.java` (+108/-0 lines, 新文件)

**修改目的**：新增针对 `producesNull()` 在各种嵌套 struct 组合下的参数化测试。

**工作逻辑**：
- `buildSchemaFromOptionalList` 方法根据布尔列表构建嵌套 struct schema，例如 `[false, true, false]` 构造 `s1(required).s2(optional).s3(required)` 的结构。
- `producesNullCases` 提供多组测试参数：从 1 层到 4 层的嵌套，覆盖全 required、全 optional、混合等组合。只要路径中有任何一个 optional struct，`producesNull()` 就应返回 true；只有全 required 时才返回 false。
- `testProducesNull` 验证 `BoundReference.producesNull()` 与预期值一致。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestBloomRowGroupFilter.java` (+3/-5 lines)

**修改目的**：修正 Bloom 过滤器测试中关于 required 嵌套字段的断言。

**工作逻辑**：
- 对 `struct_not_null.int_field`（int_field required，但父 struct 也可为 null 场景）执行 `notNull` 过滤时，断言消息从"Should read: this field is required and are always not-null"改为"Should read: bloom filter doesn't help"。
- 执行 `isNull` 过滤时，断言从"Should skip"（isFalse）改为"Should read"（isTrue），消息改为"required nested field can still be null if any ancestor is optional"。这反映了修复后的正确行为：required 嵌套字段在有 optional 祖先时仍可能为 null，因此不能跳过。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java` (+17/-0 lines)

**修改目的**：新增端到端测试验证修复在 Spark 3.4 中的效果。

**工作逻辑**：新增 `testRequiredNestedFieldInOptionalStructFilter` 测试，创建包含 `STRUCT<street: STRING NOT NULL>` 的表（struct 本身 optional），插入 struct 为 null 和非 null 的行，验证 `WHERE address.street IS NULL` 能正确返回 struct 为 null 的行（id=0）。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java` (+17/-0 lines)

**修改目的**：同上，在 Spark 3.5 中验证。

**工作逻辑**：与 Spark 3.4 测试完全相同。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java` (+17/-0 lines)

**修改目的**：同上，在 Spark 4.0 中验证。

**工作逻辑**：与 Spark 3.4 测试完全相同。

## 总结

本提交修复了一个在嵌套 struct 场景下表达式 null 判断的缺陷。核心改动是在 Accessor 链中追踪访问路径上是否存在 optional 字段，使 `BoundReference.producesNull()` 能正确判断 required 字段在 optional 父级 struct 下仍可能产生 null。修复确保了 `isNull`/`notNull` 过滤器在嵌套 struct 场景下的正确性，避免了错误跳过应读取的行。测试覆盖了 API 层的参数化测试和 Spark SQL 的端到端验证，覆盖 Spark 3.4/3.5/4.0 三个版本。
