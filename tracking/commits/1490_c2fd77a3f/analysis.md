# 提交 1490：Flink: Add RowConverter for Iceberg Source (#11301)

## 提交信息

- **序号**：1490 / 4088
- **哈希**：c2fd77a3f4c804572c4fbe6081e150510ca9e262
- **短哈希**：c2fd77a3f
- **日期**：2024-12-14（Sat Dec 14 16:49:11 2024 -0600）
- **作者**：abharath9 <abharath9@gmail.com>
- **提交说明**：Flink: Add RowConverter for Iceberg Source (#11301)
- **PR/Issue**：#11301
- **共同作者**：Bharath Kumar Avusherla <bavusherla@expediagroup.com>

## 总体目的

Iceberg 的 Flink Source（基于 Flink 新版 Source API，`IcebergSource`）通过 `RowDataConverter<T>` 接口支持把内部 `RowData` 转换为不同的输出类型。此前仓库只内置了 `AvroGenericRecordConverter`（输出 Avro `GenericRecord`）和默认的 `RowData` 输出路径，但**缺少直接输出 Flink 公开 `Row` 类型的内置 converter**。

`org.apache.flink.types.Row` 是 Flink Table API / DataStream API 中面向用户的公开行类型，下游常需要把 Iceberg 读取的数据以 `Row` 形式消费（如表函数、UDF、跨源 join 等）。此前用户要得到 `Row` 必须走以下绕路之一：

1. `IcebergSource.forRowData()` 得到 `DataStream<RowData>`，再 `.map(new RowDataToRowMapper(rowType))` 转 `Row`——多一次手动 map；
2. `IcebergSource.forOutputType(AvroGenericRecordConverter...)` 得到 `DataStream<GenericRecord>`，再连续 `.map(AvroGenericRecordToRowDataMapper).map(new RowDataToRowMapper(rowType))` 两步转换——路径更长、性能更差。

两条路径都需要用户自己拼装转换链，且都绕经 `RowData` 中间态。本提交新增 `RowConverter`，直接用 Flink 官方的 `DataStructureConverter`（`RowData` ↔ `Row` 的标准桥接）一步完成转换，用户只需：

```java
IcebergSource.forOutputType(RowConverter.fromIcebergSchema(icebergSchema))
    .tableLoader(...)
    ...
    .build();
```

即可得到 `DataStream<Row>`。

同时本提交把 `TestIcebergSourceBoundedGenericRecord` 中重复的测试脚手架（建表、插数据、跑 source、收集结果、断言）抽到新的抽象基类 `TestIcebergSourceBoundedConverterBase<T>`，并新增 `TestIcebergSourceBoundedRow` 用 `RowConverter` 跑同一套测试，确保 `Row` 输出路径与 `GenericRecord` 输出路径行为一致（无分区表、分区表、列裁剪投影三个场景）。

## 如何达成设计目的

### 生产代码

新增 `RowConverter implements RowDataConverter<Row>`：

- 构造时持有 Flink `DataStructureConverter<Object, Object>`（由 `DataStructureConverters.getConverter` 从 `RowType` 对应的 `DataType` 创建）与 `TypeInformation<Row>`。
- `apply(RowData)` 调 `converter.toExternal(rowData)` 完成 `RowData` → `Row` 转换。
- `getProducedType()` 返回 `TypeInformation<Row>`，供 Flink Source 通知下游输出类型。
- 提供静态工厂 `fromIcebergSchema(org.apache.iceberg.Schema)`：用 `FlinkSchemaUtil.convert` 把 Iceberg schema 转 `RowType`，再用 `FlinkSchemaUtil.toSchema` 得到 `TableSchema`，从中取字段类型与字段名构造 `RowTypeInfo`。

这与已有 `AvroGenericRecordConverter` 的设计完全平行（都实现 `RowDataConverter<T>`，都提供 `fromIcebergSchema` 静态工厂，都持有内部 converter 与 `TypeInformation`），只是内部转换机制不同：`AvroGenericRecordConverter` 用 `RowDataToAvroConverters`，`RowConverter` 用 `DataStructureConverters`。

### 测试重构

把 `TestIcebergSourceBoundedGenericRecord` 中与输出类型无关的通用逻辑抽到 `TestIcebergSourceBoundedConverterBase<T>`：

- 参数化：`FileFormat`（AVRO/PARQUET/ORC）、`parallelism`、`useConverter`（true 走 `forOutputType` 路径，false 走 `readerFunction` 路径）。
- 三个测试方法：`testUnpartitionedTable` / `testPartitionedTable` / `testProjection`，覆盖非分区表、分区表、列裁剪投影。
- 通用 `run()` 方法：构造 `StreamExecutionEnvironment`、`IcebergSource.Builder`、跑 source、收集 `List<Row>` 结果。
- 模板方法（abstract）：
  - `getConverter(Schema, Table)` → 返回 `RowDataConverter<T>`（走 `useConverter=true` 路径）
  - `getReaderFunction(Schema, Table, List<Expression>)` → 返回 `ReaderFunction<T>`（走 `useConverter=false` 路径，默认抛 `UnsupportedOperationException`，子类按需覆盖）
  - `getTypeInfo(Schema)` → 返回 `TypeInformation<T>`
  - `mapToRow(DataStream<T>, Schema)` → 把 `DataStream<T>` 映射为 `DataStream<Row>` 供断言（`Row` 子类直接返回，`GenericRecord` 子类做两步 map）

`TestIcebergSourceBoundedGenericRecord` 改为继承基类，只实现四个模板方法（注入 `AvroGenericRecordConverter` / `AvroGenericRecordReaderFunction` / `GenericRecordAvroTypeInfo` / Avro→RowData→Row 映射链）。`TestIcebergSourceBoundedRow` 同样继承基类，注入新的 `RowConverter` / `RowTypeInfo` / 直接返回（已是 `Row`）。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/source/reader/RowConverter.java`（新增，59 行）

**修改目的**：提供 Iceberg schema → Flink `Row` 输出的内置 converter。

**工作逻辑**：

```java
public class RowConverter implements RowDataConverter<Row> {
  private final DataStructureConverter<Object, Object> converter;
  private final TypeInformation<Row> outputTypeInfo;

  private RowConverter(RowType rowType, TypeInformation<Row> rowTypeInfo) {
    this.converter =
        DataStructureConverters.getConverter(TypeConversions.fromLogicalToDataType(rowType));
    this.outputTypeInfo = rowTypeInfo;
  }

  public static RowConverter fromIcebergSchema(org.apache.iceberg.Schema icebergSchema) {
    RowType rowType = FlinkSchemaUtil.convert(icebergSchema);
    TableSchema tableSchema = FlinkSchemaUtil.toSchema(icebergSchema);
    RowTypeInfo rowTypeInfo =
        new RowTypeInfo(tableSchema.getFieldTypes(), tableSchema.getFieldNames());
    return new RowConverter(rowType, rowTypeInfo);
  }

  @Override
  public Row apply(RowData rowData) {
    return (Row) converter.toExternal(rowData);
  }

  @Override
  public TypeInformation<Row> getProducedType() {
    return outputTypeInfo;
  }
}
```

要点：
- `DataStructureConverters.getConverter` 是 Flink Table API 的标准转换器工厂，针对 `RowType` 对应的 `DataType` 返回能做 `RowData` ↔ `Row` 双向转换的 converter。这里只用 `toExternal` 方向。
- `fromIcebergSchema` 是用户入口：传入 Iceberg `Schema`，内部转 `RowType` 与 `TableSchema`，再用 `TableSchema` 的字段类型与字段名构造 `RowTypeInfo`（保证字段名不丢失，下游 Table API 能按名引用）。
- `apply` 与 `getProducedType` 满足 `RowDataConverter<Row>`（继承 `Function<RowData, Row>` 与 `ResultTypeQueryable<Row>`）的契约。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceBoundedConverterBase.java`（新增，223 行）

**修改目的**：抽出与输出类型无关的通用测试逻辑，便于多种 converter 共享同一套回归。

**工作逻辑**：

- 泛型参数 `<T>` 表示被测 source 的输出类型。
- `@Parameters` 提供 AVRO/PARQUET/ORC × parallelism=2 × useConverter=true 的参数组合。
- 三个 `@TestTemplate` 方法：
  - `testUnpartitionedTable`：建非分区表、写 2 条随机记录、跑 source、`TestHelpers.assertRecords` 校验。
  - `testPartitionedTable`：建分区表（按 `dt` 分区）、写 2 条记录（dt=2020-03-20）、跑 source、校验。
  - `testProjection`：建分区表、写记录、用 `TypeUtil.select(schema, {1})` 裁剪出只含 `data` 字段的 schema、跑 source、`TestHelpers.assertRows` 校验只含 data 列的结果。
- 私有 `run()` 方法封装：构造 `StreamExecutionEnvironment`（设 parallelism、开 objectReuse）、`IcebergSource.Builder`（按 `useConverter` 走 `forOutputType(converter)` 或 `builder().readerFunction(...)` 两条路径）、可选 `.project(projectedSchema)`、`env.fromSource(...)`、`mapToRow` 转 `Row`、`executeAndCollect` 收集。
- 四个 `protected abstract` / `protected` 模板方法供子类实现。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceBoundedGenericRecord.java`（修改，-159 / +33 行）

**修改目的**：改为继承新基类，删除重复脚手架，只保留 GenericRecord 特有逻辑。

**工作逻辑**：

- 类声明改为 `extends TestIcebergSourceBoundedConverterBase<GenericRecord>`。
- 删除原本自带的 `@TempDir` / `@RegisterExtension HadoopCatalogExtension` / `@Parameter` 字段 / `testUnpartitionedTable` / `testPartitionedTable` / `testProjection` / `run` / `createSourceBuilderWithConverter` / `createSourceBuilderWithReaderFunction` 等大量重复代码（共 -159 行）。
- 只保留 `@Parameters`（增加 `useConverter=true` 维度）与四个 `@Override`：
  - `getConverter` → `AvroGenericRecordConverter.fromIcebergSchema(icebergSchema, table.name())`
  - `getReaderFunction` → `new AvroGenericRecordReaderFunction(...)`
  - `getTypeInfo` → `new GenericRecordAvroTypeInfo(avroSchema)`
  - `mapToRow` → `inputStream.map(AvroGenericRecordToRowDataMapper...).map(new RowDataToRowMapper(rowType))`

`testPartitionedTable` 中原手动循环设 `dt` 改为基类中的增强 for 循环写法，行为等价。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceBoundedRow.java`（新增，52 行）

**修改目的**：用新 `RowConverter` 跑与 GenericRecord 相同的测试矩阵。

**工作逻辑**：

```java
public class TestIcebergSourceBoundedRow extends TestIcebergSourceBoundedConverterBase<Row> {
  @Override
  protected RowDataConverter<Row> getConverter(Schema icebergSchema, Table table) {
    return RowConverter.fromIcebergSchema(icebergSchema);
  }

  @Override
  protected TypeInformation<Row> getTypeInfo(Schema icebergSchema) {
    TableSchema tableSchema = FlinkSchemaUtil.toSchema(icebergSchema);
    return new RowTypeInfo(tableSchema.getFieldTypes(), tableSchema.getFieldNames());
  }

  @Override
  protected DataStream<Row> mapToRow(DataStream<Row> inputStream, Schema icebergSchema) {
    return inputStream;  // 已经是 Row，无需转换
  }
}
```

注意 `mapToRow` 直接返回输入流——因为 `RowConverter` 输出已是 `Row`，无需再 map。这与 `GenericRecord` 子类需要两步 map 形成对比，体现了 `RowConverter` 直出 `Row` 的简洁性。

该测试类继承基类后自动获得 AVRO/PARQUET/ORC × parallelism=2 × useConverter=true 的参数化矩阵与三个测试方法，无需额外编写测试体。

## 小结

- **成效**：Flink Iceberg Source 现在内置 `RowConverter`，用户可通过 `IcebergSource.forOutputType(RowConverter.fromIcebergSchema(schema))` 直接得到 `DataStream<Row>`，无需再手动拼装 `RowData`→`Row` 或 `GenericRecord`→`RowData`→`Row` 的多步转换链，路径更短、性能更优、API 更直观。测试侧通过抽公共基类消除了 `GenericRecord` 与 `Row` 两条输出路径的重复脚手架，并保证二者在非分区表、分区表、投影三个场景下行为一致。
- **影响范围**：4 个文件、+367 / -159 行。1 个新生产类（`RowConverter`）、1 个新测试基类、1 个新测试子类、1 个测试类重构。仅影响 Flink 1.20 模块，不影响 Core / 其它引擎。
- **回迁到 1.4.x 的注意事项**：
  - 这是新增功能（不破坏既有 API），**建议回迁**，对 1.4.x Flink 用户直接读 `Row` 的场景有实际价值。
  - 前提条件：1.4.x 的 Flink 模块必须已具备 `RowDataConverter` 接口与 `IcebergSource.forOutputType` API。这些是较新的 Source API（Flink 1.17+ 的 Iceberg 集成），需确认 1.4.x 对应的 Flink 版本目录（如 `flink/v1.18`、`flink/v1.19`、`flink/v1.20`）中这些基础设施已存在。
  - `RowConverter` 依赖 Flink 的 `DataStructureConverter` / `DataStructureConverters` / `TypeConversions`，这些 API 在 Flink 1.15+ 可用，1.4.x 支持的 Flink 版本应满足。
  - **版本目录差异**：本提交只改了 `flink/v1.20`。若 1.4.x 还维护 `flink/v1.18` / `flink/v1.19` 等其它 Flink 版本目录，需要把 `RowConverter.java` 同步复制到对应目录（并确认 `FlinkSchemaUtil.toSchema` 等依赖方法在那些 Flink 版本中可用）。
  - 测试重构（`TestIcebergSourceBoundedConverterBase` 抽取）属于测试内部改进，可选择只回迁 `RowConverter` 生产类与 `TestIcebergSourceBoundedRow` 测试，不回迁基类抽取（若 1.4.x 的 `TestIcebergSourceBoundedGenericRecord` 与 main 偏离较大，强行回迁基类抽取可能引入冲突）。
  - `RowConverter` 是纯新增类，cherry-pick 生产代码部分无冲突风险。
