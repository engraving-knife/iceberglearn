# 提交 3245：Core, Data: Implementation of AvroFormatModel (#15254)

## 提交信息

- **序号**：3245 / 4088
- **哈希**：6d565b222a885c0e8db6c4ed2c80ef2b7c478061
- **短哈希**：6d565b222
- **日期**：2026-02-13
- **作者**：pvary
- **提交说明**：Core, Data: Implementation of AvroFormatModel (#15254)
- **PR/Issue**：#15254

## 总体目的

本提交实现了 Iceberg 新的格式模型（Format Model）抽象体系中的 Avro 格式模型。Iceberg 正在引入一个可插拔的格式模型架构（`FormatModel` / `BaseFormatModel` / `FormatModelRegistry`），旨在将文件格式特定的 I/O 逻辑（读写数据文件和删除文件）与上层表操作解耦。在此架构下，每种文件格式（Avro、Parquet、ORC）为每种对象模型（如 generic `Record`、Spark `InternalRow` 等）注册一个 `FormatModel`，上层代码通过 `FormatModelRegistry` 统一获取读写构建器，无需直接依赖具体格式的 API。

在此提交之前，`FormatModelRegistry` 的注册类列表（`CLASSES_TO_REGISTER`）为空，意味着没有任何格式模型被实际注册，整个格式模型架构尚不可用。本提交实现了 Avro 格式的 `AvroFormatModel`，并在 `data` 模块中提供了 `GenericFormatModels` 注册器（将 generic `Record` 对象模型与 Avro 格式绑定），使格式模型注册表首次具备了实际的 Avro 读写能力。这是格式模型架构落地的第一步——Avro 作为首个被实现的格式，验证了整个抽象设计的可行性，后续 Parquet 和 ORC 的格式模型可按相同模式实现。

`AvroFormatModel` 需要包装现有的 `Avro.WriteBuilder` 和 `Avro.ReadBuilder`，将其适配为统一的 `ModelWriteBuilder` 和 `ReadBuilder` 接口。为此需要将 `Avro` 类中部分内部组件的可见性从 private 提升到包级别（package-private），使同包的 `AvroFormatModel` 能够访问。同时为 `PositionDelete` 新增 `deleteClass()` 静态方法以消除 `FormatModelRegistry` 中的未检查类型转换。

## 如何达成设计目的

整体思路分四部分：(1) 新建 `AvroFormatModel` 类，继承 `BaseFormatModel`，通过内部 `WriteBuilderWrapper` 和 `ReadBuilderWrapper` 将 Avro 原生的 `Avro.WriteBuilder` / `Avro.ReadBuilder` 适配为格式模型统一接口；(2) 新建 `GenericFormatModels` 注册器，将 generic `Record` 对象模型的 Avro 读写函数注册到 `FormatModelRegistry`；(3) 调整 `Avro.java` 中三个组件的可见性以供 `AvroFormatModel` 使用，并为 `PositionDelete` 添加 `deleteClass()` 方法；(4) 将 `GenericFormatModels` 添加到 `FormatModelRegistry` 的自动注册类列表中。配套测试通过 `FormatModelRegistry` API 验证数据文件、等值删除文件和位置删除文件的读写往返（round-trip）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/avro/AvroFormatModel.java` (+277/-0 lines，新文件)

**修改目的**：实现 Avro 格式的 `FormatModel`，将 Avro 原生读写构建器适配为统一接口。

**工作逻辑**：

`AvroFormatModel<D, S>` 继承 `BaseFormatModel<D, S, DatumWriter<D>, DatumReader<D>, Schema>`，其中 `D` 为数据类型，`S` 为引擎 schema 类型，`DatumWriter`/`DatumReader` 为 Avro 原生读写器类型，`Schema` 为 Avro 文件 schema 类型。

- **静态工厂方法**：`forPositionDeletes()` 创建用于位置删除的模型（无需 writer/reader 函数，因为位置删除使用固定的 `PositionDatumWriter`）；`create(...)` 接受类型、schema 类型和 writer/reader 函数创建通用模型。

- **`format()`**：返回 `FileFormat.AVRO`。

- **`WriteBuilderWrapper`**：实现 `ModelWriteBuilder<D, S>` 接口，内部持有 `Avro.WriteBuilder`。各方法委托给内部构建器（`schema`、`set`、`setAll`、`meta`、`metricsConfig`、`overwrite` 等）。`withFileEncryptionKey` 和 `withAADPrefix` 抛出 `UnsupportedOperationException`（Avro 不支持文件级加密）。核心在 `build()` 方法中根据 `FileContent` 分三种路径：
  - `DATA`：设置 `dataContext`，通过 `writerFunction.write(schema, avroSchema, engineSchema)` 创建写入器。
  - `EQUALITY_DELETES`：设置 `deleteContext`，同样通过 writer 函数创建写入器。
  - `POSITION_DELETES`：校验 schema 和 engineSchema 必须为 null（位置删除不支持自定义 schema），设置 `deleteContext`，使用 `Avro.PositionDatumWriter` 作为写入器，并设置 `DeleteSchemaUtil.pathPosSchema()` 作为固定 schema。

- **`ReadBuilderWrapper`**：实现 `ReadBuilder<D, S>` 接口，内部持有 `Avro.ReadBuilder`。`split`、`project`、`reuseContainers`、`withNameMapping`、`idToConstant` 委托给内部构建器。`caseSensitive`、`filter`、`set` 为空操作（Avro 读取器不支持过滤，过滤是尽力而为的）。`recordsPerBatch` 抛出 `UnsupportedOperationException`（Avro 不支持批量读取）。`build()` 方法通过 `internal.createResolvingReader(...)` 传入 reader 函数构建 `CloseableIterable<D>`，其中 file schema 直接由 Avro 读取路径传给 DatumReader（故 reader 函数的 fileSchema 参数传 null）。

### `data/src/main/java/org/apache/iceberg/data/GenericFormatModels.java` (+40/-0 lines，新文件)

**修改目的**：将 generic `Record` 对象模型的 Avro 读写函数注册到 `FormatModelRegistry`。

**工作逻辑**：`register()` 方法调用 `FormatModelRegistry.register(...)` 注册两个模型：
- **数据模型**：`AvroFormatModel.create(Record.class, Void.class, writerFunc, readerFunc)`。writer 函数使用 `DataWriter.create(fileSchema)` 创建 Avro 数据写入器；reader 函数使用 `PlannedDataReader.create(icebergSchema, idToConstant)` 创建 Avro 数据读取器。`Void.class` 作为 schema 类型表示 generic 模型不使用引擎特定 schema。
- **位置删除模型**：`AvroFormatModel.forPositionDeletes()`，处理 `PositionDelete` 类型的位置删除文件。

### `core/src/main/java/org/apache/iceberg/avro/Avro.java` (+3/-4 lines)

**修改目的**：提升内部组件可见性以供 `AvroFormatModel` 访问。

**工作逻辑**：
- `WriteBuilder.createContextFunc(Function)` 从 `private` 改为包级别（`WriteBuilder createContextFunc(...)`），使 `AvroFormatModel.WriteBuilderWrapper` 能调用它来设置数据/删除上下文。
- `WriteBuilder.Context` 从 `private static class` 改为 `static class`（包级别），使 `AvroFormatModel` 能引用 `Avro.WriteBuilder.Context::dataContext` 和 `::deleteContext` 方法引用。
- `PositionDatumWriter` 从 `private static class` 改为 `static class`（包级别），使 `AvroFormatModel` 能在位置删除路径中直接 `new Avro.PositionDatumWriter()`。

### `core/src/main/java/org/apache/iceberg/deletes/PositionDelete.java` (+5/-0 lines)

**修改目的**：新增 `deleteClass()` 静态方法以提供类型安全的 `Class<PositionDelete<T>>` 获取方式。

**工作逻辑**：新增 `@SuppressWarnings("unchecked") public static <T> Class<PositionDelete<T>> deleteClass() { return (Class<PositionDelete<T>>) (Class<?>) PositionDelete.class; }`。由于 Java 泛型擦除，`PositionDelete<T>.class` 无法直接获取，需要通过原始类型加两步 unchecked cast。将此逻辑封装为静态方法，供 `FormatModelRegistry.positionDeleteWriteBuilder` 调用，消除了该处的内联 cast 和 `@SuppressWarnings("unchecked")`。

### `core/src/main/java/org/apache/iceberg/formats/FormatModelRegistry.java` (+3/-6 lines)

**修改目的**：启用 `GenericFormatModels` 的自动注册并简化位置删除构建器代码。

**工作逻辑**：
- `CLASSES_TO_REGISTER` 从空列表 `ImmutableList.of()` 改为 `ImmutableList.of("org.apache.iceberg.data.GenericFormatModels")`。`FormatModelRegistry` 在初始化时通过反射加载并调用这些类的 `register()` 方法，完成格式模型的自动注册。
- `positionDeleteWriteBuilder` 方法中，将内联的 `(Class<PositionDelete<D>>) (Class<?>) PositionDelete.class` cast 替换为 `PositionDelete.deleteClass()` 调用，移除了 `@SuppressWarnings("unchecked")` 注解。

### `data/src/test/java/org/apache/iceberg/data/DataTestHelpers.java` (+8/-0 lines)

**修改目的**：新增 `List<Record>` 级别的批量断言方法。

**工作逻辑**：新增重载方法 `assertEquals(Types.StructType struct, List<Record> expected, List<Record> actual)`，先断言两个列表大小相同，再逐条调用已有的单条记录 `assertEquals` 方法比较。供测试中验证读写往返的记录列表一致性使用。

### `data/src/test/java/org/apache/iceberg/data/TestGenericFormatModels.java` (+204/-0 lines，新文件)

**修改目的**：通过 `FormatModelRegistry` API 验证 Avro 格式模型的数据和删除文件读写往返。

**工作逻辑**：测试类使用 `@ParameterizedTest` + `@FieldSource("FILE_FORMATS")` 参数化（当前仅 `FileFormat.AVRO`），使用 `InMemoryFileIO` 和 `EncryptedFiles` 构建测试文件。三个测试方法：

- `testDataWriterRoundTrip`：通过 `FormatModelRegistry.dataWriteBuilder(AVRO, Record.class, ...)` 获取写入器，写入 10 条随机 `Record`，关闭后获取 `DataFile`。验证记录数和格式。再通过 `FormatModelRegistry.readBuilder(AVRO, Record.class, ...)` 读取回来，用 `DataTestHelpers.assertEquals` 比较原始记录和读取记录。

- `testEqualityDeleteWriterRoundTrip`：通过 `FormatModelRegistry.equalityDeleteWriteBuilder(...)` 写入等值删除记录（equality field id = 3），获取 `DeleteFile`。验证记录数、格式和 equality field ids。读取回来比较。

- `testPositionDeleteWriterRoundTrip`：通过 `FormatModelRegistry.positionDeleteWriteBuilder(...)` 写入位置删除（`PositionDelete` 包含文件路径和位置），获取 `DeleteFile`。验证记录数和格式。用 `DELETE_FILE_PATH` / `DELETE_FILE_POS` schema 投影读取回来，构建期望记录列表比较。

## 总结

本提交实现了 Iceberg 格式模型抽象体系中的 Avro 格式模型（`AvroFormatModel`），并将 generic `Record` 对象模型的 Avro 读写能力通过 `GenericFormatModels` 注册到 `FormatModelRegistry`，使该注册表首次具备实际的 Avro 读写能力。`AvroFormatModel` 通过 `WriteBuilderWrapper` 和 `ReadBuilderWrapper` 将 Avro 原生的 `Avro.WriteBuilder` / `Avro.ReadBuilder` 适配为统一的 `ModelWriteBuilder` / `ReadBuilder` 接口，处理了数据文件、等值删除文件和位置删除文件三种内容类型。配套的可见性调整和 `PositionDelete.deleteClass()` 提取保持了代码整洁。全面的往返测试验证了三种文件类型的正确读写。这是格式模型架构落地的关键一步，为后续 Parquet 和 ORC 格式模型的实现确立了模式。
