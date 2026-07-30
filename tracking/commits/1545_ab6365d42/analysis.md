# 提交 1545：Docs: Add history to Hive's metadata tables (#11902)

## 提交信息

- **序号**：1545 / 4088
- **哈希**：ab6365d42d6c3bfa7054ecd98dde8407e5ba1201
- **短哈希**：ab6365d42
- **日期**：2025-01-03（Fri Jan 3 04:38:24 2025 +0900）
- **作者**：Shohei Okumiya <git@okumin.com>
- **提交说明**：Docs: Add history to Hive's metadata tables (#11902)
- **PR/Issue**：#11902

## 总体目的

`docs/docs/hive.md` 文档中列出了 Hive 集成可用的 Iceberg 元数据表（metadata tables），供用户通过 `SELECT * FROM catalog.db.table.history` 等方式查询表的元数据信息。该列表存在两个问题：

1. **格式错误**：`all_entries` 与 `all_files` 两个表被写在同一行（`* all_entries all_files`），在 Markdown 渲染时合并为一个列表项"all_entries all_files"，用户难以察觉这是两个独立的元数据表；
2. **遗漏 `history` 表**：Iceberg 提供 `history` 元数据表（展示表的修改历史，记录每次快照变更的操作、时间戳、快照 ID 等），在 Spark 等其它引擎的文档中已有列出，但 Hive 文档中遗漏了它。用户若通过 Hive 文档了解可用元数据表，会误以为 Hive 不支持 `history` 表。

本提交修复格式错误（把 `all_entries all_files` 拆为两行）并补全遗漏的 `history` 表，使 Hive 元数据表文档准确、完整。

## 如何达成设计目的

重写 `hive.md` 中的元数据表列表，逐行展开每个表为独立的 Markdown 列表项（`* <table_name>`），并在正确位置（`files` 与 `manifests` 之间，按字母序）插入 `history`。修正后的列表按字母序排列：`all_data_files`、`all_delete_files`、`all_entries`、`all_files`、`all_manifests`、`data_files`、`delete_files`、`entries`、`files`、`history`、`manifests`、`metadata_log_entries`、`partitions`、`refs`、`snapshots`。

## 修改详情

### `docs/docs/hive.md`（修改，+14/-12 行）

**修改目的**：修复元数据表列表格式并补全 `history` 表。

**工作逻辑**：把原来的列表（其中 `all_entries all_files` 合并为一行、缺少 `history`）替换为完整的 15 项列表，每项独占一行。具体变更：

- `* all_entries all_files` 拆分为 `* all_entries` 与 `* all_files` 两行；
- 在 `* files` 之后、`* manifests` 之前新增 `* history`；
- 其余 12 项保持不变，整体按字母序排列。

修正前（有问题的片段）：
```markdown
* all_entries all_files
* all_manifests
...
* files
* manifests
```

修正后：
```markdown
* all_entries
* all_files
* all_manifests
...
* files
* history
* manifests
```

## 小结

- **成效**：修复 Hive 元数据表文档的两个缺陷——格式错误导致 `all_entries`/`all_files` 合并显示、遗漏 `history` 表。文档现在准确列出全部 15 个可用元数据表，每项独立成行，按字母序排列。
- **影响范围**：仅 `docs/docs/hive.md` 一个文档文件，14 行变更（纯列表格式修正 + 1 项新增），无代码影响。
- **回迁到 1.4.x 的注意事项**：回迁零风险，纯文档修正。可直接 cherry-pick。需确认 1.4.x 的 Hive 文档是否与 main 有结构差异——若 1.4.x 的 `hive.md` 元数据表列表与 main 一致（即有相同的格式错误与遗漏），则直接 cherry-pick；若 1.4.x 已有不同版本的列表，按"拆分 all_entries/all_files + 补 history"原则手动修正即可。`history` 元数据表在 1.4.x 上应已支持（它是 Iceberg 核心元数据表，非新功能），文档补全只是同步缺失信息。
