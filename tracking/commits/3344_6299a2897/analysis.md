# 提交 3344：docs: Add CREATE DATABASE step before CREATE TABLE in Spark quickstart (#15513)

## 提交信息

- **序号**：3344 / 4088
- **哈希**：6299a289732dfeda7b5ab6d11d4438015505910e
- **短哈希**：6299a2897
- **日期**：2026-03-04 14:08:07 -0800
- **作者**：Ramesh Reddy Adutla
- **提交说明**：docs: Add CREATE DATABASE step before CREATE TABLE in Spark quickstart (#15513)
- **PR/Issue**：#15513（关闭 issue #15509）

## 总体目的

本提交修复 Spark 快速入门文档中的一个步骤缺失问题：文档指导用户创建表 `demo.nyc.taxis`，却未事先创建数据库（命名空间）`nyc`，导致新用户照做时直接报错。

背景是：`site/docs/spark-quickstart.md` 在引导用户创建第一张 Iceberg 表时，解释了 `demo.nyc.taxis` 的含义——`demo` 是 catalog 名、`nyc` 是 database（命名空间）名、`taxis` 是表名，随后直接给出 `CREATE TABLE demo.nyc.taxis (...)` 语句。但 Iceberg/Spark 的 catalog 中，命名空间 `nyc` 不会自动创建——若该命名空间不存在，`CREATE TABLE` 会因"数据库不存在"而失败。文档此前在创建表之前从未提示用户执行 `CREATE DATABASE`，新用户（尤其是刚接触 Iceberg 的用户）照着 quickstart 操作就会卡在这一步，体验不佳。Issue #15509 即报告了此问题。本提交在 `CREATE TABLE` 之前补上 `CREATE DATABASE IF NOT EXISTS demo.nyc` 步骤，使文档流程能够顺畅走通。

## 如何达成设计目的

在 quickstart 文档"创建第一张表"段落中、`CREATE TABLE` 的代码块之前，插入一个新的 `CREATE DATABASE IF NOT EXISTS demo.nyc` 步骤，并沿用文档既有的三标签页格式（SparkSQL、Spark-Shell、PySpark）分别给出对应语法，再加一句过渡说明"Then create the table:"衔接到原有的建表步骤。使用 `IF NOT EXISTS` 保证重复执行不报错。

## 修改详情

### `site/docs/spark-quickstart.md` (+22 lines)

**修改目的**：在建表步骤前补建数据库步骤，修复新用户照文档操作即失败的问题。

**工作逻辑**：
在解释 `demo.nyc.taxis` 含义的段落之后、原 `CREATE TABLE` 代码块之前，新增以下内容：

1. 一句引导语"First, create the database if it doesn't already exist:"。

2. 三个 MkDocs `===` 标签页，分别给出三种 Spark 接口的建库语法：
   - `=== "SparkSQL"`：`CREATE DATABASE IF NOT EXISTS demo.nyc;`
   - `=== "Spark-Shell"`：`spark.sql("CREATE DATABASE IF NOT EXISTS demo.nyc")`（Scala）
   - `=== "PySpark"`：`spark.sql("CREATE DATABASE IF NOT EXISTS demo.nyc")`（Python）

3. 一句过渡语"Then create the table:"，衔接到原有 `CREATE TABLE` 步骤。

三个标签页的格式与文档中其他步骤（如建表、查询）完全一致，保持风格统一。`CREATE DATABASE IF NOT EXISTS` 使用 `IF NOT EXISTS` 子句，使命令可安全重复执行——即便命名空间已存在也不会报错。`demo.nyc` 中 `demo` 是此前已配置好的 catalog，`nyc` 是待创建的命名空间。补上此步后，后续 `CREATE TABLE demo.nyc.taxis` 才能成功。

## 总结

本提交在 Spark 快速入门文档的建表步骤前补充了 `CREATE DATABASE IF NOT EXISTS demo.nyc` 步骤（覆盖 SparkSQL/Spark-Shell/PySpark 三种接口），修复了原文档因缺少建库步骤而导致新用户照做即报错的问题（issue #15509），使入门流程完整可走通，显著改善了新用户的首次体验。
