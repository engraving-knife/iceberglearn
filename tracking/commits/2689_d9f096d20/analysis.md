# 提交 2689：Spark 3.4,3.5: Backport: Add create_file_list option to RewriteTablePathProcedure. (#14180)

## 提交信息

- **序号**：2689 / 4088
- **哈希**：d9f096d2065f66f247359596bcc6a1167b2e342c
- **短哈希**：d9f096d20
- **日期**：2025-09-26 08:45:58 -0700
- **作者**：slfan1989
- **提交说明**：Spark 3.4,3.5: Backport: Add create_file_list option to RewriteTablePathProcedure. (#14180)
- **PR/Issue**：#14180（backport 自 #13837，即提交 2679）

## 总体目的

本提交是提交 2679（PR #13837，为 RewriteTablePath 新增 `create_file_list` 选项和两个结果计数字段）在 Spark 3.4 和 Spark 3.5 模块上的 backport。

提交 2679 在 API 层（`RewriteTablePath` 接口）和 core 层（`BaseRewriteTablePath`）新增的 `createFileList()` 方法和两个计数字段是跨 Spark 版本共享的，已在 2679 中落地。本提交只需将 Spark 版本特定的改动——`RewriteTablePathSparkAction`、`RewriteTablePathProcedure` 和对应测试——同步到 Spark 3.4 和 3.5 两个模块。

功能与 2679 完全相同：允许用户通过 `create_file_list => false` 跳过文件列表生成（返回 "N/A"），并在结果中返回 `rewritten_manifest_file_paths_count` 和 `rewritten_delete_file_paths_count` 两个计数。

## 如何达成设计目的

将提交 2679 对 Spark 4.0 模块的四类文件修改（`RewriteTablePathSparkAction.java`、`RewriteTablePathProcedure.java`、`TestRewriteTablePathProcedure.java`、`TestRewriteTablePathsAction.java`）原样应用到 Spark 3.4 和 3.5 对应模块。八个文件的改动内容与 2679 的 Spark 4.0 版本一致。

## 修改详情

### Spark 3.4 模块（4 个文件）

#### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java` (+32/-6 lines)

**修改目的**：实现 `create_file_list` 选项和计数统计。

**工作逻辑**：与提交 2679 的 Spark 4.0 版本完全一致。新增 `NOT_APPLICABLE` 常量和 `createFileList` 字段（默认 true），实现 `createFileList(boolean)` 方法。重构 `rebuildMetadata()` 返回 `Result` 类型，根据 `createFileList` 标志决定是否生成文件列表，并填充两个新计数字段（`deleteFiles.size()` 和 `metaFiles.size()`）。

#### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/procedures/RewriteTablePathProcedure.java` (+20/-2 lines)

**修改目的**：在存储过程中暴露新参数和输出列。

**工作逻辑**：与提交 2679 一致。新增 `CREATE_FILE_LIST_PARAM` 布尔参数（默认 true），输出类型新增 `rewritten_manifest_file_paths_count` 和 `rewritten_delete_file_paths_count` 两列。`call()` 方法读取 `create_file_list` 参数并调用 `action.createFileList()`，`toOutputRows()` 输出两个新计数列。注意 Spark 3.4 使用 `ProcedureParameter.optional()` 而非 `optionalInParameter()`（后者在 2685 重构中才引入）。

#### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteTablePathProcedure.java` (+92/-0 lines)

**修改目的**：验证 Spark 3.4 存储过程的新选项和输出。

**工作逻辑**：与提交 2679 一致。新增 `testRewriteTablePathWithoutFileList()`（验证 `create_file_list => false` 时 file list location 为 "N/A"）和 `testRewriteTablePathWithManifestAndDeleteCounts()`（验证 manifest 文件和 delete 文件的重写计数）。

#### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteTablePathsAction.java` (+19/-0 lines)

**修改目的**：验证 Spark 3.4 Action API 层的 `createFileList(false)` 行为。

**工作逻辑**：与提交 2679 一致。`testRewritePathWithoutCreateFileList()` 通过 Action API 调用并断言 file list location 为 `NOT_APPLICABLE`。

### Spark 3.5 模块（4 个文件）

与上述 Spark 3.4 的四个文件改动完全一致，分别对应 `spark/v3.5/` 路径下的同名文件。

## 总结

本提交是提交 2679（为 RewriteTablePath 新增 `create_file_list` 选项和两个结果计数字段）在 Spark 3.4 和 3.5 模块的 backport。由于 API 和 core 层的改动已在 2679 中共享落地，本提交仅涉及 Spark 版本特定的 Action、Procedure 和测试文件。两个 Spark 版本的改动与 Spark 4.0 版本完全对应，确保三个版本的行为一致。这体现了 Iceberg 多版本 Spark 适配中功能同步落地的典型模式。
