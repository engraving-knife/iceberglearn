# 提交 0168：Docs: Fix parquet default compression codec (#9096)

## 提交信息

- **序号**：0168 / 4088
- **哈希**：72da856b3c9ba2da92a5aa5d8e69afdf9a3c24b0
- **短哈希**：72da856b3
- **日期**：2023-11-16
- **作者**：Tom Tanaka
- **提交说明**：Docs: Fix parquet default compression codec (#9096)
- **PR/Issue**：#9096

## 总体目的

Iceberg 1.4.0 起，Parquet 写入的默认压缩 codec 由原来的 `gzip` 切换到了 `zstd`（这一定义体现在 `core/src/main/java/org/apache/iceberg/TableProperties.java` 中的 `PARQUET_COMPRESSION_DEFAULT_SINCE_1_4_0 = "zstd"` 常量，Flink 写配置 `FlinkWriteConf.parquetCompressionCodec()` 与各模块的写入器都依据该常量解析默认值）。然而用户文档 `docs/configuration.md` 中关于 `write.parquet.compression-codec` 一行的“默认值”列仍然停留在 1.4.0 之前的 `gzip`，导致文档与代码行为不一致——用户按文档建表会以为默认是 gzip，但实际默认值已是 zstd，对压缩比与读写性能预期都会产生偏差。

这个提交是一处极小的纯文档修正，目的是把 `write.parquet.compression-codec` 的默认值列从 `gzip` 改为 `zstd`，使文档与 1.4.0 引入的 `PARQUET_COMPRESSION_DEFAULT_SINCE_1_4_0` 实际行为对齐。对 Iceberg 演进的意义在于消除文档与代码的漂移，避免用户误判默认压缩格式（zstd 相对 gzip 在压缩比、解压速度上更优，是 Iceberg 1.4.0 主推的默认 codec）。

## 如何达成设计目的

设计思路就是定位到 `docs/configuration.md` 中描述 Parquet 表属性默认值的那张表格的对应行，把默认值列从 `gzip` 改为 `zstd`，其余说明（候选 codec 列表 `zstd, brotli, lz4, gzip, snappy, uncompressed` 等）保持不变。改动只触及一行表格单元。

## 修改详情

### `docs/configuration.md`

**修改目的**：纠正 `write.parquet.compression-codec` 表属性的默认值描述。

**工作逻辑**：表格行 `| write.parquet.compression-codec | gzip | Parquet compression codec: zstd, brotli, lz4, gzip, snappy, uncompressed |` 中，把第二列默认值由 `gzip` 改为 `zstd`。这对应代码侧 `TableProperties.PARQUET_COMPRESSION_DEFAULT_SINCE_1_4_0 = "zstd"` 的实际默认（1.4.0 起生效），与 `FlinkWriteConf.parquetCompressionCodec()` 等使用 `PARQUET_COMPRESSION_DEFAULT`/`PARQUET_COMPRESSION_DEFAULT_SINCE_1_4_0` 解析默认的逻辑一致。

## 小结

这个提交是一行文档修正，把 `docs/configuration.md` 中 `write.parquet.compression-codec` 的默认值从过时的 `gzip` 改为 1.4.0 起实际生效的 `zstd`，使文档与 `TableProperties.PARQUET_COMPRESSION_DEFAULT_SINCE_1_4_0` 保持一致，避免用户对默认压缩格式产生误判。
