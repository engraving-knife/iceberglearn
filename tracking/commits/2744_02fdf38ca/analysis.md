# 提交 2744：Flink: Move write from AppenderFactory to FileWriterFactory

## 提交信息

- **序号**：2744 / 4088
- **哈希**：02fdf38cad61a64cce8e01a09d38b300f3fa8635
- **短哈希**：02fdf38ca
- **日期**：2025-10-14 09:28:19 +0200
- **作者**：pvary
- **提交说明**：Flink: Move write from AppenderFactory to FileWriterFactory
- **PR/Issue**：#14271

## 总体目的

在 Iceberg 的写入架构中，存在两个相关但职责不同的接口：`FileAppenderFactory` 和 `FileWriterFactory`。`FileAppenderFactory` 是较旧的接口，负责创建 `FileAppender`（底层追加器），调用方需要自行管理文件元数据（如 DataFile/DeleteFile 的构建）。`FileWriterFactory` 是较新的接口，直接创建 `DataWriter`/`EqualityDeleteWriter`/`PositionDeleteWriter`，这些 writer 内部封装了文件元数据的管理，调用方只需调用 `toDataFile()` / `toDeleteFile()` 即可获取文件元数据。

此前 Flink 的 sink 模块仍使用旧的 `FlinkAppenderFactory`（基于 `FileAppenderFactory`），这导致 Flink 写入路径与其他引擎（如 Spark）的实现不一致，且 `FileAppenderFactory` 缺少对 writer properties（如压缩配置等写属性）的传递能力。

本提交的目的是将 Flink sink 的写入路径从 `AppenderFactory` 迁移到 `FileWriterFactory`，具体包括：
1. 在核心 `BaseTaskWriter` 中新增接受 `FileWriterFactory` 的构造函数，使其同时支持新旧两种工厂
2. 将 `FlinkFileWriterFactory` 的 Builder 从包级私有改为 public，使其可在 Flink sink 中使用
3. 将 `RowDataTaskWriterFactory` 从使用 `FlinkAppenderFactory` 改为使用 `FlinkFileWriterFactory`
4. 为 `BaseFileWriterFactory` 添加 `writerProperties` 支持，使写入属性（如压缩配置）可通过 `FileWriterFactory` 传递
5. 标记 `FlinkAppenderFactory` 为 `@Deprecated`

## 如何达成设计目的

整体设计采用渐进式迁移策略，保持向后兼容：

1. **核心层支持双模式**：`BaseTaskWriter` 新增接受 `FileWriterFactory` 的构造函数，内部通过 null 检查在 `appenderFactory` 和 `writerFactory` 之间切换。`PartitionedFanoutWriter`、`PartitionedWriter`、`UnpartitionedWriter` 也相应添加新构造函数。

2. **增强 FileWriterFactory 能力**：`BaseFileWriterFactory` 新增 `writerProperties` 字段和接受该参数的构造函数，将写入属性传递到 Avro/Parquet/ORC 的 write builder。旧构造函数标记为 `@Deprecated`。同时使 `BaseFileWriterFactory` 实现 `Serializable`（Flink 需要序列化分发 writer factory）。

3. **公开 FlinkFileWriterFactory API**：将 `FlinkFileWriterFactory` 类和其 `Builder` 的构造函数和方法从包级私有改为 `public`，新增 `writerProperties` 配置方法。

4. **迁移 Flink sink 写入路径**：`RowDataTaskWriterFactory` 从创建 `FlinkAppenderFactory` 改为创建 `FlinkFileWriterFactory`，所有 Delta Task Writer 的构造参数从 `FileAppenderFactory` 改为 `FileWriterFactory`。

5. **标记旧接口废弃**：`FlinkAppenderFactory` 标记 `@Deprecated`，注明将在 1.12.0 移除。

6. **测试适配**：测试工具类 `SimpleDataUtil` 新增基于 `FileWriterFactory` 的文件写入方法；`TestFileWriterFactory` 新增序列化测试验证 `FileWriterFactory` 可被正确序列化。

## 修改详情

### `core/src/main/java/org/apache/iceberg/io/BaseTaskWriter.java` (+33/-3 lines)

**修改目的**：核心 TaskWriter 支持 FileWriterFactory。

**工作逻辑**：
- 新增 `writerFactory` 字段和接受 `FileWriterFactory` 的构造函数（旧构造函数中 `writerFactory` 设为 null）
- 在 `RollingFileWriter.newWriter()`、`RollingEqDeleteWriter.newWriter()`、`SortingPositionOnlyDeleteWriter` 的 posDeleteWriter 创建中，通过 `writerFactory != null` 判断使用哪个工厂：新工厂调用 `newDataWriter`/`newEqualityDeleteWriter`/`newPositionDeleteWriter`，旧工厂调用原有方法

