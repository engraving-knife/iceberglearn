# 提交 0695：将 Flink 1.18 的 JUnit5 迁移回移植到 Flink 1.17

## 提交信息
- **序号**：0695 / 4088
- **哈希**：c41c599fe877b676628ce05ff2653401d306c9c6
- **短哈希**：c41c599fe
- **日期**：2024-04-17 20:43:23 +0900
- **作者**：Tom Tanaka <43331405+tomtongue@users.noreply.github.com>
- **提交说明**：Flink: Backport Flink 1.18 JUnit5 migration to Flink 1.17 (#10163)
- **PR/Issue**：#10163

## 总体目的

本提交的目的是将此前在 Flink 1.18 上完成的 JUnit5 迁移工作回移植（backport）到 Flink 1.17 模块，使两个版本的测试框架保持一致。

Iceberg 的 Flink 集成模块为每个 Flink 版本维护一份独立的源码副本（`flink/v1.17/`、`flink/v1.18/`、`flink/v1.19/` 等）。此前 Flink 1.18 的测试代码已经从 JUnit 4 迁移到了 JUnit 5（JUnit Jupiter），但 Flink 1.17 的测试代码仍然使用 JUnit 4。这种不一致带来了维护负担：当开发者需要在多个版本间同步测试改动时，必须同时处理两套不同的测试 API 写法。本提交通过把 1.18 的迁移模式套用到 1.17，消除这种不一致。

迁移 JUnit 4 到 JUnit 5 是 Apache 社区的大趋势。JUnit 5 更现代的 API、更好的扩展模型、以及对 AssertJ 的原生支持，使测试代码更简洁可读。Flink 1.18 已完成迁移，1.17 跟进是自然的。

## 如何达成设计目的

整体策略是"模式复制"：将 Flink 1.18 迁移中用到的 JUnit 4 → JUnit 5 转换模式，逐一应用到 Flink 1.17 中对应的测试文件。共涉及 11 个测试文件，116 行新增、121 行删除。

迁移遵循以下固定模式：

1. **注解替换**：
   - `@Before` → `@BeforeEach`（org.junit.Before → org.junit.jupiter.api.BeforeEach）
   - `@After` → `@AfterEach`（如适用）
   - `@Test` 注解从 `org.junit.Test` 改为 `org.junit.jupiter.api.Test`

2. **临时目录替换**：
   - `@Rule public TemporaryFolder temp = new TemporaryFolder();` → `@TempDir private Path temp;`（org.junit.rules.TemporaryFolder → org.junit.jupiter.api.io.TempDir）
   - `temp.newFile(...)` / `temp.newFolder()` → `File.createTempFile(..., temp.toFile())`
   - 类型从 `File` 的 `temp` 改为 `Path` 的 `temp`，使用时需 `temp.toFile()` 转换

3. **断言替换（JUnit Assert → AssertJ assertThat）**：
   - `import org.junit.Assert;` → `import static org.assertj.core.api.Assertions.assertThat;`（和 `assertThatThrownBy` 如需）
   - `import org.assertj.core.api.Assertions;` → `import static org.assertj.core.api.Assertions.assertThat;`（改为静态导入）
   - `Assert.assertTrue("msg", actual.isPresent())` → `assertThat(actual).isPresent()`
   - `Assert.assertFalse("msg", actual.isPresent())` → `assertThat(actual).isNotPresent()`
   - `Assert.assertEquals(expected, actual)` → `assertThat(actual).isEqualTo(expected)`
   - `Assert.assertEquals("msg", expected, actual)` → `assertThat(actual).as("msg").isEqualTo(expected)`
   - `Assertions.assertThat(obj)` → `assertThat(obj)`（静态导入后简化）
   - `Assertions.assertThatThrownBy(...)` → `assertThatThrownBy(...)`

4. **Iterator 断言替换**：
   - `Assert.assertTrue("Should have more records", actual.hasNext())` → `assertThat(actual).hasNext()`
   - `Assert.assertFalse("Shouldn't have more record", actual.hasNext())` → `assertThat(actual).isExhausted()`

5. **Optional 断言替换**：
   - `Assert.assertTrue(tableSchema.getPrimaryKey().isPresent())` → `assertThat(tableSchema.getPrimaryKey()).isPresent()`
   - `ImmutableSet.of(...)` 比较改为 `containsExactly(...)` 更语义化

## 修改详情

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/AvroGenericRecordConverterBase.java`
**修改目的**：迁移 `@Test` 导入。
**工作逻辑**：`import org.junit.Test;` → `import org.junit.jupiter.api.Test;`。这是最简单的迁移，仅改 import，无断言改动。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/TestDataFileSerialization.java`
**修改目的**：迁移断言到 AssertJ 静态导入。
**工作逻辑**：新增 `import static org.assertj.core.api.Assertions.assertThat;`，移除 `import org.assertj.core.api.Assertions;` 和 `import org.junit.Test;`，新增 `import org.junit.jupiter.api.Test;`。3 处 `Assertions.assertThat(obj)` 改为 `assertThat(obj)`。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/TestFlinkCatalogFactory.java`
**修改目的**：迁移 `@Before` → `@BeforeEach`，断言改为 AssertJ 静态导入。
**工作逻辑**：新增 `assertThat` 和 `assertThatThrownBy` 静态导入。移除 `Assertions`、`@Before`、`@Test` 导入，新增 `@BeforeEach`、`@Test`（jupiter）。`@Before` → `@BeforeEach`。4 处 `Assertions.assertThat(catalog)` → `assertThat(catalog)`，2 处 `Assertions.assertThatThrownBy` → `assertThatThrownBy`。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/TestFlinkFilters.java`
**修改目的**：本提交中改动量最大的文件，全面迁移 JUnit Assert 到 AssertJ。
**工作逻辑**：移除 `Assertions`、`Assert`、`Test`（junit4）导入，新增 `assertThat` 静态导入和 `Test`（jupiter）。大量断言替换：
- 约 20 处 `Assert.assertTrue("Conversion should succeed", actual.isPresent())` → `assertThat(actual).isPresent()`
- 约 8 处 `Assert.assertFalse("Conversion should failed", actual.isPresent())` → `assertThat(actual).isNotPresent()`
- `Assert.assertEquals("Predicate operation should match", expected.op(), not.op())` → `assertThat(not.op()).as("Predicate operation should match").isEqualTo(expected.op())`
- `assertPredicatesMatch` 方法中 3 处 `Assert.assertEquals("...", expected, actual)` 合并简化为 3 行 `assertThat(actual).isEqualTo(expected)` 形式
- `matchLiteral` 中 `Assertions.assertThat(expression)` → `assertThat(expression)`，`Assert.assertTrue("Should match the literal", predicate.test(icebergLiteral))` → `assertThat(predicate.test(icebergLiteral)).isTrue()`

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/TestFlinkSchemaUtil.java`
**修改目的**：迁移断言，使用更语义化的 AssertJ 链式调用。
**工作逻辑**：新增 `assertThat`、`assertThatThrownBy` 静态导入，移除 `Assertions`、`Assert`、`Test`（junit4）导入，移除不再使用的 `ImmutableSet` 导入。断言替换：
- `Assert.assertEquals(icebergSchema.asStruct(), FlinkSchemaUtil.convert(flinkSchema).asStruct())` → `assertThat(FlinkSchemaUtil.convert(flinkSchema).asStruct()).isEqualTo(icebergSchema.asStruct())`（注意参数顺序调整，AssertJ 习惯实际值在前）
- `Assert.assertEquals(ImmutableSet.of(101), convertedSchema.identifierFieldIds())` → `assertThat(convertedSchema.identifierFieldIds()).containsExactly(101)`（更语义化）
- `Assert.assertTrue(tableSchema.getPrimaryKey().isPresent())` + `Assert.assertEquals(ImmutableSet.of("int", "string"), ImmutableSet.copyOf(...))` 合并为 `assertThat(tableSchema.getPrimaryKey()).isPresent().get().satisfies(k -> assertThat(k.getColumns()).containsExactly("int", "string"))`（链式 + 嵌套断言）
- `Assertions.assertThatThrownBy` → `assertThatThrownBy`

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/TestManifestFileSerialization.java`
**修改目的**：迁移临时目录规则和断言。
**工作逻辑**：
- `@Rule public TemporaryFolder temp = new TemporaryFolder();` → `@TempDir private Path temp;`
- 新增 `import java.nio.file.Path;`，移除 `Rule`、`TemporaryFolder`、`Assertions`、`Assert`、`Test`（junit4）导入，新增 `TempDir`、`Test`（jupiter）
- `temp.newFile("input.m0.avro")` + `Assert.assertTrue(manifestFile.delete())` → `File.createTempFile("input", "m0.avro", temp.toFile())` + `assertThat(manifestFile.delete()).isTrue()`
- `Assertions.assertThat(obj)` → `assertThat(obj)`

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/TestRowDataWrapper.java`
**修改目的**：迁移断言，包括 Iterator 相关的专用断言。
**工作逻辑**：移除 `Assertions`、`Assert` 导入，新增 `assertThat` 静态导入。`Assertions.assertThat(actual/expected)` → `assertThat(actual/expected)`。`Assert.assertEquals(message, expectedMilliseconds, actualMilliseconds)` → `assertThat(actualMilliseconds).as(message).isEqualTo(expectedMilliseconds)`。`Assert.assertTrue("Should have more records", actual.hasNext())` → `assertThat(actual).hasNext()`，`Assert.assertFalse("Shouldn't have more record", actual.hasNext())` → `assertThat(actual).isExhausted()`。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/TestTableSerialization.java`
**修改目的**：迁移临时目录规则和 `@Before` 注解。
**工作逻辑**：
- `@Rule public TemporaryFolder temp = new TemporaryFolder();` → `@TempDir private Path temp;`
- `@Before` → `@BeforeEach`
- `temp.newFolder()` + `Assert.assertTrue(tableLocation.delete())` → `File.createTempFile("junit", null, temp.toFile())` + `assertThat(tableLocation.delete()).isTrue()`
- 移除 `Assert`、`Before`、`Rule`、`TemporaryFolder`、`Test`（junit4）导入，新增 `BeforeEach`、`Test`（jupiter）、`TempDir`、`Path`

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/sink/TestAvroGenericRecordToRowDataMapper.java`
**修改目的**：迁移断言。
**工作逻辑**：移除 `import org.junit.Assert;`，新增 `assertThat` 静态导入。`Assert.assertEquals(expected, actual)` → `assertThat(actual).isEqualTo(expected)`。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/TestRowDataToAvroGenericRecordConverter.java`
**修改目的**：迁移断言。
**工作逻辑**：与上一个文件相同模式，`Assert.assertEquals(expected, actual)` → `assertThat(actual).isEqualTo(expected)`。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/util/TestFlinkPackage.java`
**修改目的**：迁移断言。
**工作逻辑**：移除 `Assert`、`Test`（junit4）导入，新增 `assertThat` 静态导入和 `Test`（jupiter）。3 处 `Assert.assertEquals(expected, actual)` → `assertThat(actual).isEqualTo(expected)`。

## 小结
- **成效**：成功达成目的。Flink 1.17 的 11 个测试文件已从 JUnit 4 迁移到 JUnit 5，与 Flink 1.18 的测试框架保持一致，降低了多版本维护的认知负担。
- **影响范围**：仅影响 `flink/v1.17/` 的测试代码，不涉及主代码，不影响 v1.18/v1.19，也不影响其他模块。迁移是纯测试层面的重构，不改变测试覆盖的行为语义。
- **回迁到 1.4.x 的注意事项**：回迁时需确保 1.4.x 的 v1.17 模块的 `build.gradle` 中已包含 JUnit 5（jupiter）和 AssertJ 的测试依赖，否则迁移后的测试无法编译。如果 1.4.x 的 v1.17 仍依赖 JUnit 4，需先添加 JUnit 5 依赖再回迁。此外需注意 `@TempDir` 注入的 `Path` 类型与原 `TemporaryFolder` 的 `File` 类型不同，涉及临时文件操作的代码（如 `File.createTempFile`）需配合 `temp.toFile()` 转换，回迁时要逐一核对。迁移中部分断言的参数顺序有调整（AssertJ 是 actual 在前、expected 在后），回迁时需小心不要搞反。
