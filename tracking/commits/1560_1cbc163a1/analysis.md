# 提交 1560 1cbc163a1 分析

## 提交信息
- 哈希：1cbc163a111713798d45e6c4d5eaffa44684f18b
- 日期：2025-01-07（Tue Jan 7 20:40:27 2025 +0900）
- 作者：Yuya Ebihara <ebyhry@gmail.com>
- 消息：Doc: Add missing fields to metadata tables in Spark page (#11897)

## 总体目的

Iceberg 在 Spark 集成中提供了一组"元数据表"（metadata tables），允许用户通过 SQL 查询表的内部元数据，例如 `prod.db.table.manifests`、`prod.db.table.entries`、`prod.db.table.all_data_files`、`prod.db.table.all_delete_files`、`prod.db.table.all_manifests`、`prod.db.table.position_deletes` 等。这些元数据表的 schema 由 Iceberg 的 `MetadataTable` 实现决定，会随着 Iceberg 版本演进而扩展（例如引入 v2 格式后新增 delete files 相关字段、`readable_metrics` 字段等）。

`docs/docs/spark-queries.md` 文档以表格形式列出了每个元数据表的字段及示例值，方便用户参考。但该文档长期未与实际 schema 同步，导致多个字段缺失：
- `manifests` 与 `all_manifests` 缺少 `content`（manifest 内容类型：data 或 deletes）、`added_delete_files_count`、`existing_delete_files_count`、`deleted_delete_files_count`，以及 `all_manifests` 还缺 `reference_snapshot_id`；
- `position_deletes` 缺少 `partition` 列；
- `all_data_files` 缺少 `spec_id` 与 `readable_metrics`；
- `all_delete_files` 缺少 `partition` 列。

用户如果按文档的字段列表写 SQL（例如 `SELECT added_delete_files_count FROM ...`）会发现文档没列该字段，造成困惑；或者按文档示例解析查询结果时，列数对不上实际查询结果。本提交把文档中的字段列表与示例数据补齐到与实际 metadata table schema 一致，提升文档准确性与可用性。

## 如何达成设计目的

提交仅修改 `docs/docs/spark-queries.md` 一个文件，对五处元数据表的字段表头与示例数据行进行同步更新：在表头中新增缺失的列名，在示例数据行中补上对应列的示例值（与实际查询输出对齐）。所有更新都是文档同步，不涉及代码、构建或测试改动。

### 修改详情

#### docs/docs/spark-queries.md

共修改 5 处元数据表（每处同时更新表头与示例行）：

1. **`manifests` 表**（约第 337 行）
   - 表头新增：`content`、`added_delete_files_count`、`existing_delete_files_count`、`deleted_delete_files_count`（位于 `deleted_data_files_count` 与 `partition_summaries` 之间）。
   - 示例行补值：`content=0`（data manifest），三个 delete files 计数均为 `0`。
   - `content` 字段表示 manifest 是数据 manifest（0）还是删除文件 manifest（1），是 v2 引入 delete files 后的必要字段。

2. **`position_deletes` 表**（约第 379 行）
   - 表头新增：`partition`（位于 `row` 与 `spec_id` 之间）。
   - 示例行补值：`partition={20211001, 11}`。
   - `partition` 列让用户知道该 positional delete 属于哪个分区，便于分区级分析。

3. **`all_data_files` 表**（约第 399 行）
   - 表头新增：`spec_id`（位于 `file_format` 与 `partition` 之间）、`readable_metrics`（位于 `sort_order_id` 之后）。
   - 示例行补值：`spec_id=0`，`readable_metrics` 给出每列的 `column_size`、`value_count`、`null_value_count`、`nan_value_count`、`lower_bound`、`upper_bound` 的可读 JSON。
   - `spec_id` 标识该数据文件按哪个 partition spec 写入；`readable_metrics` 是文件级列统计的人类可读版本（与 `column_sizes`/`value_counts` 等原始 metrics 对应，但字段名更清晰）。

4. **`all_delete_files` 表**（约第 413 行）
   - 表头新增：`partition`（位于 `spec_id` 与 `record_count` 之间）。
   - 示例行补值：`partition={20210102}` 与 `{20210103}`。
   - 与 `position_deletes` 类似，补充分区信息以便分析。

5. **`all_manifests` 表**（约第 438 行）
   - 表头新增：`content`、`added_delete_files_count`、`existing_delete_files_count`、`deleted_delete_files_count`、`reference_snapshot_id`。
   - 示例行补值：`content=0`，三个 delete files 计数均为 `0`，`reference_snapshot_id=57897183625154`。
   - `reference_snapshot_id` 是 `all_manifests` 特有字段（`manifests` 只看当前快照，`all_manifests` 列出所有快照引用过的 manifest），用于标识该 manifest 被哪个快照引用。

每处修改都同时更新了表头分隔行（`| -- | -- | ...`）以匹配新增列数，保证 markdown 表格渲染正确。

## 小结

- **成效**：补齐 `spark-queries.md` 中 5 个元数据表（`manifests`、`position_deletes`、`all_data_files`、`all_delete_files`、`all_manifests`）文档缺失的字段（`content`、`partition`、`spec_id`、`readable_metrics`、三个 delete files 计数、`reference_snapshot_id`），使文档与实际 metadata table schema 一致。
- **影响范围**：仅 `docs/docs/spark-queries.md`，18 行替换（增删各 18 行），无代码逻辑变更。
- **回迁到 1.4.x 的注意事项**：纯文档修订，对运行时无影响。1.4.x 维护分支如要同步文档质量修正，可回迁；但需注意 1.4.x 实际 metadata table schema 是否已包含这些字段——若 1.4.x 版本较老尚不支持某些字段（如 `readable_metrics`、delete files 计数），则不应回迁对应部分，以免文档与代码不符。
