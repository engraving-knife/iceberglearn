# 提交 1425：Spark 3.3: Correct the two-stage parsing strategy of antlr parser (#11630)

## 提交信息

- **序号**：1425 / 4088
- **哈希**：4337040be07565608b945300ceabca91659c7766
- **短哈希**：4337040be
- **日期**：2024-11-25（Mon Nov 25 15:48:17 2024 +0800）
- **作者**：Cheng Pan <chengpan@apache.org>
- **提交说明**：Spark 3.3: Correct the two-stage parsing strategy of antlr parser (#11630)
- **PR/Issue**：#11630

## 总体目的

Iceberg 在 Spark 3.3 的扩展解析器 `IcebergSparkSqlExtensionsParser` 中实现了 ANTLR4 的"两阶段解析"策略：先用快速的 SLL（SLL = Strong LL）模式尝试解析，失败后回退到更慢但更精确的 LL 模式。但原实现存在两个问题：

1. 在 SLL 阶段没有设置 `BailErrorStrategy`，导致 SLL 模式在遇到语法错误时不会立即抛出 `ParseCancellationException`，而是尝试错误恢复（default error strategy），这会让"SLL 失败快速回退到 LL"的设计形同虚设，因为 SLL 阶段会消耗大量时间在错误恢复上。
2. 在 LL 阶段也没有显式设置 `DefaultErrorStrategy`，错误信息可能不符合预期。

正确的两阶段解析策略（参考 ANTLR4 作者的建议，[antlr/antlr4#192](https://github.com/antlr/antlr4/issues/192#issuecomment-15238595)）应当是：
- SLL 阶段配合 `BailErrorStrategy`，遇到任何错误立即抛 `ParseCancellationException`，快速失败；
- 失败后回退到 LL 阶段，配合 `DefaultErrorStrategy`，给出最准确的错误信息和位置。

本提交修正了这个两阶段解析策略，使 Spark 3.3 模块在解析 Iceberg 扩展 SQL（如 `ALTER TABLE ... CREATE BRANCH/TAG`）时既快又准，同时错误信息从原来的 `mismatched input` 变为更准确的 `no viable alternative at input`。

## 如何达成设计目的

1. 在 SLL 阶段调用 `parser.setErrorHandler(new BailErrorStrategy)`，配合 `setPredictionMode(PredictionMode.SLL)`，确保 SLL 失败时立即抛 `ParseCancellationException`。
2. 在回退的 LL 阶段调用 `parser.setErrorHandler(new DefaultErrorStrategy)`，配合 `setPredictionMode(PredictionMode.LL)`，给出最准确的错误信息。
3. 添加注释引用 ANTLR4 的 issue，说明这是"两阶段解析策略"的推荐用法。
4. 调整 `TestBranchDDL` 和 `TestTagDDL` 中错误信息断言：从 `mismatched input` 改为 `no viable alternative at input`，与新策略下的错误信息一致。

## 修改详情

### `spark/v3.3/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/parser/extensions/IcebergSparkSqlExtensionsParser.scala`

**修改目的**：修正两阶段解析策略的错误处理器配置。

**工作逻辑**：

- 在 SLL `try` 块内，`parser.getInterpreter.setPredictionMode(PredictionMode.SLL)` 之前新增：
  ```scala
  parser.setErrorHandler(new BailErrorStrategy)
  ```
- 在 `catch` 后的 LL `try` 块内，`parser.getInterpreter.setPredictionMode(PredictionMode.LL)` 之前新增：
  ```scala
  parser.setErrorHandler(new DefaultErrorStrategy)
  ```
- 添加注释说明这是 ANTLR4 推荐的两阶段解析策略，能"在正确输入上节省大量时间"。

这样，正确输入会直接在 SLL 阶段完成解析（快）；错误输入会在 SLL 阶段被 `BailErrorStrategy` 立即打断并抛 `ParseCancellationException`，然后 LL 阶段用 `DefaultErrorStrategy` 重新解析以给出最准确的错误信息。

### `spark/v3.3/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestBranchDDL.java`

**修改目的**：同步错误信息断言。

**工作逻辑**：把三处 `.hasMessageContaining("mismatched input")` 改为 `.hasMessageContaining("no viable alternative at input")`。对应的非法 SQL 包括：
- `ALTER TABLE ... CREATE BRANCH ... RETAIN`（缺少参数）
- `ALTER TABLE ... CREATE BRANCH ... RETAIN abc DAYS`（参数类型错误）
- `ALTER TABLE ... DROP BRANCH 123`（非法分支名）

### `spark/v3.3/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestTagDDL.java`

**修改目的**：同步错误信息断言。

**工作逻辑**：把四处 `.hasMessageContaining("mismatched input")` 改为 `.hasMessageContaining("no viable alternative at input")`，包括 `no viable alternative at input '<EOF>'`、`no viable alternative at input 'abc'`、`no viable alternative at input '123'` 等具体形式。对应非法的 `CREATE TAG`/`DROP TAG` 语句。

## 小结

- **成效**：Spark 3.3 模块的 Iceberg SQL 扩展解析器现在使用正确的两阶段解析策略——正确输入更快、错误输入错误信息更准确（`no viable alternative at input`），与 ANTLR4 官方推荐用法一致。
- **影响范围**：仅 Spark 3.3 的 `spark-extensions` 模块解析器与对应 DDL 测试。不涉及表格式或元数据变更；错误信息文本的变化对外部依赖该错误信息文本的下游属于"行为微调"。
- **回迁到 1.4.x 的注意事项**：可以无风险回迁。这是一个明确的 bug 修复（原 SLL 阶段缺少 `BailErrorStrategy` 实际上让两阶段策略退化），1.4.x 的 Spark 3.3 模块若存在同样问题应回迁。需注意同时回迁测试断言的文本变更（`mismatched input` → `no viable alternative at input`），否则 CI 会失败。如果 1.4.x 已有用户依赖原错误信息文本做匹配（可能性较低），需在 release notes 中提示该变化。
