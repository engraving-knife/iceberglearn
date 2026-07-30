# 提交 0456：Kafka Connect: Sink connector with data writers and converters (#9466)

## 提交信息

- **序号**：0456
- **完整哈希**：f4ba90d648d1ec692134c43fc24fe389aa687d5e
- **短哈希**：f4ba90d64
- **日期**：2024-02-03 12:40:45 -0800
- **作者**：Bryan Keller <bryanck@gmail.com>
- **提交说明**：Kafka Connect: Sink connector with data writers and converters (#9466)
- **关联 PR**：#9466
- **修改文件**：33 个，共 2774 行新增、1 行删除

## 总体目的

本提交是 Iceberg Kafka Connect sink connector 多阶段实现中的关键一环，引入了 sink connector 的核心骨架：配置体系、connector 入口、数据写入器（writer）与 schema 转换/演进能力。Iceberg 此前已存在一个 `kafka-connect-events` 子模块（用于在 worker 之间通过控制 topic 传递提交协调事件的 Avro 事件模型），但尚未有实际的 sink connector 实现。本提交补齐了这一缺口，新增 `iceberg-kafka-connect` 子模块，使其能够接收 Kafka Connect 的 `SinkRecord` 并写入 Iceberg 表。

整体设计围绕一个典型的 Kafka Connect sink 架构展开：`IcebergSinkConnector` 作为 connector 入口向 Connect 框架注册并生成各 task 的配置；`IcebergSinkConfig` 集中管理所有可配置项（catalog 属性、Hadoop 属性、表路由、自动建表、schema 演进、提交间隔等）；`IcebergWriterFactory` 负责按表名加载或自动创建 Iceberg 表并构造对应的 `RecordWriter`；`IcebergWriter` 负责将 `SinkRecord` 写入底层 `TaskWriter<Record>`；`SchemaUtils` 与 `SchemaUpdate` 负责 Kafka Connect schema 到 Iceberg schema 的转换以及 schema 演进；`Utilities` 提供 catalog 加载、Hadoop 配置加载、writer 构造等基础设施。

需要特别指出的是，本提交是一个增量交付：部分能力以 FIXME/TODO 标注尚未完成。例如 `IcebergSinkConnector.taskClass()` 当前返回 `null`（待"connector channel"加入后切换为 `IcebergSinkTask`），`IcebergWriter.convertToRow()` 当前返回 `null`（待"record converter"加入后实现实际转换），CDC/upsert 与 delta writer 也标注为待实现。因此本提交奠定的是框架与可测试的写入/建表/schema 转换基础，端到端的记录转换与提交协调链路将在后续提交补齐。

此外，提交对既有 `kafka-connect-events` 模块的事件类做了健壮性增强：为 `Event`、`CommitComplete`、`CommitToTable`、`DataComplete`、`DataWritten`、`StartCommit`、`TableReference`、`TopicPartitionOffset` 等构造器加入 `Preconditions.checkNotNull` 校验，并将 `AvroUtil` 从包级可见改为 `public`，以便新 connector 模块复用事件序列化能力。

## 如何达成设计目的

实现路径分为四部分：一是构建配置，在 `gradle/libs.versions.toml` 引入 kafka 3.6.1 及 kafka-clients/connect-api/connect-json 依赖，在 `kafka-connect/build.gradle` 与 `settings.gradle` 注册新的 `iceberg-kafka-connect` 子项目并声明其对 iceberg-api/core/common/data 及 events 模块的依赖；二是实现 connector 与配置（`IcebergSinkConnector`、`IcebergSinkConfig`、`TableSinkConfig`）；三是实现数据写入与 schema 转换（`RecordWriter` 接口及其 `IcebergWriter`/`NoOpWriter`/`PartitionedAppendWriter` 实现、`IcebergWriterFactory`、`SchemaUtils`、`SchemaUpdate`、`WriterResult`、`Utilities`）；四是为既有事件类补齐空值校验并将 `AvroUtil` 公开。同时配套编写了覆盖配置解析、connector task 配置、writer 工厂自动建表、分区/非分区写入、schema 转换与演进、catalog 与 Hadoop 配置加载、记录值提取等场景的单元测试。

## 修改详情

### gradle/libs.versions.toml

**修改目的**：引入 Kafka 依赖版本与坐标，供新 connector 模块使用。

**工作逻辑**：新增版本常量 `kafka = "3.6.1"`，并新增三个库坐标：`kafka-clients`（`org.apache.kafka:kafka-clients`）、`kafka-connect-api`（`org.apache.kafka:connect-api`）、`kafka-connect-json`（`org.apache.kafka:connect-json`），均引用 `kafka` 版本。这些依赖在 connector 模块中以 `compileOnly` 方式引入，因为运行时由 Kafka Connect 框架提供。

### settings.gradle

**修改目的**：将新 connector 子模块注册到 Gradle 构建体系。

**工作逻辑**：在既有 `iceberg-kafka-connect:kafka-connect-events` 注册块之后，新增三行：`include ":iceberg-kafka-connect:kafka-connect"`，并将其 `projectDir` 指向 `kafka-connect/kafka-connect`，`name` 设为 `iceberg-kafka-connect`。

### kafka-connect/build.gradle

**修改目的**：为新 connector 子项目声明依赖与测试配置。

**工作逻辑**：新增 `project(":iceberg-kafka-connect:iceberg-kafka-connect")` 块，声明 `api project(':iceberg-api')`，`implementation` 依赖 iceberg-core/common/bundled-guava/data/events 模块及 jackson、avro；`compileOnly` 引入 kafka-clients/connect-api/connect-json（运行时由 Connect 框架提供）；测试侧引入 hadoop3-client，`testRuntimeOnly` 引入 iceberg-parquet/orc 以支持多格式写入测试，并配置 `useJUnitPlatform()`。

### kafka-connect/kafka-connect-events/.../AvroUtil.java

**修改目的**：将 `AvroUtil` 从包级可见改为 `public`，供新 connector 模块复用。

**工作逻辑**：仅将 `class AvroUtil` 改为 `public class AvroUtil`，其余不变。该类封装 Avro schema 构建与序列化工具，新 connector 的提交协调链路将复用其能力。

### kafka-connect/kafka-connect-events/.../Event.java

**修改目的**：为 `Event` 构造器加入空值校验，防止构建无效事件。

**工作逻辑**：在 `Event(String groupId, Payload payload)` 构造器开头加入 `Preconditions.checkNotNull(groupId, "Group ID cannot be null")` 与 `Preconditions.checkNotNull(payload, "Payload cannot be null")`，确保 groupId 与 payload 非空后再赋值。

### kafka-connect/kafka-connect-events/.../CommitComplete.java

**修改目的**：为 `CommitComplete` 构造器加入 commitId 空值校验。

**工作逻辑**：在 `CommitComplete(UUID commitId, OffsetDateTime validThroughTs)` 构造器开头加入 `Preconditions.checkNotNull(commitId, "Commit ID cannot be null")`。

### kafka-connect/kafka-connect-events/.../CommitToTable.java

**修改目的**：为 `CommitToTable` 构造器加入 commitId 与 tableReference 空值校验。

**工作逻辑**：在构造器开头加入 `Preconditions.checkNotNull(commitId, "Commit ID cannot be null")` 与 `Preconditions.checkNotNull(tableReference, "Table reference cannot be null")`。

### kafka-connect/kafka-connect-events/.../DataComplete.java

**修改目的**：为 `DataComplete` 构造器加入 commitId 空值校验。

**工作逻辑**：在 `DataComplete(UUID commitId, List<TopicPartitionOffset> assignments)` 构造器开头加入 `Preconditions.checkNotNull(commitId, "Commit ID cannot be null")`。

### kafka-connect/kafka-connect-events/.../DataWritten.java

**修改目的**：为 `DataWritten` 构造器加入 commitId 与 tableReference 空值校验。

**工作逻辑**：在构造器开头加入两条 `Preconditions.checkNotNull`，分别校验 commitId 与 tableReference。

### kafka-connect/kafka-connect-events/.../StartCommit.java

**修改目的**：为 `StartCommit` 构造器加入 commitId 空值校验。

**工作逻辑**：在 `StartCommit(UUID commitId)` 构造器开头加入 `Preconditions.checkNotNull(commitId, "Commit ID cannot be null")`。

### kafka-connect/kafka-connect-events/.../TableReference.java

**修改目的**：为 `TableReference` 构造器加入 catalog、namespace、name 三项空值校验。

**工作逻辑**：在 `TableReference(String catalog, List<String> namespace, String name)` 构造器开头加入三条 `Preconditions.checkNotNull`，分别校验 catalog、namespace、name。

### kafka-connect/kafka-connect-events/.../TopicPartitionOffset.java

**修改目的**：为 `TopicPartitionOffset` 构造器加入 topic 空值校验。

**工作逻辑**：在构造器开头加入 `Preconditions.checkNotNull(topic, "Topic cannot be null")`。

### kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/IcebergSinkConfig.java（新文件，468 行）

**修改目的**：定义 sink connector 的全部配置项与解析逻辑。

**工作逻辑**：继承 Kafka Connect 的 `AbstractConfig`，通过 `ConfigDef` 定义各配置项。主要能力包括：

1. **配置定义**：`newConfigDef()` 定义 `iceberg.tables`（目标表列表）、`iceberg.tables.dynamic-enabled`（动态路由）、`iceberg.tables.route-field`（路由字段）、`iceberg.tables.default-commit-branch/id-columns/partition-by`（默认分支/ID列/分区）、`iceberg.tables.auto-create-enabled`、`iceberg.tables.evolve-schema-enabled`、`iceberg.tables.schema-force-optional`、`iceberg.tables.schema-case-insensitive`、`iceberg.catalog`（默认 "iceberg"）、`iceberg.control.topic`（默认 "control-iceberg"）、`iceberg.control.commit.interval-ms`（默认 300000）、`iceberg.control.commit.timeout-ms`（默认 30000）、`iceberg.control.commit.threads`（默认 cores*2）、`iceberg.hadoop-conf-dir` 等。

2. **属性前缀分组**：构造器中以不同前缀抽取属性子集——`iceberg.catalog.*`（catalogProps）、`iceberg.hadoop.*`（hadoopProps）、`iceberg.kafka.*`（kafkaProps）、`iceberg.tables.auto-create-props.*`（autoCreateProps）、`iceberg.table.write-props.*`（writeProps）。其中 kafkaProps 还会尝试加载 worker 属性文件（见 `loadWorkerProps`）。

3. **校验**：`validate()` 强制 catalogProps 非空，且静态表名与动态表名二选一，动态模式必须指定 route-field，否则抛 `ConfigException`。

4. **每表配置**：`tableConfig(String tableName)` 以 `iceberg.table.<tableName>.` 为前缀解析该表的 route-regex、id-columns、partition-by、commit-branch，缺失项回退到默认值，结果缓存在 `tableConfigMap`。`partition-by` 使用 `COMMA_NO_PARENS_REGEX`（`,(?![^()]*+\\))`）分割，以避免切分 `bucket(id, 4)` 这类带括号的 transform 表达式。

5. **worker 属性加载**：`loadWorkerProps()` 通过 `System.getProperty("sun.java.command")` 解析启动命令，若为 ConnectDistributed/ConnectStandalone 且带属性文件参数，则读取该文件，校验含 `bootstrap.servers` 后返回。这使得 Kafka 相关属性只需在 worker 属性中设置一次，无需在 sink config 重复。

6. **JSON converter**：构造一个禁用 schema 的 `JsonConverter`（value 类型），供事件序列化使用。

7. **版本**：`version()` 返回 Iceberg 版本 + "-kc-" + connector 包版本。

### kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/IcebergSinkConnector.java（新文件，73 行）

**修改目的**：Kafka Connect sink connector 入口，注册配置并生成 task 配置。

**工作逻辑**：继承 `SinkConnector`。`start()` 保存 connector 属性；`config()` 返回 `IcebergSinkConfig.CONFIG_DEF`；`version()` 委托 `IcebergSinkConfig.version()`；`taskConfigs(int maxTasks)` 为每个 task 生成一份配置副本，并注入唯一的 `iceberg.coordinator.transactional.suffix`（格式 `-txn-<UUID>-<i>`），用于在多 task 并发写时隔离事务性文件命名。`taskClass()` 当前返回 `null` 并标注 FIXME，待 connector channel 加入后切换为 `IcebergSinkTask`。

### kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/TableSinkConfig.java（新文件，54 行）

**修改目的**：封装单表级配置（路由正则、ID 列、分区字段、提交分支）。

**工作逻辑**：不可变值对象，持有 `Pattern routeRegex`、`List<String> idColumns`、`List<String> partitionBy`、`String commitBranch`，仅提供 getter。由 `IcebergSinkConfig.tableConfig()` 构造。

### kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/RecordWriter.java（新文件，31 行）

**修改目的**：定义写入器接口，抽象对 `SinkRecord` 的写入与完成操作。

**工作逻辑**：接口继承 `Cloneable`，声明三个方法：`void write(SinkRecord record)` 写入单条记录、`List<WriterResult> complete()` 完成写入并返回结果列表、`void close()` 关闭资源。

### kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/IcebergWriter.java（新文件，118 行）

**修改目的**：核心写入器，将 `SinkRecord` 写入底层 Iceberg `TaskWriter<Record>`。

**工作逻辑**：

1. 构造时持有 `Table`、`tableName`、`IcebergSinkConfig` 与 `writerResults` 列表，调用 `Utilities.createTableWriter` 初始化底层 `TaskWriter`。FIXME 标注 recordConverter 待加入。

2. `write(SinkRecord)`：忽略 value 为 null 的记录（tombstone，TODO 标注可配置处理），调用 `convertToRow(record)` 转换为 Iceberg `Record` 后调用 `writer.write(row)`。异常包装为 `DataException` 并附 topic/partition/offset 信息。FIXME 标注 CDC 操作支持待加入。

3. `convertToRow(SinkRecord)`：当前返回 `null`（FIXME：待 record converter 加入后实现真实转换），即端到端转换尚未接通。

4. `flush()`：调用 `writer.complete()` 得到 `WriteResult`，将其 dataFiles/deleteFiles 连同表标识与分区类型封装为 `WriterResult` 加入列表。

5. `complete()`：触发 flush，返回并清空累积的 `writerResults`。`close()` 关闭底层 writer。

### kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/NoOpWriter.java（新文件，40 行）

**修改目的**：空操作写入器，用于表不存在且 `ignoreMissingTable` 时的占位。

**工作逻辑**：实现 `RecordWriter` 接口，三个方法均为空实现（`write` 无操作、`complete` 返回 null、`close` 无操作）。当 `IcebergWriterFactory.createWriter` 在表缺失且未启用自动建表但允许忽略时返回该实例，避免抛错中断流水。

### kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/PartitionedAppendWriter.java（新文件，55 行）

**修改目的**：分区表的 fanout 写入器，按分区路由记录。

**工作逻辑**：继承 `PartitionedFanoutWriter<Record>`。构造时接收 PartitionSpec、FileFormat、FileAppenderFactory、OutputFileFactory、FileIO、targetFileSize、Schema，并初始化 `PartitionKey` 与 `InternalRecordWrapper`。`partition(Record)` 用 wrapper 包装记录后调用 `partitionKey.partition(...)` 计算分区键并返回，由父类据此将记录路由到对应分区的写入器。

### kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/WriterResult.java（新文件，60 行）

**修改目的**：封装一次写入完成后的结果，供提交协调使用。

**工作逻辑**：不可变值对象，持有 `TableIdentifier tableIdentifier`、`List<DataFile> dataFiles`、`List<DeleteFile> deleteFiles`、`StructType partitionStruct`，仅提供 getter。由 `IcebergWriter.flush()` 构造，承载写入产出的文件清单与分区结构信息。

### kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/IcebergWriterFactory.java（新文件，115 行）

**修改目的**：按表名创建写入器，处理表加载与自动建表。

**工作逻辑**：

1. `createWriter(String tableName, SinkRecord sample, boolean ignoreMissingTable)`：用 `TableIdentifier.parse(tableName)` 从 catalog 加载表。若抛 `NoSuchTableException`：启用自动建表则调 `autoCreateTable`；否则若 `ignoreMissingTable` 返回 `NoOpWriter`；否则重抛异常。最终用加载到的表构造 `IcebergWriter`。

2. `autoCreateTable(String tableName, SinkRecord sample)`：根据 sample 的 valueSchema 是否为空，分别走 `SchemaUtils.toIcebergType(valueSchema, config)` 或 `SchemaUtils.inferIcebergType(value, config)` 推断 StructType，构建 Iceberg Schema。用 `config.tableConfig(tableName).partitionBy()` 通过 `SchemaUtils.createPartitionSpec` 构造分区 spec，若构造失败则回退为 unpartitioned 并记录错误日志。最后用 `Tasks.range(1).retry(CREATE_TABLE_RETRIES)` 尝试建表，遇到 `AlreadyExistsException` 则改为 loadTable（应对并发建表竞争），共最多 3 次尝试。

### kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/SchemaUpdate.java（新文件，121 行）

**修改目的**：描述待应用的 schema 演更变更（新增列、类型升级、置为可选）。

**工作逻辑**：包含一个 `Consumer` 内部类与三个变更描述类 `AddColumn`、`UpdateType`、`MakeOptional`：

1. `Consumer`：用三个 Map 分别收集 addColumns（按 `parent.name` 去重）、updateTypes、makeOptionals，提供 `addColumn`/`updateType`/`makeOptional` 添加方法与 `empty()` 判空方法。

2. `AddColumn`：持有 parentName（可为 null，表示顶层）、name、type，`key()` 返回 `parentName == null ? name : parentName + "." + name` 用于去重。

3. `UpdateType`：持有 name 与 `PrimitiveType`（仅允许原始类型升级）。

4. `MakeOptional`：持有 name，表示将该列置为可选。

### kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/SchemaUtils.java（新文件，351 行）

**修改目的**：Kafka Connect schema 与 Iceberg type 互转、分区 spec 构造、schema 演进提交。

**工作逻辑**：

1. **类型升级检测**：`needsDataTypeUpdate(Type currentIcebergType, Schema valueSchema)` 仅允许 FLOAT→DOUBLE（当 Connect 为 FLOAT64）与 INTEGER→LONG（当 Connect 为 INT64）两种拓宽升级，其余返回 null。

2. **schema 演进提交**：`applySchemaUpdates(Table, SchemaUpdate.Consumer)` 在 updates 非空时以 `SCHEMA_UPDATE_RETRIES`（2 次，共 3 次尝试）重试调用 `commitSchemaUpdates`。后者先 `table.refresh()` 取最新 schema，再过滤掉已存在的列、类型已匹配的列、已可选的列，最后通过 `UpdateSchema` 执行 addColumn/updateColumn/makeColumnOptional 并 commit。这保证并发演进时的幂等性。

3. **分区 spec 构造**：`createPartitionSpec(Schema, List<String> partitionBy)` 用 `TRANSFORM_REGEX`（`(\\w+)\\((.+)\\)`）解析分区字段，支持 identity（无括号）与 year/month/day/hour/bucket/truncate（带括号）变换，bucket 与 truncate 通过 `transformArgPair` 解析"字段名, 数值"参数。空列表返回 unpartitioned。

4. **Schema 转换**：`toIcebergType(Schema valueSchema, IcebergSinkConfig)` 与 `inferIcebergType(Object value, IcebergSinkConfig)` 委托内部 `SchemaGenerator`。`SchemaGenerator` 维护递增 `fieldId`（从 1 开始），`toIcebergType` 按 Connect Schema.Type 分支映射：BOOLEAN→BooleanType、BYTES（Decimal 逻辑名→DecimalType(38,scale)，否则 BinaryType）、INT8/INT16/INT32（Date/Time 逻辑名特殊处理，否则 IntegerType）、INT64（Timestamp 逻辑名→TimestampType.withZone，否则 LongType）、FLOAT32→FloatType、FLOAT64→DoubleType、ARRAY/ListType、MAP/MapType、STRUCT/StructType，STRING 默认。可选性受 `schemaForceOptional` 配置影响。`inferIcebergType` 从无 schema 的 Java 对象推断类型（String/Boolean/BigDecimal/Integer|Long→LongType/Float|Double→DoubleType/LocalDate/LocalTime/Date|OffsetDateTime/LocalDateTime/List/Map），空集合或空 Map 返回 null。

### kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/Utilities.java（新文件，249 行）

**修改目的**：提供 catalog 加载、Hadoop 配置加载、表写入器构造、记录值提取等工具方法。

**工作逻辑**：

1. **`loadCatalog(IcebergSinkConfig)`**：委托 `CatalogUtil.buildIcebergCatalog`，传入 catalogName、catalogProps 与 `loadHadoopConfig` 结果。

2. **`loadHadoopConfig(IcebergSinkConfig)`**：用反射（DynClasses/DynConstructors/DynMethods）避免硬依赖 Hadoop。优先找 `HdfsConfiguration`，回退 `Configuration`；若类路径无 Hadoop 则返回 null。否则新建实例，若配置了 `hadoopConfDir` 则加载其中的 core-site.xml/hdfs-site.xml/hive-site.xml，再将 `iceberg.hadoop.*` 属性逐条 set。最终返回 Hadoop Configuration 对象。

3. **`extractFromRecordValue(Object recordValue, String fieldName)`**：按点号分隔的字段名从记录值中提取嵌套值，支持 Connect `Struct` 与 `Map` 两种载体，逐层下钻，缺失返回 null。

4. **`createTableWriter(Table, String tableName, IcebergSinkConfig)`**：合并表属性与 `writeProps`，解析文件格式（`DEFAULT_FILE_FORMAT`，默认 parquet）与目标文件大小（`WRITE_TARGET_FILE_SIZE_BYTES`）。确定 identifierFieldIds：若 `idColumns` 配置非空则覆盖表的标识字段集（按列名查 fieldId），否则用表 schema 自身。据此构造 `GenericAppenderFactory`（带或不带标识字段与 equality delete 相关参数）。构造 `OutputFileFactory`（partitionID=1、operationId=UUID）。最后按是否分区选择 `UnpartitionedWriter` 或 `PartitionedAppendWriter`。FIXME 标注 delta writer 待加入。

### kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/IcebergSinkConfigTest.java（新文件，91 行）

**修改目的**：测试配置版本、非法配置校验、默认值与字符串分割工具。

**工作逻辑**：`testGetVersion` 断言 version 非空；`testInvalid` 断言同时指定静态与动态表名抛 `ConfigException`；`testGetDefault` 断言默认提交间隔为 300000；`testStringToList` 覆盖 null/空串/单元素/多元素/带括号 transform 的分割，验证 `COMMA_NO_PARENS_REGEX` 能正确保留 `bucket(id, 4)` 整体。

### kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/IcebergSinkConnectorTest.java（新文件，40 行）

**修改目的**：测试 connector 的 taskConfigs 生成。

**工作逻辑**：`testTaskConfigs` 启动 connector 后请求 3 份 task 配置，断言数量为 3 且每份均含 `INTERNAL_TRANSACTIONAL_SUFFIX_PROP` 键，验证事务性后缀注入正确。

### kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/data/BaseWriterTest.java（新文件，93 行）

**修改目的**：为写入器测试提供公共基础设施。

**工作逻辑**：定义带标识字段的 SCHEMA（id、data、id2，标识字段为 1 与 3）与按 data 分区的 SPEC。`@BeforeEach` 用 Mockito mock 一个 Table，配置 schema/spec（unpartitioned）/io（InMemoryFileIO）/locationProvider/encryption/properties。`writeTest` 方法用 `Utilities.createTableWriter` 创建写入器，断言其类等于期望类，写入给定记录后返回 `WriteResult`。

### kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/data/IcebergWriterFactoryTest.java（新文件，86 行）

**修改目的**：测试自动建表逻辑。

**工作逻辑**：参数化测试（分区/非分区）。mock catalog.loadTable 抛 NoSuchTableException，mock config 与 sample record（value 为 `ImmutableMap.of("id", 123, "data", "foo2")`），调用 `factory.autoCreateTable`。用 ArgumentCaptor 捕获 createTable 的参数，断言 identifier 为 `db.tbl`、schema 的 id 列为 LongType、data 列为 StringType、spec 是否分区与参数一致、props 含 test-prop。验证了从无 schema 的 Map 值推断类型并建表的能力。

### kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/data/PartitionedAppendWriterTest.java（新文件，66 行）

**修改目的**：测试分区写入器按分区产出多个数据文件。

**工作逻辑**：参数化测试（parquet/orc）。mock config 的 writeProps 设定格式，将 table.spec 设为按 data 分区的 SPEC。写入两条 data 不同的记录，断言结果有 2 个数据文件（每分区一个）、格式正确、无 delete 文件。

### kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/data/UnpartitionedWriterTest.java（新文件，63 行）

**修改目的**：测试非分区写入器产出单个数据文件。

**工作逻辑**：参数化测试（parquet/orc）。写入两条记录到 unpartitioned 表，断言结果有 1 个数据文件、格式正确、无 delete 文件，且 writer 类为 `UnpartitionedWriter`。

### kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/data/SchemaUtilsTest.java（新文件，334 行）

**修改目的**：全面测试 schema 演进、分区 spec 构造与类型转换。

**工作逻辑**：

1. `testApplySchemaUpdates`/`testApplyNestedSchemaUpdates`：验证对已存在同类型列的更新被过滤、对新增列与类型升级与置可选被应用，并用 verify 确认无多余调用（嵌套场景用 "st.i" 路径）。
2. `testApplySchemaUpdatesNoUpdates`：验证 null 或空 Consumer 时不触发 refresh/updateSchema。
3. `testNeedsDataTypeUpdate`：验证 FLOAT→DOUBLE、INT→LONG 合法，其余返回 null。
4. `testCreatePartitionSpecUnpartitioned`/`testCreatePartitionSpec`：验证空列表得到 unpartitioned，含 year/month/day/hour/bucket/truncate/identity 的列表得到含对应 transform 的分区 spec。
5. `testToIcebergType`：参数化（forceOptional true/false）覆盖全部 Connect 类型到 Iceberg 类型的映射，含 Decimal scale、Timestamp 时区、List/Map 可选性、Struct 字段可选性。
6. `testInferIcebergType`/`testInferIcebergTypeEmpty`：覆盖从 Java 对象推断类型，以及 null、空 List、首元素为 null 的 List、空 Map、值为 null 的 Map 等边界返回 null。

### kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/data/UtilitiesTest.java（新文件，183 行）

**修改目的**：测试 catalog 加载（含 Hadoop 配置）与记录值提取。

**工作逻辑**：

1. 自定义 `TestCatalog extends InMemoryCatalog implements Configurable<Configuration>` 以捕获注入的 Hadoop Configuration。
2. `testLoadCatalogNoHadoopDir`：仅通过 `iceberg.hadoop.*` 设置属性，验证 conf 含该属性，且 classpath 上的 core-site.xml（foo=bar）被加载。
3. `testLoadCatalogWithHadoopDir`（参数化 core/hdfs/hive-site.xml）：在临时目录写一个含 file-prop 的 site 文件，配置 hadoopConfDir，验证文件属性与 sink 属性均被加载，且 core-site.xml 仍被加载。
4. `testExtractFromRecordValueStruct*`/`testExtractFromRecordValueMap*`：覆盖从 Struct/Map 提取顶层、嵌套（"data.id.key"）、空字段名/不存在字段名（返回 null）的场景。

### kafka-connect/kafka-connect/src/test/resources/core-site.xml（新文件，25 行）

**修改目的**：为 UtilitiesTest 提供一个会被 classpath 自动加载的 Hadoop 配置文件。

**工作逻辑**：标准 Hadoop site XML，定义一个 `foo=bar` 属性，供 `testLoadCatalogNoHadoopDir` 与 `testLoadCatalogWithHadoopDir` 断言 core-site.xml 被 `loadHadoopConfig` 加载。

## 小结

本提交是 Iceberg Kafka Connect sink connector 实现的核心奠基之作，新增 `iceberg-kafka-connect` 子模块共约 2774 行代码与测试。它构建了从配置（`IcebergSinkConfig`/`TableSinkConfig`）、connector 入口（`IcebergSinkConnector`）、写入器体系（`RecordWriter`/`IcebergWriter`/`NoOpWriter`/`PartitionedAppendWriter`/`IcebergWriterFactory`/`WriterResult`）、schema 转换与演进（`SchemaUtils`/`SchemaUpdate`）到基础设施（`Utilities`）的完整框架，并对既有事件类补齐空值校验、公开 `AvroUtil`。配套测试覆盖配置解析、自动建表、分区/非分区多格式写入、schema 转换与演进、catalog 与 Hadoop 配置加载、记录值提取等关键路径。需要指出的是，本提交为增量交付：`taskClass()` 返回 null、`convertToRow()` 返回 null、CDC/upsert/delta writer 均标注 FIXME 待后续提交补齐，端到端记录转换与提交协调链路尚未完全接通。整体属于新功能引入，改动量大但结构清晰、测试充分。
