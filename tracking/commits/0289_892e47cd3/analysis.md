# 提交 0289：API: Support parameterized tests at class-level with JUnit5 (#9161)

## 提交信息

- **序号**：0289 / 4088
- **哈希**：892e47cd329b271517dd6ac5cf6b2591092b1146
- **短哈希**：892e47cd3
- **日期**：2023-12-19 11:18:57 +0100
- **作者**：Gianluca Principini
- **提交说明**：API: Support parameterized tests at class-level with JUnit5 (#9161)
- **PR/Issue**：#9161

## 总体目的

Iceberg 项目正在从 JUnit 4 向 JUnit 5 迁移。JUnit 4 提供 `@RunWith(Parameterized.class)` 配合 `@Parameterized.Parameter` 字段注解和 `@Parameterized.Parameters` 静态方法注解来实现参数化测试，且支持「字段注入」风格——即把参数值直接注入到 `@Parameter` 标注的字段，而不必走构造函数。这种风格对原本不是为参数化设计的测试类（构造函数已经接受其他依赖）特别友好。

JUnit 5 原生提供的是 `@ParameterizedTest` + `@MethodSource`/`@CsvSource` 等机制，但它要求每个测试方法都单独标注 `@ParameterizedTest`，且参数是通过方法签名（构造函数参数或方法参数）注入的，**并不原生支持**「在类级别声明参数、把参数注入到字段」这种 JUnit 4 风格的写法。这意味着，那些用 JUnit 4 `@Parameterized` 写就、依赖字段注入的测试类，在迁到 JUnit 5 时会面临「要么重写为构造函数参数风格、要么自己实现一个 JUnit 5 扩展」的选择。前者改动量大且会破坏字段注入带来的灵活性，后者则可以一次性解决所有类似测试类的迁移问题。

本提交的目的就是在 Iceberg 的 `iceberg-api` 测试源码集中引入一组 JUnit 5 扩展（extension）和注解，提供与 JUnit 4 `@Parameterized` 等价的能力，使测试类可以用 `@ExtendWith(ParameterizedTestExtension.class)` + `@Parameters` + `@Parameter` + `@TestTemplate` 的组合实现类级别字段注入式参数化测试，从而让 Iceberg 测试套件在迁移到 JUnit 5 时不必放弃这种风格。该实现直接借鉴自 Apache Flink 仓库的 `flink-test-utils-junit` 模块（`ParameterizedTestExtension`、`Parameter`、`Parameters`），并在 LICENSE 中补充了对 Flink 代码来源的声明。

作为该扩展的第一个实际应用，本提交同时把 `parquet` 模块的 `TestDictionaryRowGroupFilter` 从 JUnit 4 `@RunWith(Parameterized.class)` 风格迁到新的 JUnit 5 扩展，作为该机制的「样板」用例，验证可用性、并指导后续更多测试类按此模式迁移。

## 如何达成设计目的

整体设计思路是「实现一个 JUnit 5 的 `TestTemplateInvocationContextProvider` 扩展，在测试类初始化阶段读取被 `@Parameters` 标注的静态方法的返回值，对每一组参数值构造一个 `TestTemplateInvocationContext`，并通过 `BeforeEachCallback` 把参数值注入到被 `@Parameter` 标注的字段；同时支持构造函数参数风格作为字段注入风格的回退」。

具体而言：

1. **三个新文件**：
   - `Parameter.java`：字段注解，等价于 JUnit 4 的 `@Parameterized.Parameter`，通过 `index()` 指定参数在参数组中的位置（默认 0）。
   - `Parameters.java`：方法注解，等价于 JUnit 4 的 `@Parameterized.Parameters`，标注在返回 `Object[][]` 或 `Collection` 的静态方法上，并可指定测试名模板 `name()`。
   - `ParameterizedTestExtension.java`：核心扩展，实现 `TestTemplateInvocationContextProvider`，负责发现 `@Parameters` 方法、解析参数、为每组参数构造调用上下文、并按字段注入或构造函数参数两种风格分别处理。

2. **`build.gradle`**：让 `iceberg-parquet` 模块的测试依赖新增 `iceberg-api` 的 `testArtifacts` 配置，因为 `Parameter`/`Parameters`/`ParameterizedTestExtension` 三个类位于 `iceberg-api` 的 test sourceSet，需要跨模块共享。

3. **`TestDictionaryRowGroupFilter.java`**：作为首个迁移样例，把 `@RunWith(Parameterized.class)` 替换为 `@ExtendWith(ParameterizedTestExtension.class)`，把 `@Test` 替换为 `@TestTemplate`，把 `@Parameterized.Parameters` 替换为 `@Parameters`，把 `@Parameterized.Parameter` 字段替换为 `@Parameter` 字段，把 JUnit 4 的 `@Before`/`@Rule TemporaryFolder`/`Assume` 替换为 JUnit 5 的 `@BeforeEach`/`@TempDir`/`assumeThat`，并把 `TestHelpers.assertThrows` 替换为 AssertJ 的 `assertThatThrownBy`。

4. **LICENSE**：补充对 Apache Flink 代码来源的声明（三个文件），满足来源标注的合规要求。

## 修改详情

### `LICENSE`

**修改目的**：声明本仓库引入的代码源自 Apache Flink，满足来源标注与许可证兼容性要求。

**工作逻辑**：在 LICENSE 文件末尾新增一段，明确指出本产品包含了来自 Apache Flink 的代码，并具体列出三个文件：`ParameterizedTestExtension.java`（类级别参数化测试逻辑）、`Parameters.java`（参数提供者注解）、`Parameter.java`（参数字段注解）。声明 Copyright 1999-2022 The Apache Software Foundation、主页 https://flink.apache.org/、许可证 https://www.apache.org/licenses/LICENSE-2.0。

### `api/src/test/java/org/apache/iceberg/Parameter.java`（新增）

**修改目的**：提供字段注入式参数化测试中标注字段位置的字段注解，等价 JUnit 4 的 `@Parameterized.Parameter`。

**工作逻辑**：
- 注解定义为 `@Target(ElementType.FIELD)` + `@Retention(RetentionPolicy.RUNTIME)`，运行时保留以便扩展通过反射读取。
- 只有一个属性 `int index() default 0;`，表示该字段对应参数组中的第几个值（默认 0）。JavaDoc 说明：假设参数化测试只有一个 `@Parameter` 字段时，index 默认 0 即可。注释明确说明此实现取自 Flink 仓库，与 Flink 唯一的差异是把字段名从 Flink 的原名改为更直观的 `index`。

### `api/src/test/java/org/apache/iceberg/Parameters.java`（新增）

**修改目的**：提供标注参数提供者方法的方法注解，等价 JUnit 4 的 `@Parameterized.Parameters`。

**工作逻辑**：
- `@Retention(RetentionPolicy.RUNTIME)` + `@Target(ElementType.METHOD)`，运行时保留、只能标注在方法上。
- 只有一个属性 `String name() default "{index}";`，作为测试用例显示名模板，默认用 `{index}` 占位符（调用序号）。扩展在生成测试显示名时会用 `MessageFormat` 把参数值代入此模板。

### `api/src/test/java/org/apache/iceberg/ParameterizedTestExtension.java`（新增）

**修改目的**：核心 JUnit 5 扩展实现，把 `@Parameters` 方法返回的参数集合展开为多次测试调用，并通过字段注入或构造函数参数解析两种方式把参数值传给测试实例。

**工作逻辑**：

该类实现 `TestTemplateInvocationContextProvider` 接口（JUnit 5 的 `@TestTemplate` 扩展点）。被 `@ExtendWith(ParameterizedTestExtension.class)` 标注的测试类中所有 `@TestTemplate` 方法都会被该扩展处理。

关键流程：

1. **`supportsTestTemplate`**：直接返回 `true`，表示该扩展支持所有 `@TestTemplate` 方法。

2. **`provideTestTemplateInvocationContexts`**：核心入口。
   - 通过 `AnnotationSupport.findAnnotatedMethods(testClass, Parameters.class, TOP_DOWN)` 查找被 `@Parameters` 标注的方法。要求有且仅有一个，否则抛 `IllegalStateException`。
   - 反射调用该方法（`setAccessible(true)` + `invoke(null)`）拿到参数集合，并把它存入 `ExtensionContext.Store`（NAMESPACE 为 `"parameterized"`，key 为 `"parameters"`），便于其他扩展或测试方法读取。
   - 校验返回值非空。
   - 支持两种返回类型：
     - `Object[][]`：直接 `Arrays.stream` 成 `Stream<Object[]>`。
     - `Collection`：把每个元素映射为 `Object[]`（如果元素本身是 `Object[]` 就直接用，否则包装成单元素数组）。
     - 其他类型抛 `IllegalStateException`。
   - 读取 `@Parameters` 的 `name()` 作为测试名模板，传给 `createContextForParameters`。

3. **`createContextForParameters`**：
   - 用 `AnnotationSupport.findAnnotatedFields(testClass, Parameter.class)` 查找被 `@Parameter` 标注的字段。
   - 如果找不到任何 `@Parameter` 字段，走「构造函数参数」风格：对每组参数值构造一个 `ConstructorParameterResolverInvocationContext`，它内部提供 `ConstructorParameterResolver`（实现 `ParameterResolver`），通过 `supportsParameter` 永真、`resolveParameter` 按 `parameterContext.getIndex()` 返回对应位置的参数值，使测试类可以通过构造函数参数接收参数。
   - 如果找到 `@Parameter` 字段，走「字段注入」风格：把每个字段按其 `@Parameter.index()` 存入 `ExtensionContext.Store`（key 为 `"parameterField_" + index`），然后对每组参数值构造一个 `FieldInjectingInvocationContext`，它内部提供 `FieldInjectingHook`（实现 `BeforeEachCallback`），在 `beforeEach` 中把 `parameterValues[i]` 通过反射 `set` 到对应 index 的字段上（先 `setAccessible(true)`）。

4. **显示名**：两个 `InvocationContext` 都实现 `getDisplayName(int invocationIndex)`，如果模板就是默认的 `"{index}"` 则回退到父类默认实现（显示调用序号），否则用 `MessageFormat.format(testNameTemplate, parameterValues)` 把参数值代入模板，生成形如 `writerVersion=PARQUET_1_0` 这样的可读测试名。

5. **常量与辅助**：定义 `NAMESPACE`、`PARAMETERS_STORE_KEY`、`PARAMETER_FIELD_STORE_KEY_PREFIX`、`INDEX_TEMPLATE` 等常量，以及 `getParameterFieldStoreKey`/`getParameterField` 辅助方法用于在 `Store` 中存取字段反射对象。

### `build.gradle`

**修改目的**：让 `iceberg-parquet` 模块的测试能够访问 `iceberg-api` 模块测试源码中的扩展类。

**工作逻辑**：在 `project(':iceberg-parquet')` 的依赖块中新增一行 `testImplementation project(path: ':iceberg-api', configuration: 'testArtifacts')`。`testArtifacts` 是 Iceberg 项目为跨模块共享 test 类而定义的配置（在 `iceberg-api` 的 `build.gradle` 中通过 `testArtifacts` configuration 把 test sourceSet 的输出暴露出去）。这一改动让 `TestDictionaryRowGroupFilter` 能 `import org.apache.iceberg.Parameter` 等位于 `iceberg-api` test sourceSet 的类。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestDictionaryRowGroupFilter.java`

**修改目的**：作为新扩展的首个实际迁移样例，把该测试类从 JUnit 4 参数化风格迁移到 JUnit 5 + `ParameterizedTestExtension` 风格，验证扩展可用性并作为后续迁移的参考。

**工作逻辑**：

1. **import 与注解迁移**：
   - 类注解 `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`。
   - 新增 import `Parameter`、`Parameters`、`ParameterizedTestExtension`、JUnit 5 的 `BeforeEach`、`TestTemplate`、`ExtendWith`、`TempDir`、AssertJ 的 `assertThatThrownBy`、`Assumptions.assumeThat`、`java.nio.file.Path`、`java.util.Collection`。删除 JUnit 4 的 `Assume`、`Before`、`Rule`、`Test`、`TemporaryFolder`、`RunWith`、`Parameterized` 等 import，以及静态导入 `PARQUET_1_0`/`PARQUET_2_0`（改为 `WriterVersion.PARQUET_1_0`）。

2. **字段与参数声明**：
   - 原 `private final WriterVersion writerVersion;` + 构造函数 `public TestDictionaryRowGroupFilter(WriterVersion writerVersion)` → `@Parameter private WriterVersion writerVersion;` 字段注入式（去掉构造函数）。
   - 原 `@Parameterized.Parameters public static List<WriterVersion> writerVersions()` → `@Parameters(name = "writerVersion={0}") public static Collection<WriterVersion> parameters()`。返回类型从 `List` 改为 `Collection`，方法名改为 `parameters`，并把 `name` 模板设为 `"writerVersion={0}"`，这样测试显示名会变成 `writerVersion=PARQUET_1_0` 之类，便于辨认。

3. **临时目录**：
   - `@Rule public TemporaryFolder temp = new TemporaryFolder();` → `@TempDir private Path temp;`。`temp.newFile()` → `temp.toFile()`（因为 JUnit 5 的 `@TempDir` 注入的是 `Path`，需要 `toFile()` 转回 `File` 以兼容原有 Parquet API）。

4. **`@Before` → `@BeforeEach`**：方法 `createInputFile()` 的注解从 `@Before` 改为 `@BeforeEach`。

5. **`@Test` → `@TestTemplate`**：所有测试方法注解从 `@Test` 改为 `@TestTemplate`，因为参数化测试必须使用 `@TestTemplate`，每个参数组都会触发一次方法调用。共约 30+ 处方法改名。

6. **断言风格迁移**：
   - `TestHelpers.assertThrows(msg, ExceptionClass, msgContains, () -> ...)` → `assertThatThrownBy(() -> ...).isInstanceOf(...).hasMessageContaining(...)`。如 `testAssumptions` 中 8 处断言、`testMissingColumn`、`testMissingDictionaryPageForColumn` 等。AssertJ 的流式断言可读性更好，且不再依赖 `TestHelpers`。
   - `Assume.assumeTrue(msg, condition)` → `assumeThat(...).contains(...)`，见 `testFixedLenByteArray`，把 JUnit 4 `Assume` 改为 AssertJ 的 `Assumptions.assumeThat`（注意是 Assumptions 不是 Assertions，用于条件性跳过测试）。

7. **清理**：删除 `import org.apache.iceberg.TestHelpers;`、删除 `import java.util.List;`（改为 `Collection`）、删除 `import org.junit.rules.TemporaryFolder;` 等。

## 小结

本提交为 Iceberg 在 JUnit 4 → 5 迁移过程中补齐了一个关键缺口：JUnit 5 原生不支持「类级别字段注入式参数化测试」，而这正是 JUnit 4 `@RunWith(Parameterized.class)` 的常见用法。通过引入借鉴自 Apache Flink 的三个测试工具类（`Parameter` 注解、`Parameters` 注解、`ParameterizedTestExtension` 扩展），Iceberg 测试套件得以在不重写测试结构的前提下平滑迁移。同时把 `TestDictionaryRowGroupFilter` 作为首个迁移样板，展示了从 `@RunWith(Parameterized.class)` 到 `@ExtendWith(ParameterizedTestExtension.class)` 的标准改写步骤（注解替换、字段注入、`@TestTemplate`、AssertJ 断言、`@TempDir` 等）。这一基础设施提交为后续大量 JUnit 4 参数化测试的迁移铺平了道路，价值在于降低迁移成本、保持测试可读性、并避免 JUnit 5 原生 API 的局限。
