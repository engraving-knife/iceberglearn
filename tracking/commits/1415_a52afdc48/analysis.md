# 提交 1415：Add REST Catalog tests to Spark 3.5 integration test (#11093)

## 提交信息

- **序号**：1415 / 4088
- **哈希**：a52afdc484f9e63477b96d0182586692daac8eab
- **短哈希**：a52afdc48
- **日期**：2024-11-21（Thu Nov 21 15:32:26 2024 -0800）
- **作者**：Haizhou Zhao <zhaohaizhou940527@gmail.com>（与 Eduard Tudenhoefner 共同提交）
- **提交说明**：Add REST Catalog tests to Spark 3.5 integration test (#11093)
- **PR/Issue**：#11093

## 总体目的

Iceberg 的 Spark 模块维护着对 Spark 3.5 的集成测试，原本主要覆盖 Hive Catalog 与 Hadoop Catalog 两种 catalog 实现。REST Catalog 作为 Iceberg 重要的对外接口实现，其与 Spark 的端到端集成行为此前缺少常规的集成测试覆盖，存在回归风险（例如 `SparkCatalog` 通过 REST 后端访问表时的元数据刷新、表重命名、属性提交、孤立文件清理等行为）。

本提交为 Spark 3.5 的集成测试体系新增 REST Catalog 作为参数化维度，使现有的 `CatalogTestBase`/`TestBaseWithCatalog` 体系自动以 REST Catalog 跑一遍测试用例，从而把 REST Catalog 纳入常规 CI 覆盖。同时为了在 Spark 测试中能拉起一个本地 REST Catalog 服务端，对 open-api 测试固件（`RCKUtils`、`RESTCatalogServer`、`RESTServerExtension`）做了通用化扩展，支持自定义配置与随机端口。

## 如何达成设计目的

整体思路是复用 open-api 模块已有的 REST Catalog 测试固件（`RESTServerExtension`、`RESTCatalogServer`、`RCKUtils`），在 Spark 3.5 测试基类中通过 JUnit `@RegisterExtension` 拉起一个本地 REST 服务端与对应 `RESTCatalog` 客户端，然后把 REST Catalog 加入 `CatalogTestBase` 的参数化矩阵。

为了适配多个测试场景，需要解决几个工程问题：
1. **端口冲突**：默认端口 8181 在并行测试中易冲突。新增 `findFreePort()` 工具与 `FREE_PORT="0"` 约定，由 `RESTServerExtension` 在构造时把 0 替换为实际空闲端口，并在客户端配置中传入同一端口。
2. **服务端可配置**：原 `RESTCatalogServer` 仅支持从环境变量读取配置；新增带 `Map<String, String> config` 的构造器，把传入配置叠加到环境变量配置之上，使测试可在代码内注入配置（如 sqlite in-memory 数据库的连接池大小）。
3. **客户端可配置**：`RCKUtils.initCatalogClient` 新增重载，允许传入额外属性，与默认配置、系统属性合并，确保客户端与服务器端口对齐。
4. **JdbcCatalog + sqlite in-memory 的一致性**：sqlite 内存数据库对每个连接是私有的，若 `JdbcCatalog` 的连接池大小 > 1，不同连接看到的状态会不一致。因此在测试配置中显式把 `client-pool-size` 设为 1，并在注释中说明。
5. **validation catalog 适配**：测试基类原先只针对 Hadoop/Hive 设置 validation catalog；新增 REST 类型分支，让 validation catalog 直接复用已建立的 `restCatalog` 客户端实例，避免不一致。
6. **已知不兼容用例的临时跳过**：部分用例在 REST Catalog 上存在已知问题（如 #11109、#11154、#11554），通过 AssertJ 的 `assumeThat` 在 REST catalog 类型下跳过，并在注释中标注对应 issue，待后续修复后启用。

## 修改详情

### `open-api/src/testFixtures/java/org/apache/iceberg/rest/RCKUtils.java`

**修改目的**：扩展测试工具类，支持自定义配置与随机端口。

**工作逻辑**：
- 新增 `import`：`IOException`、`UncheckedIOException`、`ServerSocket`、`Map`（已有的 `Maps`）。
- `initCatalogClient()` 拆分为无参版本（委托给带参版本，传入空 map）与带参版本 `initCatalogClient(Map<String, String> properties)`：把环境配置、系统属性、传入 properties 依次合并；URI 默认值从硬编码的 `REST_PORT_DEFAULT` 改为读取 `REST_PORT` 配置项（默认回退到 `REST_PORT_DEFAULT`），使客户端与外部传入端口一致。
- 新增 `findFreePort()`：通过 `new ServerSocket(0)` 让 OS 分配一个空闲端口并立即返回，失败抛 `UncheckedIOException`。

### `open-api/src/testFixtures/java/org/apache/iceberg/rest/RESTCatalogServer.java`

**修改目的**：使服务器构造可接受外部配置。

**工作逻辑**：
- `REST_PORT` 由包级可见改为 `public static final`，方便外部引用常量名。
- 新增 `private final Map<String, String> config` 字段，并新增两个构造器：无参构造（空 config）与带 `Map` 构造（保存传入 config）。
- `initializeBackendCatalog()`：把 `environmentCatalogConfig()` 复制一份后 `putAll(config)`，让外部配置覆盖环境变量配置；其余逻辑（`JdbcCatalog` 兜底、属性设置）不变。

### `open-api/src/testFixtures/java/org/apache/iceberg/rest/RESTServerExtension.java`

**修改目的**：扩展为可配置的 JUnit 扩展，承担"分配端口、启动服务、创建并持有客户端、停止时清理"的职责。

**工作逻辑**：
- 新增常量 `FREE_PORT = "0"`：约定调用方传入 0 表示希望分配空闲端口。
- 新增字段 `RESTCatalog client`、`Map<String, String> config`，并新增带 `Map` 的构造器：在构造时检测 `REST_PORT` 是否为 `"0"`，若是则替换为 `RCKUtils.findFreePort()` 返回的端口，避免并发测试端口冲突。
- 暴露 `config()` 与 `client()` 访问器供测试使用。
- `beforeAll`：当 `RCK_LOCAL` 为 true（默认）时，用传入 config 构造 `RESTCatalogServer`，`start(false)` 启动；同时通过 `RCKUtils.initCatalogClient(config)` 建立客户端并保存。
- `afterAll`：停止服务端，并 `client.close()` 关闭客户端。

### `spark/v3.5/build.gradle`

**修改目的**：为 Spark 3.5 三个子项目添加 open-api 测试固件与运行时依赖。

**工作逻辑**：
- `iceberg-spark-3.5_2.12`（主项目）：新增 `testImplementation` 依赖 `:iceberg-open-api` 的 `testFixturesRuntimeElements`（`transitive = false`），并新增 `testRuntimeOnly libs.jetty.servlet` 以提供 REST 服务端运行所需的 servlet API。
- `iceberg-spark-extensions-3.5_2.12`：同样引入 open-api testFixtures 与 `jetty.servlet`，并额外引入 `libs.sqlite.jdbc` 作为 JdbcCatalog 后端。
- `iceberg-spark-runtime-3.5_2.12` 的 integration 测试：新增 `integrationRuntimeOnly` 依赖 `:iceberg-hive-metastore`（Hive catalog 集成测试用）、`:iceberg-core` testArtifacts、open-api testFixtures、`jetty.servlet`、`sqlite.jdbc`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestBaseWithCatalog.java`

**修改目的**：在测试基类中拉起 REST 服务并按 catalog 类型设置 validation catalog。

**工作逻辑**：
- 新增 `@RegisterExtension` 静态字段 `REST_SERVER_EXTENSION`，构造时传入 `REST_PORT=0`（请求空闲端口）与 `CLIENT_POOL_SIZE=1`（避免 sqlite in-memory 多连接不一致），并附详细注释说明 sqlite 内存库语义。
- 新增 `protected static RESTCatalog restCatalog` 字段，在 `setUpAll()`（由原 `createWarehouse()` 改名而来）中通过 `REST_SERVER_EXTENSION.client()` 初始化。
- `tearDownAll()`（由原 `dropWarehouse()` 改名）逻辑不变。
- 新增私有方法 `configureValidationCatalog()`：根据 `catalogConfig` 中的 `ICEBERG_CATALOG_TYPE` 或 `CATALOG_IMPL` 分派：
  - `HADOOP` → `HadoopCatalog`（带 `file:` 前缀的 warehouse）。
  - `REST` → 复用 `restCatalog` 实例（与服务器共享后端，validation 与测试 catalog 一致）。
  - `HIVE` → 直接用 `catalog`。
  - `InMemoryCatalog` → 新建 `InMemoryCatalog` 实例。
  - 其余抛 `IllegalArgumentException`。
- `before()` 中原本直接构造 `HadoopCatalog` 或复用 `catalog` 的两行被替换为 `configureValidationCatalog()` 调用。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/CatalogTestBase.java`

**修改目的**：在参数化矩阵中加入 REST Catalog。

**工作逻辑**：在 `parameters()` 返回数组中新增一组：使用 `SparkCatalogConfig.REST` 的 catalogName/implementation/properties，并动态把 `CatalogProperties.URI` 替换为 `restCatalog.properties()` 中实际的 URI，确保 Spark 端 catalog 配置指向测试期实际启动的 REST 服务端口。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/SparkCatalogConfig.java`

**修改目的**：新增 `REST` 枚举值。

**工作逻辑**：新增 `REST("testrest", SparkCatalog.class.getName(), ImmutableMap.of("type", "rest", "cache-enabled", "false"))`，与 Hadoop 枚举结构一致，仅 type 不同。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMetadataTables.java`

**修改目的**：适配 REST Catalog 下的元数据表测试。

**工作逻辑**：
- 在 `testMetadataTableWithV2Delete()` 写入数据后新增 `table.refresh()`，确保后续读取 `currentSnapshot().snapshotId()` 与服务器状态一致。
- `metadataLogEntriesAfterReplacingTable()` 用 `assumeThat(catalogConfig.get(ICEBERG_CATALOG_TYPE)).isNotEqualTo(ICEBERG_CATALOG_TYPE_REST)` 跳过 REST catalog，并注明需先修复 #11109。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRemoveOrphanFilesProcedure.java`

**修改目的**：兼容不同 catalog 对表 location 的 URI 协议返回差异。

**工作逻辑**：原代码直接 `new URI(table.location())`，但 REST/Jdbc catalog 返回的 location 可能不带 `file:` 前缀，而 Hadoop/Hive catalog 会带。新增判断：若 location 不以 `file:` 开头，则前置 `file:`，使后续 `new File(new URI(...))` 能正确解析本地路径。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestComputeTableStatsAction.java`

**修改目的**：在计算表统计前刷新表对象。

**工作逻辑**：在 `actions.computeTableStats(table)` 之前新增 `table.refresh()`，避免 REST Catalog 下表对象缓存的快照与服务器最新状态不同步。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestAlterTable.java`

**修改目的**：跳过 REST Catalog 下的表重命名测试。

**工作逻辑**：`testTableRename()` 新增 `assumeThat(...).isNotEqualTo(ICEBERG_CATALOG_TYPE_REST)`，注明需先修复 #11154。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestCreateTable.java`

**修改目的**：跳过 REST Catalog 下的 commit properties 测试。

**工作逻辑**：`testCreateTableCommitProperties()` 新增 `assumeThat(...).isNotEqualTo(ICEBERG_CATALOG_TYPE_REST)`，注明需先修复 #11554。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestRefreshTable.java`

**修改目的**：让 REST Catalog 也进入 cache 刷新测试覆盖范围。

**工作逻辑**：把原先只对 `SPARK` 与 `HADOOP` catalog 名设置 `cache-enabled=true` 并 clone session 的判断，扩展为对 `SPARK`、`HADOOP`、`REST` 三种 catalog 名生效（用 `Set.of(...).contains(catalogName)` 替换原两个 `equals`）。

## 小结

- **成效**：Spark 3.5 集成测试新增 REST Catalog 参数化维度，把 REST Catalog 纳入常规 CI 回归覆盖；同时把 open-api 测试固件通用化（支持配置化、空闲端口分配、客户端生命周期管理），为后续其他模块复用奠定基础。已识别三个 REST Catalog 已知缺陷（#11109、#11154、#11554）通过 `assumeThat` 临时跳过。
- **影响范围**：13 文件、+195/-21 行。涉及 open-api 测试固件（3 文件）、Spark 3.5 build.gradle（1 文件）、Spark 3.5 测试基类与各测试用例（9 文件）。无生产代码变更。
- **回迁到 1.4.x 的注意事项**：本提交是测试基础设施增强，**回迁价值高但需评估依赖**。1.4.x 是否有对应的 Spark 3.5 模块、open-api 模块结构是否一致、`RESTServerExtension`/`RESTCatalogServer` 在 1.4.x 中的 API 形态是否与本次扩展兼容，都需要核对。若 1.4.x 的 open-api 固件尚未支持 `config` 构造器，则需要先回迁 open-api 三件套改动；若 1.4.x 的 Spark 测试基类结构与 main 一致，则后续测试改动可较平滑回迁。回迁后需关注被 `assumeThat` 跳过的三个 issue 在 1.4.x 上是否同样存在，避免误以为是新缺陷。此外，build.gradle 中的依赖（`jetty.servlet`、`sqlite.jdbc`、open-api testFixtures）需确认 1.4.x 的版本目录（libs）中可用。
