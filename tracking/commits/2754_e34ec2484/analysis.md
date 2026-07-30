# 提交 2754：Core, Flink, Spark: Further deprecations for the positional deletes with row data (#14210)

## 提交信息

- **序号**：2754 / 4088
- **哈希**：e34ec248495cdee6259c35c2f44807b2d910dc4e
- **短哈希**：e34ec2484
- **日期**：2025-10-16 07:11:33 +0200
- **作者**：pvary
- **提交说明**：Core, Flink, Spark: Further deprecations for the positional deletes with row data (#14210)
- **PR/Issue**：#14210

## 总体目的

本提交是 Iceberg 废弃"带行数据的位置删除"（positional deletes with row data）的后续工作。提交说明明确指出这是 2635 的后续（"Further deprecations"），在 1.11.0 版本基础上进一步将相关 API 标记为 `@Deprecated`，并在 Flink/Spark 的 FileWriterFactory 中移除位置删除行数据的相关配置代码，为 1.12.0 完全移除做准备。

背景在于：Iceberg 的位置删除（position delete）原本可以携带被删除行的数据（row data），用于在删除时保留行内容以便 CDC 等场景。但这一特性带来了 API 复杂性和维护成本。社区决定从 1.11.0 起废弃该特性，1.12.0 移除。位置删除将只保留文件路径和行位置两个字段，不再包含行数据。

本提交在前序工作的基础上，进一步：
1. 在 `BaseFileWriterFactory`、`GenericAppenderFactory`、`GenericFileWriterFactory`、`FlinkAppenderFactory`、`SparkFileWriterFactory` 中，将所有涉及 `positionDeleteRowSchema` 的构造器和方法标记 `@Deprecated`，并新增不含该参数的替代构造器/方法。
2. 在 Flink 的 `FlinkFileWriterFactory` 中移除 `positionDeleteFlinkType` 字段及相关的 `configurePositionDelete` 实现（清空为空方法体），并从 Builder 中移除 `positionDeleteRowSchema` / `positionDeleteFlinkType` 设置方法。
3. 在 Spark 的 `SparkPositionDeltaWrite` 中，将 `positionDelete.set(file, position, null)` 改为 `positionDelete.set(file, position)`，不再传递行数据参数。
4. 调整相关测试，禁用 `testPositionDeleteWriterWithRow` 测试，并修改 WriterMetrics 测试以适配位置删除不再包含行统计信息的情况。

## 如何达成设计目的

整体设计思路是"先废弃后移除"的渐进式迁移：

1. **保留旧 API 但标记废弃**：对所有接受 `positionDeleteRowSchema` 参数的构造器和方法添加 `@Deprecated` 注解和 Javadoc，明确告知用户 1.11.0 起废弃、1.12.0 移除，并指引到新的不含该参数的替代 API。

2. **新增不含位置删除行 schema 的构造器**：在 `BaseFileWriterFactory`、`GenericFileWriterFactory`、`FlinkAppenderFactory`、`SparkFileWriterFactory` 中新增不含 `positionDeleteRowSchema` 的构造器，新构造器内部将 `positionDeleteRowSchema` 设为 null。

3. **在 Flink FileWriterFactory 中移除位置删除行数据的实现**：由于 Flink 的 `FlinkFileWriterFactory` 是较新的 API，可以直接移除 `positionDeleteFlinkType` 字段和相关 `configurePositionDelete` 实现，将三个格式（Avro/Parquet/ORC）的 `configurePositionDelete` 简化（Avro 清空为 `{}`，Parquet/ORC 仅保留 path 转换）。

4. **Spark 调用点适配**：将 `positionDelete.set(file, position, null)` 改为 `positionDelete.set(file, position)`，调用不含行数据的重载。

5. **测试适配**：禁用专门测试带行数据位置删除的 `testPositionDeleteWriterWithRow`；重构 `TestWriterMetrics` 中位置删除文件的统计信息断言（因为不再有行字段统计），抽取 `checkRowStatistics` / `checkNotExistingRowStatistics` 方法，并允许 Flink 子类覆盖（Flink 的位置删除统计行为与 core 不同）。

## 修改详情

### `data/src/main/java/org/apache/iceberg/data/BaseFileWriterFactory.java` (+34/-0 lines)

**修改目的**：新增不含 `positionDeleteRowSchema` 的构造器，并废弃旧的含该参数的构造器与 `positionDeleteRowSchema()` 方法。

**工作逻辑**：新增一个 protected 构造器，参数列表不含 `positionDeleteRowSchema`，内部将其设为 null。原构造器添加 `@Deprecated` 注解（1.11.0 废弃，1.12.0 移除），并在 Javadoc 中指引到新构造器。`positionDeleteRowSchema()` getter 方法也标记废弃。

### `data/src/main/java/org/apache/iceberg/data/GenericAppenderFactory.java` (+43/-0 lines)

**修改目的**：废弃含 `posDeleteRowSchema` 的构造器，新增不含该参数的替代构造器。

**工作逻辑**：
- 原有含 5 参数和 7 参数（含 `posDeleteRowSchema`）的构造器标记 `@Deprecated`。
- 新增两个不含 `posDeleteRowSchema` 的构造器：`GenericAppenderFactory(Schema, PartitionSpec, int[], Schema)` 和 `GenericAppenderFactory(Table, Schema, PartitionSpec, Map, int[], Schema)`，内部调用 7 参数构造器并将 `posDeleteRowSchema` 传 null。

### `data/src/main/java/org/apache/iceberg/data/GenericFileWriterFactory.java` (+43/-2 lines)

**修改目的**：为 `GenericFileWriterFactory` 新增不含 `positionDeleteRowSchema` 的构造器，废弃旧构造器与 Builder 的 `positionDeleteRowSchema` 方法。

**工作逻辑**：新增一个不含 `positionDeleteRowSchema` 的构造器（内部调用父类新构造器并传 `ImmutableMap.of()`）。旧构造器标记 `@Deprecated`。Builder 的 `positionDeleteRowSchema` 方法也标记 `@Deprecated`。同时调整方法顺序，将 `writerProperties` 放到 `positionDeleteRowSchema` 之前。

### `data/src/test/java/org/apache/iceberg/data/TestGenericFileWriterFactory.java` (+9/-1 lines)

**修改目的**：移除测试中对 `positionDeleteRowSchema` 的设置，并禁用带行数据的位置删除测试。

**工作逻辑**：在 `createWriterFactory` 中删除 `.positionDeleteRowSchema(positionDeleteRowSchema)` 调用。新增 `@Disabled("Position deletes with row data are no longer supported")` 注解的空方法 `testPositionDeleteWriterWithRow`，覆盖父类的同名测试以跳过。

### `data/src/test/java/org/apache/iceberg/io/TestWriterMetrics.java` (+54/-44 lines, 净 +10 但实际重构)

**修改目的**：重构位置删除文件的统计信息断言，适配位置删除不再包含行字段统计。

**工作逻辑**：将原来分散在 `testPositionDeleteMetrics` 等方法中的 lowerBounds/upperBounds 断言抽取为两个 protected 方法：`checkRowStatistics(Map)` 断言 bounds 包含字段 1（int=3）和字段 5（long=3），不含 2/3/4；`checkNotExistingRowStatistics(Map)` 断言 bounds 只有 2 个条目（字段 1 和 5）。这样子类（如 Flink）可以覆盖这些方法以适配不同的统计行为。

### `flink/v1.20|v2.0|v2.1` 的 `FlinkAppenderFactory.java` (各 +17/-0 lines)

**修改目的**：为三个 Flink 版本的 `FlinkAppenderFactory` 新增不含 `posDeleteRowSchema` 的构造器，废弃旧构造器。

**工作逻辑**：新增 7 参数构造器（不含 `posDeleteRowSchema`），内部调用 8 参数构造器传 null。原 8 参数构造器标记 `@Deprecated`。三个 Flink 版本的改动完全相同。

### `flink/v1.20|v2.0|v2.1` 的 `FlinkFileWriterFactory.java` (各约 -45 lines)

**修改目的**：从 Flink 的 FileWriterFactory 中移除位置删除行数据的全部实现。

**工作逻辑**：
- 移除 `positionDeleteFlinkType` 字段及其 import（`DELETE_FILE_ROW_FIELD_NAME`、`DeleteSchemaUtil`）。
- 构造器移除 `positionDeleteRowSchema` 和 `positionDeleteFlinkType` 参数，调用父类新构造器。
- `configurePositionDelete(Avro.DeleteWriteBuilder)` 改为空方法体 `{}`。
- `configurePositionDelete(Parquet.DeleteWriteBuilder)` 和 `configurePositionDelete(ORC.DeleteWriteBuilder)` 移除 `createWriterFunc` 调用，仅保留 `transformPaths`。
- 移除 `positionDeleteFlinkType()` 私有方法。
- Builder 移除 `positionDeleteRowSchema` 和 `positionDeleteFlinkType` 两个 setter 方法及对应字段。

### `flink/v1.20|v2.0|v2.1` 的测试文件（TestFlinkFileWriterFactory、TestFlinkPartitioningWriters、TestFlinkPositionDeltaWriters、TestFlinkRollingFileWriters、TestFlinkWriterMetrics、TestFlinkMergingMetrics）

**修改目的**：移除测试中对 `positionDeleteRowSchema` 的设置，禁用带行数据的位置删除测试，并适配 Flink 的位置删除统计行为。

**工作逻辑**：
- 各 FileWriterFactory 测试中删除 `.positionDeleteRowSchema(positionDeleteRowSchema)` 调用。
- `TestFlinkFileWriterFactory` 新增 `@Disabled` 的 `testPositionDeleteWriterWithRow` 空方法。
- `TestFlinkWriterMetrics` 不再设置 `positionDeleteRowSchema`，并覆盖 `checkRowStatistics`（断言 size=2）和 `checkNotExistingRowStatistics`（断言为 null，因为 Flink 不产生这些统计）。
- `TestFlinkMergingMetrics` 中 `FlinkAppenderFactory` 构造减少一个 null 参数（对应移除的 `posDeleteRowSchema`）。

### `spark/v3.4|v3.5|v4.0` 的 `SparkFileWriterFactory.java` (各 +47/-0 lines)

**修改目的**：为三个 Spark 版本的 `SparkFileWriterFactory` 新增不含 `positionDeleteRowSchema` 的构造器，废弃旧构造器与 Builder 方法。

**工作逻辑**：新增 11 参数构造器（不含 `positionDeleteRowSchema` 和 `positionDeleteSparkType`），内部将 `positionDeleteSparkType` 设为 null，调用父类新构造器。原构造器标记 `@Deprecated`。Builder 的 `positionDeleteRowSchema` 和 `positionDeleteSparkType` 方法标记 `@Deprecated`。

### `spark/v3.4|v3.5|v4.0` 的 `SparkPositionDeltaWrite.java` (各 +3/-1 lines)

**修改目的**：将位置删除写入调用从带行数据改为不带行数据。

**工作逻辑**：将 `positionDelete.set(file, position, null)` 改为 `positionDelete.set(file, position)`，调用只接收文件路径和位置的重载方法。同时为 `deleteSparkType()` 方法添加废弃注释。

## 总结

本提交是 Iceberg 废弃"带行数据的位置删除"特性的重要一步（2635 的后续）。改动范围广泛，横跨 Core、Flink（v1.20/v2.0/v2.1）、Spark（v3.4/v3.5/v4.0）三大模块共 35 个文件。核心策略是：在 Core 层保留并废弃旧 API、提供新替代 API；在 Flink 的 FileWriterFactory 中直接移除位置删除行数据的实现；在 Spark 中调整调用点；并全面适配测试（禁用带行数据的位置删除测试、重构 WriterMetrics 统计断言并允许 Flink 子类覆盖）。这为 1.12.0 完全移除该特性奠定了基础，使位置删除回归"仅文件路径+行位置"的简洁语义。
