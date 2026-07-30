# 提交 0684：将 Flink 测试迁移到 JUnit 5

## 提交信息
- **序号**：0684 / 4088
- **哈希**：943321ee6d1a8fd9da4632c456c4507e62b2898e
- **短哈希**：943321ee6
- **日期**：2024-04-15
- **作者**：Tom Tanaka
- **提交说明**：Flink: Migrate tests to JUnit5 (#10130)
- **PR/Issue**：#10130

## 总体目的

本提交将 Iceberg 的 Flink 集成测试以及部分 `data` 模块测试从 JUnit 4 迁移到 JUnit 5（Jupiter）。这是一次大范围、机械性但需要细致处理的测试框架升级，涉及 30 个测试文件，共 483 行新增、436 行删除（净增主要来自 AssertJ 流式断言的可读性扩展）。

迁移的核心动机来自测试生态演进：JUnit 5 是当前 Java 测试生态的事实标准，相比 JUnit 4 提供了更现代的扩展模型、更强大的参数化测试、更好的模块化（分 API、Engine、Launcher 三层）以及与 AssertJ 等流式断言库更自然的配合。Iceberg 项目长期目标是将所有测试统一到 JUnit 5，本提交是把 Flink 相关测试纳入统一标准的一步。

迁移覆盖两类目录：
1. **`data` 模块测试**（5 个文件）：`DataTest`、`avro/TestGenericData`、`orc/TestGenericData`、`parquet/TestGenericData`、`parquet/TestParquetEncryptionWithWriteSupport`。这些是数据读写抽象测试基类与具体格式实现，被 Flink 测试继承。
2. **`flink` 模块测试**（25 个文件，跨 v1.17/v1.18 两个 Flink 版本目录）：包括 `TestFlinkFilters`、`TestFlinkSchemaUtil`、`TestFlinkPackage`、`TestRowProjection`、序列化测试、reader/writer 测试、sink mapper 测试等。

值得注意的是，`data` 模块的 `DataTest` 是抽象基类，定义了 `@TempDir` 字段和测试模板，被 `avro`/`orc`/`parquet` 三个 `TestGenericData` 继承。因此迁移 `DataTest` 时必须同步迁移所有子类，否则 JUnit 4 的 `@Rule TemporaryFolder` 与 JUnit 5 的 `@TempDir` 机制不兼容，子类无法获取临时目录。这也是为什么本提交把 `data` 模块测试一并纳入的原因——它们是一个继承体系，必须整体迁移。

## 如何达成设计目的

迁移采用"机械替换 + 风格统一"策略：把 JUnit 4 API 替换为 JUnit 5 等价 API，同时把断言从 JUnit 4 `Assert` 切换到 AssertJ 流式断言（Iceberg 项目已在使用 AssertJ），并临时目录机制从 `@Rule TemporaryFolder` 切到 `@TempDir Path`。具体迁移模式如下：

### 1. 注解与导入替换
- `import org.junit.Test;` → `import org.junit.jupiter.api.Test;`
- `import org.junit.Rule;` 与 `import org.junit.rules.TemporaryFolder;` → `import org.junit.jupiter.api.io.TempDir;` 与 `import java.nio.file.Path;`
- `import org.junit.Assert;` → `import static org.assertj.core.api.Assertions.assertThat;`（以及按需 `assertThatThrownBy`、`withPrecision`）
- 把原来以非静态方式引用 `org.assertj.core.api.Assertions` 的（如 `Assertions.assertThatThrownBy(...)`）改为静态导入 `assertThatThrownBy(...)`，与项目风格统一。

### 2. 临时目录字段迁移
- JUnit 4：`@Rule public TemporaryFolder temp = new TemporaryFolder();`
- JUnit 5：`@TempDir protected Path temp;`（注意从 `TemporaryFolder` 对象改为 `Path` 注入）

字段可见性按原代码保留（`public` 或 `protected`/`private`），但 JUnit 5 的 `@TempDir` 是字段注入机制，不再需要 `@Rule` 注解和 `new TemporaryFolder()` 实例化。

### 3. 临时文件创建 API 迁移
这是改动量最大的部分。JUnit 4 的 `TemporaryFolder.newFile()` 在 JUnit 5 中没有直接等价物（JUnit 5 的 `@TempDir` 只提供目录 `Path`，不提供文件创建辅助方法），因此作者统一改用 JDK 的 `File.createTempFile`：

- JUnit 4：`File testFile = temp.newFile();`
- JUnit 5：`File testFile = File.createTempFile("junit", null, temp.toFile());`

其中 `temp.toFile()` 把 `Path` 转回 `File` 以适配 `File.createTempFile` 签名。带后缀的场景（如 `TestRowProjection` 中 `temp.newFile(desc + ".avro")`）改为 `File.createTempFile("junit", desc + ".avro", temp.toFile())`——前缀固定为 `"junit"`，后缀保留原文件扩展名。这是一个语义上不完全等价的替换（原 `newFile(name)` 会用 `name` 作为完整文件名，新方式用随机前缀 + 指定后缀），但测试逻辑只依赖文件存在性与扩展名，不影响测试结果。

### 4. 断言库切换（Assert → AssertJ）
把 `org.junit.Assert.*` 系列调用改为 AssertJ 流式断言。这是迁移中风格收益最大、行数变化最大的部分。典型替换模式：

| JUnit 4 | AssertJ (JUnit 5) |
|---|---|
| `Assert.assertTrue("msg", x)` | `assertThat(x).as("msg").isTrue()` 或 `assertThat(x).isTrue()` |
| `Assert.assertFalse("msg", x)` | `assertThat(x).as("msg").isFalse()` 或 `assertThat(x).isFalse()` |
| `Assert.assertEquals(expected, actual)` | `assertThat(actual).isEqualTo(expected)` |
| `Assert.assertNotNull(x)` | `assertThat(x).isNotNull()` |
| `Assert.assertTrue(actual.isPresent())` | `assertThat(actual).isPresent()` |
| `Assert.assertTrue(it.hasNext())` | `assertThat(it).hasNext()` |
| `Assert.assertFalse(it.hasNext())` | `assertThat(it).isExhausted()` |
| `Assert.assertEquals(expected, actual, delta)` | `assertThat(actual).isEqualTo(expected, withPrecision(delta))` |
| `Assert.assertThrows(msg, Cls, () -> ...)` | `assertThatThrownBy(() -> ...).hasMessage(msg).isInstanceOf(Cls.class)` |

### 5. 断言链式分组（可读性增强）
对于同一对象多个字段断言，作者用 AssertJ 的 `satisfies` / `element(i)` / `first()` 进行分组，避免多个独立断言。例如 `orc/TestGenericData` 中：

- JUnit 4：连续 8 条 `Assert.assertEquals(...)` 分别断言 4 行的两列。
- JUnit 5：
  ```java
  assertThat(rows).element(0).satisfies(record -> {
      assertThat(record.getField("tsTzCol")).isEqualTo(...);
      assertThat(record.getField("tsCol")).isEqualTo(...);
  });
  // ... element(1)/(2)/(3) 同理
  ```
  `TestFlinkSchemaUtil` 中：
  ```java
  assertThat(tableSchema.getPrimaryKey())
      .isPresent()
      .get()
      .satisfies(k -> assertThat(k.getColumns()).containsExactly("int", "string"));
  ```
  把"主键存在 + 取出 + 校验列"合并为一条流式断言，可读性显著提升。

### 6. 浮点数精度断言
JUnit 4 的 `Assert.assertEquals(expected, actual, 0.000001f)` 迁移为 AssertJ 的 `assertThat(actual).isEqualTo(expected, withPrecision(0.000001f))`，需要静态导入 `org.assertj.core.api.Assertions.withPrecision`。`TestRowProjection` 中大量经纬度浮点断言采用此模式。

### 7. CharSequence 比较简化
原代码多处用 `Comparators.charSequences().compare("test", str) == 0` 来比较字符串（因为 Iceberg 内部用 `CharSequence` 而非 `String`），AssertJ 提供更直接的 `.asString().isEqualTo("test")`，可对 CharSequence 调用 `.asString()` 后比较。`TestRowProjection` 中多处从三行（取 cmp、断言 cmp==0）简化为一行流式断言。

### 8. 异常断言增强
`TestParquetEncryptionWithWriteSupport` 与 `TestFlinkSchemaUtil` 中的 `assertThrows` 迁移为 `assertThatThrownBy`，并且**额外断言了异常消息**：

- JUnit 4：`Assert.assertThrows("Decrypted without keys", ParquetCryptoRuntimeException.class, () -> ...)`（只断言消息 + 类型）
- JUnit 5：`assertThatThrownBy(() -> ...).hasMessage("Trying to read file with encrypted footer. No keys available").isInstanceOf(ParquetCryptoRuntimeException.class);`

注意这里消息从"描述性 message"变成了对真实异常消息的断言，是测试加强。

### 9. 集合断言现代化
- `Assert.assertEquals(ImmutableSet.of(101), convertedSchema.identifierFieldIds())` → `assertThat(convertedSchema.identifierFieldIds()).containsExactly(101);`
- `ImmutableSet.copyOf(...)` 比较改为 `containsExactly`，更语义化。

### 10. 抽象基类可见性调整
`DataTest` 中 `temp` 字段从 `public` 改为 `protected`，因为 JUnit 5 `@TempDir` 注入的字段不需要 public，且子类需要访问（继承），故 `protected`。`AvroGenericRecordConverterBase` 仅把 `import org.junit.Test` 改为 `org.junit.jupiter.api.Test`，是纯导入替换。

## 修改详情

### `data/src/test/java/org/apache/iceberg/data/DataTest.java`
**修改目的**：把抽象测试基类从 JUnit 4 迁移到 JUnit 5，为三个子类迁移铺路。
**工作逻辑**：`@Rule public TemporaryFolder temp = new TemporaryFolder();` → `@TempDir protected Path temp;`；`import org.junit.Test` → `import org.junit.jupiter.api.Test`；删除 `org.junit.Rule`、`org.junit.rules.TemporaryFolder` 导入，新增 `java.nio.file.Path`、`org.junit.jupiter.api.io.TempDir`。这是整个迁移的根节点，必须先迁移它，否则子类无法编译。

### `data/src/test/java/org/apache/iceberg/data/avro/TestGenericData.java`
**修改目的**：迁移 Avro 实现子类。
**工作逻辑**：`temp.newFile()` → `File.createTempFile("junit", null, temp.toFile())`；`Assert.assertTrue("Delete should succeed", testFile.delete())` → `assertThat(testFile.delete()).isTrue()`；静态导入 `assertThat`。这是子类迁移的典型样本。

### `data/src/test/java/org/apache/iceberg/data/orc/TestGenericData.java`
**修改目的**：迁移 ORC 实现子类，含较多断言。
**工作逻辑**：同 avro 子类的临时文件/删除断言替换；额外把 8 条连续 `Assert.assertEquals` 改为 4 个 `assertThat(rows).element(i).satisfies(record -> {...})` 分组；`writeAndValidateExternalData` 中 4 条断言合并为一个 `assertThat(rows).first().satisfies(...)`。体现 AssertJ 流式分组的可读性优势。

### `data/src/test/java/org/apache/iceberg/data/parquet/TestGenericData.java`
**修改目的**：迁移 Parquet 实现子类。
**工作逻辑**：临时文件/删除断言替换；`Assert.assertTrue("Should have at least one row", it.hasNext())` → `assertThat(it).hasNext()`；`Assert.assertFalse("Should not have more than one row", it.hasNext())` → `assertThat(it).isExhausted()`；`Assert.assertEquals(actualRecord.get(0, ArrayList.class).get(0), expectedBinary)` → `assertThat(actualRecord.get(0, ArrayList.class)).first().isEqualTo(expectedBinary);`。`hasNext()` 与 `isExhausted()` 是 AssertJ 对 `Iterator`/`CloseableIterator` 的专用断言，语义更清晰。

### `data/src/test/java/org/apache/iceberg/data/parquet/TestParquetEncryptionWithWriteSupport.java`
**修改目的**：迁移 Parquet 加密测试子类，含异常断言。
**工作逻辑**：临时文件/删除断言替换；`Assert.assertThrows("Decrypted without keys", ParquetCryptoRuntimeException.class, () -> ...)` → `assertThatThrownBy(() -> ...).hasMessage("Trying to read file with encrypted footer. No keys available").isInstanceOf(ParquetCryptoRuntimeException.class);`，把"描述性 message"升级为对真实异常消息的断言，是测试加强。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkAvroReaderWriter.java`
### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkOrcReaderWriter.java`
### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkParquetReader.java`
### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkParquetWriter.java`
**修改目的**：迁移 v1.17 Flink 的 reader/writer 测试。
**工作逻辑**：统一为 JUnit 5 注解 + AssertJ 断言替换，模式同前述 `data` 模块子类（`@TempDir Path`、`File.createTempFile`、`assertThat`）。v1.17 与 v1.18 对应文件改动一致。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/AvroGenericRecordConverterBase.java`
**修改目的**：抽象基类导入替换。
**工作逻辑**：仅 `import org.junit.Test` → `import org.junit.jupiter.api.Test`，无其他改动。因为该基类只声明 `@Test` 方法签名，不涉及断言或临时目录。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/TestDataFileSerialization.java`
### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/TestFlinkCatalogFactory.java`
### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/TestManifestFileSerialization.java`
### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/TestRowDataWrapper.java`
### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/TestTableSerialization.java`
**修改目的**：迁移 v1.18 Flink 各类功能测试。
**工作逻辑**：应用统一迁移模式（注解、`@TempDir`、AssertJ 断言）。`TestFlinkCatalogFactory` 等使用临时目录的场景改为 `@TempDir Path` + `File.createTempFile`；断言改 AssertJ 流式。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/TestFlinkFilters.java`
**修改目的**：迁移 Flink 表达式过滤器转换测试（87 行改动，是单文件改动较大者）。
**工作逻辑**：`import org.junit.Assert` + `import org.assertj.core.api.Assertions` → 静态导入 `assertThat`；`import org.junit.Test` → `org.junit.jupiter.api.Test`；大量 `Assert.assertTrue("Conversion should succeed", actual.isPresent())` → `assertThat(actual).isPresent();`。该测试有 10+ 处 `isPresent` 断言全部统一替换。`Optional` 的 `isPresent` 在 AssertJ 中有专用断言 `assertThat(opt).isPresent()`。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/TestFlinkSchemaUtil.java`
**修改目的**：迁移 Flink Schema 工具测试（40 行改动）。
**工作逻辑**：`Assert.assertEquals` → `assertThat(...).isEqualTo(...)`；`ImmutableSet.of(101)` 比较 → `containsExactly(101)`；主键存在+取出+校验列合并为 `.isPresent().get().satisfies(k -> ...)`；`Assertions.assertThatThrownBy` → 静态 `assertThatThrownBy`。删除不再需要的 `ImmutableSet` 导入。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/util/TestFlinkPackage.java`
**修改目的**：迁移 Flink 包版本工具测试。
**工作逻辑**：`Assert.assertEquals("1.18.1", FlinkPackage.version())` → `assertThat(FlinkPackage.version()).isEqualTo("1.18.1");` 等。该测试用 Mockito 的 `mockStatic`，迁移不影响 mock 部分（Mockito 与 JUnit 版本无关）。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkAvroReaderWriter.java`
### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkOrcReaderWriter.java`
### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkParquetReader.java`
### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkParquetWriter.java`
**修改目的**：迁移 v1.18 Flink 的 reader/writer 测试（与 v1.17 对应文件改动一致）。
**工作逻辑**：注解、`@TempDir`、AssertJ 断言统一替换。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/data/TestRowProjection.java`
**修改目的**：迁移 Flink 行投影测试（280 行改动，是本次提交单文件改动最大者）。
**工作逻辑**：`@Rule TemporaryFolder temp` → `@TempDir private Path temp`；`temp.newFile(desc + ".avro")` → `File.createTempFile("junit", desc + ".avro", temp.toFile())`，保留 `.avro` 后缀；大量 `Assert.assertEquals("msg", expected, actual)` → `assertThat(actual).as("msg").isEqualTo(expected);`，原 JUnit 4 的"消息在前"参数顺序在 AssertJ 中通过 `.as("msg")` 表达；CharSequence 比较简化：`int cmp = Comparators.charSequences().compare("test", str); Assert.assertEquals(0, cmp);` → `assertThat(str).asString().isEqualTo("test");`；浮点精度：`Assert.assertEquals(expected, actual, 0.000001f)` → `assertThat(actual).isEqualTo(expected, withPrecision(0.000001f));`，需静态导入 `withPrecision`。该文件覆盖了几乎所有迁移模式，是迁移模式的"全集样本"。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/data/TestStructRowData.java`
**修改目的**：迁移 StructRowData 测试。
**工作逻辑**：仅 2 行改动，注解导入替换（`org.junit.Test` → `org.junit.jupiter.api.Test`）。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/TestAvroGenericRecordToRowDataMapper.java`
### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/TestRowDataToAvroGenericRecordConverter.java`
**修改目的**：迁移 Flink sink mapper/converter 测试。
**工作逻辑**：小改动，注解导入替换 + 少量断言替换。

## 小结
- **成效**：成功将 30 个测试文件从 JUnit 4 迁移到 JUnit 5，覆盖 `data` 模块抽象测试基类与继承体系、Flink v1.17/v1.18 双版本的 reader/writer/序列化/Schema/Filter/RowProjection 等测试。迁移行为保持，并在多处用 AssertJ 流式断言、`satisfies` 分组、`assertThatThrownBy` 异常消息断言等增强了可读性与测试强度。
- **影响范围**：影响 `data` 与 `flink`（v1.17/v1.18）模块的测试代码。不影响生产代码（`main/`），只影响 `test/`。但因 `DataTest` 是抽象基类被三个格式子类继承，迁移必须整体完成，否则编译失败——这也是为何 `data` 模块被一并纳入的原因。
- **回迁到 1.4.x 的注意事项**：
  - 1.4.x 分支若 Flink 测试仍用 JUnit 4，可整体 cherry-pick；但需确认 1.4.x 已有 JUnit 5 与 AssertJ 依赖（Iceberg 主分支 build.gradle 中应已声明 `junit-jupiter` 与 `assertj-core`，1.4.x 应同样具备）。
  - 该提交改动面大（30 文件），cherry-pick 时易与 1.4.x 已有改动冲突，建议作为整体单元回迁，不要拆分。
  - 注意 `DataTest` 基类迁移必须与三个子类（avro/orc/parquet `TestGenericData`）同时迁移，否则临时目录机制不一致导致编译失败。
  - `TestRowProjection` 在 v1.17/v1.18 都存在，回迁时需注意 1.4.x 支持的 Flink 版本范围；若 1.4.x 不再支持 v1.17，可只回迁对应版本目录。
  - 迁移是纯测试层重构，不改变被测生产行为，回迁风险主要在编译兼容性而非行为差异。
