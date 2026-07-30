# 提交 1894：Spark 3.4: Read DVs when reading from .position_deletes table

## 提交信息

- **序号**：1894 / 4088
- **哈希**：df27946bac0f19443842975bdd6c56174dcc0e15
- **短哈希**：df27946ba
- **日期**：2025-03-21 09:46:32 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark 3.4: Read DVs when reading from .position_deletes table
- **PR/Issue**：无 PR 号（提交说明中未包含）

## 总体目的

这个提交为 Spark 3.4 的 `.position_deletes` 元数据表添加了读取 DV（Deletion Vector，删除向量）的支持。DV 是 Iceberg format-version v3 引入的新删除格式，与传统的基于行的 position deletes 不同，DV 以紧凑的位图形式存储被删除的行位置。

`.position_deletes` 是 Iceberg 的元数据表，用于查看表中的位置删除记录。在 v3 中，删除操作可以生成 DV 文件而非传统的 position delete 文件。在此之前，Spark 3.4 的 `PositionDeletesRowReader` 只能读取传统的 position delete 文件格式，无法正确解析 DV 格式的删除文件。

本提交新增了 `DVIterator` 类，专门用于从 DV 格式的删除文件中读取删除位置，并将其转换为 Spark 的 `InternalRow` 格式，使 `.position_deletes` 表能正确显示 DV 格式的删除记录。

## 如何达成设计目的

整体设计思路是在 `PositionDeletesRowReader` 中检测删除文件是否为 DV 格式，如果是则使用新的 `DVIterator` 来迭代删除位置，否则继续使用原有的 position delete 读取路径。

关键组件：
- `DVIterator`：新的迭代器类，使用 `BaseDeleteLoader` 加载 DV 中的删除位置，并将其映射为 Spark InternalRow
- `ContentFileUtil.isDV()`：用于检测文件是否为 DV 格式
- `PositionDeletesRowReader`：在读取逻辑中添加 DV 分支

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/DVIterator.java` (新增, +101 lines)

**修改目的**：创建专门用于迭代 DV 格式删除文件的迭代器。

**工作逻辑**：

`DVIterator` 实现了 `CloseableIterator<InternalRow>` 接口。构造函数接收 `InputFile`、`DeleteFile`、`Schema`（投影）和 `idToConstant`（常量列映射）。

核心逻辑：
1. 构造时使用 `BaseDeleteLoader` 加载 DV 文件中的所有删除位置，存储为 `Iterator<Long>`。
2. `next()` 方法根据投影 schema 构建 `GenericInternalRow`：
   - `DELETE_FILE_PATH` 列：填入删除文件引用的数据文件路径
   - `DELETE_FILE_POS` 列：填入当前删除位置，并记录该位置在 row 中的索引
   - `PARTITION_COLUMN_ID`、`SPEC_ID_COLUMN_ID`、`FILE_PATH_COLUMN_ID`：从 `idToConstant` 中获取常量值
3. 首次调用 `next()` 时创建 row，后续调用仅更新删除位置字段（性能优化，避免重复创建对象）。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/PositionDeletesRowReader.java` (修改, +5 lines)

**修改目的**：在读取逻辑中添加 DV 格式分支。

**工作逻辑**：在构建迭代器的方法中，新增检查：如果 `ContentFileUtil.isDV(task.file())` 返回 true，则返回 `DVIterator`；否则继续走原有的 position delete 读取路径（`newIterable`）。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestPositionDeletesReader.java` (新增, +301 lines)

**修改目的**：添加 DV 读取的测试用例。

**工作逻辑**：新增大量测试验证从 `.position_deletes` 表读取 DV 格式删除文件的正确性，包括投影、常量列、文件路径等场景。

## 总结

本提交为 Spark 3.4 的 `.position_deletes` 元数据表添加了 DV（删除向量）格式的读取支持。通过新增 `DVIterator` 类和使用 `ContentFileUtil.isDV()` 检测，使读者能根据删除文件格式选择正确的读取路径。这是 Iceberg v3 DV 功能在 Spark 3.4 中逐步落地的一部分。
