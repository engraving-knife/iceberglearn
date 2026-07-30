# 提交 1483：Spark 3.4,3.5: Fix issue when views group by an ordinal (#11729)

## 提交信息

- **序号**：1483 / 4088
- **哈希**：587620b20dcdada7b1fa730206937c4b6a7ce527
- **短哈希**：587620b20
- **日期**：2024-12-12（Thu Dec 12 15:52:02 2024 +0800）
- **作者**：Ppei-Wang <ppeiwang2@gmail.com>
- **提交说明**：Spark 3.4,3.5: Fix issue when views group by an ordinal (#11729)
- **PR/Issue**：#11729

## 总体目的

Iceberg 的 Spark 扩展实现了 `ResolveViews` 这一 Spark Catalyst 分析规则，用于把 Iceberg View（通过 `ViewCatalog` 创建）的存储 SQL 文本重新解析成 LogicalPlan 并对其中的标识符做限定（catalog / namespace / 函数名）以保留正确解析语义。原实现只调用了 `CTESubstitution.apply(plan)` 来替换 WITH 子句（CTE），但漏掉了 Spark 在分析阶段对 `GROUP BY <序号>`、`ORDER BY <序号>` 等"未解析序号"（Unresolved Ordinal）的替换步骤。

结果：当 Iceberg View 的 SQL 中包含 `GROUP BY 1` 这类按列序号分组的写法时，由于 `UnresolvedOrdinal` 节点未被替换为对应列引用，后续分析阶段会失败（典型表现为无法解析 ordinal、抛出 AnalysisException），用户无法创建或读取这样的视图。

本提交在 `rewriteIdentifiers` 中追加 `SubstituteUnresolvedOrdinals`，与 Spark 自身会话对普通视图的处理保持一致，从而支持视图 SQL 中按序号分组的场景。

## 如何达成设计目的

在 `ResolveViews.rewriteIdentifiers` 中，原本流程：
```
CTESubstitution.apply(plan)
  -> qualifyFunctionIdentifiers
  -> qualifyTableIdentifiers
```
改为：
```
SubstituteUnresolvedOrdinals.apply(CTESubstitution.apply(plan))
  -> qualifyFunctionIdentifiers
  -> qualifyTableIdentifiers
```

即把 `SubstituteUnresolvedOrdinals`（Spark Catalyst 内置规则）叠加在 CTE 替换之后、限定标识符之前。这等价于手动补做 Spark 在 Analyzer 阶段对视图文本做的"序号替换"一步，避免 Iceberg 自定义视图解析路径绕过该步骤后留下未解析 ordinal。

同时在 `TestViews.java` 中新增两个测试用例（分别覆盖通过 `ViewCatalog` API 创建视图和通过 `CREATE VIEW` SQL 创建视图两种路径），验证 `GROUP BY 1` 在两种创建方式下都能正常工作。

## 修改详情

### `spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/ResolveViews.scala`

**修改目的**：在 Iceberg 视图解析流程中补做未解析序号替换。

**工作逻辑**：在 `rewriteIdentifiers` 方法中把原本的 `CTESubstitution.apply(plan)` 改写为 `SubstituteUnresolvedOrdinals.apply(CTESubstitution.apply(plan))`，并相应更新注释为"Substitute CTEs and Unresolved Ordinals within the view"。`SubstituteUnresolvedOrdinals` 是 Spark Catalyst 的内置规则，会把 `GROUP BY 1` / `ORDER BY 1` 中的 `UnresolvedOrdinal` 替换为对应位置的列引用表达式，从而让后续分析规则能正确识别。

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/ResolveViews.scala`

**修改目的**：同上，对 Spark 3.5 分支做相同修复。

**工作逻辑**：3.5 版本中 `ResolveViews.scala` 的 `rewriteIdentifiers` 与 3.4 几乎完全一致，本提交做完全相同的修改（同样的 `SubstituteUnresolvedOrdinals.apply(CTESubstitution.apply(plan))` 与同样的注释更新）。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java`

**修改目的**：补充针对 `GROUP BY <序号>` 的回归测试。

**工作逻辑**：新增两个测试方法：
- `readFromViewWithGroupByOrdinal`：通过 `ViewCatalog.buildView()` API 创建视图，视图 SQL 为 `SELECT id, count(1) FROM %s GROUP BY 1`，然后 `SELECT * FROM <view>` 验证结果。先插入 3 行（id=1,2,3）再插入 2 行（id=1,2），GROUP BY id 后期望得到 `(1, 2L), (2, 2L), (3, 1L)` 三行，用 `containsExactlyInAnyOrder` 校验。
- `createViewWithGroupByOrdinal`：通过 SQL `CREATE VIEW %s AS SELECT id, count(1) FROM %s GROUP BY 1` 创建视图，再查询验证结果相同。

两个用例分别覆盖 Iceberg View 的 API 创建路径与 SQL 创建路径，确保两条路径都能正确处理 ordinal。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java`

**修改目的**：在 Spark 3.5 测试套件中同步加入相同回归测试。

**工作逻辑**：与 3.4 完全相同的两个测试方法（`readFromViewWithGroupByOrdinal` / `createViewWithGroupByOrdinal`），唯一差别是测试注解使用 JUnit 5 的 `@TestTemplate`（3.5 测试基类 `ExtensionsTestBase` 用 JUnit 5 参数化方式跑多 catalog），而 3.4 使用 `@Test`。测试体逻辑与期望完全一致。

## 小结

- **成效**：Iceberg Spark 3.4 / 3.5 扩展现在能正确支持视图 SQL 中使用 `GROUP BY <序号>`（以及类似 `ORDER BY <序号>`）的写法，与 Spark 原生视图行为一致；并补齐两条视图创建路径的回归测试。
- **影响范围**：4 个文件、72 行新增 / 4 行修改。仅影响 Iceberg Spark 扩展模块（spark-extensions）的视图解析规则与对应测试，不影响 Core / 其它引擎。
- **回迁到 1.4.x 的注意事项**：
  - 1.4.x 对应 Spark 3.3_3.4_3.5（视具体版本而定），本修复影响的是视图功能正确性，对使用了 `GROUP BY <序号>` 视图的用户是必要 bug 修复，**建议回迁**。
  - cherry-pick 时要注意 1.4.x 分支支持哪些 Spark 版本：若 1.4.x 同时维护 3.4 和 3.5，需要把对应两份 `ResolveViews.scala`（v3.4 / v3.5）和两份 `TestViews.java` 都一起 cherry-pick；若 1.4.x 仅维护其中一个 Spark 版本，则只挑对应子目录即可。
  - `SubstituteUnresolvedOrdinals` 是 Spark Catalyst 内置可用规则，无需新增依赖，cherry-pick 干净。
  - 若 1.4.x 的 `ResolveViews.scala` 已与 main 有偏离（例如行号或上下文不同），需手工确认 `rewriteIdentifiers` 方法的修改位置一致即可。
