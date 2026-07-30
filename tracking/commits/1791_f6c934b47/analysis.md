# 提交 1791：Docs: Fix Hive table creation syntax errors (#12394)

## 提交信息

- **序号**：1791 / 4088
- **哈希**：f6c934b479d81f16633fe8b0b6b245133f49cd94
- **短哈希**：f6c934b47
- **日期**：2025-02-26 17:22:17 +0100
- **作者**：码界探索
- **提交说明**：Docs: Fix Hive table creation syntax errors (#12394)
- **PR/Issue**：#12394

## 总体目的

Iceberg 的 Hive 文档 `docs/docs/hive.md` 中展示了从 Hive 4.0.0-alpha-1 起支持的 Iceberg 分区建表语法示例。该示例中使用了 `STORED AS ICEBERG`，但正确的 Hive 语法应为 `STORED BY ICEBERG`。

`STORED AS` 用于指定 Hive 原生的存储格式（如 TEXTFILE、ORC、PARQUET 等），而 Iceberg 作为一种由自定义 StorageHandler 管理的表，应使用 `STORED BY` 来指定 StorageHandler。因此原文档示例会误导用户写出无法执行的建表语句。本提交修正这一语法错误，确保文档示例可被 Hive 正确执行。

## 如何达成设计目的

通过修改 `docs/docs/hive.md` 中建表示例 SQL 代码块，将 `STORED AS ICEBERG` 改为 `STORED BY ICEBERG`。改动仅限代码示例中的一个关键字，不涉及任何代码逻辑变更。

## 修改详情

### `docs/docs/hive.md`（修改, ±1 lines）

**修改目的**：修正 Hive 建表示例中的存储语法关键字错误。

**工作逻辑**：在展示 Iceberg 分区建表的 SQL 代码块中，将 `CREATE TABLE x (i int, ts timestamp) PARTITIONED BY SPEC (month(ts), bucket(2, i)) STORED AS ICEBERG;` 修改为 `... STORED BY ICEBERG;`。`STORED BY` 是 Hive 中指定自定义 StorageHandler 的正确语法，Iceberg 表正是通过 IcebergStorageHandler 管理的，因此必须使用 `STORED BY`。

## 小结

- **成效**：修正了 Hive 文档中建表示例的语法错误，使示例可直接被 Hive 执行，避免用户踩坑。
- **影响范围**：仅影响文档 `docs/docs/hive.md` 的一个 SQL 示例，不涉及代码改动。
- **回迁到 1.4.x 的注意事项**：纯文档改动，无风险，无前置依赖，可直接回迁。
