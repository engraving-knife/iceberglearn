# 提交 1086：Flink: backport PR #10956 for converter interface that deprecates ReaderFunction (#10985)

## 提交信息

- **序号**：1086 / 4088
- **哈希**：7fec19f3fd60ce7979de0f90587849d86cdd27ca
- **短哈希**：7fec19f3f
- **日期**：2024-08-22 07:39:07 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：Flink: backport PR #10956 for converter interface that deprecates ReaderFunction (#10985)
- **PR/Issue**：#10985（回迁 #10956）

## 总体目的

Iceberg Flink Source 此前要产出非 `RowData` 类型（如 Avro `GenericRecord`）的记录，需要用户实现完整的 `ReaderFunction<T>`——它要求自行处理 `FileScanTask` 读取、解密、limit 等底层细节，门槛高且易出错，也使框架难以统一优化读取路径。本提交（回迁 main 上的 #10956）引入一个更轻量的 `RowDataConverter<T>` 转换接口：框架内部仍用统一的 `RowDataFileScanTaskReader` 把 Iceberg 数据读成 `RowData`，再由用户的 converter 把 `RowData` 转成目标输出类型 `T`。这样用户只需写一个 `RowData -> T` 的简单函数即可获得自定义输出类型，框架保留对读取流程的控制。

同时把旧的 `ReaderFunction`/`AvroGenericRecordReaderFunction` 标记为 `@Deprecated`（1.7.0 起，2.0.0 移除），并提供 `IcebergSource.forRowData()` 与 `IcebergSource.forOutputType(RowDataConverter)` 两个新入口取代原 `IcebergSource.builder()`，引导用户迁移到新 API。本提交把该改动回迁到 Flink v1.18 与 v1.20 两个模块（两份代码完全一致），与 v1.19 保持同步。

## 如何达成设计目的

1. **新增 `RowDataConverter<T>` 接口**：`Serializable`，`RowData apply(RowData)` 转换，`TypeInformation<T> getProducedType()` 提供类型信息，便于 Flink 推断输出类型。
2. **新增 `ConverterReaderFunction<T>`**：实现 `DataIteratorReaderFunction<T>`，内部组合 `RowDataFileScanTaskReader`（统一读 RowData）+ `RowDataConverter`（再转 T），通过内部 `ConverterFileScanTaskReader` 用 `CloseableIterator.transform` 把 RowData 流转换成 T 流，并保留 limit/nameMapping/caseSensitive/filters 等读取配置。
3. **新增 `AvroGenericRecordConverter`**：作为参考实现，把 `RowData` 转 Avro `GenericRecord`，提供 `fromIcebergSchema`/`fromAvroSchema` 两种构造方式，复用 Flink 的 `RowDataToAvroConverters`。
4. **`IcebergSource` 改造**：新增 `forRowData()`（输出 RowData）与 `forOutputType(RowDataConverter<T>)`（输出 T）工厂方法；`builder()` 与 `readerFunction(ReaderFunction)` 标 `@Deprecated`；`readerFunction(...)` 增加校验，禁止与 `forOutputType` 混用；把原本内联在 `build()` 中的 reader function 构造逻辑抽到私有 `readerFunction(ScanContext)` 方法，并在有 converter 时返回 `ConverterReaderFunction`。
5. **废弃 `AvroGenericRecordReaderFunction`**：标 `@Deprecated`，指向新 API。

## 修改详情

### `flink/v1.18/.../source/IcebergSource.java` 与 `flink/v1.20/.../source/IcebergSource.java`

**修改目的**：提供新的输出类型入口并废弃旧 API。

**工作逻辑**：
- 新增 import `ConverterReaderFunction`、`RowDataConverter`。
- `builder()` 标 `@Deprecated`（1.7.0 起，2.0.0 移除），Javadoc 引导用 `forRowData()` 或 `forOutputType(RowDataConverter)`。
- 新增 `forOutputType(RowDataConverter<T> converter)`：返回 `new Builder<T>().converter(converter)`。
- `Builder` 增加 `private RowDataConverter<T> converter` 字段与 `private converter(...)` 链式方法（仅由 `forOutputType` 内部调用）。
- `readerFunction(ReaderFunction)` 标 `@Deprecated`，并加 `Preconditions.checkState(converter == null, ...)` 防止与 `forOutputType` 冲突。
- 把 `build()` 中构造 reader function 的逻辑抽到 `private ReaderFunction<T> readerFunction(ScanContext)`：metadata 表用 `MetaDataReaderFunction`；普通表无 converter 用 `RowDataReaderFunction`，有 converter 用 `ConverterReaderFunction`。

