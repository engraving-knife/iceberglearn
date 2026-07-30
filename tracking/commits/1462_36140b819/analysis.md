# 提交 1462：Docs: Spark procedure for stats collection (#11606)

## 提交信息

- **序号**：1462 / 4088
- **哈希**：36140b819c60743b39176e28a63ab588df445329
- **短哈希**：36140b819
- **日期**：2024-12-04（Wed Dec 4 14:34:52 2024 -0800）
- **作者**：Karuppayya <karuppayya1990@gmail.com>
- **提交说明**：Docs: Spark procedure for stats collection (#11606)
- **PR/Issue**：#11606

## 总体目的

Iceberg 提供 Spark 存储过程（procedure）`compute_table_stats`，用于为表计算 NDV（Number of Distinct Values，不同值数量）统计信息，并将结果以 Puffin 文件格式写出（参见 Iceberg 的 Puffin 规范）。这些统计信息可被查询引擎用于查询优化（如基数估计、连接顺序选择）。然而此前该存储过程在 Spark 存储过程文档（`docs/docs/spark-procedures.md`）中缺少说明，用户难以发现和使用。

本提交在 `spark-procedures.md` 文档末尾新增 `Table Statistics` 章节，完整介绍 `compute_table_stats` 存储过程的用途、参数、输出和调用示例，使用户能通过文档了解并使用该功能。

## 如何达成设计目的

在 `docs/docs/spark-procedures.md` 文件末尾追加一个完整的存储过程说明章节，遵循该文档已有的格式惯例：过程名标题、功能描述、参数表（Argument Name / Required? / Type / Description）、输出表（Output Name / Type / Description）、调用示例（SQL `CALL` 语句）。文档采用 Markdown 格式，通过 mkdocs 构建为站点页面。

## 修改详情

### `docs/docs/spark-procedures.md`

**修改目的**：新增 `compute_table_stats` 存储过程的用户文档。

**工作逻辑**：在文件末尾（最后一个已有过程说明之后）追加约 37 行内容，结构如下：

1. **章节标题**：`## Table Statistics`，作为二级章节。
2. **过程标题**：`### \`compute_table_stats\``，作为三级章节。
3. **功能描述**：说明该过程为指定表计算 NDV 统计信息（链接到 Puffin 规范 `../../format/puffin-spec.md`），默认对当前快照的所有列计算，可选指定特定快照和/或列子集。
4. **参数表**：
   | 参数名 | 必填? | 类型 | 说明 |
   |---|---|---|---|
   | `table` | 是 | string | 表名 |
   | `snapshot_id` | 否 | string | 收集统计信息的快照 ID |
   | `columns` | 否 | array<string> | 收集统计信息的列 |
5. **输出表**：
   | 输出名 | 类型 | 说明 |
   |---|---|---|
   | `statistics_file` | string | 该命令生成的统计文件路径 |
6. **调用示例**（三个）：
   - 收集表 `my_table` 最新快照的统计信息：`CALL catalog_name.system.compute_table_stats('my_table');`
   - 收集表 `my_table` 指定快照 `snap1` 的统计信息（命名参数）：`CALL catalog_name.system.compute_table_stats(table => 'my_table', snapshot_id => 'snap1');`
   - 收集表 `my_table` 指定快照 `snap1` 指定列 `col1`、`col2` 的统计信息：`CALL catalog_name.system.compute_table_stats(table => 'my_table', snapshot_id => 'snap1', columns => array('col1', 'col2'));`

注意：文件末尾仍保留 `No newline at end of file`（与原文件一致，未补换行符）。

## 小结

- **成效**：Spark 存储过程文档现包含 `compute_table_stats` 的完整说明（用途、参数、输出、示例），用户可据此调用该过程为表计算 NDV 统计信息。
- **影响范围**：仅 `docs/docs/spark-procedures.md` 一个文件，新增 37 行纯文档内容，无代码、构建或运行时逻辑变更。
- **回迁到 1.4.x 的注意事项**：纯文档改进，对运行时功能无任何影响。若 1.4.x 分支的 Spark 版本也支持 `compute_table_stats` 存储过程，则可考虑回迁此文档以保持文档完整性；但若 1.4.x 的该存储过程尚未实现或参数不同，则不应回迁（避免文档与实际功能不符）。建议先确认 1.4.x 是否已支持该存储过程再决定。整体优先级低，不影响发布产物。
