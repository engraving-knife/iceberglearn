# 提交分析：Docs: Add Delete Granularity option to write configuration

## 提交信息

| 字段 | 值 |
|------|-----|
| 提交号 | 2122 |
| 短哈希 | `4ea7f9342` |
| 完整哈希 | `4ea7f9342401860e6f4cd54fdd124f1025f93794` |
| 作者 | Tom Tanaka |
| 邮箱 | 43331405+tomtongue@users.noreply.github.com |
| 日期 | 2025-05-14 16:27:45 2025 +0900 |
| 提交信息 | Docs: Add Delete Granularity option to write configuration (#13046) |

## 总体目的

本提交为 Iceberg 文档添加了删除粒度（delete granularity）配置选项的说明。在表属性文档和 Spark 写入选项文档中分别添加了 `write.delete.granularity` 和 `delete-granularity` 配置项的描述，使用户能够了解和控制删除文件的生成粒度。

## 设计目的的实现方式

在两个文档文件中分别添加删除粒度配置项的说明行：
1. `configuration.md`：添加表属性 `write.delete.granularity`
2. `spark-configuration.md`：添加 Spark 写入选项 `delete-granularity`

## 修改详情

### 1. 修改 `configuration.md`

**文件**：`docs/docs/configuration.md`

**修改内容**：在表属性表格中添加一行：

```
| write.delete.granularity | partition | Controls the granularity of generated delete files: partition or file |
```

**目的**：文档化表级别的删除粒度配置。默认值为 `partition`，表示删除操作在分区级别生成删除文件；可选 `file` 表示在文件级别生成删除文件。

### 2. 修改 `spark-configuration.md`

**文件**：`docs/docs/spark-configuration.md`

**修改内容**：在 Spark 写入选项表格中添加一行：

```
| delete-granularity | file | Override this table's delete granularity for this write |
```

**目的**：文档化 Spark 写入时可以通过 `.option("delete-granularity", "file")` 覆盖表的删除粒度配置。Spark 写入选项的默认值为 `file`。

## 总结

本提交是一个纯文档提交，在两个文档文件中添加了删除粒度配置选项的说明：
1. 表属性 `write.delete.granularity`（默认 `partition`）——控制删除文件的生成粒度
2. Spark 写入选项 `delete-granularity`（默认 `file`）——在单次写入时覆盖表的删除粒度

删除粒度选项允许用户在分区级别和文件级别之间选择删除文件的生成粒度，影响删除操作的性能和效率。
