# 提交 2930：Spark: Fix scala warnings in View code (#14703)

## 提交信息

- **序号**：2930 / 4088
- **哈希**：cf27769762d678a8a790481318b6346c2dd480ff
- **短哈希**：cf2776976
- **日期**：2025-11-28 02:41:34 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark: Fix scala warnings in View code (#14703)
- **PR/Issue**：#14703

## 总体目的

Iceberg 为 Spark 3.4、3.5、4.0 三个版本各维护一套 spark-extensions 扩展代码（位于 `spark/v3.4/`、`spark/v3.5/`、`spark/v4.0/` 下），其中包含若干 View（视图）相关的 Scala 源文件，如 `CheckViews`、`ResolveViews`、`RewriteViewCommands`、`CreateV2ViewExec`、`DescribeV2ViewExec`、`ShowCreateV2ViewExec`、`ShowV2ViewPropertiesExec`。这些文件随 Scala 版本演进积累了一些弃用 API 用法与类型转换警告，在编译时产生噪音，也不利于将来升级到更新的 Scala/Spark 版本。

本提交系统性消除这些 Scala 编译警告，主要涉及三类问题：
1. **弃用的集合转换 API**：`scala.collection.JavaConverters._` 自 Scala 2.13 起被弃用，应替换为 `scala.jdk.CollectionConverters._`。
2. **弃用的 Map `+` 方法**：Scala 2.13 中 `Map.+(k1->v1, k2->v2)` 这种以可变元组参数向 Map 添加键值对的写法被弃用，且语义上与可变集合的 `+` 混淆；应改用 `++` 配合 `Map(...)`。
3. **隐式数组到 Seq 转换警告**：`fieldNames`、`currentNamespace` 等返回 `Array` 的值被直接当作 `Seq` 使用时，依赖隐式转换，编译器会发出警告；显式调用 `.toIndexedSeq` 可消除警告并明确类型。

清理这些警告有助于保持编译输出干净、提前为后续 Scala/Spark 升级扫清障碍。

## 如何达成设计目的

改动以"同一套修复在三个 Spark 版本目录中同步应用"的方式进行：对 7 个 View 相关 Scala 文件，在 `spark/v3.4/`、`spark/v3.5/`、`spark/v4.0/` 三处做相同模式的修改（v4.0 的 `DescribeV2ViewExec` 因额外一处 `.toIndexedSeq` 略有不同）。共涉及 21 个文件、31 处增删。修改均为机械性的 API 替换与显式转换补充，不改变运行时行为。

## 修改详情

由于三个 Spark 版本目录下同名文件的改动模式一致，下面按文件归类说明（每个文件在 `spark/v3.4/spark-extensions/`、`spark/v3.5/spark-extensions/`、`spark/v4.0/spark-extensions/` 下各有一份，共 21 个文件）。

### `.../catalyst/analysis/CheckViews.scala` (每个版本 +1/-1 lines)

**修改目的**：消除数组隐式转换警告。

**工作逻辑**：将 `SchemaUtils.checkColumnNameDuplication(query.schema.fieldNames, SQLConf.get.resolver)` 中的 `fieldNames`（返回 `Array[String]`）改为 `fieldNames.toIndexedSeq`，避免依赖 `Array` 到 `Seq` 的隐式转换。

### `.../catalyst/analysis/ResolveViews.scala` (每个版本 +2/-2 lines)

**修改目的**：消除数组隐式转换警告。

**工作逻辑**：两处改动：
- `c.copy(query = aliased, queryColumnNames = query.schema.fieldNames, ...)` → `queryColumnNames = query.schema.fieldNames.toIndexedSeq`；
- 构造 `aliases` 后的 `}` 改为 `}.toIndexedSeq`（`view.schema.fields.zipWithIndex.map {...}` 返回 `Array`，转 `IndexedSeq` 以匹配预期的 `Seq[Alias]` 类型）。

### `.../catalyst/analysis/RewriteViewCommands.scala` (每个版本 +1/-1 lines)

**修改目的**：消除数组隐式转换警告。

**工作逻辑**：将 `ResolvedNamespace(catalogManager.currentCatalog, catalogManager.currentNamespace)` 中的 `currentNamespace`（返回 `Array[String]`）改为 `currentNamespace.toIndexedSeq`。

### `.../execution/datasources/v2/CreateV2ViewExec.scala` (每个版本 +3/-3 lines)

**修改目的**：替换弃用的 `JavaConverters` 导入与弃用的 Map `+` 写法。

**工作逻辑**：
- 导入由 `import scala.collection.JavaConverters._` 改为 `import scala.jdk.CollectionConverters._`；
- 属性拼接逻辑由：
  ```scala
  properties ++
    comment.map(ViewCatalog.PROP_COMMENT -> _) +
    (ViewCatalog.PROP_CREATE_ENGINE_VERSION -> engineVersion,
      ViewCatalog.PROP_ENGINE_VERSION -> engineVersion)
  ```
  改为：
  ```scala
  properties ++
    comment.map(ViewCatalog.PROP_COMMENT -> _) ++
    Map(ViewCatalog.PROP_CREATE_ENGINE_VERSION -> engineVersion,
      ViewCatalog.PROP_ENGINE_VERSION -> engineVersion)
  ```
  这里 `comment.map(...)` 返回的是 `Option/Iterable`，原先用 `+` 把一个元组可变参数组追加到 Map 既弃用又类型混乱；改为 `++` 拼接一个显式 `Map(...)` 语义清晰——将 `properties` Map 与 comment 映射、引擎版本 Map 合并。

### `.../execution/datasources/v2/DescribeV2ViewExec.scala` (v3.4/v3.5 各 +1/-1；v4.0 +2/-2 lines)

**修改目的**：替换弃用的 `JavaConverters` 导入；v4.0 额外消除数组隐式转换警告。

**工作逻辑**：三个版本均将导入由 `scala.collection.JavaConverters._` 改为 `scala.jdk.CollectionConverters._`。v4.0 版本额外把 `view.name.split("\\.").take(2)` 改为 `view.name.split("\\.").take(2).toIndexedSeq`（`split` 返回 `Array`，赋给 `Seq[String]` 时需显式转换），因此 v4.0 该文件多一处改动。

### `.../execution/datasources/v2/ShowCreateV2ViewExec.scala` (每个版本 +1/-1 lines)

**修改目的**：替换弃用的 `JavaConverters` 导入为 `scala.jdk.CollectionConverters._`。

### `.../execution/datasources/v2/ShowV2ViewPropertiesExec.scala` (每个版本 +1/-1 lines)

**修改目的**：替换弃用的 `JavaConverters` 导入为 `scala.jdk.CollectionConverters._`。

## 总结

该提交在 Spark 3.4/3.5/4.0 三套 spark-extensions 的 7 个 View 相关 Scala 文件中同步清理 Scala 编译警告：将弃用的 `JavaConverters` 替换为 `jdk.CollectionConverters`、将弃用的 Map `+` 写法改为 `++` 配合显式 `Map(...)`、对数组到 Seq 的隐式转换补充 `.toIndexedSeq`。改动不改变运行时行为，但使编译输出更干净，并为后续 Scala/Spark 版本升级减少技术债。
