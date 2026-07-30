# 提交 0585：Spark 3.5: Fix system function pushdown in CoW row-level commands

## 提交信息

- **序号**：0585 / 4088
- **哈希**：0432048515904691d2dfbdb4a01f89883f0a034f
- **短哈希**：043204851
- **日期**：2024-03-11（Mon Mar 11 16:52:48 2024 -0700）
- **作者**：Anton Okolnychyi <aokolnychyi@apple.com>
- **提交说明**：Spark 3.5: Fix system function pushdown in CoW row-level commands (#9873)
- **PR/Issue**：#9873

## 总体目的

### 背景：Iceberg 系统函数与 StaticInvoke

Iceberg 在 Spark 中暴露了一组"系统函数"（system functions），让用户能在 SQL 谓词里直接使用 Iceberg 的 transform，例如：

- `system.bucket(4, dep) = 2`
- `system.bucket(4, dep) IN (2, 3)`
- `system.years(ts) > 30`
- `system.truncate(1, dep) = 'i'`
- `system.days(ts) <= date('2000-01-03')`

这些 transform 与 Iceberg 表的分区规约同构。当这类谓词出现在扫描查询的 `Filter` 中时，Iceberg 数据源（`IcebergScan` / `BatchScan`）能把谓词下推到文件层，做文件级裁剪（file pruning）——只读取匹配的分区文件，跳过不相关文件，极大提升查询性能。

但在 Spark 的分析阶段，这些系统函数会被解析为 `StaticInvoke` 表达式（直接调用 Iceberg 函数类的静态 Java 方法，例如 `BucketFunction.BucketBase.apply(...)`）。`StaticInvoke` 是 Spark 内部的"调用静态方法"表达式，数据源无法识别它为 transform 语义，因此**不能下推**。为了让下推生效，Iceberg 在 Spark 3.5 扩展中提供了一个 Catalyst 优化规则 `ReplaceStaticInvoke`，把满足条件的 `StaticInvoke` 替换为 `ApplyFunctionExpression`（Spark v2 函数调用形式），后者能被数据源识别并下推。

### Bug：CoW 行级命令中替换规则未触发

修复前的 `ReplaceStaticInvoke` 规则只匹配 `Filter` 节点（且只处理 `BinaryComparison` 谓词），具体逻辑如下：

```scala
plan.transformWithPruning(_.containsAllPatterns(BINARY_COMPARISON, FILTER)) {
  case filter @ Filter(condition, _) =>
    val newCondition = condition.transformWithPruning(_.containsPattern(BINARY_COMPARISON)) {
      case c @ BinaryComparison(left: StaticInvoke, right) if canReplace(left) && right.foldable =>
        c.withNewChildren(Seq(replaceStaticInvoke(left), right))
      case c @ BinaryComparison(left, right: StaticInvoke) if canReplace(right) && left.foldable =>
        c.withNewChildren(Seq(left, replaceStaticInvoke(right)))
    }
    ...
}
```

Spark 3.5 的行级命令（DELETE / UPDATE / MERGE）在 **Copy-on-Write (CoW)** 模式下生成的逻辑计划与普通扫描不同：

- CoW DELETE/UPDATE 会生成 `ReplaceData` 节点，其 `condition` 字段承载行级谓词（而非 `Filter`）；
- MERGE 还会包含 `Join` 节点，其 `condition` 可能携带系统函数谓词（例如 `ON t.id == s.id AND system.bucket(4, t.dep) IN (2, 3)`）。

由于旧规则只处理 `Filter`，**CoW 行级命令中的 `ReplaceData` 和 `Join` 节点里的 `StaticInvoke` 不会被替换**，导致：

1. 谓词无法下推到数据源，所有分区文件都被扫描（无文件裁剪），性能严重退化；
2. `IN` / `InSet` 形式的谓词（如 `system.bucket(4, dep) IN (2, 3)`）即使在 `Filter` 中也不会被替换，因为旧规则只匹配 `BinaryComparison`。

本提交的目的就是修复这两个缺陷：让 `ReplaceStaticInvoke` 覆盖 `ReplaceData`、`Join`、`Filter` 三类节点，并支持 `In`、`InSet` 谓词形式，使 CoW 行级命令中的系统函数谓词也能正确替换为 `ApplyFunctionExpression` 并下推到数据源。

## 如何达成设计目的

整体设计包含四个部分：

### 1. 扩展 `ReplaceStaticInvoke` 规则的匹配范围

- **节点层面**：从只匹配 `Filter` 扩展到匹配 `ReplaceData`、`Join`、`Filter` 三类。通过 `containsAnyPattern(COMMAND, FILTER, JOIN)` 做剪枝（`ReplaceData` 是 `COMMAND` 类型，`Join` 是 `JOIN` 类型，`Filter` 是 `FILTER` 类型），避免不必要的全树遍历。
- **表达式层面**：从只匹配 `BinaryComparison` 扩展到匹配 `BinaryComparison`、`In`、`InSet` 三类，通过 `containsAnyPattern(BINARY_COMPARISON, IN, INSET)` 做剪枝。
- **重构**：抽取 `replaceStaticInvoke(node, condition, copy)` 辅助方法，统一三类节点的替换逻辑，避免重复代码。

### 2. 引入 `BaseScalarFunction` 抽象基类

Spark 的 `ScalarFunction` 接口没有定义 `equals` / `hashCode`，默认使用对象身份比较。当 `StaticInvoke` 被替换为 `ApplyFunctionExpression` 后，Spark 在优化过程中可能需要比较两个函数实例是否相等（例如表达式去重、规则匹配）。如果函数实例不相等，可能导致优化规则无法匹配或产生不一致的计划。

新增 `BaseScalarFunction<R> implements ScalarFunction<R>` 抽象类，基于 `canonicalName()` 提供 `equals` / `hashCode`：

```java
@Override
public int hashCode() {
  return canonicalName().hashCode();
}

@Override
public boolean equals(Object other) {
  if (this == other) {
    return true;
  } else if (!(other instanceof ScalarFunction)) {
    return false;
  }
  ScalarFunction<?> that = (ScalarFunction<?>) other;
  return canonicalName().equals(that.canonicalName());
}
```

所有 Iceberg 标量函数实现类改为继承 `BaseScalarFunction` 而非直接 `implements ScalarFunction`，统一获得正确的相等性语义。

### 3. 新增测试辅助方法 `SparkPlanUtil.collectExprs`

提供从 `SparkPlan` 中收集满足谓词的 `Expression` 的工具方法，用于测试中验证 `StaticInvoke` / `ApplyFunctionExpression` 是否出现在执行计划中。

### 4. 新增端到端测试 `TestSystemFunctionPushDownInRowLevelOperations`

覆盖 DELETE / UPDATE / MERGE 三种行级命令 × CoW / MoR 两种模式 × bucket / years / months / days / hours / truncate 六种 transform × `IN` / 等值 / 范围多种谓词形式，全面验证修复效果。

## 修改详情

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/optimizer/ReplaceStaticInvoke.scala`

**修改目的**：扩展优化规则的匹配范围，使其覆盖 `ReplaceData`、`Join` 节点和 `In`、`InSet` 谓词形式，修复 CoW 行级命令中系统函数无法下推的 bug。

**工作逻辑**：

#### 新的 `apply` 方法

```scala
override def apply(plan: LogicalPlan): LogicalPlan =
  plan.transformWithPruning(_.containsAnyPattern(COMMAND, FILTER, JOIN)) {
    case replace @ ReplaceData(_, cond, _, _, _, _) =>
      replaceStaticInvoke(replace, cond, newCond => replace.copy(condition = newCond))

    case join @ Join(_, _, _, Some(cond), _) =>
      replaceStaticInvoke(join, cond, newCond => join.copy(condition = Some(newCond)))

    case filter @ Filter(cond, _) =>
      replaceStaticInvoke(filter, cond, newCond => filter.copy(condition = newCond))
  }
```

关键设计点：

1. **`containsAnyPattern(COMMAND, FILTER, JOIN)`**：剪枝条件从"同时包含 BINARY_COMPARISON 和 FILTER"改为"包含 COMMAND、FILTER、JOIN 中任意一个"。`ReplaceData` 属于 `COMMAND` 模式，`Join` 属于 `JOIN` 模式。这样规则能在 `ReplaceData`、`Join`、`Filter` 三类节点上触发。
2. **`ReplaceData(_, cond, _, _, _, _)`**：匹配 CoW 行级命令的 `ReplaceData` 节点，提取其 `condition` 字段（行级谓词），替换后用 `replace.copy(condition = newCond)` 生成新节点。
3. **`Join(_, _, _, Some(cond), _)`**：匹配带条件的 `Join` 节点（MERGE 命令中常见），提取 `Some(cond)` 中的条件，替换后用 `join.copy(condition = Some(newCond))` 生成新节点。
4. **`Filter(cond, _)`**：保留原有的 `Filter` 匹配，与 `ReplaceData`/`Join` 走同一条替换路径。

#### 抽取的辅助方法

```scala
private def replaceStaticInvoke[T <: LogicalPlan](
    node: T,
    condition: Expression,
    copy: Expression => T): T = {
  val newCondition = replaceStaticInvoke(condition)
  if (newCondition fastEquals condition) node else copy(newCondition)
}

private def replaceStaticInvoke(condition: Expression): Expression = {
  condition.transformWithPruning(_.containsAnyPattern(BINARY_COMPARISON, IN, INSET)) {
    case in @ In(value: StaticInvoke, _) if canReplace(value) =>
      in.copy(value = replaceStaticInvoke(value))

    case in @ InSet(value: StaticInvoke, _) if canReplace(value) =>
      in.copy(child = replaceStaticInvoke(value))

    case c @ BinaryComparison(left: StaticInvoke, right) if canReplace(left) && right.foldable =>
      c.withNewChildren(Seq(replaceStaticInvoke(left), right))

    case c @ BinaryComparison(left, right: StaticInvoke) if canReplace(right) && left.foldable =>
      c.withNewChildren(Seq(left, replaceStaticInvoke(right)))
  }
}
```

- **`replaceStaticInvoke[T](node, condition, copy)`**：泛型方法，接受一个节点、其条件表达式、以及一个"用新条件复制节点"的闭包。先对条件做替换，若结果与原条件 `fastEquals` 则返回原节点（避免不必要的树重建），否则用闭包生成新节点。这一抽取消除了三类节点各自的重复逻辑。
- **`replaceStaticInvoke(condition: Expression)`**：对条件表达式做递归变换，匹配四类模式：
  - `In(value: StaticInvoke, _)`：`IN (v1, v2, ...)` 谓词中左侧是 `StaticInvoke`，替换为 `ApplyFunctionExpression`；
  - `InSet(value: StaticInvoke, _)`：`IN (SET)` 谓词（Spark 在值列表较大时优化为 `InSet`）中左侧是 `StaticInvoke`，替换之；
  - `BinaryComparison(left: StaticInvoke, right)` 且 `right.foldable`：等值/范围比较左侧是 `StaticInvoke`，右侧是常量，替换左侧；
  - `BinaryComparison(left, right: StaticInvoke)` 且 `left.foldable`：对称情况，替换右侧。

`canReplace(invoke)` 检查 `StaticInvoke` 是否是 Iceberg 的 magic method 调用且非常量（`invoke.functionName == ScalarFunction.MAGIC_METHOD_NAME && !invoke.foldable`），避免误替换其他静态调用。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/functions/BaseScalarFunction.java`（新增）

**修改目的**：为所有 Iceberg 标量函数提供统一的 `equals` / `hashCode` 实现，基于 `canonicalName()` 判等，确保 Spark 优化器在比较函数实例时行为正确。

**工作逻辑**：

```java
abstract class BaseScalarFunction<R> implements ScalarFunction<R> {
  @Override
  public int hashCode() {
    return canonicalName().hashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    } else if (!(other instanceof ScalarFunction)) {
      return false;
    }
    ScalarFunction<?> that = (ScalarFunction<?>) other;
    return canonicalName().equals(that.canonicalName());
  }
}
```

- `hashCode` 直接用 `canonicalName()` 的哈希，保证两个 `equals` 的对象有相同哈希；
- `equals` 先做身份比较（`this == other`），再检查类型（必须是 `ScalarFunction`），最后比较 `canonicalName()`。`canonicalName()` 是 `ScalarFunction` 接口定义的方法，返回函数的规范名（如 `org.apache.iceberg.spark.functions.bucket`），同一类函数的规范名相同；
- 设为包级私有抽象类（`abstract class`，无 `public`），只在 `org.apache.iceberg.spark.functions` 包内被各函数实现继承。

### 七个函数实现类改为继承 `BaseScalarFunction`

**修改目的**：让所有 Iceberg 标量函数获得正确的 `equals` / `hashCode`。

**改动列表**（每个文件都是把 `implements ScalarFunction<X>` 改为 `extends BaseScalarFunction<X>`，并移除不再需要的 `ScalarFunction` import）：

- `BucketFunction.java`：`BucketBase` 类
- `DaysFunction.java`：`BaseToDaysFunction` 类
- `HoursFunction.java`：`TimestampToHoursFunction` 和 `TimestampNtzToHoursFunction` 两个类
- `IcebergVersionFunction.java`：`IcebergVersionFunctionImpl` 类
- `MonthsFunction.java`：`BaseToMonthsFunction` 类
- `TruncateFunction.java`：`TruncateBase<T>` 类
- `YearsFunction.java`：`BaseToYearsFunction` 类

例如 `BucketFunction` 的改动：

```diff
-import org.apache.spark.sql.connector.catalog.functions.ScalarFunction;
...
-  public abstract static class BucketBase implements ScalarFunction<Integer> {
+  public abstract static class BucketBase extends BaseScalarFunction<Integer> {
```

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/SparkPlanUtil.java`

**修改目的**：新增 `collectExprs` 测试辅助方法，用于从执行计划中收集满足条件的表达式，供测试断言 `StaticInvoke` / `ApplyFunctionExpression` 是否存在。

**工作逻辑**：

```java
public static List<Expression> collectExprs(
    SparkPlan sparkPlan, Predicate<Expression> predicate) {
  Seq<List<Expression>> seq =
      SPARK_HELPER.collect(
          sparkPlan,
          new PartialFunction<SparkPlan, List<Expression>>() {
            @Override
            public List<Expression> apply(SparkPlan plan) {
              List<Expression> exprs = Lists.newArrayList();
              for (Expression expr : toJavaList(plan.expressions())) {
                exprs.addAll(collectExprs(expr, predicate));
              }
              return exprs;
            }
            @Override
            public boolean isDefinedAt(SparkPlan plan) {
              return true;
            }
          });
  return toJavaList(seq).stream().flatMap(Collection::stream).collect(Collectors.toList());
}

private static List<Expression> collectExprs(
    Expression expression, Predicate<Expression> predicate) {
  Seq<Expression> seq =
      expression.collect(
          new PartialFunction<Expression, Expression>() {
            @Override
            public Expression apply(Expression expr) {
              return expr;
            }
            @Override
            public boolean isDefinedAt(Expression expr) {
              return predicate.test(expr);
            }
          });
  return toJavaList(seq);
}
```

- 外层 `collectExprs(SparkPlan, Predicate)`：用 Spark 的 `AdaptiveSparkPlanHelper.collect` 遍历执行计划的所有节点，对每个节点取出其 `expressions()`（节点直接持有的表达式列表），再对每个表达式调用内层方法收集匹配项，最终展平为单个 List。
- 内层 `collectExprs(Expression, Predicate)`：用 Spark `Expression.collect(PartialFunction)` 递归遍历表达式树，收集满足谓词的子表达式。
- 测试中用 `expr -> expr instanceof StaticInvoke || expr instanceof ApplyFunctionExpression` 作为谓词，收集所有函数调用形式的表达式。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestSystemFunctionPushDownInRowLevelOperations.java`（新增）

**修改目的**：端到端验证修复效果——CoW 行级命令中的系统函数谓词能正确下推到数据源，且 `StaticInvoke` 被替换为 `ApplyFunctionExpression`。

**工作逻辑**：

测试类覆盖矩阵：
- **行级命令**：DELETE、UPDATE、MERGE
- **写入模式**：CoW（`COPY_ON_WRITE`）、MoR（`MERGE_ON_READ`）
- **transform**：`bucket(4, dep)`、`years(ts)`、`months(ts)`、`days(ts)`、`hours(ts)`、`truncate(1, dep)`
- **谓词形式**：`IN (2, 3)`、`= 2`、`> 30`、`<= 30`、`<= date(...)`、`= 'i'` 等

核心验证方法 `checkDelete` / `checkUpdate` / `checkMerge` 的设计：

1. **`findIrrelevantFileLocations(cond)`**：执行 `SELECT filePath FROM table WHERE NOT cond`，找出**不匹配**谓词的文件路径列表。这些文件在谓词正确下推时不应被扫描。

2. **`withUnavailableLocations(irrelevantLocations, () -> { ... })`**：把这些"不相关"文件标记为不可访问（模拟文件级裁剪验证）。如果谓词下推成功，只有匹配文件被扫描，命令正常完成；如果下推失败，所有文件都被扫描，访问不可用文件会抛异常导致测试失败。

3. **`executeAndCollectFunctionCalls(query, args)`**：执行行级命令，从执行计划中收集 `StaticInvoke` 和 `ApplyFunctionExpression` 实例：

```java
private List<Expression> executeAndCollectFunctionCalls(String query, Object... args) {
  CommandResultExec command = (CommandResultExec) executeAndKeepPlan(query, args);
  V2TableWriteExec write = (V2TableWriteExec) command.commandPhysicalPlan();
  return SparkPlanUtil.collectExprs(
      write.query(),
      expr -> expr instanceof StaticInvoke || expr instanceof ApplyFunctionExpression);
}
```

4. **断言预期调用数**：

| 命令 | CoW | MoR |
|---|---|---|
| DELETE | 1 个调用 | 0 个调用 |
| UPDATE | 2 个调用 | 0 个调用 |
| MERGE | 0 个调用 | 0 个调用 |

含义：
- **MoR 模式（DELETE/UPDATE/MERGE 均为 0）**：谓词完全下推到数据源，Spark 执行计划中不残留任何函数调用——文件级裁剪生效，这是修复后的正确行为。
- **CoW DELETE（1 个调用）和 CoW UPDATE（2 个调用）**：谓词虽然下推到数据源做文件裁剪（所以 `withUnavailableLocations` 不报错），但 CoW 计划阶段不会优化掉 post-scan filter 中的函数调用，因此执行计划中仍残留 1 或 2 个 `ApplyFunctionExpression`（注意：是 `ApplyFunctionExpression` 而非 `StaticInvoke`，说明替换已生效）。注释明确说明："CoW planning currently does not optimize post-scan filters in DELETE/UPDATE"。
- **CoW/MoR MERGE（0 个调用）**：MERGE 的谓词在 join 阶段就完成了下推，执行计划中无残留。

关键在于：**修复前**，CoW 行级命令中的 `StaticInvoke` 不会被替换，谓词无法下推，`withUnavailableLocations` 会导致测试失败（尝试扫描不可用文件）；**修复后**，`StaticInvoke` 被替换为 `ApplyFunctionExpression`，谓词下推生效，文件裁剪正常工作。

5. **数据正确性验证**：每个 check 方法末尾用 `assertEquals` 验证命令执行后的数据状态正确（删除的行不存在、更新的行 salary = -1 等），确保修复不破坏数据语义。

## 小结

- **成效**：修复了 Spark 3.5 下 Iceberg 系统函数（`system.bucket` / `system.years` / `system.months` / `system.days` / `system.hours` / `system.truncate`）在 CoW 行级命令（DELETE / UPDATE / MERGE）中无法下推到数据源的 bug。修复后，包含这些系统函数的谓词能正确触发文件级裁剪，避免扫描不相关分区文件，显著提升 CoW 行级命令的性能。同时补充了对 `IN` / `InSet` 谓词形式的支持（此前只支持 `BinaryComparison`）。
- **影响范围**：改动 11 个文件，485 行新增 / 30 行删除。主要触及 `spark/v3.5` 模块：
  - `spark-extensions` 的 Scala 优化规则 `ReplaceStaticInvoke`（核心修复）；
  - `spark` 的 7 个函数实现类（继承 `BaseScalarFunction`）和新增的 `BaseScalarFunction` 基类；
  - `spark-extensions` 的测试辅助 `SparkPlanUtil` 和新增端到端测试 `TestSystemFunctionPushDownInRowLevelOperations`。
  - 不影响 Spark 3.3 / 3.4 模块（仅限 v3.5）。
- **设计亮点**：
  - 通过扩展 Catalyst 规则的匹配范围（`ReplaceData` / `Join` / `Filter` + `In` / `InSet` / `BinaryComparison`），用最小的改动覆盖所有行级命令场景；
  - 抽取泛型辅助方法 `replaceStaticInvoke[T]` 消除三类节点的重复逻辑；
  - 引入 `BaseScalarFunction` 统一函数相等性语义，从根上避免因 `equals`/`hashCode` 缺失导致的潜在优化器问题；
  - 测试用 `withUnavailableLocations` 机制验证文件级裁剪是否生效，比单纯断言计划结构更贴近实际效果。
- **回迁到 1.4.x 的注意事项**：**强烈建议回迁**，这是一个重要的性能 bug 修复，且仅限 Spark 3.5 模块。回迁时需注意：
  1. **必须整体回迁**所有 11 个文件的改动——`ReplaceStaticInvoke` 的规则扩展依赖 `BaseScalarFunction` 的 `equals`/`hashCode`（规则替换后会生成 `ApplyFunctionExpression`，其内部持有的 `ScalarFunction` 实例需要正确的相等性），不能只回迁部分；
  2. **确认 1.4.x 的 Spark 3.5 模块存在**：1.4.x 分支应已包含 `spark/v3.5/` 目录（Spark 3.5 支持在 1.4.0 已加入），路径一致；
  3. **`ReplaceData` 节点的模式匹配**依赖 Spark 3.5 的 Catalyst API（`ReplaceData` 的 case class 签名），需确认 1.4.x 使用的 Spark 3.5.x 版本中 `ReplaceData` 的字段顺序与 main 分支一致（本提交匹配 `ReplaceData(_, cond, _, _, _, _)`，即第二个字段为 condition）；
  4. **`TreePattern.COMMAND`** 在 Spark 3.5 中可用，无需额外适配；
  5. **测试依赖 `SparkPlanUtil.collectExprs` 和 `withUnavailableLocations`**：前者是本提交新增的，需一并回迁；后者是 ExtensionsTestBase 已有的工具方法，1.4.x 应已具备；
  6. 回迁后应在 1.4.x 的发行说明中标注"backported from 1.5.0, fixes CoW row-level command system function pushdown"。
