# 提交 3213：Core, Data: File Format API interfaces (#12774)

## 提交信息

- **序号**：3213 / 4088
- **哈希**：a8ece055ba93adc0c046db29b6b7b5edaf35d4da
- **短哈希**：a8ece055b
- **日期**：2026-02-06
- **作者**：pvary
- **提交说明**：Core, Data: File Format API interfaces (#12774)
- **PR/Issue**：#12774

## 总体目的

Iceberg 当前的文件读写路径是按"格式 × 引擎"两两并行展开的：Parquet/Avro/ORC 各自有一套读写器，而 generic、Spark、Flink 等每个对象模型又各自实现一套，彼此缺乏统一契约，新增格式或新增引擎时需在多处复制粘贴式地实现读取器/写入器构建逻辑，且常常要经过中间表示转换、损失性能。这一现状长期制约着 Iceberg 在多格式、多引擎下的可扩展性与一致性。

本提交引入一套全新的、统一的 File Format API 接口体系（位于 `org.apache.iceberg.formats` 包），为"存储格式（Parquet/Avro/ORC）"与"对象模型（generic/Spark/Flink 等内存表示）"之间架起统一抽象。核心思想是：用 `FormatModel<D, S>` 作为桥梁——它绑定一种文件格式与一种对象模型的数据类型 `D` 及引擎 schema 类型 `S`，对外提供 `readBuilder`/`writeBuilder`；格式底层负责解析细节，对象模型决定内存表示，从而在不引入中间表示的前提下直接转换，既统一 API 又优化性能。引擎可通过 `FormatModelRegistry` 注册自己的对象模型，使自定义数据表示无缝接入 Iceberg 的文件读写能力。

需要强调的是，本提交是这套 API 的"接口脚手架"：它定义了 `FormatModel`、`BaseFormatModel`、`FormatModelRegistry`、`ModelWriteBuilder`、`ReadBuilder`、`FileWriterBuilder`、`FileWriterBuilderImpl` 等契约与实现，但注册表中的 `CLASSES_TO_REGISTER` 列表当前为空，尚未注册任何内置格式模型，也未迁移既有读写路径——具体格式模型实现与既有路径迁移属后续工作。同时对 `PositionDeleteWriter` 做了一处过渡性调整以适配新写入器构建路径。该 PR 编号 #12774 也表明这是一个持续推进的架构演进。

## 如何达成设计目的

设计采用分层抽象：底层是面向单格式 `FileAppender`/`CloseableIterable` 的 `ModelWriteBuilder` 与 `ReadBuilder`；其上是面向 Iceberg 内容文件（`DataFile`/`DeleteFile`）的 `FileWriterBuilder`，它把低层 appender 包裹上分区规格、密钥元数据、排序顺序等 Iceberg 元数据后产出 `DataWriter`/`EqualityDeleteWriter`/`PositionDeleteWriter`；`FormatModelRegistry` 作为全局静态注册表，按 `(FileFormat, 对象模型类型)` 二元组索引各 `FormatModel`，对外暴露统一的 `readBuilder`/`dataWriteBuilder`/`equalityDeleteWriteBuilder`/`positionDeleteWriteBuilder` 入口。`BaseFormatModel` 提供抽象基类，通过 `WriterFunction`/`ReaderFunction` 两个函数式接口把"由三套 schema（Iceberg schema、文件 schema、引擎 schema）构造格式读写器"的具体逻辑留给子类。涉及 9 个文件：7 个新增（formats 包）、1 个既有类适配（`PositionDeleteWriter`）、1 个测试。

## 修改详情

### `core/src/main/java/org/apache/iceberg/formats/FormatModel.java` (+132/-0)

**修改目的**：定义统一文件格式模型的核心接口。

**工作逻辑**：
`FormatModel<D, S>` 是整套抽象的顶层契约。`format()` 返回其支持的 `FileFormat`；`type()` 返回对象模型的数据类型 `Class<? extends D>`（读取输出/写入输入的类型）；`schemaType()` 返回引擎 schema 类型 `Class<S>`。`writeBuilder(EncryptedOutputFile)` 返回 `ModelWriteBuilder<D, S>`，`readBuilder(InputFile)` 返回 `ReadBuilder<D, S>`。Javadoc 说明它桥接存储格式与内存表示、支持直接转换以避免中间表示，并指出引擎可实现自定义对象模型经 `FormatModelRegistry` 注册接入。

### `core/src/main/java/org/apache/iceberg/formats/BaseFormatModel.java` (+132/-0)

**修改目的**：提供 `FormatModel` 的抽象基类，封装读写器构造的函数式接口。

**工作逻辑**：
抽象类 `BaseFormatModel<D, S, W, R, F>` 实现 `FormatModel<D, S>`，新增三个泛型：`W`（格式特定写入器类型）、`R`（格式特定读取器类型）、`F`（底层文件 schema 类型）。持有 `type`、`schemaType`、`writerFunction`、`readerFunction`，并通过受保护方法暴露后者两个。定义两个 `@FunctionalInterface`：

- `WriterFunction<W, S, F>`：`W write(Schema icebergSchema, F fileSchema, S engineSchema)`——由三套 schema 构造格式写入器。
- `ReaderFunction<R, S, F>`：`R read(Schema icebergSchema, F fileSchema, S engineSchema, Map<Integer, ?> idToConstant)`——多一个 `idToConstant`（分区列等不在数据文件中的常量字段）。注：`fileSchema` 对 Avro 可为 `null`（后续传入）。

这把"按 schema 创建格式读写器"的具体实现下沉到子类的两个函数，基类只管编排。

### `core/src/main/java/org/apache/iceberg/formats/FormatModelRegistry.java` (+211/-0)

**修改目的**：作为全局注册表与统一读写入口。

**工作逻辑**：
静态工具类，内部以 `Map<Pair<FileFormat, Class<?>>, FormatModel<?, ?>> MODELS`（并发 map）按 `(格式, 对象模型数据类型)` 二元组索引。`register(FormatModel)` 加锁注册，校验同二元组未重复（否则抛 `IllegalArgumentException`）。对外暴露四个入口：`readBuilder(format, type, inputFile)`、`dataWriteBuilder(format, type, outputFile)`、`equalityDeleteWriteBuilder(format, type, outputFile)`、`positionDeleteWriteBuilder(format, outputFile)`（position delete 以 `PositionDelete<D>` 为数据类型查模型）。`modelFor(format, type)` 做查找，未注册则抛异常。

`registerSupportedFormats()` 在静态块中遍历 `CLASSES_TO_REGISTER`（当前空列表），用 `DynMethods` 反射调用各候选类的静态 `register` 方法；找不到方法时仅记 INFO（允许对应 jar 不在 classpath 时的优雅降级）。这一机制使 parquet/orc/avro 等模块可在 classpath 上时自动注册，而不强制依赖。`models()` 仅供测试可见。

### `core/src/main/java/org/apache/iceberg/formats/ModelWriteBuilder.java` (+127/-0)

**修改目的**：定义构造底层格式写入器（`FileAppender`）的构建器接口。

**工作逻辑**：
`ModelWriteBuilder<D, S>` 面向"仅需 appender"的场景直接暴露给用户。方法包括：`schema(Schema)`、`engineSchema(S)`（引擎对 schema 的表示，补充 Iceberg schema 无法表达的信息，如 tinyint/smallint 映射、variant 的 shredded 表示）、`set/setAll`（写入器配置，未知键应忽略）、`meta`（文件元数据键值）、`content(FileContent)`（按 DATA/EQUALITY_DELETES/POSITION_DELETES 区分 appender 配置）、`metricsConfig`、`overwrite`、`withFileEncryptionKey`/`withAADPrefix`（加密）、`build()` 返回 `FileAppender<D>`。

### `core/src/main/java/org/apache/iceberg/formats/ReadBuilder.java` (+124/-0)

**修改目的**：定义构造底层格式读取器（`CloseableIterable`）的构建器接口。

**工作逻辑**：
`ReadBuilder<D, S>` 面向读取场景。方法包括：`split(start, length)`（范围读取）、`project(Schema)`（投影 schema）、`engineProjection(S)`（引擎对投影 schema 的表示，如用 long 表示 int、variant 的 shredded 表示、struct 的具体类）、`caseSensitive`、`filter(Expression)`（谓词下推，不保证完全过滤）、`set/setAll`（读取器配置）、`reuseContainers`（复用容器降 GC）、`recordsPerBatch`（向量化批大小）、`idToConstant(Map<Integer,?>)`（元数据列常量，如分区列）、`withNameMapping(NameMapping)`、`build()` 返回 `CloseableIterable<D>`。

### `core/src/main/java/org/apache/iceberg/formats/FileWriterBuilder.java` (+159/-0)

**修改目的**：定义产出 Iceberg 内容文件写入器（`DataWriter`/`EqualityDeleteWriter`/`PositionDeleteWriter`）的高层构建器接口。

**工作逻辑**：
`FileWriterBuilder<W extends FileWriter<?, ?>, S>` 在 `ModelWriteBuilder` 之上叠加 Iceberg 元数据配置：`set/setAll`、`meta`、`metricsConfig`、`overwrite`、`withFileEncryptionKey`/`withAADPrefix`、`spec(PartitionSpec)`、`partition(StructLike)`、`keyMetadata(EncryptionKeyMetadata)`、`sortOrder(SortOrder)`、`schema(Schema)`、`engineSchema(S)`、`equalityFieldIds(int...)`（仅 equality delete 适用）、`build()` 返回具体写入器 `W`。统一了三类内容文件的配置入口。

### `core/src/main/java/org/apache/iceberg/formats/FileWriterBuilderImpl.java` (+299/-0)

**修改目的**：实现 `FileWriterBuilder`，把低层 appender 包裹为三类 Iceberg 写入器。

**工作逻辑**：
抽象类 `FileWriterBuilderImpl<W, D, S>` 持有 `ModelWriteBuilder<D, S>`（已设好 `content`）、location、format、content，以及 schema/spec/partition/keyMetadata/sortOrder/equalityFieldIds。它把 `set/meta/metricsConfig/overwrite/encryption/schema/engineSchema` 等委托给内部 `modelWriteBuilder`，而 spec/partition/keyMetadata/sortOrder/equalityFieldIds 留在本层（这些是 Iceberg 元数据，非格式层关注）。

提供三个静态工厂与三个内部子类：
- `forDataFile` → `DataFileWriterBuilder`：`build()` 校验后 `new DataWriter<>(modelWriteBuilder().build(), format, location, spec, partition, keyMetadata, sortOrder)`。
- `forEqualityDelete` → `EqualityDeleteWriterBuilder`：`build()` 时给 appender 追加 `meta("delete-type","equality")` 与 `meta("delete-field-ids", ...)`，再 `new EqualityDeleteWriter<>(appender, format, location, spec, partition, keyMetadata, sortOrder, equalityFieldIds)`。
- `forPositionDelete` → `PositionDeleteWriterBuilder`：数据类型为 `PositionDelete<D>`，`build()` 时 appender 追加 `meta("delete-type","position")`，再 `new PositionDeleteWriter<>(appender, format, location, spec, partition, keyMetadata)`。

`validate()` 校验：equality delete 必须有 equalityFieldIds、非 position delete 必须有 schema、spec 非空、分区与 spec 匹配。

### `core/src/main/java/org/apache/iceberg/deletes/PositionDeleteWriter.java` (+11/-3)

**修改目的**：过渡性适配新的 position delete 写入器构建路径。

**工作逻辑**：
字段 `appender` 类型由 `FileAppender<StructLike>` 改为 `FileAppender<PositionDelete<T>>`；构造方法入参由 `FileAppender<StructLike>` 放宽为 `FileAppender<? extends StructLike>`（因 `PositionDelete<T>` 实现 `StructLike`，可接收新路径产出的 `FileAppender<PositionDelete<D>>`），内部以 `(FileAppender<PositionDelete<T>>) appender` 做未受检强转。构造方法标注 `@Deprecated`（since 1.11.0），说明将在 1.12.0 直接接收 `FileAppender<PositionDelete<T>>` 而非通配上限形式，完成从旧签名向新签名的平滑过渡。这与 `FileWriterBuilderImpl.PositionDeleteWriterBuilder` 产出的 appender 类型对齐。

### `core/src/test/java/org/apache/iceberg/formats/TestFormatModelRegistry.java` (+125/-0)

**修改目的**：验证注册表的注册、查重与查重失败行为。

**工作逻辑**：
`@BeforeEach` 清空 `MODELS` 以隔离测试。内部 `DummyParquetFormatModel` 实现 `FormatModel<Object, Object>`（format 固定 PARQUET，writeBuilder/readBuilder 返回 null）。`testSuccessfulRegister` 验证注册后表项存在；`testRegistrationForDifferentType` 验证不同 `type`（Object vs Long）可并存；`testFailingReRegistrations` 验证同 `(PARQUET, Object)` 但不同 `schemaType`（String）或 `schemaType` 为 null 时抛 `IllegalArgumentException` 且消息含 "Cannot register class"。

## 总结

本提交为 Iceberg 引入了统一 File Format API 的接口脚手架：以 `FormatModel` 桥接文件格式与对象模型，以 `FormatModelRegistry` 集中按"(格式, 对象模型)"索引并提供统一的读写入口，以分层 `ModelWriteBuilder`/`ReadBuilder`/`FileWriterBuilder` 契约覆盖从低层 appender/iterable 到高层 `DataWriter`/`DeleteWriter` 的构建，并以 `BaseFormatModel` 的函数式接口把格式特定读写逻辑留给子类。虽然当前尚未注册内置模型、未迁移既有路径，但该接口体系为后续统一 Parquet/Avro/ORC 在多引擎下的读写、消除重复实现、支持引擎自定义对象模型奠定了架构基础，是 Iceberg 文件访问层迈向可扩展、可插拔的关键一步。
