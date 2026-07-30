# 提交 3263：Core, Arrow: Implementation of ArrowFormatModel (#15258)

## 提交信息

- **序号**：3263 / 4088
- **哈希**：6b15becc976a8353c48b9a8a6c75c7dc06cad0a3
- **短哈希**：6b15becc9
- **日期**：2026-02-16
- **作者**：pvary
- **提交说明**：Core, Arrow: Implementation of ArrowFormatModel (#15258)
- **PR/Issue**：#15258

## 总体目的

Iceberg 此前在引入新的 `FormatModel` 抽象（位于 `core` 模块的 `org.apache.iceberg.formats` 包）以统一不同文件格式（Parquet/ORC/Avro）与不同对象模型（generic Record、Spark InternalRow、Flink RowData、Arrow ColumnarBatch 等）的读写器创建流程。`FormatModelRegistry` 作为中央注册表，按 `(FileFormat, Class<?>)` 二元组索引已注册的 `FormatModel`，外部通过 `FormatModelRegistry.readBuilder(format, type, inputFile)` 即可获取对应格式与对象模型的 `ReadBuilder`，而无需在各调用方直接依赖具体格式实现（如 `Parquet.read(...)`）。

在本次提交之前，注册表中只注册了 `org.apache.iceberg.data.GenericFormatModels`，即为 generic 数据模型（`Record.class`）注册了 Parquet/ORC/Avro 的读写器。而 Arrow 向量化读取路径（`ArrowReader`）仍直接调用 `Parquet.read(location).project(...).split(...).createBatchedReaderFunc(...)...` 这一格式专属 API 来构建批量读取器，没有走统一的 `FormatModel` 注册机制。这意味着新的 FormatModel 抽象尚未覆盖 Arrow 向量化读取这一重要场景，抽象的统一性存在缺口——Arrow 的批量读取逻辑（`buildReader`）被硬编码在 `ArrowReader` 内部，无法像 generic 模型那样被注册、被发现、被统一调用。

本次提交补齐了这一缺口：新增 `ArrowFormatModels` 注册类，将 Arrow 的 Parquet 向量化批量读取器以 `ParquetFormatModel`（批量读器变体，`isBatchReader=true`）的形式注册到 `FormatModelRegistry`，键为 `(PARQUET, ColumnarBatch.class)`。注册时提供的 reader function 委托给 `ArrowReader.VectorizedCombinedScanIterator.buildReader(...)`。随后将 `ArrowReader` 中原本直接使用 `Parquet.read(...)` 构建读取器的代码改为通过 `FormatModelRegistry.readBuilder(FileFormat.PARQUET, ColumnarBatch.class, location)` 获取统一的 `ReadBuilder`，再链式配置 project/split/recordsPerBatch/caseSensitive/filter 等选项。这样 Arrow 读取路径正式纳入 FormatModel 抽象，与 generic 模型走同一套注册与构建机制，为后续将更多读取场景迁移到该 API（见紧随其后的 #15333）以及支持新格式/新对象模型奠定基础。

## 如何达成设计目的

思路分三步：① 新建 `ArrowFormatModels` 类，在其 `register()` 中调用 `ParquetFormatModel.create(...)` 批量读器重载，把 `ColumnarBatch.class` 与 Arrow 的 `buildReader` 函数注册为 `(PARQUET, ColumnarBatch)` 的 FormatModel；② 在 `FormatModelRegistry` 的 `CLASSES_TO_REGISTER` 列表中加入 `ArrowFormatModels` 全限定名，使其在静态初始化时被反射加载并注册；③ 重构 `ArrowReader.VectorizedCombinedScanIterator`，将直接调用 `Parquet.read(...)` 替换为 `FormatModelRegistry.readBuilder(...)` 获取统一构建器，并把 `createBatchedReaderFunc` 的逻辑移入注册的 reader function 中。为使 `ArrowFormatModels`（同包）能调用 `ArrowReader` 内部的构建方法，将 `VectorizedCombinedScanIterator` 与 `buildReader` 的可见性从 `private` 提升为包级（`static`）。

## 修改详情

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/ArrowFormatModels.java` (+39 lines, 新增文件)

**修改目的**：将 Arrow 的 Parquet 向量化批量读取器注册到统一的 FormatModelRegistry。

**工作逻辑**：
新类 `ArrowFormatModels` 提供静态 `register()` 方法，调用 `FormatModelRegistry.register(ParquetFormatModel.create(ColumnarBatch.class, Object.class, readerFunction))`。这里使用的是 `ParquetFormatModel.create(Class<? extends D> type, Class<S> schemaType, ReaderFunction<VectorizedReader<?>, S, MessageType> batchReaderFunction)` 这一专为批量读器设计的重载，它内部会将 `isBatchReader` 设为 `true`，使 `readBuilder` 返回的 `ReadBuilderWrapper` 走批量读器配置路径（设置 `createBatchedReaderFunc` 而非普通 reader function）。

- `type` 为 `ColumnarBatch.class`，即 Arrow 向量化读取的输出类型，也是注册表中的对象模型键。
- `schemaType` 为 `Object.class`，表示 Arrow 路径不使用独立的引擎 schema 类型（与 generic 的 `Void.class` 不同，这里用 `Object` 占位）。
- `readerFunction` 是一个四参数 lambda `(schema, fileSchema, engineSchema, idToConstant) -> ArrowReader.VectorizedCombinedScanIterator.buildReader(schema, fileSchema, NullCheckingForGet.NULL_CHECKING_ENABLED)`，它忽略了 `engineSchema` 与 `idToConstant`，只把 Iceberg schema 与 Parquet `MessageType` 传给 `buildReader`，并固定以 `NullCheckingForGet.NULL_CHECKING_ENABLED` 作为 `setArrowValidityVector` 参数（即始终启用 Arrow validity vector 的设置）。`NullCheckingForGet` 的 import 从 `ArrowReader` 迁移到了此处。

构造函数私有化（`private ArrowFormatModels() {}`）防止实例化。该类位于 `arrow` 模块，依赖 `core` 的 `FormatModelRegistry` 与 `parquet` 的 `ParquetFormatModel`。

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/ArrowReader.java` (+13/-20 lines)

**修改目的**：将 ArrowReader 的读取器构建从直接调用 `Parquet.read(...)` 改为通过 FormatModelRegistry 统一 API 获取，并将批量读器函数的创建职责上移到注册的 FormatModel。

**工作逻辑**：
核心改动在 `VectorizedCombinedScanIterator` 中构建 Parquet 迭代器的逻辑：

- 原先通过 `Parquet.read(location).project(expectedSchema).split(task.start(), task.length()).createBatchedReaderFunc(fileSchema -> buildReader(expectedSchema, fileSchema, NullCheckingForGet.NULL_CHECKING_ENABLED)).recordsPerBatch(batchSize).filter(task.residual()).caseSensitive(caseSensitive)` 一次性构建，其中 `createBatchedReaderFunc` 内联地用 `buildReader` 构造 `ArrowBatchReader`。
- 现在改为先通过 `FormatModelRegistry.readBuilder(FileFormat.PARQUET, ColumnarBatch.class, location)` 获取 `ReadBuilder<ColumnarBatch, ?>`（注册表会查找到 `ArrowFormatModels` 注册的批量读器 FormatModel，其 `readBuilder` 内部已封装了 `createBatchedReaderFunc` 调用上述 reader function），然后链式调用 `.project(expectedSchema).split(task.start(), task.length()).recordsPerBatch(batchSize).caseSensitive(caseSensitive).filter(task.residual())`，最后 `.build()`。`reuseContainers` 与 `withNameMapping` 的条件配置保持不变。

这一改动把"如何构造 ArrowBatchReader"的知识从 `ArrowReader` 调用点移到了 `ArrowFormatModels` 的注册逻辑中，调用方只需声明"我要按 PARQUET 格式读取 ColumnarBatch"即可获得正确配置的构建器。

为支持上述委托，可见性做了两处调整：
- `VectorizedCombinedScanIterator` 从 `private static final class` 改为 `static final class`（包级可见），因为 `ArrowFormatModels` 需要调用其 `buildReader`。
- `buildReader(Schema, MessageType, boolean)` 从 `private static` 改为 `static`（包级可见），供 `ArrowFormatModels` 的 reader function 调用。

同时移除了不再使用的 `import org.apache.parquet.Parquet` 与 `import org.apache.arrow.vector.NullCheckingForGet`（后者移至 `ArrowFormatModels`），新增了 `import org.apache.iceberg.formats.FormatModelRegistry` 与 `import org.apache.iceberg.formats.ReadBuilder`。

### `core/src/main/java/org/apache/iceberg/formats/FormatModelRegistry.java` (+3/-1 lines)

**修改目的**：将 `ArrowFormatModels` 加入启动时自动注册的类列表。

**工作逻辑**：
`FormatModelRegistry` 通过反射在静态初始化块中加载 `CLASSES_TO_REGISTER` 列表中的类并调用其 `register()` 方法（`registerSupportedFormats()`）。原先该列表仅含 `"org.apache.iceberg.data.GenericFormatModels"`，本次追加 `"org.apache.iceberg.arrow.vectorized.ArrowFormatModels"`。采用全限定类名字符串而非直接类引用，是为了避免 `core` 模块对 `arrow`/`data` 模块的编译期硬依赖（这些模块依赖 `core`，反向依赖会形成循环），通过反射在运行时按需加载。这样在 `FormatModelRegistry` 类初始化时，Arrow 的 Parquet 批量读器 FormatModel 就会被自动注册到 `MODELS` 映射中，键为 `(FileFormat.PARQUET, ColumnarBatch.class)`，后续 `readBuilder(PARQUET, ColumnarBatch.class, ...)` 即可命中。

## 总结

本次提交为新的 FormatModel 抽象补齐了 Arrow 向量化读取场景：新增 `ArrowFormatModels` 将 `ColumnarBatch` 的 Parquet 批量读器注册到 `FormatModelRegistry`，并把 `ArrowReader` 从直接调用 `Parquet.read(...)` 重构为通过统一的 `FormatModelRegistry.readBuilder(...)` API 构建读取器。批量读器函数的构造逻辑从调用点上移至注册的 FormatModel 中，可见性相应调整。这使得 Arrow 读取路径与 generic 数据模型走同一套注册与构建机制，统一了格式访问抽象，为后续迁移更多读取场景（#15333）及接入新格式/对象模型打下基础。
