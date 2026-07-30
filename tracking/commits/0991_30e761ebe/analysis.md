# 提交 0991：Kafka Connect: Runtime distribution with integration tests (#10739)

## 提交信息

- **序号**：0991 / 4088
- **哈希**：30e761ebe420cf56f4d985664e6fa6d2a76de171
- **短哈希**：30e761ebe
- **日期**：2024-07-29 07:55:45 -0700
- **作者**：Bryan Keller
- **提交说明**：Kafka Connect: Runtime distribution with integration tests (#10739)
- **PR/Issue**：#10739

## 总体目的

Iceberg Kafka Connect Sink 连接器在此前只有 `iceberg-kafka-connect` 与 `iceberg-kafka-connect-events` 两个模块：前者是连接器主代码（SinkConnector / SinkTask 实现），后者是事件数据模型。这两个模块只产出 jar，没有把连接器与所有运行时依赖打包成一个可直接放进 Kafka Connect `plugin.path` 的发行包（distribution），用户若想实际部署，需要自己解决传递依赖、版本冲突、CVE 修复等大量复杂工作，门槛很高。

本提交新增 `kafka-connect-runtime` 模块，专门负责：
1. 用 Gradle `distribution` 插件把 connector 与所有运行时依赖（iceberg-core、aws、gcp、azure、orc、parquet 等）按 Kafka Connect 插件目录结构组装成 zip；
2. 提供两种发行包：`main`（不含 Hive metastore 客户端，适合 REST/S3/GCS/Azure 等场景）与 `hive`（额外包含 Hive metastore 客户端依赖，适合 hive catalog 场景）；
3. 在发行包中加入 `manifest.json`（Confluent Connector Hub 元数据）与 `iceberg.png`（logo）资源；
4. 通过 `resolutionStrategy.force` 修复传递依赖中的已知 CVE（jettison、snappy-java、commons-compress、hadoop-shaded-guava）；
5. 新增一套基于 Testcontainers + Docker Compose 的集成测试，覆盖单表分区写入、多表写入、动态表写入等场景，验证端到端行为（从 Kafka 消息→Connect→Iceberg REST catalog→MinIO S3→数据文件提交）。

此外，本提交顺带修了两个小问题：(a) `IcebergSinkConfig` 中 `WRITE_PROP_PREFIX` 前缀拼错（`iceberg.table.write-props.` 应为 `iceberg.tables.write-props.`，与 `TABLES_PROP` 一致）；(b) `IcebergSinkTask.put/flush` 在 `committer` 未初始化时抛 NPE 改为安静跳过，提升容错。

## 如何达成设计目的

整体设计思路是把"运行时打包"与"集成测试"绑定在一起：
- 新模块 `iceberg-kafka-connect-runtime` 不产出 jar（`tasks.jar.enabled = false`），只产出 zip 发行包；
- `distributions` 块定义 `main` 与 `hive` 两个发行包，分别从 `runtimeClasspath` 与自定义 `hive` configuration 拉取依赖；
- 集成测试源集 `integration` 通过 `installDist` 任务先把 main 发行包安装到本地 `build/install/` 目录，再用 Testcontainers 启动 docker-compose（Kafka + Connect + MinIO + Iceberg REST），Connect 容器通过 volume 挂载 `build/install/` 作为 plugin 路径，从而测试真正打包出来的发行包；
- `check.dependsOn integrationTest`、`integrationTest.dependsOn installDist`、`assemble.dependsOn distZip, hiveDistZip` 把任务依赖串起来，确保 CI 与 release 流程都跑测试与产包。

依赖管理上，对 Hadoop、Hive 等带大量传递依赖的库逐项 exclude，避免 jar 冲突与类加载问题，并通过 `force` 覆盖已知 CVE 版本。

## 修改详情

### `settings.gradle`

**修改目的**：把新模块 `kafka-connect-runtime` 纳入 Gradle 构建。

**工作逻辑**：

```gradle
include ":iceberg-kafka-connect:kafka-connect-runtime"
project(":iceberg-kafka-connect:kafka-connect-runtime").projectDir = file('kafka-connect/kafka-connect-runtime')
project(":iceberg-kafka-connect:kafka-connect-runtime").name = "iceberg-kafka-connect-runtime"
```

与已有 `iceberg-kafka-connect-events`、`iceberg-kafka-connect` 子模块并列，路径与 project name 都做了显式映射。

### `gradle/libs.versions.toml`

**修改目的**：把 `hadoop3-client` 这个 version alias 改名为更通用的 `hadoop3`，并新增 `hadoop3-common` alias，以便 runtime 模块同时引用 `hadoop-client` 与 `hadoop-common`。

**工作逻辑**：

```diff
-hadoop3-client = "3.3.6"
+hadoop3 = "3.3.6"
...
-hadoop3-client = { module = "org.apache.hadoop:hadoop-client", version.ref = "hadoop3-client" }
+hadoop3-client = { module = "org.apache.hadoop:hadoop-client", version.ref = "hadoop3" }
+hadoop3-common = { module = "org.apache.hadoop:hadoop-common", version.ref = "hadoop3" }
```

`hadoop3-client` 别名保留，方便已有引用不破坏；新别名 `hadoop3-common` 给 runtime 模块使用。

### `kafka-connect/build.gradle`

**修改目的**：在 kafka-connect 父 build.gradle 中追加第三个子项目 `iceberg-kafka-connect-runtime` 的完整构建配置（约 180 行新增）。

**工作逻辑**：

1. **`apply plugin: 'distribution'`**：启用 Gradle 发行包插件，提供 `distZip`、`distTar`、`installDist` 等任务。
2. **`configurations.hive`**：新建一个继承自 `runtimeClasspath` 的 configuration，专门收集带 Hive metastore 的依赖；在 `all` 块里全局 exclude `javax.activation:activation`，并通过 `resolutionStrategy.force` 修复 jettison、snappy-java、commons-compress、hadoop-shaded-guava 的 CVE 版本。
3. **`sourceSets.integration`**：声明独立的 integration 源集（`src/integration/java`、`src/integration/resources`），把 integration 测试与单元测试隔离；`integrationImplementation` / `integrationRuntime` 继承自 `testImplementation` / `testRuntimeOnly`。
4. **`dependencies` 块**：
   - `implementation project(':iceberg-kafka-connect:iceberg-kafka-connect')`：引入 connector 主代码；
   - `implementation(libs.hadoop3.common)`：引入 hadoop-common 并大量 exclude（log4j、slf4j、avro、curator、zookeeper 等），避免与 connector 已有依赖冲突；
   - `implementation project(':iceberg-orc')` / `:iceberg-parquet`：让 runtime 自带 ORC/Parquet 写入支持；
   - `implementation project(':iceberg-aws')` + `platform(libs.awssdk.bom)` + 显式 aws-sdk 子模块（apache-client、auth、iam、sso、s3、kms、glue、sts、dynamodb、lakeformation）：提供 S3/Glue/DynamoDB/LakeFormation 集成；
   - `implementation project(':iceberg-gcp')` + `platform(libs.google.libraries.bom)` + `google-cloud-storage`：提供 GCS 集成；
   - `implementation project(':iceberg-azure')` + `platform(libs.azuresdk.bom)` + `azure-storage-file-datalake` / `azure-identity`：提供 ADLS 集成；
   - `hive project(':iceberg-hive-metastore')` + `hive(libs.hive2.metastore)` + `hive(libs.hadoop3.client)`：hive configuration 额外带 Hive metastore 客户端，并大量 exclude（avro、hbase、jetty、parquet-hadoop-bundle 等）；
   - `integrationImplementation` 引入 iceberg-api/common/core、bundled-guava、jackson、kafka-clients、kafka-connect-api/json、testcontainers、httpclient5、awaitility 等，用于集成测试。
5. **`task integrationTest`**：自定义 Test 任务，使用 integration 源集，`useJUnitPlatform()`，并把 `extraJvmArgs` 传给 JVM。
6. **`processResources`**：把 `manifest.json` 中的 `__VERSION__` 占位符替换为 `project.version`。
7. **`distributions.main`** 与 **`distributions.hive`**：定义两个发行包结构——`manifest.json` 放根目录、依赖 jar 放 `lib/`、LICENSE 放 `doc/`、`iceberg.png` 放 `assets/`；main 用 `runtimeClasspath`，hive 用 `configurations.hive`。
8. **任务依赖串联**：`tasks.jar.enabled = false`（不产 jar）；`tasks.distTar.enabled = false` 与 `tasks.hiveDistTar.enabled = false`（只产 zip 不产 tar，节省构建时间）；`distZip.dependsOn processResources`；`integrationTest.dependsOn installDist`；`check.dependsOn integrationTest`；`assemble.dependsOn distZip, hiveDistZip`。

### `kafka-connect/kafka-connect-runtime/src/main/resources/manifest.json`（新增）

**修改目的**：提供 Confluent Connector Hub / Kafka Connect 识别插件所需的元数据。

**工作逻辑**：JSON 字段包括 `title`、`name`（iceberg-kafka-connect）、`version`（构建时被替换为 `__VERSION__`）、`description`、`component_types: ["sink"]`、`delivery_guarantee: ["exactly_once"]`、`supported_encodings: ["any"]`、`license`、`owner`（Apache Software Foundation）、`support`（社区支持链接）等。

### `kafka-connect/kafka-connect-runtime/src/main/resources/iceberg.png`（新增）

**修改目的**：连接器 logo 资源，供 manifest.json 引用与 Connector Hub 展示。

### `kafka-connect/kafka-connect-runtime/docker/docker-compose.yml`（新增）

**修改目的**：定义集成测试所需的服务栈（MinIO、Iceberg REST、Kafka、Kafka Connect），由 Testcontainers 启动。

**工作逻辑**：
- `minio`：S3 兼容对象存储（端口 9000/9001），通过 `create-bucket` 服务预建 bucket；
- `iceberg`：`tabulario/iceberg-rest` REST catalog 镜像，指向 MinIO 作为 warehouse；
- `kafka`：`confluentinc/cp-kafka` 单节点 KRaft 模式（无 ZooKeeper），暴露 29092 给宿主机；
- `connect`：`confluentinc/cp-kafka-connect` 镜像，把宿主机的 `../build/install/` 挂载到容器 `/test/kafka-connect`，作为 `CONNECT_PLUGIN_PATH`，从而加载刚打包的 iceberg-kafka-connect 发行包；配置 JSON converter 与 500ms offset flush 间隔。

### `kafka-connect/kafka-connect-runtime/src/integration/java/org/apache/iceberg/connect/TestContext.java`（新增）

**修改目的**：集成测试共享的单例上下文，负责启动 docker-compose、初始化本地 REST catalog、Kafka producer、Kafka admin、connector catalog 配置等。

**工作逻辑**：
- `INSTANCE` 单例在构造时用 `ComposeContainer` 启动 `./docker/docker-compose.yml`，并 `waitingFor("connect", Wait.forHttp("/connectors"))` 直到 Connect REST 就绪；
- `initLocalCatalog()`：用 `RESTCatalog` 连到本地 8181 端口，配置 S3FileIO 指向 localhost:9000 的 MinIO；
- `connectorCatalogProperties()`：返回 connector 配置用的 catalog 属性（注意这里 URI 用 `http://iceberg:8181`，因为是 Connect 容器内访问）；
- `initLocalProducer()` / `initLocalAdmin()`：本地 Kafka 客户端，连 `localhost:29092`；
- `startConnector/stopConnector`：委托给 `KafkaConnectUtils`。

### `kafka-connect/kafka-connect-runtime/src/integration/java/org/apache/iceberg/connect/KafkaConnectUtils.java`（新增）

**修改目的**：封装对 Kafka Connect REST API 的调用（创建/停止/查询 connector、获取 connector 状态、获取配置）。

**工作逻辑**：用 Apache HttpClient5 调用 `http://localhost:8083/connectors` 等 REST 端点，JSON 解析用 Jackson。`ensureConnectorRunning` 轮询直到 connector 状态变为 RUNNING。

### `kafka-connect/kafka-connect-runtime/src/integration/java/org/apache/iceberg/connect/TestEvent.java`（新增）

**修改目的**：定义集成测试用的事件数据模型，可序列化为 JSON 或 Kafka Connect Struct。

**工作逻辑**：定义 `TEST_SCHEMA`（id、type、ts、payload 四字段，pk = id）与 `TEST_SPEC`（按 `type` 分区）；`serialize(useSchema)` 用 JsonConverter 把事件转成 JSON 字符串，方便 producer 直接发送。

### `kafka-connect/kafka-connect-runtime/src/integration/java/org/apache/iceberg/connect/IntegrationTestBase.java`（新增）

**修改目的**：所有集成测试的基类，提供 catalog/admin/producer 初始化、topic 创建删除、事件发送、snapshot 属性断言、data/delete 文件枚举等通用方法。

**工作逻辑**：
- `@BeforeEach` 创建本地 catalog、producer、admin，并为每个测试分配随机 `connectorName` 与 `testTopic`；
- `@AfterEach` 关闭资源；
- `assertSnapshotProps`：断言 snapshot summary 含 `kafka.connect.offsets.*` 与 `kafka.connect.commit-id`，验证 commit 元信息正确写入；
- `dataFiles` / `deleteFiles`：枚举最新 snapshot 新增的数据/删除文件；
- `createTopic` / `deleteTopic` / `send` / `flush`：Kafka 操作辅助。

### `kafka-connect/kafka-connect-runtime/src/integration/java/org/apache/iceberg/connect/IntegrationTest.java`（新增）

**修改目的**：单表分区写入的端到端集成测试。

**工作逻辑**：
- 参数化测试（`@NullSource` + `@ValueSource(strings = "test_branch")`）覆盖无分支与有分支两种 commit 路径；
- `testIcebergSinkPartitionedTable`：建表、发消息、启动 connector、等待数据文件出现、断言文件数与每文件记录数、断言 snapshot 属性；
- 还有未在 diff 头部展示的其它测试方法（如未分区表、schema 演进等）。

### `kafka-connect/kafka-connect-runtime/src/integration/java/org/apache/iceberg/connect/IntegrationMultiTableTest.java`（新增）

**修改目的**：多表写入集成测试。

**工作逻辑**：在同一个 connector 中配置多张 Iceberg 表（通过 topic→table 路由），验证每张表都能正确收到数据并提交 snapshot。

### `kafka-connect/kafka-connect-runtime/src/integration/java/org/apache/iceberg/connect/IntegrationDynamicTableTest.java`（新增）

**修改目的**：动态表（dynamic table）写入集成测试。

**工作逻辑**：connector 根据消息 topic 动态决定写入哪张表（而非预先在配置中列出），验证动态表创建与写入行为。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/IcebergSinkConfig.java`（修改）

**修改目的**：修复 write-props 配置前缀。

**工作逻辑**：

```diff
-  private static final String WRITE_PROP_PREFIX = "iceberg.table.write-props.";
+  private static final String WRITE_PROP_PREFIX = "iceberg.tables.write-props.";
```

原前缀 `iceberg.table.write-props.` 与其它 `iceberg.tables.*` 前缀不一致（单数 table vs 复数 tables），导致用户配置 `iceberg.tables.write-props.*` 时无法被识别。修正为复数以与 `TABLES_PROP = "iceberg.tables"` 一致。这是一个用户可见的兼容性变更：之前用单数前缀的旧配置将不再生效，需要迁移到复数前缀。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/IcebergSinkTask.java`（修改）

**修改目的**：让 `put` 与 `flush` 在 `committer` 未初始化时安静跳过，而不是抛 `NullPointerException`。

**工作逻辑**：

```diff
   @Override
   public void put(Collection<SinkRecord> sinkRecords) {
-    Preconditions.checkNotNull(committer, "Committer wasn't initialized");
-    committer.save(sinkRecords);
+    if (committer != null) {
+      committer.save(sinkRecords);
+    }
   }

   @Override
   public void flush(Map<TopicPartition, OffsetAndMetadata> currentOffsets) {
-    Preconditions.checkNotNull(committer, "Committer wasn't initialized");
-    committer.save(null);
+    if (committer != null) {
+      committer.save(null);
+    }
   }
```

这在 connector 启动失败、committer 未就绪的边界场景下避免任务直接异常退出，让 Kafka Connect 框架有机会重试或记录更明确的错误。

## 小结

- **成效**：(1) 新增 `iceberg-kafka-connect-runtime` 模块，产出可直接部署到 Kafka Connect 的 zip 发行包（main 与 hive 两种），并在打包阶段修复已知 CVE；(2) 提供完整的基于 Testcontainers + Docker Compose 的集成测试套件（单表/多表/动态表），在 CI 上验证 connector 端到端行为；(3) 修复 `IcebergSinkConfig.WRITE_PROP_PREFIX` 前缀拼写问题；(4) 让 `IcebergSinkTask.put/flush` 在 committer 未就绪时优雅降级。
- **影响范围**：新增 `kafka-connect-runtime` 模块（10 个新文件，约 1350 行）；修改 `settings.gradle`、`gradle/libs.versions.toml`、`kafka-connect/build.gradle`、`IcebergSinkConfig.java`、`IcebergSinkTask.java`。
- **回迁到 1.4.x 的注意事项**：建议回迁，但需注意：(1) `IcebergSinkConfig.WRITE_PROP_PREFIX` 从单数改复数是用户可见的配置兼容性变更，回迁后需在 release notes 中明确告知用户迁移配置前缀；(2) 集成测试依赖 Docker 与 Testcontainers，需确认 1.4.x 的 CI 环境支持；(3) 新模块引入大量传递依赖（aws/gcp/azure/hive），需确认 1.4.x 上这些 catalog 模块版本与 runtime 模块期望一致；(4) 若 1.4.x 上已存在用户自行打包的部署方案，回迁后可能与用户既有流程冲突，建议作为可选模块发布。
