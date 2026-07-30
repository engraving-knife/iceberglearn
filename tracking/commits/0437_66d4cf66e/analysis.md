# 提交 0437：Spark: Throw exception on `ALTER VIEW <viewName> AS <query>` (#9510)

## 提交信息

- **序号**：0437
- **哈希**：66d4cf66e2fd3f53ce4c1f0baba335ad4f968c26
- **短哈希**：66d4cf66e
- **日期**：2024-02-01 10:11:19 +0100
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Spark: Throw exception on `ALTER VIEW <viewName> AS <query>` (#9510)。补充说明：`ALTER VIEW <viewName> AS <query>` 在底层查询变更时无法保留列别名（column aliases）与列注释（column comment），可能导致非预期行为；建议改用 `CREATE OR REPLACE VIEW`，后者在以列别名/注释定义视图 schema 时语义更明确。
- **PR/Issue**：#9510

## 总体目的

本提交是对 Iceberg Spark 视图功能的一项"主动收窄"：明确拒绝 `ALTER VIEW <viewName> AS <query>` 语法，引导用户改用 `CREATE OR REPLACE VIEW`。问题根源在于语义缺陷——`ALTER VIEW ... AS ...` 虽能替换视图的底层查询，但在替换时无法可靠保留视图原有的列别名与列注释。视图的 schema（列名、注释）是视图契约的一部分，下游查询依赖这些列名；若 `ALTER VIEW AS` 静默地以新查询的列覆盖原有别名/注释，会导致下游查询因列名变化而失败，或注释丢失而难以追溯，造成难以察觉的回归。

`CREATE OR REPLACE VIEW` 则要求显式重新声明列别名与注释（如 `CREATE OR REPLACE VIEW v (col1 COMMENT '...', ...) AS ...`），schema 定义更明确，避免隐式覆盖。因此在 Iceberg 视图尚不支持安全地执行 `ALTER VIEW AS` 之前，主动抛异常比"可能错误地执行"更稳妥——这是典型的"失败优先（fail-fast）"策略，以明确错误替代隐蔽的数据契约破坏。

## 如何达成设计目的

在 Spark 分析阶段的 `CheckViews` 校验规则中新增一个模式匹配分支，识别 `AlterViewAs(ResolvedV2View(_, _), _, _)`（即对已解析的 Iceberg V2 视图执行 `ALTER VIEW ... AS ...`），直接抛 `AnalysisException` 并给出明确的迁移指引消息。这样在查询计划早期就拦截非法操作，无需等到执行层。配套新增测试：一方面验证 `ALTER VIEW AS` 确实抛出预期异常；另一方面通过 `CREATE OR REPLACE VIEW` 的列别名保留测试，证明推荐的替代方案能正确保留别名与注释，强化"应改用此方式"的引导。

## 修改详情

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/CheckViews.scala`

**修改目的**：在分析校验阶段拦截 `ALTER VIEW ... AS ...`，抛出明确异常。

**工作逻辑**：`CheckViews` 是一个 `LogicalPlan => Unit` 的校验函数，原已对 `CreateIcebergView` 做列数、列名重复等校验。本次新增 import `AlterViewAs` 与 `ResolvedV2View`，并在 `apply` 的模式匹配中新增分支：

```scala
case AlterViewAs(ResolvedV2View(_, _), _, _) =>
  throw new AnalysisException("ALTER VIEW <viewName> AS is not supported. Use CREATE OR REPLACE VIEW instead")
```

即只要是对已解析的 Iceberg V2 视图执行 `AlterViewAs`，立即抛异常。其余情况落入 `case _ => // OK`。这是最小侵入的拦截点——既在 Analyzer 完成解析之后（能拿到 `ResolvedV2View`），又在执行之前，时机恰当。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java`

**修改目的**：验证 `ALTER VIEW AS` 被拒绝，并证明 `CREATE OR REPLACE VIEW` 能正确保留列别名/注释作为推荐替代。

**工作逻辑**：

1. 新增 `createOrReplaceViewWithColumnAliases`：先 `CREATE VIEW v (new_id COMMENT 'ID', new_data COMMENT 'DATA') AS SELECT id, data FROM ...`，断言视图属性含 `spark.query-column-names = id,data`、列名/注释正确、查询结果为 3 行。再 `CREATE OR REPLACE VIEW v (data2 COMMENT 'new data', id2 COMMENT 'new ID') AS SELECT data, id FROM ...`，断言列名/注释被正确替换为新的（data2/id2 及其注释）、`spark.query-column-names` 更新为 `data,id`、查询结果对应新列名。该用例完整展示了 `CREATE OR REPLACE VIEW` 如何安全地变更视图查询并显式重声明列别名与注释。

2. 新增 `alterViewIsNotSupported`：先建视图并查询确认有 3 行，再执行 `ALTER VIEW v AS SELECT id FROM ... WHERE id > 3`，断言抛 `AnalysisException` 且消息含 "ALTER VIEW <viewName> AS is not supported. Use CREATE OR REPLACE VIEW instead"。

3. 顺带把 `insertRows` 的数据生成从 `UUID.randomUUID().toString()` 改为确定性的 `Integer.toString(i * 2)`，并移除不再使用的 `UUID` import。这使得 `createOrReplaceViewWithColumnAliases` 中对查询结果的确切断言（`row("2", 1), row("4", 2), row("6", 3)`）成为可能——随机数据无法做精确断言。

## 小结

本提交以"主动拒绝"方式规避了 `ALTER VIEW AS` 的语义缺陷，是质量优先的设计取舍。在功能尚未能安全实现前，明确报错优于静默误用。`CREATE OR REPLACE VIEW` 作为推荐替代被测试验证可正确保留列别名与注释，引导清晰。把 `insertRows` 改为确定性数据是合理的附带改进，使精确断言可行。该改动与 0435（视图属性变更）同属 Iceberg Spark 视图功能完善系列，共同明确了视图可做与不可做的操作边界。
