# 提交 3275：Flink: Backport moving Flink to use the new FormatModel API (#15354)

## 提交信息

- **序号**：3275 / 4088
- **哈希**：ccdf23ca25ce16984684884b36fb94096ccda096
- **短哈希**：ccdf23ca2
- **日期**：2026-02-18
- **作者**：pvary
- **提交说明**：Flink: Backport moving Flink to use the new FormatModel API (#15354)
- **PR/Issue**：#15354（回移自 #15329）

## 总体目的

本提交是 3272 提交（PR #15329，把 Flink 迁移到新 FormatModel API）的回移（backport）。3272 只迁移了最新的 Flink 2.1 适配器（`flink/v2.1/flink/`），而 Iceberg 同时维护多个 Flink 版本的适配器——本提交把同样的迁移应用到 Flink 1.20（`flink/v1.20/flink/`）与 Flink 2.0（`flink/v2.0/flink/`）两个较旧的适配器目录。

回移的根本原因是：Iceberg 的 `core` 模块中的 `FormatModelRegistry` 通过一个按类名字符串反射加载的 `CLASSES_TO_REGISTER` 列表来注册各引擎的格式模型，3272 已把 `"org.apache.iceberg.flink.data.FlinkFormatModels"` 加入该列表。这个类名在三个 Flink 版本目录下都相同（包名与类名一致），所以当 classpath 上是 Flink 1.20 或 2.0 时，registry 会去加载对应目录下的 `FlinkFormatModels` 类。如果只在 2.1 目录新增该类而 1.20/2.0 目录没有，那么使用旧 Flink 版本时 registry 反射加载会失败（`ClassNotFoundException`），导致读写功能不可用。因此必须把同样的迁移同步到所有受支持的 Flink 版本目录。

通过比对 blob 哈希可以确认：三个 Flink 版本目录下的 `FlinkFormatModels.java`、`FlinkFileWriterFactory.java`、`RowDataFileScanTaskReader.java`、`TestCompressionSettings.java`、`TestDynamicWriter.java` 内容完全一致（逐字节相同）。这是一次纯机械式回移，不涉及任何逻辑改动，也不需要再修改 `core/FormatModelRegistry.java`（注册入口已在 3272 中加好）。

## 如何达成设计目的

整体思路就是把 3272 在 `flink/v2.1/flink/` 下新增/修改的 5 个文件，原样复制到 `flink/v1.20/flink/` 与 `flink/v2.0/flink/` 两个对应目录下。每个 Flink 版本目录有独立的源码树（`src/main/java/...`、`src/test/java/...`），文件路径除了版本段（`v1.20`/`v2.0`/`v2.1`）外完全一致。涉及 10 个文件改动（两个版本 × 5 个文件）。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/data/FlinkFormatModels.java` (+58/-0 lines，新文件)

**修改目的**：为 Flink 1.20 适配器提供与 2.1 相同的格式模型注册类。

**工作逻辑**：与 3272 在 `flink/v2.1/` 下新增的 `FlinkFormatModels` 完全相同。`register()` 方法注册三个 `FormatModel`：Parquet + `RowData`/`RowType`（writer 用 `FlinkParquetWriters.buildWriter(engineSchema, fileSchema)`，reader 用 `FlinkParquetReaders.buildReader(icebergSchema, fileSchema, idToConstant)`）；Avro + `RowData`/`RowType`（writer 用 `new FlinkAvroWriter(engineSchema)`，reader 用 `FlinkPlannedAvroReader.create(icebergSchema, idToConstant)`）；ORC + `RowData`/`RowType`（writer 用 `FlinkOrcWriter.buildWriter(engineSchema, icebergSchema)`，reader 用 `new FlinkOrcReader(icebergSchema, fileSchema, idToConstant)`）。私有构造器防止实例化。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkFileWriterFactory.java` (+13/-85 lines)

**修改目的**：让 Flink 1.20 写工厂走新 FormatModel API。

**工作逻辑**：与 3272 改动一致。类签名由 `extends BaseFileWriterFactory<RowData>` 改为 `extends RegistryBasedFileWriterFactory<RowData, RowType>`；构造器向 `super(...)` 透传 `RowData.class` 与通过 `equalityDeleteInputSchema(...)` 计算的引擎 `RowType`；删除 9 个 `configureXxx` 回调与 `dataFlinkType()/equalityDeleteFlinkType()` 惰性方法；新增私有静态 `equalityDeleteInputSchema(RowType, Schema)`。内容与 v2.1 版本逐字节相同。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/source/RowDataFileScanTaskReader.java` (+8/-104 lines)

**修改目的**：让 Flink 1.20 读路径走新 FormatModel API。

**工作逻辑**：与 3272 改动一致。`readTask` 中 `switch (PARQUET/AVRO/ORC)` 与 `newAvroIterable`/`newParquetIterable`/`newOrcIterable` 三个私有方法替换为 `FormatModelRegistry.readBuilder(format, RowData.class, inputFile)` + 链式 `.project(schema).idToConstant(idToConstant).split(...).caseSensitive(...).filter(task.residual()).reuseContainers().build()`，nameMapping 单点处理。删除 Avro/Parquet/ORC/各 Flink reader 类的 import。内容与 v2.1 版本逐字节相同。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/TestCompressionSettings.java` (+5/-5 lines)

**修改目的**：更新反射访问以匹配新基类与泛型签名。

**工作逻辑**：与 3272 改动一致。反射字段访问目标类由 `BaseFileWriterFactory` 改为 `RegistryBasedFileWriterFactory`；`DynFields.BoundField<IcebergStreamWriter>`、`<TaskWriter>`、`<FileWriterFactory>` 改为带泛型 `<?>` 的形式。内容与 v2.1 版本逐字节相同。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicWriter.java` (+3/-3 lines)

**修改目的**：同上，更新反射访问。

**工作逻辑**：与 3272 改动一致。目标类由 `BaseFileWriterFactory` 改为 `RegistryBasedFileWriterFactory`，`DynFields.BoundField<FileWriterFactory>` 改为 `<FileWriterFactory<?>>`。内容与 v2.1 版本逐字节相同。

### `flink/v2.0/flink/...` 下 5 个文件（与 v1.20 完全相同）

**修改目的**：为 Flink 2.0 适配器同步上述迁移。

**工作逻辑**：5 个文件（`FlinkFormatModels.java`、`FlinkFileWriterFactory.java`、`RowDataFileScanTaskReader.java`、`TestCompressionSettings.java`、`TestDynamicWriter.java`）的改动与 v1.20 完全相同（逐字节一致），只是位于 `flink/v2.0/flink/` 目录树下。这样 Flink 2.0 适配器也走新 FormatModel API。

## 总结

本提交是 3272（PR #15329）的纯机械式回移，把 Flink 适配器向新 FormatModel API 的迁移同步到 Flink 1.20 与 Flink 2.0 两个较旧版本目录。由于 `FormatModelRegistry` 的注册入口已通过类名字符串反射加载，三个 Flink 版本目录下的对应类内容完全一致，回移不需要任何逻辑改动，只保证旧 Flink 版本下 registry 能成功反射加载到 `FlinkFormatModels` 类。这是多版本引擎适配器同步维护的常规操作，确保 1.11.0 的 FormatModel 重构对所有受支持的 Flink 版本生效。
