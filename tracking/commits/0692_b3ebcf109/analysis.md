# 提交 0692：重构代码与属性以使 Flink 1.19 可工作

## 提交信息
- **序号**：0692 / 4088
- **哈希**：b3ebcf109a3e94727051908995e3364fa2b89039
- **短哈希**：b3ebcf109
- **日期**：2024-04-15 10:03:00 -0700
- **作者**：Rodrigo Meneses <rmenesespinillos@apple.com>
- **提交说明**：Flink: Refactoring code and properties to make Flink 1.19 to work
- **PR/Issue**：无（提交说明中未标注 PR 号）

## 总体目的

本提交是 Flink 1.19 支持的实质性适配提交。在 0691 提交恢复了 `flink/v1.18/` 之后，`flink/v1.19/` 目录（由前一个提交 fbcd142c5 通过 move 产生）虽然存在，但其内容仍然是 v1.18 的代码：`build.gradle` 里 `flinkMajorVersion` 还是 `'1.18'`，依赖还引用 `libs.flink118.*`，测试中 `FlinkPackage.version()` 断言还是 `"1.18.1"`。本提交的任务就是把这些全部改造为真正面向 Flink 1.19 的配置和代码。

除了把"1.18"标签替换为"1.19"之外，本提交还解决了一个 Flink 1.19 引入的实际兼容性问题：FLINK-33226 之后，Flink 不再允许删除"当前正在使用"的数据库（database）。这导致大量测试中直接 `DROP DATABASE` 的清理代码会失败。为此作者引入了一个 `dropDatabase` 辅助方法，在删库前先切换到默认 catalog 的默认数据库，从而绕开该限制。

此外，本提交还同步完成了 Flink 1.16 的版本移除（在构建配置层面），把支持的 Flink 版本从 `1.16/1.17/1.18` 调整为 `1.17/1.18/1.19`，为后续 0693 提交（彻底删除 v1.16 源码）做好了构建配置准备。

## 如何达成设计目的

整体策略分为三个层面：

1. **构建系统与版本属性层**：在 `gradle/libs.versions.toml` 中新增 `flink119 = "1.19.0"` 及一整套 `flink119-*` 依赖别名，同时移除 `flink116` 及其全部依赖别名；在 `gradle.properties` 中把默认 Flink 版本从 `1.18` 改为 `1.19`，已知版本列表改为 `1.17,1.18,1.19`；在 `settings.gradle` 和 `flink/build.gradle` 中注册 v1.19 子项目、移除 v1.16 子项目注册；在 CI 和打包脚本中更新版本矩阵。

2. **v1.19 模块构建脚本层**：把 `flink/v1.19/build.gradle` 中的 `flinkMajorVersion` 从 `'1.18'` 改为 `'1.19'`，把所有 `libs.flink118.*` 引用替换为 `libs.flink119.*`。

3. **v1.19 测试代码适配层**：针对 Flink 1.19 的行为变化（FLINK-33226）做测试代码重构——在 `TestBase` 和 `FlinkTestBase` 中新增 `dropDatabase` 方法，并把十几个测试类中直接 `sql("DROP DATABASE ...")` 的调用替换为 `dropDatabase(...)`；在 `FlinkCatalogFactory` 中新增 `DEFAULT_CATALOG_NAME` 常量供上述方法使用；处理 `TestDataStatisticsOperator` 中 Flink 1.19 API 变化（`createOperatorStateBackend` 签名变更）；更新 `TestFlinkPackage` 中的版本断言；简化 `TestIcebergConnector` 中不再需要的 try-finally 清理逻辑。

## 修改详情

### `.github/workflows/flink-ci.yml`
**修改目的**：更新 CI 的 Flink 版本测试矩阵。
**工作逻辑**：将 matrix 中的 `flink: ['1.16', '1.17', '1.18']` 改为 `flink: ['1.17', '1.18', '1.19']`，移除 1.16、加入 1.19。

### `dev/stage-binaries.sh`
**修改目的**：更新二进制打包脚本支持的 Flink 版本。
**工作逻辑**：`FLINK_VERSIONS` 从 `1.16,1.17,1.18` 改为 `1.17,1.18,1.19`。

### `flink/build.gradle`
**修改目的**：更新 Flink 模块的版本加载入口。
**工作逻辑**：移除 `if (flinkVersions.contains("1.16"))` 块，新增 `if (flinkVersions.contains("1.19"))` 块，使 build 根据当前要构建的版本列表动态 apply 对应版本子目录的 `build.gradle`。

