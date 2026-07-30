# 提交分析：Kafka Connect: Initial project setup and event data structures (#8701)

## 提交信息

- 哈希：d1a3c104501d2f4bd1fb46cdefb0073d8fa3a227
- 短哈希：d1a3c1045
- 日期：2024-01-10 06:07:38 -0800
- 作者：Bryan Keller (bryanck@gmail.com)
- 说明：Kafka Connect: Initial project setup and event data structures (#8701)

## 总体目的

本提交是 Iceberg 引入 Kafka Connect 集成模块的"奠基"性改动。它并不实现 Connector 本身（Sink/Source connector 的 connector/worker 逻辑留给后续提交），而是先建立 Gradle 子模块骨架，并定义 Connector 在分布式协调过程中所需的全部"控制事件"（control events）数据结构。这是 Iceberg 与 Kafka Connect 深度集成的第一步——后续 coordinator/worker 之间的提交协调、数据写入回报、提交完成通知等都将以本提交定义的事件类型作为通信契约。

设计上采用"控制主题"（control topic）模式：在多个 Kafka Connect worker 之间通过一个共享的 Kafka 主题传递结构化事件，由一个 coordinator 角色发起提交周期，workers 把已写入的 Iceberg 数据文件信息回报给 coordinator，coordinator 汇总后向 Iceberg 表发起提交，再把结果广播给所有 workers 与下游消费者。这种模式让 Iceberg 的"原子提交"语义能够跨越多个独立 Kafka Connect 任务（每个任务可能写入若干数据文件）被正确地聚合，是 exactly-once sink 的关键。

事件结构采用 Avro 序列化，并复用 Iceberg 自身的 schema/类型系统（`Types.StructType`、`NestedField`、字段 ID 等）来描述事件 payload。这样做有两个好处：1) 字段 ID（而非字段名）作为稳定标识，便于后续事件结构演进时的前向/后向兼容；2) 直接复用 Iceberg `AvroSchemaUtil` / `AvroEncoderUtil` 的 schema 转换与编码能力，避免引入第二套序列化栈。本提交还为此对 core 模块的 `TypeToSchema` 做了重构——把它从"基于 `Map<StructType, String>` 的命名查找"扩展为"基于 `BiFunction<Integer, StructType, String>` 的命名函数"，使不同 field-id 指向的 struct 可以映射到不同的 Java 实现类（例如同一事件里 data_files 用 `GenericDataFile`、delete_files 用 `GenericDeleteFile`、payload 自身用具体 Payload 子类）。

事件模型上，定义了一个统一的 `Event` 信封（包含 id / type / timestamp / group_id / payload），payload 是一个多态接口 `Payload`，对应 5 种具体子类型，覆盖一个完整的提交周期：`StartCommit`（coordinator 通知 workers 开始回报）→ `DataWritten`（worker 报告本批次写入的数据/删除文件）→ `DataComplete`（worker 报告本周期已无更多数据）→ `CommitToTable`（coordinator 完成向 Iceberg 表的提交并广播结果）→ `CommitComplete`（最终通知，含 valid-through 时间戳供下游消费）。这一闭环在后续 Connector 实现中将驱动整个 sink 的提交编排。

## 如何达成设计目的

模块结构上，在仓库根新建 `kafka-connect/` 目录，并在 `settings.gradle` 中注册 `iceberg-kafka-connect` 父项目与 `iceberg-kafka-connect-events` 子项目（位于 `kafka-connect/kafka-connect-events/`）。`build.gradle` 仅声明必要依赖（`iceberg-api`、`iceberg-core`、`iceberg-common`、bundled guava、Avro），并启用 JUnit Platform（JUnit5）。` .github/labeler.yml` 增加 `KAFKACONNECT` 标签规则，使 PR 自动打标。

事件实现上，每个事件类都实现 Avro 的 `IndexedRecord` 接口，并采用统一的"字段 ID 常量 + 静态 ICEBERG_SCHEMA + AvroUtil.convert 生成 AVRO_SCHEMA"模式：1) 用整型常量（如 `10_000`、`10_100`...）作为字段 ID，跨类不冲突；2) 用 Iceberg `StructType.of(NestedField...)` 描述 schema；3) 通过 `AvroUtil.convert(icebergSchema, JavaClass.class)` 转成 Avro Schema，转换时通过 `FIELD_ID_TO_CLASS` 映射告诉转换器某个 field-id 对应的 struct 应绑定到哪个 Java 类；4) `get(int)`/`put(int, Object)` 通过 `AvroUtil.positionToId(position, avroSchema)` 把 Avro 字段位置翻译回字段 ID 再 switch，保证字段顺序变化时不会错位。每个事件类都提供两个构造器：一个公开的业务构造器（赋值并绑定静态 AVRO_SCHEMA），一个 `Schema` 参数构造器（供 Avro 反射在反序列化时实例化对象）。

