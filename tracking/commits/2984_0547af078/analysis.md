# 提交 2984：Flink: Fix write unknown type to ORC exception and add ut for unknown type (#14761)

## 提交信息

- **序号**：2984 / 4088
- **哈希**：0547af078c9d21b8642a4efcb3cbc85756547f22
- **短哈希**：0547af078
- **日期**：2025-12-09
- **作者**：GuoYu
- **提交说明**：Flink: Fix write unknown type to ORC exception and add ut for unknown type (#14761)
- **PR/Issue**：#14761

## 总体目的

Iceberg v3 表规范引入了 `UnknownType`（未知类型）——当一个字段在历史 schema 中存在但后续被删除（dropped），该字段在 v3 表中会被标记为 `UnknownType`，表示其类型已不可知。这种字段仍可能出现在表的某些历史 schema 中，读取时应跳过，写入时也不应再处理。Iceberg 的 Spark 集成已经对 `UnknownType` 做了处理，但 Flink 集成在向 ORC 写入含 `UnknownType` 字段的表时会抛异常，且读取路径也存在问题。

具体问题链路：当 Flink 通过 `FlinkOrcWriters` 把 `RowData` 写入 ORC 时，`FlinkSchemaVisitor` 会遍历 Iceberg schema 为每个字段创建对应的 `OrcValueWriter`。原实现对 `UnknownType` 字段不做特殊处理，会尝试为它创建 writer，而 ORC 无法处理这种类型，导致写入抛异常。即便跳过创建 writer，还存在第二个问题：传给 `FlinkOrcWriters.struct(...)` 的 `types` 列表来自 Flink `RowType`，其中 `UnknownType` 会被映射为 Flink 的 `NULL` 逻辑类型，仍占据一个位置；而 `writers` 列表若跳过了未知字段则位置数减少，两个列表长度不一致会导致 `RowDataWriter` 用错误的索引从 `RowData` 取值，写出错位数据。

本提交的目的就是修复上述两个问题，让 Flink 能正确地以 ORC（以及 Parquet/Avro）格式读写含 `UnknownType` 字段的 v3 表：访问 schema 时跳过未知类型字段，并在 ORC struct writer 中建立"writer 位置 → RowData 原始字段位置"的映射，跳过 `NULL` 逻辑类型字段，确保字段对齐。同时新增端到端单元测试覆盖读、写两条路径。

## 如何达成设计目的

整体思路分两处协同修改：

1. 在 `FlinkSchemaVisitor`（读写共用的 schema 访问器）的 `struct` 方法中，遇到 `Types.UnknownType.get()` 的字段时不递归访问、不加入 `results`，这样不会为未知字段创建 writer/reader。

2. 在 `FlinkOrcWriters.struct(...)` 中处理 `writers`（已排除未知字段）与 `types`（仍含 `NULL` 逻辑类型对应未知字段）长度不一致的问题：遍历 `types`，跳过 `LogicalTypeRoot.NULL`，为每个非 NULL 类型记录其原始下标到 `fieldIndexes` 数组，并构造只含非 NULL 类型的 `logicalTypes` 列表；`RowDataWriter` 构造器接收 `fieldIndexes`，在为每个 writer 创建 `FlinkRowData.createFieldGetter` 时用 `fieldIndexes[i]` 而非 `i` 作为 RowData 字段下标，从而正确跳过未知列、从原始 RowData 的正确位置取值。

此外新增 `TestFlinkUnknownType` 端到端测试，覆盖 Parquet/Avro/ORC 三种格式下 v3 表含未知类型列的读与写；并在测试工具 `ReaderUtil` 中新增带 schema 参数的 `createDataIterator` 重载，供该测试使用。改动集中在 `flink/v2.1/flink/` 目录。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/data/FlinkOrcWriters.java` (+15/-2 lines)

**修改目的**：修复 ORC struct writer 在 schema 含未知类型字段时因 `writers` 与 `types` 长度不一致导致的字段错位问题。

**工作逻辑**：
`struct(List<OrcValueWriter<?>> writers, List<LogicalType> types)` 方法原先直接 `return new RowDataWriter(writers, types)`。改为先构建 `fieldIndexes` 映射：遍历 `types`，对每个非 `LogicalTypeRoot.NULL` 的类型，把其原始下标 `i` 记入 `fieldIndexes[fieldIndex]` 并 `fieldIndex++`，同时加入 `logicalTypes` 列表。这样 `fieldIndexes` 的第 k 项就是第 k 个 writer 对应的 RowData 原始字段下标。最终 `return new RowDataWriter(fieldIndexes, writers, logicalTypes)`。

`RowDataWriter` 构造器签名从 `(List<OrcValueWriter<?>> writers, List<LogicalType> types)` 改为 `(int[] fieldIndexes, List<OrcValueWriter<?>> writers, List<LogicalType> types)`。创建 field getter 时由 `FlinkRowData.createFieldGetter(types.get(i), i)` 改为 `FlinkRowData.createFieldGetter(types.get(i), fieldIndexes[i])`——即第 i 个 writer 用第 i 个（已过滤后的）类型，但从 RowData 的第 `fieldIndexes[i]` 个位置取值，正确跳过未知/NULL 列。新增 import `LogicalTypeRoot`。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/data/FlinkSchemaVisitor.java` (+3/-1 lines)

**修改目的**：在 schema 访问时跳过 `UnknownType` 字段，不为其创建 writer/reader。

**工作逻辑**：
在 `struct` 方法遍历字段时，原先无条件 `results.add(visit(fieldFlinkType, iField.type(), visitor))`。改为加判断：`if (iField.type() != Types.UnknownType.get()) { results.add(visit(fieldFlinkType, iField.type(), visitor)); }`。这样 `UnknownType` 字段不会出现在 `results`（即 `writers`）中，配合 `FlinkOrcWriters.struct` 的索引映射，实现未知字段被完全跳过。`beforeField`/`afterField` 仍正常调用以保持 visitor 状态一致。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/TestFlinkUnknownType.java` (+179/-0 lines, 新文件)

**修改目的**：端到端验证 v3 表含未知类型列时的读、写正确性，覆盖 Parquet/Avro/ORC 三种格式。

**工作逻辑**：
参数化测试，`@Parameters` 提供 `PARQUET`/`AVRO`/`ORC` 三种 `FileFormat`。表 schema 为 `SCHEMA_WITH_UNKNOWN_COL`：`id(int)`、`data(string)`、`unknown_col(UnknownType)`、`data1(string)`。建表时设 `format-version=3` 与 `write.format.default=<格式>`。期望数据 `EXCEPTED_ROW_DATA` 为两行，其中 `unknown_col` 位置为 null。

- `testV3TableUnknownTypeRead`：用 `GenericAppenderHelper` 写入期望记录，然后用 `ReaderUtil.createDataIterator(combinedScanTask, table.schema(), table.schema())` 读取，断言读出的 `GenericRowData` 与期望一致（未知列读出为 null）。
- `testV3TableUnknownTypeWrite`：用 `RowDataTaskWriterFactory` 创建 TaskWriter，写入 `EXCEPTED_ROW_DATA`，提交 append，再用 `SimpleDataUtil.tableRecords(table)` 读回，断言与期望记录一致。验证写入路径（含 ORC）不再抛异常且数据正确。

`exceptedRecords()` 辅助方法把 `GenericRowData` 转成 `GenericRecord`（`StringData` 转 `String`）。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/source/reader/ReaderUtil.java` (+7/-1 lines)

**修改目的**：新增带 schema 参数的 `createDataIterator` 重载，供未知类型测试使用自定义 schema。

**工作逻辑**：
原 `createDataIterator(CombinedScanTask)` 改为委托新重载 `createDataIterator(combinedTask, TestFixtures.SCHEMA, TestFixtures.SCHEMA)`。新重载 `createDataIterator(CombinedScanTask, Schema tableSchema, Schema projectSchema)` 用传入的 schema 构造 `RowDataFileScanTaskReader(tableSchema, projectSchema, null, true, Collections.emptyList())`，并把 `new org.apache.hadoop.conf.Configuration()` 简化为 `new Configuration()`（新增 import）。这样 `TestFlinkUnknownType` 可以用含未知列的表 schema 来构造读取迭代器。

## 总结

该提交修复了 Flink 向 ORC 写入含 `UnknownType`（v3 表中被删除字段的类型）的表时抛异常、以及字段错位的正确性缺陷。核心机制是两处协同：`FlinkSchemaVisitor` 跳过未知类型字段不为它创建 writer/reader；`FlinkOrcWriters.struct` 建立 writer 位置到 RowData 原始字段位置的映射，跳过 `NULL` 逻辑类型字段，确保 `RowDataWriter` 从正确位置取值。新增的 `TestFlinkUnknownType` 端到端测试覆盖了 Parquet/Avro/ORC 三种格式下 v3 表含未知类型列的读、写两条路径，验证了修复的有效性并防止回归。
