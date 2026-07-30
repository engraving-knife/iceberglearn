# 提交 1416：Spark 3.5: Correct the two-stage parsing strategy of antlr parser (#11628)

## 提交信息

- **序号**：1416 / 4088
- **哈希**：ce4c44792a31fdadb99489bca50360d9d190877d
- **短哈希**：ce4c44792
- **日期**：2024-11-22（Fri Nov 22 22:26:23 2024 +0800）
- **作者**：Cheng Pan <chengpan@apache.org>
- **提交说明**：Spark 3.5: Correct the two-stage parsing strategy of antlr parser (#11628)
- **PR/Issue**：#11628

## 总体目的

Iceberg 为 Spark 3.5 提供了一套自定义 SQL 扩展语法（如 `ALTER TABLE ... CREATE BRANCH`、`CREATE TAG` 等），由 `IcebergSparkSqlExtensionsParser` 通过 ANTLR4 解析。该解析器采用了 ANTLR4 推荐的"两阶段解析"（two-stage parsing）策略以兼顾性能与准确性：先用快速的 SLL 模式尝试解析，失败后再回退到精确但更慢的 LL 模式。

然而原实现在两阶段切换时遗漏了关键的 `ErrorStrategy` 配置：SLL 阶段应搭配 `BailErrorStrategy`（一旦遇到错误立即抛出 `ParseCancellationException` 以便快速回退），LL 阶段应搭配 `DefaultErrorStrategy`（用于生成可读的错误信息并尝试恢复）。原实现未设置 `BailErrorStrategy`，导致 SLL 模式遇到错误时不会立即取消，而是尝试错误恢复，既无法快速回退到 LL，也可能产生质量较差的错误信息。本提交修正该策略，使两阶段解析按 ANTLR4 官方推荐方式工作，并同步更新相关测试断言以匹配新的错误信息。

## 如何达成设计目的

修改 `IcebergSparkSqlExtensionsParser.scala` 中的 `parse` 方法：
- 在 SLL 阶段调用 `parser.setErrorHandler(new BailErrorStrategy)`，确保 SLL 遇到语法错误时立即抛出 `ParseCancellationException`，从而进入 catch 块回退到 LL。
- 在 LL 阶段调用 `parser.setErrorHandler(new DefaultErrorStrategy)`，让 LL 模式生成标准的错误报告与恢复行为。
- 同时在两阶段注释中引用 ANTLR4 官方 issue（https://github.com/antlr/antlr4/issues/192#issuecomment-15238595）说明设计意图，并把注释从"first, try parsing with potentially faster SLL mode"扩展为"first, try parsing with potentially faster SLL mode and BailErrorStrategy"等更清晰的描述。

由于错误策略变化，错误信息文本从原来的"mismatched input"变为更准确的"no viable alternative at input"，因此同步更新 `TestBranchDDL` 与 `TestTagDDL` 中相关断言。

## 修改详情

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/parser/extensions/IcebergSparkSqlExtensionsParser.scala`

**修改目的**：修正两阶段解析的 `ErrorStrategy` 配置。

**工作逻辑**：在 `parse` 方法中：
- SLL 阶段（内层 try）`parser.getInterpreter.setPredictionMode(PredictionMode.SLL)` 之前新增 `parser.setErrorHandler(new BailErrorStrategy)`。
- LL 阶段（catch 后）`parser.getInterpreter.setPredictionMode(PredictionMode.LL)` 之前新增 `parser.setErrorHandler(new DefaultErrorStrategy)`。
- 注释更新：在两阶段前补充引用 ANTLR4 issue 链接，说明"two-stage parsing strategy"在正确输入上可大幅节省时间；把 SLL 与 LL 的注释分别扩展为包含 `BailErrorStrategy` 与 `DefaultErrorStrategy`。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestBranchDDL.java`

**修改目的**：适配新的错误信息文本。

**工作逻辑**：把三处 `assertThatThrownBy(...).hasMessageContaining("mismatched input")`（或 `"mismatched input '123'"`）改为 `hasMessageContaining("no viable alternative at input")`（或 `"no viable alternative at input '123'"`）。覆盖用例：
- `ALTER TABLE ... CREATE BRANCH ... RETAIN`（缺少天数）
- `ALTER TABLE ... CREATE BRANCH ... RETAIN abc DAYS`（天数非数字）
- `ALTER TABLE ... DROP BRANCH 123`（分支名不合规）

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestTagDDL.java`

**修改目的**：适配新的错误信息文本。

**工作逻辑**：把四处断言改为 `"no viable alternative at input"` 或带具体 token 的 `"no viable alternative at input '<EOF>'"` / `"no viable alternative at input 'abc'"` / `"no viable alternative at input '123'"`。覆盖用例：
- `CREATE TAG ... AS OF VERSION ... RETAIN`（缺少天数）
- `CREATE TAG ... RETAIN abc DAYS`
- `CREATE TAG 123`（tag 名不合规）
- `DROP TAG 123`

## 小结

- **成效**：`IcebergSparkSqlExtensionsParser` 的两阶段解析策略与 ANTLR4 官方推荐对齐，SLL 阶段失败可立即回退到 LL，避免在 SLL 阶段进行无意义的错误恢复；LL 阶段使用 `DefaultErrorStrategy` 生成更准确的错误信息（"no viable alternative at input"）。
- **影响范围**：3 文件、+13/-9 行。生产代码仅 scala 解析器一处（4 行新增 + 注释），其余为测试断言文本调整。
- **回迁到 1.4.x 的注意事项**：1.4.x 若维护 Spark 3.5 模块且解析器实现与 main 一致，则**回迁风险低**，主要是确认 1.4.x 中 `IcebergSparkSqlExtensionsParser` 是否同样缺失 `BailErrorStrategy`/`DefaultErrorStrategy` 设置——若同样缺失则应回迁。回迁时务必连同测试断言改动一起回迁，否则 `TestBranchDDL`/`TestTagDDL` 在新错误策略下会因 "mismatched input" 断言失败。若 1.4.x 不再维护 Spark 3.5（或该版本 scala 解析器实现有差异），则按实际代码情况决定。
