# 提交 2529：Docs: Add removed_delete_files_count to rewrite_data_files output (#13865)

## 提交信息

- **序号**：2529 / 4088
- **哈希**：65301261052da712f8d3140bee12c052afb7b03d
- **短哈希**：653012610
- **日期**：2025-08-19 09:48:44 -0700
- **作者**：Manu Zhang
- **提交说明**：Docs: Add removed_delete_files_count to rewrite_data_files output (#13865)
- **PR/Issue**：#13865

## 总体目的

此次提交是针对 Spark `rewrite_data_files` 存储过程文档的补充。Iceberg 的 `rewrite_data_files` 过程在执行数据文件重写时会同时处理 delete files（删除文件，例如位置删除和等值删除），而原有文档的输出字段列表中缺少 `removed_delete_files_count` 这一指标，导致用户无法从文档中了解到该输出字段的存在及其含义。

提交通过在 `docs/docs/spark-procedures.md` 中追加一行表格记录，将 `removed_delete_files_count` 字段及其说明加入到 `rewriteDataFiles` 存储过程的输出列说明表中，使文档与实际过程返回的列保持一致，避免用户疑惑。

这类文档补全属于轻量级维护工作，但对于用户理解重写操作对删除文件的影响非常关键——特别是带有删除文件的 v2 格式表，重写后 delete files 是否被清理直接关系到存储空间与读取性能。

## 如何达成设计目的

- 直接在 `rewriteDataFiles` 存储过程输出字段表格中插入一行，字段名为 `removed_delete_files_count`，类型 `int`，描述为"Number of delete files removed by this command"。
- 保持表格原有列顺序与格式一致，仅做增量补充。

## 修改详情

### `docs/docs/spark-procedures.md` (+1)

**修改目的**：补充 `rewrite_data_files` 存储过程输出字段文档，新增 `removed_delete_files_count` 行。

**工作逻辑**：在输出字段表格中位于 `failed_data_files_count` 之后插入新行，描述重写操作过程中被移除的 delete files 数量。该字段反映了重写数据文件时同时清理掉的等值/位置删除文件数量，是衡量重写收益（不仅压缩数据文件，还回收删除文件占用空间）的重要指标。

## 总结

一次纯文档补全提交，将 `rewrite_data_files` 存储过程实际返回的 `removed_delete_files_count` 字段补入官方文档输出表，使文档与代码行为保持一致，便于用户理解重写操作对 delete files 的清理效果。
