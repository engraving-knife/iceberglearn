# 提交 3056：Docs: Fix MERGE INTO example in Getting Started (#14943)

## 提交信息

- **序号**：3056 / 4088
- **哈希**：9c3bed6a651f20fc06b1a4c329e40910bd6eb36a
- **短哈希**：9c3bed6a6
- **日期**：2025-12-30
- **作者**：Varun Lakhyani
- **提交说明**：Docs: Fix MERGE INTO example in Getting Started (#14943)
- **PR/Issue**：#14943

## 总体目的

本提交修复了 Iceberg "Getting Started" 文档中 Spark SQL 示例的可运行性问题。在修改前，文档里的 `MERGE INTO` 示例存在两处与上下文不一致的地方，导致用户如果按文档原样执行 SQL 会直接报错，无法完成入门体验。

第一处问题是表名不匹配：示例中 `MERGE INTO` 的目标表写成了 `local.db.target`，而前文 `CREATE TABLE` 和 `INSERT INTO` 创建并写入的表叫 `local.db.table`。用户照抄会得到 "table not found" 之类的错误。第二处问题是列名/语义不匹配：示例使用 `WHEN MATCHED THEN UPDATE SET t.count = t.count + u.count`，但 `updates` 表与前文定义的表只有 `id` 和 `data` 两列，并不存在 `count` 列，因此该 UPDATE 语句在语义上无法成立。

此外，原先的示例还缺少 `source` 和 `updates` 这两张被 `MERGE INTO`/`INSERT INTO SELECT` 引用的源表的建表与插入语句。文档只创建了 `local.db.table`，后续却直接 `SELECT * FROM source` 和 `SELECT * FROM updates`，用户无法复现。本提交补齐了这两张以 Parquet 格式创建的临时表及其数据插入语句，使整个 Getting Started 流程自洽、可一次跑通。

与此配套，仓库中 `spark/v3.4`、`v3.5`、`v4.0`、`v4.1` 四个版本下的 `TestRoundTrip.java` 集成测试里长期存在一条 TODO 注释，明确指出"文档示例无法直接运行，测试套件不得不对示例做修改才能跑"。文档修复后，这条 TODO 已失去存在意义，本提交一并将其删除，使测试与文档保持一致。

## 如何达成设计目的

思路是直接校正文档示例使其自洽，并同步清理测试中遗留的 TODO 注释。涉及 `docs/docs/spark-getting-started.md` 文档文件与四个 Spark 版本（3.4/3.5/4.0/4.1）`spark-runtime` 模块下的 `TestRoundTrip.java`，改动方向是：补建表与插入语句、修正目标表名与更新列、移除已过时的 TODO。

## 修改详情

### `docs/docs/spark-getting-started.md` (+6/-2 lines)

**修改目的**：使 Getting Started 的 Spark SQL 示例完整且可直接运行。

**工作逻辑**：
在 `CREATE TABLE local.db.table ...` 之后新增 `CREATE TABLE source ... USING parquet` 和 `CREATE TABLE updates ... USING parquet`，为后续的 `INSERT INTO ... SELECT` 与 `MERGE INTO` 提供数据源。在 `INSERT INTO local.db.table VALUES ...` 之后补充对 `source` 和 `updates` 的 `INSERT INTO` 语句，例如 `INSERT INTO updates VALUES (1, 'x'), (2, 'x'), (4, 'z')`，其中 id=4 的行用于验证 `WHEN NOT MATCHED THEN INSERT *` 分支。最后把 `MERGE INTO local.db.target t ... UPDATE SET t.count = t.count + u.count` 改为 `MERGE INTO local.db.table t ... UPDATE SET t.data = u.data`，修正目标表名并把不存在的 `count` 列替换为实际存在的 `data` 列，使语义与表结构一致。

### `spark/v3.4/spark-runtime/src/integration/java/org/apache/iceberg/spark/TestRoundTrip.java` (+0/-2 lines)

**修改目的**：移除因文档示例不可运行而遗留的 TODO 注释。

**工作逻辑**：
删除注释 `// TODO Update doc example so that it can actually be run, modifications were required for this` 与下一行 `// test suite to run`。文档修复后该 TODO 不再成立，删除以保持注释与实际状态一致。

### `spark/v3.5/spark-runtime/src/integration/java/org/apache/iceberg/spark/TestRoundTrip.java` (+0/-2 lines)

**修改目的**：同 v3.4，移除相同 TODO 注释。

**工作逻辑**：与 v3.4 版本完全相同的删除操作，保持多版本间测试代码一致。

### `spark/v4.0/spark-runtime/src/integration/java/org/apache/iceberg/spark/TestRoundTrip.java` (+0/-2 lines)

**修改目的**：同 v3.4，移除相同 TODO 注释。

**工作逻辑**：与 v3.4 版本完全相同的删除操作。

### `spark/v4.1/spark-runtime/src/integration/java/org/apache/iceberg/spark/TestRoundTrip.java` (+0/-2 lines)

**修改目的**：同 v3.4，移除相同 TODO 注释。

**工作逻辑**：与 v3.4 版本完全相同的删除操作。

## 总结

本提交通过补齐源表建表/插入语句、修正 `MERGE INTO` 的目标表名与更新列，让 Iceberg 入门文档的 Spark SQL 示例可以原样运行，显著降低了新用户的上手摩擦；同时清理了四个 Spark 版本测试中遗留的 TODO 注释，使文档与测试代码保持一致。
