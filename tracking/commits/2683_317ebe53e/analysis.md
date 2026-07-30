# 提交 2683：Docs: Enhance RewriteTablePath procedure documentation with parameter details. (#14181)

## 提交信息

- **序号**：2683 / 4088
- **哈希**：317ebe53ea295e92e440913c6369189a664691db
- **短哈希**：317ebe53e
- **日期**：2025-09-25 08:26:40 +0200
- **作者**：slfan1989
- **提交说明**：Docs: Enhance RewriteTablePath procedure documentation with parameter details. (#14181)
- **PR/Issue**：#14181

## 总体目的

本提交是提交 2679（PR #13837，为 RewriteTablePath 新增 `create_file_list` 选项和两个结果计数字段）的配套文档更新。在 2679 中新增了 `create_file_list` 参数和 `rewritten_manifest_file_paths_count`、`rewritten_delete_file_paths_count` 两个输出列，但 Spark 存储过程文档（`spark-procedures.md`）中 RewriteTablePath 的参数表和输出表未同步更新。本提交补齐这些文档，使用户能从官方文档了解到新参数和新输出列的存在与用途。

## 如何达成设计目的

在 `docs/docs/spark-procedures.md` 的 RewriteTablePath 存储过程文档中，向参数表新增 `create_file_list` 行，向输出表新增两个计数列行。

## 修改详情

### `docs/docs/spark-procedures.md` (+3/-1 lines)

**修改目的**：同步 RewriteTablePath 文档与代码变更。

**工作逻辑**：
- 在参数表中新增 `create_file_list` 行：类型为 boolean，默认值为 true，描述为"是否生成包含重写元数据路径的文件列表"。
- 在输出表中新增 `rewritten_manifest_file_paths_count` 行（类型 int，描述"重写了路径的 manifest 文件数量"）和 `rewritten_delete_file_paths_count` 行（类型 int，描述"重写了路径的 delete 文件数量"）。
- 原参数表中 `staging_location` 行后面的空行被替换为新行（格式微调）。

## 总结

这是一次纯文档提交，为提交 2679 新增的 `create_file_list` 参数和两个结果计数字段补充了 Spark 存储过程文档。文档与代码保持同步是 Iceberg 文档维护的常规工作，确保用户能从文档获取完整的参数和输出信息。改动仅 3 行新增、1 行删除，风险极低。
