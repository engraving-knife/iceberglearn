# 提交 1495：Spark: Read DVs when reading from .position_deletes table (#11657)

## 提交信息

- **序号**：1495 / 4088
- **哈希**：2a5b089aa52b2253318985b007af951909adfade
- **短哈希**：2a5b089aa
- **日期**：2024-12-16（Mon Dec 16 08:50:49 2024 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Spark: Read DVs when reading from .position_deletes table (#11657)
- **PR/Issue**：#11657

## 总体目的

Iceberg v3 格式引入了"删除向量"（Deletion Vectors，DV）机制：行级别的位置删除可以以紧凑的位图（Roaring bitmap）形式存储，而非传统 v2 的"位置删除文件"（每个被删行一条 `(file_path, pos)` 记录）。`PositionDeletesTable` 是 Iceberg 暴露给用户查询的元数据表（通过 `tbl.position_deletes` 访问），用于检视删除文件内部内容。

此前，当用户在 Spark 中读取 `.position_deletes` 元数据表、且底层删除文件是 v3 的 DV 文件时，`PositionDeletesRowReader` 仍走通用读取路径，无法正确解析 DV 紧凑格式——因为 DV 文件不直接包含 `(file_path, pos)` 行，而是编码在位图中。本提交为 `PositionDeletesRowReader` 增加 DV 分支：当 `task.file()` 是 DV 文件时，使用新增的 `DVIterator` 把 DV 展开为 `(file_path, pos)` 形式的 `InternalRow` 流，使元数据表查询对 v2/v3 删除文件都能正确呈现。

## 如何达成设计目的

1. 新增 `DVIterator` 类（`CloseableIterator<InternalRow>`），内部使用 `BaseDeleteLoader` 加载 DV 中记录的被删位置列表，再按投影 schema 将其展开为 Spark `InternalRow`。
2. 在 `PositionDeletesRowReader` 的迭代器构建入口处，先判断 `ContentFileUtil.isDV(task.file())`，若是 DV 则返回 `DVIterator`，否则沿用原有路径。
3. 新增 `TestPositionDeletesReader`，针对 v2 与 v3 两种 `formatVersion` 参数化测试，验证元数据表读取结果的正确性（含无删除文件、多个删除文件、列顺序不同等场景）。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/DVIterator.java`（新增）

**修改目的**：把 DV 文件展开为 Spark 可消费的 `InternalRow` 迭代器。

**工作逻辑**：

- 构造函数接收 `InputFile`、`DeleteFile`、投影 `Schema`、`idToConstant`（分区列等常量映射）。
- 通过 `new BaseDeleteLoader(ignored -> inputFile).loadPositionDeletes(ImmutableList.of(deleteFile), deleteFile.referencedDataFile())` 把 DV 反序列化为 `List<Long>`（被删位置列表）。`BaseDeleteLoader` 是 `iceberg-data` 模块提供的通用删除加载器，能识别 DV 与 v2 位置删除文件。
- `hasNext()/next()` 遍历位置列表。首次 `next()` 时根据 `projection.columns()` 构造一个可复用的 `GenericInternalRow`，按 `fieldId` 填充：
  - `DELETE_FILE_PATH`：被删数据文件路径，取自 `deleteFile.referencedDataFile()`（DV 文件记录它所针对的数据文件路径）。
  - `DELETE_FILE_POS`：被删行号，即当前位置值；并记录 `deletedPositionIndex` 以便后续行复用此 row 仅更新该字段。
  - `PARTITION_COLUMN_ID`、`SPEC_ID_COLUMN_ID`、`FILE_PATH_COLUMN_ID`：从 `idToConstant` 取常量。
- 后续 `next()` 复用同一 `GenericInternalRow`，仅 `update(deletedPositionIndex, position)` 更新被删行号，减少对象分配。`close()` 空实现（`BaseDeleteLoader` 已在加载阶段完成 IO）。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/PositionDeletesRowReader.java`

**修改目的**：在读取入口增加 DV 分支判断。

**工作逻辑**：在 `newIterable(...)` 调用之前插入判断：

```java
if (ContentFileUtil.isDV(task.file())) {
  return new DVIterator(inputFile, task.file(), expectedSchema(), idToConstant);
}
```

`ContentFileUtil.isDV` 通过 `ContentFile.content()` 与文件版本号判断是否为 DV 删除文件。命中则直接返回 `DVIterator`，跳过原本面向 v2 位置删除文件的通用读取路径。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestPositionDeletesReader.java`（新增）

**修改目的**：验证 `PositionDeletesRowReader` 在 v2/v3 两种格式下读取 `.position_deletes` 元数据表的正确性。

**工作逻辑**：

- 用 `@Parameters(name = "formatVersion = {0}")` 参数化，覆盖 `formatVersion = 2` 与 `3`。
- `before()`：创建一张分区表（bucket(data,16)），写入两个数据文件并 append。
- `readPositionDeletesTableWithNoDeleteFiles`：无删除文件时 `planFiles()` 应为空。
- `readPositionDeletesTableWithMultipleDeleteFiles`：通过 `FileHelpers.writeDeleteFile(..., formatVersion)` 写入两个位置删除文件（v2 写传统位置删除；v3 写 DV），分别针对两个数据文件，commit `newRowDelta`。然后读取 `position_deletes` 元数据表，投影 `DELETE_FILE_PATH`、`DELETE_FILE_POS`、`PositionDeletesTable.DELETE_FILE_PATH`。断言每个 scan task 读出的行符合预期：
  - v3（DV）时 `dataFileLocation` 取自 `deleteFile.referencedDataFile()`；v2 时取自 `dataFile.location()`。
  - 行号、文件路径与写入一致。
- `readPositionDeletesTableWithDifferentColumnOrdering`：投影列以"倒序"选择（先 `DELETE_FILE_POS` 再 `DELETE_FILE_PATH`），验证投影列顺序无关性。
- 辅助方法 `internalRowsToJava`/`toJava` 用 `SparkSchemaUtil` 把 `InternalRow` 转 `Object[]` 便于断言。

## 小结

- **成效**：Spark 读取 `.position_deletes` 元数据表现在能正确处理 v3 DV 删除文件，把位图展开为 `(file_path, pos)` 行；复用 `GenericInternalRow` 减少分配；测试覆盖 v2/v3 与多种投影场景。
- **影响范围**：`spark/v3.5` 模块的 1 个新增主代码类、1 个修改的 reader、1 个新增测试类，共 407 行新增。仅影响元数据表读取路径，不影响正常数据扫描与删除应用逻辑。
- **回迁到 1.4.x 的注意事项**：
  - DV 是 v3 特性，1.4.x 是否支持 v3 表格式是回迁的前提。若 1.4.x 已支持 v3/DV（依赖 `ContentFileUtil.isDV`、`BaseDeleteLoader.loadPositionDeletes`、`DeleteFile.referencedDataFile()` 等 API），则可回迁。
  - 需确认 1.4.x 的 `PositionDeletesRowReader` 与本提交基线一致；若 1.4.x 该 reader 接口签名不同（如构造参数、方法名），需做适配。
  - 依赖的 `BaseDeleteLoader.loadPositionDeletes` 与 `ContentFileUtil.isDV` 必须在 1.4.x 中可用，否则需一并回迁其依赖。
  - 仅作用于 `spark/v3.5`；若 1.4.x 同时维护 spark/v3.4 等其他分支，应评估是否需要同步（本提交未触及 v3.4，因为 v3.4 可能尚未支持 DV，或需单独评估）。
  - 回迁后应运行新增的 `TestPositionDeletesReader` 在 v2/v3 两套参数下验证。
