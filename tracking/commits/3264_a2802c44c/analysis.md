# 提交 3264：Data, MR: Moving other reader usages to the new FormatModel API (#15333)

## 提交信息

- **序号**：3264 / 4088
- **哈希**：a2802c44cebf0d7cb2d4c514e60d662d380853f6
- **短哈希**：a2802c44c
- **日期**：2026-02-16
- **作者**：pvary
- **提交说明**：Data, MR: Moving other reader usages to the new FormatModel API (#15333)
- **PR/Issue**：#15333

## 总体目的

紧随 #15258（提交 3263）为 Arrow 向量化读取引入 `ArrowFormatModel` 并将 `ArrowReader` 迁移到 `FormatModelRegistry` 之后，本次提交继续推进 FormatModel 抽象的统一收敛工作，把 data 模块与 mr（MapReduce）模块中剩余的三处直接依赖具体格式 API（`Avro.read`/`Parquet.read`/`ORC.read`）的读取入口迁移到统一的 `FormatModelRegistry.readBuilder(...)` API。

这三处分别是：`BaseDeleteLoader`（读取删除文件，用于 equality/position delete 的处理）、`GenericReader`（generic 数据模型 `Record` 的数据文件读取，被 `GenericTableScan` 等使用）、以及 `IcebergInputFormat`（Hadoop MapReduce 输入格式，为 MR 任务读取 Iceberg 表数据）。它们原先都包含一段结构高度相似的 `switch (format) { case AVRO/PARQUET/ORC: ... }` 代码块，每个 case 分支针对该格式调用专属的 `ReadBuilder`（如 `Avro.read(input).project(...).createResolvingReader(...)`、`Parquet.read(input).project(...).createReaderFunc(...)`、`ORC.read(input).project(...).createReaderFunc(...)`），并在其中嵌入格式特有逻辑——例如 ORC 需要先用 `TypeUtil.selectNot` 把分区常量字段与元数据字段从投影 schema 中剔除（`projectionWithoutConstantAndMetadataFields`），各格式还要把分区常量 map 以不同方式注入 reader function。这种"每处调用点、每种格式各写一遍"的模式导致了大量重复代码，且格式特有知识散落在各个调用方，新增格式或调整读取选项时需要多处同步修改，维护成本高、易出错。

本次迁移后，三个调用点都改为通过 `FormatModelRegistry.readBuilder(format, Record.class, inputFile)` 获取统一的 `ReadBuilder<Record, ?>`，再以相同的链式 API（`project`/`split`/`caseSensitive`/`filter`/`reuseContainers`/`withNameMapping`/`idToConstant`）完成配置。分区常量不再在各格式 reader function 中分别注入，而是统一通过 `ReadBuilder.idToConstant(map)` 传入；ORC 对常量/元数据字段的特殊投影逻辑被下沉到 ORC 的 `ReadBuilderWrapper` 内部（通过 `internal.constantFieldIds(...)` 处理），调用方不再感知。这消除了三处重复的 switch-case 与多个 per-format 辅助方法，净减少约 178 行代码（+42/-220），使读取入口与 generic/arrow 模型走完全一致的注册式构建路径，FormatModel 抽象的统一性基本达成。

## 如何达成设计目的

对三个文件逐一将 `switch(format)` 块替换为单次 `FormatModelRegistry.readBuilder(format, Record.class, inputFile)` 调用加链式配置，删除因此变为冗余的 per-format 辅助方法（`newParquetReaderFunc`/`newOrcReaderFunc`、`newAvroIterable`/`newParquetIterable`/`newOrcIterable`/`constantsMap`）及其相关的格式专属 import（`Avro`/`Parquet`/`ORC`/`GenericParquetReaders`/`GenericOrcReader`/`PlannedDataReader`/`TypeUtil`/`Sets`/`MetadataColumns`/`PartitionUtil` 等），替换为 `FormatModelRegistry` 与 `ReadBuilder` 的 import。分区常量的传递统一改用 `ReadBuilder.idToConstant(...)`。

## 修改详情

### `data/src/main/java/org/apache/iceberg/data/BaseDeleteLoader.java` (+4/-49 lines)

**修改目的**：将删除文件读取入口迁移到统一 FormatModel API，消除 per-format switch 分支。

**工作逻辑**：
原 `readDeletes` 末尾根据 `format` 分三路：AVRO 用 `Avro.read(inputFile).project(projection).reuseContainers().createResolvingReader(PlannedDataReader::create).build()`；PARQUET 用 `Parquet.read(...).project(...).filter(filter).reuseContainers().createReaderFunc(newParquetReaderFunc(projection)).build()`；ORC 用 `ORC.read(...).project(...).filter(filter).createReaderFunc(newOrcReaderFunc(projection)).build()`（ORC 注释说明其自动复用容器故不调 reuseContainers）。另有两个私有方法 `newParquetReaderFunc`/`newOrcReaderFunc` 分别返回 `fileSchema -> GenericParquetReaders.buildReader(projection, fileSchema)` 与 `fileSchema -> GenericOrcReader.buildReader(projection, fileSchema)` 的 reader function。

迁移后整段替换为：
```java
ReadBuilder<Record, ?> builder =
    FormatModelRegistry.readBuilder(format, Record.class, inputFile);
return builder.project(projection).reuseContainers().filter(filter).build();
```
统一调用 `reuseContainers()`（ORC 的 `ReadBuilderWrapper` 内部会按需处理），`filter` 在删除读取中统一传入。两个 reader function 辅助方法被删除——具体的 reader 构造逻辑已由 `GenericFormatModels` 在注册时提供（`GenericParquetReaders.buildReader` / `GenericOrcReader.buildReader`）。相应删除了 `Avro`/`Parquet`/`ORC`/`GenericParquetReaders`/`GenericOrcReader`/`PlannedDataReader`/`MessageType`/`TypeDescription`/`ParquetValueReader`/`OrcRowReader` 等格式专属 import，新增 `FormatModelRegistry`/`ReadBuilder` import。注意此处未传 `idToConstant`，因为删除文件读取不需要分区常量注入。

### `data/src/main/java/org/apache/iceberg/data/GenericReader.java` (+10/-64 lines)

**修改目的**：将 generic 数据文件读取迁移到统一 FormatModel API，并把分区常量注入与 ORC 特殊投影下沉到格式模型。

**工作逻辑**：
原 `open` 方法按 `task.file().format()` 分三路，每路分别构建 Avro/Parquet/ORC 的 `ReadBuilder`：Avro 用 `createResolvingReader(schema -> PlannedDataReader.create(schema, partition))` 注入分区常量；Parquet 用 `createReaderFunc(fileSchema -> GenericParquetReaders.buildReader(fileProjection, fileSchema, partition))` 注入；ORC 则先计算 `projectionWithoutConstantAndMetadataFields = TypeUtil.selectNot(fileProjection, Sets.union(partition.keySet(), MetadataColumns.metadataFieldIds()))`，再 `createReaderFunc(fileSchema -> GenericOrcReader.buildReader(fileProjection, fileSchema, partition))`。`reuseContainers` 仅对 Avro/Parquet 条件调用，ORC 不调用。default 分支抛 `UnsupportedOperationException`。

迁移后整段替换为：
```java
ReadBuilder<Record, ?> builder =
    FormatModelRegistry.readBuilder(task.file().format(), Record.class, input);
if (reuseContainers) {
  builder = builder.reuseContainers();
}
return builder
    .project(fileProjection)
    .idToConstant(partition)
    .split(task.start(), task.length())
    .caseSensitive(caseSensitive)
    .filter(task.residual())
    .build();
```
关键变化：① 分区常量统一通过 `builder.idToConstant(partition)` 传入，由各格式的 `ReadBuilderWrapper` 在 `build()` 时转交给注册的 reader function（其第 4 参数即 `idToConstant`）；② ORC 原先调用方手动做的 `TypeUtil.selectNot`（剔除常量与元数据字段）不再需要，因为 ORC 的 `ReadBuilderWrapper.idToConstant` 内部调用 `internal.constantFieldIds(newIdToConstant.keySet())` 让 ORC.ReadBuilder 自行处理常量字段投影；③ `reuseContainers` 对所有格式统一条件调用，ORC 的 `ReadBuilderWrapper` 内部会正确处理。删除了大量格式专属 import（`Avro`/`Parquet`/`ORC`/`GenericParquetReaders`/`GenericOrcReader`/`PlannedDataReader`/`MetadataColumns`/`Sets`/`TypeUtil`），新增 `FormatModelRegistry`/`ReadBuilder`。

### `mr/src/main/java/org/apache/iceberg/mr/mapreduce/IcebergInputFormat.java` (+28/-107 lines)

**修改目的**：将 Hadoop MapReduce 输入格式的读取入口迁移到统一 FormatModel API，删除三个 per-format iterable 构建方法与 constantsMap 辅助方法。

**工作逻辑**：
原 `openTask` 通过 `switch (file.format())` 分派到 `newAvroIterable`/`newOrcIterable`/`newParquetIterable` 三个私有方法。这三个方法各自构建对应格式的 `ReadBuilder`，处理 `reuseContainers`、`nameMapping`、`createResolvingReader`/`createReaderFunc`（内联注入分区常量），ORC 还做 `TypeUtil.selectNot` 投影，最后统一调用 `applyResidualFiltering(...)`。另有 `constantsMap(task, converter)` 辅助方法用于计算分区常量（仅在投影包含身份分区列时调用 `PartitionUtil.constantsMap`，否则返回空 map）。

迁移后 `openTask` 直接：
```java
ReadBuilder<Record, ?> readBuilder =
    FormatModelRegistry.readBuilder(file.format(), Record.class, inputFile);
if (reuseContainers) {
  readBuilder = readBuilder.reuseContainers();
}
if (nameMapping != null) {
  readBuilder = readBuilder.withNameMapping(NameMappingParser.fromJson(nameMapping));
}
return applyResidualFiltering(
    (CloseableIterable<T>)
        readBuilder
            .project(readSchema)
            .split(currentTask.start(), currentTask.length())
            .caseSensitive(caseSensitive)
            .filter(currentTask.residual())
            .build(),
    currentTask.residual(),
    readSchema);
```
三个 `newXxxIterable` 方法与 `constantsMap` 方法全部删除。注意此处未调用 `idToConstant`——原 MR 路径虽计算了 `constantsMap`，但新代码简化为不传分区常量（`applyResidualFiltering` 与后续 `InternalRecordWrapper` 等处理保持不变）。由于 `ReadBuilder<Record, ?>` 产出 `CloseableIterable<Record>`，而方法签名需要 `CloseableIterable<T>`，使用 `@SuppressWarnings("unchecked")` 的未检查强转（这与原实现中各 per-format 方法返回 `CloseableIterable<T>` 但内部实为 Record iterable 的实际行为一致，T 在 generic 场景即 Record）。`openTask` 方法上新增 `@SuppressWarnings("unchecked")` 注解。同时清理了大量不再使用的 import（`Avro`/`Parquet`/`ORC`/`GenericParquetReaders`/`GenericOrcReader`/`PlannedDataReader`/`IdentityPartitionConverters`/`MetadataColumns`/`PartitionSpec`/`PartitionUtil`/`Sets`/`Type`/`TypeUtil`/`Collections`/`Map`/`Set`/`BiFunction`），新增 `Record`/`FormatModelRegistry`/`ReadBuilder`。

## 总结

本次提交将 data 模块的 `BaseDeleteLoader`、`GenericReader` 与 mr 模块的 `IcebergInputFormat` 三处读取入口统一迁移到 `FormatModelRegistry.readBuilder(...)` API，删除了三处重复的 per-format switch-case 块与多个 per-format 辅助方法，净减少约 178 行代码。分区常量注入统一改用 `ReadBuilder.idToConstant`，ORC 对常量/元数据字段的特殊投影逻辑下沉到格式模型内部。至此 generic 数据模型与 Arrow 模型的所有主要读取入口都已纳入 FormatModel 抽象，显著降低了重复代码与格式专属知识在调用方的散落，完成了 FormatModel 统一化重构的主要迁移工作。
