# 提交 3254：Orc, Data: Implementation of ORCFormatModel (#15255)

## 提交信息

- **序号**：3254 / 4088
- **哈希**：83fe4ffc580cde897eab7c7471d2dc7860e204bf
- **短哈希**：83fe4ffc5
- **日期**：2026-02-15
- **作者**：pvary
- **提交说明**：Orc, Data: Implementation of ORCFormatModel (#15255)
- **PR/Issue**：#15255

## 总体目的

继 Avro（#15254，序号 3245）和 Parquet（#15253，序号 3253）之后，本次提交为 ORC 格式实现 `ORCFormatModel`，完成 FormatModel 抽象层在 Iceberg 三大核心文件格式上的全覆盖。FormatModel 抽象层的目标是将文件格式特定的读写逻辑从引擎集成代码中解耦，使 Spark、Flink 等引擎通过统一的 `FormatModelRegistry` 获取格式模型，以标准化 API 完成文件读写，无需直接依赖各格式特定的 builder API。

ORC 格式的 FormatModel 实现面临两个与 Parquet 不同的特殊挑战。首先是加密支持：Parquet 原生支持文件级加密（通过 `withFileEncryptionKey` 和 `withAADPrefix`），而 ORC 不支持文件加密，因此 `ORCFormatModel.WriteBuilderWrapper` 对这两个方法直接抛出 `UnsupportedOperationException`。其次是常量字段处理：ORC 读取时需要从 schema 中排除常量字段和元数据列（避免读取不存在的物理列），因此 `ORC.ReadBuilder` 新增了 `constantFieldIds` 设置，并在 `build()` 时通过 `TypeUtil.selectNot` 从投影 schema 中移除这些字段。

此外，ORC 的 reader function 签名与 Parquet 不同——ORC 的 reader function 只接收 `TypeDescription`（ORC schema）而非同时接收 Iceberg schema，因此 `ReadBuilderWrapper` 需要在 `project()` 时暂存 `icebergSchema`，在 `build()` 时通过闭包传入 reader function。

## 如何达成设计目的

新建 `ORCFormatModel` 类继承 `BaseFormatModel`，通过 `WriteBuilderWrapper` 和 `ReadBuilderWrapper` 内部类适配既有的 `ORC.WriteBuilder` 和 `ORC.ReadBuilder`。在 `data` 模块注册 ORC 格式模型，绑定 `GenericOrcWriter` 和 `GenericOrcReader`。修改 `ORC.java` 开放内部 API 可见性并新增常量字段 ID 处理逻辑。

## 修改详情

### `orc/src/main/java/org/apache/iceberg/orc/ORCFormatModel.java` (+311 lines，新增文件)

**修改目的**：实现 ORC 格式的 FormatModel，提供统一的读写构建器 API。

**工作逻辑**：
该类继承 `BaseFormatModel<D, S, OrcRowWriter<?>, R, TypeDescription>`，泛型 `TypeDescription` 为 ORC 的 schema 类型。提供三个静态工厂方法，与 `ParquetFormatModel` 结构对称：`forPositionDeletes()`、`create(Class, Class, WriterFunction, ReaderFunction)`（行式读取）和 `create(Class, Class, ReaderFunction)`（批量读取，`isBatchReader=true`）。

`WriteBuilderWrapper` 包装 `ORC.WriteBuilder`，`build()` 时根据 `FileContent` 分支：DATA 和 EQUALITY_DELETES 调用 `writerFunction.write(icebergSchema, typeDescription, engineSchema)`；POSITION_DELETES 校验不传入 schema，使用 `GenericOrcWriter.buildWriter` 创建 writer 后包装为 `GenericOrcWriters.positionDelete(...)`，并设置 `DeleteSchemaUtil.pathPosSchema()`。与 Parquet 不同的是，`withFileEncryptionKey(ByteBuffer)` 和 `withAADPrefix(ByteBuffer)` 直接抛出 `UnsupportedOperationException("ORC does not support file encryption keys")`，反映 ORC 格式不支持文件级加密的事实。同时 `WRITER_VERSION_KEY` 处理也不存在（ORC 无对应概念）。

`ReadBuilderWrapper` 包装 `ORC.ReadBuilder`，相比 Parquet 版本有两处关键差异：第一，`project(Schema)` 方法除了调用 `internal.project(schema)` 外，还暂存 `this.icebergSchema = schema`，因为 ORC 的 reader function 只接收 `TypeDescription`，需要在外层闭包中保留 Iceberg schema 以便传入 `ReaderFunction.read(icebergSchema, typeDescription, engineSchema, idToConstant)`；第二，`idToConstant(Map)` 方法调用 `internal.constantFieldIds(newIdToConstant.keySet())` 将常量字段 ID 传入底层 ORC builder，使其在构建时从 schema 中排除这些字段。`build()` 方法中新增 `Preconditions.checkNotNull(reuseContainers, "Reuse containers is required for ORC read")` 断言（此处 `reuseContainers` 是 boolean 字段，此检查实际总是通过，可能是预留扩展）。

### `orc/src/main/java/org/apache/iceberg/orc/ORC.java` (+21/-6 lines)

**修改目的**：开放内部 API 供 FormatModel 包装层访问，并新增常量字段 ID 排除逻辑。

**工作逻辑**：
三处关键修改：

1. `WriteBuilder.createContextFunc` 和 `WriteBuilder.Context` 的可见性从 `private` 提升为 package-private，使 `ORCFormatModel.WriteBuilderWrapper` 能设置上下文并访问 `Context.dataContext()` / `Context.deleteContext()`。注释也从大写 "Supposed" 改为小写 "supposed"（风格规范化）。

2. `ReadBuilder` 新增字段 `Set<Integer> constantFieldIds = ImmutableSet.of()` 和设置方法 `constantFieldIds(Set<Integer>)`。在 `build()` 方法中，将原本直接传入的 `schema` 替换为 `TypeUtil.selectNot(schema, Sets.union(constantFieldIds, MetadataColumns.metadataFieldIds()))`，即从投影 schema 中移除常量字段 ID 和元数据列（如 `_file`、`_pos` 等 Iceberg 内部列）。这确保 ORC 读取器不会尝试读取这些在物理 ORC 文件中不存在的列，常量值由上层通过 `idToConstant` 注入。

3. 新增 `MetadataColumns`、`ImmutableSet`、`Sets`、`TypeUtil` 的 import 以支持上述 schema 过滤逻辑。

### `data/src/main/java/org/apache/iceberg/data/GenericFormatModels.java` (+14 lines)

**修改目的**：在格式模型注册表中注册 ORC 格式的通用读写器。

**工作逻辑**：
在 Parquet 格式模型注册之后，新增两处注册：第一处通过 `ORCFormatModel.create(Record.class, Void.class, ...)` 注册数据文件的 ORC 格式模型，writer function 调用 `GenericOrcWriter.buildWriter(icebergSchema, fileSchema)`，reader function 调用 `GenericOrcReader.buildReader(icebergSchema, fileSchema, idToConstant)`；第二处通过 `ORCFormatModel.forPositionDeletes()` 注册位置删除文件的格式模型。与 Parquet 注册结构完全对称。

### `data/src/test/java/org/apache/iceberg/data/TestGenericFormatModels.java` (+2/-1 lines)

**修改目的**：将 ORC 格式纳入格式模型测试覆盖范围。

**工作逻辑**：
将 `FILE_FORMATS` 数组从 `{FileFormat.AVRO, FileFormat.PARQUET}` 扩展为 `{FileFormat.AVRO, FileFormat.PARQUET, FileFormat.ORC}`，使格式模型读写测试自动覆盖 ORC 格式。至此三大核心格式（Avro、Parquet、ORC）全部纳入 FormatModel 测试矩阵。

## 总结

本次提交实现了 ORC 格式的 FormatModel 抽象层落地，新建 `ORCFormatModel` 类将 ORC 读写构建器封装为统一 `BaseFormatModel` 接口，正确处理了 ORC 不支持文件加密的特性（抛出 `UnsupportedOperationException`）以及常量字段排除需求（通过 `TypeUtil.selectNot` 从 schema 中移除）。同时扩展了 `ORC.ReadBuilder` 支持常量字段 ID 设置。至此 Iceberg 三大核心文件格式（Avro、Parquet、ORC）的 FormatModel 抽象层全部完成，为引擎通过统一 API 操作所有格式奠定了基础。
