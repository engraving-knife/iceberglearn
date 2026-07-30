# 提交 0027：Docs: Fix missing semicolons in SQL snippets. (#8748)

## 提交信息

- **序号**：0027 / 4088
- **哈希**：982242ba8e9bbd34686e429ba893f7bad379799e
- **短哈希**：982242ba8
- **日期**：2023-10-10
- **作者**：Priyansh Agrawal
- **提交说明**：Docs: Fix missing semicolons in SQL snippets. (#8748)
- **PR/Issue**：#8748

## 总体目的

这个提交是一次系统性的文档质量改进，目标是消除 Iceberg 官方文档中所有 SQL 代码片段里缺失的结尾分号。提交说明展示了 PR 的演进过程：作者最初只是想为 `spark-getting-started.md` 中 `CREATE TABLE ...` 语句补一个分号，但随后决定把整个 `spark-getting-started.md` 中的所有缺失分号都补齐，最后进一步扩展到整个 `docs/` 目录下所有文档。

虽然 SQL 在 Spark/Flink 等引擎中作为单语句执行时末尾分号并非必需，但作为官方文档，SQL 示例的语法严谨性会直接影响用户复用粘贴时的体验：很多用户会把示例直接拷贝到 SQL CLI 中执行，或在脚本里组合多段示例，缺失分号会导致语句边界不清晰甚至解析错误。同时，统一的分号风格也让文档显得更专业、更一致。

本提交共修改 10 个文档文件，新增 101 行、删除 101 行——即纯粹的字符级修改，没有语义改动，是典型的"擦除文档技术债"型提交。

## 如何达成设计目的

整体思路简单直接：遍历 `docs/` 目录下所有 Markdown 文档中的 SQL 代码块（` ```sql ` 围栏），为每条 SQL 语句末尾补上 `;`，同时顺手修了几处微小的措辞与冗余。修改覆盖了 Spark DDL、Spark Procedures、Flink DDL/Queries、分区、分支标签、Dell 集成、Spark 配置/查询等多类文档，确保整个文档库示例风格一致。

## 修改详情

### [docs/spark-ddl.md](file:///Users/fengxiaohang/trae/iceberglearn/docs/spark-ddl.md)

**修改目的**：为 Spark DDL 文档中所有 SQL 语句补齐缺失的分号。

**工作逻辑**：本文件是改动最密集的之一，覆盖 `CREATE TABLE`、`DROP TABLE`、`DROP TABLE PURGE`、`ALTER TABLE ... RENAME TO`、`SET TBLPROPERTIES`、`UNSET TBLPROPERTIES`、`ADD COLUMN`（含嵌套 struct/array/map 字段添加、`FIRST`/`AFTER` 位置子句）、`RENAME COLUMN`、`ALTER COLUMN`（类型变更、注释、`FIRST`/`AFTER`、`DROP NOT NULL`）、`DROP COLUMN`、`ADD PARTITION FIELD`（含 transform 与自定义命名）、`DROP PARTITION FIELD`、`REPLACE PARTITION FIELD` 等几乎所有 Spark DDL 命令。每一处都是在语句末尾追加 `;`，例如 `USING iceberg` → `USING iceberg;`、`PARTITIONED BY (category)` → `PARTITIONED BY (category);`、`ALTER TABLE prod.db.sample RENAME COLUMN data TO payload` → `... TO payload;` 等。

### [docs/spark-procedures.md](file:///Users/fengxiaohang/trae/iceberglearn/docs/spark-procedures.md)

**修改目的**：为 Spark 存储过程文档中所有 `CALL ...` 与相关 SQL 示例补齐分号。

**工作逻辑**：本文件改动量最大（80 行），覆盖 `rollback_to_snapshot`、`rollback_to_timestamp`、`set_current_snapshot`、`cherrypick_snapshot`、`publish_changes`、`fast_forward`、`expire_snapshots`、`remove_orphan_files`、`rewrite_data_files`（含 bin-pack、sort、zOrder、min-input-files、filter 等多种用法）、`rewrite_manifests`、`rewrite_position_delete_files`、`snapshot`、`migrate`、`add_files`、`register_table`、`ancestors_of`、`create_changelog_view` 等 procedure 的调用示例。每一处 `CALL ...` 后均加上 `;`，对多行 `CALL ... ( ... )` 形式的语句，分号补在结束的 `)` 后，例如：
```sql
CALL spark_catalog.system.add_files(
  table => 'db.tbl',
  source_table => '`parquet`.`path/to/table`'
);
```
此外，对 `SELECT * FROM tbl_changes`、`SELECT * FROM spark_catalog.db.tbl.changes` 这类查询语句也补上了分号。

### [docs/branching-and-tagging.md](file:///Users/fengxiaohang/trae/iceberglearn/docs/branching-and-tagging.md)

**修改目的**：为分支与标签文档中的 `ALTER TABLE ... CREATE TAG/BRANCH`、`SET TBLPROPERTIES`、`INSERT INTO`、`CALL fast_forward` 等示例补分号。

**工作逻辑**：本文件展示了用 tag/branch 做快照保留策略与 WAP（Write-Audit-Publish）审计分支的完整流程。修改覆盖每周/每月/每年保留 tag、临时 test-branch、WAP 配置中的 `'write.wap.enabled'='true'`、`CREATE BRANCH audit-branch`、`SET spark.wap.branch = audit-branch` 后的 `INSERT INTO prod.db.table VALUES (3, 'c')`、以及最后的 `fast_forward` 调用。所有示例统一补分号，使整套审计分支流程示例可以直接复制到 CLI 中执行。

### [docs/spark-getting-started.md](file:///Users/fengxiaohang/trae/iceberglearn/docs/spark-getting-started.md)

**修改目的**：为 Spark 入门文档补分号并修一处微小措辞。

**工作逻辑**：这是 PR 的最初起点。补分号处覆盖 `CREATE TABLE local.db.table (id bigint, data string) USING iceberg`、`MERGE INTO ... WHEN NOT MATCHED THEN INSERT *`、`SELECT count(1) ... GROUP BY data`、`SELECT * FROM local.db.table.snapshots` 等示例。此外还有两处非分号修改：
- 行尾多余空格清理：`Spark is currently the most feature-rich compute engine for Iceberg operations.` 去掉了句末空格；
- 措辞修正：`use the an Iceberg table's name` → `use the Iceberg table's name`（去除冗余的 "an"）；`use the an Iceberg table name` → 同前；
- `inspect tables` 段落里 `all of the snapshots in a table` → `all snapshots in a table`，更简洁。

### [docs/partitioning.md](file:///Users/fengxiaohang/trae/iceberglearn/docs/partitioning.md)

**修改目的**：为分区文档中的 `SELECT ... WHERE` 与 `INSERT INTO ... SELECT` 示例补分号。

**工作逻辑**：本文件用 `logs` 表例子讲解分区裁剪。修改覆盖三处示例：`SELECT level, message FROM logs WHERE event_time BETWEEN ...`、`INSERT INTO logs PARTITION (event_date) SELECT ... FROM unstructured_log_source`、`SELECT level, count(1) ... WHERE event_time BETWEEN ... AND event_date = '2018-12-01'`，全部补上末尾分号，便于用户复制到 Hive/Spark CLI 执行。

### [docs/spark-configuration.md](file:///Users/fengxiaohang/trae/iceberglearn/docs/spark-configuration.md)

**修改目的**：为 Spark 配置文档中的 `SELECT` 示例补分号。

**工作逻辑**：本文件演示 catalog 命名空间用法。两处修改：
- `SELECT * FROM hive_prod.db.table -- load db.table from catalog hive_prod` → 末尾加分号；
- `SELECT * FROM table -- load db.table from catalog hive_prod` → 末尾加分号。
分号加在 SQL 语句的末尾、行内注释之前，保持注释可读性。

### [docs/flink-ddl.md](file:///Users/fengxiaohang/trae/iceberglearn/docs/flink-ddl.md)

**修改目的**：为 Flink DDL 文档中的 `ALTER TABLE ... SET` 示例补分号。

**工作逻辑**：本文件改动只有一行：`ALTER TABLE \`hive_catalog\`.\`default\`.\`sample\` SET ('write.format.default'='avro')` → 末尾补 `;`。Flink DDL 文档示例量较少，仅此处遗漏分号。

