# 提交 1079：Flink: deprecate ReaderFunction with a new Converter interface to simplify user experience (#10956)

## 提交信息

- **序号**：1079 / 4088
- **哈希**：85cf79de0156e4bb4d02931c8d8e10e1d6b478c5
- **短哈希**：85cf79de0
- **日期**：2024-08-21 14:56:32 -0700
- **作者**：Steven Zhen Wu <stevenz3wu@gmail.com>
- **提交说明**：Flink: deprecate ReaderFunction with a new Converter interface to simplify user experience (#10956)
- **PR/Issue**：#10956

## 总体目的

Iceberg 的 Flink Source（`IcebergSource<T>`）支持把 Iceberg 表中的数据读取为任意输出类型 `T`（如 Flink 的 `RowData`、Avro `GenericRecord`、用户自定义 POJO 等）。在 main 分支 1.7.0 之前，用户若想得到非 `RowData` 的输出类型，需要：

1. 自己实现一个 `ReaderFunction<T>` 接口（内部要做 Iceberg `FileScanTask` 读取、解密、limit 控制、Flink `RowDataFileScanTaskReader` 调用、再到 `T` 的转换等一整套逻辑）；
2. 通过 `IcebergSource.<T>builder().readerFunction(myReaderFunction)` 注入。

这套 API 有几个痛点：

1. **`ReaderFunction` 抽象层级过低**：用户为了实现"把 `RowData` 转 `T`"这个简单需求，必须重写整个读取链路（包括 schema 投影、nameMapping、加解密、filter、limit 等），代码量大、容易出错、与 Iceberg 内部 API 耦合紧密。
2. **样板代码重复**：每个用户自定义 `ReaderFunction` 都要复制粘贴一遍 `RowDataFileScanTaskReader` + `DataIterator` + `RecordLimiter` 的样板逻辑，违背 DRY 原则。
3. **入口 API 不直观**：`IcebergSource.<T>builder()` 是泛型方法，用户需要自己指定 `<T>` 并后续调用 `readerFunction`，没有"我要 RowData 输出"和"我要自定义输出类型"的清晰入口区分。
4. **维护负担**：Iceberg 内部对 `ReaderFunction` 链路的任何改动（如新增配置、修改 schema 处理）都可能破坏用户自定义实现，二进制兼容性难保证。

本提交的目的是引入新的 `RowDataConverter<T>` 接口（一个简单的 `Function<RowData, T>` + `ResultTypeQueryable<T>` + `Serializable`），把"读取 Iceberg 数据为 `RowData`"和"把 `RowData` 转为 `T`"两件事解耦：

- Iceberg 内部负责完成所有 Iceberg 侧的读取逻辑（产生 `RowData`）；
- 用户只需提供一个 `RowDataConverter<T>` 实现"把 `RowData` 转为 `T`"，极大降低用户定制输出的成本。

配套提供新的入口方法 `IcebergSource.forRowData()`（输出 `RowData`）和 `IcebergSource.forOutputType(RowDataConverter<T>)`（输出 `T`），并把旧的 `IcebergSource.builder()` 和 `Builder.readerFunction()` 标记为 `@Deprecated`（since 1.7.0，将在 2.0.0 移除），引导用户迁移到新 API。

同时本提交提供了一个开箱即用的 `AvroGenericRecordConverter`，让需要 Avro `GenericRecord` 输出的用户直接用 `IcebergSource.forOutputType(AvroGenericRecordConverter.fromIcebergSchema(...))` 替代此前要自己写 `AvroGenericRecordReaderFunction` 的繁琐流程。

## 如何达成设计目的

设计思路分四步：

1. **定义新接口 `RowDataConverter<T>`**：继承 `Function<RowData, T>`（提供 `apply(RowData): T`）、`ResultTypeQueryable<T>`（提供 `getProducedType()` 让 Flink 知道输出类型信息）、`Serializable`（Flink source 需要序列化分发）。接口极简，用户实现成本最低。

2. **实现 `ConverterReaderFunction<T>`**：内部 `@Internal` 类，继承已有的 `DataIteratorReaderFunction<T>`，把 `RowDataFileScanTaskReader`（产出 `RowData`）和 `RowDataConverter<T>` 串联起来——通过 `CloseableIterator.transform(rowDataIterator, converter)` 把 `RowData` 流转换为 `T` 流。这样 Iceberg 把所有"读取 Iceberg 为 RowData"的复杂逻辑（schema 投影、nameMapping、加解密、filter、limit）封装在内部，用户只需提供 converter。

3. **改造 `IcebergSource.Builder`**：
   - 新增 `converter` 字段和私有 `converter()` setter；
   - 新增公共工厂 `forOutputType(RowDataConverter<T>)`，自动设置 converter；
   - `forRowData()` 工厂保持不变（输出 `RowData`，不需要 converter）；
   - 旧 `builder()` 工厂和 `readerFunction()` setter 标 `@Deprecated`；
   - 在 `readerFunction()` setter 中加状态检查：若 builder 已通过 `forOutputType` 创建（converter 非 null），则禁止再设 readerFunction，避免冲突；
   - 把 `build()` 中"根据 table 类型创建 readerFunction"的逻辑提取为私有方法 `readerFunction(ScanContext)`，新增 converter 分支：converter 非 null 时返回 `ConverterReaderFunction`，否则维持原 `RowDataReaderFunction`/`MetaDataReaderFunction` 行为。

4. **提供 Avro 场景的开箱即用 converter `AvroGenericRecordConverter`**：实现 `RowDataConverter<GenericRecord>`，封装 Flink 的 `RowDataToAvroConverters` 把 `RowData` 转 Avro `GenericRecord`；提供 `fromIcebergSchema` 和 `fromAvroSchema` 两个工厂方法。把旧的 `AvroGenericRecordReaderFunction` 标 `@Deprecated`，引导用户用新 converter。

5. **测试增强**：在 `TestIcebergSourceBoundedGenericRecord` 中新增 `useConverter` 参数化维度，让同一组测试既跑旧 `readerFunction` 路径（`useConverter=false`）也跑新 `forOutputType(converter)` 路径（`useConverter=true`），保证两条链路行为等价；新增 `{PARQUET, 2, false}` 参数组合专门覆盖旧路径。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/source/IcebergSource.java`

**修改目的**：新增 `forOutputType(RowDataConverter<T>)` 工厂和 `converter` 字段；废弃旧 `builder()` 和 `readerFunction()` setter；重构 `build()` 中 readerFunction 创建逻辑。

**工作逻辑**：

1. **新增 import 与字段**：引入 `ConverterReaderFunction`、`RowDataConverter`；Builder 新增 `private RowDataConverter<T> converter` 字段。

2. **新增工厂方法 `forOutputType`**：

```java
public static <T> Builder<T> forOutputType(RowDataConverter<T> converter) {
  return new Builder<T>().converter(converter);
}
```

调用时直接传入 converter，Builder 内部记录 converter，后续 `build()` 时会自动构造 `ConverterReaderFunction`。

3. **废弃旧 `builder()` 工厂**：加 `@Deprecated` 注解和 Javadoc，提示 since 1.7.0、2.0.0 移除，建议改用 `forRowData()` 或 `forOutputType(RowDataConverter)`。

4. **废弃旧 `readerFunction()` setter 并加状态检查**：

```java
@Deprecated
public Builder<T> readerFunction(ReaderFunction<T> newReaderFunction) {
  Preconditions.checkState(
      converter == null,
      "Cannot set reader function when builder was created via IcebergSource.forOutputType(Converter)");
  this.readerFunction = newReaderFunction;
  return this;
}
```

如果用户已经通过 `forOutputType` 设了 converter，再尝试设 readerFunction 会抛 `IllegalStateException`，防止两种模式混用导致行为不确定。

5. **新增私有 `converter()` setter**：仅供 `forOutputType` 内部调用，不对外暴露。

6. **重构 `build()` 中的 readerFunction 创建**：把原内联的 if-else（BaseMetadataTable → MetaDataReaderFunction，否则 → RowDataReaderFunction）提取为私有方法 `readerFunction(ScanContext context)`，并新增 converter 分支：

```java
private ReaderFunction<T> readerFunction(ScanContext context) {
  if (table instanceof BaseMetadataTable) {
    return (ReaderFunction<T>) new MetaDataReaderFunction(...);
  } else {
    if (converter == null) {
      return (ReaderFunction<T>) new RowDataReaderFunction(...);
    } else {
      return new ConverterReaderFunction<>(converter, ...);
    }
  }
}
```

注意：metadata table 分支不使用 converter（因为 metadata table 走的是 `MetaDataReaderFunction` 而非 `RowDataFileScanTaskReader`，converter 模型不适用）。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/source/reader/RowDataConverter.java`（新增）

**修改目的**：定义新的简单转换器接口，作为用户自定义输出类型的契约。

**工作逻辑**：

```java
public interface RowDataConverter<T>
    extends Function<RowData, T>, ResultTypeQueryable<T>, Serializable {}
```

- `Function<RowData, T>`：提供 `T apply(RowData)` 方法，把 RowData 转 T；
- `ResultTypeQueryable<T>`：提供 `TypeInformation<T> getProducedType()`，让 Flink 推断输出类型；
- `Serializable`：Flink source 需要把 converter 序列化分发到 taskmanager。

接口无任何方法定义（继承三个父接口的方法），是一个"标记接口 + 复合接口"。Javadoc 说明 `<T>` 为输出类型。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/source/reader/ConverterReaderFunction.java`（新增）

**修改目的**：内部类，串联 `RowDataFileScanTaskReader` 与 `RowDataConverter<T>`，产生 `T` 类型的数据流。

**工作逻辑**：

- 标注 `@Internal`，表示对用户不可见、不保证兼容。
- 字段与 `RowDataReaderFunction` 类似（tableSchema、readSchema、nameMapping、caseSensitive、io、encryption、filters、limit），但额外持有 `RowDataConverter<T> converter`。
- 构造函数接收所有 Iceberg 读取上下文参数 + converter，调用父类 `DataIteratorReaderFunction` 的构造传入 `ListDataIteratorBatcher`。
- `createDataIterator(split)`：
  1. 创建 `RowDataFileScanTaskReader`（产出 RowData）；
  2. 用 `ConverterFileScanTaskReader` 包装，应用 converter；
  3. 用 `LimitableDataIterator` 包装以应用 limit。
- 内部静态类 `ConverterFileScanTaskReader<T>`：实现 `FileScanTaskReader<T>`，`open()` 时调用 `rowDataReader.open(...)` 拿到 `CloseableIterator<RowData>`，再用 `CloseableIterator.transform(..., converter)` 转为 `CloseableIterator<T>`。这是把 converter 串入读取链路的关键。
- `readSchema(tableSchema, projectedSchema)`：投影 schema 处理，与 `RowDataReaderFunction` 一致。
- `lazyLimiter()`：延迟创建 `RecordLimiter`，避免序列化需求。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/source/reader/AvroGenericRecordConverter.java`（新增）

**修改目的**：提供开箱即用的 `RowDataConverter<GenericRecord>` 实现，让 Avro 输出场景用户无需再写 `ReaderFunction`。

**工作逻辑**：

```java
public class AvroGenericRecordConverter implements RowDataConverter<GenericRecord> {
  private final Schema avroSchema;
  private final RowDataToAvroConverters.RowDataToAvroConverter flinkConverter;
  private final TypeInformation<GenericRecord> outputTypeInfo;
  ...
}
```

- 字段：Avro schema、Flink 的 RowData→Avro converter、输出 TypeInformation。
- 私有构造，通过两个静态工厂创建：
  - `fromIcebergSchema(icebergSchema, tableName)`：从 Iceberg schema 出发，用 `FlinkSchemaUtil.convert` 转 Flink RowType，用 `AvroSchemaUtil.convert` 转 Avro schema。
  - `fromAvroSchema(avroSchema, tableName)`：从已有 Avro schema 出发，用 Flink 的 `AvroSchemaConverter.convertToDataType` 反推 RowType。
- `apply(RowData)`：调用 `flinkConverter.convert(avroSchema, rowData)` 返回 `GenericRecord`。
- `getProducedType()`：返回 `GenericRecordAvroTypeInfo`，让 Flink 知道输出类型。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/source/reader/AvroGenericRecordReaderFunction.java`

**修改目的**：把旧的 `AvroGenericRecordReaderFunction` 标记为 `@Deprecated`，引导用户迁移到 `IcebergSource.forOutputType(AvroGenericRecordConverter)`。

**工作逻辑**：在类 Javadoc 中加 `@deprecated since 1.7.0. Will be removed in 2.0.0; use {@link IcebergSource#forOutputType(RowDataConverter)} and {@link AvroGenericRecordConverter} instead.`，并加 `@Deprecated` 注解。类实现保持不变，向后兼容。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/source/reader/IcebergSourceSplitReader.java`

**修改目的**：附带的小清理——给 `RecordsBySplits` 加菱形钻石操作符 `<>`。

**工作逻辑**：

```diff
-        return new RecordsBySplits(Collections.emptyMap(), Collections.emptySet());
+        return new RecordsBySplits<>(Collections.emptyMap(), Collections.emptySet());
```

`RecordsBySplits` 是泛型类，原代码用裸类型构造（依赖上下文推断），改为显式 `<>` 钻石操作符更规范，消除编译器 raw-type 警告。与本提交主题无直接关系，属于顺手清理。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceBoundedGenericRecord.java`

**修改目的**：扩展参数化测试，同时覆盖新的 converter 路径和旧的 readerFunction 路径，验证两者行为等价。

**工作逻辑**：

1. **新增参数 `useConverter`**：`@Parameters` 名称模板加 `useConverter = {2}`；参数矩阵从 `{AVRO, 2}`/`{PARQUET, 2}`/`{ORC, 2}` 改为 `{AVRO, 2, true}`/`{PARQUET, 2, true}`/`{PARQUET, 2, false}`/`{ORC, 2, true}`。新增 `{PARQUET, 2, false}` 一组专门跑旧 `readerFunction` 路径，确保废弃路径仍工作。
2. **新增 `@Parameter(2) private boolean useConverter`** 字段。
3. **测试方法体拆分**：原 `runTest` 内联创建 `AvroGenericRecordReaderFunction` 并调用 `IcebergSource.<GenericRecord>builder().readerFunction(...)`，重构为根据 `useConverter` 二选一：
   - `useConverter=true`：调用 `createSourceBuilderWithConverter`，用 `AvroGenericRecordConverter.fromIcebergSchema` + `IcebergSource.forOutputType(converter)`。
   - `useConverter=false`：调用 `createSourceBuilderWithReaderFunction`，沿用旧 `AvroGenericRecordReaderFunction` + `IcebergSource.<GenericRecord>builder().readerFunction(...)`。
4. **`readSchema` 计算位置上移**：原在 builder 配置后计算，新代码在创建 builder 前计算（converter 工厂需要 readSchema），属于必要的位置调整。
5. 两个新私有辅助方法封装各自的 builder 创建逻辑，使测试主体保持简洁。

## 小结

- **成效**：成功引入了 `RowDataConverter<T>` 接口和 `IcebergSource.forOutputType(converter)` / `forRowData()` 入口，把"读 Iceberg 为 RowData"与"RowData 转 T"解耦，大幅降低用户自定义输出类型的代码量（从实现整个 `ReaderFunction` 简化为实现一个 `Function<RowData, T>`）。提供开箱即用的 `AvroGenericRecordConverter`，让 Avro 场景从写 80+ 行 `AvroGenericRecordReaderFunction` 简化为一行 `IcebergSource.forOutputType(AvroGenericRecordConverter.fromIcebergSchema(...))`。同时通过 `@Deprecated` 平滑引导用户从旧 API 迁移，保持向后兼容。
- **影响范围**：仅 `flink/v1.20` 模块，7 个文件（4 个新增、3 个修改），351 行新增、43 行删除。改动属于 API 层增强，不改变底层读取逻辑（`RowDataFileScanTaskReader`、`DataIterator` 等保持不变）。
- **回迁到 1.4.x 的注意事项**：本提交是 API 增强 + 废弃标记，**回迁到 1.4.x 需要谨慎评估**。关键考虑：(1) **版本号问题**：本提交在 main 上标记 `@Deprecated since 1.7.0`、`Will be removed in 2.0.0`，而 1.4.x 是更早的维护分支（1.4.x < 1.7.0），在 1.4.x 上引入"since 1.7.0"的废弃标记在版本语义上是混乱的——若回迁，应把 since 版本改为 1.4.x 的某个 patch 版本（如 1.4.4）。(2) **模块路径**：1.4.x 分支可能没有 `flink/v1.20` 模块（1.4.x 同期维护的是 Flink 1.17/1.18/1.19），需要确认对应模块路径，cherry-pick 时手动调整路径。(3) **依赖前置**：需要 1.4.x 上的 `DataIteratorReaderFunction`、`RowDataFileScanTaskReader`、`CloseableIterator.transform`、`LimitableDataIterator`、`RecordLimiter` 等 API 与 main 一致，否则 `ConverterReaderFunction` 编译不通过。(4) **API 兼容性**：本提交是**纯新增 API** + 废弃标记，不删除/修改任何既有 API 行为（除 `IcebergSourceSplitReader` 钻石操作符这种无害清理），对 1.4.x 现有用户零影响，理论上可安全回迁以让 1.4.x 用户也享受新 API 便利。但若 1.4.x 已接近停止维护，回迁价值有限，可不必回迁，让用户在升级到 1.7.x+ 时再使用新 API。建议根据 1.4.x 实际维护状态决定。
