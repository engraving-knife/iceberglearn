# 提交 3833：Parquet: Add opt-in uncompressed row group size tracking (#16327)

## 提交信息

- **序号**：3833 / 4088
- **哈希**：e829aaf8e254b4a2fd85f693f86d40566965473e
- **短哈希**：e829aaf8e
- **日期**：2026-06-06 21:58:50 -0700
- **作者**：Neelesh Salian <nssalian@users.noreply.github.com>
- **提交说明**：Parquet: Add opt-in uncompressed row group size tracking (#16327)
- **PR/Issue**：#16327

## 总体目的

本提交为 Iceberg 的 Parquet 写入器新增一个可选功能：基于"未压缩（原始）数据大小"来判断何时切分 Parquet row group，从而在使用压缩编解码器时更准确地命中 `write.parquet.row-group-size-bytes` 目标。

背景：Parquet 写入器在写入过程中需要决定何时结束当前 row group 并开始新 row group，目标 row group 大小由 `write.parquet.row-group-size-bytes`（默认 128MB）控制。原实现通过 `writeStore.getBufferedSize()` 获取缓冲区大小来判断，但该缓冲区大小在压缩编解码器下反映的是"压缩后"的预估大小，而非未压缩原始大小。当数据可压缩性高时（如重复值多的列），压缩后缓冲区大小会显著小于原始大小，导致写入器误以为还没达到目标，继续往 row group 里写，最终产生的 row group 解压后远超目标大小。过大的 row group 会增加读取端内存压力、降低列式跳过效率。

本提交新增表属性 `write.parquet.row-group-size-track-uncompressed`（默认 `false`，opt-in）。启用后，写入器在每条记录写入前后测量 `writeStore.getBufferedSize()` 的差值，累加为 `rowGroupUncompressedSize`，并用该未压缩累计大小判断是否达到 row group 目标，从而更准确地控制 row group 大小。默认关闭以保持向后兼容与现有性能特征。

## 如何达成设计目的

设计上分三层：
- 配置层：在 `TableProperties` 新增 `PARQUET_ROW_GROUP_SIZE_TRACK_UNCOMPRESSED` 属性键与默认值 `false`，并在 `Parquet.Context` 中读取该属性，通过构造函数传递。
- 写入器层：`ParquetWriter` 新增 `trackUncompressedSize` 字段与 `rowGroupUncompressedSize` 累计变量。`add()` 方法在启用时调用 `writeTracked()`——记录写入前后的 `getBufferedSize()` 差值并累加；未启用时维持原 `model.write` 直接调用。
- 判断层：`checkSize()` 重构为根据 `trackUncompressedSize` 选择使用 `rowGroupUncompressedSize`（启用）或 `writeStore.getBufferedSize()`（默认）作为当前大小，统一委托给新提取的 `evaluateRowGroupSize(currentSize, useMinRecordCountFloor)` 方法计算是否 flush 与下次检查间隔。启用模式下若 `rowGroupUncompressedSize >= targetRowGroupSize` 直接 flush。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableProperties.java` (+4/-0 lines)

**修改目的**：新增表属性键与默认值。

**工作逻辑**：
```java
public static final String PARQUET_ROW_GROUP_SIZE_TRACK_UNCOMPRESSED =
    "write.parquet.row-group-size-track-uncompressed";
public static final boolean PARQUET_ROW_GROUP_SIZE_TRACK_UNCOMPRESSED_DEFAULT = false;
```

### `docs/docs/configuration.md` (+1/-0 lines)

**修改目的**：在表属性文档中记录新选项。

**工作逻辑**：
在 `write.parquet.row-group-size-bytes` 行后新增：
```
| write.parquet.row-group-size-track-uncompressed     | false                       | Track raw data size to enforce the row group size target accurately with compressing codecs |
```

### `parquet/src/main/java/org/apache/iceberg/parquet/Parquet.java` (+21/-4 lines)

**修改目的**：在 `Context` 中读取并传递 `trackUncompressedRowGroupSize` 配置。

**工作逻辑**：
- `Context` 新增 `trackUncompressedRowGroupSize` 字段、构造参数与访问器。
- `dataContext` 从配置读取：
```java
boolean trackUncompressedRowGroupSize =
    PropertyUtil.propertyAsBoolean(
        config,
        PARQUET_ROW_GROUP_SIZE_TRACK_UNCOMPRESSED,
        PARQUET_ROW_GROUP_SIZE_TRACK_UNCOMPRESSED_DEFAULT);
```
- 构造 `ParquetWriter` 时传入该标志（在带 encryption 与普通 builder 两条路径都传入）。
- `deleteContext` 复用 `dataContext.trackUncompressedRowGroupSize()`。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetWriter.java` (+42/-12 lines)

**修改目的**：实现未压缩大小追踪与基于其的 row group 切分判断。

**工作逻辑**：
- 新增字段 `boolean trackUncompressedSize` 与 `long rowGroupUncompressedSize`，构造函数新增参数。
- `add()` 方法分支：
```java
if (trackUncompressedSize) {
  writeTracked(value);
} else {
  model.write(0, value);
}
writeStore.endRecord();
checkSize();
```
- `writeTracked()` 测量写入前后缓冲区差值并累加：
```java
private void writeTracked(T value) {
  long sizeBefore = writeStore.getBufferedSize();
  model.write(0, value);
  rowGroupUncompressedSize += writeStore.getBufferedSize() - sizeBefore;
}
```
- `checkSize()` 重构为根据 `trackUncompressedSize` 选择大小来源，委托 `evaluateRowGroupSize`：
  - 启用模式：若 `rowGroupUncompressedSize >= targetRowGroupSize` 直接 flush；否则按 `nextCheckRecordCount` 检查。
  - 默认模式：按原逻辑用 `writeStore.getBufferedSize()` 检查。
- 新增 `evaluateRowGroupSize(currentSize, useMinRecordCountFloor)` 统一计算：超过目标则 flush，否则估算下次检查间隔（启用模式不强制 `minRowCount` 下限）。
- `flushRowGroup` 后重置 `rowGroupUncompressedSize = 0`。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestParquetDataWriter.java` (+69/-0 lines)

**修改目的**：覆盖启用追踪后 row group 大小更准确的行为。

**工作逻辑**：
新增测试构造可压缩数据，分别用默认与启用 `PARQUET_ROW_GROUP_SIZE_TRACK_UNCOMPRESSED` 写入，比较产生的 row group 大小差异，验证启用后 row group 的未压缩大小更接近目标，不会因压缩而过度膨胀。

## 总结

本提交为 Parquet 写入器新增 opt-in 的未压缩 row group 大小追踪能力，解决压缩编解码器下基于压缩后缓冲区大小判断导致 row group 解压后过大的问题。启用后通过逐条记录测量缓冲区差值累加未压缩大小，更准确地命中 row group 目标。默认关闭以保持兼容。这对高可压缩数据集的读取端内存与列式跳过效率有积极意义。本提交经过多轮 PR 评审修订，体现了对性能与兼容性平衡的审慎。
