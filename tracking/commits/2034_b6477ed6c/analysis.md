# 提交 2034：Flink: Add support for Flink 2.0

## 提交信息

- **序号**：2034 / 4088
- **哈希**：b6477ed6c3bc0577579c8cc9955f1b8b88da7f3d
- **短哈希**：b6477ed6c
- **日期**：2025-04-23 13:21:24 -0700
- **作者**：Maximilian Michels
- **提交说明**：Flink: Add support for Flink 2.0
- **PR/Issue**：无（属于 Flink 2.0 支持系列的一部分）

## 总体目的

本提交是 Flink 2.0 支持重构系列（提交 2032-2035）的第三步，也是最核心的一步。在前两个提交中，`flink/v2.0/` 目录已从 `flink/v1.20/` 派生出来并保持内容相同。本提交在此基础上对 `flink/v2.0/` 目录中的代码进行修改，使其适配 Flink 2.0 的 API 变更，正式添加对 Flink 2.0 的支持。

Flink 2.0 相比 1.x 引入了若干 API 变更和破坏性改动，主要包括：
1. **CatalogFactory 接口变更**：Flink 2.0 移除了旧的 `requiredContext()`/`supportedProperties()`/`createCatalog(name, properties)` 方法，改为 `factoryIdentifier()`/`requiredOptions()`/`optionalOptions()`/`createCatalog(Context)` 的新接口。
2. **TableSchema API 迁移**：`org.apache.flink.table.api.TableSchema` 和 `CatalogTableImpl` 被废弃，改为使用 `CatalogTable.newBuilder()` 构建器模式和 `org.apache.flink.table.legacy.api.TableSchema`。
3. **Operator API 变更**：Flink 2.0 移除了 `StreamOperator` 的部分工厂方法，需要引入 `IcebergFilesCommitterFactory` 来适配。
4. **Kryo 序列化问题**：Flink 2.0 中 Kryo 序列化器的 Scala 扩展存在问题，需要通过覆盖 `FlinkScalaKryoInstantiator` 类来规避。

同时，本提交还更新了构建配置（Gradle、CI、版本目录等），将 Flink 2.0 纳入构建体系和 CI 矩阵。

## 如何达成设计目的

整体设计分为两个层面：

**构建配置层面**：
- 在 `gradle/libs.versions.toml` 中新增 `flink20 = "2.0.0"` 版本及对应的依赖库坐标（flink20-avro、flink20-core、flink20-runtime 等）
- 在 `settings.gradle` 中注册 `iceberg-flink-2.0` 和 `iceberg-flink-runtime-2.0` 子项目
- 在 `flink/build.gradle` 中添加对 v2.0 目录的 `apply from` 引用
- 在 `jmh.gradle` 中将 v2.0 项目加入 JMH 基准测试列表
- 在 `gradle.properties` 中将默认 Flink 版本设为 `2.0`，已知版本列表添加 `2.0`
- 在 CI 配置 `.github/workflows/flink-ci.yml` 中将矩阵从 `['1.18', '1.19', '1.20']` 更新为 `['1.19', '1.20', '2.0']`
- 在 `dev/stage-binaries.sh` 中将 `FLINK_VERSIONS` 更新为 `1.19,1.20,2.0`

**源码适配层面**：
- 修改 `FlinkCatalogFactory` 以实现 Flink 2.0 的新 `CatalogFactory` 接口
- 修改 `FlinkCatalog` 以使用新的 `CatalogTable` 构建器替代废弃的 `CatalogTableImpl`
- 新增 `IcebergFilesCommitterFactory` 以适配 Flink 2.0 的 Operator 工厂 API
- 新增 `FlinkScalaKryoInstantiator` 空类以规避 Kryo 序列化问题
- 修改多个 source/sink/maintenance 相关类以适配 API 变更

## 修改详情

### `gradle/libs.versions.toml` (修改, +12/-0 lines)

**修改目的**：在版本目录中注册 Flink 2.0 的版本号和依赖坐标。

**工作逻辑**：
新增 `flink20 = { strictly = "2.0.0" }` 版本定义，以及两组依赖坐标：编译依赖（flink20-avro、flink20-connector-base、flink20-connector-files、flink20-metrics-dropwizard、flink20-streaming-java、flink20-table-api-java-bridge）和测试依赖（flink20-connector-test-utils、flink20-core、flink20-runtime、flink20-test-utils、flink20-test-utilsjunit）。

### `settings.gradle` (修改, +9/-0 lines)

**修改目的**：在 Gradle 设置中注册 Flink 2.0 子项目。

**工作逻辑**：
当 `flinkVersions` 包含 `"2.0"` 时，include `:iceberg-flink:flink-2.0` 和 `:iceberg-flink:flink-runtime-2.0` 两个子项目，分别指向 `flink/v2.0/flink` 和 `flink/v2.0/flink-runtime` 目录，并设置项目名称。

### `flink/build.gradle` (修改, +4/-0 lines)

**修改目的**：在 Flink 顶层构建文件中引入 v2.0 的构建脚本。

**工作逻辑**：
新增条件块：当 `flinkVersions` 包含 `"2.0"` 时，`apply from: file("$projectDir/v2.0/build.gradle")`。

### `flink/v2.0/build.gradle` (修改, +18/-18 lines)

