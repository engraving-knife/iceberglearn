# 提交 2752：Kafka Connect: Use GenericFileWriterFactory instead of GenericAppenderFactory (#14328)

## 提交信息

- **序号**：2752 / 4088
- **哈希**：d831673c2ccba4213de9e1d859045526e3b2e333
- **短哈希**：d831673c2
- **日期**：2025-10-15 17:46:41 +0200
- **作者**：pvary
- **提交说明**：Kafka Connect: Use GenericFileWriterFactory instead of GenericAppenderFactory (#14328)
- **PR/Issue**：#14328

## 总体目的

本提交将 Kafka Connect 模块的文件写入实现从已废弃的 `GenericAppenderFactory` 迁移到新的 `GenericFileWriterFactory`。

背景在于：Iceberg 正在推进写入 API 的现代化，将旧的 `FileAppenderFactory` 接口逐步替换为新的 `FileWriterFactory` 接口。`FileAppenderFactory` 只负责创建文件追加器（appender），而 `FileWriterFactory` 是更上层的抽象，能够统一处理数据文件、等值删除文件和位置删除文件的写入，并且支持写入属性（writer properties）的传递。

`GenericAppenderFactory` 是 `FileAppenderFactory` 的通用实现，而 `GenericFileWriterFactory` 是 `FileWriterFactory` 的通用实现。由于 1.11.0 起 Iceberg 不再支持带行数据的位置删除（position deletes with row data），旧 API 中相关的参数也变得过时。Kafka Connect 作为 sink 连接器，需要写入 Iceberg 表，因此需要同步迁移到新 API，以保证与主线一致并为后续版本（1.12.0）移除旧 API 做准备。

本提交与后续 2758（"Core: Deprecate and remove GenericAppenderFactory from tests"）属于同一迁移系列，2758 进一步在测试中清理对 `GenericAppenderFactory` 的使用。

## 如何达成设计目的

迁移分两步走：

1. **开放 `GenericFileWriterFactory` 的 API**：原来 `GenericFileWriterFactory` 及其 `Builder` 大多是包级可见（package-private），Kafka Connect 位于不同的模块无法直接使用。本提交将类、新增的构造器、`Builder` 类及其方法改为 `public`，并新增一个支持 `writerProperties` 的构造器，同时将旧构造器（含 `positionDeleteRowSchema`）标记为 `@Deprecated`。

2. **改造 Kafka Connect 的写入逻辑**：在 `RecordUtils` 中将 `GenericAppenderFactory` 的使用替换为 `GenericFileWriterFactory.Builder`；将 `PartitionedAppendWriter` 接收的参数类型从 `FileAppenderFactory` 改为 `FileWriterFactory`，并传递给父类 `PartitionedFanoutWriter`。

## 修改详情

### `data/src/main/java/org/apache/iceberg/data/GenericFileWriterFactory.java` (+61/-12 lines)

**修改目的**：将 `GenericFileWriterFactory` 及其 `Builder` 暴露为 public API，并新增支持 `writerProperties` 的构造器。

**工作逻辑**：
- 将类声明从 `class` 改为 `public class`，`Builder` 类从 `static class` 改为 `public static class`，构造器与 Builder 方法均改为 `public`。
- 新增一个不含 `positionDeleteRowSchema` 但含 `writerProperties` 的构造器，内部调用父类新构造器。原含 `positionDeleteRowSchema` 的构造器标记 `@Deprecated`（1.11.0 起废弃，1.12.0 移除）。
- 在 `Builder` 中新增 `writerProperties` 字段（默认 `ImmutableMap.of()`）和 `writerProperties(Map)` setter 方法，并在 `build()` 时将其传入新构造器。这样外部调用方可以传递表属性（如压缩配置）给写入器。
- 重新排序了 `writerProperties` 与 `positionDeleteRowSchema` 方法的位置，使新 API 在前。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/PartitionedAppendWriter.java` (+6/-2 lines)

**修改目的**：将分区写入器从使用 `FileAppenderFactory` 改为使用 `FileWriterFactory`。

**工作逻辑**：构造器参数由 `FileAppenderFactory<Record> appenderFactory` 改为 `FileWriterFactory<Record> fileWriterFactory`，import 相应调整，并调用父类 `PartitionedFanoutWriter` 的对应构造器（接收 `FileWriterFactory`）。`PartitionedFanoutWriter` 在新 API 下直接使用 `FileWriterFactory` 创建数据/删除文件写入器。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/RecordUtils.java` (+37/-19 lines)

**修改目的**：将 Kafka Connect 写入器的创建逻辑从 `GenericAppenderFactory` 迁移到 `GenericFileWriterFactory`。

**工作逻辑**：
- import 从 `GenericAppenderFactory`、`FileAppenderFactory` 改为 `GenericFileWriterFactory`、`FileWriterFactory`。
- 原来根据是否有标识字段（identifier field ids）分两种情况创建 `GenericAppenderFactory`：无标识字段时仅设置 schema/spec/props；有标识字段时额外设置等值字段 id 与等值删除行 schema。
- 现改为统一使用 `GenericFileWriterFactory.Builder(table)`：无标识字段时设置 `dataSchema`、`dataFileFormat`、`writerProperties`；有标识字段时额外设置 `equalityFieldIds`、`equalityDeleteRowSchema`、`deleteFileFormat`。这样新 API 下既能写数据文件，也能在需要时写等值删除文件。
- 后续创建 `UnpartitionedWriter` 与 `PartitionedAppendWriter` 时，将 `appenderFactory` 参数改为 `writerFactory`。

## 总结

本提交完成了 Kafka Connect 模块从旧写入 API（`GenericAppenderFactory`/`FileAppenderFactory`）到新写入 API（`GenericFileWriterFactory`/`FileWriterFactory`）的迁移。核心改动有两点：一是将 `GenericFileWriterFactory` 的 API 提升为 public 并新增 `writerProperties` 支持，使跨模块可用；二是改造 Kafka Connect 的 `RecordUtils` 与 `PartitionedAppendWriter` 使用新工厂。这是 Iceberg 推进写入 API 现代化、并在 1.12.0 移除旧 API 的整体计划的一部分，与 2758 共同构成完整的迁移工作。迁移后 Kafka Connect 不再依赖带行数据的位置删除能力，符合 1.11.0 起的废弃策略。