### `flink/v1.18/.../source/reader/RowDataConverter.java` 与 `flink/v1.20/.../source/reader/RowDataConverter.java`（新增）

**修改目的**：定义轻量转换接口。

**工作逻辑**：`Serializable` 接口，`RowData apply(RowData rowData)` 与 `TypeInformation<T> getProducedType()` 两个方法。

### `flink/v1.18/.../source/reader/ConverterReaderFunction.java` 与 `flink/v1.20/.../source/reader/ConverterReaderFunction.java`（新增）

**修改目的**：基于 converter 的 reader function 实现。

**工作逻辑**：继承 `DataIteratorReaderFunction<T>`，构造时持有 converter 与读取相关配置（tableSchema、projectedSchema、nameMapping、caseSensitive、io、encryption、filters、limit）。`createDataIterator` 用 `RowDataFileScanTaskReader` 读 RowData，包成 `ConverterFileScanTaskReader`（其 `open` 用 `CloseableIterator.transform(rowDataReader.open(...), converter)` 转换），再套 `LimitableDataIterator` 做 limit。`readSchema` 在 projectedSchema 为 null 时回退到 tableSchema。

### `flink/v1.18/.../source/reader/AvroGenericRecordConverter.java` 与 `flink/v1.20/.../source/reader/AvroGenericRecordConverter.java`（新增）

**修改目的**：RowData → Avro GenericRecord 的参考实现。

**工作逻辑**：实现 `RowDataConverter<GenericRecord>`，内部持有 avroSchema、`RowDataToAvroConverters.RowDataToAvroConverter`、`TypeInformation<GenericRecord>`。提供 `fromIcebergSchema(Schema, tableName)`（用 `FlinkSchemaUtil.convert` + `AvroSchemaUtil.convert`）与 `fromAvroSchema(Schema, tableName)`（用 `AvroSchemaConverter.convertToDataType`）两种构造。`apply` 调用 flinkConverter 转换，`getProducedType` 返回 `GenericRecordAvroTypeInfo`。

### `flink/v1.18/.../source/reader/AvroGenericRecordReaderFunction.java` 与 `flink/v1.20/.../source/reader/AvroGenericRecordReaderFunction.java`

**修改目的**：标记旧实现为废弃。

**工作逻辑**：类 Javadoc 增加 `@deprecated since 1.7.0. Will be removed in 2.0.0`，指向 `IcebergSource#forOutputType(RowDataConverter)` 与 `AvroGenericRecordConverter`，并加 `@Deprecated` 注解。import `IcebergSource`。

### `flink/v1.18/.../source/reader/IcebergSourceSplitReader.java` 与 `flink/v1.20/.../source/reader/IcebergSourceSplitReader.java`

**修改目的**：顺手修复泛型菱形语法。

**工作逻辑**：`new RecordsBySplits(Collections.emptyMap(), Collections.emptySet())` 改为 `new RecordsBySplits<>(...)`，消除原始类型告警。

### `flink/v1.18/.../source/TestIcebergSourceBoundedGenericRecord.java` 与 `flink/v1.20/.../source/TestIcebergSourceBoundedGenericRecord.java`

**修改目的**：测试迁移到新 converter API。

**工作逻辑**：把原先基于 `AvroGenericRecordReaderFunction` + `IcebergSource.builder().readerFunction(...)` 的用例改为 `IcebergSource.forOutputType(AvroGenericRecordConverter.fromIcebergSchema(...))`，验证新路径产出正确的 GenericRecord，并保留对旧路径的覆盖意图。

## 小结

- **成效**：在 Flink v1.18 与 v1.20 上引入 `RowDataConverter` 转换接口与 `IcebergSource.forOutputType`/`forRowData` 入口，显著降低自定义输出类型的开发门槛，并废弃 `ReaderFunction`/`AvroGenericRecordReaderFunction`，统一读取路径，便于后续优化。
- **影响范围**：`flink/v1.18` 与 `flink/v1.20` 两个模块各 7 个文件（新增 3 个、修改 4 个），含测试。新增 API 标 `@Internal`/公开，旧 API 标 `@Deprecated`，保持二进制兼容。
- **回迁到 1.4.x 的注意事项**：不建议回迁。这是 main 分支上的 API 演进（废弃 + 新增入口），1.4.x 已发布且 API 冻结，引入新 converter 接口会改变 1.4.x 的公开 API 表面。若 1.4.x 确需此能力，需评估对 1.4.x `IcebergSource` API 兼容性的影响，并同步回迁 `RowDataFileScanTaskReader`/`CloseableIterator.transform` 等依赖。一般情况下 1.4.x 用户可继续使用旧的 `ReaderFunction` 路径，无需回迁。
