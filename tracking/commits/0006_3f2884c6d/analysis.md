# 提交 0006：Spark: Fix Decimal value conversion in V2 filters (#8682)

## 提交信息

- **序号**：0006 / 4088
- **哈希**：3f2884c6d2e514f1112bd440fedfbb7d30227a96
- **短哈希**：3f2884c6d
- **日期**：2023-09-29 12:38:36 -0700
- **作者**：Anton Okolnychyi
- **提交说明**：Spark: Fix Decimal value conversion in V2 filters (#8682)
- **PR/Issue**：#8682

## 总体目的

这个提交修复了 Spark V2 数据源过滤器（filter）在处理 Decimal 类型字面量（literal）时的转换缺陷。

在 Iceberg 的 Spark 集成中，当用户对 Iceberg 表执行带有过滤条件的查询时，Spark 会将过滤谓词下推（push down）给 Iceberg 的扫描层。`SparkV2Filters` 类负责将 Spark 的 `Literal<?>` 对象转换为 Iceberg 内部能识别的值。在此提交之前，`convertLiteral` 方法只对 `UTF8String`（字符串类型）做了特殊处理，将其转成 Java 的 `String`，其他类型则原样返回 `literal.value()`。

问题在于：Spark 的 Decimal 类型在内部使用的是 `org.apache.spark.sql.types.Decimal` 这一特定类，而 Iceberg 的表达式和绑定层期望的是 `java.math.BigDecimal`。如果不做转换直接把 Spark 的 `Decimal` 对象传给 Iceberg，下游的类型匹配、比较等逻辑会失败或行为不正确，导致带 Decimal 列的过滤条件无法被正确下推和求值。这直接影响诸如 `salary > 100.03` 这类针对 `DECIMAL(10,2)` 列的查询无法走 Iceberg 的下推路径，影响查询性能甚至正确性。

通过为 `Decimal` 类型增加显式转换分支（调用 `toJavaBigDecimal()`），本提交确保 Decimal 字面量被正确归一化为 Java 标准类型，使得 Iceberg 扫描过滤器能正确识别和处理这类谓词。由于 Iceberg 同时维护 spark v3.3、v3.4、v3.5 三个版本分支，该修复在三个版本中同步应用，保证跨版本行为一致。

## 如何达成设计目的

设计思路非常直接：在 `SparkV2Filters.convertLiteral` 方法中已有的 `UTF8String` 分支之后，新增一个 `Decimal` 类型分支，调用 Spark `Decimal` 类提供的 `toJavaBigDecimal()` 方法将其转换为 `java.math.BigDecimal`。同时在三个 Spark 版本的 `TestFilterPushDown` 测试类中各新增一个 `testFilterPushdownWithDecimalValues` 测试用例，构造 `DECIMAL(10, 2)` 列并验证 `salary > 100.03` 这样的过滤条件能正确下推到 Iceberg 扫描层并返回正确结果。改动结构清晰、范围小而精准，修改与测试一一对应。

## 修改详情

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/SparkV2Filters.java`

**修改目的**：为 Spark 3.3 版本的 V2 过滤器添加 Decimal 字面量到 BigDecimal 的转换。

**工作逻辑**：新增了 `import org.apache.spark.sql.types.Decimal;`。在 `convertLiteral(Literal<?> literal)` 方法中，在原有的 `UTF8String` 分支之后新增 `else if (literal.value() instanceof Decimal)` 分支，调用 `((Decimal) literal.value()).toJavaBigDecimal()` 返回标准 Java `BigDecimal`。这样 Iceberg 表达式层在绑定和求值时就能拿到与 schema 中 Decimal 类型对应的 Java 类型。

### `spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/sql/TestFilterPushDown.java`

**修改目的**：为 Spark 3.3 版本添加 Decimal 过滤下推的回归测试。

**工作逻辑**：新增 `import java.math.BigDecimal;`。新增测试方法 `testFilterPushdownWithDecimalValues()`：创建含 `id INT, salary DECIMAL(10, 2), dep STRING` 且按 `dep` 分区的表，插入两行 `(1, 100.01, 'd1')` 和 `(2, 100.05, 'd1')`，然后用 `checkFilters` 验证查询谓词 `dep = 'd1' AND salary > 100.03` 的下推行为——Spark 后扫描过滤器为 `isnotnull(salary) AND (salary > 100.03)`，Iceberg 扫描过滤器为 `dep IS NOT NULL, salary IS NOT NULL, dep = 'd1', salary > 100.03`，并断言结果只有 `(2, 100.05, 'd1')` 一行。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkV2Filters.java`

**修改目的**：为 Spark 3.4 版本同步应用相同的 Decimal 转换修复。

**工作逻辑**：与 v3.3 版本完全一致——新增 `Decimal` 的 import，并在 `convertLiteral` 中新增 `Decimal` 到 `BigDecimal` 的转换分支。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestFilterPushDown.java`

**修改目的**：为 Spark 3.4 版本添加对应的 Decimal 过滤下推回归测试。

**工作逻辑**：测试逻辑与 v3.3 基本一致，区别在于额外调用了 `configurePlanningMode(planningMode)`，因为 v3.4/v3.5 的测试基类支持分布式/本地两种计划模式，需要在测试中显式配置以保证两种计划模式下下推行为都正确。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkV2Filters.java`

**修改目的**：为 Spark 3.5 版本同步应用相同的 Decimal 转换修复。

**工作逻辑**：与 v3.4 版本的改动完全一致。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestFilterPushDown.java`

**修改目的**：为 Spark 3.5 版本添加对应的 Decimal 过滤下推回归测试。

**工作逻辑**：与 v3.4 版本的测试逻辑完全一致，同样调用 `configurePlanningMode(planningMode)` 以覆盖两种计划模式。

## 小结

该提交修复了 Spark V2 过滤器中 Decimal 字面量未正确转换为 BigDecimal 的缺陷，使带 Decimal 列的过滤谓词能被正确下推到 Iceberg 扫描层，保证查询正确性与性能，并在三个 Spark 版本分支上同步修复与测试。
