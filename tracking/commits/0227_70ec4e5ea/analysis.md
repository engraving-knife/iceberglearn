# 提交 0227：Spark: Bump Spark minor versions for 3.3 and 3.4 (#9187)

## 提交信息

- **序号**：0227 / 4088
- **哈希**：70ec4e5ead8db9bd946c44731192b47862bfdeea
- **短哈希**：70ec4e5ea
- **日期**：2023-12-06
- **作者**：Ajantha Bhat
- **提交说明**：Spark: Bump Spark minor versions for 3.3 and 3.4 (#9187)
- **PR/Issue**：#9187

## 总体目的

这个提交把 Iceberg Spark 集成所依赖的 Spark 3.3 与 Spark 3.4 的次版本号向上升级一个补丁版本，确保 Iceberg 与最新的 Spark 维护版本保持兼容并跑通 CI。

具体来说：`spark-hive33` 从 `3.3.2` 升到 `3.3.3`，`spark-hive34` 从 `3.4.1` 升到 `3.4.2`。这两个是 Spark 3.3.x 和 3.4.x 系列的维护版本，通常包含 bug 修复与小的改进。Iceberg 的多版本 Spark 集成矩阵需要在每个主/次版本上选取一个稳定点进行构建与测试，定期跟进上游补丁版本是必要的维护工作。

升级后作者发现 Spark 3.4 中某些测试场景下旧有的 `DROP TABLE IF EXISTS` 行为发生了变化（可能与外部表的删除语义或 trash 行为有关），导致测试用例中遗留的源表在下次测试运行时干扰断言，因此同步把若干处 `DROP TABLE IF EXISTS` 改为 `DROP TABLE IF EXISTS ... PURGE`，强制彻底删除表数据。这同时修复了 `TestSnapshotTableProcedure` 中一处格式说明符大小写的 bug：把 `%S`（大写，会把字符串内容大写化）改成正确的 `%s`。

## 如何达成设计目的

整体思路是依赖版本升级 + 测试适配：

1. 在 `gradle/libs.versions.toml` 中更新两个 Spark 版本字符串。
2. 跑受影响的 Spark 3.4 测试，把删除外部/源表相关的语句加上 `PURGE`，避免版本升级后表残留影响后续测试。
3. 顺手修掉一处 `%S` 格式说明符笔误。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 Spark 3.3 / 3.4 集成所用版本。

**工作逻辑**：把 `spark-hive33 = "3.3.2"` 改为 `spark-hive33 = "3.3.3"`，把 `spark-hive34 = "3.4.1"` 改为 `spark-hive34 = "3.4.2"`。`spark-hive32` 与 `spark-hive35` 保持不变。这一行定义在 Gradle version catalog 中，被各 Spark 模块构建脚本引用。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestAddFilesProcedure.java`

**修改目的**：让 `dropTables()` 清理方法在版本升级后能彻底删除源表。

**工作逻辑**：在 `@After` 方法中把 `sql("DROP TABLE IF EXISTS %s", sourceTableName)` 改为 `sql("DROP TABLE IF EXISTS %s PURGE", sourceTableName)`。PURGE 选项让 Spark 直接跳过 trash 删除表数据，避免外部表残留影响后续测试。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestSnapshotTableProcedure.java`

**修改目的**：修复源表清理语句，并顺手修掉一处格式说明符大小写 bug。

**工作逻辑**：原代码 `sql("DROP TABLE IF EXISTS %S", sourceName)` 中使用了大写 `%S`，会把 `sourceName` 内容整体大写化（导致 SQL 中的标识符错误）。本提交改为 `sql("DROP TABLE IF EXISTS %s PURGE", sourceName)`，既修正为正确的 `%s`，又同时加上 PURGE。一举两得。

### `spark/v3.4/spark-runtime/src/integration/java/org/apache/iceberg/spark/SmokeTest.java`

**修改目的**：让冒烟测试中两次重建 `source` 与 `updates` 表时彻底清理旧表。

**工作逻辑**：把 `sql("DROP TABLE IF EXISTS source")` 和 `sql("DROP TABLE IF EXISTS updates")` 两处都加上 `PURGE`。SmokeTest 在同一用例内会先建表、删表、再建表，PURGE 确保重建时底层目录是干净的，避免 3.4.2 上数据残留导致行数断言失败。

## 小结

常规的依赖版本跟进维护，附带几个测试用例适配 PURGE 行为与一处格式说明符 bug 修复，保证 Spark 3.3/3.4 集成在新补丁版本上持续可构建可测试。
