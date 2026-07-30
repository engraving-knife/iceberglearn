# 提交 2311：Spark 3.4: Prevent unnecessary failure when executing DML queries with identifier fields (#13448)

## 提交信息

- **序号**：2311 / 4088
- **哈希**：b86dc9e94c9778d3091fbf104015865973e1d319
- **短哈希**：b86dc9e94
- **日期**：2025-07-02 16:49:33 -0700
- **作者**：Szehon Ho
- **提交说明**：Spark 3.4: Prevent unnecessary failure when executing DML queries with identifier fields (#13448)
- **PR/Issue**：#13448

## 总体目的

这个提交修复了 Spark 3.4 中执行带有标识字段（identifier fields）的 DML（数据操作语言）查询时出现的不必要失败问题。

在 Iceberg 中，标识字段（identifier fields）是表 schema 中用于唯一标识行的字段集合。当构建元数据列（metadata columns）的 Schema 时，代码错误地传递了表的 `identifierFieldIds()`。这导致当扫描只包含元数据列（如 `_pos`、`_partition` 等）而不包含实际数据列时，Schema 中引用了不存在的标识字段 ID，从而引发验证失败。

这种失败是"不必要的"，因为元数据列的扫描不应该受到标识字段的约束。元数据列（如 `_pos` 表示行位置）是 Iceberg 内部使用的虚拟列，与表的标识字段无关。将标识字段 ID 传递给元数据列 Schema 会导致 Schema 验证失败，因为引用的字段 ID 实际上不在元数据列的 Schema 中。

## 如何达成设计目的

核心修复是在 `SparkScanBuilder` 中构建元数据列 Schema 时，将 `table.schema().identifierFieldIds()` 替换为 `ImmutableSet.of()`（空集合）。这样元数据列的 Schema 不再包含任何标识字段 ID，避免了不必要的验证失败。

此外，测试文件也做了相应改进：将测试表中的 `id` 字段从 `optional` 改为 `required`，并修改了测试数据构造方式（从 `createDataset` 改为 `createDataFrame` 以支持 not-null 约束），新增了专门测试标识字段场景的测试用例 `testIdentifierFields`。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java` (+3/-1 lines)

**修改目的**：修复元数据列 Schema 中错误包含标识字段 ID 的问题。

**工作逻辑**：在 `pruneMetadataColumns` 方法（推测）中构建新 Schema 时，将 `table.schema().identifierFieldIds()` 改为 `ImmutableSet.of()`。元数据列扫描场景下不需要标识字段信息，传入空集合确保 Schema 不会引用不存在的字段 ID，从而避免验证错误。同时添加了 `ImmutableSet` 的 import。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkMetadataColumns.java` (+27/-13 lines)

**修改目的**：改进测试以覆盖标识字段场景，并修复测试数据构造方式。

**工作逻辑**：
1. 将测试表的 `id` 字段从 `optional` 改为 `required`，使其可以设为标识字段
2. 将 `createDataset(ids, Encoders.LONG())` 替换为 `createDataFrame(rows, StructType)`，因为后者可以正确表达 not-null 约束
3. 新增 `testIdentifierFields` 测试方法：设置 `id` 为标识字段后执行 INSERT 操作，验证 `_spec_id` 和 `_partition` 元数据列能正确查询。这个测试在修复前会失败。

## 总结

这个提交修复了一个影响标识字段表上 DML 操作的 bug。问题根源是元数据列 Schema 错误地继承了表的标识字段 ID，导致验证失败。修复方式简洁有效（传入空集合），同时新增了针对性测试用例以防止回归。该修复仅限于 Spark 3.4 模块。
