# 提交 3451：Spark: Replicate position delete array/map fix to Spark 3.4, 3.5, and 4.0 (#15743)

## 提交信息

- **序号**：3451 / 4088
- **哈希**：32b2f00125261b93c29aca9068d78390135a80f1
- **短哈希**：32b2f00125
- **日期**：2026-03-23 14:32:39 -0700
- **作者**：Szehon Ho
- **提交说明**：Spark: Replicate position delete array/map fix to Spark 3.4, 3.5, and 4.0 (#15743)
- **PR/Issue**：#15743

## 总体目的

将 PR #15632（提交 3449）中针对 Spark 4.1 的 position delete array/map 修复，移植到 Spark 3.4、3.5 和 4.0 模块。原始 PR 修复了包含 array/map 列时 `rewrite_position_delete_files` 失败的问题，但只更新了 Spark 4.1 模块。

## 如何达成设计目的

- 在 Spark 3.4、3.5、4.0 三个模块中复制相同的 `PositionDeletesRowReader` 修改
- 移植 `TestRewritePositionDeleteFilesProcedure` 和 `TestPositionDeletesTable` 的测试用例
- 核心修改（`ExpressionUtil.extractByIdInclusive`）在 api 模块中，已被原始 PR 修改，各 Spark 版本共享

## 修改详情

### Spark 3.4 模块

#### `spark/v3.4/spark-extensions/src/test/.../TestRewritePositionDeleteFilesProcedure.java` (+62/-0 lines)
**修改目的**：添加包含 array/map 列的 rewrite_position_delete_files 测试。

#### `spark/v3.4/spark/src/main/.../PositionDeletesRowReader.java` (+8/-13 lines)
**修改目的**：修改 PositionDeletesRowReader 以保留所有非恒定字段（包括 array/map）。
**工作逻辑**：与 Spark 4.1 修改一致，移除 `nonConstantFieldIds()` 方法中对原始类型的过滤。

#### `spark/v3.4/spark/src/test/.../TestPositionDeletesTable.java` (+54/-0 lines)
**修改目的**：添加包含 array/map 列的 position_deletes 元数据表测试。

### Spark 3.5 模块

与 Spark 3.4 完全相同的修改，应用于三个文件。

### Spark 4.0 模块

与 Spark 3.4 完全相同的修改，应用于三个文件。

## 总结

该提交是 PR #15632 的后续移植提交，将 position delete array/map 修复从 Spark 4.1 移植到 Spark 3.4、3.5 和 4.0 三个模块。每个模块都修改了 `PositionDeletesRowReader`（移除原始类型过滤）并添加了对应的测试用例。核心的 `ExpressionUtil` 修改在共享的 api 模块中，不需要重复修改。
