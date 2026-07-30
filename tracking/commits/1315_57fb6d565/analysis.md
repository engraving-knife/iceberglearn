# 提交 1315：Doc: Update rewrite data files spark procedure (#11396)

## 提交信息

- **序号**：1315 / 4088
- **哈希**：57fb6d56588ea91e995663b1a6f8bfee34060fa8
- **短哈希**：57fb6d565
- **日期**：2024-10-31（Thu Oct 31 13:31:14 2024 -0700）
- **作者**：Hongyue/Steve Zhang <steveiszhy@gmail.com>
- **提交说明**：Doc: Update rewrite data files spark procedure (#11396)
- **PR/Issue**：#11396

## 总体目的

`docs/docs/spark-procedures.md` 是 Iceberg 文档中介绍 Spark 系统存储过程（`system.rewrite_data_files` 等）的核心页面。本次更新主要补齐两类信息：

1. **补齐缺失的参数文档**：`rewrite_data_files` 过程实际支持但文档表中未列出的几个选项，包括 `partial-progress.max-failed-commits`、`output-spec-id`、`remove-dangling-deletes`。其中 `remove-dangling-deletes` 是较新的能力——重写数据文件时顺带清理"悬空"删除文件（dangling deletes），即不再应用于任何活动数据文件的删除文件。
2. **更新示例**：把 bin-pack 策略示例从原先仅展示 `min-input-files=2` 改为同时演示 `remove-dangling-deletes=true`，让读者更直观看到该新选项的用法；同时修正原示例的英文措辞（"2 or more files need to be rewritten" → "at least two files need rewriting, and then remove any dangling delete files"），并修复示例 SQL 中 `map('min-input-files','2')` 参数空格不一致问题。

此外，新增一段 `!!! info` 提示块，说明 `remove-dangling-deletes` 的限制：仅基于数据序列号判定悬空，无法清理"全局等值删除"或"条件不匹配的等值删除"，也无法清理位置删除文件中"位置不再匹配任何活动数据文件"的删除记录。这有助于用户理解该选项的边界。

## 如何达成设计目的

1. 在 `rewrite_data_files` 选项表中按合适位置插入三行新参数说明：
   - `partial-progress.max-failed-commits`（默认值与 `partial-progress.max-commits` 相同）：partial progress 启用时允许的失败提交数上限；
   - `output-spec-id`（默认为当前分区 spec id）：指定输出分区 spec，重写时按其重新组织数据；
   - `remove-dangling-deletes`（默认 false）：重写后移除悬空删除文件，会额外产生一个 commit。
2. 在选项表后追加 `!!! info` admonition（MkDocs Material 风格提示块），说明该选项的边界。
3. 修改"bin-pack 示例"段落的英文说明和 SQL：把 `map('min-input-files','2')` 改为 `map('min-input-files', '2', 'remove-dangling-deletes', 'true')`，并把示例描述改写为同时体现 bin-pack 与悬空删除清理两个动作。

## 修改详情

### `docs/docs/spark-procedures.md`

**修改目的**：补齐 `rewrite_data_files` 过程的选项文档与示例。

**工作逻辑**：

- 在选项表 `partial-progress.max-commits` 行之后插入新行：
  ```
  | `partial-progress.max-failed-commits` | value of `partital-progress.max-commits` | Maximum amount of failed commits allowed before job failure, if partial progress is enabled |
  ```
  注意默认值列中保留了原文里的拼写错误 "partital"（应为 "partial"），这是引用了同表中已有键的拼写——文档中该键名拼写就是错的，这里沿用以便读者匹配。
- 在 `delete-file-threshold` 行之后追加两行：
  ```
  | `output-spec-id` | current partition spec id | Identifier of the output partition spec. Data will be reorganized during the rewrite to align with the output partitioning. |
  | `remove-dangling-deletes` | false | Remove dangling position and equality deletes after rewriting. A delete file is considered dangling if it does not apply to any live data files. Enabling this will generate an additional commit for the removal. |
  ```
- 在选项表后插入 admonition：
  ```
  !!! info
      Dangling delete files are removed based solely on data sequence numbers. This action does not apply to global
      equality deletes or invalid equality deletes if their delete conditions do not match any data files,
      nor to position delete files containing position deletes no longer matching any live data files.
  ```
  含义：移除悬空删除文件仅依据数据序列号判定；它无法清理全局等值删除、条件不匹配的等值删除，以及位置删除文件里位置不再匹配任何活动数据文件的部分。
- 修改 bin-pack 示例描述：
  - 旧：`Rewrite the data files in table db.sample using bin-pack strategy in any partition where more than 2 or more files need to be rewritten.`
  - 新：`Rewrite the data files in table db.sample using bin-pack strategy in any partition where at least two files need rewriting, and then remove any dangling delete files.`
  - 同时把 SQL 改为：
    ```sql
    CALL catalog_name.system.rewrite_data_files(table => 'db.sample', options => map('min-input-files', '2', 'remove-dangling-deletes', 'true'));
    ```
    （原 SQL 的 `map('min-input-files','2')` 在 `'2'` 前后没有空格，新版按 Spark 风格补齐空格，且新增了第二个键值对。）

## 小结

- **成效**：`rewrite_data_files` 文档现完整覆盖 `partial-progress.max-failed-commits`、`output-spec-id`、`remove-dangling-deletes` 三个之前未文档化的选项；新增的 `!!! info` 块明确了 `remove-dangling-deletes` 的能力边界；示例同步更新到推荐用法。
- **影响范围**：1 个文件、共 11 行变更（9 增 2 删），纯文档修改，无代码改动。
- **回迁到 1.4.x 的注意事项**：
  - 纯文档变更，回迁零运行时风险。
  - 但需先确认 1.4.x 的 Spark 过程实现是否已支持 `remove-dangling-deletes`、`output-spec-id`、`partial-progress.max-failed-commits` 这三个选项。如果实现尚未支持某选项，回迁文档反而会误导用户。
  - 建议回迁前比对 1.4.x 中 `SparkActions` / `RewriteDataFilesSparkAction` 实际接受的 options 集合，仅文档化已支持的选项；若 `remove-dangling-deletes` 在 1.4.x 尚未实现，则该选项及其 info 提示块应一并跳过。
  - 示例 SQL 的更新可单独回迁，但前提是 1.4.x 已支持 `remove-dangling-deletes`，否则示例会指向不存在的选项。