### [docs/flink-queries.md](file:///Users/fengxiaohang/trae/iceberglearn/docs/flink-queries.md)

**修改目的**：为 Flink 查询文档中的多行 `SELECT ... JOIN ... ORDER BY` 示例补分号。

**工作逻辑**：本文件演示如何联查 `table$history` 与 `table$snapshots`。修改仅一处，将 `order by made_current_at` 改为 `order by made_current_at;`。

### [docs/spark-queries.md](file:///Users/fengxiaohang/trae/iceberglearn/docs/spark-queries.md)

**修改目的**：为 Spark 查询文档中与 `flink-queries.md` 对应的多行联查示例补分号。

**工作逻辑**：与 `flink-queries.md` 对称，本文件示例 `select ... from prod.db.table.history h join prod.db.table.snapshots s on h.snapshot_id = s.snapshot_id order by made_current_at` 末尾补 `;`。

### [docs/dell.md](file:///Users/fengxiaohang/trae/iceberglearn/docs/dell.md)

**修改目的**：为 Dell ECS Catalog 配置示例补分号。

**工作逻辑**：本文件演示创建 Dell ECS Catalog 的 `CREATE CATALOG my_catalog WITH ( ... )` 语句。原本末尾是 `'ecs.s3.secret-access-key' = '<Your-ecs-s3-secret-access-key>')`，修改后变为 `...')';`，即将分号补在闭括号之后。

## 小结

通过对 `docs/` 下 10 个文档文件的 SQL 示例统一补齐缺失分号（并附带极少量措辞修正），消除了官方文档中长期存在的不一致问题，提升了用户复制粘贴执行示例的体验与文档的整体专业度。
