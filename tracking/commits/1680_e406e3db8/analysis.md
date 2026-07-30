# 提交 1680：Spark: Test metadata tables with format-version=3 (#12135)

## 提交信息

- **序号**：1680 / 4088
- **哈希**：e406e3db80d1735fe6c840ce99ecc300ae1dd763
- **短哈希**：e406e3db8
- **日期**：2025-02-04（Tue Feb 4 16:33:42 2025 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Spark: Test metadata tables with format-version=3 (#12135)
- **PR/Issue**：#12135

## 总体目的

Iceberg 的元数据表（metadata tables，如 `files`、`entries`、`position_deletes` 等）是暴露表内部元数据给 Spark SQL 查询的重要功能。format-version=3 引入了新的元数据字段与语义（如 position deletes 表新增 `content_offset` 与 `content_size_in_bytes` 列、DV 相关字段），但此前的 `TestMetadataTables` 测试类**只以 format-version=2 运行**，format-version=3 的元数据表行为完全未被测试覆盖。

本提交的目标是：

1. 把 `TestMetadataTables` 参数化，使其同时在 format-version=2 和 format-version=3 下运行所有测试，补齐 v3 的测试覆盖；
2. 新增 `testPositionDeletesTable` 测试，验证 `position_deletes` 元数据表在 v2/v3 下的列结构差异（v3 额外包含 `deleteFile.contentOffset()` 与 `deleteFile.contentSizeInBytes()`）；
3. 修复 `DVIterator` 在处理 `DELETE_FILE_ROW_FIELD_ID` 字段时的遗漏——DVs（Deletion Vectors）不记录被删除的具体行号，此前该字段未被处理会导致查询失败，现在返回 null。

## 如何达成设计目的

1. **参数化测试**：在 `TestMetadataTables` 上新增 `@Parameter(index = 3) private int formatVersion` 字段，扩展 `parameters()` 方法，在原有的 catalog 配置矩阵（HIVE/HADOOP/SPARK/REST）基础上，为 SPARK 和 REST catalog 各增加一组 format-version=3 的参数组合。所有建表 SQL 中的 `'format-version'='2'` 改为 `'format-version'='%s'` + `formatVersion` 参数。
2. **新增 position_deletes 测试**：创建表、写入 4 条记录、删除 id=1 和 id=3 的 2 条记录，然后查询 `position_deletes` 元数据表，按 formatVersion 断言不同的列结构（v3 比 v2 多 `contentOffset` 与 `contentSizeInBytes` 两列）。
3. **DVIterator 修复**：在遍历 DV 删除文件元数据列时，新增对 `MetadataColumns.DELETE_FILE_ROW_FIELD_ID` 的处理分支，返回 null（DVs 不记录被删除行的位置）。

## 修改详情

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMetadataTables.java`（修改，+127/-18 行）

**修改目的**：参数化 format-version 并新增 position_deletes 元数据表测试。

**工作逻辑**：

- 新增 `@Parameter(index = 3) private int formatVersion` 字段与 `@Parameters` 工厂方法。参数矩阵在原有 4 组（HIVE v2、HADOOP v2、SPARK v2、REST v2）基础上新增 2 组（SPARK v3、REST v3），共 6 组。HIVE 与 HADOOP catalog 不测 v3（可能因 catalog 限制）。
- 所有建表 SQL 从硬编码 `'format-version'='2'` 改为 `'format-version'='%s'` + `formatVersion` 参数（涉及 `testUnpartitionedTable`、`testPartitionedTable`、`testAllFilesUnpartitioned`、`testAllFilesPartitioned`、`testMetadataLogEntries`、`testHistoryMetadataTable`、`testManifestEntriesMetaTable`、`testPartitionsTable`、`testPartitionsTableWithPartitionEvolution` 等共 9 个测试方法）。
- 新增 `testPositionDeletesTable()` 测试方法：
  - 建表并写入 4 条记录（id=1/2/3/4），执行 `DELETE FROM %s WHERE id=1 OR id=3`；
  - 验证 `delete_files` 元数据表有 1 条记录；
  - 查询 `position_deletes` 元数据表，期望 2 行（被删除的 id=1 对应行号 0、id=3 对应行号 2）；
  - format-version ≥ 3 时，期望每行包含 7 列（`dataFile.location`、`pos`、`row`（null）、`spec_id`、`deleteFile.location`、`content_offset`、`content_size_in_bytes`）；
  - format-version < 3 时，期望每行包含 5 列（`dataFile.location`、`pos`、`row`（null）、`spec_id`、`deleteFile.location`）——即 v2 不包含 `content_offset` 与 `content_size_in_bytes`。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/DVIterator.java`（修改，+3 行）

**修改目的**：处理 `DELETE_FILE_ROW_FIELD_ID` 元数据列，使 DV 删除文件的 `row` 字段查询返回 null 而非报错。

**工作逻辑**：在 `DVIterator` 遍历请求字段列表时，新增一个 `else if (fieldId == MetadataColumns.DELETE_FILE_ROW_FIELD_ID)` 分支，`rowValues.add(null)`——因为 DVs（Deletion Vectors）使用位图标记被删除的行，不像 position deletes 文件那样记录每条删除的行号，所以 `row` 字段对 DV 始终为 null。注释明确说明"DVs don't track the row that was deleted"。

## 小结

- **成效**：补齐了 Spark 3.5 元数据表在 format-version=3 下的测试覆盖，新增 `position_deletes` 元数据表的端到端测试，并修复了 `DVIterator` 在查询 `DELETE_FILE_ROW_FIELD_ID` 字段时的遗漏，使 DV 场景下的元数据表查询不再报错。
- **影响范围**：`spark/v3.5` 模块——测试类 `TestMetadataTables` 大幅扩展、生产代码 `DVIterator` 小修。`DVIterator` 的修复对使用 DV 的用户是必要的 bug 修复，否则查询含 `row` 字段的元数据表会抛异常。
- **回迁到 1.4.x 的注意事项**：
  1. 需确认 1.4.x 分支是否支持 format-version=3 与 DV（Deletion Vectors）。若 1.4.x 已支持 DV 则 `DVIterator` 的修复应一并回迁（这是一个 bug 修复）；
  2. `TestMetadataTables` 的参数化改造需确认 1.4.x 的 `ParameterizedTestExtension` / `@Parameter` / `@Parameters` 等 JUnit5 扩展可用；
  3. `MetadataColumns.DELETE_FILE_ROW_FIELD_ID` 常量需在 1.4.x 中存在，否则 `DVIterator` 修改无法编译；
  4. 本提交只改了 spark/v3.5，若 1.4.x 还有 3.3/3.4 模块，可按需同步测试扩展。
