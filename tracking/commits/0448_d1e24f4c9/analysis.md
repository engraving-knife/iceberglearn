# 提交 0448：Spark: Create ExtensionTestBase for migration to JUnit5 (#9613)

## 提交信息

- **序号**：0448
- **完整哈希**：d1e24f4c9571ab7fba66d692cde28188f265d34e
- **短哈希**：d1e24f4c9
- **日期**：2024-02-02 19:26:12 +0900
- **作者**：Tom Tanaka <43331405+tomtongue@users.noreply.github.com>
- **提交说明**：Spark: Create ExtensionTestBase for migration to JUnit5 (#9613)
- **关联 PR**：#9613

## 总体目的

本提交是 Iceberg Spark 模块测试体系从 JUnit 4 迁移到 JUnit 5 的又一关键步骤。在此之前的提交中，Iceberg 已经在 Spark 侧建立了 JUnit 5 的新基类链：`TestBase` → `TestBaseWithCatalog` → `CatalogTestBase`（位于 `org.apache.iceberg.spark` 包，使用 `@ExtendWith(ParameterizedTestExtension.class)` + Iceberg 自定义的 `@Parameter`/`@Parameters` + `@TestTemplate`），用以取代旧的 JUnit 4 链 `SparkTestBase` → `SparkCatalogTestBase` → `SparkExtensionsTestBase`。然而，Spark extensions 这一层（位于 `spark-extensions` 子项目）的测试基类此前还停留在 JUnit 4 的 `SparkExtensionsTestBase` 上，没有对应的 JUnit 5 对等物，导致 extensions 下的所有测试用例无法迁移。

本提交补齐了这块缺口：在 `spark-extensions` 下新建 `ExtensionsTestBase`（JUnit 5，`extends CatalogTestBase`），把原 `SparkExtensionsTestBase.startMetastoreAndSpark()` 中的 metastore + SparkSession 初始化逻辑以 `@BeforeAll` 静态方法的形式搬过来（并把对 `SparkTestBase.*` 静态字段的赋值改为对 `TestBase.*` 的赋值）。同时为了让 JUnit 5 测试能在 `iceberg-spark-extensions` 子项目里跑起来，在 `spark/v3.5/build.gradle` 的 extensions 子项目配置里加上了 `test { useJUnitPlatform() }`。

为了验证新基类链可用，本提交把 `TestAddFilesProcedure` 作为第一个迁移样例，从 `extends SparkExtensionsTestBase` 改为 `extends ExtensionsTestBase`，并完成全部 JUnit 4 → 5 的注解/断言/参数化改写。这是一个"搭脚手架 + 第一个示范用例"的提交：先把基础设施（新基类 + gradle 配置）铺好，再用一个真实测试验证模式可行，后续其他 extensions 测试类即可按相同套路批量迁移。

## 如何达成设计目的

实现路径有三块。第一，在 `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/` 下新建 `ExtensionsTestBase.java`：声明为 `abstract class ExtensionsTestBase extends CatalogTestBase`，用 `@BeforeAll public static void startMetastoreAndSpark()` 启动 `TestHiveMetastore`、把 `hiveConf` 与 `spark`、`catalog` 三个静态字段写到 `TestBase` 上（而非旧的 `SparkTestBase`），SparkSession 配置项（master、`spark.sql.extensions=IcebergSparkSessionExtensions`、动态分区覆盖、shuffle 分区数、AQE 随机开关等）与旧实现完全一致。第二，修改 `spark/v3.5/build.gradle`，在 `iceberg-spark-extensions-${sparkMajorVersion}_${scalaVersion}` 子项目的 `test { useJUnitPlatform() }`，让该子项目用 JUnit Platform 引擎跑测试，否则 JUnit 5 注解会被忽略。第三，把 `TestAddFilesProcedure` 从 `extends SparkExtensionsTestBase` 改为 `extends ExtensionsTestBase`，类上加 `@ExtendWith(ParameterizedTestExtension.class)`，构造函数改为 `@Parameter(index=3) int formatVersion` 字段注入（前 3 个 index 由 `CatalogTestBase` 接收），`@Before/@After/@Test/@Ignore/@Rule TemporaryFolder` 分别改为 `@BeforeEach/@AfterEach/@TestTemplate/@Disabled/@TempDir Path`，`Assert.assertEquals/Assume.assumeFalse` 改为 AssertJ `assertThat(...).isEqualTo(...)/assumeThat(...).isNotEqualTo(...)`，临时目录访问从 `temp.newFolder()`/`temp.newFile()` 改为 `temp.toFile()`/`temp.resolve(...).toFile()`。

## 修改详情

### `spark/v3.5/build.gradle`
**修改目的**：让 `iceberg-spark-extensions` 子项目用 JUnit Platform 运行测试，使 JUnit 5 注解生效。
**工作逻辑**：在 extensions 子项目的配置块里新增
```groovy
test {
  useJUnitPlatform()
}
```
`useJUnitPlatform()` 告诉 Gradle 的 `Test` 任务使用 JUnit Platform 引擎，这样 `@TestTemplate`/`@BeforeEach`/`@ExtendWith` 等 JUnit 5 注解才会被发现和执行。不加这一行，新迁移的测试类会"静默不跑"——编译通过但 0 测试执行，是迁移期最容易踩的坑。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/ExtensionsTestBase.java`（新增）
**修改目的**：提供 extensions 测试层的 JUnit 5 基类，作为 `SparkExtensionsTestBase`（JUnit 4）的对等替代。
**工作逻辑**：
- `public abstract class ExtensionsTestBase extends CatalogTestBase`：继承 JUnit 5 的 `CatalogTestBase`，自动获得 `@ExtendWith(ParameterizedTestExtension.class)`、`@Parameters` 工厂方法（HIVE/HADOOP/SPARK 三种 catalog 配置）、`@TempDir Path temp` 等能力。
- `@BeforeAll public static void startMetastoreAndSpark()`：与旧 `SparkExtensionsTestBase` 同名同语义，启动 `TestHiveMetastore`，把 `hiveConf` 赋给 `TestBase.hiveConf`；用 `SparkSession.builder()` 链式配置创建 SparkSession 并赋给 `TestBase.spark`；通过 `CatalogUtil.loadCatalog(HiveCatalog.class.getName(), "hive", ImmutableMap.of(), hiveConf)` 创建 HiveCatalog 并赋给 `TestBase.catalog`。
- 关键差异在于：把对 `SparkTestBase.metastore/hiveConf/spark/catalog` 静态字段的赋值改为对 `TestBase.*` 的赋值，因为新基类链挂在 `TestBase` 而非 `SparkTestBase` 下。其余 SparkSession 配置（`local[2]`、`spark.testing`、动态分区覆盖、`IcebergSparkSessionExtensions`、`METASTOREURIS`、shuffle 分区 4、metastore pruning fallback、text dataset nullability、AQE 随机布尔）与旧实现一字不差，保证迁移前后测试环境等价。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestAddFilesProcedure.java`
**修改目的**：作为第一个迁移样例，演示从 `SparkExtensionsTestBase`（JUnit 4）迁移到 `ExtensionsTestBase`（JUnit 5）的标准套路，验证新基类链可用。
**工作逻辑**（按改动类型分组）：
1. **类声明与基类**：`extends SparkExtensionsTestBase` 改为 `@ExtendWith(ParameterizedTestExtension.class) public class TestAddFilesProcedure extends ExtensionsTestBase`。删除接收 `(catalogName, implementation, config, formatVersion)` 的构造函数，`formatVersion` 改为 `@Parameter(index=3) private int formatVersion`（前三个参数 index 0/1/2 由 `CatalogTestBase` 通过 `@Parameter` 注入 `catalogName/implementation/config`）。`@Parameters` 工厂方法的 import 从 `org.junit.runners.Parameterized.Parameters` 改为 `org.apache.iceberg.Parameters`，方法签名保持 `public static Object[][] parameters()`。
2. **生命周期注解**：`@Before` → `@BeforeEach`、`@After` → `@AfterEach`、`@Test` → `@TestTemplate`、`@Ignore` → `@Disabled`。`@TestTemplate` 是 JUnit 5 在参数化场景下用的注解（配合 `ParameterizedTestExtension`，每个参数组合执行一次）。
3. **临时目录**：`@Rule public TemporaryFolder temp = new TemporaryFolder();` → `@TempDir private Path temp;`。访问方式随之改变：`temp.newFolder()` → `temp.toFile()`、`temp.newFile("test.avro")` → `temp.resolve("test.avro").toFile()`，因此 `setupTempDirs` 不再需要 try/catch `IOException`。
4. **断言**：`Assert.assertEquals(2L, result)` → `assertThat(result).isEqualTo(2L)`；`Assert.assertEquals(msg, expected, actual)` → `assertThat(actual).as(msg).isEqualTo(expected)` 或 `assertThat(...).as(msg).hasSize(n)`；`Assert.assertTrue(msg, matcher.find())` → `assertThat(matcher.find()).as(msg).isTrue()`；`Assume.assumeFalse(catalogName.equals("spark_catalog"))` → `assumeThat(catalogName).isNotEqualTo("spark_catalog")`。新增 `import static org.assertj.core.api.Assumptions.assumeThat`。
5. **import 调整**：删掉 `org.junit.*`、`org.junit.rules.TemporaryFolder`、`org.junit.runners.Parameterized.Parameters`、`java.io.IOException`、`java.util.Map`；新增 `org.junit.jupiter.api.*`、`org.junit.jupiter.api.extension.ExtendWith`、`org.junit.jupiter.api.io.TempDir`、`org.apache.iceberg.Parameter`/`ParameterizedTestExtension`/`Parameters`、`java.nio.file.Path`、`static org.assertj.core.api.Assumptions.assumeThat`。测试用例的业务逻辑（创建 source file/hive 表、`CALL system.add_files`、断言 Iceberg 表内容）完全不变，只动测试框架层。

## 小结

本提交为 Iceberg Spark extensions 测试层补齐了 JUnit 5 的基础设施：新建 `ExtensionsTestBase`（继承 `CatalogTestBase`，承载 metastore + SparkSession 初始化）、在 `build.gradle` 启用 `useJUnitPlatform()`，并把 `TestAddFilesProcedure` 作为首个迁移样例完成全部 JUnit 4 → 5 改写（`@TestTemplate`、`@Parameter` 字段注入、`@TempDir`、AssertJ 断言）。这是一次"脚手架 + 示范"提交，验证了新基类链的可行性，为后续批量迁移 `spark-extensions` 下其余测试类确立了标准模式。
