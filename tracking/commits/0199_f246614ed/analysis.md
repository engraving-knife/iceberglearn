# 提交 0199：Flink: Backport #9078 to v1.16 and v1.15 (#9151)

## 提交信息

- **序号**：0199 / 4088
- **哈希**：f246614ed9d98f84eae13ff57768d377a95d3ced
- **短哈希**：f246614ed
- **日期**：2023-11-27
- **作者**：CG
- **提交说明**：Flink: Backport #9078 to v1.16 and v1.15 (#9151)
- **PR/Issue**：#9151（回 port #9078 到 v1.16 与 v1.15）

## 总体目的

这个提交把主线 PR #9078 引入的 Flink 测试基础设施 JUnit 5 化改动回 port 到 Iceberg 的 Flink v1.15 与 v1.16 两个维护分支。#9078 的核心是为 Flink 测试套件建立 JUnit 5（Jupiter）版本的测试基类，替代旧的 JUnit 4 `FlinkTestBase`，并完成首个测试类 `TestCatalogTableLoader` 的迁移作为示范。

与同期 Spark 侧的 JUnit 5 迁移（见提交 0196）类似，Flink 测试套件此前依赖 JUnit 4（`@BeforeClass/@AfterClass`、`org.junit.Assert`、`@Before/@After`），而 JUnit 5 提供了更现代的扩展模型（`@RegisterExtension` + `MiniClusterExtension`）、`@TempDir` 临时目录管理、`@BeforeAll/@AfterAll` 生命周期以及与 AssertJ 流式断言的更好集成。本次回 port 在 v1.15 与 v1.16 两个分支各新增了 `MiniFlinkClusterExtension`（迷你集群扩展工厂）与抽象基类 `TestBase`，并把 `TestCatalogTableLoader` 从旧 `FlinkTestBase`（JUnit 4）迁移到新 `TestBase`（JUnit 5）。这为这两个维护分支后续逐步迁移其余 Flink 测试类奠定了基础，对保持维护版本与主线测试基础设施的一致性具有意义。

## 如何达成设计目的

在 v1.15 与 v1.16 两个分支上做对称改动，每个分支改三个文件：新增 `MiniFlinkClusterExtension` 提供禁用 classloader 泄漏检查的 `MiniClusterExtension` 工厂方法；新增抽象基类 `TestBase`（继承 Flink 的 `TestBaseUtils`）承载 Hive Metastore 启停、`TableEnvironment` 与 SQL 辅助方法，使用 JUnit 5 的 `@RegisterExtension`/`@BeforeAll/@AfterAll`/`@TempDir`；修改 `TestCatalogTableLoader` 把父类由 `FlinkTestBase` 改为 `TestBase`，并把 JUnit 4 注解与 `Assert` 断言改为 JUnit 5 注解与 AssertJ 断言。

## 修改详情

### `flink/v1.15/flink/src/test/java/org/apache/iceberg/flink/MiniFlinkClusterExtension.java`（新增，53 行）与 `flink/v1.16/.../MiniFlinkClusterExtension.java`（新增，同）

**修改目的**：提供 JUnit 5 风格的迷你 Flink 集群创建工厂，替代旧 JUnit 4 下的集群资源管理方式。

**工作逻辑**：定义常量 `DEFAULT_TM_NUM=1`、`DEFAULT_PARALLELISM=4`；构建 `DISABLE_CLASSLOADER_CHECK_CONFIG`（设置 `CoreOptions.CHECK_LEAKED_CLASSLOADER=false`，因为 Avro 序列化器会缓存 classloader 导致误报泄漏）；提供静态工厂 `createWithClassloaderCheckDisabled()` 返回 `MiniClusterExtension`（基于 `MiniClusterResourceConfiguration`），Javadoc 说明在 Iceberg 集成测试中 job 完成后断言结果时会访问已被 TaskManager 关闭的 classloader，故需禁用泄漏检查。私有构造方法防止实例化。

### `flink/v1.15/flink/src/test/java/org/apache/iceberg/flink/TestBase.java`（新增，130 行）与 `flink/v1.16/.../TestBase.java`（新增，同）

**修改目的**：建立 Flink 测试的 JUnit 5 抽象基类，集中承载 Metastore/Spark-less TableEnvironment 生命周期与通用 SQL 辅助方法。

**工作逻辑**：
- 继承 Flink 的 `org.apache.flink.test.util.TestBaseUtils`。
- 用 `@RegisterExtension` 注册静态 `MiniClusterExtension`（由 `MiniFlinkClusterExtension.createWithClassloaderCheckDisabled()` 创建）；用 `@TempDir Path temporaryDirectory` 管理临时目录（替代手动 temp file）。
- 静态字段 `metastore`/`hiveConf`/`catalog`；`@BeforeAll startMetastore()` 启动 `TestHiveMetastore` 并通过 `CatalogUtil.loadCatalog` 加载 `HiveCatalog`；`@AfterAll stopMetastore()` 停止并置空。
- `getTableEnv()` 双重检查锁懒加载 `TableEnvironment`（batch 模式，关闭 `TABLE_EXEC_ICEBERG_INFER_SOURCE_PARALLELISM`）。
- 辅助方法：`exec()`/`sql()` 执行 SQL 并收集 `Row` 列表；`assertSameElements()` 用 AssertJ `containsExactlyInAnyOrderElementsOf` 断言；`dropCatalog()` 处理 FLINK-29677 后无法删除当前使用 catalog 的问题（先切到 `default_catalog` 再 DROP）。

### `flink/v1.15/flink/src/test/java/org/apache/iceberg/flink/TestCatalogTableLoader.java` 与 `flink/v1.16/.../TestCatalogTableLoader.java`

**修改目的**：作为首个迁移到新 JUnit 5 基类的 Flink 测试类，验证基类可用并树立迁移范式。

**工作逻辑**：父类由 `FlinkTestBase` 改为 `TestBase`；import 把 `org.junit.AfterClass`/`org.junit.Assert`/`org.junit.BeforeClass`/`org.junit.Test` 换成 `org.junit.jupiter.api.AfterAll`/`org.junit.jupiter.api.BeforeAll`/`org.junit.jupiter.api.Test`（AssertJ `Assertions` 已有）；`@BeforeClass`→`@BeforeAll`、`@AfterClass`→`@AfterAll`；`Assert.assertTrue(warehouse.delete())` → `Assertions.assertThat(warehouse.delete()).isTrue()`；`Assert.assertTrue("Failed to delete " + ..., fs.delete(...))` → `Assertions.assertThat(fs.delete(...)).as("Failed to delete " + ...).isTrue()`；`Assert.assertEquals("my_value", ...)` → `Assertions.assertThat(...).isEqualTo("my_value")`。

## 小结

该提交把 #9078 的 Flink 测试 JUnit 5 基础设施回 port 到 v1.15/v1.16，为这两个维护分支的 Flink 测试套件现代化迁移奠定基础，并保持与主线测试基础设施的一致性。
