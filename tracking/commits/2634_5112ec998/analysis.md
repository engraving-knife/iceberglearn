# 提交 2634：Core: Don't copy stats of delete files in DeleteFileIndex (#13161)

## 提交信息

- **序号**：2634 / 4088
- **哈希**：5112ec998722fdcae0600d3fe929d70adf332dc8
- **短哈希**：5112ec998
- **日期**：2025-09-15 08:56:13 +0200
- **作者**：Yuya Ebihara
- **提交说明**：Core: Don't copy stats of delete files in DeleteFileIndex (#13161)
- **PR/Issue**：#13161

## 总体目的

`DeleteFileIndex` 在构建删除文件索引时，会将扫描到的删除文件调用 `entry.file().copy()` 进行完整拷贝（包含全部统计信息），以便后续基于统计信息对数据文件做更好的过滤。然而，完整拷贝所有列的统计信息会占用大量内存，尤其在删除文件较多或列数较多时会造成内存压力。

实际上，对删除文件做过滤时只需要保留少量关键列的统计信息即可：
- 对于位置删除（position deletes）文件，只需要 `DELETE_FILE_PATH` 字段的统计信息（用于按文件路径过滤）。
- 对于等值删除（equality deletes）文件，只需要等值字段（equality field ids）的统计信息（用于按等值字段过滤）。

因此，本提交改为只拷贝所需列的最小统计信息，而非全部统计信息，从而在不损失过滤效果的前提下显著降低内存占用。

## 如何达成设计目的

1. 在 `DeleteFileIndex` 中，将原先的 `entry.file().copy()` 替换为 `ContentFileUtil.copy(file, true, columns)`，其中 `columns` 按删除文件类型选择最小列集合：
   - 位置删除文件：仅 `DELETE_FILE_PATH` 字段 ID。
   - 等值删除文件：该文件的 `equalityFieldIds` 集合。
2. `ContentFileUtil.copy` 接受 `keepStats=true` 和指定列集合，仅保留这些列的统计信息，其余统计信息被丢弃。
3. 新增两个测试用例验证扫描后删除文件确实只保留了预期的列统计信息。

## 修改详情

### `core/src/main/java/org/apache/iceberg/DeleteFileIndex.java` (+7/-1 lines)

**修改目的**：在构建删除文件索引时只拷贝必要的列统计信息。

**工作逻辑**：原先直接 `files.add(entry.file().copy())`。修改后先获取 `DeleteFile file = entry.file()`，然后根据文件内容类型选择需要保留统计信息的列集合：
- 如果是 `POSITION_DELETES`，则 `columns = Set.of(MetadataColumns.DELETE_FILE_PATH.fieldId())`。
- 否则（等值删除），`columns = Set.copyOf(file.equalityFieldIds())`。
最后调用 `ContentFileUtil.copy(file, true, columns)` 仅保留这些列的统计信息。注释说明保留最小统计信息以避免内存压力，同时仍保留统计信息用于更好地过滤。

### `core/src/test/java/org/apache/iceberg/DeleteFileIndexTestBase.java` (+121/-0 lines)

**修改目的**：新增测试验证删除文件只保留了必要的列统计信息。

**工作逻辑**：
- 新增辅助方法 `posDeletesWithMetrics` 构造带完整统计信息的位置删除文件，包含字段 1 和 `DELETE_FILE_PATH` 的各类统计（值计数、空值计数、nan 计数、上下界）。
- 新增辅助方法 `eqDeletesWithMetrics` 构造带完整统计信息的等值删除文件，包含字段 1 和字段 2 的统计。
- 新增测试 `testPositionDeleteDiscardMetrics`：向表添加数据文件和带完整统计的位置删除文件，扫描后断言删除文件仅保留 `DELETE_FILE_PATH` 字段的统计信息，其他字段统计被丢弃。
- 新增测试 `testEqualityDeleteDiscardMetrics`：类似地验证等值删除文件扫描后仅保留等值字段（fieldId=2）的统计信息。

## 总结

本提交优化了 `DeleteFileIndex` 的内存使用，将删除文件统计信息的拷贝从"全量拷贝"改为"按需最小拷贝"。位置删除仅保留文件路径字段统计，等值删除仅保留等值字段统计。这在不降低过滤效果的前提下减少了内存占用，对存在大量删除文件的场景尤其有益。配套测试充分验证了行为正确性。
