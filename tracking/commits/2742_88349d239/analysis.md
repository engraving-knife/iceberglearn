# 提交 2742：[SPARK][MIRROR] Removed the unused imports for scala

## 提交信息

- **序号**：2742 / 4088
- **哈希**：88349d239fe5c47872d37d356d1afe6a4c2301f3
- **短哈希**：88349d239
- **日期**：2025-10-13 09:53:29 -0700
- **作者**：jackylee
- **提交说明**：[SPARK][MIRROR] Removed the unused imports for scala
- **PR/Issue**：#14311

## 总体目的

这是一个代码清理提交，移除 Iceberg Spark 集成模块中 Scala 源码的未使用 import。Iceberg 的 Spark 集成包含大量从 Spark 镜像（mirror）过来的 Scala 代码，这些代码在维护过程中可能遗留了不再使用的 import 语句。未使用的 import 不仅影响代码整洁度，还可能在 Spark 版本升级时引入潜在的编译问题（例如被引用的类在新版本中被移除）。

本提交系统性地清理了 Spark v3.4、v3.5、v4.0 三个版本模块中的未使用 import，并修复了一些 Scala 模式匹配中的变量绑定问题（将未使用的绑定变量改为 `_` 通配符）。

## 如何达成设计目的

清理工作分为两类：
1. **移除完全未使用的 import 语句**：直接删除不再被引用的类导入
2. **简化模式匹配中的未使用变量绑定**：在 Scala 的 case 匹配中，将未使用的变量名改为 `_`，既消除编译器警告，也明确表示该变量不被使用

## 修改详情

### Spark v3.4 模块

#### `spark/v3.4/spark-extensions/.../ReplaceStaticInvoke.scala` (+0/-1 lines)
**修改目的**：移除未使用的 `ReplaceData` import。

#### `spark/v3.4/spark-extensions/.../IcebergSparkSqlExtensionsParser.scala` (+1/-1 lines)
**修改目的**：简化模式匹配中未使用的变量绑定。
**工作逻辑**：将 `Some(startIndex), Some(stopIndex), Some(sqlText), Some(objectType), Some(objectName)` 改为 `Some(_), Some(_), Some(_), Some(_), Some(_)`，这些变量在 match 分支中未被使用。

#### `spark/v3.4/spark-extensions/.../SetIdentifierFields.scala` (+0/-1 lines)
**修改目的**：移除未使用的 `Transform` import。

#### `spark/v3.4/spark/.../SparkExpressionConverter.scala` (+2/-2 lines)
**修改目的**：简化模式匹配中未使用的变量绑定。
**工作逻辑**：将 `case dummyRelation: DummyRelation` 和 `case localRelation: LocalRelation` 改为 `case _: DummyRelation` 和 `case _: LocalRelation`，变量名未被使用。

### Spark v3.5 模块

#### `spark/v3.5/spark-extensions/.../IcebergSparkSqlExtensionsParser.scala` (+2/-9 lines)
**修改目的**：移除多个未使用 import 并简化模式匹配。
**工作逻辑**：移除 `Spark3Util`、`SparkTable`、`EliminateSubqueryAliases`、`UnresolvedRelation`、`Table`、`TableCatalog`、`Try` 共 7 个未使用 import；同样简化模式匹配变量绑定。

#### `spark/v3.5/spark-extensions/.../SetIdentifierFields.scala` (+0/-1 lines)
**修改目的**：移除未使用的 `Transform` import。

#### `spark/v3.5/spark/.../SparkExpressionConverter.scala` (+2/-2 lines)
**修改目的**：与 v3.4 相同的模式匹配变量绑定简化。

### Spark v4.0 模块

#### `spark/v4.0/spark-extensions/.../IcebergSparkSqlExtensionsParser.scala` (+2/-3 lines)
**修改目的**：移除未使用 import 并简化模式匹配。
**工作逻辑**：移除 `SparkProcedures` import；简化模式匹配变量绑定（跨行格式调整）。

#### `spark/v4.0/spark-extensions/.../SetIdentifierFields.scala` (+0/-1 lines)
**修改目的**：移除未使用的 `Transform` import。

#### `spark/v4.0/spark-extensions/.../AlterV2ViewUnsetPropertiesExec.scala` (+0/-1 lines)
**修改目的**：移除未使用的 `AnalysisException` import。

#### `spark/v4.0/spark-extensions/.../ExtendedDataSourceV2Strategy.scala` (+0/-3 lines)
**修改目的**：移除未使用的 `InternalRow`、`Expression`、`GenericInternalRow` import。

#### `spark/v4.0/spark/.../ThetaSketchAgg.scala` (+0/-1 lines)
**修改目的**：移除未使用的 `col` import。

## 总结

本提交系统性清理了 Iceberg Spark 集成模块（v3.4、v3.5、v4.0）中 Scala 源码的未使用 import 和未使用的模式匹配变量绑定。共修改 12 个文件，移除 25 行、新增 8 行。这类代码清理有助于保持代码整洁、消除编译器警告，并在 Spark 版本升级时减少因未使用 import 引用的类被移除而导致的编译失败风险。
