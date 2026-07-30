# 提交 1629：Core, Spark: Include content offset/size in PositionDeletesTable (#11808)

## 提交信息

- **序号**：1629 / 4088
- **哈希**：026a9b00459ae458eb4c6c620c78742dc43a00e8
- **短哈希**：026a9b004
- **日期**：2025-01-24（Fri Jan 24 07:47:34 2025 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core, Spark: Include content offset/size in PositionDeletesTable
- **PR/Issue**：#11808

## 总体目的

`PositionDeletesTable` 是 Iceberg 的元数据表之一，把表里所有位置删除（v2 中的 position deletes 文件、v3 中的 DV/deletion vector）以"行"的形式暴露出来，便于排查和工具化读取。v3 引入 DV 后，DV 实际存储在 Puffin 文件中作为一段 blob，一段 blob 在 Puffin 文件内有 `contentOffset` 与 `contentSizeInBytes` 两个属性（API 已通过 #11446 在 `DeleteFile` 上提供）。然而 `PositionDeletesTable` 此前的 schema 没有把这两个属性暴露出来，外部工具若想直接定位到 Puffin 文件中的 DV blob 字节区间，只能依赖 `DeleteFile` API 而无法通过 SQL/扫描元数据表获得，体验不一致，也限制了下游（如清理工具、外部 DV 解析器）对 DV 物理位置的探查。

本提交在 `PositionDeletesTable` 的 schema 中、仅当表格式版本 >= 3 时新增两列：

- `content_offset`（LongType，optional）：DV 内容在 Puffin 文件中的起始偏移；
- `content_size_in_bytes`（LongType，optional）：DV blob 的字节长度。

并在 Spark 3.5 的 `DVIterator` 中填充这两列的值，使通过 Spark SQL 查询 `position_deletes` 元数据表时能看到 DV 的物理位置信息。

## 如何达成设计目的

1. 在 `MetadataColumns` 中预留两个新的列 ID（`CONTENT_OFFSET_COLUMN_ID = Integer.MAX_VALUE - 6`、`CONTENT_SIZE_IN_BYTES_COLUMN_ID = Integer.MAX_VALUE - 7`）；
2. `PositionDeletesTable.calculateSchema()` 中按 `formatVersion >= 3` 条件在原有 6 列基础上追加这两列，避免影响 v2 表的 schema；
3. Spark 3.5 的 `DVIterator.next()` 在拼装 `GenericInternalRow` 时，针对新 fieldId 分别填入 `deleteFile.contentOffset()` 与 `ScanTaskUtil.contentSizeInBytes(deleteFile)`；
4. 测试侧：`TestMetadataTableScans` 更新字段 ID 计数断言（v3 多 2 列）；`TestPositionDeletesReader` 在 v3 路径下额外投影并断言这两列的值。

## 修改详情

### `core/src/main/java/org/apache/iceberg/MetadataColumns.java`（修改，+2）

**修改目的**：为新增两列预留 metadata column ID。

**工作逻辑**：

```
public static final int CONTENT_OFFSET_COLUMN_ID = Integer.MAX_VALUE - 6;
public static final int CONTENT_SIZE_IN_BYTES_COLUMN_ID = Integer.MAX_VALUE - 7;
```

注意只声明了 ID 常量，没有像 `DELETE_FILE_PATH` 那样直接定义 `NestedField`——这是因为这两列只在 v3 表里出现，且其 doc/name 由 `PositionDeletesTable` 自身定义。ID 取 `Integer.MAX_VALUE - 6/7`，紧接 `PARTITION_COLUMN_ID`（`MAX_VALUE - 5`）之后，落在 Iceberg 预留的 metadata column ID 区段。

### `core/src/main/java/org/apache/iceberg/PositionDeletesTable.java`（修改，+51/-22）

**修改目的**：在 schema 中按 v3 条件追加 `content_offset` / `content_size_in_bytes` 列。

**工作逻辑**：

- 新增两个列名常量：
  ```
  public static final String CONTENT_OFFSET = "content_offset";
  public static final String CONTENT_SIZE_IN_BYTES = "content_size_in_bytes";
  ```
- `calculateSchema()` 改造：原来用 `ImmutableList.of(...)` 一次性构造 6 列；现在改用 `ImmutableList.Builder` 累加，先添加原有 6 列（`DELETE_FILE_PATH` metadata 列、`DELETE_FILE_POS`、`DELETE_FILE_ROW`、`PARTITION`、`SPEC_ID`、`DELETE_FILE_PATH` 字符串列），随后：
  ```
  if (formatVersion >= 3) {
    builder
      .add(optional(CONTENT_OFFSET_COLUMN_ID, CONTENT_OFFSET, LongType,
          "The offset in the DV where the content starts"))
      .add(optional(CONTENT_SIZE_IN_BYTES_COLUMN_ID, CONTENT_SIZE_IN_BYTES, LongType,
          "The length in bytes of the DV blob"));
  }
  ```
  仅 v3 表追加这两列；v2 表 schema 不变，保持向后兼容。两列都设为 `optional`，因为对于非 DV 的位置删除文件（虽然 v3 主要走 DV，但接口上保留可选语义）这两个值可能为 null。
