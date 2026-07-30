# 提交 0287：Spark: Add tests for SELECT using tag/branch prefix identifier (#9286)

## 提交信息

- **序号**：0287 / 4088
- **哈希**：97666096e8ad3fb46d23ab8c868197be08353c67
- **短哈希**：97666096e
- **日期**：2023-12-19 08:21:24 +0100
- **作者**：Wing Yew Poon
- **提交说明**：Spark: Add tests for SELECT using tag/branch prefix identifier (#9286)
- **PR/Issue**：#9286

## 总体目的

Iceberg 的 Spark 集成支持通过表名后缀的形式做时间旅行（time travel），即在表名后追加形如 `at_timestamp_<ms>`、`snapshot_id_<id>` 的前缀标识符来指定读取的快照。在此基础上，Iceberg 也支持通过 `tag_<tag名>` 与 `branch_<branch名>` 前缀标识符直接在表名中引用某个 tag 或 branch，从而用一条纯 SQL `SELECT * FROM <table>.tag_<tagName>` 完成基于分支/标签的读取，而不必显式调用 `VERSION AS OF` 或 `FOR SYSTEM_VERSION AS OF`。

然而，在 v3.3、v3.4、v3.5 三个 Spark 版本的 `TestSelect` 测试中，针对 tag 与 branch 的测试用例（原名 `testTagReferenceAsOf` 和 `testBranchReferenceAsOf`）只覆盖了 `VERSION AS OF '<ref>'`、`FOR SYSTEM_VERSION AS OF '<ref>'` 以及 DataFrameReader 的 `option(TAG/BRANCH, ...)` 三种读法，并没有覆盖 `tag_`/`branch_` 前缀标识符这种表名形式的读取。这意味着该前缀解析路径在测试矩阵中存在盲区，一旦 `SparkCatalog.load` 中处理 namespace 作为 selector 的逻辑出现回归，CI 无法及时捕捉。

本提交的目的就是为这条前缀标识符读取路径补齐测试，确保通过 `SELECT * FROM <table>.tag_test_tag` 与 `SELECT * FROM <table>.branch_test_branch` 这样的 SQL 也能正确读到对应 tag/branch 的快照数据，并与现有读法的结果保持一致。

需要注意的是，Spark 内置的 session catalog（`spark_catalog`）不支持扩展表名（extended table names），该前缀语法仅在用户自定义的 Iceberg catalog（如 `testhive`、`testhadoop` 等）下可用。因此测试需要按 `catalogName` 做条件跳过，避免在 session catalog 下误报失败。这一点与同文件中已有的 `testTimestampInTableName`、`testSnapshotIdInTableName` 等用例的处理方式一致。

## 如何达成设计目的

整体设计思路是：在原有 `testTagReferenceAsOf` 与 `testBranchReferenceAsOf` 两个测试方法中，分别新增一段基于前缀标识符的读取断言，而不是单独新建测试方法。这样可以在最小改动下复用既有的"建表、写数据、创建 tag/branch、写第二份数据"的测试铺垫，把新读法直接挂在已有断言链之后。

同时，为使方法名更贴合实际测试内容（不再仅是 "AsOf" 一种读法），把两个方法重命名为 `testTagReference` 与 `testBranchReference`，并把方法内的注释措辞从"read the table at the snapshot"调整为"read the table at the tag/branch"，与新读法语义对齐。新增的前缀读断言通过 `if (!"spark_catalog".equals(catalogName))` 守卫，仅在非 session catalog 下执行。三个 Spark 版本（v3.3、v3.4、v3.5）的 `TestSelect.java` 做完全一致的修改，保证三条分支的行为统一。

## 修改详情

### `spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java`

**修改目的**：为 v3.3 Spark 补充 tag/branch 前缀标识符 SELECT 的测试覆盖。

**工作逻辑**：

- 把 `testTagReferenceAsOf` 重命名为 `testTagReference`，把 `testBranchReferenceAsOf` 重命名为 `testBranchReference`，使方法名反映已涵盖多种读法。
- 在 `testTagReference` 中：保留原流程（创建 `test_tag`、记录 `expected`、写第二份数据、用 `VERSION AS OF 'test_tag'` 与 `FOR SYSTEM_VERSION AS OF 'test_tag'` 两种读法断言）。新增一段：当 `catalogName` 不等于 `"spark_catalog"` 时，执行 `SELECT * FROM %s.tag_test_tag` 并断言其结果与 `expected` 一致。注释统一改为"read the table at the tag"，并把原先误标为"read the table using DataFrameReader option: branch"的注释修正为"option: tag"。
- 在 `testBranchReference` 中：与 tag 类似，保留原 `VERSION AS OF 'test_branch'` 与 `FOR SYSTEM_VERSION AS OF 'test_branch'` 断言，新增 `if (!"spark_catalog".equals(catalogName))` 守卫下的 `SELECT * FROM %s.branch_test_branch` 断言。注释统一改为"read the table at the branch"。
- 调整空行与注释顺序，使新增的前缀读断言位于 Hive 语法读法之后、DataFrameReader option 读法之前，整体阅读顺序为：Spark SQL `VERSION AS OF` → Hive `FOR SYSTEM_VERSION AS OF` → 表名前缀读法 → DataFrameReader option 读法。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java`

**修改目的**：为 v3.4 Spark 补充 tag/branch 前缀标识符 SELECT 的测试覆盖。

**工作逻辑**：与 v3.3 同名文件的修改完全一致——重命名两个测试方法、新增 `tag_test_tag` 与 `branch_test_branch` 的前缀读断言、用 `!"spark_catalog".equals(catalogName)` 守卫跳过 session catalog、修正注释措辞。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java`

**修改目的**：为 v3.5 Spark 补充 tag/branch 前缀标识符 SELECT 的测试覆盖。

**工作逻辑**：与 v3.3、v3.4 同名文件的修改完全一致。

## 小结

本提交为 v3.3、v3.4、v3.5 三个 Spark 版本的 `TestSelect` 测试补充了通过表名前缀标识符（`tag_<name>`、`branch_<name>`）进行 `SELECT` 读取的测试用例，填补了该时间旅行读法在测试矩阵中的空白。新断言复用既有测试铺垫，并通过 `catalogName` 守卫跳过不支持扩展表名的 `spark_catalog`，确保测试在所有支持的 catalog 下都能正确验证 tag/branch 前缀解析路径。
