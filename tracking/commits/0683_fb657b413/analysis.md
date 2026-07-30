# 提交 0683：简化 SparkSchemaUtil#schemaForTable 方法

## 提交信息
- **序号**：0683 / 4088
- **哈希**：fb657b413e2bb7f6c5e2c78465173df0426d3527
- **短哈希**：fb657b413
- **日期**：2024-04-14
- **作者**：Amogh Jahagirdar
- **提交说明**：Spark: Simplify SparkSchemaUtil#schemaForTable (#10137)
- **PR/Issue**：#10137

## 总体目的

本提交对 Iceberg Spark 集成模块中的 `SparkSchemaUtil.schemaForTable` 方法进行代码简化重构。该方法负责把 Spark 中某个表的 `StructType` schema 转换为 Iceberg 的 `Schema` 对象。

简化前，`schemaForTable` 方法体内联了三行类型转换逻辑：先用 `SparkTypeVisitor.visit` 把 Spark `StructType` 转为 Iceberg `Type`，再通过 `asNestedType().asStructType().fields()` 取出字段并构造 `Schema`。然而，同类 `SparkSchemaUtil` 中已经存在一个完全等价的 `convert(StructType)` 方法，其内部执行的是一模一样的转换流程。

作者 Amogh Jahagirdar 发现了这个重复：`schemaForTable` 实际上就是在做 `convert(StructType)` 的工作，却把 `convert` 的实现内联了一份。这种代码重复既增加维护成本（两处改动需同步），也降低了代码可读性。本次提交的目标就是消除重复，让 `schemaForTable` 直接委托给已存在的 `convert(StructType)` 方法。

该改动同时应用到 Spark v3.3、v3.4、v3.5 三个版本目录（因为 Iceberg 为不同 Spark 版本各自维护一套源码副本），三处改动完全一致。

## 如何达成设计目的

策略非常直接：把 `schemaForTable` 的三行内联实现替换为单行 `return convert(spark.table(name).schema());`。

这里依赖了一个关键事实：`convert(StructType sparkType)` 这个重载方法等价于 `convert(sparkType, false)`，其实现为：
```java
Type converted = SparkTypeVisitor.visit(sparkType, new SparkTypeToType(sparkType));
Schema schema = new Schema(converted.asNestedType().asStructType().fields());
if (useTimestampWithoutZone) {
  schema = SparkFixupTimestampType.fixup(schema);
}
return schema;
```
由于 `useTimestampWithoutZone` 默认为 `false`，`SparkFixupTimestampType.fixup` 分支不会执行，因此 `convert(sparkType)` 的行为与原 `schemaForTable` 内联逻辑完全等价。也就是说，简化是行为保持（behavior-preserving）的重构，没有引入任何功能变化。

通过这一改动，`schemaForTable` 的语义也更清晰了——读者一眼就能看出"取表 schema 然后转成 Iceberg schema"，而无需理解 `SparkTypeVisitor` + `SparkTypeToType` + `asNestedType` + `asStructType` + `fields()` 这一长串链式调用。这也是封装复用带来的可读性收益。

## 修改详情

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/SparkSchemaUtil.java`
**修改目的**：消除 `schemaForTable` 中的重复转换逻辑，改为委托 `convert(StructType)`。
**工作逻辑**：简化前的方法体为：
```java
StructType sparkType = spark.table(name).schema();
Type converted = SparkTypeVisitor.visit(sparkType, new SparkTypeToType(sparkType));
return new Schema(converted.asNestedType().asStructType().fields());
```
简化后为：
```java
return convert(spark.table(name).schema());
```
行为完全一致，因为 `convert(StructType)` 重载等价于 `convert(sparkType, false)`，而 `useTimestampWithoutZone=false` 时不执行 `SparkFixupTimestampType.fixup`，剩余逻辑与原内联代码逐行对应。同时去掉了不再需要的局部变量 `sparkType`、`converted`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkSchemaUtil.java`
**修改目的**：同上，对 Spark v3.4 版本应用相同的简化。
**工作逻辑**：与 v3.3 完全一致的替换。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkSchemaUtil.java`
**修改目的**：同上，对 Spark v3.5 版本应用相同的简化。
**工作逻辑**：与 v3.3 完全一致的替换。

## 小结
- **成效**：成功消除了 `schemaForTable` 与 `convert(StructType)` 之间的代码重复，简化为单行委托调用，行为保持不变。
- **影响范围**：仅影响 Spark 集成模块（v3.3/v3.4/v3.5）的 `SparkSchemaUtil.schemaForTable` 方法，该方法用于把 Spark 表 schema 转为 Iceberg schema，调用方主要是 Spark SQL 中需要根据表名解析 schema 的场景。
- **回迁到 1.4.x 的注意事项**：1.4.x 分支若仍保留旧的三行内联实现，可直接 cherry-pick；需确认 1.4.x 分支的 `convert(StructType)` 重载已存在且行为等价（该重载是 Iceberg 较早就有稳定 API，预计可用）。属于纯重构，无行为变化，回迁风险极低，但需确认三处 Spark 版本目录在 1.4.x 中均存在。
