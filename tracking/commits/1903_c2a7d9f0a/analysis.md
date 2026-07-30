# 提交 1903：Spark 3.4: Detect dangling DVs properly

## 提交信息

- **序号**：1903 / 4088
- **哈希**：c2a7d9f0ad1406a74d972ed1dc2b7bee1606944a
- **短哈希**：c2a7d9f0a
- **日期**：2025-03-22 10:53:33 -0600
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark 3.4: Detect dangling DVs properly

  this backports #12270 to Spark 3.4
- **PR/Issue**：backport #12270

## 总体目的

这个提交修复了 `RemoveDanglingDeletesSparkAction` 在 v3 表上无法正确检测悬挂 DV（Dangling Deletion Vectors）的问题。

"悬挂删除文件"（dangling delete files）是指引用了已不存在数据文件的删除文件。当数据文件被过期清理或重写后，关联的删除文件可能变成悬挂状态——它们引用的数据文件已不存在，因此这些删除文件本身也应该被清理。

在 v2 中，position delete 文件的悬挂检测基于 sequence number 和分区匹配。但在 v3 中，DV 文件使用 Puffin 格式存储，并通过 `referenced_data_file` 字段指向其关联的数据文件。原有的 `findDanglingDeletes()` 方法只检测传统的 delete entries，无法识别 DV 格式的悬挂删除文件。

本提交新增 `findDanglingDvs()` 方法，专门检测引用了不存在数据文件的 DV 文件。

## 如何达成设计目的

整体设计思路是在 `RemoveDanglingDeletesSparkAction.doExecute()` 中同时收集传统悬挂删除文件和悬挂 DV：

1. 新增 `findDanglingDvs()` 方法：通过 join `delete_files` 元数据表和 `data_files` 元数据表，找出 `referenced_data_file` 在 `data_files` 中不存在的 DV 文件（即引用的数据文件已被删除的 DV）。

2. 将两种悬挂删除文件合并到 `DeleteFileSet` 中统一处理。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RemoveDanglingDeletesSparkAction.java` (修改, +38/-1 lines)

**修改目的**：新增 DV 悬挂检测逻辑。

**工作逻辑**：

1. `doExecute()` 方法中，将 `List<DeleteFile>` 改为 `DeleteFileSet`，分别添加 `findDanglingDeletes()` 和新增的 `findDanglingDvs()` 的结果。

2. 新增 `findDanglingDvs()` 方法：
   - 加载 `delete_files` 元数据表，过滤出 `file_format` 为 `PUFFIN` 的记录（即 DV 文件）
   - 加载 `data_files` 元数据表
   - 对两表进行 left outer join，条件为 `dvs.referenced_data_file = data_files.file_path`
   - 过滤出 `data_files.file_path` 为 null 的记录（即引用的数据文件不存在）
   - 将结果映射为 `SparkDeleteFile` 对象返回

3. 修复了一个注释拼写错误：`delete fies` → `delete files`。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewritePositionDeleteFilesAction.java` (修改, +91 lines)

**修改目的**：新增悬挂 DV 检测的测试用例。

**工作逻辑**：新增测试验证当数据文件被删除后，引用它的 DV 文件能被正确检测并清理。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestPositionDeletesTable.java` (修改, +11/-9 lines)

**修改目的**：适配性修改。

## 总结

本提交修复了 `RemoveDanglingDeletesSparkAction` 无法检测悬挂 DV 的问题。新增 `findDanglingDvs()` 方法通过 join delete_files 和 data_files 元数据表，找出引用了已删除数据文件的 DV 文件。这确保了 v3 表的 DV 文件在数据文件被清理后也能被正确识别和清理，避免悬挂删除文件影响查询性能。
