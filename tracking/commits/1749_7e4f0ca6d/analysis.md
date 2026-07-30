# 提交 1749：Docs: Add rewrite_table_path Spark Procedure (#12115)

## 提交信息

- **序号**：1749 / 4088
- **哈希**：7e4f0ca6dda72f7401e949f20e4177702d31cd3d
- **短哈希**：7e4f0ca6d
- **日期**：2025-02-18 23:31:59 -0800
- **作者**：Hongyue/Steve Zhang
- **提交说明**：Docs: Add rewrite_table_path Spark Procedure (#12115)
- **PR/Issue**：#12115

## 总体目的

本提交旨在为 Iceberg 的 Spark 存储过程 `rewrite_table_path` 补充官方文档。`rewrite_table_path` 是一个用于在迁移/复制 Iceberg 表到新存储位置时进行路径重写的过程，它能够将表元数据文件中的绝对路径前缀替换为目标前缀，从而为表的全量或增量复制做好准备。

在此提交之前，该存储过程虽然在代码层面已实现，但在 Spark 存储过程官方文档中缺少对应说明，导致用户难以发现和使用此功能。本次提交通过在 `spark-procedures.md` 中新增 "Table Replication" 章节来补齐这一文档缺口，详细说明了过程的用途、参数、输出、操作模式以及使用示例。

## 如何达成设计目的

提交仅修改了文档文件，通过 Markdown 格式在 `spark-procedures.md` 末尾追加了一个新的 "Table Replication" 章节。该章节按照与其他存储过程文档一致的写作风格，包含了过程描述、参数表、操作模式说明、输出说明、文件列表格式说明以及使用示例，并辅以 `!!! info` 和 `!!! warning` 提示框强调关键注意事项。

## 修改详情

### `docs/docs/spark-procedures.md`（修改, +91/-1 lines）

**修改目的**：为 `rewrite_table_path` 存储过程补充官方文档。

**工作逻辑**：在文件末尾新增 "Table Replication" 章节，主要包含以下内容：

1. **过程概述**：说明 `rewrite_table_path` 过程用于将 Iceberg 表的元数据文件中所有绝对路径的源前缀替换为目标前缀，为将表复制到新位置做准备。通过 `!!! info` 提示框强调该过程仅暂存重写后的元数据文件并准备待复制文件清单，实际的文件复制不在本过程的范围内。

2. **参数表**：列出 6 个参数，其中 `table`、`source_prefix`、`target_prefix` 为必填，`start_version`、`end_version`、`staging_location` 为可选。可选参数提供了默认值（如表的 metadata log 中的第一个/最后一个 metadata.json、表 metadata 目录下的新子目录）。

3. **操作模式**：描述了两种模式——全量重写（Full Rewrite，默认模式，重写所有可达的 metadata 文件）和增量重写（Incremental Rewrite，通过 `start_version` 和 `end_version` 限定范围，仅重写该范围内新增的 metadata 文件）。

4. **输出**：过程返回 `latest_version`（重写后的最新 metadata 文件名）和 `file_list_location`（包含源路径到目标路径映射的 CSV 文件路径）。并对 CSV 文件格式进行了说明，每行包含 source path 和 target path，文件可能是原始数据文件路径或暂存路径（对于被重写的文件）。

5. **示例**：提供了两个 SQL 调用示例，分别演示全量重写（HDFS 到 S3）和增量重写（指定版本范围和暂存位置）。

6. **后续步骤**：说明重写完成后可使用第三方工具（如 DistCp）复制文件，并使用 `register_table` 过程在目标位置注册表。

7. **限制警告**：通过 `!!! warning` 提示框说明当前不支持包含分区统计文件（partition statistics files）的表的路径重写。

## 小结

- **成效**：补齐了 `rewrite_table_path` Spark 存储过程的官方文档，使用户能够了解和使用该表复制/迁移功能。
- **影响范围**：仅涉及文档，不影响代码逻辑。影响 Spark 存储过程文档的使用者。
- **回迁到 1.4.x 的注意事项**：纯文档变更，无代码依赖，可安全回迁。但需确认 1.4.x 分支中 `rewrite_table_path` 过程是否已实现，若未实现则文档与代码不匹配。建议先确认 1.4.x 分支中该过程的实现状态再决定是否回迁文档。
