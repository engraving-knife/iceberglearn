# 提交 0447：Spark 3.4: Bypass Spark's ViewCatalog API when replacing a view (#9614)

## 提交信息

- **序号**：0447
- **完整哈希**：756fa6894a8f87bd0d5213c2bb86ba697ce7f655
- **短哈希**：756fa6894
- **日期**：2024-02-02 09:43:50 +0100
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Spark 3.4: Bypass Spark's ViewCatalog API when replacing a view (#9614)
- **关联 PR**：#9614
- **完整说明**：Spark 的 `ViewCatalog` API 在 3.5 中没有 `replace()` 方法（它是后来才引入的），因此绕过 Spark 的 `ViewCatalog`，以便在执行 `CREATE OR REPLACE` 后能保留视图的历史。

## 总体目的

本提交要解决的核心问题是：在 Spark 3.4 中执行 `CREATE OR REPLACE VIEW` 时，视图的历史（history）会被丢弃。原因在于 v3.4 的 `CreateV2ViewExec.scala` 原实现采用"先 `dropView` 再 `createView`"的方式来模拟 replace，而 `dropView` 会把视图连同其历史一起删掉，新创建的视图从零开始，versionId 永远回到 1，`view.history()` 也只有一条记录。这与 Iceberg 视图"可版本化、可追溯历史"的设计理念相违背——`CREATE OR REPLACE VIEW` 在语义上应当是"替换"而非"删除重建"，应当保留旧版本历史并追加一个新版本。

更进一步的背景是：Spark 官方的 `ViewCatalog` 接口本身并不能解决这个问题。`ViewCatalog` 只提供 `createView`，没有 `replaceView`（`replaceView` 在 Spark 的 `ViewCatalog` 中是很后面才加进来的），因此无法直接通过实现 `ViewCatalog` 来获得"保留历史的替换"能力。v3.5 目录里此前已经引入了一个 Iceberg 自定义的扩展接口 `SupportsReplaceView`（继承自 `ViewCatalog`，额外声明 `replaceView(...)`），并由 `CreateV2ViewExec` 通过 Scala 的 `match` 模式匹配在 catalog 实现了该接口时走 `replaceView`、否则回退到旧的 drop+create。本提交把同一套机制回 port 到 Spark 3.4 目录，使 3.4 与 3.5 行为一致，同时顺手清理了 v3.5 中未使用的 `LogicalPlan` import。

## 如何达成设计目的

实现路径分三步。第一步，在 `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/` 下新建 `SupportsReplaceView.java`——一个继承 `ViewCatalog` 的接口，声明 `replaceView(...)` 方法（签名与 v3.5 中已有的同名接口一致，包含 ident、sql、currentCatalog、currentNamespace、schema、queryColumnNames、columnAliases、columnComments、properties 共九个参数）。第二步，让 `SparkCatalog` 同时实现 `ViewCatalog` 与 `SupportsReplaceView`，并在其中给出 `replaceView` 的真正实现：通过 `asViewCatalog.buildView(...)` 链式调用 `.replace()` 来走 Iceberg 视图的 replace 路径（而非 drop+create），从而保留历史。第三步，改写 v3.4 的 `CreateV2ViewExec.scala`：在 `replace` 分支用 `catalog match { case c: SupportsReplaceView => c.replaceView(...); case _ => 旧的 drop+create }` 模式匹配，使替换走新路径、不支持时回退旧路径，并删掉原来的 `FIXME: replaceView API doesn't exist in Spark 3.5` 注释。

