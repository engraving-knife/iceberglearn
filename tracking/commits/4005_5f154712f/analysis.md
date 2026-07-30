# 提交 4005：Core, Spark: Fix row lineage last updated sequence inheritance (#17039)

## 提交信息

- **序号**：4005 / 4088
- **哈希**：5f154712f799a0b24d4d2dac8d889de67aefa051
- **短哈希**：5f154712f
- **日期**：2026-07-09 10:42:43 -0600
- **作者**：Gang Wu
- **提交说明**：Core, Spark: Fix row lineage last updated sequence inheritance (#17039)
- **PR/Issue**：#17039

## 总体目的

本提交修复 row lineage（行血缘）中 `_last_updated_sequence_number` 元数据列的继承逻辑 bug。该列用于在扫描时向 reader 暴露每行最后更新的序列号。

问题在于：`PartitionUtil` 在构建扫描常量时，错误地使用 `task.file().fileSequenceNumber()`（文件被提交到 manifest 时的序列号）作为 `_last_updated_SEQUENCE_NUMBER` 的值，而非 `dataSequenceNumber()`（数据行实际写入时的序列号）。

这在以下场景产生错误：当一个 V2 表执行 `rewrite`（数据文件重写）时，rewrite 携带的是较老的 data sequence number（保留原始数据语义），但 file sequence number 是新的。如果后来该表升级到 V3（启用 row lineage），扫描时 `_last_updated_sequence_number` 应该反映数据行的原始更新序列（data sequence number），而不是文件重写的提交序列（file sequence number）。原实现用 file sequence number 会导致升级后行更新序列被错误地推进到 rewrite 时间点。

## 如何达成设计目的

核心修改是将 `PartitionUtil` 中 `_last_updated_sequence_number` 常量的来源从 `fileSequenceNumber()` 改为 `dataSequenceNumber()`，恢复 row lineage spec 的原始措辞——即该列继承数据文件的 data sequence number。

同时新增测试验证：V2 表 rewrite 后升级到 V3，扫描时 `_last_updated_SEQUENCE_NUMBER` 等于原始 data sequence number 而非新的 file sequence number。

## 修改详情

### `core/src/main/java/org/apache/iceberg/util/PartitionUtil.java` (+1/-1 lines)

**修改目的**：修复 `_last_updated_sequence_number` 常量来源。

**工作逻辑**：
```java
idToConstant.put(
    MetadataColumns.LAST_UPDATED_SEQUENCE_NUMBER.fieldId(),
    convertConstant.apply(Types.LongType.get(), task.file().dataSequenceNumber()));  // 原为 fileSequenceNumber()
```
改为使用 `dataSequenceNumber()`，确保扫描时该列反映数据行的原始更新序列，而非文件重写/提交序列。

### `core/src/test/java/org/apache/iceberg/TestRowLineageAssignment.java` (+41/-0 lines)

**修改目的**：新增测试验证升级后 last updated sequence 的正确继承。

**工作逻辑**：
`lastUpdatedAfterUpgrade` 测试：
1. 创建 V2 表，append FILE_A，记录原始 sequence number。
2. 执行 `newRewrite()`，用 FILE_B 替换 FILE_A，并传入 `originalSequenceNumber` 作为 rewrite 的 sequence number（模拟保留原始数据序列）。
3. 升级表到 V3。
4. 执行 `newFastAppend()` 触发 row lineage 分配（不重写数据文件）。
5. 扫描并断言：
   - 文件是 FILE_B。
   - `dataSequenceNumber()` 等于 originalSequenceNumber。
   - `fileSequenceNumber()` 大于 originalSequenceNumber（rewrite 是新提交）。
   - `firstRowId()` 非空（V3 已分配行 ID）。
   - `PartitionUtil.constantsMap(task).get(LAST_UPDATED_SEQUENCE_NUMBER.fieldId())` 等于 originalSequenceNumber（验证修复）。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (+43/-0 lines)
### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (+43/-0 lines)
### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (+43/-0 lines)

**修改目的**：在 Spark 3.5/4.0/4.1 三个版本的 rewrite 测试中新增相同场景的端到端验证。

**工作逻辑**：三个文件改动相同，验证 Spark 层面 rewrite 后 `_last_updated_sequence_number` 的正确性（具体测试内容与核心测试类似，针对 Spark actions 框架）。

## 总结

本提交修复了一个 row lineage 序列号继承的语义 bug：将 `_last_updated_sequence_number` 从基于 file sequence number 改为基于 data sequence number，确保 V2 rewrite + V3 升级场景下行更新序列反映原始数据写入时间而非文件重写时间。改动很小（一行核心代码）但语义影响重要，配套在核心和三个 Spark 版本中新增了测试覆盖。
