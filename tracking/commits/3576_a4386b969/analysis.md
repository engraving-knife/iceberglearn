# 提交 3576：Core, Spark: Verify that TRUNCATE removes orphaned DVs (#16078)

## 提交信息

- **序号**：3576 / 4088
- **哈希**：a4386b96971e306008d013e079d62dfef53ddcce
- **短哈希**：a4386b969
- **日期**：2026-04-23 19:33:09 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core, Spark: Verify that TRUNCATE removes orphaned DVs (#16078)
- **PR/Issue**：#16078

## 总体目的

该提交添加测试来验证 TRUNCATE 操作（通过 `deleteFromRowFilter(Expressions.alwaysTrue())` 实现）能够正确移除孤立的删除向量（Deletion Vectors, DVs）。删除向量是 Iceberg 格式版本 3 引入的特性，用于标记数据文件中被删除的行位置。当表被 TRUNCATE（删除所有数据）时，所有数据文件都被移除，此时引用这些数据文件的 DVs 成为"孤立"的（orphaned），也应被移除。

之前已有两个测试用例验证了通过行过滤删除数据文件时 DVs 被移除的行为，但这些测试未检查快照摘要中的 `REMOVED_DVS_PROP` 和 `REMOVED_DELETE_FILES_PROP`。该提交一方面为已有测试补充了快照摘要断言，另一方面新增了一个专门的 TRUNCATE 场景测试（Core 层和 Spark 层各一个），验证当 `deleteFromRowFilter(alwaysTrue())` 时所有 DVs 都被正确移除。

## 如何达成设计目的

在 Core 层的 `TestDeleteFiles` 中新增单元测试，直接构建带有 DVs 的数据文件，执行 `deleteFromRowFilter(alwaysTrue())`，验证快照摘要中 `REMOVED_DVS_PROP` 和 `REMOVED_DELETE_FILES_PROP` 的值，以及删除清单中 DVs 的状态为 DELETED。在 Spark 层的 `TestDeleteFrom` 中新增端到端测试，通过 Spark SQL 执行 DELETE 和 TRUNCATE 操作，验证快照摘要中的 DV 相关属性。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestDeleteFiles.java` (+71/-0 lines)

**修改目的**：为已有测试补充 DV 移除断言，新增 TRUNCATE 场景测试。

**工作逻辑**：
- 在两个已有测试中，为 `deleteSnap.summary()` 新增断言：`REMOVED_DVS_PROP = "1"` 和 `REMOVED_DELETE_FILES_PROP = "1"`，验证删除数据文件时 DV 也被移除并记录在摘要中。
- 新增 `removingDataFilesWhenTruncatingAlsoRemovesDVs` 测试：
  - 构建 DV 文件 `dv1`（引用 `DATA_FILE_BUCKET_0_IDS_0_2`）和 `dv2`（引用 `DATA_FILE_BUCKET_0_IDS_8_10`），通过 `newRowDelta` 添加两个数据文件和两个 DVs。
  - 执行 `table.newDelete().deleteFromRowFilter(Expressions.alwaysTrue())`（即 TRUNCATE）。
  - 验证 `REMOVED_DVS_PROP = "2"` 和 `REMOVED_DELETE_FILES_PROP = "2"`，两个 DVs 都被移除。
  - 验证删除清单中 dv1 和 dv2 的状态均为 `Status.DELETED`。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/sql/TestDeleteFrom.java` (+38/-0 lines)

**修改目的**：Spark SQL 端到端验证 TRUNCATE 移除 DVs。

**工作逻辑**：
新增 `truncateWithDVs` 测试：
- 创建 format-version=3、merge-on-read 删除模式的表，插入 3 条记录。
- 执行 `DELETE FROM %s WHERE id = 1`：验证摘要中 `ADDED_DVS_PROP = "1"`、`ADDED_POS_DELETES_PROP = "1"`。
- 执行 `DELETE FROM %s WHERE id = 2`：验证 `ADDED_DVS_PROP = "1"`、`REMOVED_DVS_PROP = "1"`（DVs 合并）、`ADDED_POS_DELETES_PROP = "2"`。
- 执行 `TRUNCATE TABLE %s`：验证 `REMOVED_DVS_PROP = "1"`、`REMOVED_DELETE_FILES_PROP = "1"`、`REMOVED_POS_DELETES_PROP = "2"`，所有 DV 和删除文件都被移除。
- 验证表为空。

## 总结

该提交通过添加测试验证了 TRUNCATE 操作正确移除孤立 DVs 的行为。这是一个测试增强提交，确保删除向量在表截断时不会残留，防止存储空间浪费和潜在的读取正确性问题。测试覆盖 Core 层单元测试和 Spark 层端到端测试两个层面。