为了支持"同一 schema 中不同 struct 字段绑定到不同 Java 类"这种多态需求，提交重构了 `core/avro/TypeToSchema`：把它从具体类改成抽象类，把"通过 StructType 查名"的能力抽象成 `BiFunction<Integer, StructType, String>`，并新增两个具体子类 `WithTypeToName`（保留原有 Map 行为，向后兼容）与 `WithNamesFunction`（按 field-id 查名，支持事件序列化）。`AvroSchemaUtil` 新增 `convert(Type, BiFunction)` 重载，`convertTypes` 等历史方法切换到 `WithTypeToName`。配套在 `DecoderResolver` 新增 `clearCache()` 方法，在事件反序列化后清理 ThreadLocal 缓存，避免长生命周期线程上缓存累积导致内存泄漏。

## 修改详情

### .github/labeler.yml
**修改目的**：为新的 kafka-connect 模块配置 GitHub PR 自动打标规则。
**工作逻辑**：新增 `KAFKACONNECT` 标签，匹配 `kafka-connect/**/*` 路径下的任意文件变更，使相关 PR 在 labeler action 触发时自动打上模块标签，便于维护者分流。

### settings.gradle
**修改目的**：把新模块纳入 Gradle 构建树。
**工作逻辑**：`include 'kafka-connect'` 并重命名为 `iceberg-kafka-connect`；再 `include ":iceberg-kafka-connect:kafka-connect-events"` 并显式指定 `projectDir` 与 `name`（`iceberg-kafka-connect-events`）。两级结构（父 `iceberg-kafka-connect` + 子 `iceberg-kafka-connect-events`）为将来添加更多子模块（如 connector 本体）预留空间。

### kafka-connect/build.gradle
**修改目的**：声明 events 子模块的依赖与测试配置。
**工作逻辑**：`api project(':iceberg-api')` 让事件类对外暴露的 Iceberg 类型可见；`implementation` 引入 `iceberg-core`（用于 `DataFile`、`PartitionData`、`AvroEncoderUtil` 等）、`iceberg-common`、bundled guava 与 `avro.avro`。`test { useJUnitPlatform() }` 启用 JUnit5。

### core/src/main/java/org/apache/iceberg/avro/AvroSchemaUtil.java
**修改目的**：扩展 schema 转换入口，支持按 field-id 命名的函数式查询。
**工作逻辑**：新增 `convert(Type type, BiFunction<Integer, Types.StructType, String> namesFunction)` 重载，内部用 `new TypeToSchema.WithNamesFunction(namesFunction)`；把原有 `convert(schema, names)`、`convert(type, names)`、`convertTypes` 改为使用 `WithTypeToName` 子类。这样既保留了基于 `Map<StructType, String>` 的旧 API，又新增了基于函数的新 API，事件模块通过新 API 实现"field-id → Java 类名"映射。

### core/src/main/java/org/apache/iceberg/avro/TypeToSchema.java
**修改目的**：把 `TypeToSchema` 重构为抽象基类，支持两种命名策略。
**工作逻辑**：
- 改为 `abstract class`，字段从 `Map<Types.StructType, String> names` 改为 `BiFunction<Integer, Types.StructType, String> namesFunction`，移除 `results` map 与 `getConversionMap()`。
- 新增抽象方法 `lookupSchema(Type, String recordName)` 与 `cacheSchema(Type, String recordName, Schema)`，把"结果缓存"策略下放到子类。
- `struct` 方法先从 `namesFunction.apply(fieldId, struct)` 取记录名（取不到则用 `"r" + fieldId`），再调用 `lookupSchema` 检查是否已生成，最后 `cacheSchema` 缓存。`list`/`map`/primitive 方法同样切换到 `lookupSchema`/`cacheSchema`。
- 新增 `WithTypeToName` 子类：内部维护 `Map<Type, Schema>`，`lookupSchema` 按 type 查、`cacheSchema` 按 type 存——等价于原有行为，并提供 `getConversionMap()`。
- 新增 `WithNamesFunction` 子类：内部维护 `Map<String, Schema>`（按 recordName 缓存），`cacheSchema` 仅在 recordName 非空时缓存，`lookupSchema` 按 recordName 查。这种策略允许同一个 StructType 在不同 field-id 下绑到不同的 Java 类（因为 recordName 来自 namesFunction，可包含 Java 类名）。

