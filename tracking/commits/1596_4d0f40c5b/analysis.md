# 提交 1596：Spark: Fix Puffin suffix for DVs (#11986)

## 提交信息

- **序号**：1596 / 4088
- **哈希**：4d0f40c5b98c16320e2ad795cb5c9a0cd3b1d360
- **短哈希**：4d0f40c5b
- **日期**：2025-01-17（Fri Jan 17 06:59:22 2025 -0700）
- **作者**：Amogh Jahagirdar <amoghj@apache.org>
- **提交说明**：Spark: Fix Puffin suffix for DVs (#11986)
- **PR/Issue**：#11986

## 总体目的

Iceberg v3 格式规范中，位置删除（position deletes）推荐使用基于 Puffin 文件格式的 DV（deletion vector）。Spark 3.5 模块原本通过 `SparkWriteConf.useDVs()` 单独判断"是否启用 DV"（条件是 `formatVersion >= 3`），但删除文件的格式仍然走通用的 `deleteFileFormat()` 配置路径——默认是 Parquet。这导致一个矛盾：在 format version 3 的表上写 DV 时，写入代码按 DV 序列化，但生成的删除文件名后缀并不是 `.puffin`，而是默认删除文件格式对应的后缀（例如 `.parquet`）。文件名后缀与实际内容不一致会引发后续读取 / 工具链问题——Puffin 文件应当以 `.puffin` 结尾，这是 Iceberg 规范与各种 reader 的隐式约定。

本提交通过"单一来源"修复该不一致：当表 format version >= 3 且表本身不是元数据表（`BaseMetadataTable`）时，`deleteFileFormat()` 直接返回 `FileFormat.PUFFIN`；删除 `SparkWriteConf.useDVs()` 这个独立判断；`SparkPositionDeltaWrite.Context.useDVs()` 改为从 `deleteFileFormat == FileFormat.PUFFIN` 派生。这样 DV 启用与文件格式后缀永远保持一致，DV 文件天然得到 `.puffin` 后缀。

同时引入 `BaseMetadataTable` / `TableUtil.formatVersion(table)` 取代 `HasTableOperations` / `ops.current().formatVersion()`，因为元数据表（如 `metadata` 表、`files` 表等内部衍生表）不直接暴露 `TableOperations`，旧代码遇到元数据表会强转失败。`TableUtil.formatVersion` 抽象了"普通表 vs 元数据表"的取值差异，使该判断在两类表上都安全。

## 如何达成设计目的

### 统一"是否使用 DV"的判定到文件格式

旧逻辑：

- `SparkWriteConf.deleteFileFormat()` 从 `TableProperties.DELETE_FILE_FORMAT` / Spark conf 解析，默认 `Parquet`。
- `SparkWriteConf.useDVs()`：`((HasTableOperations) table).operations().current().formatVersion() >= 3`。
- `SparkPositionDeltaWrite.Context` 同时持有 `deleteFileFormat`（来自 `deleteFileFormat()`）和 `useDVs`（来自 `useDVs()`），两者互不参考。

问题：format version 3 的表上 `useDVs=true` 但 `deleteFileFormat=Parquet`，DV 内容被写到带 `.parquet` 后缀的文件中。

新逻辑：

- `SparkWriteConf.deleteFileFormat()`：先判断 `!(table instanceof BaseMetadataTable) && TableUtil.formatVersion(table) >= 3`，命中则直接返回 `FileFormat.PUFFIN`；否则走原配置解析路径。
- 删除 `SparkWriteConf.useDVs()`。
- `SparkPositionDeltaWrite.Context` 不再单独存 `useDVs`，`useDVs()` 方法返回 `deleteFileFormat == FileFormat.PUFFIN`。

如此一来，format version 3 表的删除文件天然是 Puffin 格式，文件名后缀正确为 `.puffin`；format version < 3 的表仍按用户配置走（默认 Parquet），不影响存量行为。`BaseMetadataTable` 排除是为了避免给元数据表（其内部不会走 DV 写入路径）也强制 Puffin。

### 测试加固

- `TestSparkWriteConf.testDVWriteConf`：把表升级到 format version 3，断言 `writeConf.deleteFileFormat() == FileFormat.PUFFIN`，覆盖主修复。
- `TestMergeOnReadDelete` / `TestMergeOnReadMerge` / `TestMergeOnReadUpdate`：在已有的 DV 数量与 recordCount 断言基础上，新增 `assertThat(dvs).allMatch(dv -> FileFormat.fromFileName(dv.location()) == FileFormat.PUFFIN)`，确保 MOR 路径下生成的 DV 文件确实以 Puffin 后缀落地。这直接复现并锁定了原 bug 表现（文件名后缀错误）。

### 修改详情

#### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkWriteConf.java`（核心修复）

- import：`BaseMetadataTable`、`TableUtil` 替换 `HasTableOperations`、`TableOperations`。
- `deleteFileFormat()` 开头新增：

```java
if (!(table instanceof BaseMetadataTable) && TableUtil.formatVersion(table) >= 3) {
  return FileFormat.PUFFIN;
}
```

- 删除 `useDVs()` 方法。

#### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeltaWrite.java`

- `Context` 字段移除 `useDVs`，构造方法不再调用 `writeConf.useDVs()`。
- `Context.useDVs()` 改为 `return deleteFileFormat == FileFormat.PUFFIN;`。

#### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestSparkWriteConf.java`

- 新增 `testDVWriteConf`：升级 format version 至 3，断言 `deleteFileFormat()` 返回 `FileFormat.PUFFIN`。

#### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMergeOnReadDelete.java`、`TestMergeOnReadMerge.java`、`TestMergeOnReadUpdate.java`

- 在 DV 断言后追加 `FileFormat.fromFileName(dv.location()) == FileFormat.PUFFIN` 检查，确保 MOR DELETE / MERGE / UPDATE 生成的 DV 文件后缀为 Puffin。

## 小结

- **成效**：修复了 Spark 3.5 在 format version 3 表上写 DV 时文件名后缀与实际内容不一致的 bug——DV 文件现在正确以 `.puffin` 后缀落地；同时消除了"是否启用 DV"与"删除文件格式"两个独立判断的潜在不一致风险，使二者统一由 `deleteFileFormat()` 派生。`BaseMetadataTable` 的排除避免了元数据表被误判为需要 Puffin。
- **影响范围**：仅 Spark 3.5 模块。主代码变更约 +6 / -7 行（核心 `SparkWriteConf` 与 `SparkPositionDeltaWrite.Context`），其余为测试加固。属于行为修复，对 format version < 3 的表完全无影响。
- **回迁到 1.4.x 的注意事项**：此 bug 影响实际产物（DV 文件后缀错误可能让下游 reader / 工具无法识别），**建议回迁**到 1.4.x 维护的 Spark 3.5 模块。回迁时需同时带上 `TableUtil.formatVersion` / `BaseMetadataTable` 依赖（确保 1.4.x 已有这些 API），以及对应的 MOR 测试断言与 `testDVWriteConf`。注意本提交未触及 Spark 3.3 / 3.4 分支，1.4.x 若仍发布这些版本的 artifact 需评估是否存在同样问题。
