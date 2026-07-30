# 提交 3143：API: Optimize NOT IN and != predicates for single-value partition manifests (#15064)

## 提交信息

- **序号**：3143 / 4088
- **哈希**：157b2488ebfc138370bf482458df96bcdc7245dd
- **短哈希**：157b2488e
- **日期**：2026-01-22
- **作者**：Joy Haldar
- **提交说明**：API: Optimize NOT IN and != predicates for single-value partition manifests (#15064)
- **PR/Issue**：#15064

## 总体目的

`ManifestEvaluator` 负责根据 manifest 文件中存储的分区字段统计（`lowerBound`/`upperBound`/`containsNull`/`containsNaN`）判断该 manifest 是否可能包含匹配查询谓词的行，从而在计划阶段跳过不相关的 manifest 文件（裁剪）。对于 `!=`（`notEq`）和 `NOT IN`（`notIn`）这类否定谓词，此前的实现一律返回 `ROWS_MIGHT_MATCH`（保守地保留 manifest），理由是 bounds 不一定是真正的 min/max：例如 `notEq(col, X)` 且 bounds 为 `(X, Y)` 时，不能保证 `X` 真的出现在 col 中，因此无法裁剪。

然而在分区表中，一个 manifest 文件往往只对应单一分区值——即该分区字段在该 manifest 的所有数据行中取同一个值，表现为 `lowerBound == upperBound` 且无 null、无 NaN。在这种“单值”情形下，bounds 实际上就是该唯一值的精确表达，否定谓词可以被安全求值：若该唯一值等于 `notEq` 的字面量、或属于 `notIn` 的排除集合，则 manifest 中所有行都不可能满足谓词，可以裁剪。本提交正是针对这一常见场景做优化，让 `notEq`/`notIn` 在单值 manifest 上返回 `ROWS_CANNOT_MATCH`，减少不必要的 manifest 读取，提升分区表上否定谓词查询的计划效率。改动仅限 `api` 模块的 `ManifestEvaluator` 与其测试，不改变任何对外语义（仅在可证明无匹配时更激进地裁剪，否则维持原保守行为）。

## 如何达成设计目的

新增一个私有泛型方法 `uniqueValue(BoundReference<T>)`，集中判定某分区字段是否为“单值”：无 null、浮点类型无 NaN（或 NaN 信息未知时也视为不安全）、`lowerBound`/`upperBound` 均存在且相等，满足时返回该唯一值，否则返回 null。`notEq` 与 `notIn` 在原有保守返回之前，先调用 `uniqueValue`：若得到非 null 单值且与谓词字面量满足“必然无匹配”的条件（`notEq` 为单值等于字面量、`notIn` 为单值属于排除集合），则直接返回 `ROWS_CANNOT_MATCH`。测试侧扩展 schema 与 manifest 文件统计，新增覆盖普通多值、单值字符串、含 null、含 NaN、NaN 未知、无 NaN 单值浮点等多种情形的用例。

## 修改详情

### `api/src/main/java/org/apache/iceberg/expressions/ManifestEvaluator.java` (+47/-0 lines)

**修改目的**：让 `notEq`/`notIn` 在单值 manifest 上安全裁剪。

**工作逻辑**：
- `notEq(BoundReference<T>, Literal<T>)`：在原有注释“bounds 不一定是 min/max，无法用 bounds 回答”之后，新增优化逻辑：`T value = uniqueValue(ref);` 若 `value != null && lit.comparator().compare(value, lit.value()) == 0` 则返回 `ROWS_CANNOT_MATCH`。语义：当 manifest 中该分区字段只有一个值且无 null/NaN，且该值等于 `!=` 的字面量，则所有行都等于该字面量，没有任何行满足 `col != X`，可裁剪。
- `notIn(BoundReference<T>, Set<T>)`：同样调用 `uniqueValue(ref)`，若 `value != null && literalSet.contains(value)` 则返回 `ROWS_CANNOT_MATCH`。语义：单值属于排除集合时，所有行都被排除，可裁剪。
- 新增私有方法 `uniqueValue(BoundReference<T>)`：
  - `int pos = Accessors.toPosition(ref.accessor()); PartitionFieldSummary fieldStats = stats.get(pos);` 定位字段统计。
  - 若 `fieldStats.containsNull()` 返回 null——存在 null 行时，null 行满足 `!=`/`NOT IN`（SQL 语义下 null != X 视为 unknown，但 manifest 裁剪为安全起见保留），不能裁剪。
  - 对 `FLOAT`/`DOUBLE` 类型：若 `fieldStats.containsNaN() == null || fieldStats.containsNaN()` 返回 null——NaN 信息未知或存在 NaN 时，NaN 行同样可能满足否定谓词，不能裁剪。
  - 取 `lowerBound`/`upperBound`，任一为 null 返回 null；用 `Conversions.fromByteBuffer` 还原为 `lower`/`upper`，若 `ref.comparator().compare(lower, upper) != 0` 返回 null（多值范围）；否则返回 `lower` 作为唯一值。
  - 该方法严格地把“单值且无 null/NaN”这一可证明安全的情形识别出来，其余情形仍走原保守路径。

### `api/src/test/java/org/apache/iceberg/expressions/TestInclusiveManifestEvaluator.java` (+104/-2 lines)

**修改目的**：覆盖 `notEq`/`notIn` 在单值及各种 NaN/null 情形下的裁剪行为。

**工作逻辑**：
- 扩展测试 `SCHEMA` 与 `SPEC`，新增 3 个分区字段：`single_value_with_nan`(id=16, Float)、`single_value_nan_unknown`(id=17, Float)、`single_value_no_nan`(id=18, Float)。
- 扩展测试 `FILE` 的字段统计，为三个新字段分别构造：`containsNull=false, containsNaN=true, lower=upper=5.0F`（含 NaN 单值）、`containsNull=false, containsNaN=null, lower=upper=5.0F`（NaN 未知单值）、`containsNull=false, containsNaN=false, lower=upper=5.0F`（无 NaN 单值）。这覆盖了 `uniqueValue` 对浮点 NaN 的三种分支。
- 新增 `testNotEqWithSingleValue`：
  - 多值列 `no_nulls` 上 `notEqual("no_nulls","a")` 仍 `shouldRead=true`（无法裁剪）。
  - 单值字符串列 `no_nulls_same_value_a`：`notEqual(...,"a")` 返回 false（单值等于字面量，裁剪）；`notEqual(...,"b")` 返回 true（单值不等于字面量，保留）。
  - 含 null 单值列 `all_same_value_or_null`：`notEqual(...,"a")` 返回 true（有 null，保留）。
  - 浮点单值含 NaN `single_value_with_nan`：`notEqual(...,5.0F)` 返回 true（有 NaN，保留）。
  - NaN 未知 `single_value_nan_unknown`：返回 true（NaN 未知，保留）。
  - 无 NaN 单值 `single_value_no_nan`：`notEqual(...,5.0F)` 返回 false（单值等于字面量且无 NaN，裁剪）。
- 新增 `testNotInWithSingleValue`：对称覆盖 `notIn`，多值保留、单值在排除集合内裁剪、单值不在集合内保留、含 null/NaN/NaN 未知保留、无 NaN 单值在集合内裁剪。

## 总结

该提交为 `ManifestEvaluator` 的 `notEq`/`notIn` 谓词引入了基于“单值 manifest”的安全裁剪优化：通过新增 `uniqueValue` 方法精确识别分区字段无 null、无 NaN 且 lower==upper 的单值情形，使否定谓词在该情形下能正确返回 `ROWS_CANNOT_MATCH`，从而跳过不必要的 manifest 读取，提升分区表上 `!=`/`NOT IN` 查询的计划效率，同时通过详尽测试覆盖了 null、NaN 已知/未知/存在等边界条件，保证优化不破坏正确性。