此外，配套的改动还有：`SparkView.QUERY_COLUMN_NAMES` 常量从 `private` 提升为 `public`，以便 `SparkCatalog` 引用；`SparkCatalog.createView` 中拼 `query-column-names` 的实现从 `StringJoiner` 改为 Iceberg relocated 的 `Joiner.on(",")`，并把属性 key 从硬编码字符串 `"spark.query-column-names"` 改为 `SparkView.QUERY_COLUMN_NAMES` 常量，`ImmutableMap.builder()` 改为 `.buildKeepingLast()` 以便在属性重复时保留后者。测试侧新增 `createOrReplaceViewKeepsViewHistory` 用例，断言替换后 `history().hasSize(2)`、`versionId()==2`、`schemaId()==1`、`schemas().hasSize(2)`。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SupportsReplaceView.java`（新增）
**修改目的**：为 Spark 3.4 引入 Iceberg 自定义的"可替换视图"扩展接口，作为绕过 Spark `ViewCatalog` 缺失 `replaceView` 的桥梁。
**工作逻辑**：`public interface SupportsReplaceView extends ViewCatalog`，声明 `View replaceView(Identifier ident, String sql, String currentCatalog, String[] currentNamespace, StructType schema, String[] queryColumnNames, String[] columnAliases, String[] columnComments, Map<String, String> properties) throws NoSuchViewException, NoSuchNamespaceException;`。签名与 v3.5 中的同名接口完全一致，保证 Scala 侧模式匹配代码可跨版本复用。它扩展 `ViewCatalog` 而不是独立接口，是因为 `CreateV2ViewExec` 拿到的 `catalog` 本来就是 `ViewCatalog`，模式匹配时只需检查是否同时是 `SupportsReplaceView` 即可分流。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java`
**修改目的**：让 `SparkCatalog` 实现 `SupportsReplaceView`，并提供保留历史的 `replaceView` 实现；同时统一 `createView`/`replaceView` 之间 `query-column-names` 属性的写法。
**工作逻辑**：
1. 类声明由 `implements ViewCatalog` 改为 `implements ViewCatalog, SupportsReplaceView`。
2. 删除 `import java.util.StringJoiner`，新增 `import ...Joiner` 与已有的 `COMMA` Splitter 旁加一个 `COMMA_JOINER = Joiner.on(",")`。
3. `createView` 中拼 `query-column-names` 由 `StringJoiner` 改为 `COMMA_JOINER.join(queryColumnNames)`，属性 key 由字面量 `"spark.query-column-names"` 改为常量 `SparkView.QUERY_COLUMN_NAMES`，`ImmutableMap.builder().build()` 改为 `.buildKeepingLast()`（防止用户在 properties 里也传同名 key 时抛 `IllegalArgumentException`，改为保留后者）。
4. 新增 `replaceView(...)` 方法：构造 `icebergSchema`、`props`（与 `createView` 完全相同的属性拼装逻辑，复用 `Spark3Util.rebuildCreateProperties`、`SparkView.QUERY_COLUMN_NAMES`、`buildKeepingLast`），然后调用 `asViewCatalog.buildView(...).withDefaultCatalog(...).withDefaultNamespace(...).withQuery("spark", sql).withSchema(...).withLocation(...).withProperties(props).replace()`。关键在于结尾调 `.replace()` 而非 `.create()`，走 Iceberg 视图的"替换"路径，旧版本与新版本共存于历史中。捕获 Iceberg 的 `NoSuchNamespaceException` 转换为 Spark 的 `NoSuchNamespaceException`；catalog 不支持视图时抛 `UnsupportedOperationException`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkView.java`
**修改目的**：把 `QUERY_COLUMN_NAMES` 常量从 `private` 提升为 `public`，供 `SparkCatalog` 引用。
**工作逻辑**：`private static final String QUERY_COLUMN_NAMES = "spark.query-column-names";` → `public static final String QUERY_COLUMN_NAMES = "spark.query-column-names";`。`RESERVED_PROPERTIES` 集合中对该常量的引用保持不变。

### `spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/CreateV2ViewExec.scala`
**修改目的**：在 `CREATE OR REPLACE VIEW` 分支改用 `SupportsReplaceView` 模式匹配，使 Iceberg catalog 走保留历史的 replace 路径。
**工作逻辑**：新增 `import org.apache.iceberg.spark.SupportsReplaceView`。把原来 `if (replace) { if (catalog.viewExists(ident)) catalog.dropView(ident); catalog.createView(...) }`（带 FIXME 注释）的整体逻辑，改为：
```scala
if (replace) {
  catalog match {
    case c: SupportsReplaceView =>
      c.replaceView(ident, queryText, currentCatalog, currentNamespace, viewSchema,
        queryColumnNames.toArray, columnAliases.toArray,
        columnComments.map(c => c.orNull).toArray, newProperties.asJava)
    case _ =>
      if (catalog.viewExists(ident)) catalog.dropView(ident)
      catalog.createView(...)
  }
}
```
即：catalog 实现 `SupportsReplaceView` 时走 `replaceView`（保留历史），否则回退到旧的 drop+create。这样既修复了 Iceberg catalog 的历史丢失问题，又不破坏对其他非 Iceberg catalog 的兼容性。

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/CreateV2ViewExec.scala`
**修改目的**：清理 v3.5 目录中未使用的 import。
**工作逻辑**：删除 `import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan`。v3.5 的 `CreateV2ViewExec` 此前已经引入了 `SupportsReplaceView` 与模式匹配逻辑（在更早的提交中完成），本次只做 import 清理，使两个版本的文件内容趋于一致（diff 后两份 `CreateV2ViewExec.scala` 哈希相同：`388d391a4`）。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java`
**修改目的**：新增回归测试，验证 `CREATE OR REPLACE VIEW` 后视图历史与版本被保留。
**工作逻辑**：新增 `createOrReplaceViewKeepsViewHistory()` 测试。先 `CREATE VIEW` 一个 `SELECT id, data FROM <table> WHERE id <= 3`，断言 `history().hasSize(1)`、`versionId()==1`、`schemaId()==0`、`schemas().hasSize(1)`、schema 为 `(new_id, new_data)`。再 `CREATE OR REPLACE VIEW` 改为 `SELECT id FROM <table> WHERE id > 3` 并改名列为 `updated_id`，断言 `history().hasSize(2)`、`versionId()==2`、`schemaId()==1`、`schemas().hasSize(2)`、schema 变为单列 `(updated_id)`。该用例直接命中旧的 drop+create bug——旧逻辑下替换后 history 会重新归零、versionId 回到 1。

## 小结

本提交为 Spark 3.4 修复了 `CREATE OR REPLACE VIEW` 丢失视图历史的问题。做法是引入 Iceberg 自定义的 `SupportsReplaceView` 接口（与 v3.5 已有的接口同构），让 `SparkCatalog` 实现它并通过 `ViewBuilder.replace()` 走 Iceberg 视图的替换路径，`CreateV2ViewExec` 用 Scala 模式匹配在 catalog 支持时走 `replaceView`、不支持时回退 drop+create。配套统一了 `query-column-names` 属性的写法（常量化 + `Joiner` + `buildKeepingLast`），并新增了覆盖历史保留与版本递增的回归测试。修复后 v3.4 与 v3.5 在视图替换行为上一致，为后续在 Spark 3.4 上构建可版本化、可追溯的视图语义打下基础。
