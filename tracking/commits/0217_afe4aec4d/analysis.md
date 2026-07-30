# 提交 0217：Spark: Don't allow branch_ usage with VERSION AS OF (#9219)

## 提交信息

- **序号**：0217 / 4088
- **哈希**：afe4aec4db795f4829757d738a5bbcf1b7db8fd2
- **短哈希**：afe4aec4d
- **日期**：2023-12-05
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark: Don't allow branch_ usage with VERSION AS OF (#9219)
- **PR/Issue**：#9219

## 总体目的

Spark SQL 允许通过表标识符内嵌分支来读取特定分支上的数据，例如 `catalog.ns.tbl.branch_b1`；同时也允许通过 `VERSION AS OF <snapshotId>` / `TIMESTAMP AS OF <ts>` 来做时间旅行。原先 Iceberg 的 `SparkCatalog.loadTable` 在处理 `AS OF` 时只检查 `sparkTable.snapshotId() == null`，即只要表标识符里没有显式带上 snapshot，就允许再叠加 `AS OF`。但分支同样会"锁定"读取起点——分支会解析为分支头对应的 snapshot。于是 `tbl.branch_b1 VERSION AS OF <snapshotId>` 这种写法语义上同时指定了两个读取起点（分支头 + 指定 snapshot），二者可能不一致，会让用户误以为读到的是"分支 b1 在 snapshotId 时的数据"，而实际上分支与 snapshot 是各自独立解析的，结果容易产生混淆或错误的读取。

本提交把这个隐患显式化为错误：当表标识符里同时带了 `branch_` 前缀分支，又用了 `VERSION AS OF` 时，直接抛出 `IllegalArgumentException("Cannot do time-travel based on both table identifier and AS OF")`，与原先 snapshotId 冲突时的错误信息一致。修复同时覆盖 Spark 3.3、3.4、3.5 三个版本分支，每个分支都加上等价的回归测试。

## 如何达成设计目的

整体思路很简单：在 `SparkCatalog.loadTable` 已有的"snapshotId 冲突"断言旁边追加一个"branch 冲突"断言。为此需要在 `SparkTable` 暴露已有的 `branch` 字段（原本是包级可见），新增一个 `public String branch()` 访问器供 `SparkCatalog` 读取。三处版本（3.3/3.4/3.5）改动完全一致，并各自新增 `testInvalidTimeTravelAgainstBranchIdentifierWithAsOf` 测试覆盖该错误路径。

## 修改详情

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java`（同样适用于 v3.4、v3.5）

**修改目的**：在 `loadTable` 解析 `AS OF` 时，禁止表标识符已携带 `branch_` 分支。

**工作逻辑**：原断言为 `sparkTable.snapshotId() == null`，意为"如果表名里已经带了 snapshot，就不能再叠加 AS OF"。修改后改为 `sparkTable.snapshotId() == null && sparkTable.branch() == null`，把分支也纳入同一禁用集合。错误消息沿用 "Cannot do time-travel based on both table identifier and AS OF"，与原有 snapshot 路径保持一致，对调用方语义统一：表标识符已经"固定了读取起点"时，不能再叠加 AS OF 时间旅行。

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/source/SparkTable.java`（同样适用于 v3.4、v3.5）

**修改目的**：暴露 `branch` 字段供 `SparkCatalog` 读取。

**工作逻辑**：新增 `public String branch() { return branch; }`。`branch` 字段本身早已存在（在 `SparkTable` 构造时从表标识符解析得到），只是原先没有公开访问器；为了让 `SparkCatalog` 能在断言里访问它，将其提升为 public。

### `spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java`（同样适用于 v3.4、v3.5）

**修改目的**：新增回归测试覆盖分支 + `VERSION AS OF` 的冲突。

**工作逻辑**：新增 `testInvalidTimeTravelAgainstBranchIdentifierWithAsOf`。流程：先取当前表 snapshotId，创建分支 `b1`，再 INSERT 产生第二个 snapshot，然后执行 `SELECT * FROM %s.branch_b1 VERSION AS OF %s`，断言抛 `IllegalArgumentException` 且消息匹配 "Cannot do time-travel based on both table identifier and AS OF"。这把"分支 + AS OF 冲突"作为正式契约固化下来。

## 小结

本提交修补了 Spark Catalog 在 `branch_` 表标识符与 `VERSION AS OF` 联用时语义模糊的缺陷，把它显式变成错误，避免用户读到与预期不符的数据，是 Iceberg 时间旅行/分支语义正确性的小而重要的修复。
