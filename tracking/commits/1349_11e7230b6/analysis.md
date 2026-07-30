# 提交 1349：API: Removes Explicit Parameterization of Schema Tests (#11444)

## 提交信息

- **序号**：1349 / 4088
- **哈希**：11e7230b678c1dd76993117c7470ce92b63709d7
- **短哈希**：11e7230b6
- **日期**：2024-11-07（Thu Nov 7 09:47:50 2024 -0600）
- **作者**：Russell Spitzer <russell.spitzer@GMAIL.COM>
- **提交说明**：API: Removes Explicit Parameterization of Schema Tests (#11444)
- **PR/Issue**：#11444

## 总体目的

`TestSchema` 此前对 `Schema.checkCompatibility` 的测试使用了硬编码的版本列表与单个 `@Test` 方法：
- `testUnsupportedTimestampNano` 用 `@ValueSource(ints = {1, 2})` 硬编码 v1/v2；
- `testSupportedTimestampNano` 是单个 `@Test`，只测 v3；
- `testUnsupportedInitialDefault`/`testSupportedInitialDefault` 同样硬编码 v1/v2 与单个 v3；
- `testSupportedWriteDefault` 用 `@ValueSource(ints = {1, 2, 3})`。

这种写法的问题：当 Iceberg 引入新的 format version（如 v4）或新的"需要最低版本限制"的类型时，所有这些测试都要手动改版本号与 `@ValueSource` 列表，容易遗漏；且"某类型在哪些版本受支持"这一信息已经存在于 `Schema.MIN_FORMAT_VERSIONS` 与 `Schema.DEFAULT_VALUES_MIN_FORMAT_VERSION` 中，测试却重复硬编码了一份。

本提交把测试改为从 `Schema` 的常量动态推导测试参数：
- 暴露 `Schema.DEFAULT_VALUES_MIN_FORMAT_VERSION` 与 `Schema.MIN_FORMAT_VERSIONS` 为 `@VisibleForTesting` 包级可见；
- 在 `TestHelpers` 中新增 `MAX_FORMAT_VERSION = 3` 与 `ALL_VERSIONS = [1..3]` 常量；
- 在 `TestSchema` 中用 `@MethodSource`/`@FieldSource`（JUnit 5.11 引入）从这些常量生成 `(类型, 版本)` 参数组合，自动覆盖所有受限制类型在所有"低于/不低于最低版本"的版本上的行为。

这样未来新增类型限制或新 format version 时，只需更新 `Schema.MIN_FORMAT_VERSIONS`/`DEFAULT_VALUES_MIN_FORMAT_VERSION`/`MAX_FORMAT_VERSION`，测试会自动扩展覆盖。

## 如何达成设计目的

- **暴露 Schema 内部常量**：把 `DEFAULT_VALUES_MIN_FORMAT_VERSION` 与 `MIN_FORMAT_VERSIONS` 从 `private` 改为包级可见并加 `@VisibleForTesting`，让同包测试可直接引用。
- **集中管理版本常量**：在 `TestHelpers`（api 测试公共工具类）中定义 `MAX_FORMAT_VERSION = 3` 与 `ALL_VERSIONS = IntStream.rangeClosed(1, MAX_FORMAT_VERSION).toArray()`，作为所有 schema 测试的版本上界。
- **类型参数化**：在 `TestSchema` 中定义 `TEST_TYPES = [TimestampNanoType.withoutZone(), TimestampNanoType.withZone()]`（即 `MIN_FORMAT_VERSIONS` 当前覆盖的全部类型），并提供 `generateTypeSchema(Type)` 工具方法：把给定类型放到 schema 的多个嵌套位置（顶层、数组元素、struct optional 字段、struct required 字段、嵌套 struct 数组元素），一次性校验所有嵌套场景。
- **动态生成参数组合**：
  - `unsupportedTypes()`：对每个 `TEST_TYPES` 中的类型，生成所有 `1..MIN_FORMAT_VERSIONS.get(typeId)-1` 的版本（即该类型不支持的版本）。
  - `supportedTypes()`：对每个类型，生成所有 `MIN_FORMAT_VERSIONS.get(typeId)..MAX_FORMAT_VERSION` 的版本（即该类型支持的版本）。
  - `unsupportedInitialDefault`：`IntStream.range(1, DEFAULT_VALUES_MIN_FORMAT_VERSION).toArray()`，即 default value 不支持的版本。
  - `supportedInitialDefault`：`IntStream.rangeClosed(DEFAULT_VALUES_MIN_FORMAT_VERSION, MAX_FORMAT_VERSION).toArray()`。
- **改写测试方法**：
  - `testUnsupportedTypes(Type, int)` 用 `@MethodSource("unsupportedTypes")`，错误信息模板也改为按 `type` 和 `MIN_FORMAT_VERSIONS.get(type.typeId())` 动态填充，不再硬编码 `timestamptz_ns`/`v3`。
  - `testTypeSupported(Type, int)` 用 `@MethodSource("supportedTypes")`。
  - `testUnsupportedInitialDefault(int)` 用 `@FieldSource("unsupportedInitialDefault")`。
  - `testSupportedInitialDefault(int)` 用 `@FieldSource("supportedInitialDefault")`。
  - `testSupportedWriteDefault(int)` 用 `@FieldSource("org.apache.iceberg.TestHelpers#ALL_VERSIONS")`（跨类引用 FieldSource）。

## 修改详情

### `api/src/main/java/org/apache/iceberg/Schema.java`

**修改目的**：暴露最低版本限制常量给测试。

**工作逻辑**：
- 新增 import `org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting`。
- `DEFAULT_VALUES_MIN_FORMAT_VERSION` 由 `private static final` 改为 `@VisibleForTesting static final`（包级可见）。
- `MIN_FORMAT_VERSIONS` 由 `private static final` 改为 `@VisibleForTesting static final`。
- 值不变：`DEFAULT_VALUES_MIN_FORMAT_VERSION = 3`、`MIN_FORMAT_VERSIONS = {TIMESTAMP_NANO: 3}`。

### `api/src/test/java/org/apache/iceberg/TestHelpers.java`

**修改目的**：集中定义 format version 上界与全集。

**工作逻辑**：
```java
public static final int MAX_FORMAT_VERSION = 3;
public static final int[] ALL_VERSIONS = IntStream.rangeClosed(1, MAX_FORMAT_VERSION).toArray();
```
`ALL_VERSIONS = [1, 2, 3]`。引入 `IntStream` 即隐含 import `java.util.stream.IntStream`（文件原已 import）。

### `api/src/test/java/org/apache/iceberg/TestSchema.java`

**修改目的**：用参数化测试取代硬编码版本，并按类型动态生成 schema。

**工作逻辑**：

1. **新增 imports**：`static Schema.DEFAULT_VALUES_MIN_FORMAT_VERSION`、`static Schema.MIN_FORMAT_VERSIONS`、`static TestHelpers.MAX_FORMAT_VERSION`、`java.util.List`、`java.util.stream.IntStream`、`java.util.stream.Stream`、`ImmutableList`、`Type`、`Arguments`、`FieldSource`、`MethodSource`；移除 `org.junit.jupiter.api.Test` 与 `ValueSource`。

2. **TEST_TYPES** 取代原 `TS_NANO_CASES` 静态 schema：
   ```java
   private static final List<Type> TEST_TYPES =
       ImmutableList.of(Types.TimestampNanoType.withoutZone(), Types.TimestampNanoType.withZone());
   ```

3. **generateTypeSchema(Type)**：动态构造一个把给定类型放到 5 个嵌套位置的 schema（top、arr.element、struct.inner_op、struct.inner_req、struct.struct_arr.deep），便于一次性验证所有嵌套形式。

4. **unsupportedTypes()** MethodSource：
   ```java
   return TEST_TYPES.stream().flatMap(type ->
       IntStream.range(1, MIN_FORMAT_VERSIONS.get(type.typeId()))
           .mapToObj(v -> Arguments.of(type, v)));
   ```

5. **testUnsupportedTypes(Type, int)**：用 `@MethodSource("unsupportedTypes")`，错误信息模板用 `%s`/`%s` 占位 type 与 `MIN_FORMAT_VERSIONS.get(type.typeId())`，覆盖 5 个嵌套位置的错误输出。

6. **supportedTypes()** MethodSource：
   ```java
   return TEST_TYPES.stream().flatMap(type ->
       IntStream.rangeClosed(MIN_FORMAT_VERSIONS.get(type.typeId()), MAX_FORMAT_VERSION)
           .mapToObj(v -> Arguments.of(type, v)));
   ```

7. **testTypeSupported(Type, int)**：用 `@MethodSource("supportedTypes")`，断言不抛异常。

8. **unsupportedInitialDefault** 与 **supportedInitialDefault** FieldSource：
   ```java
   private static int[] unsupportedInitialDefault =
       IntStream.range(1, DEFAULT_VALUES_MIN_FORMAT_VERSION).toArray();
   private static int[] supportedInitialDefault =
       IntStream.rangeClosed(DEFAULT_VALUES_MIN_FORMAT_VERSION, MAX_FORMAT_VERSION).toArray();
   ```

9. **testUnsupportedInitialDefault(int)** / **testSupportedInitialDefault(int)** 改用 `@FieldSource`。

10. **testSupportedWriteDefault(int)** 改用 `@FieldSource("org.apache.iceberg.TestHelpers#ALL_VERSIONS")` 跨类引用。

## 小结

- **成效**：`TestSchema` 不再硬编码版本列表与类型，所有 (类型, 版本) 组合由 `Schema` 的限制常量与 `TestHelpers.MAX_FORMAT_VERSION` 自动推导。新增类型限制或新 format version 时，只需更新源码常量，测试自动覆盖。错误信息模板也按类型动态填充，避免与 `Schema.checkCompatibility` 的实际输出脱节。
- **影响范围**：api 模块 `Schema`（仅可见性调整，加 `@VisibleForTesting`）、`TestHelpers`（新增两个常量）、`TestSchema`（重写参数化测试）。无运行时行为变更。
- **回迁到 1.4.x 的注意事项**：
  - 本提交使用 `@FieldSource` 注解，该注解是 **JUnit 5.11** 引入的特性。1.4.x 若使用 JUnit 5.10 或更低版本，**直接 cherry-pick 会编译失败**。
  - 1.4.x 若已升级到 JUnit 5.11+，可考虑回迁以提升测试可维护性；否则需要把 `@FieldSource` 改回 `@ValueSource` 或 `@MethodSource` 形式（失去部分自动推导便利）。
  - 该改动是测试基础设施改进，不修复 bug 也不增加功能，对 1.4.x 运行时无影响，**优先级低**，是否回迁移取决于 1.4.x 的 JUnit 版本与测试维护策略。
  - 若 1.4.x 计划支持 v4 表或新增受版本限制的类型，回迁本提交可让测试自动扩展，值得考虑。
