# 提交 3272：Core, Data, Flink: Moving Flink to use the new FormatModel API (#15329)

## 提交信息

- **序号**：3272 / 4088
- **哈希**：68b7a2a71056aafd982b16e45e7f7ec254802e4a
- **短哈希**：68b7a2a71
- **日期**：2026-02-17
- **作者**：pvary
- **提交说明**：Core, Data, Flink: Moving Flink to use the new FormatModel API (#15329)
- **PR/Issue**：#15329

## 总体目的

本提交把 Flink 2.1 集成（`flink/v2.1/`）从旧的格式特定 builder 回调模型迁移到新的 `FormatModelRegistry` 统一对象模型，是 3271 提交（Data 模块迁移）之后的第二个引擎迁移。涉及两侧：写侧的 `FlinkFileWriterFactory` 与读侧的 `RowDataFileScanTaskReader`。

旧的 Flink 写工厂 `FlinkFileWriterFactory extends BaseFileWriterFactory<RowData>` 同样要覆写 9 个 `configureXxx(Avro|Parquet|ORC...)` 回调，每个回调用 Flink 的 `FlinkAvroWriter`、`FlinkParquetWriters.buildWriter`、`FlinkOrcWriter.buildWriter` 配置格式特定 builder，position delete 还要 `transformPaths(path -> StringData.fromString(...))`。这些回调把 Flink 与 Avro/Parquet/ORC 的 builder API 强耦合，且 Flink 还要把 Iceberg `Schema` 转成 Flink `RowType`（通过 `FlinkSchemaUtil.convert`），缓存在 `dataFlinkType`/`equalityDeleteFlinkType` 字段里惰性计算。

旧的 Flink 读路径 `RowDataFileScanTaskReader` 在 `readTask` 中按 `task.file().format()` 做 `switch (PARQUET/AVRO/ORC)`，分别调用 `newParquetIterable`/`newAvroIterable`/`newOrcIterable` 三个私有方法。每个方法各自构造 `Parquet.ReadBuilder`/`Avro.ReadBuilder`/`ORC.ReadBuilder`，调用 `createReaderFunc(FlinkParquetReaders::buildReader / FlinkPlannedAvroReader::create / FlinkOrcReader::new)`，并处理 `nameMapping`、`split`、`project`、`filter`、`caseSensitive`、`reuseContainers` 等。ORC 还多一步 `TypeUtil.selectNot` 剔除常量与元数据字段。这种 switch + 三段重复结构是典型的"格式耦合"代码。

本提交把两侧都迁移到 `FormatModelRegistry`：(1) 新增 `FlinkFormatModels` 注册类，把 Flink 的 `RowData`/`RowType` 与 Parquet/Avro/ORC 三种格式的 writer/reader 工厂注册到全局 registry；(2) `FormatModelRegistry` 的 `CLASSES_TO_REGISTER` 列表追加 `FlinkFormatModels`，使其在 registry 静态初始化时被加载；(3) `FlinkFileWriterFactory` 改为继承 3271 引入的 `RegistryBasedFileWriterFactory<RowData, RowType>`，删除全部 9 个 `configureXxx` 回调与惰性 `dataFlinkType()`/`equalityDeleteFlinkType()` 辅助方法，改为构造时一次性计算并传入引擎 `RowType`；(4) `RowDataFileScanTaskReader` 删除 `switch` 与三个 `newXxxIterable` 私有方法，改为单次 `FormatModelRegistry.readBuilder(format, RowData.class, inputFile)` + 链式 `project/idToConstant/split/caseSensitive/filter/reuseContainers/build` 调用，nameMapping 单点处理。同时更新两个测试中通过反射访问 `writerProperties` 字段的 `DynFields` 调用，把目标类由 `BaseFileWriterFactory` 改为 `RegistryBasedFileWriterFactory`，并把若干 `FileWriterFactory`/`TaskWriter`/`IcebergStreamWriter` 类型改为带泛型 `<?>` 的形式以匹配新基类的泛型签名。

## 如何达成设计目的

整体思路是"注册 Flink 的格式模型 + 用 RegistryBasedFileWriterFactory 替换旧基类 + 用 FormatModelRegistry.readBuilder 替换 switch"。涉及四个生产文件与两个测试文件：在 `core` 模块的 `FormatModelRegistry.CLASSES_TO_REGISTER` 列表加一项；在 `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/data/` 新增 `FlinkFormatModels` 注册类；改写 `FlinkFileWriterFactory` 与 `RowDataFileScanTaskReader`；同步更新两个测试的反射访问。

## 修改详情

### `core/src/main/java/org/apache/iceberg/formats/FormatModelRegistry.java` (+2/-1 lines)

**修改目的**：把 Flink 的格式模型注册类纳入 registry 静态初始化。

**工作逻辑**：在 `CLASSES_TO_REGISTER` 这个 `ImmutableList<String>` 中，于 `GenericFormatModels`、`ArrowFormatModels` 之后追加 `"org.apache.iceberg.flink.data.FlinkFormatModels"`。registry 在静态块里会对列表中每个类调用 `register()`，从而把 Flink 的 `RowData` 对象模型注册到三种格式上。注意这是反射式注册，core 模块不需要在编译期依赖 flink 模块。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/data/FlinkFormatModels.java` (+58/-0 lines，新文件)

**修改目的**：把 Flink 的 Parquet/Avro/ORC 读写函数注册为 `FormatModel`。

**工作逻辑**：`FlinkFormatModels.register()` 调用三次 `FormatModelRegistry.register(...)`：
- Parquet：`ParquetFormatModel.create(RowData.class, RowType.class, writerFunc, readerFunc)`，其中 `writerFunc = (icebergSchema, fileSchema, engineSchema) -> FlinkParquetWriters.buildWriter(engineSchema, fileSchema)`，`readerFunc = (icebergSchema, fileSchema, engineSchema, idToConstant) -> FlinkParquetReaders.buildReader(icebergSchema, fileSchema, idToConstant)`。
- Avro：`AvroFormatModel.create(RowData.class, RowType.class, writerFunc, readerFunc)`，writer 返回 `new FlinkAvroWriter(engineSchema)`，reader 返回 `FlinkPlannedAvroReader.create(icebergSchema, idToConstant)`。
- ORC：`ORCFormatModel.create(RowData.class, RowType.class, writerFunc, readerFunc)`，writer 返回 `FlinkOrcWriter.buildWriter(engineSchema, icebergSchema)`，reader 返回 `new FlinkOrcReader(icebergSchema, fileSchema, idToConstant)`。

这些函数直接复用既有的 Flink 读写器实现，只是把它们包装成 `FormatModel` 要求的 `(icebergSchema, fileSchema, engineSchema[, idToConstant])` 函数式接口。私有构造器 `FlinkFormatModels()` 防止实例化。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkFileWriterFactory.java` (+13/-85 lines)

**修改目的**：让 Flink 写工厂走新 FormatModel API。

**工作逻辑**：类签名由 `extends BaseFileWriterFactory<RowData>` 改为 `extends RegistryBasedFileWriterFactory<RowData, RowType>`，构造器从 private 改为 package-private（便于 builder 复用）。`super(...)` 调用新增 `RowData.class` 作为 `inputType`，并新增两个引擎 schema 参数：`dataFlinkType == null ? FlinkSchemaUtil.convert(dataSchema) : dataFlinkType` 与 `equalityDeleteInputSchema(equalityDeleteFlinkType, equalityDeleteRowSchema)`。新增私有静态方法 `equalityDeleteInputSchema(RowType rowType, Schema rowSchema)`：rowType 非空返回 rowType，否则若 rowSchema 非空返回 `FlinkSchemaUtil.convert(rowSchema)`，否则返回 null——把原先惰性 `dataFlinkType()`/`equalityDeleteFlinkType()` 的逻辑前移到构造期。删除全部 9 个 `configureDataWrite/configureEqualityDelete/configurePositionDelete` 回调、`dataFlinkType()`/`equalityDeleteFlinkType()` 惰性方法、`StringData` import 与 Avro/Parquet/ORC 的 `transformPaths` 处理（这些在新模型里由 FormatModel 内部处理）。`Builder` 内部类保留不变。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/source/RowDataFileScanTaskReader.java` (+8/-104 lines)

**修改目的**：让 Flink 读路径走新 FormatModel API。

**工作逻辑**：`readTask` 中原先按 `task.file().format()` 的 `switch (PARQUET/AVRO/ORC)` 替换为单次 `FormatModelRegistry.readBuilder(task.file().format(), RowData.class, inputFilesDecryptor.getInputFile(task))` 得到 `ReadBuilder<RowData, RowType>`，然后链式 `.withNameMapping(if nameMapping != null).project(schema).idToConstant(idToConstant).split(task.start(), task.length()).caseSensitive(caseSensitive).filter(task.residual()).reuseContainers().build()`。ORC 路径里原先需要的 `TypeUtil.selectNot(schema, Sets.union(idToConstant.keySet(), MetadataColumns.metadataFieldIds()))` 剔除常量/元数据字段的特殊处理不再由 Flink 侧负责，由 FormatModel 内部统一处理。删除 `newAvroIterable`/`newParquetIterable`/`newOrcIterable` 三个私有方法及对应 import（`Avro`/`Parquet`/`ORC`/`MetadataColumns`/`Sets`/`TypeUtil`/各 Flink reader 类），新增 `FormatModelRegistry`/`ReadBuilder` import。rowFilter 初始化的局部变量也去掉冗余 `this.` 前缀。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/TestCompressionSettings.java` (+5/-5 lines)

**修改目的**：更新反射访问以匹配新基类与泛型签名。

**工作逻辑**：反射字段访问的目标类由 `BaseFileWriterFactory` 改为 `RegistryBasedFileWriterFactory`；`DynFields.BoundField<IcebergStreamWriter>`、`<TaskWriter>`、`<FileWriterFactory>` 改为 `<IcebergStreamWriter<?>>`、`<TaskWriter<?>>`、`<FileWriterFactory<?>>`，因为 `RegistryBasedFileWriterFactory<T,S>` 与 `TaskWriter<T>` 等现在是泛型类，反射拿到的是带类型参数的实例。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicWriter.java` (+3/-3 lines)

**修改目的**：同上，更新反射访问。

**工作逻辑**：目标类由 `BaseFileWriterFactory` 改为 `RegistryBasedFileWriterFactory`，`DynFields.BoundField<FileWriterFactory>` 改为 `<FileWriterFactory<?>>`。

## 总结

本提交通过新增 `FlinkFormatModels` 注册类并把 `FlinkFileWriterFactory`/`RowDataFileScanTaskReader` 迁移到 `FormatModelRegistry` 与 `RegistryBasedFileWriterFactory<RowData, RowType>`，消除了 Flink 适配器中 9 个格式特定写回调与 3 段格式特定读分支，使 Flink 读写路径不再直接依赖 Avro/Parquet/ORC 的 builder API。这是 FormatModel API 迁移系列在引擎侧的首次落地，与 3271（Data）形成完整的读写两端迁移，为 3274（Spark）提交的同类迁移提供了第二个范例。
