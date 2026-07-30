# 提交 0461：Spark 3.4: Fix CREATE OR REPLACE VIEW when view doesn't exist

## 提交信息

- **序号**：0461
- **完整哈希**：4a3e06bd963a6cf40128b635c193447a4b4a4bf4
- **短哈希**：4a3e06bd9
- **日期**：2024-02-05 10:34:23 +0100
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Spark 3.4: Fix CREATE OR REPLACE VIEW when view doesn't exist
- **PR**：#9646

## 总体目的

本提交修复了 Iceberg 在 Spark 3.4 中处理 `CREATE OR REPLACE VIEW` 语句时的一个缺陷。当目标视图尚不存在时，原实现调用的是 `ViewBuilder.replace()` 方法，而 `replace()` 的语义是「替换已存在的视图」，因此当视图不存在时会失败或抛出异常，这与 SQL 标准中 `CREATE OR REPLACE VIEW` 的语义不符——后者应在视图不存在时创建视图，在视图已存在时替换视图。

`CREATE OR REPLACE VIEW` 是数据工程中常用的幂等性写法，用户依赖它在 ETL 流水线、定时刷新任务中安全地（重新）定义视图，而不必先判断视图是否存在。若该语句在视图不存在时报错，会让流水线在首次执行时即失败，破坏幂等性。本提交将内部调用从 `replace()` 改为 `createOrReplace()`，使得无论视图是否已存在都能正确工作，从而恢复 SQL 标准所要求的「不存在即创建、存在即替换」语义。

此外，作者新增了一个针对性的回归测试 `createOrReplaceView`，确保两次连续执行 `CREATE OR REPLACE VIEW`（一次用于创建、一次用于替换）都能返回正确的数据集，防止未来回归。

## 如何达成设计目的

修改路径非常直接：在 `SparkCatalog` 中创建视图的分支上，把 `ViewBuilder.replace()` 调用替换为 `ViewBuilder.createOrReplace()`。`createOrReplace()` 对应的语义正好匹配 `CREATE OR REPLACE VIEW`：若视图存在则替换，不存在则创建。同时新增测试覆盖了「先创建后替换」两条路径，验证返回数据符合 WHERE 过滤条件。

## 修改详情

### spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java

**修改目的**：为该修复新增回归测试，覆盖 `CREATE OR REPLACE VIEW` 在视图不存在与已存在两种场景下的行为。

**工作逻辑**：
- 新增 `@Test createOrReplaceView()` 方法。
- 首先调用 `insertRows(6)` 向基础表插入 6 行数据（id 1–6）。
- 第一次执行 `CREATE OR REPLACE VIEW simpleView AS SELECT id FROM <table> WHERE id <= 3`，此时视图不存在，应被创建。断言查询视图返回 3 行，且恰好为 1、2、3。
- 第二次执行 `CREATE OR REPLACE VIEW simpleView AS SELECT id FROM <table> WHERE id > 3`，此时视图已存在，应被替换。断言查询视图返回 3 行，且恰好为 4、5、6。
- 通过两次断言验证「创建」与「替换」两个分支都被正确触发，并返回正确的过滤结果。

### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java

**修改目的**：修正 `CREATE OR REPLACE VIEW` 的内部实现，使其在视图不存在时也能成功执行。

**工作逻辑**：
- 在 `SparkCatalog` 中处理视图创建的代码块里，原先构造完 `ViewBuilder`（带 schema、location、properties）后调用 `.replace()`。
- `.replace()` 仅在视图已存在时进行替换，视图不存在时会抛异常，导致 `CREATE OR REPLACE VIEW` 语义不正确。
- 本提交将 `.replace()` 改为 `.createOrReplace()`，后者兼具「不存在则创建、存在则替换」的语义，正确对应 SQL 中的 `CREATE OR REPLACE VIEW`。
- 随后照常包装为 `SparkView` 返回。周围的 `NoSuchNamespaceException` 捕获逻辑保持不变。

## 小结

本提交通过单行核心改动（`replace()` → `createOrReplace()`）修复了 Spark 3.4 中 `CREATE OR REPLACE VIEW` 在视图不存在时失败的缺陷，使其行为符合 SQL 标准。配套新增的测试同时覆盖了创建与替换两条路径，确保修复的稳定性与可回归性。改动小而精准，风险低，直接解决了用户在 ETL 幂等场景下的痛点。
