# 提交 2759：Core: Deprecate and remove GenericAppenderFactory from tests (#14353)

## 提交信息

- **序号**：2759 / 4088
- **哈希**：03f2af8ab465d4a5238753be206bcf366130f9a0
- **短哈希**：03f2af8ab
- **日期**：2025-10-17 13:52:06 +0200
- **作者**：pvary
- **提交说明**：Core: Deprecate and remove GenericAppenderFactory from tests (#14353)
- **PR/Issue**：#14353

## 总体目的

本提交是 Iceberg 写入 API 现代化迁移的延续，主要完成三件事：(1) 将 `GenericAppenderFactory` 整个类标记为 `@Deprecated`；(2) 删除已废弃的 `SortedPosDeleteWriter` 及其测试；(3) 将大量测试代码从 `GenericAppenderFactory`/`FileAppenderFactory`/`FileAppender` 迁移到新的 `GenericFileWriterFactory`/`FileWriterFactory`/`DataWriter` API。

这是 2751（Kafka Connect 迁移）的后续，也是 2753（位置删除废弃）的配套工作。背景在于：Iceberg 正在将旧的 `FileAppenderFactory` 接口（只创建 appender）替换为新的 `FileWriterFactory` 接口（统一创建 DataWriter/EqualityDeleteWriter/PositionDeleteWriter，并能直接产出 `DataFile`/`DeleteFile`）。`GenericAppenderFactory` 是旧接口的通用实现，本提交将其标记废弃并清理测试中的使用，为 1.12.0 移除做准备。

同时，`SortedPosDeleteWriter` 是一个基于 `FileAppenderFactory` 的位置删除写入器，支持带行数据的位置删除。由于 1.11.0 起不再支持带行数据的位置删除（见 2753），该类已无存在的必要，本提交将其及其测试 `TestGenericSortedPosDeleteWriter` 一并删除。

## 如何达成设计目的

整体分几个层面推进：

1. **废弃 `GenericAppenderFactory`**：在类级别添加 `@Deprecated` 注解和 Javadoc，指明 1.12.0 移除、改用 `GenericFileWriterFactory`。同时移除 2753 中给某个构造器加的 `@Deprecated`（因为整个类已废弃，单独废弃构造器多余）。

2. **删除 `SortedPosDeleteWriter` 及其测试**：`SortedPosDeleteWriter` 完全基于 `FileAppenderFactory`，且用于带行数据的位置删除，已被新 API 取代。删除该类（214 行）和对应测试 `TestGenericSortedPosDeleteWriter`（340 行）。

3. **新增 `MetricsConfig.forPositionDelete()` 无参版本**：原来位置删除的 MetricsConfig 需要 `Table` 参数来读取列模式。新 API 下测试可能没有 Table 对象，因此新增一个无参版本，返回只对 `DELETE_FILE_PATH` 和 `DELETE_FILE_POS` 两个元数据列开启 Full metrics 的配置。原 `forPositionDelete(Table)` 标记废弃。

4. **`BaseFileWriterFactory` 适配 null table**：新 API 的 `GenericFileWriterFactory.Builder` 新增无参构造器（table=null）供测试使用。`BaseFileWriterFactory` 在 `newDataWriter`/`newEqualityDeleteWriter`/`newPositionDeleteWriter` 中需要从 table 获取 properties 和 MetricsConfig，因此增加 null 检查：table 为 null 时使用空 properties 和默认 MetricsConfig（位置删除用无参 `forPositionDelete()`）。

5. **测试迁移**：将 core、data、flink（v1.20/v2.0/v2.1）、spark（v3.4/v3.5/v4.0）共数十个测试文件中的 `GenericAppenderFactory` 用法替换为 `GenericFileWriterFactory.Builder`。典型模式是：用 `new GenericFileWriterFactory.Builder(table).dataSchema(schema).dataFileFormat(format).build().newDataWriter(...)` 替代 `new GenericAppenderFactory(schema).newAppender(...)`，并用 `writer.toDataFile()` 替代手动构建 `DataFiles.builder(...)`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/MetricsConfig.java` (+16/-0 lines)

**修改目的**：新增不需要 Table 的位置删除 MetricsConfig 工厂方法。

**工作逻辑**：新增静态常量 `POSITION_DELETE_MODE`，对 `DELETE_FILE_PATH` 和 `DELETE_FILE_POS` 两个元数据列设置 `MetricsModes.Full.get()`，默认模式保持 `DEFAULT_MODE`。新增 `public static MetricsConfig forPositionDelete()` 返回该常量。原 `forPositionDelete(Table)` 方法标记 `@Deprecated`（1.11.0 废弃，1.12.0 移除，改用无参版本）。

### `core/src/main/java/org/apache/iceberg/io/SortedPosDeleteWriter.java` (-214 lines, 删除)

**修改目的**：删除已废弃的位置删除写入器。

**工作逻辑**：整个文件删除。该类基于 `FileAppenderFactory`，支持带行数据的位置删除（`delete(path, pos, row)`），与新 API 方向不符，已被 `BaseFileWriterFactory.newPositionDeleteWriter` 取代。

### `data/src/main/java/org/apache/iceberg/data/BaseFileWriterFactory.java` (+15/-3 lines)

**修改目的**：适配 table 为 null 的场景（测试用无参 Builder 构造时）。

**工作逻辑**：在 `newDataWriter`、`newEqualityDeleteWriter`、`newPositionDeleteWriter` 三个方法中，将 `table.properties()` 改为 `table == null ? ImmutableMap.of() : table.properties()`，将 `MetricsConfig.forTable(table)` 改为 `table == null ? MetricsConfig.getDefault() : MetricsConfig.forTable(table)`，将 `MetricsConfig.forPositionDelete(table)` 改为 `table == null ? MetricsConfig.forPositionDelete() : MetricsConfig.forPositionDelete(table)`。

### `data/src/main/java/org/apache/iceberg/data/GenericAppenderFactory.java` (+13/-7 lines)

**修改目的**：将整个类标记为废弃。

**工作逻辑**：在类级别 Javadoc 添加 `@deprecated will be removed in 1.12.0; use GenericFileWriterFactory instead.` 和 `@Deprecated` 注解。移除 2753 中给单个构造器加的 `@Deprecated`（因为类已整体废弃，重复废弃无意义）。

### `data/src/main/java/org/apache/iceberg/data/GenericFileWriterFactory.java` (+4/-0 lines)

**修改目的**：为 Builder 新增无参构造器，供没有 Table 对象的测试使用。

**工作逻辑**：新增 `public Builder() { this.table = null; }`。原有 `public Builder(Table table)` 保持不变。结合 `BaseFileWriterFactory` 的 null table 处理，使测试可以在无 Table 的情况下创建 DataWriter。

### `data/src/test/java/org/apache/iceberg/data/GenericAppenderHelper.java` (+29/-22 lines, 净 +7 但大幅重构)

**修改目的**：将测试辅助类从 `GenericAppenderFactory` 迁移到 `GenericFileWriterFactory`。

**工作逻辑**：原来用 `new GenericAppenderFactory(table.schema())` 创建 appender，用 `appender.addAll(records)` 写入，再手动用 `DataFiles.builder(...)` 构建 `DataFile`。现改为用 `new GenericFileWriterFactory.Builder(table).dataFileFormat(format).writerProperties(...).build().newDataWriter(encrypt(outputFile), table.spec(), partition)` 创建 `DataWriter`，用 `writer.write(records)` 写入，用 `writer.toDataFile()` 直接获取 `DataFile`。ORC/Parquet 的配置通过 `builder.writerProperties(...)` 传入而非 `appenderFactory.setAll(...)`。

### `data/src/test/java/org/apache/iceberg/io/TestGenericSortedPosDeleteWriter.java` (-340 lines, 删除)

**修改目的**：删除已废弃 `SortedPosDeleteWriter` 的对应测试。

**工作逻辑**：整个文件删除。

### 其余测试文件（core/data/flink/spark 多个版本，约 60 个文件）

**修改目的**：将测试从旧 API 迁移到新 API。

**工作逻辑**：统一的迁移模式：
- import 从 `GenericAppenderFactory`、`FileAppender`、`FileAppenderFactory` 改为 `GenericFileWriterFactory`、`DataWriter`、`FileWriterFactory`。
- 创建写入器：`new GenericAppenderFactory(schema, spec, eqIds, eqSchema, null).newAppender(output, format)` → `new GenericFileWriterFactory.Builder(table).dataSchema(schema).dataFileFormat(format).equalityFieldIds(...).equalityDeleteRowSchema(...).build().newDataWriter(encrypt(output), spec, partition)`。
- 写入数据：`appender.addAll(records)` / `appender.add(record)` → `writer.write(records)` / `writer.write(record)`。
- 获取结果：手动 `DataFiles.builder(spec).withMetrics(appender.metrics())...build()` → `writer.toDataFile()`。
- 字段类型：`FileAppenderFactory<Record> appenderFactory` → `FileWriterFactory<Record> fileWriterFactory`，并传递给 `BaseTaskWriter` 等父类。
- Flink 的 `SimpleDataUtil`、`ReaderUtil` 等工具类也做了类似迁移。
- 部分测试中原先传给 `GenericAppenderFactory` 的 `posDeleteRowSchema` 参数被移除（符合 2753 的废弃方向）。

## 总结

本提交是 Iceberg 写入 API 迁移的重要里程碑，覆盖 core/data/flink（三版本）/spark（三版本）共 65 个文件、净删除约 642 行代码。核心成果包括：(1) 正式将 `GenericAppenderFactory` 整体标记废弃；(2) 删除已过时的 `SortedPosDeleteWriter` 及其测试（共 554 行）；(3) 新增 `MetricsConfig.forPositionDelete()` 无参版本和 `GenericFileWriterFactory.Builder` 无参构造器以支持无 Table 的测试场景；(4) 将数十个测试文件统一从旧 `FileAppenderFactory`/`FileAppender` API 迁移到新 `FileWriterFactory`/`DataWriter` API，并用 `writer.toDataFile()` 简化数据文件构建。这为 1.12.0 完全移除 `GenericAppenderFactory` 奠定了基础，是 2751/2753 迁移系列的收尾工作。迁移后测试代码更简洁，且与新 API 方向一致。