### `core/src/main/java/org/apache/iceberg/io/PartitionedFanoutWriter.java` (+10/-0 lines)
**修改目的**：添加接受 FileWriterFactory 的构造函数。

### `core/src/main/java/org/apache/iceberg/io/PartitionedWriter.java` (+10/-0 lines)
**修改目的**：添加接受 FileWriterFactory 的构造函数。

### `core/src/main/java/org/apache/iceberg/io/UnpartitionedWriter.java` (+11/-0 lines)
**修改目的**：添加接受 FileWriterFactory 的构造函数。

### `data/src/main/java/org/apache/iceberg/data/BaseFileWriterFactory.java` (+33/-4 lines)

**修改目的**：添加 writerProperties 支持和 Serializable 接口。

**工作逻辑**：
- 实现 `Serializable` 接口
- 新增 `writerProperties` 字段和接受该参数的构造函数（旧构造函数标记 `@Deprecated`，设 `writerProperties` 为空 Map）
- 在所有数据写入和删除写入的 builder 中（Avro/Parquet/ORC），添加 `.setAll(writerProperties)` 调用

### `data/src/test/java/org/apache/iceberg/io/TestFileWriterFactory.java` (+12/-0 lines)

**修改目的**：添加序列化测试。

**工作逻辑**：新增 `testSerialization` 测试，验证 `FileWriterFactory` 可被序列化和反序列化而不抛出异常——这对 Flink 的分布式部署至关重要。

### `flink/v2.0/flink/.../BaseDeltaTaskWriter.java` (+3/-3 lines)
**修改目的**：将参数类型从 `FileAppenderFactory` 改为 `FileWriterFactory`。

### `flink/v2.0/flink/.../FlinkAppenderFactory.java` (+5/-0 lines)
**修改目的**：标记为 `@Deprecated`。
**工作逻辑**：添加 `@Deprecated` 注解和 Javadoc，注明 1.11.0 起废弃，1.12.0 移除。

### `flink/v2.0/flink/.../FlinkFileWriterFactory.java` (+27/-18 lines)

**修改目的**：公开 API 并支持 writerProperties。

**工作逻辑**：
- 类从包级私有改为 `public`
- 构造函数改为 `private`（通过 Builder 创建）
- Builder 类及所有方法改为 `public`
- 新增 `writerProperties` 字段和对应的 Builder 方法
- 构造函数和 `build()` 方法增加 `writerProperties` 参数

### `flink/v2.0/flink/.../PartitionedDeltaWriter.java` (+3/-3 lines)
**修改目的**：将参数类型从 `FileAppenderFactory` 改为 `FileWriterFactory`。

### `flink/v2.0/flink/.../RowDataTaskWriterFactory.java` (+38/-28 lines)

**修改目的**：从使用 FlinkAppenderFactory 迁移到 FlinkFileWriterFactory。

**工作逻辑**：
- 字段类型从 `FileAppenderFactory<RowData>` 改为 `FileWriterFactory<RowData>`
- 在三种场景（无 equality、upsert、普通 equality）下，将 `new FlinkAppenderFactory(...)` 替换为 `new FlinkFileWriterFactory.Builder(table)...build()`
- 所有 writer 创建处将 `appenderFactory` 参数改为 `fileWriterFactory`

### `flink/v2.0/flink/.../UnpartitionedDeltaWriter.java` (+3/-3 lines)
**修改目的**：将参数类型从 `FileAppenderFactory` 改为 `FileWriterFactory`。

### `flink/v2.0/flink/.../SimpleDataUtil.java` (+69/-22 lines)

**修改目的**：测试工具类适配 FileWriterFactory。

**工作逻辑**：
- `writeDataFile` 方法改用 `FlinkFileWriterFactory` 和 `DataWriter` 替代旧的 `FileAppender` 和 `DataFiles.Builder`
- 新增接受 `FileWriterFactory` 的 `writeEqDeleteFile` 和 `writePosDeleteFile` 重载方法
- 新增 `encrypt(OutputFile)` 辅助方法创建 `EncryptedOutputFile`

### 其他测试文件（TestCompressionSettings、TestFlinkManifest、TestIcebergCommitter、TestIcebergFilesCommitter、TestDynamicWriter）

**修改目的**：适配新的 FileWriterFactory 接口。

## 总结

本提交是 Flink sink 写入路径的重要架构迁移，将写入从旧的 `AppenderFactory` 模式迁移到新的 `FileWriterFactory` 模式。改动涉及核心 IO 层（BaseTaskWriter 及其子类）、数据层（BaseFileWriterFactory）和 Flink sink 层，共 18 个文件。迁移使 Flink 与其他引擎的写入实现保持一致，并新增了 writerProperties 支持（使压缩等写入属性可通过 FileWriterFactory 传递）。设计上保持了向后兼容（旧接口标记 @Deprecated 但仍可用），对应的 backport 提交为 2744（PR #14325）。
