# 提交 3601：Parquet: Add write.parquet.page-version table property (#15700)

## 提交信息

- **序号**：3601 / 4088
- **哈希**：8d0508308cd068778897c06d3a802740ed4305a6
- **短哈希**：8d0508308
- **日期**：2026-04-27 14:59:31 +0200
- **作者**：Harrison Crosse
- **提交说明**：Parquet: Add write.parquet.page-version table property (#15700)
- **PR/Issue**：#15700

## 总体目的

这个提交为 Iceberg 添加了通过表属性 `write.parquet.page-version` 来配置 Parquet 数据页版本（v1 或 v2）的能力。

Parquet 文件格式有两种数据页版本：DataPage V1（PARQUET_1_0）和 DataPage V2（PARQUET_2_0）。V2 页格式提供了一些改进，如更高效的编码、更好的压缩率、以及更精确的页级统计信息。之前 Iceberg 的 WriteBuilder 有一个 `writerVersion` 设置，但它没有通过表属性暴露给用户，无法在表级别进行配置。

这个提交将 writer version 的配置从 WriteBuilder 的内部字段改为通过配置属性（`write.parquet.page-version`）来控制，使其可以通过表属性、catalog 属性等方式进行配置，同时支持数据文件和删除文件的独立配置。

## 如何达成设计目的

实现方案：
1. 在 `TableProperties` 中新增 `PARQUET_PAGE_VERSION` 和 `DELETE_PARQUET_PAGE_VERSION` 属性常量，默认值为 "v1"。
2. 重构 `Parquet.WriteBuilder`，将 `writerVersion` 从实例字段改为存储在 config map 中，通过 `Context` 类统一管理。
3. 在 `Context`（data context 和 delete context）中解析 `PARQUET_PAGE_VERSION` 属性，转换为 `WriterVersion` 枚举。
4. 移除 `ParquetFormatModel` 中的 `WRITER_VERSION_KEY` 特殊处理，统一走标准属性设置流程。
5. 添加了 `toWriterVersion()` 工具方法进行字符串到枚举的转换和错误处理。
6. 新增 `TestParquetPageVersion` 测试类。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableProperties.java` (+4/-0 lines)

**修改目的**：定义 Parquet 页版本相关的表属性。

**工作逻辑**：
```java
public static final String PARQUET_PAGE_VERSION = "write.parquet.page-version";
public static final String DELETE_PARQUET_PAGE_VERSION = "write.delete.parquet.page-version";
public static final String PARQUET_PAGE_VERSION_DEFAULT = "v1";
```
分别为数据文件和删除文件定义了页版本属性，默认值为 "v1"（即 DataPage V1）。

### `parquet/src/main/java/org/apache/iceberg/parquet/Parquet.java` (+40/-15 lines)

**修改目的**：重构 WriteBuilder 以支持通过配置属性控制 writer version。

**工作逻辑**：

1. **修改 `writerVersion()` 方法**：不再设置实例字段，而是将值存入 config map：
```java
public WriteBuilder writerVersion(WriterVersion version) {
  Preconditions.checkNotNull(version, "Writer version cannot be null");
  Preconditions.checkArgument(
      version == WriterVersion.PARQUET_1_0 || version == WriterVersion.PARQUET_2_0,
      "Unsupported writer version: %s", version);
  config.put(PARQUET_PAGE_VERSION, version.name());
  return this;
}
```

2. **移除 `withWriterVersion()` 测试方法**和实例字段 `writerVersion`。

3. **在 `Context` 类中新增 `writerVersion` 字段**：Context 是配置上下文，data context 和 delete context 分别解析页版本属性。delete context 如果没有单独配置，则继承 data context 的 writer version：
```java
String deletePageVersion = config.get(DELETE_PARQUET_PAGE_VERSION);
WriterVersion writerVersion =
    deletePageVersion != null
        ? toWriterVersion(deletePageVersion)
        : dataContext.writerVersion();
```

4. **新增 `toWriterVersion()` 转换方法**：
```java
private static WriterVersion toWriterVersion(String pageVersion) {
  try {
    return WriterVersion.fromString(pageVersion);
  } catch (IllegalArgumentException e) {
    throw new IllegalArgumentException(
        "Unsupported Parquet page version: " + pageVersion + " (must be v1 or v2)");
  }
}
```

5. **构建 ParquetProperties 和 ParquetWriteBuilder 时使用 `context.writerVersion()`** 而非实例字段。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetFormatModel.java` (+0/-6 lines)

**修改目的**：移除特殊的 writer version 处理逻辑。

**工作逻辑**：
移除了 `WRITER_VERSION_KEY` 常量和 `set()` 方法中对它的特殊处理分支。现在 writer version 统一通过标准属性设置流程（`internal.set(property, value)`）处理，因为 WriteBuilder 会从 config map 中读取 `PARQUET_PAGE_VERSION`。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestDictionaryRowGroupFilter.java` (+1/-1 lines)

**修改目的**：适配测试中的 writer version 设置方式变更。

**工作逻辑**：
将原来通过 `withWriterVersion()` 设置的方式改为通过标准属性设置。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestParquetPageVersion.java` (+251 lines, new file)

**修改目的**：新增测试验证页版本配置功能。

**工作逻辑**：
测试类验证了通过表属性配置 v1 和 v2 页版本的能力，包括数据文件和删除文件的独立配置、默认值行为、无效值处理等场景。

### `docs/docs/configuration.md` (+1/-0 lines)

**修改目的**：文档记录新配置。

**工作逻辑**：
在 Parquet 写入配置表中新增：`write.parquet.page-version | v1 | Parquet data page version: v1 (DataPage V1) or v2 (DataPage V2)`。

## 总结

这个提交将 Parquet writer version 的配置从内部 API 暴露为标准的表属性，使用户可以通过 SQL 或 catalog 配置来选择 Parquet 数据页版本。这是一个有用的功能增强，特别是对于需要利用 Parquet V2 页格式优势（如更好的压缩和页级统计）的用户。重构将配置管理统一到 Context 模式中，代码结构更加清晰，同时支持数据文件和删除文件的独立配置。
