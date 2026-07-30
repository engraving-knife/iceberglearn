# 提交 0674：Spark 3.4: Fix system function pushdown in CoW row-level commands (#10119)

## 提交信息
- **序号**：0674 / 4088
- **哈希**：290a6a0c5d6b1fc4cfb738b90d95013315c69605
- **短哈希**：290a6a0c5
- **日期**：2024-04-11 14:04:13 -0700
- **作者**：Anton Okolnychyi
- **提交说明**：Spark 3.4: Fix system function pushdown in CoW row-level commands (#10119)
- **PR/Issue**：#10119

## 总体目的

这个提交修复 Iceberg Spark 3.4 集成中"系统函数（system function）下推"在 CoW（Copy-on-Write）行级命令（DELETE/UPDATE/MERGE）中的缺陷。

**背景**：Iceberg 在 Spark 中提供了一组系统函数，如 `system.bucket(N, col)`、`system.years(ts)`、`system.months(ts)`、`system.days(ts)`、`system.hours(ts)`、`system.truncate(N, col)`、`system.iceberg_version()`。这些函数与表的分区变换（partition transform）对应——例如一个表按 `bucket(4, dep)` 分区时，用户可以在 SQL 中写 `DELETE FROM t WHERE system.bucket(4, dep) IN (2, 3)`，让 Iceberg 通过文件级裁剪（file pruning）只扫描与指定 bucket 对应的数据文件，而无需读取全表。

为了让这种裁剪生效，Spark 的 `StaticInvoke` 表达式（即 `system.xxx()` 的初始表示，对应一个未绑定的静态 Java 方法调用）必须被替换为 Iceberg 的 `ApplyFunctionExpression`（即绑定后的 Iceberg 函数调用），从而使 Iceberg 的 scan builder 能识别条件中的系统函数并据此裁剪文件。这一替换由自定义 Catalyst 规则 `ReplaceStaticInvoke` 完成。

**Bug**：原 `ReplaceStaticInvoke` 规则只覆盖 `Filter` 节点内的 `BinaryComparison`（如 `=`、`<`、`>`）表达式。这有两个盲点：
1. 不处理 `In`/`InSet` 谓词（如 `system.bucket(4, dep) IN (2, 3)`）——`In`/`InSet` 不属于 `BinaryComparison`，导致 `IN` 形式的系统函数条件中的 `StaticInvoke` 不会被替换，函数无法下推到 scan。
2. 不处理 `Join` 节点中的条件——而 CoW 行级命令（DELETE/UPDATE/MERGE）在物理计划层面通过 `ReplaceData` 命令实现，`ReplaceData` 内部包含一个 `Join`（源表与变更集的连接），其连接条件中带系统函数的谓词不会被替换，导致 CoW 模式下系统函数下推失效。

后果是：用户在 CoW 模式下用 `system.bucket(...) IN (...)` 等 IN 谓词作为 DELETE/UPDATE/MERGE 条件时，无法享受文件级裁剪，全表数据被扫描，严重影响性能。

本提交通过扩展 `ReplaceStaticInvoke` 规则覆盖 `Join` 节点条件和 `In`/`InSet` 表达式，并新增 `BaseScalarFunction` 抽象基类为所有 Iceberg 系统函数提供正确的 `equals`/`hashCode`，使 CoW 行级命令下的系统函数下推恢复正常工作。

## 如何达成设计目的

整体设计思路分三层：

1. **扩展 `ReplaceStaticInvoke` 规则覆盖范围**：把原本只针对 `Filter`+`BinaryComparison` 的处理逻辑泛化为可以处理 `Filter` 和 `Join` 节点，并在表达式层面支持 `BinaryComparison`、`In`、`InSet` 三种谓词形式。具体做法是：
   - 把规则顶层的 pruning 模式从 `containsAllPatterns(BINARY_COMPARISON, FILTER)` 改为 `containsAnyPattern(COMMAND, FILTER, JOIN)`，使其能在包含 `ReplaceData` 命令、`Filter` 或 `Join` 的计划上触发。
   - 抽取一个私有方法 `replaceStaticInvoke(condition: Expression)` 处理表达式层面的 `BinaryComparison`/`In`/`InSet` 替换，并在 `Filter` 和 `Join` 两种节点上复用。
   - 抽取一个泛型私有方法 `replaceStaticInvoke[T](node, condition, copy)` 处理"替换后是否需要重建节点"的逻辑（用 `fastEquals` 检测是否真的发生了替换，避免不必要的节点重建）。

2. **新增 `BaseScalarFunction` 抽象基类**：所有 Iceberg Spark 系统函数（`BucketFunction`、`DaysFunction`、`HoursFunction`、`IcebergVersionFunction`、`MonthsFunction`、`TruncateFunction`、`YearsFunction`）原本直接 `implements ScalarFunction<R>`，没有自定义 `equals`/`hashCode`。这会导致两个对同一函数的调用（如 `bucket(4, dep)` 出现在 scan 与 post-scan filter 两处）在 Catalyst 优化器中被视为不同表达式，影响表达式去重与规则匹配。本提交新增 `BaseScalarFunction<R> implements ScalarFunction<R>`，基于 `canonicalName()` 实现 `equals`/`hashCode`，让所有函数共享一致的相等性语义。所有具体函数的基类改为 `extends BaseScalarFunction<R>`。

3. **新增端到端测试 `TestSystemFunctionPushDownInRowLevelOperations`**：通过 20+ 测试用例覆盖 DELETE/UPDATE/MERGE 三种行级命令 × CoW/MoR 两种模式 × 多种变换函数（bucket/years/months/days/hours/truncate）× 多种谓词形式（IN / `=` / `>` / `<=`）的组合。测试通过 `withUnavailableLocations` 把与条件无关的文件标记为不可用，然后断言执行后这些文件未被扫描（验证裁剪生效），同时统计物理计划中 `StaticInvoke`/`ApplyFunctionExpression` 的出现次数来验证下推是否如预期。

## 修改详情

### `spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/optimizer/ReplaceStaticInvoke.scala`

**修改目的**：扩展 `ReplaceStaticInvoke` 规则以处理 `Join` 节点条件和 `In`/`InSet` 表达式，使 CoW 行级命令下的系统函数能正确下推。

**工作逻辑**：

1. 新增 import：`In`、`InSet`（表达式类型）、`Join`、`ReplaceData`（逻辑计划节点）、`COMMAND`、`IN`、`INSET`、`JOIN`（TreePattern 枚举）。

2. **顶层 `apply` 改造**：
   - 改前：
     ```scala
     override def apply(plan: LogicalPlan): LogicalPlan =
       plan.transformWithPruning(_.containsAllPatterns(BINARY_COMPARISON, FILTER)) {
         case filter @ Filter(condition, _) =>
           val newCondition = condition.transformWithPruning(_.containsPattern(BINARY_COMPARISON)) {
             case c @ BinaryComparison(left: StaticInvoke, right) if canReplace(left) && right.foldable =>
               c.withNewChildren(Seq(replaceStaticInvoke(left), right))
             case c @ BinaryComparison(left, right: StaticInvoke) if canReplace(right) && left.foldable =>
               c.withNewChildren(Seq(left, replaceStaticInvoke(right)))
           }
           if (newCondition fastEquals condition) filter else filter.copy(condition = newCondition)
       }
     ```
   - 改后：
     ```scala
     override def apply(plan: LogicalPlan): LogicalPlan =
       plan.transformWithPruning(_.containsAnyPattern(COMMAND, FILTER, JOIN)) {
         case join @ Join(_, _, _, Some(cond), _) =>
           replaceStaticInvoke(join, cond, newCond => join.copy(condition = Some(newCond)))
         case filter @ Filter(cond, _) =>
           replaceStaticInvoke(filter, cond, newCond => filter.copy(condition = newCond))
       }
     ```
   - 关键变化：
     - pruning 模式从 `containsAllPatterns(BINARY_COMPARISON, FILTER)`（必须同时包含 BinaryComparison 和 Filter）改为 `containsAnyPattern(COMMAND, FILTER, JOIN)`（任一即可）。新增 `COMMAND` 是为了能进入 `ReplaceData` 命令内部（ReplaceData 是 command，其内部 Join 不会被 Filter 模式触发）；新增 `JOIN` 是为直接命中 Join 节点；移除 `BINARY_COMPARISON` 是因为顶层 pruning 改为按节点类型（Filter/Join/Command）触发，BinaryComparison 的 pruning 下放到表达式层。
     - 新增 `Join` 分支：匹配带条件的 Join，对其 condition 调用 `replaceStaticInvoke`，并用闭包 `newCond => join.copy(condition = Some(newCond))` 重建 Join。
     - `Filter` 分支同样改为调用统一的 `replaceStaticInvoke` 方法。

3. **抽取两个私有方法**：
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
   - `replaceStaticInvoke[T](node, condition, copy)`：泛型方法，调用表达式层的 `replaceStaticInvoke(condition)`，并用 `fastEquals` 检测是否变化。无变化时返回原节点（避免重建，保持引用稳定，便于后续规则匹配）。
   - `replaceStaticInvoke(condition)`：表达式层处理，pruning 用 `containsAnyPattern(BINARY_COMPARISON, IN, INSET)`，新增两个 case：
     - `In(value: StaticInvoke, _)`：处理 `system.xxx() IN (a, b, c)` 形式，调用 `in.copy(value = replaceStaticInvoke(value))` 把 `In` 的 value 替换为已绑定的 `ApplyFunctionExpression`。
     - `InSet(value: StaticInvoke, _)`：处理 Spark 把 `IN` 优化为 `InSet`（当列表足够大时）的情况，调用 `in.copy(child = replaceStaticInvoke(value))`（注意 `InSet` 用 `child` 而非 `value`）。
   - 原有 `BinaryComparison` 两个 case 保留不变。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/functions/BaseScalarFunction.java`（新增）

**修改目的**：为所有 Iceberg Spark 系统函数提供基于 `canonicalName()` 的 `equals`/`hashCode` 实现，保证同一函数的不同实例在 Catalyst 优化器中被视为相等。

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
- `hashCode` 直接基于 `canonicalName()`（Iceberg 函数的全局唯一名称，如 `org.apache.iceberg.spark.functions.bucket`）。
- `equals` 先做引用相等快路径，再校验对方是 `ScalarFunction`，最后比较 `canonicalName()`。这保证两个不同实例但同名的 Iceberg 函数被视为相等。
- 之所以关键：Catalyst 优化器在表达式替换、去重、`fastEquals` 比较时会用 `equals`。若函数没有正确的 `equals`，两个 `bucket(4, dep)` 调用会被视为不同表达式，使 `ReplaceStaticInvoke` 的替换结果在后续优化阶段无法被识别为"同一函数"，可能导致下推链路断裂或表达式重复。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/functions/*.java`（7 个函数文件）

**修改目的**：让所有 Iceberg 系统函数的基类从直接 `implements ScalarFunction<R>` 改为 `extends BaseScalarFunction<R>`，统一继承新的 `equals`/`hashCode`。

**工作逻辑**：每个文件做两处改动：
1. 移除 `import org.apache.spark.sql.connector.catalog.functions.ScalarFunction;`（不再直接用）。
2. 把内部抽象基类的声明从 `implements ScalarFunction<R>` 改为 `extends BaseScalarFunction<R>`。

涉及文件与具体改动：
- `BucketFunction.java`：`BucketBase` 改为 `extends BaseScalarFunction<Integer>`。
- `DaysFunction.java`：`BaseToDaysFunction` 改为 `extends BaseScalarFunction<Integer>`。
- `HoursFunction.java`：`TimestampToHoursFunction` 与 `TimestampNtzToHoursFunction` 都改为 `extends BaseScalarFunction<Integer>`。
- `IcebergVersionFunction.java`：`IcebergVersionFunctionImpl` 改为 `extends BaseScalarFunction<UTF8String>`。
- `MonthsFunction.java`：`BaseToMonthsFunction` 改为 `extends BaseScalarFunction<Integer>`。
- `TruncateFunction.java`：`TruncateBase<T>` 改为 `extends BaseScalarFunction<T>`。
- `YearsFunction.java`：`BaseToYearsFunction` 改为 `extends BaseScalarFunction<Integer>`。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/SparkPlanUtil.java`

**修改目的**：为测试提供从物理计划中收集符合谓词条件的表达式（`StaticInvoke`/`ApplyFunctionExpression`）的工具方法。

**工作逻辑**：新增 `collectExprs(SparkPlan sparkPlan, Predicate<Expression> predicate)`：
- 用 `AdaptiveSparkPlanHelper.collect` 遍历 SparkPlan 树（包括 AdaptiveSparkPlan 内部），对每个 plan 节点收集其 `expressions()`，再对每个 expression 用 `collect` + `PartialFunction` 收集满足 predicate 的子表达式。
- 新增私有 `collectExprs(Expression expression, Predicate<Expression> predicate)` 用 Scala 的 `collect` 提取满足谓词的子表达式。
- 这种"双层 collect"设计能跨越 `ApplyFunctionExpression` 嵌套在复杂表达式中的情况，把所有目标表达式平铺为一个 List。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestSystemFunctionPushDownInRowLevelOperations.java`（新增）

**修改目的**：端到端验证 CoW/MoR 模式下 DELETE/UPDATE/MERGE 三类行级命令中系统函数的下推行为。

**工作逻辑**：

1. **测试矩阵**：通过 `@Parameters` 仅使用 Hive catalog，但每个变换（bucket/years/months/days/hours/truncate）× 每种命令（DELETE/UPDATE/MERGE）× 两种模式（CoW/MoR）× 多种谓词（IN / `=` / `>` / `<=`）组合出 20+ 测试方法。

2. **核心断言模式**（以 `checkDelete` 为例）：
   ```java
   private void checkDelete(RowLevelOperationMode mode, String cond) {
     withUnavailableLocations(
         findIrrelevantFileLocations(cond),
         () -> {
           // 设置 delete 模式 + distribution mode = NONE
           sql("ALTER TABLE %s SET TBLPROPERTIES (...)");
           // 构造变更集（要删除的 id）
           Dataset<Row> changeDF = spark.table(tableName).where(cond).limit(2).select("id");
           changeDF.coalesce(1).writeTo(tableName(CHANGES_TABLE_NAME)).create();
           // 执行 DELETE，收集物理计划中的 StaticInvoke/ApplyFunctionExpression
           List<Expression> calls = executeAndCollectFunctionCalls(
               "DELETE FROM %s t WHERE %s AND t.id IN (SELECT id FROM %s)", ...);
           // CoW 计划当前不会优化 DELETE 中的 post-scan filter
           int expectedCallCount = mode == COPY_ON_WRITE ? 1 : 0;
           assertThat(calls).hasSize(expectedCallCount);
           // 验证结果正确
           assertEquals("Should have no matching rows", ImmutableList.of(), sql(...));
         });
   }
   ```
   - `findIrrelevantFileLocations(cond)`：用 `NOT cond` 查询找出"与条件无关"的文件路径（这些文件不应被扫描）。
   - `withUnavailableLocations`：把这些文件标记为不可用，模拟文件被裁剪。如果裁剪逻辑错误地扫描了这些文件，测试会失败。
   - `executeAndCollectFunctionCalls`：执行 SQL 并保留物理计划，从 `V2TableWriteExec.query()` 中收集 `StaticInvoke` 或 `ApplyFunctionExpression` 表达式，统计其数量。
   - `expectedCallCount`：
     - DELETE：CoW 期望 1 个调用（post-scan filter 中保留一次 ApplyFunctionExpression）；MoR 期望 0 个（条件完全下推到 scan，scan 后不再有调用）。
     - UPDATE：CoW 期望 2 个调用（CoW UPDATE 中条件出现在两处——scan filter 与 post-scan filter）；MoR 期望 0 个。
     - MERGE：CoW 与 MoR 都期望 0 个（Merge 的条件完全下推）。

3. **`initTable(transform)`**：创建带分区变换的测试表，插入 6 条数据，分布在不同的分区/bucket 中（如 bucket 0-3、years 5/50 等），使文件级裁剪有可观测效果。

4. **关键设计**：测试同时验证"裁剪生效"（无关文件不被扫描）与"下推行为符合预期"（calls 数量符合 CoW/MoR 模式特点）。这种"行为 + 结果"双重验证比单纯断言文件裁剪更严格。

## 小结

- **成效**：成功修复了 CoW 行级命令中系统函数下推失效的问题。通过扩展 `ReplaceStaticInvoke` 规则覆盖 `Join` 条件和 `In`/`InSet` 谓词，并新增 `BaseScalarFunction` 提供正确的 `equals`/`hashCode`，CoW 模式下 `DELETE FROM t WHERE system.bucket(4, dep) IN (2, 3)` 等查询现在能正确下推到 scan 进行文件裁剪。20+ 测试用例覆盖了各种变换函数、谓词形式、命令类型与模式的组合。
- **影响范围**：仅影响 `spark/v3.4/spark-extensions`（优化器规则）与 `spark/v3.4/spark`（函数基类）。所有使用 Iceberg Spark 3.4 集成、CoW 模式下行级命令 + 系统函数谓词的用户都会受益——文件裁剪生效可显著减少 I/O。MoR 模式本就工作正常，本提交保持其行为不变。
- **回迁到 1.4.x 的注意事项**：
  1. 本提交依赖 Spark 3.4 的 Catalyst 内部 API（`ReplaceData`、`Join`、`In`、`InSet`、`COMMAND`/`JOIN`/`IN`/`INSET` TreePattern）。1.4.x 若支持 Spark 3.4，回迁无障碍；若也要支持 Spark 3.3/3.2，需要分别移植到 `spark/v3.3/spark-extensions` 与 `spark/v3.2/spark-extensions`（Catalyst API 在不同 Spark 版本间有差异，需逐版本适配）。
  2. `BaseScalarFunction` 是新增类，所有函数文件的 import 调整与基类替换必须一起回迁，不能只回迁部分函数（否则 equals/hashCode 不一致）。
  3. `SparkPlanUtil.collectExprs` 依赖 `AdaptiveSparkPlanHelper` 与 Scala `PartialFunction`，1.4.x 中若已有此工具类则直接补充方法即可。
  4. 测试中 `withUnavailableLocations` 是测试基类 `SparkExtensionsTestBase` 提供的工具方法，回迁时需确认 1.4.x 中此方法存在。
  5. 注意本提交注释中提到"CoW planning currently does not optimize post-scan filters in DELETE"——这是一个已知的 CoW 限制，本提交并未修复它，只是把 expectedCallCount 设为符合当前限制的值。后续若有提交优化 CoW 的 post-scan filter，需相应调整测试期望值。
