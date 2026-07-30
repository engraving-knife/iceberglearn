# 提交 0292：Spark 3.5: Remove UnresolvedIcebergTable (#9338)

## 提交信息

- **序号**：0292 / 4088
- **哈希**：980733c2e1eb10b6e0d2ac6643490a827c806c28
- **短哈希**：980733c2e
- **日期**：2023-12-19 18:05:17 +0100
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Spark 3.5: Remove UnresolvedIcebergTable (#9338)
- **PR/Issue**：#9338

## 总体目的

本提交要清理 Spark 3.5 扩展解析器 `IcebergSparkSqlExtensionsParser` 中一段长期存在的死代码——`UnresolvedIcebergTable` 提取器对象（extractor object）。该对象定义在解析器类内部，包含一个 `unapply` 方法，设计意图是在模式匹配中判断一个 `LogicalPlan` 是否引用了一张 Iceberg 表：它先用 `EliminateSubqueryAliases` 剥离子查询别名，再匹配 `UnresolvedRelation`，并通过 `Spark3Util.catalogAndIdentifier` 解析多段标识符，最后尝试从 `TableCatalog` 加载表并判断其是否为 `SparkTable`。

然而，通过对整个 `spark/v3.5` 模块进行检索可以确认，`UnresolvedIcebergTable` 自引入以来从未被任何代码引用——既没有在解析规则的 `case` 分支中被用作模式，也没有在 AST 构建器或分析器中被调用。它是一段纯粹的死代码，既不会被执行，也不会产生任何副作用，但它的存在会误导读者以为解析器在某处依赖这一判断逻辑。

清理这类死代码的动机是多方面的：首先，死代码会增加维护负担，后续开发者在阅读解析器时需要花费精力理解一个永远不会被触发的分支；其次，它内部还引用了 `Spark3Util`、`SparkSession`、`TableCatalog.loadTable` 等较重的依赖，给人"解析器会在解析阶段主动加载表"的错误印象，而实际上 Iceberg 的 Spark 解析扩展只负责识别 Iceberg 专有 SQL 命令（如 `ALTER TABLE ... ADD PARTITION FIELD` 等），表的实际加载发生在分析阶段而非解析阶段；最后，移除死代码也是为后续重构（例如升级 Spark 版本、调整解析器结构）减少不必要的耦合点，避免改动时还要兼顾一段无人调用的逻辑。

## 如何达成设计目的

设计思路非常简单直接：整段删除 `UnresolvedIcebergTable` 对象定义（共 30 行），不新增任何代码。由于该对象没有任何调用方，删除后编译器不会报错，运行时行为也完全不变。提交者刻意将改动控制在最小范围——只删除对象本体，未顺带清理因此变成无用的 import（如 `EliminateSubqueryAliases`、`UnresolvedRelation`、`SparkTable`、`TableCatalog`、`Table`、`SparkSession`、`scala.util.Try` 等），以保持 diff 的聚焦与可审查性。这种"外科手术式"的删改使审查者能一眼确认改动只是移除死代码，不引入任何行为变化。

## 修改详情

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/parser/extensions/IcebergSparkSqlExtensionsParser.scala`

**修改目的**：移除从未被引用的 `UnresolvedIcebergTable` 提取器对象，消除死代码。

**工作逻辑**：

被删除的代码块（原位于 `parsePlan` 方法与 `isIcebergCommand` 方法之间）如下：

```scala
object UnresolvedIcebergTable {

  def unapply(plan: LogicalPlan): Option[LogicalPlan] = {
    EliminateSubqueryAliases(plan) match {
      case UnresolvedRelation(multipartIdentifier, _, _) if isIcebergTable(multipartIdentifier) =>
        Some(plan)
      case _ =>
        None
    }
  }

  private def isIcebergTable(multipartIdent: Seq[String]): Boolean = {
    val catalogAndIdentifier = Spark3Util.catalogAndIdentifier(SparkSession.active, multipartIdent.asJava)
    catalogAndIdentifier.catalog match {
      case tableCatalog: TableCatalog =>
        Try(tableCatalog.loadTable(catalogAndIdentifier.identifier))
          .map(isIcebergTable)
          .getOrElse(false)

      case _ =>
        false
    }
  }

  private def isIcebergTable(table: Table): Boolean = table match {
    case _: SparkTable => true
    case _ => false
  }
}
```

逐层分析这段被删代码的逻辑与为何它属于死代码：

- `unapply` 方法是 Scala 模式匹配的入口。若某处写了 `case UnresolvedIcebergTable(plan) => ...`，编译期会调用此 `unapply`。但全模块检索确认没有任何 `case UnresolvedIcebergTable(...)` 的调用点，因此该 `unapply` 永远不会被触发。
- 内部 `isIcebergTable(multipartIdent)` 通过 `Spark3Util.catalogAndIdentifier` 解析标识符，再尝试 `tableCatalog.loadTable` 加载表，并用 `Try` 包裹以容错（表不存在时返回 `false`）。这是一段较重的、带副作用的逻辑（会触发 catalog 加载），如果真的在解析阶段被调用，会带来性能与正确性影响——这恰恰说明它不应留在解析器中：Iceberg 的 SQL 扩展解析器只负责识别 Iceberg 专有语法命令，表加载属于分析阶段（analyzer）的职责。
- `isIcebergTable(table: Table)` 通过模式匹配判断表是否为 `SparkTable`（Iceberg 在 Spark 中的表实现）。

由于整段对象无人调用，删除后：
- 编译仍然通过（Scala 编译器不会因删除未使用成员报错）。
- 运行时行为零变化——没有任何执行路径经过这段代码。
- 需要说明的是，删除后文件顶部的若干 import（`EliminateSubqueryAliases`、`UnresolvedRelation`、`SparkTable`、`Table`、`TableCatalog`、`SparkSession`、`scala.util.Try`）在文件体内不再被使用，但本提交未一并清理这些 import。这是有意为之的最小化改动策略，遗留的未使用 import 不会影响编译与运行，可由后续的 lint/格式化提交统一处理。

## 小结

本提交通过整段删除 `IcebergSparkSqlExtensionsParser` 中从未被调用的 `UnresolvedIcebergTable` 提取器对象，清理了 Spark 3.5 扩展解析器中的一段死代码。该对象虽设计了"在 LogicalPlan 上判断是否引用 Iceberg 表"的能力，但自引入起就无任何调用方，其存在既增加阅读负担又容易误导读者以为解析阶段会主动加载表。删除改动是纯减法（30 行删除、0 行新增），不改变任何运行时行为，体现了"保持解析器职责单一、移除误导性死代码"的维护目标。
