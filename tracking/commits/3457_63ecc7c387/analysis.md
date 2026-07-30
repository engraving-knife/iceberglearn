# 提交 3457：Spark: fix NPE thrown for MAP/LIST columns on DELETE, UPDATE, and MERGE operations (#15726)

## 提交信息

- **序号**：3457 / 4088
- **哈希**：63ecc7c3871ec1a1846c2d335c2055ea1865a81f
- **短哈希**：63ecc7c387
- **日期**：2026-03-24 23:06:56 -0700
- **作者**：antonlin1
- **提交说明**：Spark: fix NPE thrown for MAP/LIST columns on DELETE, UPDATE, and MERGE operations (#15726)
- **PR/Issue**：#15726

## 总体目的

修复 Spark 4.1 中当表包含 MAP/LIST 类型列时，在执行 DELETE、UPDATE 和 MERGE 操作时抛出 NPE 的问题。

**根本原因**：`BaseSparkScanBuilder.allUsedFieldIds()` 方法使用了 `TypeUtil.getProjectedIds()` 来收集所有已使用的字段 ID，但该方法是为列投影设计的，会省略 MAP 和 LIST 的字段 ID。这导致 `_partition` 结构的子 ID 被重新分配为与 MAP/LIST 列相同的 ID，在 merge-on-read 扫描中当 `_partition` 元数据列包含在投影中时，触发 `PruneColumns.isStruct()` 中的 NPE。

## 如何达成设计目的

- 将 `TypeUtil.getProjectedIds()` 替换为 `TypeUtil.indexById().keySet()`
- `indexById()` 会递归索引所有字段 ID（包括 MAP 和 LIST），与 1.11 之前 Spark 3.5 代码的行为一致
- 添加回归测试验证 MAP 和 LIST 列场景

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/BaseSparkScanBuilder.java` (+1/-1 lines)

**修改目的**：修复 `allUsedFieldIds()` 方法，使其包含所有字段 ID。

**工作逻辑**：
- 旧代码：`TypeUtil.getProjectedIds(tableSchema.asStruct()).stream()` — 省略 MAP/LIST 字段 ID
- 新代码：`TypeUtil.indexById(tableSchema.asStruct()).keySet().stream()` — 递归索引所有字段 ID

关键区别：
- `getProjectedIds()` 是为列投影设计的，只返回叶子原始类型字段
- `indexById()` 递归遍历所有类型（包括 struct、list、map），返回完整的字段 ID 映射

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkMetadataColumns.java` (+82/-0 lines)

**修改目的**：添加 MAP 和 LIST 列的回归测试。

**工作逻辑**：

1. **`testPartitionMetadataColumnWithMapColumn()`**：
   - 创建包含 MAP 类型列（`tags`）的表
   - 使用 bucket 分区
   - 设置 `write.delete.mode` 为 `merge-on-read`（确保 DELETE 走 merge-on-read 路径，会添加 `_partition` 到投影）
   - 插入两行数据到同一文件
   - 执行 DELETE 操作
   - 验证 `SELECT id, _partition` 查询返回正确结果

2. **`testPartitionMetadataColumnWithListColumn()`**：
   - 创建包含 LIST 类型列（`tags`）的表
   - 与 MAP 测试相同的流程

两个测试都确保 merge-on-read DELETE 不会因 MAP/LIST 列导致 NPE。

## 总结

该提交修复了 Spark 4.1 中表包含 MAP/LIST 列时 DELETE/UPDATE/MERGE 操作抛出 NPE 的 bug。根本原因是 `allUsedFieldIds()` 使用了 `getProjectedIds()`（省略 MAP/LIST ID），导致 `_partition` 子 ID 与 MAP/LIST 列 ID 冲突。修复改用 `indexById()` 确保所有字段 ID 被正确索引。
