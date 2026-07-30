# 提交 3224：Core: Prevent exceptions in ExpressionUtil for unpartitioned tables (#15243)

## 提交信息

- **序号**：3224 / 4088
- **哈希**：865b60d473d4a43c5dc623fb84fd542fc556fdd9
- **短哈希**：865b60d47
- **日期**：2026-02-09
- **作者**：Anton Okolnychyi
- **提交说明**：Core: Prevent exceptions in ExpressionUtil for unpartitioned tables (#15243)
- **PR/Issue**：#15243

## 总体目的

`ExpressionUtil.selectsPartitions(...)` 用于判断一个（未绑定的）过滤表达式是否"选中完整分区"，Spark 4.1 的 `SparkScanBuilder` 据此决定某个谓词是可以完全下推到 Iceberg 侧评估（命中整分区，可省略 post-scan 过滤），还是只能作为 post-scan 过滤保留。该方法有两个重载：按 `PartitionSpec` 判断的版本，以及按 `Table` 判断的版本——后者遍历 `table.specs()` 的所有 spec，用 `allMatch` 判断表达式是否对该表的**每一个** spec 都选中完整分区。

问题出在未分区（unpartitioned）的 spec 上。按 `PartitionSpec` 判断的版本原本直接进入投影+等价比较流程：调用 `Projections.inclusive(spec).project(expr)` 与 `Projections.strict(spec).project(expr)`，再以 `equivalent(... spec.partitionType() ...)` 比较。而 `BaseProjectionEvaluator.predicate(UnboundPredicate)` 内部会执行 `pred.bind(spec.schema().asStruct(), caseSensitive)`：对未分区 spec（`PartitionSpec.unpartitioned()`，其 schema 为空的 `new Schema()`），把引用了某个字段（如 `lessThan("id", 1L)`）的谓词绑定到空 schema 的 struct 上时，`Binder` 找不到该字段，抛出 `ValidationException`。因此只要 `selectsPartitions` 被作用于未分区 spec（且表达式引用了非空字段），就会抛异常。

此前 `SparkScanBuilder` 用一个本地 `unpartitioned()` 守卫规避了"整张表全部 spec 都未分区"的情形（此时直接走 post-scan 过滤、不调用 `selectsPartitions`）；但该守卫对**混合 spec** 的表（即 specs 中既有未分区 spec、也有分区 spec）无能为力——`unpartitioned()` 返回 false，于是调用 `selectsPartitions(expr, table, ...)`，其 `allMatch` 在遍历到未分区 spec 时即触发上述绑定异常。本提交把"未分区即返回 false"的判断下沉到 `ExpressionUtil.selectsPartitions(Expression, PartitionSpec, boolean)` 内部，从根上同时覆盖全未分区与混合 spec 两种情况，既消除异常、也简化了 `SparkScanBuilder` 的调用方（可移除本地 `unpartitioned()` 守卫）。返回 `false` 在语义上也是正确的：未分区 spec 没有分区边界，无从"选中完整分区"。

## 如何达成设计目的

整体思路是"把未分区判断下沉到工具方法内部，做最早短路"。在 `ExpressionUtil` 的 `PartitionSpec` 重载入口处加 `if (spec.isUnpartitioned()) return false;`，在任何投影/绑定之前返回，避免对空 schema 绑定谓词而抛异常；该重载被 `Table` 重载的 `allMatch` 调用，因此混合 spec 表也会被正确处理（未分区 spec 短路返回 false，`allMatch` 随之返回 false）。随后在 `SparkScanBuilder` 中删除已冗余的 `unpartitioned()` 守卫及其私有方法，让 `selectsPartitions` 单点负责未分区语义，并补一个覆盖混合 spec 表的单元测试。共改动三个文件：`ExpressionUtil.java`（修复点）、`SparkScanBuilder.java`（消费方简化）、`TestExpressionUtil.java`（回归测试）。

## 修改详情

### `api/src/main/java/org/apache/iceberg/expressions/ExpressionUtil.java` (+4/-0 lines)

**修改目的**：在 `selectsPartitions(Expression, PartitionSpec, boolean)` 入口对未分区 spec 短路返回 false，避免后续投影/绑定抛异常。

**工作逻辑**：
在该方法体最前面新增：

```java
if (spec.isUnpartitioned()) {
  return false;
}
```

随后的原有逻辑不变——对 `Projections.inclusive(spec, caseSensitive).project(expr)` 与 `Projections.strict(spec, caseSensitive).project(expr)` 的结果调用 `equivalent(... spec.partitionType() caseSensitive)` 比较：若 inclusive 与 strict 投影等价，则说明表达式选中完整分区，返回 true。

短路之所以能消除异常：原流程中 `BaseProjectionEvaluator.predicate(UnboundPredicate)` 会执行 `pred.bind(spec.schema().asStruct(), caseSensitive)`，而 `PartitionSpec.unpartitioned()` 的 schema 为空 `new Schema()`，把引用了字段的谓词绑定到空 struct 会抛 `ValidationException`。短路在前即避免进入该路径。语义上，未分区 spec 无分区边界，`selectsPartitions` 应回 false（与 `SparkScanBuilder` 期望"未分区则保留为 post-scan 过滤"一致）。

### `api/src/test/java/org/apache/iceberg/expressions/TestExpressionUtil.java` (+19/-0 lines)

**修改目的**：新增覆盖混合 spec 表（含未分区 spec）的回归测试，确保不再抛异常且返回 false。

**工作逻辑**：
新增 `testSelectsPartitionsWithUnpartitionedTable()`：用 Mockito 构造一个 `Table` mock，其 `specs()` 返回 `ImmutableMap.of(0, PartitionSpec.unpartitioned(), 1, PartitionSpec.builderFor(SCHEMA).identity("val").build())`——即一个未分区 spec 与一个 `identity("val")` 分区 spec 并存的混合表。断言 `ExpressionUtil.selectsPartitions(Expressions.lessThan("id", 1L), table, true)` 为 `false`，描述为 "Should return false for unpartitioned table (no partition boundaries to select)"。该测试对应修复前会抛 `ValidationException` 的混合 spec 场景。同时新增 `mockito` 的 `mock`/`when` 与 `Table` 的静态 import 以构造 mock 表。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java` (+1/-8 lines)

**修改目的**：移除已冗余的本地 `unpartitioned()` 守卫与方法，统一由 `ExpressionUtil.selectsPartitions` 负责未分区语义。

**工作逻辑**：
原 `prune()`（推断 post-scan/pushed 谓词）中的判断为：

```java
if (expr == null
    || unpartitioned()
    || !ExpressionUtil.selectsPartitions(expr, table, caseSensitive)) {
  postScanFilters.add(predicate);
} else {
  LOG.info("Evaluating completely on Iceberg side: {}", predicate);
  ...
}
```

现简化为：

```java
if (expr == null || !ExpressionUtil.selectsPartitions(expr, table, caseSensitive)) {
  postScanFilters.add(predicate);
} else {
  ...
}
```

由于 `selectsPartitions` 现对未分区 spec 返回 false（进而 `allMatch` 对含未分区 spec 的表返回 false），原 `unpartitioned()` 守卫产生的短路效果已被等价覆盖：未分区表会得到 `!selectsPartitions(...) == true`，同样进入 `postScanFilters`，行为一致。同时删除私有方法 `private boolean unpartitioned() { return table.specs().values().stream().noneMatch(PartitionSpec::isPartitioned); }` 及不再使用的 `org.apache.iceberg.PartitionSpec` import。注意原 `unpartitioned()` 仅在"全部 spec 都未分区"时返回 true，无法保护混合 spec 表；下沉后的新逻辑则同时覆盖混合 spec 表，是行为上的缺陷修复（混合 spec 表此前会抛异常，现安全返回 post-scan 过滤路径）。

## 总结

本提交修复了 `ExpressionUtil.selectsPartitions` 在作用到未分区 spec 时因谓词绑定到空 schema 而抛 `ValidationException` 的缺陷（对混合 spec 的表尤其致命，原有 `SparkScanBuilder.unpartitioned()` 守卫无法覆盖），方法是在 `PartitionSpec` 重载入口短路返回 `false`——语义正确且避免绑定；并据此简化 `SparkScanBuilder` 调用方（移除冗余守卫与私有方法、清理 import），补一个混合 spec 表的回归测试。核心价值是消除未分区/混合 spec 表上的运行时异常，使"完全在 Iceberg 侧评估"的下推判断对未分区场景安全且单一来源化。
