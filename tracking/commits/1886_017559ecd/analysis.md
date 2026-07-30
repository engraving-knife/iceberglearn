# 提交 1886：Spark: Detect dangling DVs properly (#12270)

## 提交信息

- **序号**：1886 / 4088
- **哈希**：017559ecdcef7d51514795966024f8e2d84409fc
- **短哈希**：017559ecd
- **日期**：2025-03-20 07:40:41 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark: Detect dangling DVs properly (#12270)
- **PR/Issue**：#12270

## 总体目的

本提交修复 `RemoveDanglingDeletesSparkAction` 无法正确检测 dangling（悬空）DV（deletion vector，删除向量）的问题。

背景：Iceberg 的 `RemoveDanglingDeletesSparkAction` 用于清理不再有效的 delete 文件（即删除目标数据文件已不存在的 delete 文件）。此前的 `findDanglingDeletes` 方法通过 `min_data_sequence_number` 等条件检测 dangling 的 position delete 和 equality delete 文件，但对于 DV（存储在 puffin 文件中的 deletion vector，通过 `referenced_data_file` 字段引用其所删除的数据文件）未做检测。

当数据文件被 compaction（合并）或其他操作移除后，指向这些数据文件的 DV 就变成 dangling 的，但旧逻辑不会清理它们，导致这些 puffin 文件残留，既浪费存储又可能在读取时造成无效查找。

本提交新增 `findDanglingDvs` 方法，通过 left outer join delete_files（puffin 格式）与 data_files，找出 `referenced_data_file` 在 data_files 中不存在的 DV，将其作为 dangling delete 清理。

## 如何达成设计目的

1. **新增 findDanglingDvs 方法**：加载 `DELETE_FILES` 元数据表，过滤 `file_format = PUFFIN` 的 DV 文件；与 `DATA_FILES` 元数据表做 left outer join（条件 `dvs.referenced_data_file = dataFiles.file_path`），筛选 `dataFiles.file_path IS NULL` 的行，即为引用了不存在数据文件的 dangling DV。将 Row 映射为 `DeleteFile` 包装对象返回。

2. **合并到 doExecute**：`doExecute` 中将 `findDanglingDeletes()` 和 `findDanglingDvs()` 的结果合并到一个 `DeleteFileSet`（去重），统一通过 `RewriteFiles` 删除。

3. **修复注释笔误**："delete fies" → "delete files"。

4. **测试**：在 `TestRemoveDanglingDeleteAction` 中新增 `testPartitionedDeletesWithDanglingDvs` 测试；在 `TestRewritePositionDeleteFilesAction` 中新增两个测试验证 compaction 后 dangling DV 被清理、以及未 compaction 时有效 DV 不被误删。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RemoveDanglingDeletesSparkAction.java` (修改, +26/-2 lines)

**修改目的**：新增 dangling DV 检测逻辑。

**工作逻辑**：
- 导入 `FileFormat` 和 `DeleteFileSet`。
- `doExecute` 中：创建 `DeleteFileSet danglingDeletes = DeleteFileSet.create();`，`addAll(findDanglingDeletes())` 和 `addAll(findDanglingDvs())` 合并去重，遍历删除。
- 新增 `findDanglingDvs()` 方法：
  - `dvs = loadMetadataTable(DELETE_FILES).where(file_format = PUFFIN)`
  - `dataFiles = loadMetadataTable(DATA_FILES)`
  - left outer join on `dvs.referenced_data_file = dataFiles.file_path`，filter `dataFiles.file_path IS NULL`，select dvs 的所有列。
  - 在 driver 上将 Row 映射为 `SparkDeleteFile`（因 SparkDeleteFile 不可序列化）。
- 修复注释 "delete fies" → "delete files"。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRemoveDanglingDeleteAction.java` (修改, +91/-40 lines)

**修改目的**：测试 dangling DV 检测，并重构测试辅助方法。

**工作逻辑**：
- 提取 `allEntries()` 和 `liveEntries()` 辅助方法（原内联的 entries 查询），减少重复代码。
- 新增 `testPartitionedDeletesWithDanglingDvs` 测试（要求 format-version >= 3）：
  - setup 分区表，append FILE_A/C/D，row delta 添加 FILE_A 的 deletes 和指向不存在的 FILE_B 的 deletes（fileBDeletes、fileB2Deletes）。
  - 执行 `removeDanglingDeleteFiles`，断言移除了 2 个指向 FILE_B 的 delete 文件，FILE_A 的 delete 保留。
  - 验证 live entries 符合预期。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewritePositionDeleteFilesAction.java` (修改, +92/-0 lines)

**修改目的**：端到端验证 compaction 触发 dangling DV 清理及有效 DV 保留。

**工作逻辑**：
- `cleanup` 中新增 `DROP TABLE IF EXISTS` 清理 Spark 注册表。
- `testRemoveDanglingDVsAfterCompaction`：创建分区表（format-version=3），插入 3 批数据，执行两次 delete 产生 DV。然后 `rewriteDataFiles` 并开启 `REMOVE_DANGLING_DELETES=true`、`REWRITE_ALL=true`，compaction 后所有数据文件被合并为 1 个，原 DV 全部变 dangling 被清理。断言 delete 文件数为 0，removedDeleteFilesCount 等于清理前数量。
- `testValidDVsAreNotRemovedDuringDanglingDeletesRemoval`：同样创建表并插入数据、执行一次 delete 产生 DV。然后 `rewriteDataFiles` 开启 `REMOVE_DANGLING_DELETES=true` 但设置阈值使数据文件不被 compaction（`MIN_FILE_SIZE_BYTES=0`、`DELETE_RATIO_THRESHOLD=1.0`）。断言 delete 文件不变（有效 DV 不被误删），removedDeleteFilesCount 为 0。

## 总结

本提交修复 `RemoveDanglingDeletesSparkAction` 无法检测 dangling DV 的问题，新增 `findDanglingDvs` 方法通过 left outer join delete_files 与 data_files 找出引用不存在数据文件的 puffin DV。合并到 `doExecute` 的删除集合中统一清理。新增三个测试覆盖 dangling DV 清理和有效 DV 保留场景。