### core/src/main/java/org/apache/iceberg/data/avro/DecoderResolver.java
**修改目的**：新增 `clearCache()` 方法用于清理 ThreadLocal 解码器缓存。
**工作逻辑**：`DecoderResolver` 用 `ThreadLocal` 缓存 `ResolvingDecoder` 以加速重复反序列化。但 Kafka Connect worker 是长生命周期线程，反复解码不同 schema 的事件会导致缓存无限增长。新增公开静态方法 `clearCache()` 直接 `DECODER_CACHES.get().clear()`，供事件反序列化路径在每次 `decode` 后调用，避免内存泄漏。

### kafka-connect/kafka-connect-events/src/main/java/org/apache/iceberg/connect/events/Event.java
**修改目的**：定义统一的事件信封类，承载所有控制事件的公共元数据与多态 payload。
**工作逻辑**：
- 实现 `IndexedRecord`。字段：`id (UUID)`、`type (PayloadType)`、`timestamp (OffsetDateTime)`、`groupId (String)`、`payload (Payload)`。
- 字段 ID 常量 `ID=10_500`、`TYPE=10_501`、`TIMESTAMP=10_502`、`GROUP_ID=10_503`、`PAYLOAD=10_504`。
- 业务构造器：`id` 用 `UUID.randomUUID()`，`timestamp` 用 `OffsetDateTime.now(UTC).truncatedTo(MICROS)`（与 Iceberg timestamp 精度对齐），`type` 取自 `payload.type()`。然后构造 `StructType`（payload 字段的 schema 由 `payload.writeSchema()` 提供），再用 `AvroUtil.convert(icebergSchema, getClass(), typeMap)` 生成 Avro Schema——其中 `typeMap` 把基础 `FIELD_ID_TO_CLASS` 复制一份并加入 `PAYLOAD → payload.getClass().getName()`，让 Avro 反射知道 payload 字段应反序列化成哪个具体 Payload 子类。
- `Schema` 构造器：仅赋值 `avroSchema`，供 Avro 反射调用。
- `put`/`get` 用 `AvroUtil.positionToId` 翻译位置；`put` 中对未知 ordinal 直接忽略（前向兼容：未来新加字段不会让旧 reader 崩溃），`get` 中对未知 ordinal 抛 `UnsupportedOperationException`。

### kafka-connect/kafka-connect-events/src/main/java/org/apache/iceberg/connect/events/Payload.java
**修改目的**：定义所有事件 payload 的公共接口。
**工作逻辑**：继承 Avro `IndexedRecord`，声明两个方法：`PayloadType type()`（返回枚举标识）与 `Types.StructType writeSchema()`（返回该 payload 的 Iceberg struct schema，供 `Event` 构造时拼装）。子类负责实现这两个方法以及 `getSchema`/`get`/`put`。

### kafka-connect/kafka-connect-events/src/main/java/org/apache/iceberg/connect/events/PayloadType.java
**修改目的**：定义事件类型枚举，作为 Event 的 `type` 字段与 Avro 序列化时的整数标识。
**工作逻辑**：5 个枚举常量 `START_COMMIT(0)`、`DATA_WRITTEN(1)`、`DATA_COMPLETE(2)`、`COMMIT_TO_TABLE(3)`、`COMMIT_COMPLETE(4)`，每个带整型 id。`id()` 用于在 Event 的 `put`/`get` 中作为 Avro int 写入读出，比直接存枚举名更紧凑且兼容性更好。Javadoc 标注每个枚举值对应的 payload 类。

### kafka-connect/kafka-connect-events/src/main/java/org/apache/iceberg/connect/events/StartCommit.java
**修改目的**：定义"coordinator 发起提交周期"事件的 payload。
**工作逻辑**：字段仅 `commitId (UUID)`，字段 ID `COMMIT_ID=10_200`。schema 是单字段 struct。`type()` 返回 `START_COMMIT`。该事件由 coordinator 广播，workers 收到后开始回报自己已写入的数据文件。