### `flink/v1.19/build.gradle`
**修改目的**：把 v1.19 子模块的构建配置从 v1.18 适配为真正的 v1.19。
**工作逻辑**：
- `flinkMajorVersion` 从 `'1.18'` 改为 `'1.19'`，这决定项目名 `iceberg-flink-1.19` 和 `iceberg-flink-runtime-1.19`。
- compileOnly 依赖全部从 `libs.flink118.*` 替换为 `libs.flink119.*`（avro、metrics.dropwizard、streaming.java、table.api.java.bridge、connector.base、connector.files），planner 的版本引用也从 `flink118` 改为 `flink119`。
- testImplementation 依赖同样替换（connector.test.utils、core、runtime、test.utilsjunit、test.utils）。
- `iceberg-flink-runtime-1.19` 子项目的 implementation/integrationImplementation 依赖也做了对应替换。

### `gradle.properties`
**修改目的**：更新默认和已知 Flink 版本属性。
**工作逻辑**：`systemProp.defaultFlinkVersions` 从 `1.18` 改为 `1.19`；`systemProp.knownFlinkVersions` 从 `1.16,1.17,1.18` 改为 `1.17,1.18,1.19`。

### `gradle/libs.versions.toml`
**修改目的**：在版本目录中注册 Flink 1.19 依赖、移除 Flink 1.16 依赖。
**工作逻辑**：
- 在 `[versions]` 段新增 `flink119 = { strictly = "1.19.0"}`，移除 `flink116`。
- 在 `[libraries]` 段新增 6 个 `flink119-*` 别名（avro、connector-base、connector-files、metrics-dropwizard、streaming-java、table-api-java-bridge），移除 6 个 `flink116-*` 别名。
- 在测试依赖段新增 5 个 `flink119-*` 别名（connector-test-utils、core、runtime、test-utils、test-utilsjunit），移除 5 个 `flink116-*` 别名。

