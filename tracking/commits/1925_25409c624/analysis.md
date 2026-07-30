# 提交 1925：Spark 3.4: Migrate SparkRowLevelOperationsTestBase related tests to JUnit 5 (#12656)

## 提交信息

- **序号**：1925 / 4088
- **哈希**：25409c624e092f56c9a01bc281da9d94f6285f83
- **短哈希**：25409c624
- **日期**：2025-03-27 10:31:37 +0100
- **作者**：Tom Tanaka
- **提交说明**：Spark 3.4: Migrate SparkRowLevelOperationsTestBase related tests to JUnit 5 (#12656)
- **PR/Issue**：#12656

## 总体目的

Iceberg 各模块正逐步把测试从 JUnit 4 迁移到 JUnit 5。本提交针对 Spark 3.4 模块的行级操作（row-level operations）测试体系做迁移：以 `SparkRowLevelOperationsTestBase` 为基类的一组参数化测试（`TestDelete`、`TestMerge`、`TestUpdate` 及其 CopyOnWrite/MergeOnRead 子类）原先基于 JUnit 4 的 `@RunWith(Parameterized.class)` + 构造器注入参数 + `org.junit.Assert`，本次改为 JUnit 5 的 `@ExtendWith(ParameterizedTestExtension.class)`（Iceberg 自定义扩展）+ `@Parameter` 字段注入 + AssertJ 断言。

迁移动机：与全仓库 JUnit 5 化方向保持一致；JUnit 5 的扩展模型更灵活、参数注入更清晰；AssertJ 断言比 JUnit `Assert` 更可读。同时把 `fileFormat` 字段类型从 `String` 改为 `FileFormat` 枚举，提升类型安全。

## 如何达成设计目的

1. **基类 `SparkRowLevelOperationsTestBase`**：
   - 去掉 `@RunWith(Parameterized.class)`，改为 `@ExtendWith(ParameterizedTestExtension.class)`；父类由 `SparkExtensionsTestBase` 改为 `ExtensionsTestBase`（基类自身也做了 JUnit 5 化）。
   - 删除接收 10 个参数的构造器与 `protected final` 字段，改为 `@Parameter(index = N)` 注解的可变实例字段（index 0-2 留给 catalog/implementation/config 由父类处理，3-9 为 fileFormat/vectorized/distributionMode/fanoutEnabled/branch/planningMode/formatVersion）。
   - `@Parameters` 方法内把 `"orc"`/`"parquet"`/`"avro"` 字符串改为 `FileFormat.ORC/PARQUET/AVRO` 枚举。
   - `switch (fileFormat)` 改为枚举 case；`Assert.assertFalse(vectorized)` 改为 `assertThat(vectorized).isFalse()`。
   - `Assert.assertEquals` / `Assert.assertTrue` 改为 AssertJ `assertThat(...).isEqualTo(...)` / `.isIn(...)`。
   - `writeDataFile` 改用 `temp.resolve(fileFormat.addExtension(UUID.randomUUID().toString())).toFile()` 生成唯一文件名（替代 `temp.newFile()`），避免文件名冲突。
   - `isParquet()` 由 `equalsIgnoreCase` 改为 `equals(FileFormat.PARQUET)`。
2. **子类测试（TestDelete/TestMerge/TestUpdate 及 CoW/MoR 子类）**：
   - 删除各自的巨型构造器（仅转发给 super），改为继承基类的 `@Parameter` 字段。
   - 注解迁移：`@Test` → `@TestTemplate`（参数化测试在 JUnit 5 用 `@TestTemplate` + 扩展），`@BeforeClass` → `@BeforeAll`，`@After` → `@AfterEach`，`@Before` → `@BeforeEach`，`@Ignore` → `@Disabled` 等。
   - `org.junit.Assert.*` → AssertJ `assertThat(...)`；`org.junit.Assume.assumeTrue` → `assumeThat(...)`。
   - 部分类加 `@ExtendWith(ParameterizedTestExtension.class)`。
3. **build.gradle**：给 `iceberg-spark-extensions-3.4` 测试新增 `testImplementation libs.awaitility`（部分异步断言用 Awaitility）。
4. 测试用例 `fileFormat` 字符串字面量改为 `FileFormat.X` 枚举常量。

## 修改详情

### `spark/v3.4/build.gradle` (修改, +1 line)

**修改目的**：为扩展测试新增 awaitility 测试依赖。

**工作逻辑**：在 `iceberg-spark-extensions-3.4` 的 testImplementation 中加入 `libs.awaitility`，供异步/重试断言使用。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/SparkRowLevelOperationsTestBase.java` (修改, +58/-48 lines)

**修改目的**：基类迁移到 JUnit 5 参数化扩展，fileFormat 改为枚举。

**工作逻辑**：

- 注解：`@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`；父类 `SparkExtensionsTestBase` → `ExtensionsTestBase`。
- 字段：删除构造器与 `final` 字段，改为 `@Parameter(index=3..9)` 注解的可变字段，类型从 `String fileFormat` 改为 `FileFormat fileFormat`。
- `@Parameters` 数据：`"orc"`/`"parquet"`/`"avro"` → `FileFormat.ORC/PARQUET/AVRO`。
- `createAndInitTable` 的 `switch` 改枚举 case；avro 分支 `Assert.assertFalse` → `assertThat(vectorized).isFalse()`。
- `validateSnapshot`/`validateProperty`：`Assert.assertEquals`/`Assert.assertTrue` → `assertThat(...).isEqualTo(...)`/`.isIn(...)`。
- `writeDataFile`：`Files.localOutput(temp.newFile())` → `Files.localOutput(temp.resolve(fileFormat.addExtension(UUID.randomUUID().toString())).toFile())`。
- `isParquet()`：`equalsIgnoreCase` → `equals(FileFormat.PARQUET)`。

### `TestDelete.java` / `TestMerge.java` / `TestUpdate.java` 及 CopyOnWrite/MergeOnRead 子类 (修改, 大量行)

**修改目的**：子类跟随基类迁移到 JUnit 5。

**工作逻辑**（以 `TestDelete` 为典型）：

- 删除接收 10 参数并转发 super 的构造器。
- import 与注解：`org.junit.Test` → `org.junit.jupiter.api.TestTemplate`；`org.junit.BeforeClass` → `org.junit.jupiter.api.BeforeAll`；`org.junit.After` → `org.junit.jupiter.api.AfterEach`；加 `@ExtendWith(ParameterizedTestExtension.class)`。
- `Assert.assertEquals("msg", expected, actual)` → `assertThat(actual).as("msg").isEqualTo(expected)`；`Assert.assertEquals("msg", expected, Iterables.size(...))` → `assertThat(...).as("msg").hasSize(...)`。
- 新增 `import org.awaitility.Awaitility`（部分用例需要）。
- 其余子类（`TestCopyOnWriteDelete` 等）做同样的构造器删除与注解/断言迁移，行数大幅减少（构造器代码删除）。

### 3.5 模块对应测试 (修改, 少量)

**修改目的**：同步 3.5 模块中受影响的少量适配（如 `TestCopyOnWriteDelete` 等），保持两版本一致。

**工作逻辑**：3.5 模块的小幅改动（构造器/注解相关），与 3.4 迁移配套。

## 总结

本提交把 Spark 3.4 行级操作测试体系（`SparkRowLevelOperationsTestBase` 及 `TestDelete`/`TestMerge`/`TestUpdate` 与其 CoW/MoR 子类）从 JUnit 4 迁移到 JUnit 5：用 Iceberg 自定义 `ParameterizedTestExtension` 替代 `@RunWith(Parameterized.class)`，构造器参数注入改为 `@Parameter` 字段注入，`@Test`→`@TestTemplate`，`Assert`→AssertJ，`fileFormat` 由 String 改为 `FileFormat` 枚举，并新增 awaitility 依赖、改进测试数据文件命名。迁移后测试代码更简洁、类型更安全，与全仓库 JUnit 5 化方向一致。
