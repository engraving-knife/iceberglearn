# 提交 1218：Arrow: Remove unused readers (#11276)

## 提交信息

- **序号**：1218 / 4088
- **哈希**：208ab20dc9ab8bcab3ee525d0ddaba80eeae7609
- **短哈希**：208ab20dc
- **日期**：2024-10-08（Tue Oct 8 00:21:50 2024 -0700）
- **作者**：Wing Yew Poon <wypoon@cloudera.com>
- **提交说明**：Arrow: Remove unused readers (#11276)
- **PR/Issue**：#11276

## 总体目的

Iceberg 的 `arrow` 模块在向量化的 Parquet 读取路径中，于 `VectorizedDictionaryEncodedParquetValuesReader` 与 `VectorizedParquetDefinitionLevelReader` 两个类里维护了一批内部 reader 类，用于把 Parquet 的不同物理类型解码到对应的 Arrow `FieldVector`。其中针对 decimal 类型的 reader 共有 4 个（按编码方式与底层存储类型组合）：

- `FixedLengthDecimalDictEncodedReader` / `FixedLengthDecimalReader`：定长字节存储的 decimal（字典编码 / 普通）。
- `IntBackedDecimalDictEncodedReader` / `IntBackedDecimalReader`：以 32 位 int 存储的 decimal（字典编码 / 普通）。
- `LongBackedDecimalDictEncodedReader` / `LongBackedDecimalReader`：以 64 位 long 存储的 decimal（字典编码 / 普通）。

这些 reader 类以及对应的工厂方法在仓库中**没有任何调用方**（已被其他实现路径取代或从未被启用），属于死代码。本提交清理这 6 个 reader 类与 6 个工厂方法，以及不再需要的 `DecimalVector` import，减少维护负担并避免误导。

## 如何达成设计目的

直接删除 `arrow` 模块下两个文件中所有未被引用的 decimal reader 内部类及其工厂方法。删除前已确认无调用方（工厂方法为包级可见，无外部引用）。无功能变化。

## 修改详情

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/parquet/VectorizedDictionaryEncodedParquetValuesReader.java`

**修改目的**：删除未使用的字典编码 decimal reader。

**工作逻辑**：

- 移除 `import org.apache.arrow.vector.DecimalVector;`（不再需要）。
- 删除 3 个内部类：
  - `FixedLengthDecimalDictEncodedReader`：原用于把字典解码出的字节通过 `DecimalVectorUtil.setBigEndian` 写入 `DecimalVector`。
  - `IntBackedDecimalDictEncodedReader`：原用于把字典解码出的 int 写入 `DecimalVector`。
  - `LongBackedDecimalDictEncodedReader`：原用于把字典解码出的 long 写入 `DecimalVector`。
- 删除 3 个工厂方法：`fixedLengthDecimalDictEncodedReader()`、`intBackedDecimalDictEncodedReader()`、`longBackedDecimalDictEncodedReader()`。
- 保留其他与 decimal 无关的 reader（`VarWidthBinaryDictEncodedReader`、`FixedSizeBinaryDictEncodedReader`、`FixedWidthBinaryDictEncodedReader` 等）及其工厂方法。

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/parquet/VectorizedParquetDefinitionLevelReader.java`

**修改目的**：删除未使用的非字典编码 decimal reader。

**工作逻辑**：

- 移除 `import org.apache.arrow.vector.DecimalVector;`。
- 删除 3 个内部类：
  - `FixedLengthDecimalReader`：原 `nextVal` 从 `ValuesAsBytesReader` 读定长字节写入 `DecimalVector`；`nextDictEncodedVal` 委托 `fixedLengthDecimalDictEncodedReader()`（RLE 模式）或直接解码（PACKED 模式）。
  - `IntBackedDecimalReader`：原 `nextVal` 从 buffer 读 int 写入 `DecimalVector`；`nextDictEncodedVal` 委托 `intBackedDecimalDictEncodedReader()` 或直接 `decodeToInt`。
  - `LongBackedDecimalReader`：原 `nextVal` 从 buffer 读 long 写入 `DecimalVector`；`nextDictEncodedVal` 委托 `longBackedDecimalDictEncodedReader()` 或直接 `decodeToLong`。
- 删除 3 个工厂方法：`fixedLengthDecimalReader()`、`intBackedDecimalReader()`、`longBackedDecimalReader()`。
- 保留其他 reader（`FixedSizeBinaryReader`、`FixedWidthBinaryReader`、`VarWidthReader`、`BooleanReader` 等）及其工厂方法。

## 小结

- **成效**：移除了 `arrow` 模块向量化的 Parquet decimal 读取路径中的死代码（6 个未使用的内部 reader 类与 6 个工厂方法），减少约 146 行代码，降低维护成本与误导风险。
- **影响范围**：仅 `arrow` 模块两个文件的删除操作，无 API 变更，无功能变化（删除的代码本就未被调用）。
- **回迁到 1.4.x 的注意事项**：纯死代码清理，对运行时行为无任何影响。**可安全回迁**到 1.4.x，但**非必须**——1.4.x 保留这些死代码不会造成任何问题，仅是代码整洁度问题。若 1.4.x 上有人（或下游 fork）私下调用了这些包级可见的工厂方法，回迁会破坏其编译；但鉴于这些方法本就是包级可见且语义上是为内部 vectorized 读取服务，外部依赖的可能性极低。建议根据 1.4.x 的代码整洁策略决定是否回迁。