**修改目的**：将 v2.0 构建文件中的 Flink 版本和依赖从 1.20 切换到 2.0。

**工作逻辑**：
- `flinkMajorVersion` 从 `'1.20'` 改为 `'2.0'`
- 所有 `libs.flink120.*` 依赖引用改为 `libs.flink20.*`
- `flink-table-planner` 的版本引用从 `flink120` 改为 `flink20`

### `gradle.properties` (修改, +2/-2 lines)

**修改目的**：更新默认 Flink 版本和已知版本列表。

**工作逻辑**：
`defaultFlinkVersions` 从 `1.20` 改为 `2.0`，`knownFlinkVersions` 从 `1.18,1.19,1.20` 改为 `1.18,1.19,1.20,2.0`。

### `.github/workflows/flink-ci.yml` (修改, +1/-1 lines)

**修改目的**：更新 CI 矩阵，用 Flink 2.0 替换 Flink 1.18。

**工作逻辑**：
CI 矩阵的 flink 维度从 `['1.18', '1.19', '1.20']` 更新为 `['1.19', '1.20', '2.0']`。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/FlinkCatalogFactory.java` (修改, +18/-15 lines)

**修改目的**：适配 Flink 2.0 新的 `CatalogFactory` 接口。

**工作逻辑**：
Flink 2.0 废弃了旧的 `CatalogFactory` 接口方法。主要变更：
- 新增 `FACTORY_IDENTIFIER = "iceberg"` 常量
- 移除 `TYPE` 和 `PROPERTY_VERSION` 常量
- `requiredContext()` 方法替换为 `factoryIdentifier()` 返回 `"iceberg"`
- `supportedProperties()` 方法替换为 `requiredOptions()` 和 `optionalOptions()`，均返回空集
- `createCatalog(String name, Map properties)` 替换为 `createCatalog(Context context)`，通过 `context.getName()` 和 `context.getOptions()` 获取参数

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/FlinkCatalog.java` (修改, +9/-4 lines)

**修改目的**：适配 Flink 2.0 中 `TableSchema` 和 `CatalogTable` 的 API 变更。

**工作逻辑**：
- 导入从 `org.apache.flink.table.api.TableSchema` 改为 `org.apache.flink.table.legacy.api.TableSchema`（Flink 2.0 将旧 API 移到 legacy 包）
- 移除 `CatalogTableImpl` 导入
- `copyCatalogTable` 方法中，用 `CatalogTable.newBuilder().schema(schema.toSchema()).partitionKeys(partitionKeys).options(props).build()` 替代 `new CatalogTableImpl(schema, partitionKeys, props, null)`，使用构建器模式创建 CatalogTable

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergFilesCommitterFactory.java` (新增, +72 lines)

**修改目的**：为 Flink 2.0 提供 `IcebergFilesCommitter` 的 Operator 工厂类。

**工作逻辑**：
Flink 2.0 的 Operator API 要求使用 `OneInputStreamOperatorFactory` 接口。该工厂类实现 `OneInputStreamOperatorFactory<FlinkWriteResult, Void>`，持有 tableLoader、overwriteMode、snapshotProperties、workerPoolSize、branch、spec 等参数，通过 `createStreamOperator()` 创建 `IcebergFilesCommitter` 实例。设置 `ChainingStrategy.ALWAYS` 允许算子链式合并。

### `flink/v2.0/flink/src/main/java/org/apache/flink/table/api/runtime/types/FlinkScalaKryoInstantiator.java` (新增, +26 lines)

**修改目的**：规避 Flink 2.0 中 Kryo 序列化器的 Scala 扩展问题。

**工作逻辑**：
创建一个空的 `FlinkScalaKryoInstantiator` 类，覆盖 Flink 内部的同名类，避免加载 KryoSerializer 的 Scala 扩展。这是针对 FLINK-37546 问题的临时 workaround，直到 Flink 修复 Kryo 相关问题。

### 其他源码文件（约 60 个文件） (修改)

**修改目的**：适配 Flink 2.0 中各种 API 变更。

**工作逻辑**：
涉及多个模块的适配性修改，主要包括：
- **Sink 模块**：`FlinkSink`、`IcebergSink`、`IcebergStreamWriter`、`IcebergFilesCommitter`、`DataStatisticsOperator` 等适配新的 Operator API
- **Source 模块**：`StreamingReaderOperator`、`StreamingMonitorFunction`、`IcebergTableSource` 等适配 API 变更
- **Maintenance 模块**：`TriggerManager`、`ExpireSnapshotsProcessor` 等适配变更
- **测试文件**：大量测试类适配 Flink 2.0 API，如 `TestFlinkCatalogTable`、`TestIcebergCommitter`、`TestMonitorSource` 等
- 移除 `org.apache.flink.table.factories.TableFactory` SPI 注册文件（Flink 2.0 不再使用旧的 TableFactory SPI）

## 总结

本提交是 Flink 2.0 支持系列的核心提交，通过修改构建配置和适配源码，正式在 Iceberg 中添加了对 Flink 2.0 的支持。主要适配了 Flink 2.0 的 CatalogFactory 新接口、TableSchema/CatalogTable API 迁移、Operator 工厂 API 变更，并通过覆盖类规避了 Kryo 序列化问题。同时更新了 CI 矩阵和构建脚本，将 Flink 2.0 纳入持续集成。涉及 72 个文件，净增 136 行。
