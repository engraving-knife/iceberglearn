# 提交 1145：Kafka Connect: Docs on configuring the sink (#10746)

## 提交信息

- **序号**：1145
- **哈希**：5439cbdb278232779fdd9a392bbf57f007f9bda0
- **短哈希**：5439cbdb2
- **日期**：2024-09-10（Tue Sep 10 09:42:31 2024 -0700）
- **作者**：Bryan Keller <bryanck@gmail.com>
- **提交说明**：Kafka Connect: Docs on configuring the sink (#10746)
- **PR/Issue**：#10746
- **共同作者**：Daniel Weeks <daniel.weeks@databricks.com>、Ajantha Bhat <ajanthabhat@gmail.com>

## 总体目的

Apache Iceberg 在 `kafka-connect/` 模块下提供了一个官方的 Kafka Connect Sink Connector（`IcebergSinkConnector`），用于将 Kafka 中的数据持续写入 Iceberg 表。该 connector 已具备较多能力（集中式 commit 协调、exactly-once 语义、多表 fan-out、自动建表与 schema evolution、column mapping 等），但仓库文档站此前缺少一篇面向使用者的"如何配置 Sink Connector"指南，导致用户只能阅读源码或零散的 README。

本提交新增一篇完整的产品文档 `docs/docs/kafka-connect.md`（352 行），系统讲解 Connector 的能力、安装、配置项、各类 Catalog（REST/Hive/Glue/Nessie）与存储（S3/ADLS/GCS/HDFS）的对接示例，以及单表/多表 fan-out 的实战配置；同时把它注册到 `docs/mkdocs.yml` 的导航中，让用户能从 Iceberg 文档站直接进入。

## 如何达成设计目的

1. **新建文档**：在 `docs/docs/kafka-connect.md` 中按 mkdocs Material 主题的格式撰写完整文档，含 YAML front matter（`title: "Kafka Connect"`）、ASF License 头、章节层级、配置表格、JSON/SQL/bash 代码块。
2. **注册导航**：在 `docs/mkdocs.yml` 的 `nav:` 列表中、`Druid` 之后、`Integrations` 之前追加一行 `- Kafka Connect: kafka-connect.md`，使该文档出现在文档站顶部导航的"引擎集成"区段。

文档本身是新增的纯文档，不涉及任何 Java/构建代码变更。

## 修改详情

### `docs/docs/kafka-connect.md`（新增 352 行）

**修改目的**：作为 Iceberg Sink Connector 的官方使用文档。

**工作逻辑与内容结构**：

1. **引言**：介绍 Kafka Connect 框架与 Iceberg Sink Connector 的定位——"将 Kafka 数据写入 Iceberg 表的 sink connector"。
2. **Features**：列出 connector 五大能力：
   - Commit coordination for centralized Iceberg commits（集中式 commit 协调）
   - Exactly-once delivery semantics（精确一次语义）
   - Multi-table fan-out（多表扇出）
   - Automatic table creation and schema evolution（自动建表与 schema 演进）
   - Field name mapping via Iceberg's column mapping（通过 column mapping 实现字段名映射）
3. **Installation**：通过 `./gradlew -x test -x integrationTest clean build` 构建，产物在 `./kafka-connect/kafka-connect-runtime/build/distributions`，提供两份发行包（一份捆绑 Hive Metastore 客户端，一份不捆绑），拷贝到 Kafka Connect plugins 目录即可。
4. **Requirements**：依赖 KIP-447 实现 exactly-once，要求 **Kafka 2.5+**。这是文档化的重要前置约束。
5. **Configuration 表**：以一张完整表格列出所有配置项及其说明，按语义分四组：
   - **表相关**：`iceberg.tables`、`iceberg.tables.dynamic-enabled`、`iceberg.tables.route-field`、`iceberg.tables.default-commit-branch`、`iceberg.tables.default-id-columns`、`iceberg.tables.default-partition-by`、`iceberg.tables.auto-create-enabled`、`iceberg.tables.evolve-schema-enabled`、`iceberg.tables.schema-force-optional`、`iceberg.tables.schema-case-insensitive`、`iceberg.tables.auto-create-props.*`、`iceberg.tables.write-props.*`；
   - **表级覆盖**：`iceberg.table.<table name>.commit-branch`、`id-columns`、`partition-by`、`route-regex`；
   - **控制 topic**：`iceberg.control.topic`（默认 `control-iceberg`）、`iceberg.control.commit.interval-ms`（默认 300000=5 分钟）、`iceberg.control.commit.timeout-ms`（默认 30000=30 秒）、`iceberg.control.commit.threads`（默认 cores*2）；
   - **Catalog / Hadoop / Kafka**：`iceberg.catalog`、`iceberg.catalog.*`、`iceberg.hadoop-conf-dir`、`iceberg.hadoop.*`、`iceberg.kafka.*`。
6. **路由规则说明**：明确 `dynamic-enabled=false`（默认）时必须指定 `iceberg.tables`；`dynamic-enabled=true` 时必须指定 `iceberg.tables.route-field`，按记录字段值动态决定目标表名。
7. **Kafka configuration 子节**：默认从 worker properties 读取 Kafka 客户端配置连控制 topic，读不到时可用 `iceberg.kafka.*` 显式覆盖；并说明消息需用合适的 Connect converter 转为 struct/map。
8. **Catalog configuration 子节**：列举核心 catalog 类型（REST、Glue、DynamoDB、Hadoop、Nessie、JDBC、Hive）包含在默认发行包中，JDBC 驱动需自带；Hive catalog 建议用捆绑 HMS 客户端的发行包。给出 `iceberg.catalog.type`（rest/hive/hadoop）与 `iceberg.catalog.catalog-impl`（其他类型）两种设置方式。
9. **四类 Catalog 示例**：分别给出 REST、Hive（含 S3FileIO 与 S3 凭据）、Glue、Nessie 的 JSON 配置片段。Hive 示例特别提醒："使用 S3 存储时务必把 `io-impl` 设为 `org.apache.iceberg.aws.s3.S3FileIO`，否则默认 `HadoopFileIO` 与 `HiveCatalog` 组合不适用"。
10. **Azure ADLS 配置示例**：详细说明 Azure Java SDK 要求注入 `AZURE_CLIENT_ID`、`AZURE_TENANT_ID`、`AZURE_CLIENT_SECRET` 三个环境变量，并指明三者的获取位置（App Registrations / Tenant Properties / Certificates & Secrets）；强调 App Registration 必须被授予 "Storage Blob Data Contributor" 角色；最后给出 connector 配置示例，使用 `ADLSFileIO` 与 `iceberg.catalog.include-credentials=true`。
11. **Google GCS 配置示例**：默认使用 Application Default Credentials（ADC），链接到 Google Cloud 官方文档；给出使用 `GCSFileIO` 的 JSON 配置片段。
12. **Hadoop configuration 子节**：说明 Hadoop 配置的加载顺序与优先级——classpath → `iceberg.hadoop-conf-dir` → `iceberg.hadoop.*`，后者优先级最高。
13. **Examples 章节**：给出三个端到端示例：
    - **Single destination table**：单表写入，含建表 SQL 与 connector JSON 配置；
    - **Multi-table fan-out, static routing**：静态多表路由，按 `type` 字段值通过 `route-regex` 匹配到 `events_list` / `events_create` 两张表，其他记录跳过；
    - **Multi-table fan-out, dynamic routing**：动态多表路由，`dynamic-enabled=true`，按 `db_table` 字段值作为目标表名，表不存在则跳过该记录。
14. **Control topic 创建说明**：当 Kafka 集群 `auto.create.topics.enable=true`（默认）时控制 topic 自动创建，否则需手动 `bin/kafka-topics --create ...`；并提示 Confluent Cloud 默认关闭自动创建。

### `docs/mkdocs.yml`

**修改目的**：将新增文档加入文档站导航。

**工作逻辑**：在 `nav:` 列表中，紧跟 `Druid` 之后、`Integrations:` 区段之前，插入 `- Kafka Connect: kafka-connect.md`。这样文档站的"引擎集成"区域就会出现 "Kafka Connect" 入口，点击后渲染 `docs/docs/kafka-connect.md`。

## 小结

- **成效**：填补了 Iceberg Kafka Connect Sink Connector 长期缺少官方使用文档的空白；新增的 352 行文档覆盖安装、全部配置项、四类 Catalog 与三种云存储的对接示例、以及三种典型路由场景的端到端配置，使用者可"照抄即用"；同时通过 mkdocs 导航注册让该文档对用户可见。
- **影响范围**：仅 `docs/docs/kafka-connect.md`（新增）与 `docs/mkdocs.yml`（+1 行），共 +353 行，无任何代码或构建脚本变更。
- **回迁到 1.4.x 的注意事项**：
  - 这是纯文档新增，**无运行时影响**，回迁零风险。
  - **是否回迁取决于 1.4.x 是否包含 Kafka Connect 模块**：若 1.4.x 分支已存在 `kafka-connect/` 模块（即 connector 已发布或即将发布），强烈建议回迁此文档以提供用户指引；若 1.4.x 早于 Kafka Connect 模块引入时间，则文档与代码不匹配，不应回迁。
  - 回迁时需同时回迁 `docs/mkdocs.yml` 的导航条目，否则文档存在但用户在导航中找不到入口。
  - 注意校对文档中提到的配置项是否与 1.4.x 实际支持的配置项一致——若 1.4.x 的 connector 版本较老，部分配置项（如 `iceberg.tables.schema-force-optional`、`iceberg.control.commit.threads`）可能尚未引入，回迁后需在文档中标注版本差异，避免误导用户。
  - 文档中提到的 Kafka 2.5+ 要求与发行包位置 `kafka-connect/kafka-connect-runtime/build/distributions` 在 1.4.x 中应保持一致；如有差异需同步修正。
