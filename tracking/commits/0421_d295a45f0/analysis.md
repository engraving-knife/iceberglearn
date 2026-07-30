# 提交 0421：Spark 3.4: Fix writing of default values in CoW for rows with NULL columns which are unmatched (#9556)

## 提交信息

- **序号**：0421
- **哈希**：d295a45f07ea9af37aabd1afacd64755d0e84f82
- **短哈希**：d295a45f0
- **日期**：Tue Jan 30 09:18:03 2024 -0800
- **作者**：Amogh Jahagirdar <amogh@tabular.io>
- **提交说明**：Spark 3.4: Fix writing of default values in CoW for rows with NULL columns which are unmatched (#9556)
- **PR/Issue**：#9556

## 总体目的

在 Spark 3.4 的 Iceberg 集成中，`MERGE INTO` 语句通过 `RewriteMergeIntoTable` 规则重写为基于 `MergeRows` 算子的 Copy-on-Write（CoW）计划。CoW 模式下，目标表的所有行都需要被重写：匹配到源的行执行 UPDATE/DELETE 动作，未匹配到源的行则原样保留（passed through）。为了正确地重写数据，`MergeRows` 算子的输出 schema 必须有准确的 **nullable 属性**——某列在输出中是否可能为 NULL，取决于所有可能产生输出的分支（matched actions 的输出、not-matched actions 的输出，以及未被任何 action 命中而原样透传的目标行）中该列的 nullable 性。

本提交修复的 bug 是：在 Spark 3.4 的 CoW 重写路径中，`buildMergeRowsOutput` 计算 `MergeRows` 输出 schema 的 nullable 映射时，**只考虑了 matched actions 和 not-matched actions 的输出表达式，却遗漏了"未匹配目标行原样透传"这一路**。这意味着：如果目标表有某列允许 NULL（例如 `value INT`，可为空），且该列在所有 action 输出中恰好都是非空字面量（例如 `UPDATE SET id=123, value=456`，456 是非空 literal），那么输出 schema 会错误地把该列标记为 **non-nullable**。当 Spark 的 writer 后续处理一条未匹配目标行（该行此列的值确实是 NULL）时，由于 schema 声明该列不可为空，writer 会把 NULL 替换为该类型的默认值（如 INT 的 0），导致**数据正确性被破坏**：原本应当保留为 NULL 的值被写成了 0。

这个 bug 的触发条件很具体：CoW 模式 + MERGE 只含 matched 子句（无 not-matched insert）+ 目标表有可为空的列 + 存在未匹配到源的目标行且该列为 NULL。在这种场景下，未匹配行的 NULL 列会被错误地写成默认值。提交通过把 `readAttrs`（目标行原始属性）纳入 nullable 计算的 outputs 集合来修复——因为 `readAttrs` 反映了目标表真实的列可空性，把它纳入后，任何在目标表中可为空的列在输出 schema 中也会被正确标记为 nullable，从而保证 NULL 值在透传时不被篡改。

值得注意的是，这个 bug 是 Spark 3.4 特有的。Spark 3.3 的 Iceberg 重写路径通过另一种方式（向 matchedActions 追加一个 `TrueLiteral` 条件 + `readAttrs` 输出的 catch-all 动作）来处理未匹配行的透传，`readAttrs` 已经隐式包含在 matchedOutputs 中，所以 nullable 计算天然正确；Spark 3.5 则直接复用 Spark 原生的 `RewriteMergeIntoTable`（Iceberg 不再覆盖），也不存在此问题。本提交只在 v3.4 的 `RewriteMergeIntoTable.scala` 中做了 1 行修复，同时在 v3.3/v3.4/v3.5 三个版本的 `TestMerge.java` 中各新增 3 个回归测试，确保三个版本行为一致且防止未来回归。

## 如何达成设计目的

修复极其精炼：把 v3.4 `RewriteMergeIntoTable.scala` 中 `buildMergeRowsOutput(matchedOutputs, notMatchedOutputs, readAttrs)` 的调用改为 `buildMergeRowsOutput(matchedOutputs, notMatchedOutputs :+ readAttrs, readAttrs)`。`buildMergeRowsOutput` 内部会把 `notMatchedOutputs` 中所有非空的输出序列收集起来，连同 matchedOutputs 一起传给 `buildMergingOutput` 计算 nullable 映射——`buildMergingOutput` 对每个列索引检查"是否存在某个输出在该索引处 nullable"，只要有一个 nullable 就把输出属性标记为 nullable。把 `readAttrs`（目标行原始属性序列，`Attribute` 是 `Expression` 的子类型）追加到 `notMatchedOutputs`，就等于让目标表的真实列可空性参与 nullable 计算：目标表中任何可为空的列，其 `readAttrs` 元素就是 nullable 的，从而输出 schema 中对应列也会被正确标记为 nullable。这与 v3.3 把 `readAttrs` 放进 matchedOutputs 的效果等价，只是放置位置不同。

## 修改详情

### spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/RewriteMergeIntoTable.scala

**修改目的**：修复 CoW MERGE 重写中 `MergeRows` 输出 schema 的 nullable 计算遗漏"未匹配目标行透传"路径的 bug。

**工作逻辑**：在 CoW 重写路径（`buildCoWWritePlan`，约 228 行）中，构造 `MergeRows` 算子时：
- 修改前：`output = buildMergeRowsOutput(matchedOutputs, notMatchedOutputs, readAttrs)`
- 修改后：`output = buildMergeRowsOutput(matchedOutputs, notMatchedOutputs :+ readAttrs, readAttrs)`

`buildMergeRowsOutput(matchedOutputs, notMatchedOutputs, attrs)` 的内部逻辑是：`val outputs = matchedOutputs.flatten.filter(_.nonEmpty) ++ notMatchedOutputs.filter(_.nonEmpty)`，然后 `buildMergingOutput(outputs, attrs)` 对每个列索引 `i` 计算 `outputs.exists(output => output(i).nullable)` 作为该列的 nullable。

修改前，`outputs` 只包含 matched/not-matched **action** 的输出表达式。当 MERGE 只有 matched 子句（`notMatchedOutputs` 为空）且 matched action 的 SET 值都是非空 literal 时，所有列的 nullable 都被算成 false，输出 schema 错误地标记为 non-nullable。但 `MergeRows` 设置了 `emitNotMatchedTargetRows = true`，意味着未匹配源的目标行会用 `readAttrs`（目标原始属性）原样透传——这些行的可空列可能持有 NULL，却被 non-nullable schema 篡改为默认值。

修改后，`notMatchedOutputs :+ readAttrs` 把目标行原始属性序列作为一条额外的"输出"纳入计算。由于 `readAttrs` 中可为空的列对应的 `Attribute` 本身就是 nullable 的，`outputs.exists(...)` 会对这些列返回 true，输出 schema 正确标记为 nullable。这样 writer 在处理未匹配行的 NULL 值时会保留 NULL 而非写成默认值。注意 `readAttrs` 作为 `Seq[Attribute]` 被追加到 `Seq[Seq[Expression]]` 中（`Attribute` 实现 `Expression`），类型上合法。

### spark/v3.3/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMerge.java

**修改目的**：为 Spark 3.3 添加回归测试，验证未匹配行含 NULL 列时 MERGE 的 UPDATE/DELETE 行为正确（v3.3 通过 catch-all matched action 已正确处理，测试用于防回归）。

**工作逻辑**：新增 3 个测试方法（+72 行），均使用相同的目标表数据 `{id:1, value:2}, {id:6, value:null}` 和源 `{id:1, value:100}`：
- `testMergeWithOnlyUpdateNullUnmatchedValues`：MERGE ON t.id==s.id WHEN MATCHED THEN UPDATE SET id=123, value=456。预期未匹配行 `{6, null}` 原样保留（value 仍为 NULL），匹配行更新为 `{123, 456}`。若 bug 存在，未匹配行的 value NULL 会被写成 0。
- `testMergeWithOnlyUpdateSingleFieldNullUnmatchedValues`：UPDATE SET id=123（只更新 id，value 保留原值）。预期 `{6, null}` 保留，`{123, 2}`（value 取自原行）。
- `testMergeWithOnlyDeleteNullUnmatchedValues`：WHEN MATCHED THEN DELETE。预期匹配行被删，未匹配行 `{6, null}` 保留。

### spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMerge.java

**修改目的**：为 Spark 3.4 添加相同的 3 个回归测试，直接覆盖被修复的 bug 路径。内容与 v3.3 完全一致（+72 行）。

### spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMerge.java

**修改目的**：为 Spark 3.5 添加相同的 3 个回归测试。v3.5 复用 Spark 原生 MERGE 重写，无 Iceberg 自定义 `RewriteMergeIntoTable.scala`，测试用于验证原生路径同样正确处理 NULL 未匹配行。内容与 v3.3/v3.4 完全一致（+72 行）。

## 小结

这是一个典型的"nullable 计算遗漏分支"导致的静默数据损坏 bug。根因在于 Spark 3.4 的 CoW MERGE 重写用 `emitNotMatchedTargetRows = true` 让 `MergeRows` 算子直接透传未匹配目标行，但计算输出 schema 的 nullable 时却没有把这条透传路径（`readAttrs`）纳入考量，导致可为空的列被误标为 non-nullable，writer 据此把 NULL 篡改为默认值。修复只需 1 行——把 `readAttrs` 追加到 `notMatchedOutputs` 参与 nullable 计算，体现了对 `buildMergingOutput` 语义的精准理解。跨版本对比很有启发：v3.3 用 catch-all matched action 隐式规避了此问题，v3.5 用 Spark 原生重写也不受影响，只有 v3.4 的 Iceberg 自定义重写踩中了这个坑，说明不同版本对同一逻辑的不同实现路径会引入不同的 nullable 推断缺陷。3 个回归测试跨三个版本同步添加，既验证修复又防止未来重构回归，是负责任的测试实践。
