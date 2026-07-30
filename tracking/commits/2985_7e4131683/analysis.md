# 提交 2985：Flink: Backport fix write unknown type to ORC exception and add ut for unknown type (#14806)

## 提交信息

- **序号**：2985 / 4088
- **哈希**：7e41316832458217548c9b5ff296da28d3e0ce33
- **短哈希**：7e4131683
- **日期**：2025-12-09 17:02:46 +0100
- **作者**：GuoYu
- **提交说明**：Flink: Backport fix write unknown type to ORC exception and add ut for unknown type (#14806)
- **PR/Issue**：#14806（backport of #14761）

## 总体目的

本提交是 PR #14761 的反向移植（backport），目的是修复 Flink 集成模块在将包含 UnknownType（未知类型）字段的 Iceberg V3 表写入 ORC 文件格式时抛出异常的问题，并为 UnknownType 的读写场景补充单元测试。

Iceberg V3 表规范引入了 UnknownType，用于表示字段类型在当前引擎/库版本中无法识别的情况。当 Flink 写 ORC 时，由于 Flink 把 UnknownType 转换为 `LogicalTypeRoot.NULL`，原有 `FlinkOrcWriters` 直接把 NULL 类型字段当作普通列写入并按位置索引取值，导致字段错位或异常。本提交通过跳过 UnknownType 字段的写器创建并维护字段索引映射来解决该问题。

## 如何达成设计目的

整体思路分两部分：

1. **写入侧（FlinkOrcWriters / FlinkSchemaVisitor）**：在 schema 访问阶段跳过 `UnknownType` 字段，不为其创建 ORC 写器；在 `RowDataWriter` 中通过 `fieldIndexes` 数组维护 Iceberg schema 字段位置与实际写入字段位置之间的映射，保证 `FieldGetter` 仍按正确的原始索引从 RowData 取值。
2. **测试侧（TestFlinkUnknownType / ReaderUtil）**：新增参数化测试类，针对 PARQUET、AVRO、ORC 三种格式分别测试 V3 表中含 UnknownType 列的读写行为，验证 UnknownType 列读出为 null，其它列正常读写。

改动同时应用到 `flink/v1.20` 和 `flink/v2.0` 两个 Flink 版本分支，保证一致性。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/data/FlinkOrcWriters.java` (+19/-2 lines)

**修改目的**：修复 ORC 写入器对 UnknownType（NULL LogicalTypeRoot）字段的处理，避免字段错位和异常。

**工作逻辑**：
- `struct(...)` 方法原本直接把 `writers` 和 `types` 传给 `RowDataWriter`。修改后，遍历 `types`，跳过 `LogicalTypeRoot.NULL` 的字段，构建 `fieldIndexes` 数组（记录原 schema 位置）与精简后的 `logicalTypes` 列表，再传给 `RowDataWriter`。
- `RowDataWriter` 构造函数新增 `int[] fieldIndexes` 参数，在创建 `FieldGetter` 时使用 `fieldIndexes[i]` 取原始字段位置，而不是直接使用 `i`。这样即使跳过了 NULL 字段，仍能从 RowData 的正确位置读取非未知字段的值。

```java
int[] fieldIndexes = new int[writers.size()];
int fieldIndex = 0;
List<LogicalType> logicalTypes = Lists.newArrayList();
for (int i = 0; i < types.size(); i += 1) {
  LogicalType logicalType = types.get(i);
  if (!logicalType.is(LogicalTypeRoot.NULL)) {
    fieldIndexes[fieldIndex] = i;
    fieldIndex += 1;
    logicalTypes.add(logicalType);
  }
}
return new RowDataWriter(fieldIndexes, writers, logicalTypes);
```

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/data/FlinkSchemaVisitor.java` (+3/-1 lines)

**修改目的**：在 schema 访问阶段跳过 `UnknownType` 字段，不为其生成访问结果。

**工作逻辑**：
- 在 `struct` 访问逻辑中，原代码无条件地对每个字段调用 `visit(...)` 并加入 `results`。修改后增加判断：`if (iField.type() != Types.UnknownType.get())` 才进行访问并加入结果列表。这样 UnknownType 字段不会出现在最终生成的写器/读器结构中，配合 `FlinkOrcWriters` 中的索引映射保证一致性。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/TestFlinkUnknownType.java` (+179 lines, 新增)

**修改目的**：为 V3 表含 UnknownType 列的读写场景提供单元测试覆盖。

**工作逻辑**：
- 定义包含 `id`(int)、`data`(string)、`unknown_col`(UnknownType)、`data1`(string) 四列的 schema，使用 `format-version=3`。
- 参数化测试覆盖 `PARQUET`、`AVRO`、`ORC` 三种文件格式。
- `testV3TableUnknownTypeRead`：通过 `GenericAppenderHelper` 写入记录后，使用 `ReaderUtil.createDataIterator` 读取，验证 unknown_col 读出为 null，其余字段正确。
- `testV3TableUnknownTypeWrite`：使用 `RowDataTaskWriterFactory` 写入 `GenericRowData`（unknown_col 为 null），提交后通过 `SimpleDataUtil.tableRecords` 读回验证。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/source/reader/ReaderUtil.java` (+10/-2 lines)

**修改目的**：支持测试中使用自定义表 schema 创建 `DataIterator`，而非固定使用 `TestFixtures.SCHEMA`。

**工作逻辑**：
- 新增重载方法 `createDataIterator(CombinedScanTask, Schema tableSchema, Schema projectSchema)`，原方法委托给新方法并传入默认 schema。同时将内联的 `new org.apache.hadoop.conf.Configuration()` 改为导入后直接使用 `new Configuration()`，清理代码风格。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/data/FlinkOrcWriters.java` (+19/-2 lines)

**修改目的**：与 v1.20 相同的修复，应用到 Flink 2.0 分支。

**工作逻辑**：与 v1.20 版本完全一致的改动。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/data/FlinkSchemaVisitor.java` (+3/-1 lines)

**修改目的**：与 v1.20 相同的修复，应用到 Flink 2.0 分支。

**工作逻辑**：与 v1.20 版本完全一致的改动。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/TestFlinkUnknownType.java` (+179 lines, 新增)

**修改目的**：与 v1.20 相同的测试，应用到 Flink 2.0 分支。

**工作逻辑**：与 v1.20 版本完全一致的测试。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/source/reader/ReaderUtil.java` (+10/-2 lines)

**修改目的**：与 v1.20 相同的测试工具方法扩展，应用到 Flink 2.0 分支。

**工作逻辑**：与 v1.20 版本完全一致的改动。

## 总结

本提交通过在 Flink ORC 写入路径中跳过 UnknownType 字段并维护字段索引映射，修复了 V3 表含未知类型列时 ORC 写入异常的问题，同时通过 schema visitor 跳过 UnknownType 保证读写一致性。新增的参数化测试覆盖三种主流文件格式（PARQUET/AVRO/ORC）的读写场景，提升了 Iceberg V3 表在 Flink 集成中的健壮性。改动同时应用到 Flink 1.20 和 2.0 两个版本分支，保持跨版本一致性。
