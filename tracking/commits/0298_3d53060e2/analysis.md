# 提交 0298：Spark 3.5: Migrate tests to JUnit5 in data directory (#9341)

## 提交信息

- **序号**：0298 / 4088
- **哈希**：3d53060e2868da4bb9eb939e6f492e6fbcc9688d
- **短哈希**：3d53060e2
- **日期**：2023-12-22 08:37:04 +0100
- **作者**：Chinmay Bhat
- **提交说明**：Spark 3.5: Migrate tests to JUnit5 in data directory (#9341)
- **PR/Issue**：#9341

## 总体目的

Iceberg 项目长期使用 JUnit 4 作为测试框架，但随着测试用例规模增长和测试复杂度提升，JUnit 4 的局限性逐渐显现：扩展模型较弱（基于 `@Rule`）、参数化测试支持有限、生命周期注解不够灵活等。JUnit 5（Jupiter）提供了更现代的扩展机制（`@ExtendWith`）、更清晰的断言 API（结合 AssertJ 流式断言）、更灵活的参数化测试（`@TestTemplate`、`@ParameterizedTest`）、以及对临时目录等测试基础设施的更好支持（`@TempDir` 注入 `Path` 而非 `TemporaryFolder` 的 `File`）。

社区在 Spark 3.5 模块上启动了一项系统性的 JUnit 4 → JUnit 5 迁移工作，按目录分批进行，以控制每次 PR 的规模和回归风险。本次提交（#9341）专门迁移 `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/` 目录下的测试类（共 22 个文件，767 行新增、522 行删除），是该系列迁移的一部分。该目录包含 Avro/ORC/Parquet 读写器、Spark 数据类型转换、向量化读取等核心数据读写路径的测试，是保障 Spark 集成质量的关键测试集合。

迁移的核心动机包括：统一测试框架到 JUnit 5 以便后续利用其扩展能力；将 AssertJ 的 `Assertions.assertThat(...)` 静态调用统一为 `assertThat(...)` 静态导入以提升可读性；将 JUnit 4 的 `TemporaryFolder`（基于 `File`）替换为 JUnit 5 的 `@TempDir` 注入 `java.nio.file.Path`，以更贴近现代 Java IO API；并为参数化测试场景引入 `@TestTemplate` 机制。

## 如何达成设计目的

整体设计思路是"机械性但有规则的迁移"：保持每个测试类的测试逻辑不变，仅替换测试基础设施 API。具体通过统一的注解/import/断言替换规则来达成，并对涉及参数化测试的抽象基类（`AvroDataTest`）通过新增 `ParameterizedAvroDataTest` 子类来支持 `@TestTemplate` 模式，避免破坏原有继承体系。迁移后所有测试方法签名和断言语义保持等价，确保测试覆盖行为不变。

## 修改详情

本次迁移涉及 22 个文件，新增 1 个文件（`ParameterizedAvroDataTest.java`）。下面按迁移模式分类说明，并对关键文件单独展开。

### 迁移模式总览

**1. 注解替换（JUnit 4 → JUnit 5）**
- `org.junit.Test` → `org.junit.jupiter.api.Test`
- `org.junit.Ignore` → `org.junit.jupiter.api.Disabled`
- `org.junit.Before` → `org.junit.jupiter.api.BeforeEach`
- `org.junit.After` → `org.junit.jupiter.api.AfterEach`
- `org.junit.BeforeClass` → `org.junit.jupiter.api.BeforeAll`
- `org.junit.AfterClass` → `org.junit.jupiter.api.AfterAll`
- `@Test` 上的 `@Ignore` → `@Disabled`

**2. 临时目录替换**
- `@Rule public TemporaryFolder temp = new TemporaryFolder();` → `@TempDir protected Path temp;`
- `temp.newFile()` → `File.createTempFile("junit", null, temp.toFile())`
- `temp.newFolder()` → `java.nio.file.Files.createTempDirectory(temp, null).toFile()` 或 `temp.toFile()`
- `temp.getRoot().getAbsolutePath()` → `temp.toAbsolutePath()`
- `temp.newFolder(desc)` → `temp.resolve(desc).toFile()`
- import 由 `org.junit.rules.TemporaryFolder` 改为 `org.junit.jupiter.api.io.TempDir`，并新增 `java.nio.file.Path` 导入

**3. 断言替换（JUnit Assert + AssertJ Assertions → AssertJ assertThat 静态导入）**
- `import org.junit.Assert;` 和 `import org.assertj.core.api.Assertions;` 移除
- 新增 `import static org.assertj.core.api.Assertions.assertThat;`（及 `assertThatThrownBy`、`Assumptions.assumeThat` 等）
- `Assert.assertEquals("msg", expected, actual)` → `assertThat(actual).as("msg").isEqualTo(expected)`
- `Assert.assertTrue("msg", cond)` → `assertThat(cond).as("msg").isTrue()`
- `Assert.assertFalse("msg", cond)` → `assertThat(cond).as("msg").isFalse()`
- `Assert.assertNotNull("msg", obj)` → `assertThat(obj).as("msg").isNotNull()`
- `Assert.assertNull("msg", obj)` → `assertThat(obj).as("msg").isNull()`
- `Assert.assertArrayEquals("msg", expected, actual)` → `assertThat(actual).as("msg").isEqualTo(expected)`
- `Assert.assertNotSame(...)` → `assertThat(...).as(...).isNotSameAs(...)`
- `Assertions.assertThatThrownBy(...)` → `assertThatThrownBy(...)`
- `Assertions.assertThat(x).as(m).isInstanceOf(C.class)` → `assertThat(x).as(m).isInstanceOf(C.class)`（仅去掉 `Assertions.` 前缀）

**4. 假设（Assumption）替换**
- `org.junit.Assume.assumeTrue("msg", cond)` → `static org.assertj.core.api.Assumptions.assumeThat(...).as("msg").isNull()/isTrue()`
- 例如 `Assume.assumeTrue("...", null == TypeUtil.find(...))` → `assumeThat(TypeUtil.find(...)).as("...").isNull()`

**5. 迭代器断言（AssertJ 特化）**
- `Assert.assertTrue("Should have expected number of rows", rows.hasNext())` → `assertThat(rows).as("...").hasNext()`
- `Assert.assertFalse("Should not have extra rows", rows.hasNext())` → `assertThat(rows).as("...").isExhausted()`

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/AvroDataTest.java`

**修改目的**：将抽象测试基类迁移到 JUnit 5，并将临时目录字段从 `TemporaryFolder` 改为 `@TempDir Path`。

**工作逻辑**：作为 `TestSparkAvroReader`、`TestSparkParquetReader`、`TestParquetVectorizedReads` 等多个子类的父类，其迁移直接影响所有子类。`@Rule public TemporaryFolder temp` 改为 `@TempDir protected Path temp`（可见性提升为 `protected` 以便子类访问）。import 中移除 `org.junit.Rule`、`org.junit.rules.TemporaryFolder`，新增 `org.junit.jupiter.api.Test`、`org.junit.jupiter.api.io.TempDir`、`java.nio.file.Path`。`@Test` 注解来源切换到 jupiter 包。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/ParameterizedAvroDataTest.java`（新增文件）

**修改目的**：为参数化测试场景提供与 `AvroDataTest` 等价但使用 `@TestTemplate` 的抽象基类。

**工作逻辑**：该文件是 `AvroDataTest` 的"参数化版本"，共 284 行。其类注释明确说明："Copy of `AvroDataTest` that marks tests with `@TestTemplate` instead of `@Test` to make the tests work in a parameterized environment."。二者结构完全一致（相同的 `SUPPORTED_PRIMITIVES` schema 定义、相同的 `@TempDir Path temp` 字段、相同的 `writeAndValidate` 抽象方法、相同的测试方法集），唯一区别是每个测试方法使用 `@org.junit.jupiter.api.TestTemplate` 而非 `@Test`。这样当子类运行在参数化测试扩展环境下时，这些测试方法可以针对每组参数重复执行，而普通 `AvroDataTest` 子类则按普通 `@Test` 执行一次。这是 JUnit 5 迁移中为兼容参数化场景特意设计的双轨结构。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/TestHelpers.java`

**修改目的**：将测试辅助类的断言统一为 AssertJ 静态导入风格。

**工作逻辑**：`TestHelpers` 提供大量 `assertEqualsSafe`/`assertEqualsUnsafe` 方法，对各种 Iceberg 类型（LONG、DATE、TIMESTAMP、STRING、UUID、FIXED、BINARY、DECIMAL、STRUCT、LIST、MAP）做逐字段比较。迁移将其中所有 `Assert.assertEquals/assertNotNull/assertArrayEquals` 和 `Assertions.assertThat(...)` 统一替换为 `assertThat(...)` 静态调用形式。例如：
- `Assert.assertNotNull("Should have a matching key", matchingKey)` → `assertThat(matchingKey).as("Should have a matching key").isNotNull()`
- `Assert.assertArrayEquals("Bytes should match", expectedBytes, actualBytes)` → `assertThat(actual).as("Bytes should match").isEqualTo(expectedBytes)`（AssertJ 对数组用 `isEqualTo` 即可，无需专门 `assertArrayEquals`）
- DATE 类型断言中原本 `Assertions.assertThat(expected).as("Should be an int").isInstanceOf(Integer.class)` 简化为 `assertThat(expected).as("Should be an int").isInstanceOf(Integer.class)`，同时把 `Assert.assertEquals("ISO-8601 date should be equal", date.toString(), actual.toString())` 改为 `assertThat(actual.toString()).as("ISO-8601 date should be equal").isEqualTo(String.valueOf(date))`，统一用 `String.valueOf` 替代 `toString()` 以避免潜在的 NPE。该文件改动量最大（241 行变更），因为辅助类内断言密度高。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/GenericsHelpers.java`

**修改目的**：同 `TestHelpers`，将辅助类断言迁移到 AssertJ 静态导入风格。

**工作逻辑**：与 `TestHelpers` 平行的另一套用于 `IcebergGenerics` 读取路径的对比辅助类。迁移模式完全相同：移除 `org.junit.Assert` 和 `org.assertj.core.api.Assertions` import，新增 `assertThat` 静态导入；所有 `Assert.assertEquals`、`Assertions.assertThat` 替换为 `assertThat`；`Assert.assertArrayEquals` → `isEqualTo`；MAP key 数量比较 `Assert.assertEquals("Should have the same number of keys", expected.keySet().size(), actual.keySet().size())` → `assertThat(actual.keySet()).as("...").hasSameSizeAs(expected.keySet())`，使用 AssertJ 更语义化的 `hasSameSizeAs`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/TestOrcWrite.java`

**修改目的**：迁移 ORC 写入测试到 JUnit 5。

**工作逻辑**：典型小文件迁移。`@Rule TemporaryFolder` → `@TempDir Path`；`temp.newFile()` → `File.createTempFile("junit", null, temp.toFile())`；`Assert.assertTrue("Delete should succeed", testFile.delete())` → `assertThat(testFile.delete()).as("Delete should succeed").isTrue()`；`Assert.assertNotNull("Split offsets not present", writer.splitOffsets())` → `assertThat(writer.splitOffsets()).as("Split offsets not present").isNotNull()`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/TestSparkParquetReader.java`

**修改目的**：迁移 Parquet 读取测试到 JUnit 5，并处理 `Assume` 假设语句。

**工作逻辑**：该类继承 `AvroDataTest`。关键迁移点：
- `Assume.assumeTrue("Parquet Avro cannot write non-string map keys", null == TypeUtil.find(...))` → `assumeThat(TypeUtil.find(...)).as("Parquet Avro cannot write non-string map keys").isNull()`，使用 AssertJ 的 `Assumptions.assumeThat` 静态导入，将"假设条件成立"改写为"假设某个值为 null"的流式断言，语义更清晰。
- `temp.newFile()` → `File.createTempFile("junit", null, temp.toFile())`
- `Assert.assertTrue("Should have expected number of rows", rows.hasNext())` → `assertThat(rows).as("...").hasNext()`
- `Assert.assertFalse("Should not have extra rows", rows.hasNext())` → `assertThat(rows).as("...").isExhausted()`
- `temp.newFolder().getCanonicalPath()` → `java.nio.file.Files.createTempDirectory(temp, null).toFile().getCanonicalPath()`
- `temp.getRoot().getAbsolutePath()` → `temp.toAbsolutePath()`
- `Assert.assertEquals(rows.size(), readRows.size())` + `Assertions.assertThat(readRows).isEqualTo(rows)` 合并为 `assertThat(readRows).hasSameSizeAs(rows)` + `assertThat(readRows).isEqualTo(rows)`

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/parquet/vectorized/TestParquetVectorizedReads.java`

**修改目的**：迁移向量化 Parquet 读取测试到 JUnit 5。

**工作逻辑**：该类继承 `AvroDataTest`，涉及大量 `@Ignore` 注解的禁用测试方法（`testArray`、`testArrayOfStructs`、`testMap` 等），全部从 `@Ignore` 迁移到 `@Disabled`。`Assume.assumeTrue` → `assumeThat(...).isNull()`（同 `TestSparkParquetReader` 模式）。`Assertions.assertThatThrownBy` → `assertThatThrownBy` 静态导入。`temp.newFile()` → `File.createTempFile("junit", null, temp.toFile())`。`Assert.assertEquals(expectedSize, numRowsRead)` → `assertThat(numRowsRead).isEqualTo(expectedSize)`。

### 其余测试类（`TestParquetAvroReader`、`TestParquetAvroWriter`、`TestSparkAvroEnums`、`TestSparkAvroReader`、`TestSparkDateTimes`、`TestSparkOrcReadMetadataColumns`、`TestSparkOrcReader`、`TestSparkParquetReadMetadataColumns`、`TestSparkParquetWriter`、`TestSparkRecordOrcReaderWriter`、`TestParquetDictionaryEncodedVectorizedReads`、`TestParquetDictionaryFallbackToPlainEncodingVectorizedReads`、`source/TestAvroScan`、`source/TestDataFrameWrites`、`source/TestParquetScan`）

**修改目的**：将 data 目录及其 source 子目录下剩余测试类统一迁移到 JUnit 5。

**工作逻辑**：均遵循前述统一迁移模式——注解替换（`@Test`/`@Before`/`@After`/`@Ignore` → jupiter 对应注解）、`TemporaryFolder` → `@TempDir Path`、`Assert.*` 和 `Assertions.assertThat` → `assertThat` 静态导入、`Assume.assumeTrue` → `assumeThat`、临时文件创建方式适配 `Path`。其中 `TestDataFrameWrites`（109 行变更）和 `TestParquetScan`（64 行变更）改动较大，主要是因为断言密度高、临时目录/文件操作多；`TestSparkOrcReadMetadataColumns`、`TestSparkParquetReadMetadataColumns` 涉及元数据列读取，除常规断言迁移外还包含对迭代器的 `hasNext`/`isExhausted` 断言替换。

## 小结

本次提交是 Iceberg Spark 3.5 模块 JUnit 4 → JUnit 5 系列迁移的 data 目录批次，涉及 22 个文件、净增 245 行。迁移严格遵循统一的注解/import/断言替换规则，保持测试逻辑不变，核心收益包括：统一到 JUnit 5 现代测试框架、断言统一为 AssertJ 流式静态导入提升可读性、临时目录改用 `@TempDir Path` 贴近 NIO API、并通过新增 `ParameterizedAvroDataTest` 为参数化测试场景铺设 `@TestTemplate` 双轨结构。该迁移为后续利用 JUnit 5 扩展能力和参数化测试奠定了基础，是提升测试基础设施现代化水平的重要一步。
