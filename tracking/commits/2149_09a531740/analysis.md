# 提交 2149：Spark, Flink: Backport add max files rewrite option for RewriteAction

## 提交信息

- **序号**：2149 / 4088
- **哈希**：09a531740ccec41d69c166ec484be6da16a09947
- **短哈希**：09a531740
- **日期**：2025-05-20 05:38:17 -0700
- **作者**：B Vadlamani
- **提交说明**：Spark, Flink: Backport add max files rewrite option for RewriteAction (#13082)
- **PR/Issue**：#13082（backport #12824）

## 总体目的

这个提交是将 #12824（提交 2140）中新增的 `max-files-to-rewrite` 选项 backport 到 Spark 和 Flink 的旧版本。原始功能在 main 分支的 Core、Flink 2.0 和 Spark v3.5/v4.0 中实现，但 Spark v3.4 和 Flink 1.19/1.20 作为仍受支持的版本也需要这一功能。该选项允许用户限制单次数据文件重写操作处理的文件数量，便于在大规模数据场景下分批执行重写操作，控制资源使用和执行时间。

## 如何达成设计目的

1. 在 Flink 1.19 和 1.20 的 RewriteDataFiles.java 中添加 MAX_FILES_TO_REWRITE 常量和相关配置支持。
2. 在 Spark v3.4 的 RewriteDataFilesSparkAction.java 中添加 MAX_FILES_TO_REWRITE 到支持的属性集合。
3. 更新文档中 Spark procedures 的说明，提及 max-files-to-rewrite 选项。

## 修改详情

### `docs/docs/spark-procedures.md` (修改, +2/-1 lines)

**修改目的**：在 Spark 过程文档中说明 max-files-to-rewrite 选项。

**工作逻辑**：在 RewriteDataFiles 过程的文档中添加对 max-files-to-rewrite 参数的说明，告知用户可以通过该参数限制重写的最大文件数。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/RewriteDataFiles.java` (修改, +12 lines)

**修改目的**：在 Flink 1.19 中暴露 max-files-to-rewrite 选项。

**工作逻辑**：添加 MAX_FILES_TO_REWRITE 常量定义和配置属性支持，与 Flink 2.0 的实现一致。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/RewriteDataFiles.java` (修改, +6 lines)

**修改目的**：在 Flink 1.20 中暴露 max-files-to-rewrite 选项。

**工作逻辑**：与 Flink 1.19 类似，添加配置支持（由于部分代码可能已存在，新增行数较少）。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteDataFilesSparkAction.java` (修改, +2/-1 lines)

**修改目的**：在 Spark 3.4 中支持 max-files-to-rewrite 选项。

**工作逻辑**：将 MAX_FILES_TO_REWRITE 添加到 RewriteDataFilesSparkAction 支持的属性集合中。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/RewriteDataFilesSparkAction.java`（如有修改）(修改)

**修改目的**：更新测试以适配新增属性。

## 总结

这个提交是 #12824 的 backport，将 max-files-to-rewrite 选项扩展到 Spark 3.4 和 Flink 1.19/1.20 版本。同时更新了文档，确保用户了解该新功能的使用方法。该 backport 保持了与原始实现的一致性。
