# 提交 1417：Spark 3.4: Correct the two-stage parsing strategy of antlr parser (#7734)

## 提交信息

- **序号**：1417 / 4088
- **哈希**：f717ebde46b235681165636da0c8f44bfa1823e5
- **短哈希**：f717ebde4
- **日期**：2024-11-22（Fri Nov 22 22:26:47 2024 +0800）
- **作者**：Cheng Pan <chengpan@apache.org>
- **提交说明**：Spark 3.4: Correct the two-stage parsing strategy of antlr parser (#7734)
- **PR/Issue**：#7734

## 总体目的

与同日提交的 #11628（Spark 3.5 版本，本仓库序号 1416）完全对应，本提交是同一修复在 Spark 3.4 模块的镜像。Iceberg 的 Spark 3.4 SQL 扩展解析器 `IcebergSparkSqlExtensionsParser` 同样采用了 ANTLR4 的两阶段解析（SLL → LL）策略，但同样遗漏了 `BailErrorStrategy`（SLL 阶段）与 `DefaultErrorStrategy`（LL 阶段）的设置，导致 SLL 阶段不能快速回退、错误信息不准确。本提交对 Spark 3.4 模块做与 1416 相同的修正，使两代 Spark 模块行为一致。

## 如何达成设计目的

与 1416 完全相同：在 `parse` 方法的 SLL 阶段设置 `BailErrorStrategy`，LL 阶段设置 `DefaultErrorStrategy`，并更新注释引用 ANTLR4 官方 issue。同步把 `TestBranchDDL` 与 `TestTagDDL` 中相关断言从 `"mismatched input"` 改为 `"no viable alternative at input"`。

## 修改详情

### `spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/parser/extensions/IcebergSparkSqlExtensionsParser.scala`

**修改目的**：修正两阶段解析的 `ErrorStrategy` 配置。

**工作逻辑**：与 1416 中对应文件完全一致：
- SLL 阶段前新增 `parser.setErrorHandler(new BailErrorStrategy)`。
- LL 阶段前新增 `parser.setErrorHandler(new DefaultErrorStrategy)`。
- 注释补充 ANTLR4 issue 链接与 `BailErrorStrategy`/`DefaultErrorStrategy` 说明。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestBranchDDL.java`

**修改目的**：适配新的错误信息文本。

**工作逻辑**：三处断言从 `"mismatched input"`（含带 token 的 `'123'` 变体）改为 `"no viable alternative at input"`（含 `'123'` 变体）。覆盖用例与 1416 相同：`CREATE BRANCH ... RETAIN` 缺天数、`RETAIN abc DAYS`、`DROP BRANCH 123`。区别在于 3.4 中测试方法使用 `@Test`（Spark 3.4 扩展测试基类 `SparkExtensionsTestBase` 的约定），而 3.5 中是 `@TestTemplate`。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestTagDDL.java`

**修改目的**：适配新的错误信息文本。

**工作逻辑**：四处断言改为 `"no viable alternative at input"` 系列，覆盖 `CREATE TAG ... RETAIN` 缺天数（含 `<EOF>` 变体）、`RETAIN abc DAYS`、`CREATE TAG 123`、`DROP TAG 123`。与 1416 中 3.5 版本一致，仅测试基类与方法注解不同。

## 小结

- **成效**：Spark 3.4 模块的两阶段解析策略与 ANTLR4 官方推荐对齐，行为与 Spark 3.5 模块（1416）保持一致，避免 SLL 阶段无谓的错误恢复，并产生更准确的错误信息。
- **影响范围**：3 文件、+13/-9 行。生产代码仅 scala 解析器一处（4 行新增 + 注释），其余为测试断言调整。
- **回迁到 1.4.x 的注意事项**：1.4.x 若仍维护 Spark 3.4 模块，则**回迁风险低**且与 1416 配套回迁可保持两代 Spark 行为一致。需确认 1.4.x 的 Spark 3.4 解析器实现是否同样缺失 `ErrorStrategy` 设置；若是则应回迁，并务必同步测试断言改动。注意 1.4.x 中 Spark 3.4 的测试基类与方法注解可能与 main 不同（如 `@Test` vs `@TestTemplate`），回迁时以 1.4.x 实际约定为准。
