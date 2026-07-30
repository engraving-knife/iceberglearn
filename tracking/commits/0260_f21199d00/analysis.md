# 提交 0260：MR: Migrate tests to JUnit5 (#9241)

## 提交信息

- **序号**：0260 / 4088
- **哈希**：f21199d002c6ad008a6772783b4fdad622c09b59
- **短哈希**：f21199d00
- **日期**：2023-12-11
- **作者**：L S Chetan Rao
- **提交说明**：MR: Migrate tests to JUnit5 (#9241)
- **PR/Issue**：#9241

## 总体目的

Iceberg 的 `mr`（MapReduce / Hive 集成）模块的测试套件此前仍基于 JUnit 4。本次提交将该模块下的测试统一迁移到 JUnit 5（Jupiter），这是 Iceberg 各子模块逐步向 JUnit 5 演进的一部分。JUnit 5 提供了更现代的扩展模型、更清晰的断言 API（配合 AssertJ 流式断言），并与较新版本的测试生态（Mockito、Testcontainers 等）更契合。迁移后，mr 模块的测试将统一使用 `org.junit.jupiter.api.*` 注解与 AssertJ 断言，便于后续维护与跨模块一致性，也减少对旧版 JUnit 4 运行时的依赖。

由于 JUnit 5 不再使用 `@Rule` 机制，临时目录等共享 fixture 改用 JUnit 5 的 `@TempDir` 扩展；`@Before` 改为 `@BeforeEach`；`org.junit.Assert` / `org.junit.Assume` 的静态调用改为 AssertJ 的 `assertThat` / `assumeThat`。这是一次纯测试基础设施迁移，不改动任何产品代码逻辑。

## 如何达成设计目的

整体思路是「构建配置开启 JUnit Platform + 逐文件替换 API」。首先在 `mr/build.gradle` 的 `test` 块中启用 `useJUnitPlatform()`，让 Gradle 用 JUnit 5 的 Platform 引擎运行测试（同时仍可经 `junit-vintage-engine` 兼容旧测试，但本提交直接把目标文件改写为 JUnit 5 写法）。随后对 16 个测试/工具类文件做机械但系统的 API 替换：

- 导入：`org.junit.Test` → `org.junit.jupiter.api.Test`；`org.junit.Before` → `org.junit.jupiter.api.BeforeEach`；`org.junit.Assert` → `org.assertj.core.api.Assertions.*`；`org.junit.Assume` → `org.assertj.core.api.Assumptions.assumeThat`；`org.junit.Rule` + `org.junit.rules.TemporaryFolder` → `org.junit.jupiter.api.io.TempDir`。
- 临时目录：`@Rule public TemporaryFolder temp = new TemporaryFolder();` → `@TempDir private Path temp;`，`temp.newFolder("x")` → `temp.resolve("x").toString()`，`temp.getRoot()` → `temp.toFile()`。
- 断言：`Assert.assertEquals(expected, actual)` → `assertThat(actual).isEqualTo(expected)`；`Assert.assertFalse(x)` → `assertThat(x).isFalse()`；`Assert.assertNull(x)` → `assertThat(x).isNull()`；`Assert.assertArrayEquals(a, b)` 等 byte[] 比较并入 AssertJ `isEqualTo`。
- 假设：`Assume.assumeFalse(msg, cond)` → `assumeThat(cond).as(msg).isFalse()`。

## 修改详情

### `mr/build.gradle`

**修改目的**：在 iceberg-mr 子项目的 `test` 块中启用 JUnit 5 Platform，使测试以 JUnit Jupiter 引擎运行。

**工作逻辑**：

```groovy
  test {
    useJUnitPlatform()
  }
```

这是迁移的先决条件：没有 `useJUnitPlatform()`，JUnit 5 注解不会被识别。

### `mr/src/test/java/org/apache/iceberg/mr/TestCatalogs.java`

**修改目的**：把 catalog 加载相关的测试迁移到 JUnit 5，并改用 `@TempDir` 与 AssertJ。

**工作逻辑**：`@Rule public TemporaryFolder temp` → `@TempDir private Path temp`；`@Before` → `@BeforeEach`；`temp.newFolder(...)` → `temp.resolve(...).toString()`；`temp.getRoot()` → `temp.toFile()`；`Assert.assertEquals(...)` → `assertThat(...).isEqualTo(...)`；`Assertions.assertThatThrownBy(...)` 改为静态导入的 `assertThatThrownBy(...)`。

### `mr/src/test/java/org/apache/iceberg/mr/hive/HiveIcebergTestUtils.java`

**修改目的**：测试工具类的断言迁移到 AssertJ。

**工作逻辑**：移除 `org.junit.Assert` 导入，引入 `org.assertj.core.api.Assertions.assertThat`。`assertEquals(expected, actual)` → `assertThat(actual).isEqualTo(expected)`；OffsetDateTime 比较改为先取 `actual.toInstant()` 再 `isEqualTo(expected.toInstant())`；`Assert.assertArrayEquals((byte[]) expected, (byte[]) actual)` 分支被合并进统一的 `assertThat(actual.get(i)).isEqualTo(expected.get(i))`（即 byte[] 也走 AssertJ 比较）；`assertEquals(size1, size2)` → `hasSameSizeAs`；`assertFalse(new File(...).exists())` → `assertThat(...).doesNotExist()`；`assertEquals(dataFileNum, dataFiles.size())` → `assertThat(dataFiles).hasSize(dataFileNum)`。

### `mr/src/test/java/org/apache/iceberg/mr/hive/TestDeserializer.java`

**修改目的**：反序列化测试迁移到 JUnit 5 + AssertJ，含 `Assume` → `assumeThat`。

**工作逻辑**：`Assert.assertEquals(expected, actual)` → `assertThat(actual).isEqualTo(expected)`（注意参数顺序对调以符合 AssertJ「actual 在前」的惯例）；`Assert.assertNull(deserializer.deserialize(null))` → `assertThat(...).isNull()`；`Assume.assumeFalse("No test yet for Hive3 (Date/Timestamp creation)", HiveVersion.min(HiveVersion.HIVE_3))` → `assumeThat(HiveVersion.min(HiveVersion.HIVE_3)).as("No test yet for Hive3 (Date/Timestamp creation)").isFalse()`。

### ObjectInspector 系列测试

**修改目的**：将 10 个 ObjectInspector 测试统一迁移到 JUnit 5 / AssertJ。

涉及文件（行为一致，均为 API 替换）：

- `mr/src/test/java/org/apache/iceberg/mr/hive/TestIcebergBinaryObjectInspector.java`
- `mr/src/test/java/org/apache/iceberg/mr/hive/TestIcebergDateObjectInspector.java`
- `mr/src/test/java/org/apache/iceberg/mr/hive/TestIcebergDecimalObjectInspector.java`
- `mr/src/test/java/org/apache/iceberg/mr/hive/TestIcebergFixedObjectInspector.java`
- `mr/src/test/java/org/apache/iceberg/mr/hive/TestIcebergObjectInspector.java`
- `mr/src/test/java/org/apache/iceberg/mr/hive/TestIcebergRecordObjectInspector.java`
- `mr/src/test/java/org/apache/iceberg/mr/hive/TestIcebergTimeObjectInspector.java`
- `mr/src/test/java/org/apache/iceberg/mr/hive/TestIcebergTimestampObjectInspector.java`
- `mr/src/test/java/org/apache/iceberg/mr/hive/TestIcebergTimestampWithZoneObjectInspector.java`
- `mr/src/test/java/org/apache/iceberg/mr/hive/TestIcebergUUIDObjectInspector.java`

**工作逻辑**：统一替换注解导入（`org.junit.Test` → `org.junit.jupiter.api.Test`，`@Before` → `@BeforeEach`），断言导入（`org.junit.Assert` → AssertJ `assertThat`），并把 `Assert.assertEquals` / `Assert.assertTrue` / `Assert.assertNull` 等调用改写为 AssertJ 流式断言。`TestIcebergObjectInspector` 体量最大（约 184 行改动），同样遵循上述替换规则。

### `mr/src/test/java/org/apache/iceberg/mr/hive/TestHiveIcebergFilterFactory.java`、`TestHiveIcebergOutputCommitter.java`、`TestHiveIcebergSerDe.java`

**修改目的**：Hive-Iceberg 集成相关测试迁移到 JUnit 5 / AssertJ。

**工作逻辑**：与上述文件一致的 API 替换：JUnit 4 注解/断言 → JUnit 5 注解 + AssertJ 断言；`@Before` → `@BeforeEach`；必要处把 `@Rule TemporaryFolder` 改为 `@TempDir Path`。

## 小结

通过在 `mr/build.gradle` 启用 `useJUnitPlatform()` 并将 16 个测试/工具类文件从 JUnit 4 + `org.junit.Assert` 统一迁移到 JUnit 5 Jupiter 注解与 AssertJ 流式断言，本提交完成了 mr 模块测试套件向 JUnit 5 的现代化迁移，与 Iceberg 其余模块的测试栈保持一致。
