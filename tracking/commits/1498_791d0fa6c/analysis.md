# 提交 1498：Spark 3.4: Add REST catalog to Spark integration tests (#11698)

## 提交信息

- **序号**：1498 / 4088
- **哈希**：791d0fa6cf9408ad3b8a1ef633a059ba1f2255ad
- **短哈希**：791d0fa6c
- **日期**：2024-12-16（Mon Dec 16 19:34:05 2024 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Spark 3.4: Add REST catalog to Spark integration tests (#11698)
- **PR/Issue**：#11698

## 总体目的

Iceberg 的 Spark 集成测试此前主要基于 Hadoop catalog 与 Hive catalog，缺乏对 REST Catalog 的端到端覆盖。随着 REST Catalog 成为推荐的开放目录协议（许多引擎、云服务都通过 REST Catalog 接入 Iceberg），保证它在 Spark 3.4 上行为正确至关重要。

本提交将 REST Catalog 加入 Spark 3.4 的参数化集成测试矩阵：在 `SparkCatalogConfig` 中新增 `REST` 枚举项，在 `SparkTestBaseWithCatalog` 中启动一个内嵌的 `RESTCatalogServer`（JUnit4 `@ClassRule`），让所有继承 `SparkCatalogTestBase` 的测试用例自动以 `[Hadoop, REST, SPARK]` 三套 catalog 配置并行运行。同时对个别尚不兼容 REST 的用例做条件跳过，并修正几处与 catalog 类型相关的测试假设（如本地路径 file 协议、表重命名、namespace 默认值等），保证 REST 路径下测试稳定。

## 如何达成设计目的

1. 在 `spark/v3.4/build.gradle` 中为测试与集成测试添加 `iceberg-open-api` 的 testFixtures 依赖、`jetty.servlet` 与 `sqlite.jdbc` 运行时依赖，使 REST Catalog Server 可在测试中启动。
2. 新增 `RESTServerRule`（JUnit4 `ExternalResource` 包装 `RESTCatalogServer`），提供 `client()` 与 `uri()` 给测试用。
3. `SparkCatalogConfig` 增加 `REST` 项；`SparkCatalogTestBase` 在参数化数据中追加 REST 配置（带 `CatalogProperties.URI`）；`SparkTestBaseWithCatalog` 用 `@ClassRule REST_SERVER_RULE` 启动服务器，并把 `validationCatalog` 选择逻辑改为按 `type`/`catalog-impl` 分发（支持 Hadoop/REST/Hive/InMemory）。
4. 对若干测试用例做兼容性适配：
   - `TestAlterTable.testTableRename`：用 AssertJ `assumeThat` 跳过 Hadoop（不支持 rename）与 REST（待修复 issue #11154）。
   - `TestNamespaceSQL`：跳过 REST 的默认 namespace 测试；放宽 drop non-empty namespace 的异常断言为 `isInstanceOfAny(NamespaceNotEmptyException, BadRequestException)`；SHOW NAMESPACES 在 REST 下也只返回 1 个。
   - `TestRefreshTable`：REST 也要 clone session 测试缓存刷新。
   - `TestMetadataTables`、`TestComputeTableStatsAction`、`TestCreateTableAsSelect`：在断言前 `table.refresh()`，确保 REST 路径下 Spark 端与验证端 table 实例看到同一快照（REST catalog 默认 `cache-enabled=false`，但验证用 `restCatalog` 客户端可能缓存不同步）。
   - `TestRemoveOrphanFilesProcedure`：兼容不同 catalog 对本地路径返回 file 协议与否的差异（Hadoop/Hive 返回 `file:` 前缀，Jdbc/REST 不返回），统一补 `file:` 前缀再解析 URI。

## 修改详情

### `spark/v3.4/build.gradle`

**修改目的**：补齐 REST Catalog 集成测试所需依赖。

**工作逻辑**：

- `iceberg-spark` 子项目：新增 `testImplementation` `iceberg-open-api` 的 `testFixturesRuntimeElements`（`transitive = false`，只取其测试 fixtures），并新增 `testRuntimeOnly libs.jetty.servlet`（REST Server 基于 Jetty）。
- `iceberg-spark-extensions` 子项目：同样引入 `iceberg-open-api` testFixtures 与 `jetty.servlet`、`sqlite.jdbc`（REST Catalog Server 默认用 sqlite 做 catalog 后端）。
- `iceberg-spark-runtime` 子项目：新增 `integrationRuntimeOnly` 一组依赖（`iceberg-hive-metastore`、`iceberg-core` 的 testArtifacts、`iceberg-open-api` testFixtures、`jetty.servlet`、`sqlite.jdbc`），用于运行集成测试时类路径完整。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/rest/RESTServerRule.java`（新增）

**修改目的**：把 `RESTCatalogServer`（来自 `iceberg-open-api` testFixtures）包装为 JUnit4 规则。

**工作逻辑**：

- 继承 `ExternalResource`，`before()` 启动服务器、`after()` 关闭；懒加载 `client` 与 `localServer`，使用双重检查锁保证线程安全。
- 构造函数接收配置 `Map`；若 `REST_PORT=0`（FREE_PORT）则用 `RCKUtils.findFreePort()` 分配一个真实端口。
- 暴露 `client()`（返回 `RESTCatalog`）与 `uri()`（从 client properties 取 `CatalogProperties.URI`）。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/SparkCatalogConfig.java`

**修改目的**：新增 `REST` 枚举项。

**工作逻辑**：

```java
REST(
    "testrest",
    SparkCatalog.class.getName(),
    ImmutableMap.of("type", "rest", "cache-enabled", "false")),
```

catalog 名 `testrest`，使用 `SparkCatalog`（type=rest），关闭客户端缓存以便观察服务端最新状态。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/SparkCatalogTestBase.java`

**修改目的**：把 REST 配置加入参数化数据。

**工作逻辑**：在 `Parameterized` 参数数组中追加一组：catalogName=`testrest`，implementation=`SparkCatalog`，properties 在 `SparkCatalogConfig.REST.properties()` 基础上追加 `CatalogProperties.URI = REST_SERVER_RULE.uri()`，让 Spark 客户端连接到内嵌服务器。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/SparkTestBaseWithCatalog.java`

**修改目的**：启动 REST 服务器并按 catalog 类型分发 `validationCatalog`。

**工作逻辑**：

- 新增 `@ClassRule public static final RESTServerRule REST_SERVER_RULE`，配置：`REST_PORT=0`（自由端口）、`CLIENT_POOL_SIZE=1`（注释解释 sqlite 内存库对每个连接独立可见，需把连接池设为 1 以保证 catalog 视图一致）。
- 新增 `protected static RESTCatalog restCatalog;`，在 `createWarehouse()` 中 `restCatalog = REST_SERVER_RULE.client();`。
- `validationCatalog`/`validationNamespaceCatalog` 从 final 改为非 final（因为 `configureValidationCatalog()` 在构造时赋值）。
- 新增私有方法 `configureValidationCatalog()`：按 `catalogConfig` 中的 `ICEBERG_CATALOG_TYPE`（hadoop/rest/hive）或 `CATALOG_IMPL`（inmemory）选择对应 `validationCatalog` 实现，最后强转为 `SupportsNamespaces`。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMetadataTables.java`

**修改目的**：适配 REST catalog 的快照可见性。

**工作逻辑**：在 `spark.createDataFrame(...).writeTo(tableName).append()` 之后、读取 `currentSnapshot()` 之前插入 `table.refresh();`。因为 REST catalog 默认 `cache-enabled=false`，但测试持有的 `table` 句柄可能未及时反映 Spark 写入的最新快照，refresh 后再取快照 ID 才稳定。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRemoveOrphanFilesProcedure.java`

**修改目的**：兼容不同 catalog 返回 `table.location()` 的协议差异。

**工作逻辑**：

```java
String location = table.location();
// not every catalog will return file proto for local directories
// i.e. Hadoop and Hive Catalog do, Jdbc and REST do not
if (!location.startsWith("file:")) {
  location = "file:" + location;
}
File statsLocation = new File(new URI(location)).toPath().resolve("data").resolve(statsFileName).toFile();
```

避免 REST/Jdbc catalog 返回不带 `file:` 前缀的路径导致 `new URI(...)` 解析失败。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestComputeTableStatsAction.java`

**修改目的**：在计算统计前 refresh table。

**工作逻辑**：在 `actions.computeTableStats(table)` 之前加 `table.refresh();`，与 TestMetadataTables 同理。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestAlterTable.java`

**修改目的**：用 AssertJ `assumeThat` 替换 `Assume.assumeFalse`，并跳过 REST 的 rename 测试。

**工作逻辑**：

```java
assumeThat(catalogConfig.get(ICEBERG_CATALOG_TYPE))
    .as("need to fix https://github.com/apache/iceberg/issues/11154 before enabling this for the REST catalog")
    .isNotEqualTo(ICEBERG_CATALOG_TYPE_REST);
assumeThat(validationCatalog)
    .as("Hadoop catalog does not support rename")
    .isNotInstanceOf(HadoopCatalog.class);
```

REST catalog 的 rename 尚有未修复问题（#11154），暂跳过；Hadoop catalog 不支持 rename，继续跳过。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestCreateTableAsSelect.java`

**修改目的**：RTAS 后 refresh table。

**工作逻辑**：在 `spark.sql(...)` 执行 RTAS 之后、读取 schema 之前加 `rtasTable.refresh();`，确保后续断言基于最新 schema。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestNamespaceSQL.java`

**修改目的**：适配 REST catalog 的 namespace 行为差异。

**工作逻辑**：

- `testDefaultNamespace`：新增 `assumeThat(catalogConfig.get(ICEBERG_CATALOG_TYPE)).as("REST has no default namespace configured").isNotEqualTo(ICEBERG_CATALOG_TYPE_REST);`，跳过 REST（与 Hadoop 一样无默认 namespace）。
- `testDropEmptyNamespace`（删除非空 namespace）：异常断言从单一 `NamespaceNotEmptyException` 放宽为 `isInstanceOfAny(NamespaceNotEmptyException.class, BadRequestException.class)` 并 `hasMessageContaining("Namespace db is not empty.")`——REST 服务端可能以 `BadRequestException` 形式返回该错误。
- `testListNamespaces`：`SHOW NAMESPACES IN catalogName` 在 Hadoop 或 REST 下都只返回 1 个 namespace（而非默认 namespace列表），把 `if (isHadoopCatalog)` 扩展为 `if (isHadoopCatalog || catalogConfig.get(ICEBERG_CATALOG_TYPE).equals(ICEBERG_CATALOG_TYPE_REST))`。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestRefreshTable.java`

**修改目的**：让 REST catalog 也参与 `REFRESH TABLE` 缓存测试。

**工作逻辑**：把原先判断 `SPARK` 或 `HADOOP` 才设置 `cache-enabled=true` 并 clone session 的条件，改为用 `Set.of(SPARK, HADOOP, REST).contains(catalogName)`，REST 也纳入。

## 小结

- **成效**：Spark 3.4 集成测试矩阵新增 REST Catalog，显著扩大端到端覆盖；新增 `RESTServerRule` 让 JUnit4 测试可方便地启动内嵌 REST 服务器；统一了 `validationCatalog` 的分发逻辑；修正了多处与 catalog 类型相关的测试假设，使 REST 路径下测试稳定。
- **影响范围**：`spark/v3.4` 模块的 12 个文件（1 个构建脚本、1 个新增 RESTServerRule、1 个枚举、1 个参数化基类、1 个测试基类、6 个测试用例适配），共 242 行新增/20 行删除。全部为测试代码与构建配置，无产品运行时变更。
- **回迁到 1.4.x 的注意事项**：
  - 本提交是测试基础设施增强，**原则上可回迁**，但前提是 1.4.x 时期 `iceberg-open-api` 模块已提供 `RESTCatalogServer` 与 testFixtures（`testFixturesRuntimeElements` 配置）。若 1.4.x 的 `iceberg-open-api` 尚无 testFixtures 机制，则需先回迁相关基础设施。
  - 1.4.x 的 spark/v3.4 测试基线若与 main 已分叉（如某些用例已修复或调整），需逐个用例核对，避免与已有改动冲突；尤其 `TestRemoveOrphanFilesProcedure`、`TestNamespaceSQL`、`TestAlterTable` 等可能已有不同处理。
  - 回迁后应完整运行 `spark/v3.4` 测试套件，确认 REST 路径无系统性失败；关注 #11154（REST rename）在 1.4.x 是否已修复，若已修复可移除 `assumeThat` 跳过。
  - 由于这是测试增强，不影响 1.4.x 发布产物功能，回迁优先级中等——主要价值是扩大回归覆盖，便于在 1.4.x 上发现 REST Catalog 相关 bug。
