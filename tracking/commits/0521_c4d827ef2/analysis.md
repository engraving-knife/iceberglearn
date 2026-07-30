# 提交 0521：Spark: Migrate tests to JUnit5

## 提交信息

- **序号**：0521 / 4088
- **哈希**：c4d827ef2b6ca5d801e877623195e8ceeae88679
- **短哈希**：c4d827ef2
- **日期**：2024-02-20 09:17:46 +0100
- **作者**：Tom Tanaka <43331405+tomtongue@users.noreply.github.com>
- **提交说明**：Spark: Migrate tests to JUnit5 (#9670)
- **PR/Issue**：#9670

## 总体目的

本提交将 Spark v3.5 模块下 `spark-extensions` 测试子模块中的全部行级操作测试从 JUnit 4 迁移到 JUnit 5（Jupiter）。这是 Apache Iceberg 项目持续多个版本的"全仓库 JUnit 5 迁移"工作的组成部分——API 模块早在 PR #9161（提交 892e47cd3，`API: Support parameterized tests at class-level with JUnit5`）就已引入了自定义的 `ParameterizedTestExtension`、`@Parameter`、`@Parameters` 三个 JUnit 5 扩展点（位于 `api/src/test/java/org/apache/iceberg/`），用于替代 JUnit 4 的 `@RunWith(Parameterized.class)` + 构造器注入式参数化测试。本提交则把这些扩展点在 Spark v3.5 测试侧落地使用。

迁移要解决的核心问题包括：

1. **统一测试框架**：消除仓库内 JUnit 4 与 JUnit 5 并存的双轨局面，使 Spark v3.5 测试与已迁移的 API/核心模块保持一致，减少依赖混乱。
2. **改用更现代的断言与假设语义**：JUnit 4 的 `org.junit.Assert.*` 与 `org.junit.Assume.*` 被 AssertJ 的流式 `assertThat` / `assumeThat` 替代，错误信息更可读、组合性更强。
3. **修复并发测试的脆弱性**：原 `testDeleteWithSerializableIsolation` / `testDeleteWithSnapshotIsolation` 等并发测试使用 `while (barrier.get() < n) sleep(10);` 形式的忙等待，一旦条件永不满足会无限挂起。迁移过程中引入 Awaitility 提供带超时的轮询，使测试失败时能快速报错而非卡死。
4. **统一参数化机制**：用字段注入（`@Parameter(index=...)`）替代构造器注入，简化子类构造器模板代码。

## 如何达成设计目的

整体设计思路是"逐文件机械式迁移 + 必要的逻辑修补"，主要技术路径如下：

1. **依赖切换**：在 `spark/v3.5/build.gradle` 中为 `iceberg-spark-extensions` 测试新增 `testImplementation libs.awaitility`，为并发测试提供 Awaitility 库。原有 JUnit 5（`org.junit.jupiter.api.*`）依赖通过其他 build.gradle 配置已传递可用，无需额外声明。

2. **参数化测试机制切换**：从 JUnit 4 的 `@RunWith(Parameterized.class)` + 构造器接收 `(catalogName, implementation, config, fileFormat, ...)` 改为 JUnit 5 的 `@ExtendWith(ParameterizedTestExtension.class)` + 用 `@Parameter(index = N)` 注解的字段注入。Iceberg 自定义的 `ParameterizedTestExtension`（API 模块提供）实现了 JUnit 5 的 `TestTemplate` + `ParameterResolver` 机制，在每次测试调用前从 `@Parameters` 静态方法返回的二维数组中按行注入到带 `@Parameter` 的字段上。`catalogName`/`implementation`/`config` 三个前缀字段（index 0/1/2）由父类 `ExtensionsTestBase`/`CatalogTestBase` 接收，子类只声明 index ≥ 3 的字段。

3. **生命周期注解切换**：
   - `@Test` → `@TestTemplate`（参数化测试在 JUnit 5 下必须用 `@TestTemplate`，因为每次调用都是一次"模板调用"）
   - `@Before` → `@BeforeEach`，`@After` → `@AfterEach`
   - `@BeforeClass` → `@BeforeAll`（注意方法仍需 `static`）

4. **断言/假设切换**：
   - `Assert.assertEquals(msg, expected, actual)` → `assertThat(actual).as(msg).isEqualTo(expected)`
   - `Assert.assertTrue(msg, cond)` → `assertThat(cond).as(msg).isTrue()`
   - `Assert.assertFalse(cond)` → `assertThat(cond).isFalse()`
   - `Iterables.size(table.snapshots())` → `assertThat(table.snapshots()).hasSize(n)`（避免 Guava 间接调用）
   - `Assume.assumeFalse/assumeTrue` → AssertJ 的 `assumeThat(...).isTrue()/isNotEqualToIgnoringCase(...)`

5. **基类切换**：从 `SparkExtensionsTestBase`（JUnit 4 版）切换到 `ExtensionsTestBase`（JUnit 5 版，使用 `@BeforeAll` 启动 metastore/spark）。`ExtensionsTestBase` 继承自 `CatalogTestBase`，提供共享的 `temp`（JUnit 5 的 `@TempDir` 风格的 `Path`）以及 `spark`/`metastore`/`catalog` 静态字段。删除了原 `@Rule public TemporaryFolder temp = new TemporaryFolder();`，改用基类提供的 `temp`（一个 `Path`），所以原 `temp.newFolder()` → `temp.toFile()`，原 `temp.newFile()` → `temp.resolve(...).toFile()`。

6. **`FileFormat` 枚举化**：原参数表中 `"parquet"`/`"orc"`/`"avro"` 字符串字面量改为 `FileFormat.PARQUET`/`ORC`/`AVRO` 枚举常量，`fileFormat` 字段类型从 `String` 改为 `FileFormat`。这样 `switch (fileFormat)` 可以直接使用 `case PARQUET:` 等枚举 case，避免字符串大小写比较的脆弱性；`isParquet()` 由 `equalsIgnoreCase` 改为 `equals(FileFormat.PARQUET)`。`writeDataFile` 中生成临时文件路径改用 `fileFormat.addExtension(UUID.randomUUID().toString())`，确保文件扩展名与格式匹配。

7. **并发测试引入 Awaitility**：把 `while (barrier.get() < numOperations * 2) sleep(10);` 改写为 `Awaitility.await().pollInterval(10, MILLISECONDS).atMost(5, SECONDS).until(() -> barrier.get() >= currentNumOperations * 2);`，并删除了 append 循环里多余的 `sleep(10)`。这保留了原同步语义（等待对方到达 barrier），但加入了 5 秒上限，避免死锁时无限挂起。注意 lambda 捕获需将 `numOperations` 拷贝为 effectively final 的 `currentNumOperations`。

## 修改详情

### `spark/v3.5/build.gradle`

**修改目的**：为 `iceberg-spark-extensions-${sparkMajorVersion}_${scalaVersion}` 测试添加 Awaitility 依赖。

**工作逻辑**：在 `testImplementation` 区块新增 `testImplementation libs.awaitility;`，与已有的 `avro.avro`、`parquet.hadoop` 并列。Awaitility 在 `TestDelete` 的并发隔离测试中被显式 `import org.awaitility.Awaitility;` 使用。该依赖仅作用于测试编译与运行期，不会进入发布产物。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/SparkRowLevelOperationsTestBase.java`

**修改目的**：作为所有行级操作测试（Delete/Merge/Update/CopyOnRead/MergeOnRead）的抽象基类，承担参数表声明与公共校验逻辑。本文件是迁移的"中枢"。

**工作逻辑**：

- 类注解由 `@RunWith(Parameterized.class)` 改为 `@ExtendWith(ParameterizedTestExtension.class)`，父类由 `SparkExtensionsTestBase` 改为 `ExtensionsTestBase`。
- 删除了 8 参数构造器与 6 个 `final` 字段，改为 6 个带 `@Parameter(index = 3..8)` 的可变字段（`fileFormat`、`vectorized`、`distributionMode`、`fanoutEnabled`、`branch`、`planningMode`）。index 0/1/2（catalogName/implementation/config）由父类 `CatalogTestBase` 处理。
- `@Parameters` 注解的导入由 `org.junit.runners.Parameterized.Parameters` 改为 `org.apache.iceberg.Parameters`；参数表中 `"parquet"` 等改为 `FileFormat.PARQUET` 等。
- `switch (fileFormat)` 的 case 改为枚举常量 `case PARQUET:`，avro 分支的 `Assert.assertFalse(vectorized)` 改为 `assertThat(vectorized).isFalse()`。
- `validateProperty` / `validateSnapshot` 中的 `Assert.assertEquals` / `Assert.assertTrue` 改为 AssertJ 流式断言，`expectedValues.contains(actual)` 改用 `assertThat(actual).isIn(expectedValues)`。
- `writeDataFile` 改用 `temp.resolve(fileFormat.addExtension(UUID.randomUUID().toString())).toFile()` 生成带正确扩展名的临时输出文件。
- `isParquet()` 由 `fileFormat.equalsIgnoreCase(FileFormat.PARQUET.name())` 改为 `fileFormat.equals(FileFormat.PARQUET)`。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestDelete.java`

**修改目的**：迁移 DELETE 测试套件（最大、改动最复杂的文件，298 行变动）。

**工作逻辑**：

- 删除 8 参数构造器，类上加 `@ExtendWith(ParameterizedTestExtension.class)`。
- `@BeforeClass setupSparkConf` → `@BeforeAll`，`@After removeTables` → `@AfterEach`，所有 `@Test` → `@TestTemplate`。
- `Assume.assumeFalse("...", fileFormat.equals("avro"))` → `assumeThat(fileFormat).as("Avro does not support metadata delete").isNotEqualTo(FileFormat.AVRO)`，使用枚举避免字符串比较。
- `testDeleteWithSerializableIsolation` / `testDeleteWithSnapshotIsolation` 两个并发测试中的忙等待 `while (barrier.get() < numOperations * 2) sleep(10);` 改为 `Awaitility.await().pollInterval(10, MILLISECONDS).atMost(5, SECONDS).until(...)`，删除 append 后多余的 `sleep(10)`。`executorService.awaitTermination` 的 `Assert.assertTrue` 改为 `assertThat(...).as("Timeout").isTrue()`。
- 删除未再使用的 `import ...Iterables`，新增 `import org.awaitility.Awaitility;`、`import org.apache.iceberg.FileFormat;`、`import org.apache.iceberg.ParameterizedTestExtension;`、`import static org.assertj.core.api.Assertions.assertThat;`。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMerge.java`

**修改目的**：迁移 MERGE 测试套件（276 行变动，规模仅次于 TestDelete）。

**工作逻辑**：与 TestDelete 同构——删除构造器、`@ExtendWith(ParameterizedTestExtension.class)`、生命周期注解替换、`Assert.*` → `assertThat`、`Assume.*` → `assumeThat`、`@Test` → `@TestTemplate`。该文件不涉及并发测试，故不引入 Awaitility。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestUpdate.java`

**修改目的**：迁移 UPDATE 测试套件（274 行变动）。

**工作逻辑**：与 TestMerge 同构。全量替换断言/生命周期注解/参数化机制。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestCopyOnWriteDelete.java`、`TestCopyOnWriteMerge.java`、`TestCopyOnWriteUpdate.java`

**修改目的**：迁移三个 CopyOnWrite 子类测试。

**工作逻辑**：每个文件主要是删除调用父类 8 参数构造器的子类构造器（约 13 行），并替换 `@Test` → `@TestTemplate`、`@After` → `@AfterEach`、`@Rule TemporaryFolder` 删除改用基类 `temp`、断言改 AssertJ。由于公共逻辑已下沉到 `SparkRowLevelOperationsTestBase`，子类改动较轻。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMergeOnReadDelete.java`、`TestMergeOnReadMerge.java`、`TestMergeOnReadUpdate.java`

**修改目的**：迁移三个 MergeOnRead 子类测试。

**工作逻辑**：与 CopyOnRead 系列同构——删构造器、换注解、换断言、改用基类 `temp`。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestConflictValidation.java`

**修改目的**：迁移冲突校验测试（43 行变动）。

**工作逻辑**：`@Test` → `@TestTemplate`，`@After` → `@AfterEach`，断言改 AssertJ。该类继承 `SparkRowLevelOperationsTestBase`，因此同样受参数化机制切换影响。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestWriteAborts.java`

**修改目的**：迁移写入中断测试（27 行变动）。

**工作逻辑**：

- 类注解加 `@ExtendWith(ParameterizedTestExtension.class)`，父类由 `SparkExtensionsTestBase` 改为 `ExtensionsTestBase`。
- 删除 `@Rule public TemporaryFolder temp = new TemporaryFolder();` 与构造器；`temp.newFolder()` 改为 `temp.toFile()`。
- `@After` → `@AfterEach`，`@Test` → `@TestTemplate`，`@Parameterized.Parameters` → `@Parameters`（Iceberg 包）。

## 小结

**成效**：Spark v3.5 行级操作测试套件完成 JUnit 5 迁移，与 API/核心模块的测试框架统一；并发测试因 Awaitility 引入而具备超时失败能力，CI 稳健性提升；AssertJ 流式断言使失败信息更直观。统计上 13 文件、+531/-723（净减 192 行），主要源于删除各子类重复的构造器模板与 `@Rule TemporaryFolder` 样板。

**影响范围**：仅 `spark/v3.5/spark-extensions` 测试目录与 `spark/v3.5/build.gradle`，不触及生产代码，对运行时行为零影响。`ParameterizedTestExtension` 等扩展点早在 API 模块就绪，本提交只是消费侧落地。

**回迁到 1.4.x 的注意事项**：

1. 1.4.x 分支需先确认 `api/src/test/java/org/apache/iceberg/{Parameter,ParameterizedTestExtension,Parameters}.java` 已存在（来自 PR #9161）。若 1.4.x 未合入 #9161，则本提交无法直接 cherry-pick，需先回迁 #9161。
2. 需确认 1.4.x 的 `spark/v3.5/spark-extensions/.../ExtensionsTestBase.java`（JUnit 5 版基类）已存在；否则子类 `extends ExtensionsTestBase` 会编译失败。可能需同步回迁该基类或保留原 `SparkExtensionsTestBase`。
3. `libs.awaitility` 版本目录项需在 1.4.x 的 `gradle/libs.versions.toml` 中存在；否则 build.gradle 引用会失败。
4. 若 1.4.x 仍依赖 JUnit 4 运行时（`junit-vintage-engine`），迁移后这些测试类不再被 vintage 引擎发现（因为注解全换），需确认 `useJUnitPlatform()` 已启用。
5. 字段注入使字段不再是 `final`，对线程安全敏感的并发测试需复核——本提交中并发测试的字段（`fileFormat` 等）均为只读参数，安全。
