# 提交 1786：Kafka Connect: Add SMTs for Debezium and AWS DMS (#11936)

## 提交信息

- **序号**：1786 / 4088
- **哈希**：7d9e96f9f6a02c664c1f011a381a89f8c69a4924
- **短哈希**：7d9e96f9f
- **日期**：2025-02-25 09:19:08 -0800
- **作者**：ismail simsek
- **提交说明**：Kafka Connect: Add SMTs for Debezium and AWS DMS (#11936)
- **PR/Issue**：#11936

## 总体目的

这个提交为 Iceberg Kafka Connect Sink Connector 新增了一组 SMT（Single Message Transform，单消息变换）组件，用于将不同数据源的 CDC（Change Data Capture，变更数据捕获）消息格式转换为 Iceberg Sink Connector 可消费的统一格式。

Kafka Connect 的 SMT 机制允许在消息到达 Sink Connector 之前对消息进行变换处理。在实际的 CDC 场景中，不同的数据源（如 Debezium、AWS DMS、MongoDB Debezium）产生的消息格式各不相同，Iceberg Sink Connector 无法直接处理这些异构格式。本提交提供了以下 SMT 来解决这一格式适配问题：

1. **DebeziumTransform**：将 Debezium 格式的 CDC 消息（包含 before/after/op/ts_ms 等字段）转换为统一 CDC 格式，提升 before/after 字段到顶层并添加 `_cdc` 元数据。
2. **DmsTransform**：将 AWS DMS 格式的消息转换为统一 CDC 格式。
3. **MongoDebeziumTransform**：将 MongoDB Debezium 消息中的 BSON 字符串转换为有类型的 Struct，作为 DebeziumTransform 的前置处理。
4. **KafkaMetadataTransform**：将 Kafka 元数据（topic、partition、offset、timestamp）注入到记录中。
5. **JsonToMapTransform**：将 JSON 字符串解析为 Map 结构，避免动态键导致 Iceberg 表 schema 爆炸。
6. **CopyValue**：将一个字段的值复制到新字段。

此外还包含 MongoDB 数据转换工具类（从 Debezium 项目引入）和 CDC 常量定义。

## 如何达成设计目的

提交通过以下层次修改来达成目标：

1. **新建 Gradle 模块**：创建 `kafka-connect-transforms` 子模块，包含所有 SMT 实现和测试，作为 `kafka-connect-runtime` 的依赖。

2. **实现 SMT 类**：每个 SMT 实现 Kafka Connect 的 `Transformation<R>` 接口，在 `apply` 方法中对消息进行变换。所有 CDC 变换共享 `CdcConstants` 中定义的元数据字段名（如 `_cdc`、`op`、`ts`、`source`、`target`、`key`）。

3. **添加依赖**：在版本目录中新增 `bson`（MongoDB BSON 库）和 `kafka-connect-transforms`（Kafka Connect 内置变换库）依赖。

4. **编写文档**：在 `docs/docs/kafka-connect.md` 中新增 SMT 章节，描述每个 SMT 的用途、配置和使用示例。

5. **编写测试**：为每个 SMT 编写全面的单元测试，包含测试资源文件（JSON、MongoDB 事件等）。

## 修改详情

### `settings.gradle`（修改, +4/-0 lines）

**修改目的**：注册新的 `kafka-connect-transforms` Gradle 子模块。

**工作逻辑**：在 Kafka Connect 模块配置块中新增 `include ":iceberg-kafka-connect:kafka-connect-transforms"` 及对应的项目目录和名称配置。

### `kafka-connect/build.gradle`（修改, +17/-0 lines）

**修改目的**：配置新模块的依赖关系。

**工作逻辑**：
- 在 `iceberg-kafka-connect-runtime` 的依赖中添加 `implementation project(':iceberg-kafka-connect:iceberg-kafka-connect-transforms')`。
- 新增 `iceberg-kafka-connect-transforms` 项目配置，依赖 bundled-guava、bson、slf4j-api，以及 compileOnly 的 kafka-clients、kafka-connect-api、kafka-connect-json、kafka-connect-transforms。

### `gradle/libs.versions.toml`（修改, +3/-0 lines）

**修改目的**：添加新依赖版本声明。

**工作逻辑**：新增 `bson-ver = "4.11.0"` 版本定义，以及 `bson` 和 `kafka-connect-transforms` 库声明。

### `kafka-connect/kafka-connect-transforms/src/main/java/org/apache/iceberg/connect/transforms/CdcConstants.java`（新增, +34 lines）

**修改目的**：定义 CDC 元数据的常量字段名。

**工作逻辑**：定义操作类型常量（OP_INSERT="I"、OP_UPDATE="U"、OP_DELETE="D"）和字段名常量（COL_CDC="_cdc"、COL_OP="op"、COL_TS="ts"、COL_OFFSET="offset"、COL_SOURCE="source"、COL_TARGET="target"、COL_KEY="key"）。

### `kafka-connect/kafka-connect-transforms/src/main/java/org/apache/iceberg/connect/transforms/DebeziumTransform.java`（新增, +251 lines）

**修改目的**：将 Debezium CDC 消息转换为统一 CDC 格式。

**工作逻辑**：
- 实现 `Transformation<R>` 接口，支持有 schema 和无 schema 两种模式。
- `applyWithSchema`：从 Debezium 消息中提取 `op` 字段映射为 I/U/D 操作码；对删除操作取 `before` 字段，其他取 `after` 字段作为 payload；构造 `_cdc` 元数据结构（op、ts、offset、source、target、key）；将 payload 字段提升到顶层并附加 `_cdc` 字段。
- 支持配置 `cdc.target.pattern`（如 `{db}.{table}`）设置目标表名。
- `applySchemaless`：对无 schema 的 Map 数据执行相同逻辑。

### `kafka-connect/kafka-connect-transforms/src/main/java/org/apache/iceberg/connect/transforms/DmsTransform.java`（新增, +105 lines）

**修改目的**：将 AWS DMS 消息转换为统一 CDC 格式。

**工作逻辑**：
- 仅支持无 schema 模式（DMS 记录不支持 schema）。
- 从 `data` 字段提升数据到顶层，从 `metadata` 字段提取操作类型（update→U、delete→D、其他→I）、时间戳和源信息。
- 构造 `_cdc` 元数据并附加到记录。

### `kafka-connect/kafka-connect-transforms/src/main/java/org/apache/iceberg/connect/transforms/MongoDebeziumTransform.java`（新增, +320 lines）

**修改目的**：将 MongoDB Debezium 消息中的 BSON 字符串转换为有类型的 Struct。

**工作逻辑**：
- Debezium Mongo Connector 将 before/after 字段生成为 BSON 字符串，本 SMT 将其解析为 Connect Struct。
- 使用 `BsonDocument.parse()` 解析 BSON，通过 `MongoDataConverter` 将 BSON 节点转换为 Connect Schema 和 Struct。
- 支持配置 `array_handling_mode`（array 或 document）控制数组处理方式。

### `kafka-connect/kafka-connect-transforms/src/main/java/org/apache/iceberg/connect/transforms/KafkaMetadataTransform.java`（新增, +297 lines）

**修改目的**：将 Kafka 元数据注入到记录中。

**工作逻辑**：
- 将 Kafka 消息的 topic、partition、offset、timestamp 注入到记录的 Schema 和 value 中。
- 支持配置 `field_name`（前缀，默认 `_kafka_metadata`）、`nested`（是否嵌套为 Struct）、`external_field`（附加常量键值对）。

### `kafka-connect/kafka-connect-transforms/src/main/java/org/apache/iceberg/connect/transforms/JsonToMapTransform.java`（新增, +157 lines）

**修改目的**：将 JSON 字符串解析为 Map 结构，避免 schema 爆炸。

**工作逻辑**：
- 使用 Jackson ObjectMapper 解析 JSON 字符串。
- 支持配置 `json.root`：为 true 时将整个 JSON 转为 `Map<String,String>` 放入 `payload` 字段；为 false 时推断基本类型和数组的 schema，嵌套对象转为 `Map<String,String>`。
- 用于处理键动态变化的 JSON 数据，避免 Iceberg 表因 schema 演化产生过多列。

### `kafka-connect/kafka-connect-transforms/src/main/java/org/apache/iceberg/connect/transforms/JsonToMapUtils.java`（新增, +314 lines）

**修改目的**：JSON 到 Map 转换的工具类。

**工作逻辑**：提供 JSON 节点类型推断、Schema 构建和值转换的辅助方法，支持基本类型、数组和嵌套对象的处理。

### `kafka-connect/kafka-connect-transforms/src/main/java/org/apache/iceberg/connect/transforms/CopyValue.java`（新增, +131 lines）

**修改目的**：将一个字段的值复制到新字段。

**工作逻辑**：支持配置 `source.field` 和 `target.field`，在有 schema 和无 schema 两种模式下复制字段值，使用 LRU 缓存优化 Schema 转换。

### `kafka-connect/kafka-connect-transforms/src/main/java/org/apache/iceberg/connect/transforms/JsonToMapException.java`（新增, +29 lines）

**修改目的**：JSON 转换异常类。

### `kafka-connect/kafka-connect-transforms/src/main/java/org/debezium/connector/mongodb/transforms/MongoDataConverter.java`（新增, +515 lines）

**修改目的**：从 Debezium 项目引入的 BSON 到 Connect Struct 转换器。

**工作逻辑**：将 BSON 文档递归转换为 Connect Schema 和 Struct，支持各种 BSON 类型（字符串、整数、浮点、布尔、日期、二进制、文档、数组等）。

### `kafka-connect/kafka-connect-transforms/src/main/java/org/debezium/connector/mongodb/transforms/ArrayEncoding.java`（新增, +67 lines）

**修改目的**：从 Debezium 引入的数组编码枚举。

**工作逻辑**：定义 `array`（数组类型）和 `document`（Struct of Structs，_0/_1/_2...）两种数组编码方式。

### `docs/docs/kafka-connect.md`（修改, +167/-0 lines）

**修改目的**：添加 SMT 使用文档。

**工作逻辑**：新增"SMTs for the Apache Iceberg Sink Connector"章节，描述每个 SMT 的用途、配置参数和使用示例，所有 SMT 标注为实验性（Experimental）。

### 测试文件（新增, 约 2000+ lines）

包含 `CopyValueTest`、`DebeziumTransformTest`、`DmsTransformTest`、`JsonToMapTransformTest`、`JsonToMapUtilsTest`、`KafkaMetadataTransformTest`、`MongoDebeziumTransformTest`、`MongoArrayConverterTest`、`MongoDataConverterTest` 等测试类，以及 JSON 测试资源文件。

### 其他文件

- `.github/workflows/kafka-connect-ci.yml`：更新 CI 配置。
- `kafka-connect-runtime/hive/LICENSE`、`NOTICE` 和 `main/LICENSE`、`NOTICE`：更新许可证文件（因新增依赖）。

## 小结

- **成效**：成功为 Iceberg Kafka Connect Sink Connector 新增了一组 SMT 组件，支持 Debezium（含 MongoDB）、AWS DMS 等 CDC 数据源的消息格式转换，以及 Kafka 元数据注入、JSON 到 Map 转换和字段复制等辅助变换。这使得用户可以方便地将各种 CDC 数据源的数据通过 Kafka Connect 写入 Iceberg 表，无需编写自定义代码。
- **影响范围**：新增 `kafka-connect-transforms` 模块，涉及 37 个文件，新增约 5000 行代码。影响 Kafka Connect 集成功能，不影响 Iceberg 核心模块。所有 SMT 标注为实验性功能。
- **回迁到 1.4.x 的注意事项**：可选回迁。此提交是 Kafka Connect 模块的新增功能，与核心模块无耦合。回迁前需确认 1.4.x 分支是否已有 `kafka-connect` 模块结构。如果 1.4.x 已有 Kafka Connect 模块，可将 transforms 子模块整体添加。需注意 bson 依赖版本和 Kafka Connect API 版本的兼容性。由于是纯新增功能（不改已有代码），回迁风险较低。
