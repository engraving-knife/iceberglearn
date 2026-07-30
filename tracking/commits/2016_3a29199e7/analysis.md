# 提交 2016：Core: Use ALL_VERSIONS constant in TestBase (#12748)

## 提交信息

- **序号**：2016 / 4088
- **哈希**：3a29199e73f2e9ae0f8f92a1a0732a338c66aa0d
- **短哈希**：3a29199e7
- **日期**：2025-04-18 18:28:51 -0500
- **作者**：sullis
- **提交说明**：Core: Use ALL_VERSIONS constant in TestBase (#12748)
- **PR/Issue**：#12748

## 总体目的

这个提交是对测试代码的小型重构，将 `TestHelpers.ALL_VERSIONS` 常量的类型从 `int[]` 改为 `List<Integer>`，并在多个测试基类中统一使用该常量，消除硬编码的格式版本列表和冗余的类型转换代码。

此前 `ALL_VERSIONS` 是一个 `int[]` 数组，由 `IntStream.rangeClosed(1, MAX_FORMAT_VERSION).toArray()` 生成。在使用时，测试代码需要通过 `Ints.asList(TestHelpers.ALL_VERSIONS)` 将其转换为 `List<Integer>`，或者直接硬编码 `Arrays.asList(1, 2, 3)`。这种方式不够直观，且硬编码的版本列表与 `MAX_FORMAT_VERSION` 常量不同步，当新增格式版本时需要多处修改。

本提交将 `ALL_VERSIONS` 直接改为 `List<Integer>` 类型，使测试代码可以直接引用，无需额外转换，并消除了硬编码的版本列表。

## 如何达成设计目的

1. 将 `TestHelpers.ALL_VERSIONS` 的类型从 `int[]` 改为 `List<Integer>`，使用 `boxed().collect(Collectors.toUnmodifiableList())` 生成不可变列表。
2. 在 `TestBase` 中将 `parameters()` 方法重命名为 `formatVersions()`，返回 `ALL_VERSIONS` 而非硬编码的 `Arrays.asList(1, 2, 3)`。
3. 在 `TestRewriteFiles` 和 `TestRowLineageMetadata` 中直接使用 `ALL_VERSIONS`，移除 `Ints.asList()` 转换。

## 修改详情

### `api/src/test/java/org/apache/iceberg/TestHelpers.java` (修改, +3/-1 lines)

**修改目的**：将 `ALL_VERSIONS` 常量类型从 `int[]` 改为 `List<Integer>`。

**工作逻辑**：
将 `public static final int[] ALL_VERSIONS = IntStream.rangeClosed(1, MAX_FORMAT_VERSION).toArray()` 改为 `public static final List<Integer> ALL_VERSIONS = IntStream.rangeClosed(1, MAX_FORMAT_VERSION).boxed().collect(Collectors.toUnmodifiableList())`。新增 `Collectors` 的 import。

### `core/src/test/java/org/apache/iceberg/TestBase.java` (修改, +3/-2 lines)

**修改目的**：使用 `ALL_VERSIONS` 常量替代硬编码版本列表。

**工作逻辑**：
将 `protected static List<Object> parameters()` 方法重命名为 `protected static List<Integer> formatVersions()`，返回值从 `Arrays.asList(1, 2, 3)` 改为 `ALL_VERSIONS`。新增 `ALL_VERSIONS` 的静态 import。

### `core/src/test/java/org/apache/iceberg/TestRewriteFiles.java` (修改, +1/-2 lines)

**修改目的**：使用 `ALL_VERSIONS` 直接操作，移除 `Ints.asList` 转换。

**工作逻辑**：
将 `Ints.asList(TestHelpers.ALL_VERSIONS).stream()` 改为 `TestHelpers.ALL_VERSIONS.stream()`，移除 `Ints` 的 import。

### `core/src/test/java/org/apache/iceberg/TestRowLineageMetadata.java` (修改, +1/-2 lines)

**修改目的**：使用 `ALL_VERSIONS` 直接引用，移除 `Ints.asList` 转换。

**工作逻辑**：
将 `Ints.asList(TestHelpers.ALL_VERSIONS)` 改为 `TestHelpers.ALL_VERSIONS`，移除 `Ints` 的 import。

## 总结

本提交是测试代码的小型重构，将 `ALL_VERSIONS` 从 `int[]` 改为 `List<Integer>` 并在多个测试类中统一使用，消除了硬编码版本列表和冗余的类型转换，提升了代码的可维护性。当未来新增格式版本时，只需修改 `MAX_FORMAT_VERSION` 常量即可。
