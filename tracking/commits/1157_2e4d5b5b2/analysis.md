# 提交 1157：Docs: Fix missing options for remove_orphan_files procedure (#11080)

## 提交信息

- **序号**：1157 / 4088
- **哈希**：2e4d5b5b21c8872e347d7f86bc6c72c3484eb7fc
- **短哈希**：2e4d5b5b2
- **日期**：2024-09-14（Sat Sep 14 12:56:21 2024 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Docs: Fix missing options for remove_orphan_files procedure (#11080)
- **PR/Issue**：#11080

## 总体目的

Iceberg 的 Spark procedure `system.remove_orphan_files` 用于清理表所在目录下、但未被任何 metadata 引用的"孤儿文件"。该 procedure 早已支持多个新选项（如 `file_list_view` 跳过目录列举、`equal_schemes` / `equal_authorities` 处理 URI 前缀不一致、`prefix_mismatch_mode` 控制前缀不匹配时的行为），但官方文档 `docs/docs/spark-procedures.md` 中只列出了 `location` / `dry_run` / `max_concurrent_deletes` 三个老选项，没有同步更新。这导致用户无法从文档了解到这些能力，只能看源码或 PR。

本提交纯文档更新，把上述 4 个新选项补入"Arguments"表格，并补充对应的使用示例（含 Java 构造 `file_list_view` 的代码片段与多种 SQL 调用方式）。

## 如何达成设计目的

直接编辑 `docs/docs/spark-procedures.md`：
1. 在 `remove_orphan_files` 章节的"Arguments"表格末尾追加 4 行，分别描述 `file_list_view` / `equal_schemes` / `equal_authorities` / `prefix_mismatch_mode` 的类型与语义。
2. 在"Usage"小节追加 5 段示例：使用 `file_list_view`（含 Java DataFrame 构造代码）、`prefix_mismatch_mode=IGNORE`、`prefix_mismatch_mode=DELETE`、`equal_schemes=map('file','file1')`、`equal_authorities=map('ns1','ns2')`。

无任何代码改动，纯 markdown 文档修订。

## 修改详情

### `docs/docs/spark-procedures.md`

**修改目的**：补全 `remove_orphan_files` procedure 的参数与用法说明。

**工作逻辑**：
- 在 `remove_orphan_files` 章节的 Arguments 表格中，原表格只有 3 行（`location` / `dry_run` / `max_concurrent_deletes`），新增 4 行：
  - `file_list_view` (string)：用作"文件清单来源"的 Spark 视图名，跳过目录列举，由用户提前构造好 DataFrame 注册为临时视图。适用于对象存储列举性能差、或文件清单需要从外部系统获取的场景。
  - `equal_schemes` (map<string,string>)：把"等价的文件系统 scheme"映射到统一 scheme。key 是逗号分隔的 scheme 列表，value 是目标 scheme。默认 `map('s3a,s3n','s3')`，让 s3a/s3n 路径与 s3 路径视为等价。
  - `equal_authorities` (map<string,string>)：与 `equal_schemes` 类似，但作用于 URI 的 authority 部分（如 `ns1` / `ns2` 这样的 nameservice）。
  - `prefix_mismatch_mode` (string)：当文件路径前缀（scheme/authority）与 metadata 中记录的不一致时的行为，三选一：
    - `ERROR`（默认）：抛异常。
    - `IGNORE`：跳过该文件不做处理。
    - `DELETE`：仍然删除该文件。
- 在 Usage 小节追加示例：
  - 先给出 Java 代码片段，展示如何用 `spark.createDataFrame(allFiles, FilePathLastModifiedRecord.class)` 构造含 `file_path` / `last_modified` 两列的 DataFrame，注册为临时视图 `files_view`，再通过 `CALL catalog_name.system.remove_orphan_files(table => 'db.sample', file_list_view => 'files_view')` 调用 procedure。
  - 4 段 SQL 示例分别展示 `prefix_mismatch_mode => 'IGNORE'`、`prefix_mismatch_mode => 'DELETE'`、`equal_schemes => map('file', 'file1')`、`equal_authorities => map('ns1', 'ns2')` 的用法。

## 小结

- **成效**：用户现在可以从官方文档了解到 `remove_orphan_files` 的全部 7 个参数，特别是 `file_list_view`（外部文件清单）与 `prefix_mismatch_mode` / `equal_schemes` / `equal_authorities`（前缀不一致处理）这些已有但未文档化的能力，减少误用与 issue 提问。
- **影响范围**：仅 `docs/docs/spark-procedures.md` 一个文件，新增约 38 行（4 行表格 + 34 行示例），无任何代码、构建或测试改动。
- **回迁到 1.4.x 的注意事项**：
  1. 这是纯文档更新，描述的是 procedure 早已支持的能力。1.4.x 分支若 `remove_orphan_files` 已支持这 4 个参数，则文档可一并回迁保持与 main 一致。
  2. 若 1.4.x 的 `remove_orphan_files` 实现尚未合入 `file_list_view` / `prefix_mismatch_mode` 等能力（这些是在更早的 PR 中加入 main 的），则回迁文档反而会误导用户。需要先确认 1.4.x 的实现已具备这些选项再回迁文档。
  3. 文档不影响运行时行为，无兼容性风险。
