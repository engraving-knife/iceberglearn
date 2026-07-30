# 提交 1967：Flink: Backport RowConverter for Iceberg Source (#12713)

## 提交信息

- **序号**：1967 / 4088
- **哈希**：b99f33dcaa747d169fe2bd14407f961607b479de
- **短哈希**：b99f33dca
- **日期**：2025-04-07 10:21:38 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Backport RowConverter for Iceberg Source (#12713)
  - Backports #11301
- **PR/Issue**：#12713（回填 #11301）

## 总体目的

Iceberg 的 Flink Source 在读取数据时，从底层读取到的是 Flink 内部的 `RowData`。如果用户希望源直接产出 Flink Table API/SQL 使用的 `Row` 类型（而非 `GenericRecord` 或 `RowData`），需要一个能将 `RowData` 转换为 `Row` 的转换器。此前 Flink Source 已有 `AvroGenericRecordConverter`（产出 Avro `GenericRecord`），但缺少一个直接产出 `Row` 的 `RowConverter`。

本提交（回填 #11301 至 Flink 1.18/1.19）的目的：
1. 新增 `RowConverter`，实现 `RowDataConverter<Row>` 接口，利用 Flink 的 `DataStructureConverter` 把 `RowData` 转换为 `Row`，并基于 Iceberg schema 构造对应的 `RowTypeInfo`。
2. 重构现有 `TestIcebergSourceBoundedGenericRecord` 测试：把通用的测试逻辑（建表、写数据、运行 source、断言）抽取到一个新的抽象基类 `TestIcebergSourceBoundedConverterBase<T>`，让不同输出类型（`GenericRecord`、`Row`）的测试共享同一套用例，仅各自实现 `getConverter`/`getTypeInfo`/`mapToRow` 三个钩子。
3. 新增 `TestIcebergSourceBoundedRow` 测试，验证使用 `RowConverter` 时 source 能正确产出 `Row`。

## 如何达成设计目的

设计上遵循已有的 `RowDataConverter<T>` 抽象与转换器模式：

1. **`RowConverter`**：实现 `RowDataConverter<Row>`。通过静态工厂 `fromIcebergSchema(Schema)` 构造：先把 Iceberg schema 转 Flink `RowType` 与 `TableSchema`，再据此构造 `RowTypeInfo`，并用 `DataStructureConverters` 获取 `RowData <-> Row` 的转换器。`apply(RowData)` 调用 `converter.toExternal(rowData)` 返回 `Row`；`getProducedType()` 返回 `RowTypeInfo`。
2. **测试基类 `TestIcebergSourceBoundedConverterBase<T>`**：抽象类，包含建表、写随机数据、配置并运行 Iceberg Source（带可选 converter）、收集结果并断言的全部通用逻辑，并通过抽象方法 `getConverter`/`getTypeInfo`/`mapToRow` 让子类指定输出类型与转换方式。
3. **`TestIcebergSourceBoundedGenericRecord`**：改为继承基类，实现 `getConverter` 返回 `AvroGenericRecordConverter`，删除原本重复的测试代码（约 192 行删减）。
4. **`TestIcebergSourceBoundedRow`**（新增）：继承基类，实现 `getConverter` 返回 `RowConverter`，`mapToRow` 直接返回输入流（已是 Row）。

上述改动在 Flink 1.18 与 1.19 两个版本同步应用。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/source/reader/RowConverter.java` (新增, +59/-0 lines)

**修改目的**：提供 RowData 到 Row 的转换器。

**工作逻辑**：
- 实现 `RowDataConverter<Row>`，字段持有 `DataStructureConverter<Object,Object> converter` 与 `TypeInformation<Row> outputTypeInfo`。
- 静态工厂 `fromIcebergSchema(icebergSchema)`：用 `FlinkSchemaUtil.convert` 得到 `RowType`，用 `FlinkSchemaUtil.toSchema` 得到 `TableSchema`，构造 `RowTypeInfo(fieldTypes, fieldNames)`，再用 `DataStructureConverters.getConverter(fromLogicalToDataType(rowType))` 创建转换器。
- `apply(RowData)`：`(Row) converter.toExternal(rowData)`。
- `getProducedType()`：返回 `outputTypeInfo`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceBoundedConverterBase.java` (新增, +222/-0 lines)

**修改目的**：抽取不同输出类型共享的 source 测试基类。

**工作逻辑**：抽象类 `TestIcebergSourceBoundedConverterBase<T>`，含 `@TempDir`、`HadoopCatalogExtension`、参数化（format/parallelism/useConverter）。提供建表、`GenericAppenderHelper` 写入随机 `Record`、构建并运行 Iceberg Source（根据 useConverter 决定是否用 `getConverter` 设置 `RowDataConverter`，否则用默认 reader function + mapper）、`CloseableIterator` 收集结果、断言记录等通用方法。定义抽象钩子 `getConverter`、`getTypeInfo`、`mapToRow` 供子类实现。包含 `testUnpartitionedTable`、`testPartitionedTable` 等测试模板。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceBoundedGenericRecord.java` (修改, +40/-152 lines)

**修改目的**：改为继承基类，消除重复代码。

**工作逻辑**：类改为 `extends TestIcebergSourceBoundedConverterBase<GenericRecord>`，保留参数化 `parameters()`，实现 `getConverter` 返回 `AvroGenericRecordConverter.fromIcebergSchema(...)`、`getTypeInfo` 返回 `GenericRecordAvroTypeInfo`、`mapToRow` 用 `RowDataToRowMapper`/`AvroGenericRecordToRowDataMapper` 转换。删除原本内联的建表/运行/断言逻辑（约 152 行删除）。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceBoundedRow.java` (新增, +52/-0 lines)

**修改目的**：验证 RowConverter 输出 Row 的场景。

**工作逻辑**：`TestIcebergSourceBoundedRow extends TestIcebergSourceBoundedConverterBase<Row>`，实现 `getConverter` 返回 `RowConverter.fromIcebergSchema(icebergSchema)`，`getTypeInfo` 返回基于 `FlinkSchemaUtil.toSchema` 的 `RowTypeInfo`，`mapToRow` 直接返回输入流（已是 Row）。

### Flink 1.18 同名四个文件 (新增/修改, 同上)

**修改目的**：在 Flink 1.18 同步应用相同改动。

**工作逻辑**：与 v1.19 完全一致的新增 `RowConverter`、`TestIcebergSourceBoundedConverterBase`、`TestIcebergSourceBoundedRow`，以及对 `TestIcebergSourceBoundedGenericRecord` 的重构。

## 总结

本提交（回填 #11301 至 Flink 1.18/1.19）为 Iceberg Flink Source 新增 `RowConverter`，将读取到的 `RowData` 转换为 Flink Table API 的 `Row` 类型，并基于 Iceberg schema 构造 `RowTypeInfo`。同时重构测试，把通用 source 测试逻辑抽取到 `TestIcebergSourceBoundedConverterBase<T>` 基类，`GenericRecord` 测试改为继承基类去除重复，新增 `Row` 测试覆盖 `RowConverter` 路径。两个 Flink 版本同步改动。