### kafka-connect/kafka-connect-events/src/main/java/org/apache/iceberg/connect/events/DataWritten.java
**修改目的**：定义"worker 报告已写入数据"事件的 payload，是事件体系中最复杂的一类。
**工作逻辑**：
- 字段：`partitionType (StructType)`（用于构造 DataFile/DeleteFile 的 schema，不序列化）、`commitId (UUID)`、`tableReference (TableReference)`、`dataFiles (List<DataFile>)`、`deleteFiles (List<DeleteFile>)`、`icebergSchema (StructType, lazy)`。
- 字段 ID：`COMMIT_ID=10_300`、`TABLE_REFERENCE=10_301`、`DATA_FILES=10_302`、`DATA_FILES_ELEMENT=10_303`、`DELETE_FILES=10_304`、`DELETE_FILES_ELEMENT=10_304`（注意：DELETE_FILES 与 DELETE_FILES_ELEMENT 共用 id 10_304，看起来是 bug 但在本提交范围内不影响功能，因为 schema 中字段名不同；后续提交可能修正）。
- `writeSchema()` 懒构造：用 `DataFile.getType(partitionType)` 拿到 datafile 的 struct 类型，再组装包含 commit_id / table_reference / data_files / delete_files 的 struct。data_files 与 delete_files 都是 `ListType.ofRequired(ELEMENT_ID, dataFileStruct)`——二者共享同一种元素 struct，因为 Iceberg 的 DataFile 与 DeleteFile 在序列化层用同一 schema（通过 `GenericDataFile` / `GenericDeleteFile` 区分）。
- 业务构造器调用 `AvroUtil.convert(writeSchema(), getClass())`，`AvroUtil` 通过 `FIELD_ID_TO_CLASS` 把 `TABLE_REFERENCE` 绑到 `TableReference`、`DATA_FILES_ELEMENT` 绑到 `GenericDataFile`、`DELETE_FILES_ELEMENT` 绑到 `GenericDeleteFile`。
- `Schema` 构造器的 Javadoc 明确说明：反射构造的实例没有 `partitionType`，因此不可重新序列化（仅用于读）。

### kafka-connect/kafka-connect-events/src/main/java/org/apache/iceberg/connect/events/DataComplete.java
**修改目的**：定义"worker 报告本周期数据已发完"事件的 payload。
**工作逻辑**：字段 `commitId (UUID)` 与 `assignments (List<TopicPartitionOffset>)`。字段 ID `COMMIT_ID=10_100`、`ASSIGNMENTS=10_101`、`ASSIGNMENTS_ELEMENT=10_102`。`assignments` 携带该 worker 处理的 topic-partition-offset 信息，让 coordinator 知道哪些分区已被消费到哪个 offset，从而在提交完成后正确推进 Kafka 消费位 offset（用于 exactly-once 的 sink 提交协调）。

### kafka-connect/kafka-connect-events/src/main/java/org/apache/iceberg/connect/events/CommitToTable.java
**修改目的**：定义"coordinator 已完成向 Iceberg 表的提交"事件的 payload。
**工作逻辑**：字段 `commitId`、`tableReference`、`snapshotId (Long)`、`validThroughTs (OffsetDateTime)`。字段 ID `COMMIT_ID=10_400` 等。`snapshotId` 是 Iceberg 提交产生的新快照 id；`validThroughTs` 是"该提交涵盖的最大记录时间戳"（即所有 worker 中记录时间戳最小值中的最大值），供下游消费者判断数据完备性。该事件由 coordinator 广播，workers 与下游消费者据此感知提交完成。

### kafka-connect/kafka-connect-events/src/main/java/org/apache/iceberg/connect/events/CommitComplete.java
**修改目的**：定义"提交周期完全结束"的最终通知 payload。
**工作逻辑**：字段 `commitId` 与 `validThroughTs`。字段 ID `COMMIT_ID=10_000`、`VALID_THROUGH_TS=10_001`。Javadoc 说明该事件不被 sink 消费，是信息性的，用于触发下游流程（如通知下游 pipeline "截至 validThroughTs 的数据已就绪"）。

### kafka-connect/kafka-connect-events/src/main/java/org/apache/iceberg/connect/events/TableReference.java
**修改目的**：定义跨事件复用的"表标识"结构。
**工作逻辑**：字段 `catalog (String)`、`namespace (List<String>)`、`name (String)`。字段 ID `CATALOG=10_600`、`NAMESPACE=10_601`、`NAME=10_603`（注意 10_602 跳过，预留给 namespace 元素 id）。提供 `of(String catalog, TableIdentifier)` 工厂方法（从 Iceberg `TableIdentifier` 构造）与 `identifier()` 方法（转回 `TableIdentifier`）。`put` 中 namespace 字段从 `List<Utf8>` 转 `List<String>`（Avro 反射默认返回 Utf8）。`ICEBERG_SCHEMA` 是 public static，供 DataWritten / CommitToTable 嵌套引用。

