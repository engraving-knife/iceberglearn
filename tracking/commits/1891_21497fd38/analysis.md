# 提交 1891：Use InternalData with Avro for readers. (#12476)

## 提交信息

- **序号**：1891 / 4088
- **哈希**：21497fd38521959ce04d7fde7d823a896a2f565c
- **短哈希**：21497fd38
- **日期**：2025-03-20 11:59:34 -0700
- **作者**：Daniel Weeks
- **提交说明**：Use InternalData with Avro for readers. (#12476)
- **PR/Issue**：#12476

## 总体目的

这个提交将 Iceberg 的 manifest 文件和 manifest list 文件的读取路径从直接使用 `Avro` API 切换为使用统一的 `InternalData` 抽象层。这是 Iceberg 内部读取器架构统一化的一部分，旨在让所有文件格式（Avro、Parquet、ORC）的读取都通过 `InternalData` 这个统一入口进行。

在此之前，manifest 文件和 manifest list 文件的读取直接调用 `Avro.read(...)` 构建读取器，并使用 `rename()` 方法将 Avro schema 中的名称映射到 Java 类。这种方式有两个问题：一是 Avro 特有的 API 耦合在了核心读取路径中，不利于后续支持多种文件格式的 manifest 读取；二是使用 `rename("r508", ...)` 这种基于字段 ID 的名称映射方式较为脆弱。

`InternalData.read(FileFormat, inputFile)` 提供了一个格式无关的读取入口，通过 `setRootType` 和 `setCustomType` 来指定 Java 类型映射，使读取逻辑更加清晰且可扩展到其他文件格式。

## 如何达成设计目的

整体设计思路是将 `Avro.read()` 调用替换为 `InternalData.read(FileFormat.AVRO, ...)`，并用 `setRootType` / `setCustomType` 替代原来的 `rename()` 调用。具体涉及三个读取入口：

1. `ManifestLists.read()`：读取 manifest list 文件（即快照对应的 manifest 文件列表）
2. `AllManifestsTable`：metadata 表中读取所有 manifest 文件
3. `ManifestReader.readMetadata()`：读取 manifest 文件头部元数据

同时在 `ManifestFile` 接口中新增了 `PARTITION_SUMMARIES_ELEMENT_ID` 常量（值为 508），替代硬编码的 `508`，使代码更可读且便于在 `setCustomType` 中引用。

## 修改详情

### `api/src/main/java/org/apache/iceberg/ManifestFile.java` (修改, +4/-1 lines)

**修改目的**：提取 manifest 文件 schema 中分区摘要列表元素的字段 ID 为常量。

**工作逻辑**：新增常量 `PARTITION_SUMMARIES_ELEMENT_ID = 508`，并在 `PARTITIONS` 字段定义中将硬编码的 `508` 替换为该常量引用。这个常量后续在 `setCustomType` 调用中用于指定分区摘要字段的 Java 类型映射。

### `core/src/main/java/org/apache/iceberg/AllManifestsTable.java` (修改, +11/-7 lines)

**修改目的**：将 manifest list 读取从 Avro API 切换到 InternalData API。

**工作逻辑**：将 `Avro.read(io.newInputFile(manifestListLocation))` 替换为 `InternalData.read(FileFormat.AVRO, io.newInputFile(manifestListLocation))`。原来的三个 `rename()` 调用（分别映射 `manifest_file`、`partitions`、`r508` 到对应 Java 类）被替换为 `setRootType(GenericManifestFile.class)` 和 `setCustomType(ManifestFile.PARTITION_SUMMARIES_ELEMENT_ID, GenericPartitionFieldSummary.class)`。同时移除了 `classLoader` 和 `reuseContainers` 的设置。

### `core/src/main/java/org/apache/iceberg/ManifestLists.java` (修改, +11/-7 lines)

**修改目的**：与 `AllManifestsTable` 相同的修改，将 manifest list 读取切换到 InternalData。

**工作逻辑**：将 `Avro.read(manifestList)` 替换为 `InternalData.read(FileFormat.AVRO, manifestList)`，使用 `setRootType` 和 `setCustomType` 替代 `rename()` 调用，移除 `classLoader` 和 `reuseContainers` 设置。

### `core/src/main/java/org/apache/iceberg/ManifestReader.java` (修改, +14/-5 lines)

**修改目的**：将 manifest 文件头部元数据读取切换到 InternalData，同时保留对 Avro 特有元数据的访问。

**工作逻辑**：将 `Avro.read(inputFile)` 替换为 `InternalData.read(FileFormat.AVRO, inputFile)`。由于 `InternalData.read` 返回的是 `CloseableIterable` 而非 `AvroIterable`，而代码需要读取 Avro 文件的元数据（`headerReader.getMetadata()`），因此添加了类型检查：如果返回的 reader 是 `AvroIterable` 实例，则调用 `getMetadata()`；否则抛出异常。这保留了 Avro 特有元数据的访问能力，同时使用统一的读取入口。

## 总结

本提交通过将 manifest 和 manifest list 的 Avro 读取路径迁移到 `InternalData` 统一抽象层，减少了 Avro API 在核心代码中的直接耦合，为后续支持多文件格式的 manifest 读取奠定了基础。同时提取了字段 ID 常量提升代码可读性。
