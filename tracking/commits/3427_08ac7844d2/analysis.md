# 提交 3427：Core: Propagate Avro compression settings to manifest writers (#15652)

## 提交信息

- **序号**：3427 / 4088
- **哈希**：08ac7844d2e223c3c4209f2f941acf4dd55c1bfb
- **短哈希**：08ac7844d2
- **日期**：2026-03-20 12:04:20 -0500
- **作者**：Russell Spitzer
- **提交说明**：Core: Propagate Avro compression settings to manifest writers (#15652)
- **PR/Issue**：#15652

## 总体目的

将 Avro 压缩设置传播到清单写入器（manifest writers）。此前清单文件使用固定的压缩设置，无法通过表属性配置。本提交新增 `write.manifest.compression-codec` 和 `write.manifest.compression-level` 表属性，允许用户为清单文件配置 Avro 压缩编解码器和压缩级别。这在清单文件较大时特别有用，可以使用更高压缩比减少存储空间。

## 如何达成设计目的

1. 在 `TableProperties` 中新增 `MANIFEST_COMPRESSION` 和 `MANIFEST_COMPRESSION_LEVEL` 属性
2. 在 `SnapshotProducer` 中新增 `manifestWriterProperties` 方法，从表元数据读取清单压缩设置并转换为 Avro 写入属性
3. 在 `ManifestFiles` 的所有 `write` 和 `writeDeleteManifest` 方法中新增接受 `writerProperties` 参数的重载
4. 在 `ManifestWriter` 的所有版本（V1-V4）构造函数中传递 `writerProperties`，在 `appender()` 方法中通过 `.set(writerProperties())` 应用到 Avro 写入器

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableProperties.java` (+6 lines)

**修改目的**：新增清单压缩属性常量。

**工作逻辑**：
- `MANIFEST_COMPRESSION = "write.manifest.compression-codec"`，默认值 `"gzip"`
- `MANIFEST_COMPRESSION_LEVEL = "write.manifest.compression-level"`，默认值 `null`

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java` (+33/-3 lines)

**修改目的**：从表属性读取清单压缩设置并传递给清单写入器。

**工作逻辑**：
- 新增 `manifestWriterProps` 字段，在构造函数中通过 `manifestWriterProperties(ops.current())` 初始化
- `manifestWriterProperties` 方法：读取 `MANIFEST_COMPRESSION` 和 `MANIFEST_COMPRESSION_LEVEL`，映射为 `AVRO_COMPRESSION` 和 `AVRO_COMPRESSION_LEVEL`
- `newManifestWriter` 和 `newDeleteManifestWriter` 方法传递 `manifestWriterProps`

### `core/src/main/java/org/apache/iceberg/ManifestFiles.java` (+116/-8 lines)

**修改目的**：所有 write/writeDeleteManifest 方法新增 writerProperties 参数的重载。

**工作逻辑**：
- 新增多个接受 `Map<String, String> writerProperties` 参数的 `write` 和 `writeDeleteManifest` 重载方法
- `newWriter` 内部方法新增 `writerProperties` 参数，传递给各版本 Writer 构造函数
- 各版本 Writer（V1-V4）和 DeleteWriter（V2-V4）构造函数新增 `writerProperties` 参数

### `core/src/main/java/org/apache/iceberg/ManifestWriter.java` (+78/-16 lines)

**修改目的**：在 ManifestWriter 中存储和应用 writerProperties。

**工作逻辑**：
- 基类新增 `writerProperties` 字段和 `writerProperties()` getter
- 所有版本（V1-V4）的 Writer 和 DeleteWriter 构造函数新增 `writerProperties` 参数
- 各版本的 `appender()` 方法在构建 Avro appender 时添加 `.set(writerProperties())`

### 其他文件
- `InternalData.java` (+14 lines)：支持测试
- `ManifestBenchmark.java` (+279 lines, 新文件)：清单写入性能基准测试
- `TestManifestWriterVersions.java` (+31 lines)：测试清单写入器版本兼容性
- `TestSnapshotProducer.java` (+19 lines)：测试压缩设置传播
- `AvroTestHelpers.java` (+15/-1 lines)：测试辅助工具

## 总结

本提交新增了清单文件的 Avro 压缩配置能力，通过 `write.manifest.compression-codec` 和 `write.manifest.compression-level` 表属性控制。压缩设置从 `SnapshotProducer` 传播到 `ManifestFiles`，再应用到 `ManifestWriter` 的各版本写入器。默认使用 gzip 压缩，用户可配置其他编解码器和压缩级别。