- `formatVersion` 通过 `TableUtil.formatVersion(table())` 获取。

### `core/src/test/java/org/apache/iceberg/TestMetadataTableScans.java`（修改，+6/-1）

**修改目的**：调整字段 ID 总数断言以反映 v3 多出的 2 列。

**工作逻辑**：

```
int expectedIds =
    formatVersion >= 3
        ? 2012 // partition col + 8 columns + 2003 ids inside the deleted row column
        : 2010; // partition col + 6 columns + 2003 ids inside the deleted row column
assertThat(TypeUtil.indexById(positionDeletesTable.schema().asStruct()).size())
    .isEqualTo(expectedIds);
```

v3 多出 2 列（`content_offset`、`content_size_in_bytes`），ID 总数从 2010 增至 2012。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/DVIterator.java`（修改，+5）

**修改目的**：在 Spark 读取 `position_deletes` 元数据表、迭代 DV 行时填充新列。

**工作逻辑**：

`DVIterator` 负责把一个 DV 展开成多行（每个被删除的位置一行）。`next()` 中根据 `projection.columns()` 的 fieldId 决定每列的取值，本次新增两个分支：

```
} else if (fieldId == MetadataColumns.CONTENT_OFFSET_COLUMN_ID) {
  rowValues.add(deleteFile.contentOffset());
} else if (fieldId == MetadataColumns.CONTENT_SIZE_IN_BYTES_COLUMN_ID) {
  rowValues.add(ScanTaskUtil.contentSizeInBytes(deleteFile));
}
```

- `contentOffset()` 直接来自 `DeleteFile` API（Puffin 中 DV blob 的起始偏移）；
- `contentSizeInBytes` 通过 `ScanTaskUtil.contentSizeInBytes(deleteFile)` 获取——该方法对 DV（Puffin 格式）返回 `deleteFile.contentSizeInBytes()`（blob 长度），对非 DV 删除文件回退到 `fileSizeInBytes()`。这里调用工具方法而非直接 `deleteFile.contentSizeInBytes()` 是为了与"内容大小"语义统一。

新列与其它 metadata 列一样在第一行时填充，后续行复用同一 `GenericInternalRow` 只更新 position 字段。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestPositionDeletesReader.java`（修改，+50/-20）

**修改目的**：在 v3 路径下投影并断言两列值正确。

**工作逻辑**：

- 测试方法中投影列构造改为按 `formatVersion` 决定是否追加 `CONTENT_OFFSET` / `CONTENT_SIZE_IN_BYTES`；
- 期望行从 `Object[]{...}` 改为 `List<Object>` 动态构造：v3 时每行追加 `deleteFile1.contentOffset()` 与 `deleteFile1.contentSizeInBytes()`（对两个 scan task 的两行均如此），最后 `containsExactly(first.toArray(), second.toArray())`；
- 对 `deleteFile2` 的第二段扫描任务对称处理。

## 小结

- **成效**：补齐了 `PositionDeletesTable` 在 v3 下对 DV 物理位置（`content_offset`、`content_size_in_bytes`）的暴露，外部工具可通过 SQL/扫描元数据表直接拿到 DV blob 在 Puffin 文件中的字节区间，无需绕道 `DeleteFile` API；为后续 DV 排查、清理、外部解析器提供一致的数据访问路径。
- **影响范围**：仅 v3 表 schema 新增 2 列、v2 完全不变；Spark 3.5 `DVIterator` 新增 2 个分支。新增列均为 optional，对旧客户端读取 v3 表无破坏（不投影则不读取）。`MetadataColumns` 中新增的两个 ID 在 `Integer.MAX_VALUE - 6/7` 区段，未与现有列冲突。
- **回迁到 1.4.x 的注意事项**：
  - 依赖 `DeleteFile.contentOffset()` / `contentSizeInBytes()` API，这两个由 #11446 引入；1.4.x 若已合入 #11446 即可回迁；
  - 依赖 `ScanTaskUtil.contentSizeInBytes`（同样来自 #11446）；
  - 依赖 `TableUtil.formatVersion(table())`，确认 1.4.x 上有等价 API；
  - 1.4.x 若 DV 支持尚未完整落地，回迁后这两列在 v3 表上会始终为 null（没有 DV 写入路径），但 schema 仍会新增，需确认下游消费者能容忍 null。Spark 3.4/3.3 模块若也有 `DVIterator` 等价类，回迁时应同步覆盖，本提交仅修改了 Spark 3.5。
