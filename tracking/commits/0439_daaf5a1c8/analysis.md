# 提交 0439：Spark 3.4: Throw exception on `ALTER VIEW <viewName> AS <query>` (#9612)

## 提交信息

- **序号**：0439
- **哈希**：daaf5a1c873e38938634156df9c8b6568c404874
- **短哈希**：daaf5a1c8
- **日期**：2024-02-01 15:29:52 +0100
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Spark 3.4: Throw exception on `ALTER VIEW <viewName> AS <query>` (#9612)。完整说明为："`ALTER VIEW <viewName> AS <query>` doesn't allow to preserve column aliases and column comment when the underlying query is changed, which can lead to unexpected behavior. For now it's better to use `CREATE OR REPLACE VIEW` as that is more explicit when the schema of the view is defined with column aliases/comments."
- **PR/Issue**：#9612

## 总体目的

本提交明确禁止在 Iceberg Spark 3.4 视图上执行 `ALTER VIEW <viewName> AS <query>` 这条 SQL，改为抛出 `AnalysisException`，并提示用户改用 `CREATE OR REPLACE VIEW`。这是一个面向语义正确性的防御性改动。

问题的根源在于 `ALTER VIEW ... AS <query>` 这条命令的语义在视图带列别名/列注释时是不安全的。Iceberg 视图在创建时可以指定列别名和列注释（例如 `CREATE VIEW v (new_id COMMENT 'ID') AS SELECT id ...`），这些别名和注释作为视图 schema 的一部分被持久化。但 `ALTER VIEW v AS <新查询>` 这条命令只替换底层查询文本，无法稳定地保留原有的列别名和列注释——当新查询的列顺序或结构发生变化时，原有别名/注释与新查询列的对应关系会被破坏，产生未定义行为。相比之下，`CREATE OR REPLACE VIEW` 要求用户在替换时重新显式声明列别名与注释，语义清晰、无歧义。

因此本提交选择"先禁止、再引导"的策略：在分析检查阶段（`CheckViews`）拦截 `AlterViewAs` 命令，给出明确错误信息和替代方案，避免用户踩到这个语义陷阱。这同时也降低了 Iceberg 自身实现视图的复杂度——不需要为 `ALTER VIEW AS` 设计一套列别名/注释保留规则。

## 如何达成设计目的

实现非常简洁：在 `CheckViews` 检查规则中新增一个 case 分支，匹配到 `AlterViewAs(ResolvedV2View(_, _), _, _)` 时抛出 `AnalysisException`，错误信息明确指向替代方案 `CREATE OR REPLACE VIEW`。`CheckViews` 是 Spark 分析阶段的检查规则，会在查询计划解析完成后执行，适合做这类"不允许此命令"的拦截。匹配条件要求目标是 `ResolvedV2View`（即 Iceberg 视图），因此只影响 Iceberg catalog 下的视图，不影响其他 catalog。配套测试验证了拦截行为，并补充了一个 `CREATE OR REPLACE VIEW` 保留列别名/注释的正向用例，说明替代方案确实能正确工作。

## 修改详情

### spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/CheckViews.scala

**修改目的**：在视图检查规则中拦截 `ALTER VIEW <viewName> AS <query>`，对其抛出异常。

**工作逻辑**：`CheckViews` 是一个 `LogicalPlan => Unit` 的检查对象，原本只对 `CreateIcebergView` 做列数与列名重复校验。本提交新增 import `AlterViewAs` 和 `ResolvedV2View`，并增加 case 分支：

```scala
case AlterViewAs(ResolvedV2View(_, _), _, _) =>
  throw new AnalysisException("ALTER VIEW <viewName> AS is not supported. Use CREATE OR REPLACE VIEW instead")
```

该分支匹配目标为已解析的 Iceberg V2 视图的 `AlterViewAs` 命令，直接抛异常。由于位于 `case _ => // OK` 之前，所以会优先命中。匹配只针对 `ResolvedV2View`，因此非 Iceberg 视图不受影响。

### spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java

**修改目的**：验证 `ALTER VIEW AS` 被正确拦截，并补充 `CREATE OR REPLACE VIEW` 保留列别名/注释的正向测试；同时把测试数据由随机 UUID 改为确定性值以便断言。

**工作逻辑**：新增两个测试方法：
- `createOrReplaceViewWithColumnAliases`：先创建带列别名和注释的视图（`new_id COMMENT 'ID'`, `new_data COMMENT 'DATA'`），校验 schema 与 `spark.query-column-names` 属性正确；然后用 `CREATE OR REPLACE VIEW` 替换为新的列别名/注释（`data2 COMMENT 'new data'`, `id2 COMMENT 'new ID'`），校验新 schema、新属性、查询结果全部正确。这证明替代方案能完整保留/替换列别名与注释。
- `alterViewIsNotSupported`：创建视图后执行 `ALTER VIEW %s AS SELECT id FROM %s WHERE id > 3`，断言抛出 `AnalysisException` 且消息包含 `"ALTER VIEW <viewName> AS is not supported. Use CREATE OR REPLACE VIEW instead"`。

此外，`insertRows` 方法把原来 `new SimpleRecord(i, UUID.randomUUID().toString())` 改为 `new SimpleRecord(i, Integer.toString(i * 2))`，使数据变为确定值（`data = "2", "4", "6"`），从而让 `createOrReplaceViewWithColumnAliases` 中的 `containsExactlyInAnyOrder(row("2", 1), row("4", 2), row("6", 3))` 这类断言成为可能。同时移除了不再使用的 `java.util.UUID` import。

## 小结

本提交是一个小而精的防御性改动：通过在 `CheckViews` 中拦截 `ALTER VIEW AS`，避免了该命令在列别名/注释保留上的语义陷阱，并明确引导用户使用 `CREATE OR REPLACE VIEW`。它紧跟 0438 提交（后者重构了 `ResolvedV2View` 解析路径，使得 `AlterViewAs` 能在分析阶段被识别为针对 Iceberg 视图的命令），二者配合形成了完整的视图命令支持矩阵：`CREATE VIEW`、`CREATE OR REPLACE VIEW`、`ALTER VIEW SET/UNSET TBLPROPERTIES`、`DROP VIEW`、`SHOW VIEWS` 均支持，而 `ALTER VIEW AS` 因语义不安全被显式禁止。测试数据从随机改为确定值也是一个值得注意的工程改进，提升了测试的可重复性与可断言性。
