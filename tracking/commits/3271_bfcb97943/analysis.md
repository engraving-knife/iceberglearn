# 提交 3271：Data: Moving GenericFileWriterFactory to the new FormatModel API (#15334)

## 提交信息

- **序号**：3271 / 4088
- **哈希**：bfcb97943831591f1340c584467b08995ae19965
- **短哈希**：bfcb97943
- **日期**：2026-02-17
- **作者**：pvary
- **提交说明**：Data: Moving GenericFileWriterFactory to the new FormatModel API (#15334)
- **PR/Issue**：#15334

## 总体目的

本提交把 `data` 模块中的 `GenericFileWriterFactory` 从旧的"格式特定 builder 回调"模型迁移到新的 `FormatModelRegistry` 统一对象模型，并引入一个新的公共基类 `RegistryBasedFileWriterFactory<T, S>` 作为后续引擎集成（Flink、Spark 等）迁移的范本。这是 Iceberg 1.11.0 一系列"FormatModel API 迁移"工作的第一步——为 Data 模块本身打样，紧接着 3272/3274 提交会把 Flink、Spark 同步迁移到同一模型。

旧的 `BaseFileWriterFactory<T>` 设计有一个明显痛点：每个引擎集成（`GenericFileWriterFactory`、`FlinkFileWriterFactory`、`SparkFileWriterFactory`）都必须覆写 9 个格式特定回调方法——`configureDataWrite(Avro.DataWriteBuilder)`、`configureDataWrite(Parquet.DataWriteBuilder)`、`configureDataWrite(ORC.DataWriteBuilder)`，以及 equality delete 和 position delete 各三种格式共 9 个钩子。这些回调让工厂直接面对 `Avro.DataWriteBuilder`、`Parquet.DataWriteBuilder`、`ORC.DataWriteBuilder` 等格式特定类型，导致每个引擎都要知道"如何用 Avro/Parquet/ORC 的 builder + 自己的 writer 函数构造一个 writer"。结果是工厂代码与具体格式 API 强耦合，新增一种格式要改所有引擎集成，新增一种引擎又要重复写 9 个回调。

新的 `FormatModelRegistry`（core 模块中已存在）把"格式 + 对象模型"组合注册为 `FormatModel`，并提供统一的 `dataWriteBuilder(format, inputType, file)` / `equalityDeleteWriteBuilder(format, inputType, file)` / `positionDeleteWriteBuilder(format, file)` 入口，返回一个泛型 `FileWriterBuilder<W, S>`，其中 `W` 是 writer 类型（`DataWriter<T>`/`EqualityDeleteWriter<T>`/`PositionDeleteWriter<T>`），`S` 是引擎特定的 schema 类型（例如 Generic/Spark 用 Iceberg `Schema`，Flink 用 `RowType`）。引擎集成只需在工厂构造时传入"inputSchema / equalityDeleteInputSchema"两个引擎 schema，然后调用 builder 上的统一方法（`schema/engineSchema/setAll/metricsConfig/spec/partition/keyMetadata/sortOrder/overwrite/build`）即可，完全不再感知 Avro/Parquet/ORC 的差异。

本提交还处理了一个遗留特例：旧的 `positionDeleteRowSchema`（带行数据的位置删除，1.11.0 起已废弃）在新 `RegistryBasedFileWriterFactory.newPositionDeleteWriter` 中不被支持（新模型只支持不带行数据的位置删除）。`GenericFileWriterFactory` 因此覆写 `newPositionDeleteWriter`，在检测到 `positionDeleteRowSchema != null` 时退回旧的格式特定 builder 路径并打印 `LOG.warn` 警告，从而在过渡期保留兼容。旧 `BaseFileWriterFactory` 整体被标记 `@Deprecated`（1.11.0 起、1.12.0 移除），为后续引擎迁移划清时间线。

## 如何达成设计目的

整体设计是"引入新基类 + 迁移一个实例 + 废弃旧基类"。涉及三个文件：(1) 新增 `RegistryBasedFileWriterFactory<T, S>` 抽象基类，实现 `FileWriterFactory<T>`，内部三个 `newXxxWriter` 全部走 `FormatModelRegistry`；(2) `GenericFileWriterFactory` 改为继承 `RegistryBasedFileWriterFactory<Record, Schema>`，构造器透传新基类所需参数（含 `inputType=Record.class`、`inputSchema`、`equalityDeleteInputSchema`），9 个 `configureXxx` 回调全部标记 `@Deprecated` 但保留（兼容旧基类路径），并覆写 `newPositionDeleteWriter` 处理 legacy position-delete-row-schema；(3) `BaseFileWriterFactory` 标记 `@Deprecated`。

## 修改详情

### `data/src/main/java/org/apache/iceberg/data/RegistryBasedFileWriterFactory.java` (+182/-0 lines，新文件)

**修改目的**：提供基于 `FormatModelRegistry` 的通用文件写工厂基类，供所有引擎集成复用。

**工作逻辑**：抽象类 `RegistryBasedFileWriterFactory<T, S> implements FileWriterFactory<T>, Serializable`，其中 `T` 是行类型、`S` 是引擎 schema 类型。构造器接收 `Table table, FileFormat dataFileFormat, Class<T> inputType, Schema dataSchema, SortOrder dataSortOrder, FileFormat deleteFileFormat, int[] equalityFieldIds, Schema equalityDeleteRowSchema, SortOrder equalityDeleteSortOrder, Map<String,String> writerProperties, S inputSchema, S equalityDeleteInputSchema`，全部存为字段（`writerProperties` 为 null 时回退 `ImmutableMap.of()`）。暴露 `inputSchema()` / `equalityDeleteInputSchema()` 供子类取用。三个 writer 工厂方法：
- `newDataWriter(file, spec, partition)`：校验 `dataSchema` 非空，取表属性与 `MetricsConfig.forTable(table)`（表为 null 时用默认），调用 `FormatModelRegistry.dataWriteBuilder(dataFileFormat, inputType, file)` 得到 `FileWriterBuilder<DataWriter<T>, S>`，链式调用 `.schema(dataSchema).engineSchema(inputSchema()).setAll(properties).setAll(writerProperties).metricsConfig(metricsConfig).spec(spec).partition(partition).keyMetadata(keyMetadata).sortOrder(dataSortOrder).overwrite().build()`，IO 异常包装为 `UncheckedIOException`。
- `newEqualityDeleteWriter(file, spec, partition)`：类似，用 `FormatModelRegistry.equalityDeleteWriteBuilder(deleteFileFormat, inputType, file)`，额外传 `equalityFieldIds(equalityFieldIds)`，schema 用 `equalityDeleteRowSchema`、engineSchema 用 `equalityDeleteInputSchema()`、sortOrder 用 `equalityDeleteSortOrder`。
- `newPositionDeleteWriter(file, spec, partition)`：用 `FormatModelRegistry.positionDeleteWriteBuilder(deleteFileFormat, file)`（注意不带 inputType，因为位置删除不写行数据），`MetricsConfig.forPositionDelete(table)`，不传 schema/equalityFieldIds/sortOrder。这是新模型的限制——位置删除只支持纯 (path, pos) 形式。

设计关键在于工厂不再 import 任何 `Avro`/`Parquet`/`ORC` 格式 API，全部通过 `FormatModelRegistry` 与 `FileWriterBuilder` 的统一接口完成，新增格式或新增引擎都不需要改本类。

### `data/src/main/java/org/apache/iceberg/data/GenericFileWriterFactory.java` (+137/-46 lines)

**修改目的**：把 `GenericFileWriterFactory` 从继承 `BaseFileWriterFactory<Record>` 改为继承 `RegistryBasedFileWriterFactory<Record, Schema>`，并保留 legacy position-delete-row 兼容。

**工作逻辑**：
- 类签名改为 `extends RegistryBasedFileWriterFactory<Record, Schema>`，新增 `LOG` 与四个私有字段 `table, format, positionDeleteRowSchema, writerProperties`（用于 legacy position-delete 路径）。
- 三个构造器全部更新为向 `super(...)` 透传新基类所需的参数：新增 `Record.class` 作为 `inputType`；新增两个引擎 schema 参数（数据写与 equality 删除写）。前两个构造器传 `null, null`（无引擎 schema 时由 FormatModel 用默认），第三个构造器（带 positionDeleteRowSchema）传 `dataSchema` 与 `equalityDeleteRowSchema` 作为引擎 schema，并把 `table/format/positionDeleteRowSchema/writerProperties` 存到本类字段以备 legacy 路径使用。
- 9 个 `configureDataWrite/configureEqualityDelete/configurePositionDelete(Avro|Parquet|ORC...)` 回调全部加 `@Deprecated` 注解与 Javadoc（"Since 1.11.0, will be removed in 1.12.0. It won't be called starting in 1.11.0 as the configuration is done by the FormatModelRegistry."）。这些方法在新路径下不会被调用，仅当有人仍使用旧 `BaseFileWriterFactory` 时才走它们。
- 新增 `newPositionDeleteWriter(file, spec, partition)` 覆写：若 `positionDeleteRowSchema == null` 直接 `super.newPositionDeleteWriter(...)`（走新 FormatModel 路径）；否则 `LOG.warn("Deprecated feature used. Position delete row schema is used...")`，然后按 `format`（AVRO/ORC/PARQUET）分别用 `Avro.writeDeletes`/`ORC.writeDeletes`/`Parquet.writeDeletes` + `createWriterFunc(DataWriter::create | GenericOrcWriter::buildWriter | GenericParquetWriter::create)` + `rowSchema(positionDeleteRowSchema)` + `withSpec` + `withKeyMetadata` + `buildPositionWriter()` 构造带行数据的位置删除 writer。这段保留了 1.11.0 之前"position delete 携带行数据"特性的兼容，但明确标记为废弃。

### `data/src/main/java/org/apache/iceberg/data/BaseFileWriterFactory.java` (+7/-8 lines)

**修改目的**：把旧基类整体标记为废弃。

**工作逻辑**：类上加 `@Deprecated` 注解与 Javadoc（"since version 1.11.0 and will be removed in 1.12.0. Use RegistryBasedFileWriterFactory"）。原先仅针对某个带 `positionDeleteRowSchema` 构造器的 `@Deprecated` Javadoc 被移除（因为整个类都已废弃，单独标记构造器已无意义）。

## 总结

本提交通过引入 `RegistryBasedFileWriterFactory<T, S>` 这个基于 `FormatModelRegistry` 的新基类，并把 `GenericFileWriterFactory` 迁移到该基类，把"格式特定 builder 回调"这一与 Avro/Parquet/ORC 强耦合的旧模型替换为统一的 `FileWriterBuilder` 接口，显著降低了新增格式与新增引擎的耦合成本；同时通过保留 legacy `positionDeleteRowSchema` 覆写路径与标记旧 `BaseFileWriterFactory` 为 `@Deprecated`（1.12.0 移除），保证过渡期向后兼容。这是 Iceberg 1.11.0 FormatModel API 迁移的第一步，为 3272（Flink）/3274（Spark）提交的引擎迁移提供了可复用的范本。