### kafka-connect/kafka-connect-events/src/main/java/org/apache/iceberg/connect/events/TopicPartitionOffset.java
**修改目的**：定义 Kafka topic-partition-offset 三元组（带时间戳）结构，供 DataComplete 携带消费进度。
**工作逻辑**：字段 `topic`、`partition (Integer)`、`offset (Long, optional)`、`timestamp (OffsetDateTime, optional)`。字段 ID `TOPIC=10_700` 等。`offset` 与 `timestamp` 是 optional，对应"分区已分配但尚未消费到具体 offset"的场景。`ICEBERG_SCHEMA` 是 public static 供 DataComplete 嵌套。

### kafka-connect/kafka-connect-events/src/main/java/org/apache/iceberg/connect/events/AvroUtil.java
**修改目的**：提供事件序列化的统一工具与 field-id→Java 类映射。
**工作逻辑**：
- `FIELD_ID_TO_CLASS` 静态 map：把 5 个字段 ID（`DataComplete.ASSIGNMENTS_ELEMENT`、`DataFile.PARTITION_ID`、`DataWritten.TABLE_REFERENCE`、`DataWritten.DATA_FILES_ELEMENT`、`DataWritten.DELETE_FILES_ELEMENT`、`CommitToTable.TABLE_REFERENCE`）映射到对应 Java 类名。其中 `GenericDataFile` / `GenericDeleteFile` 用字符串写全名（避免直接引用 core 模块的 Generic 类导致循环依赖或类加载问题）。
- `encode(Event)`：委托 `AvroEncoderUtil.encode(event, event.getSchema())`，把 IOException 包装成 `UncheckedIOException`。
- `decode(byte[])`：`AvroEncoderUtil.decode(bytes)`，并立即 `DecoderResolver.clearCache()` 防止内存泄漏。
- `convert(StructType, Class)` 与 `convert(StructType, Class, Map<Integer, String>)`：调用 `AvroSchemaUtil.convert(icebergSchema, (fieldId, struct) -> struct.equals(icebergSchema) ? javaClass.getName() : typeMap.get(fieldId))`——根 struct 用业务类名，子 struct 按 field-id 查 typeMap。
- `positionToId(int, Schema)`：从 Avro schema 取出第 position 个字段的 `FIELD_ID_PROP` 属性，把 Avro 位置翻译回 Iceberg 字段 id，是事件类 `get`/`put` 的关键桥梁。

### kafka-connect/kafka-connect-events/src/test/java/org/apache/iceberg/connect/events/EventTestUtil.java
**修改目的**：提供测试夹具，构造 DataFile/DeleteFile/PartitionSpec 等复杂对象。
**工作逻辑**：定义静态 `SPEC`（一个单字段 partition spec）、`createDataFile()`、`createDeleteFile()`、`now()`（返回固定 OffsetDateTime）等工厂方法，供序列化测试使用。

### kafka-connect/kafka-connect-events/src/test/java/org/apache/iceberg/connect/events/EventSerializationTest.java
**修改目的**：覆盖 5 种事件类型的 round-trip 序列化测试。
**工作逻辑**：每种 payload 类型一个 `@Test`：构造 Event → `AvroUtil.encode` → `AvroUtil.decode` → 用 AssertJ `usingRecursiveComparison().ignoringFieldsMatchingRegexes(...)` 比对原始与反序列化对象。忽略的字段包括 `avroSchema`、`icebergSchema`、`schema`、`payload.partitionType`、`fromProjectionPos`（这些是运行期/非序列化字段或不可比较对象）。测试覆盖了 StartCommit、DataWritten、DataComplete、CommitToTable、CommitComplete 五种事件，验证事件模型的端到端正确性。

## 小结

本提交为 Iceberg Kafka Connect 集成奠定了基础：建立 `iceberg-kafka-connect-events` 子模块，定义了完整的 5 种控制事件 + 信封 + 复用结构（TableReference、TopicPartitionOffset）的 Avro 数据模型，覆盖一个完整提交周期的协调流程（StartCommit → DataWritten → DataComplete → CommitToTable → CommitComplete）。为支持事件 schema 中多态 struct 字段绑定到不同 Java 类，重构了 core 模块的 `TypeToSchema` 为抽象类 + 两个子类，并新增 `AvroSchemaUtil.convert(Type, BiFunction)` 重载。配套在 `DecoderResolver` 新增 `clearCache()` 防止 worker 长线程上的缓存泄漏。事件模型采用字段 ID 驱动（而非字段名）保证演进兼容性，并通过 `put` 中"未知 ordinal 忽略"实现前向兼容，为后续 Connector 实现与事件结构演进留出空间。
