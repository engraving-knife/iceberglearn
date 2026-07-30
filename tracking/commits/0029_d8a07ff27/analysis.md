# 提交 0029：Docs: Document all metadata tables (#8709)

## 提交信息

- **序号**：0029 / 4088
- **哈希**：d8a07ff276dc0efbfbbe3df3579c638c863b2686
- **短哈希**：d8a07ff27
- **日期**：2023-10-10
- **作者**：Naveen Kumar
- **提交说明**：Docs: Document all metadata tables (#8709)
- **PR/Issue**：#8709

## 总体目的

这个提交补齐了 Iceberg Spark 查询文档中遗漏的若干元数据表（metadata table）的说明。Iceberg 通过元数据表机制，允许用户像查询普通表一样用 `SELECT * FROM prod.db.table.<metadata_table_name>` 查询表的内部元数据，比如快照、文件、manifest、分区、entries 等。这些元数据表对应 [MetadataTableType.java](file:///Users/fengxiaohang/trae/iceberglearn/core/src/main/java/org/apache/iceberg/MetadataTableType.java) 中定义的 16 个枚举值：`ENTRIES`、`FILES`、`DATA_FILES`、`DELETE_FILES`、`HISTORY`、`METADATA_LOG_ENTRIES`、`SNAPSHOTS`、`REFS`、`MANIFESTS`、`PARTITIONS`、`ALL_DATA_FILES`、`ALL_DELETE_FILES`、`ALL_FILES`、`ALL_MANIFESTS`、`ALL_ENTRIES`、`POSITION_DELETES`。

但在本次提交之前，[docs/spark-queries.md](file:///Users/fengxiaohang/trae/iceberglearn/docs/spark-queries.md) 中只文档化了 `snapshots`、`files`、`manifests`、`partitions`、`history`、`data_files`、`delete_files`、`all_files`、`all_data_files`、`all_manifests` 等元数据表，却遗漏了 `entries`、`position_deletes`、`all_delete_files`、`all_entries` 这 4 张表。值得注意的是：在 `Files` 节末尾的注释里（spark-queries.md 第 295 行附近），文档已经提到过 `prod.db.table.all_delete_files` 的存在，但并没有专门的小节给出 schema 与示例；`entries`、`position_deletes`、`all_entries` 则完全没有任何说明。这种"代码已实现、文档未补齐"的状态会让用户难以发现和使用这些诊断能力。

本次提交就是在 spark-queries.md 中新增 4 个小节，分别文档化这 4 张元数据表：在每个小节里给出查询 SQL 示例和示例输出表格（含字段 schema）。这是纯文档改动，不修改任何 Java 代码，共新增 49 行，不删除任何内容。

## 如何达成设计目的

整体思路是按现有文档的结构风格，为每张遗漏的元数据表新增一个 `### <表名>` 或 `#### <表名>` 小节，包含简短描述、`SELECT` 示例与字段表。具体安排：

1. `entries` 和 `position_deletes` 放在"当前快照范围"的元数据表区（与 `files`、`partitions` 并列）。
2. `all_delete_files` 和 `all_entries` 放在"全部快照范围"（`All Metadata Tables` 节）下，与已有的 `all_data_files`、`all_manifests` 并列。

每个小节都按统一格式：一句话说明表用途、SQL 示例代码块、字段表格示例。

## 修改详情

### [docs/spark-queries.md](file:///Users/fengxiaohang/trae/iceberglearn/docs/spark-queries.md)

本提交只修改这一个文件，新增 4 个小节。

#### 新增 `### Entries` 小节（在 `### Files` 之前）

**修改目的**：文档化 `prod.db.table.entries` 元数据表，对应 `MetadataTableType.ENTRIES`，由 [BaseEntriesTable](file:///Users/fengxiaohang/trae/iceberglearn/core/src/main/java/org/apache/iceberg/BaseEntriesTable.java) 的当前快照版本实现。

**工作逻辑**：新小节说明该表"show all the table's current manifest entries for both data and delete files"——即展示当前快照中所有 manifest 条目，同时包含数据文件和删除文件。SQL 示例为 `SELECT * FROM prod.db.table.entries;`。输出表 schema 包含 6 列：`status`（条目状态：added/existing/deleted）、`snapshot_id`、`sequence_number`、`file_sequence_number`、`data_file`（嵌套结构，含 content、file_path、file_format、spec_id、record_count、file_size_in_bytes、列级统计等）、`readable_metrics`（人类可读的列级指标）。示例行展示了 status=2（ENTRY_ADDED）、content=0（数据文件）的条目，以及 `readable_metrics` 中 c1 列的统计信息。位置上放在 `### Files` 之前符合逻辑顺序——entries 是 manifest 中的原始条目，files 是从中提取出的文件视图。

#### 新增 `### Positional Delete Files` 小节（在 `### Partitions` 之后、`### All Metadata Tables` 之前）

**修改目的**：文档化 `prod.db.table.position_deletes` 元数据表，对应 `MetadataTableType.POSITION_DELETES`，由 [PositionDeletesTable](file:///Users/fengxiaohang/trae/iceberglearn/core/src/main/java/org/apache/iceberg/PositionDeletesTable.java) 实现。

**工作逻辑**：新小节说明该表"show all positional delete files from the current snapshot of table"——即把当前快照中的位置删除文件（position delete files）展开为可查询的行数据。与 `delete_files` 元数据表（展示删除文件本身的元数据）不同，`position_deletes` 实际读取删除文件内容并把每一条删除记录（被删除行的位置）作为一行输出。SQL 示例为 `SELECT * from prod.db.table.position_deletes;`。输出表 schema 包含 5 列：`file_path`（被删除数据文件路径）、`pos`（被删除行在该文件中的位置）、`row`（行字段，对于 position delete 通常为 null）、`spec_id`（分区规格 ID）、`delete_file_path`（删除文件路径）。该表对运维场景非常有用：可以定位到具体哪一行被删除、来自哪个数据文件。

#### 新增 `#### All Delete Files` 小节（在 `#### All Data Files` 之后、`#### All Manifests` 之前）

**修改目的**：文档化 `prod.db.table.all_delete_files` 元数据表，对应 `MetadataTableType.ALL_DELETE_FILES`。

**工作逻辑**：新小节说明该表"show the table's delete files and each file's metadata from all the snapshots"——即跨所有快照汇总删除文件及其元数据。与 `all_data_files` 对称，`all_delete_files` 把所有快照中出现过的删除文件（position deletes 与 equality deletes）都列出来，可能因同一文件被多个快照引用而产生重复行。SQL 示例为 `SELECT * FROM prod.db.table.all_delete_files;`。输出表 schema 与 `files` 表一致，含 17 列：`content`（1=Position Deletes，2=Equality Deletes）、`file_path`、`file_format`、`spec_id`、`record_count`、`file_size_in_bytes`、各种列级统计（column_sizes/value_counts/null_value_counts/nan_value_counts/lower_bounds/upper_bounds）、`key_metadata`、`split_offsets`、`equality_ids`、`sort_order_id`、`readable_metrics`。示例两行分别展示了 content=1（位置删除）与 content=2（等值删除）的样例。这是补齐 `Files` 节末注释中提到的 `all_delete_files` 引用——之前文档提到却没专门说明。

#### 新增 `#### All Entries` 小节（在 `#### All Delete Files` 之后、`#### All Manifests` 之前）

**修改目的**：文档化 `prod.db.table.all_entries` 元数据表，对应 `MetadataTableType.ALL_ENTRIES`，由 [AllEntriesTable](file:///Users/fengxiaohang/trae/iceberglearn/core/src/main/java/org/apache/iceberg/AllEntriesTable.java) 实现。

**工作逻辑**：新小节说明该表"show the table's manifest entries from all the snapshots for both data and delete files"——即跨所有快照汇总所有 manifest 条目（含数据文件与删除文件）。这是 `entries` 的"全部快照"版本，与 `all_data_files`/`all_delete_files` 是文件级聚合不同，`all_entries` 是 manifest 条目级聚合，保留了 `status` 字段表示条目在对应快照中是 added/existing/deleted。SQL 示例为 `SELECT * FROM prod.db.table.all_entries;`。输出表 schema 与 `entries` 一致：`status`、`snapshot_id`、`sequence_number`、`file_sequence_number`、`data_file`、`readable_metrics`。该表便于审计场景下追溯某文件在历史快照中的状态变化。

## 小结

通过在 spark-queries.md 中新增 `entries`、`position_deletes`、`all_delete_files`、`all_entries` 四个元数据表的文档小节，补齐了元数据表体系在用户文档中的最后一块拼图，使用户能完整了解并使用 Iceberg 提供的全部 16 类元数据诊断视图。