### `settings.gradle`
**修改目的**：注册 v1.19 子项目、移除 v1.16 子项目。
**工作逻辑**：移除 `flinkVersions.contains("1.16")` 块（含 `iceberg-flink:flink-1.16` 和 `iceberg-flink:flink-runtime-1.16` 两个项目及其目录/名称映射）；新增 `flinkVersions.contains("1.19")` 块，按相同模式注册 v1.19 的两个子项目。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/FlinkCatalogFactory.java`
**修改目的**：新增 `DEFAULT_CATALOG_NAME` 常量，供测试辅助方法使用。
**工作逻辑**：新增 `public static final String DEFAULT_CATALOG_NAME = "default_catalog";`，把原来 `BASE_NAMESPACE` 后的空行调整。该常量在 Flink 中是内置的默认 catalog 名，此处将其显式定义为公共常量，避免测试代码硬编码字符串。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/FlinkTestBase.java`
**修改目的**：新增 `dropDatabase` 辅助方法以适配 FLINK-33226。
**工作逻辑**：新增 import `DEFAULT_CATALOG_NAME`。新增 `dropDatabase(String database, boolean ifExists)` 方法：先记录当前 catalog，切换到 `DEFAULT_CATALOG_NAME`，再 `USE` 到该 catalog 的第一个数据库（即默认数据库），然后切回原 catalog，最后执行 `DROP DATABASE`。这样确保被删除的数据库不是"当前正在使用"的数据库，绕开 Flink 1.19 的限制。方法带有详细 Javadoc 说明原因。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/TestBase.java`
**修改目的**：同 FlinkTestBase，新增 `dropDatabase` 并将 `dropCatalog` 中的硬编码替换为常量。
**工作逻辑**：
- import `DEFAULT_CATALOG_NAME`。
- `dropCatalog` 中 `sql("USE CATALOG default_catalog")` 改为 `sql("USE CATALOG %s", DEFAULT_CATALOG_NAME)`。
- 新增与 FlinkTestBase 中相同的 `dropDatabase` 方法（含 Javadoc）。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/TestIcebergConnector.java`
**修改目的**：简化 `testCatalogDatabaseConflictWithFlinkDatabase` 测试，移除不再需要的 try-finally。
**工作逻辑**：原代码用 try-finally 在测试后手动 `DROP TABLE` 和 `DROP DATABASE`；改造后移除 try-finally，直接调用 `testCreateConnectorTable()` 和断言。这是因为测试清理逻辑已由 `@AfterEach` 的 `clean()` 方法（使用新的 `dropDatabase`）统一处理，无需在测试方法内重复清理。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/TestChangeLogTable.java`
**修改目的**：将直接 `DROP DATABASE` 替换为 `dropDatabase`。
**工作逻辑**：`clean()` 方法中 `sql("DROP DATABASE IF EXISTS %s", DATABASE_NAME)` 改为 `dropDatabase(DATABASE_NAME, true)`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/TestFlinkCatalogDatabase.java`
**修改目的**：将多处 `DROP DATABASE` 替换为 `dropDatabase`。
**工作逻辑**：`clean()` 及多个测试方法中，共 4 处 `sql("DROP DATABASE IF EXISTS %s", flinkDatabase)` 或 `sql("DROP DATABASE %s", flinkDatabase)` 替换为 `dropDatabase(flinkDatabase, true)`。其中 `assertThatThrownBy(() -> sql("DROP DATABASE %s", flinkDatabase))` 改为 `assertThatThrownBy(() -> dropDatabase(flinkDatabase, true))`，验证删除非空数据库仍会抛出 `DatabaseNotEmptyException`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/TestFlinkCatalogTable.java`
**修改目的**：将 `cleanNamespaces()` 中的 `DROP DATABASE` 替换为 `dropDatabase`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/TestFlinkCatalogTablePartitions.java`
**修改目的**：将 `cleanNamespaces()` 中的 `DROP DATABASE` 替换为 `dropDatabase`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/TestFlinkHiveCatalog.java`
**修改目的**：将 `DROP DATABASE test_db` 替换为 `dropDatabase("test_db", false)`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/TestFlinkTableSink.java`
**修改目的**：将 `clean()` 中的 `DROP DATABASE` 替换为 `dropDatabase`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/TestFlinkUpsert.java`
**修改目的**：将 `clean()` 中的 `DROP DATABASE` 替换为 `dropDatabase`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/actions/TestRewriteDataFilesAction.java`
**修改目的**：将 `clean()` 中的 `DROP DATABASE` 替换为 `dropDatabase`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkMetaDataTable.java`
**修改目的**：将 `clean()` 中的 `DROP DATABASE` 替换为 `dropDatabase`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkTableSource.java`
**修改目的**：将 `clean()` 中的 `DROP DATABASE` 替换为 `dropDatabase`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestMetadataTableReadableMetrics.java`
**修改目的**：将 `clean()` 中的 `DROP DATABASE` 替换为 `dropDatabase`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestStreamScanSql.java`
**修改目的**：将 `clean()` 中的 `DROP DATABASE` 替换为 `dropDatabase`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestDataStatisticsOperator.java`
**修改目的**：适配 Flink 1.19 的 `createOperatorStateBackend` API 签名变化。
**工作逻辑**：新增 import `OperatorStateBackendParametersImpl`。原调用 `abstractStateBackend.createOperatorStateBackend(env, "test-operator", Collections.emptyList(), cancelStreamRegistry)` 改为传入 `new OperatorStateBackendParametersImpl(env, "test-operator", Collections.emptyList(), cancelStreamRegistry)`。Flink 1.19 将该方法的参数从多个参数改为接收一个参数对象，这是 Flink 内部 API 的重构。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/util/TestFlinkPackage.java`
**修改目的**：更新版本断言以匹配 Flink 1.19。
**工作逻辑**：`assertThat(FlinkPackage.version()).isEqualTo("1.18.1")` 改为 `isEqualTo("1.19.0")`。

## 小结
- **成效**：成功达成目的。经过本提交，`flink/v1.19/` 模块在构建配置和测试代码层面都已真正面向 Flink 1.19，可以编译和运行。同时构建系统层面的 v1.16 移除也为 0693 提交铺平了道路。
- **影响范围**：主要影响 Flink 模块。涉及版本目录、Gradle 属性、settings、CI、打包脚本等全局构建配置，以及 v1.19 子模块的构建脚本和 18 个测试/主代码文件。不影响 core、spark 等其他引擎模块，也不影响 v1.17、v1.18 子模块。
- **回迁到 1.4.x 的注意事项**：回迁时需确保 1.4.x 分支已有 `flink/v1.19/` 目录（即已回迁 0691 及其前置 move 提交）。`libs.versions.toml` 中需同步新增 `flink119` 版本和依赖别名。`dropDatabase` 辅助方法和 `DEFAULT_CATALOG_NAME` 常量是 v1.19 特有的适配，如果 1.4.x 的 v1.17/v1.18 也遇到 FLINK-33226 问题，可考虑同步引入。`OperatorStateBackendParametersImpl` 的 API 变化是 Flink 1.19 特有的，回迁时需确认 1.4.x 使用的 Flink 1.19 版本中该类存在且签名一致。
