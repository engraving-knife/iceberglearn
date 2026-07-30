# 提交 1110：Add REST Compatibility Kit (#10908)

## 提交信息

- **序号**：1110 / 4088
- **哈希**：c95bf58793b3a7ef9b0ffa630b907ce964cd32fe
- **短哈希**：c95bf5879
- **日期**：2024-08-27（Tue Aug 27 09:29:55 2024 -0700）
- **作者**：Daniel Weeks <dweeks@apache.org>
- **提交说明**：Add REST Compatibility Kit (#10908)
- **PR/Issue**：#10908
- **影响模块**：`open-api`（新增 RCK 子模块测试代码 + 构建配置）、`build.gradle`、`deploy.gradle`、`gradle/libs.versions.toml`

## 总体目的

Iceberg REST Catalog 规范（`rest-catalog-open-api.yaml`）是一个对外开放的接口契约，第三方（如 Snowflake、Tabular、Nessie、Polaris 等）都可以实现自己的 REST Catalog 服务端。问题是：**如何验证一个第三方实现确实符合 Iceberg REST 规范？**

本提交在 `iceberg-open-api` 模块中引入 **REST Compatibility Kit（RCK）** —— 一个 Technology Compatibility Kit（TCK），基于 Iceberg Java 参考实现既有的 `CatalogTests` 与 `ViewCatalogTests` 抽象测试基类，提供一套可针对**任意 REST Catalog 服务端**执行的兼容性测试套件。第三方实现方可以拉取本套件、配置自己的 REST 服务端地址，跑通即认为基本符合规范。

具体目标：

1. 复用 `iceberg-core` 既有 `CatalogTests<RESTCatalog>` / `ViewCatalogTests<RESTCatalog>` 大量参考实现测试，作为兼容性基准。
2. 提供本地 Jetty + JdbcCatalog 后端的默认测试服务端，让 RCK 自身可独立运行验证。
3. 提供环境变量与 system property 两套配置入口，让外部 REST 服务端可以接入。
4. 把 RCK 的 testFixtures 与 test jar 发布到 Maven 仓库，让第三方可在自己项目中依赖使用。

## 如何达成设计目的

整体架构：

```
RESTCompatibilityKitSuite (JUnit Suite 入口)
  ├── RESTCompatibilityKitCatalogTests  extends  CatalogTests<RESTCatalog>
  └── RESTCompatibilityKitViewCatalogTests  extends  ViewCatalogTests<RESTCatalog>
        ↑ @ExtendWith(RESTServerExtension.class)  控制本地服务端启停
                ↓
        RESTServerExtension
          - 若 rck.local=true（默认）→ 启动 RESTCatalogServer（Jetty + JdbcCatalog 后端）
          - 若 rck.local=false → 不启动本地服务端，测试连接 rck.uri 指定的外部 REST 服务端
                ↓
        RCKUtils.initCatalogClient()  →  构造 RESTCatalog 客户端，从环境变量 + system property 读配置
                ↓
        CatalogTests/ViewCatalogTests  的几百个测试用例  →  通过 RESTCatalog 走 REST 协议访问服务端
```

关键设计点：

- **配置入口双通道**：`CATALOG_*` 环境变量（`CATALOG_` 前缀剥离，`__` → `-`，`_` → `.`，转小写）+ `rck.*` system property（`rck.` 前缀剥离后剩 key 即 catalog 属性）。两条通道在 `RCKUtils.initCatalogClient` 中合并为 catalog properties。
- **行为开关**：REST 规范不强制的行为通过 `rck.requires-namespace-create`、`rck.supports-serverside-retry`、`rck.overrides-requested-location` 三个开关让被测方声明，测试基类 override 对应 hook 方法读取这些开关决定是否跳过/调整某些用例。
- **本地默认后端**：`RESTCatalogServer` 默认用 `JdbcCatalog`（sqlite::memory:）做后端，监听 8181 端口，用 Jetty + `RESTCatalogServlet` + `RESTCatalogAdapter`（这里是 `RESTServerCatalogAdapter` 子类，额外支持 `include-credentials` 把 S3/GCP/Azure 凭据注入 `LoadTableResponse`）暴露 REST 接口。
- **测试隔离**：`RCKUtils.TEST_NAMESPACES = [ns, newdb]`，`@BeforeAll` 校验这些 namespace 在服务端不存在，`@BeforeEach` 调 `purgeCatalogTestEntries` 清掉可能残留的测试 namespace/表/view，保证每个测试干净。
- **构建发布**：`build.gradle` 给 `iceberg-open-api` 加 `java-test-fixtures` 插件，引入 `testFixturesImplementation`/`testImplementation` 依赖（含 jetty、sqlite-jdbc、aws/gcp/azure bundle）；`deploy.gradle` 移除"不发布 iceberg-open-api"的早 return，新增 `isOpenApi` 分支，发布 `testJar` 与 `testFixturesJar`，让第三方可拉取使用；`libs.versions.toml` 加 `junit-platform = "1.10.3"` 与 `junit-suite-api`/`junit-suite-engine` 两个 alias 用于 JUnit Suite；`test` 任务配置 `outputs.upToDateWhen {false}`（永远重跑）+ `maxParallelForks = 1`（避免并发冲突）+ 过滤 `rck` 前缀的 system property 透传给测试。

## 修改详情

### `open-api/README.md`（新增 63 行）

**修改目的**：为 RCK 提供完整使用文档。

**工作逻辑**：

- "Open API spec" 段：原有内容（lint、生成 Python 代码）保留。
- 新增 "REST Compatibility Kit (RCK)" 段：解释 RCK 是什么；配置方式（环境变量 `CATALOG_*` 与 system property `rck.*`，含变量名转换规则与示例）；行为开关表（`rck.requires-namespace-create`/`rck.supports-serverside-retry`，默认均为 true）；运行示例 `./gradlew :iceberg-open-api:test --tests RESTCompatibilityKitSuite -Drck.local=false -Drck.uri=... -Drck.warehouse=... -Drck.credential=...`。

### `open-api/src/test/java/org/apache/iceberg/rest/RESTCompatibilityKitSuite.java`（新增 45 行）

**修改目的**：RCK 的 JUnit Suite 入口。

**工作逻辑**：

- 标注 `@Suite`、`@SuiteDisplayName("Iceberg REST Compatibility Kit")`、`@SelectClasses({RESTCompatibilityKitCatalogTests.class, RESTCompatibilityKitViewCatalogTests.class})`。
- 定义三个常量：`RCK_REQUIRES_NAMESPACE_CREATE`、`RCK_SUPPORTS_SERVERSIDE_RETRY`、`RCK_OVERRIDES_REQUESTED_LOCATION`，分别对应被测方对 REST 规范未强制行为的支持声明。
- `protected` 构造函数。

### `open-api/src/test/java/org/apache/iceberg/rest/RESTCompatibilityKitCatalogTests.java`（新增 87 行）

**修改目的**：表 catalog 兼容性测试。

**工作逻辑**：

- `@ExtendWith(RESTServerExtension.class)` 控制本地服务端启停。
- 继承 `CatalogTests<RESTCatalog>`（来自 iceberg-core testArtifacts），自动复用其几百个表 catalog 测试用例。
- `@BeforeAll beforeClass()`：调 `RCKUtils.initCatalogClient()` 初始化 RESTCatalog 客户端；断言 `RCKUtils.TEST_NAMESPACES` 在服务端不存在（避免污染既有数据）。
- `@BeforeEach before()`：调 `RCKUtils.purgeCatalogTestEntries(restCatalog)` 清理可能残留的测试 namespace/表/view。
- `@AfterAll afterClass()`：关 catalog。
- `catalog()` 返回 `restCatalog`。
- Override `requiresNamespaceCreate()`/`supportsServerSideRetry()`/`overridesRequestedLocation()` 三个 hook 方法，从 catalog properties 读取对应 `rck.*` 开关，默认值与基类对齐（`requiresNamespaceCreate` 默认走基类，`supportsServerSideRetry` 默认 true，`overridesRequestedLocation` 默认 false）。

### `open-api/src/test/java/org/apache/iceberg/rest/RESTCompatibilityKitViewCatalogTests.java`（新增 91 行）

**修改目的**：View catalog 兼容性测试。

**工作逻辑**：结构与 `RESTCompatibilityKitCatalogTests` 对称，继承 `ViewCatalogTests<RESTCatalog>`；额外 override `tableCatalog()` 返回同一个 `restCatalog`（view 测试需要表 catalog 配合）；三个行为开关的 override 与表测试一致。

### `open-api/src/testFixtures/java/org/apache/iceberg/rest/RCKUtils.java`（新增 110 行）

**修改目的**：RCK 公共工具，对外发布（testFixtures）。

**工作逻辑**：

- `environmentCatalogConfig()`：扫 `System.getenv()`，过滤 `CATALOG_` 前缀的变量，按规则转换 key（`CATALOG_` 剥离 → `__` 转 `-` → `_` 转 `.` → 全小写），value 不变。javadoc 给出多个示例。
- `initCatalogClient()`：合并 `environmentCatalogConfig()` + `Maps.fromProperties(System.getProperties())`；默认 `uri=http://localhost:8181/`、`warehouse=rck_warehouse`；构造 `RESTCatalog`，`setConf(new Configuration())`，`initialize("rck_catalog", properties)`，返回。
- `purgeCatalogTestEntries(RESTCatalog)`：若 `rck.purge-test-namespaces` 默认 true，遍历 `TEST_NAMESPACES`，存在则 `listTables`/`listViews` 全部 drop 再 `dropNamespace`。
- 常量：`CATALOG_ENV_PREFIX`、`RCK_LOCAL`、`RCK_PURGE_TEST_NAMESPACES`、`TEST_NAMESPACES = [ns, newdb]`。

### `open-api/src/testFixtures/java/org/apache/iceberg/rest/RESTCatalogServer.java`（新增 123 行）

**修改目的**：本地 REST 服务端，用于默认场景与独立 main 启动。

**工作逻辑**：

- 内部类 `CatalogContext`：包装 `Catalog` 与配置 `Map`。
- `initializeBackendCatalog()`：从环境变量读 catalog properties；默认 `JdbcCatalog` + `jdbc:sqlite::memory:` + `jdbc.schema-version=V1`；若没设 warehouse 则创建临时目录；用 `CatalogUtil.buildIcebergCatalog` 构造后端 catalog。
- `start(boolean join)`：用 `RESTServerCatalogAdapter(catalogContext)` 包装为 `RESTCatalogAdapter`，包进 `RESTCatalogServlet`，挂到 Jetty `ServletContextHandler` + `GzipHandler`；监听端口从 `rest.port` 读（默认 8181）；`join=true` 时阻塞主线程（main 用），`join=false` 时启动后立即返回（测试用）。
- `stop()`：关 httpServer。
- `main(String[])`：`new RESTCatalogServer().start(true)`，便于命令行启动一个本地 REST server。

### `open-api/src/testFixtures/java/org/apache/iceberg/rest/RESTServerCatalogAdapter.java`（新增 85 行）

**修改目的**：扩展 `RESTCatalogAdapter`，按需注入云存储凭据到 `LoadTableResponse`。

**工作逻辑**：

- 继承 `RESTCatalogAdapter`，构造时传入 `CatalogContext`。
- Override `handleRequest`：调用 super 处理；若响应是 `LoadTableResponse` 且配置中 `include-credentials=true`，调 `applyCredentials` 把 catalog 配置中的 S3（`access-key-id`/`secret-access-key`/`session-token`）、GCP（`gcs-oauth2-token`）、Azure（`adls.sas-token.*`/`adls.connection-string.*` 前缀的 key）凭据复制到 table config，让客户端拿到表加载响应后能直接访问底层云存储。

### `open-api/src/testFixtures/java/org/apache/iceberg/rest/RESTServerExtension.java`（新增 45 行）

**修改目的**：JUnit 5 扩展，按需启停本地服务端。

**工作逻辑**：实现 `BeforeAllCallback`/`AfterAllCallback`。`beforeAll`：读 JUnit `ConfigurationParameter` `rck.local`（默认 `"true"`），为 true 时 `new RESTCatalogServer()` + `start(false)`。`afterAll`：若 `localServer != null` 则 `stop()`。

### `build.gradle`

**修改目的**：为 `iceberg-open-api` 模块配置测试与 testFixtures 依赖。

**工作逻辑**：

- `apply plugin: 'java-test-fixtures'` 启用 testFixtures。
- `dependencies` 块：
  - `testImplementation`：`iceberg-api`、`iceberg-core`、`iceberg-core` 的 test runtimeClasspath、本模块 testFixtures；junit jupiter/suite-api/suite-engine；assertj；`iceberg-aws-bundle`/`iceberg-gcp-bundle`/`iceberg-azure-bundle`（运行时 bundle，方便测试时连云）。
  - `testFixturesImplementation`：`iceberg-api`、`iceberg-core` + `testArtifacts` 配置 + test runtimeClasspath；`iceberg-aws`/`iceberg-gcp`/`iceberg-azure`（非 bundle，便于注入凭据）；`jetty-servlet`/`jetty-server`/`sqlite-jdbc`。
- `test` 任务：`useJUnitPlatform()`；`outputs.upToDateWhen {false}`（永远重跑兼容性测试，避免缓存掩盖问题）；`maxParallelForks = 1`；过滤 `rck` 前缀的 system property 透传给测试（同时保留原 key 和去掉 `rck.` 前缀的 key 两份），避免污染其他 build/test 配置。

### `deploy.gradle`

**修改目的**：发布 `iceberg-open-api` 的 test 与 testFixtures artifact。

**工作逻辑**：

- 移除原 `if (it.name == 'iceberg-open-api') { return }`（之前不发布本模块）。
- 新增 `def isOpenApi = it.name == 'iceberg-open-api'`。
- `apache` publication 中：`isBom` 走 `javaPlatform`；`isOpenApi` 时 `artifact testJar` + `artifact testFixturesJar`；其他走原逻辑（`components.java` 或 shadowJar）。

### `gradle/libs.versions.toml`

**修改目的**：新增 JUnit Platform Suite 依赖。

**工作逻辑**：

- `[versions]` 加 `junit-platform = "1.10.3"`。
- `[libraries]` 加 `junit-suite-api = { module = "org.junit.platform:junit-platform-suite-api", version.ref = "junit-platform" }` 与 `junit-suite-engine = { module = "org.junit.platform:junit-platform-suite-engine", version.ref = "junit-platform" }`。

## 小结

- **成效**：Iceberg 现在拥有一个官方 REST Catalog 兼容性测试套件（RCK），可针对任意实现 Iceberg REST 规范的服务端运行，复用 Java 参考实现既有的 `CatalogTests`/`ViewCatalogTests` 数百个用例。默认本地 Jetty + JdbcCatalog 后端可独立跑通；外部服务端通过 `rck.*` system property 或 `CATALOG_*` 环境变量接入。testFixtures 与 test jar 发布到 Maven 仓库供第三方依赖。
- **影响范围**：仅 `open-api` 模块与构建脚本，不影响任何运行时代码或既有 API。RCK 是测试基础设施，对生产无影响。
- **回迁到 1.4.x 的注意事项**：
  - RCK 是新功能引入，**不建议**回迁到 1.4.x 维护分支。1.4.x 已发布版本，其 REST Catalog 规范支持范围与 main 不同；强行回迁可能因 `CatalogTests`/`ViewCatalogTests` 基类签名差异（基类在 main 上可能已有新增抽象方法）而无法编译。
  - 若 1.4.x 用户需要 RCK，建议直接使用 main 分支已发布的 RCK artifact（本提交让 `iceberg-open-api` 的 testFixtures/testJar 可发布），针对 1.4.x 实现的 REST 服务端跑兼容性测试。
  - 若确实要在 1.4.x 内部回迁，需同步确认 `iceberg-core` 的 `CatalogTests`/`ViewCatalogTests` 基类签名与 RCK override 的方法签名一致；`testArtifacts` configuration 在 1.4.x 是否已存在；`java-test-fixtures` 插件在 1.4.x 的 gradle 配置中是否可用。
  - 注意 `deploy.gradle` 的发布改动会改变 1.4.x 的发布产物（多出 `iceberg-open-api` 的 testJar/testFixturesJar），需要在 release 流程中评估影响。
