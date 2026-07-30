# 提交 1726：Core: Add InternalData read and write builders (#12060)

## 提交信息

- **序号**：1726 / 4088
- **哈希**：b8fdd847f57b33fb8202cd6636cfd1d38f855684
- **短哈希**：b8fdd847f
- **日期**：2025-02-13 11:36:41 -0800
- **作者**：Ryan Blue
- **提交说明**：Core: Add InternalData read and write builders (#12060)
- **PR/Issue**：#12060

## 总体目的

引入 `InternalData` 抽象层，为 Iceberg 内部数据文件（如 manifest 文件、manifest list 文件等元数据文件）的读写提供统一的构建器（builder）接口。此前，内部元数据文件的读写直接依赖于 Avro 格式的 `Avro.write()` 和 `Avro.read()` API，导致元数据读写与 Avro 格式紧耦合。

通过引入 `InternalData` 抽象层，实现以下目标：

1. **格式无关的内部数据读写**：通过统一的 `InternalData.write(format, file)` 和 `InternalData.read(format, file)` API，根据文件格式自动选择对应的读写器。目前支持 Avro 和 Parquet 两种格式。
2. **支持 Parquet 格式的元数据文件**：为将来使用 Parquet 格式存储元数据文件（如 manifest 文件）奠定基础，Parquet 格式在列式存储和压缩方面优于 Avro。
3. **统一的自定义类型支持**：通过 `ReadBuilder.setRootType()` 和 `ReadBuilder.setCustomType()` 接口，统一了不同格式的自定义类型映射方式，不再需要每种格式单独实现。
4. **模块化解耦**：Parquet 模块的注册通过动态加载（`DynMethods`）实现，Core 模块不直接依赖 Parquet 模块，保持模块间的松耦合。

## 如何达成设计目的

通过以下层次的设计来实现目标：

1. **新增 `InternalData` 类**：定义 `WriteBuilder` 和 `ReadBuilder` 接口，以及格式注册机制。各文件格式通过 `register()` 方法注册自己的构建器工厂函数。
2. **新增 `InternalParquet` 类**：在 Parquet 模块中注册 Parquet 格式的读写构建器，使用动态方法调用避免 Core 模块对 Parquet 模块的编译时依赖。
3. **修改 `Parquet.WriteBuilder` 和 `Parquet.ReadBuilder`**：实现 `InternalData.WriteBuilder` 和 `InternalData.ReadBuilder` 接口，使 Parquet 的构建器符合统一 API。
4. **修改 `Avro.WriteBuilder` 和 `Avro.ReadBuilder`**：同样实现 `InternalData` 接口，并增加自定义类型支持。
5. **新增 `SupportsCustomTypes` 接口**：统一自定义类型设置方式，Avro 的 `InternalReader` 实现此接口。
6. **修改 Manifest 读写器**：将直接调用 `Avro.write()/read()` 改为通过 `InternalData.write()/read()`，并使用新的 `setRootType/setCustomType` API。
7. **重命名元数据包装类**：将 `V1/V2/V3Metadata` 中的 `IndexedManifestFile` 重命名为 `ManifestFileWrapper`，`IndexedManifestEntry` 重命名为 `ManifestEntryWrapper`，使命名更清晰。

## 修改详情

### `core/src/main/java/org/apache/iceberg/InternalData.java`（新增, +169 lines）

**修改目的**：创建内部数据读写的统一抽象层。

**工作逻辑**：
- 定义 `WriteBuilder` 接口，包含 `schema()`, `named()`, `set()`, `meta()`, `overwrite()`, `build()` 等方法。
- 定义 `ReadBuilder` 接口，包含 `project()`, `split()`, `reuseContainers()`, `setRootType()`, `setCustomType()`, `build()` 等方法。
- 使用 `Map<FileFormat, Function<OutputFile, WriteBuilder>>` 和 `Map<FileFormat, Function<InputFile, ReadBuilder>>` 存储已注册的格式构建器。
- `registerSupportedFormats()` 方法注册 Avro 格式，并通过 `DynMethods` 动态加载 Parquet 模块的 `InternalParquet.register()` 方法（如果 Parquet 模块在 classpath 中）。
- 静态初始化块中调用 `registerSupportedFormats()` 完成注册。

### `parquet/src/main/java/org/apache/iceberg/InternalParquet.java`（新增, +42 lines）

**修改目的**：注册 Parquet 格式的内部数据读写构建器。

**工作逻辑**：`register()` 方法调用 `InternalData.register()` 注册 Parquet 格式的 `WriteBuilder`（使用 `InternalWriter::create`）和 `ReadBuilder`（使用 `InternalReader::create`）。通过 `Parquet.write()` 和 `Parquet.read()` 创建构建器，并设置对应的写入/读取函数。

### `parquet/src/main/java/org/apache/iceberg/parquet/Parquet.java`（修改, +61/-14 lines）

**修改目的**：使 Parquet 的构建器实现 `InternalData` 接口。

**工作逻辑**：
- `WriteBuilder` 类声明实现 `InternalData.WriteBuilder`，为接口方法添加 `@Override` 注解。
- `write()` 方法增加对 `EncryptedOutputFile` 的类型检查和委托。
- `ReadBuilder` 类声明实现 `InternalData.ReadBuilder`，为接口方法添加 `@Override` 注解。
- 新增 `createReaderFunc(BiFunction<Schema, MessageType, ParquetValueReader<?>>)` 方法，支持带 Schema 参数的读取函数（用于自定义类型映射）。
- 实现 `setRootType()` 和 `setCustomType()` 方法（当前抛出 `UnsupportedOperationException`，为后续支持预留）。
- `build()` 方法中处理新的 `readerFuncWithSchema`，将其适配为单参数的 `readerFunc`。
- 修改了 `createReaderFunc` 和 `createBatchedReaderFunc` 的互斥校验逻辑，增加对 `readerFuncWithSchema` 的检查。

### `core/src/main/java/org/apache/iceberg/avro/Avro.java`（修改, +39/-4 lines）

**修改目的**：使 Avro 的构建器实现 `InternalData` 接口，并支持自定义类型。

**工作逻辑**：
- `WriteBuilder` 实现 `InternalData.WriteBuilder`，添加 `@Override` 注解。
- `write()` 方法增加对 `EncryptedOutputFile` 的类型检查和委托。
- `ReadBuilder` 实现 `InternalData.ReadBuilder`，添加 `@Override` 注解。
- 新增 `typeMap` 和 `rootType` 字段，实现 `setRootType()` 和 `setCustomType()` 方法。
- `build()` 方法中，如果 reader 实现了 `SupportsCustomTypes`，则调用 `setCustomTypes()` 传递自定义类型映射。

### `core/src/main/java/org/apache/iceberg/avro/SupportsCustomTypes.java`（新增, +28 lines）

**修改目的**：定义自定义类型支持接口。

**工作逻辑**：声明 `setCustomTypes(Class<? extends StructLike> rootType, Map<Integer, Class<? extends StructLike>> typesById)` 方法，用于通过字段 ID 设置自定义 Java 类型映射。与 `SupportsCustomRecords`（按名称设置）互补，此接口按 ID 设置。

### `core/src/main/java/org/apache/iceberg/avro/InternalReader.java`（修改, +11/-1 lines）

**修改目的**：实现 `SupportsCustomTypes` 接口。

**工作逻辑**：`InternalReader` 类声明实现 `SupportsCustomTypes`，新增 `setCustomTypes()` 方法，内部调用已有的 `setRootType()` 和 `setCustomType()` 方法完成类型映射设置。

### `core/src/main/java/org/apache/iceberg/avro/SupportsCustomRecords.java`（修改, +1/-1 line）

**修改目的**：更新接口文档注释。

**工作逻辑**：将注释从 "support custom record classes" 改为 "support custom record classes by name"，以区分新增的 `SupportsCustomTypes`（按 ID）。

### `core/src/main/java/org/apache/iceberg/ManifestReader.java`（修改, +6/-21 lines）

**修改目的**：使用 `InternalData` 替代直接调用 Avro API 读取 manifest 文件。

**工作逻辑**：将原来 switch-case 分支（仅支持 AVRO 格式）替换为统一的 `InternalData.read(format, file)` 调用。使用 `setRootType(GenericManifestEntry.class)` 和 `setCustomType()` 设置自定义类型，不再需要单独的 `newReader()` 方法创建 `DatumReader`。代码从 21 行减少到 6 行，更简洁且格式无关。

### `core/src/main/java/org/apache/iceberg/ManifestWriter.java`（修改, +12/-15 lines）

**修改目的**：使用 `InternalData` 替代直接调用 Avro API 写入 manifest 文件。

**工作逻辑**：将 V1/V2/V3Writer 的 `newAppendWriter()` 方法中 `Avro.write(file)` 替换为 `InternalData.write(FileFormat.AVRO, file)`。将 `IndexedManifestEntry` 重命名为 `ManifestEntryWrapper`，并简化构造参数（不再需要传递 `spec.partitionType()`）。

### `core/src/main/java/org/apache/iceberg/ManifestListWriter.java`（修改, +8/-9 lines）

**修改目的**：使用 `InternalData` 替代直接调用 Avro API 写入 manifest list 文件。

**工作逻辑**：将 V1/V2/V3Writer 的 `newAppender()` 方法中 `Avro.write(file)` 替换为 `InternalData.write(FileFormat.AVRO, file)`。将 `IndexedManifestFile` 重命名为 `ManifestFileWrapper`。

### `core/src/main/java/org/apache/iceberg/V1Metadata.java`（修改, +45/-48 lines）

**修改目的**：重命名内部包装类，适配新的构建器 API。

**工作逻辑**：将 `IndexedManifestFile` 重命名为 `ManifestFileWrapper`，将 `IndexedManifestEntry` 重命名为 `ManifestEntryWrapper`。使用 `Types.NestedField.builder()` 替代 `Types.NestedField.of()` 构建字段，以支持更灵活的字段构建。

### `core/src/main/java/org/apache/iceberg/V2Metadata.java`（修改, +44/-47 lines）

**修改目的**：同 V1Metadata，重命名包装类并适配新 API。

**工作逻辑**：与 V1Metadata 相同的重命名和构建器适配。

### `core/src/main/java/org/apache/iceberg/V3Metadata.java`（修改, +44/-47 lines）

**修改目的**：同 V1/V2Metadata，重命名包装类并适配新 API。

**工作逻辑**：与 V1/V2Metadata 相同的重命名和构建器适配。`ManifestEntryWrapper` 构造简化，不再需要 `partitionType` 参数。

## 小结

- **成效**：成功引入了 `InternalData` 抽象层，使 Iceberg 内部元数据文件的读写不再与 Avro 格式紧耦合。通过注册机制支持 Avro 和 Parquet 两种格式，为后续使用 Parquet 存储元数据文件奠定了基础。同时统一了自定义类型的设置方式，简化了 ManifestReader/Writer 的代码。
- **影响范围**：影响 Core 模块的元数据读写路径（ManifestReader、ManifestWriter、ManifestListWriter）和 Parquet 模块的构建器 API。这是一个架构层面的重构，但对外部 API 无影响。
- **回迁到 1.4.x 的注意事项**：这是一个较大的架构重构，回迁到 1.4.x 需要谨慎评估。需要确认 1.4.x 分支中相关文件（ManifestReader、ManifestWriter、Avro、Parquet 等）的代码结构是否与 main 分支兼容。由于涉及多模块（core、parquet）的协调修改，且依赖 `InternalWriter` 和 `InternalReader`（parquet 模块）的存在，回迁需要同时处理多个文件的变更。建议仅在 1.4.x 需要支持 Parquet 格式元数据文件时才回迁，否则可能引入不必要的复杂性和风险。如果仅为了 bug 修复，不建议回迁此架构性变更。
