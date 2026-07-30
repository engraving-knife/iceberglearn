# 提交 0443：Spark: Bypass Spark's ViewCatalog API when replacing a view (#9596)

## 提交信息

- **序号**：0443
- **哈希**：6bbf70a52ebccfaba4e7e08facd72b84b571e2a6
- **短哈希**：6bbf70a52
- **日期**：2024-02-01 18:55:57 +0100
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Spark: Bypass Spark's ViewCatalog API when replacing a view (#9596)
- **PR/Issue**：#9596

## 总体目的

本提交解决 Spark 3.5 上执行 `CREATE OR REPLACE VIEW` 时丢失视图历史的问题。背景是 Spark 3.5 的 `ViewCatalog` API 当时还没有 `replace()` 方法（要等后续版本才引入），所以 Iceberg 在 `CreateV2ViewExec` 里实现"replace"时只能走"先 `dropView` 再 `createView`"的折中——代码里还留了一句 `// FIXME: replaceView API doesn't exist in Spark 3.5`。这条路径的副作用是：drop 会把视图连同其历史一起删掉，再 create 等于新建一个全新视图，`view.history()` 永远只有一条，`currentVersion().versionId()` 永远是 1，所有版本演进信息都丢了。而 Iceberg core 的 `SQLView` 本身是支持 `replace()` 的，会保留历史并新增一个 version，这正是用户期望的语义。

修复思路是绕开 Spark 的 `ViewCatalog` API：自定义一个 Iceberg 接口 `SupportsReplaceView extends ViewCatalog`，声明一个 `replaceView(...)` 方法；让 `SparkCatalog` 实现这个接口，在 `replaceView` 内部直接调用 Iceberg 的 `asViewCatalog.buildView(...).replace()`，从而保留视图历史。然后在 `CreateV2ViewExec` 的 replace 分支做模式匹配：如果 catalog 是 `SupportsReplaceView`，就调 `replaceView`（保历史）；否则退回到原来的 drop+create 逻辑，保持对其他 catalog 的兼容。

附带的小清理是把 `createQueryColumnNames` 的拼接从 JDK `StringJoiner` 换成 Iceberg relocated 的 `Joiner`，并把原本 private 的常量 `SparkView.QUERY_COLUMN_NAMES` 提为 public，以便 `SparkCatalog` 在 create 和 replace 两条路径上复用同一个属性键。

## 如何达成设计目的

实现分四步：(1) 新建 `SupportsReplaceView` 接口，继承 Spark 的 `ViewCatalog`，声明 `replaceView` 方法签名；(2) `SparkCatalog` 改为同时实现 `ViewCatalog` 和 `SupportsReplaceView`，新增 `replaceView` 实现，内部走 Iceberg `buildView(...).replace()`；(3) `CreateV2ViewExec` 的 replace 分支用模式匹配区分"支持 replace 的 Iceberg catalog"和"普通 catalog"，前者调 `replaceView`，后者保留原 drop+create；(4) `SparkView.QUERY_COLUMN_NAMES` 从 private 提为 public，`SparkCatalog` 用 `COMMA_JOINER.join(queryColumnNames)` 替换原 `StringJoiner`，并把硬编码字符串换成常量。新增测试 `createOrReplaceViewKeepsViewHistory` 验证 replace 后 history 长度为 2、versionId 为 2、schema 数为 2。

## 修改详情

### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SupportsReplaceView.java

**修改目的**：定义一个 Iceberg 自有的、绕开 Spark `ViewCatalog` 缺失 `replace` 的扩展接口。

**工作逻辑**：新建接口，`extends org.apache.spark.sql.connector.catalog.ViewCatalog`，声明唯一方法 `replaceView(Identifier, String sql, String currentCatalog, String[] currentNamespace, StructType schema, String[] queryColumnNames, String[] columnAliases, String[] columnComments, Map<String,String> properties)`，可抛 `NoSuchViewException`/`NoSuchNamespaceException`。参数集合与 Spark `createView` 保持一致，便于 `CreateV2ViewExec` 用相同数据调用。之所以继承 `ViewCatalog`，是为了让 `SparkCatalog` 仍能作为 `ViewCatalog` 暴露给 Spark，同时通过 `instanceof SupportsReplaceView` 探测到 replace 能力。

### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java

**修改目的**：让 Iceberg 的 `SparkCatalog` 真正提供 `replaceView` 实现，并复用 `SparkView.QUERY_COLUMN_NAMES` 常量与 `Joiner`。

**工作逻辑**：

1. 类签名从 `implements ViewCatalog` 改为 `implements ViewCatalog, SupportsReplaceView`。
2. import 调整：移除 `java.util.StringJoiner`，新增 `org.apache.iceberg.relocated.com.google.common.base.Joiner`；新增静态字段 `COMMA_JOINER = Joiner.on(",")`。
3. `createView` 方法内原本用 `StringJoiner` 拼 `queryColumnNames` 并写入属性键 `"spark.query-column-names"`（硬编码字符串），改为 `COMMA_JOINER.join(queryColumnNames)` 写入 `SparkView.QUERY_COLUMN_NAMES` 常量；构造 ImmutableMap 时从 `build()` 改为 `buildKeepingLast()`，避免 `rebuildCreateProperties` 与显式 put 出现重复键时报错（后者覆盖前者）。
4. 新增 `replaceView` 方法，逻辑与 `createView` 几乎一致——构造 iceberg schema、组装 properties（同样写 `QUERY_COLUMN_NAMES`）、调 `asViewCatalog.buildView(ident).withDefaultCatalog(...).withDefaultNamespace(...).withQuery("spark", sql).withSchema(...).withLocation(...).withProperties(...)`——区别只在于最后调 `.replace()` 而非 `.create()`。`replace()` 会在 Iceberg 侧保留 history 并追加新 version。捕获 `org.apache.iceberg.exceptions.NoSuchNamespaceException` 转成 Spark 的 `NoSuchNamespaceException`；若底层 catalog 不支持视图（`asViewCatalog == null`），抛 `UnsupportedOperationException`。

### spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/CreateV2ViewExec.scala

**修改目的**：在 `CREATE OR REPLACE VIEW` 执行节点里，对支持 `replaceView` 的 catalog 走保历史路径。

**工作逻辑**：原 replace 分支无条件 `if (catalog.viewExists(ident)) catalog.dropView(ident)` 然后 `catalog.createView(...)`。改为对 `catalog` 做模式匹配：

```scala
catalog match {
  case c: SupportsReplaceView =>
    c.replaceView(ident, queryText, currentCatalog, currentNamespace, viewSchema,
      queryColumnNames.toArray, columnAliases.toArray,
      columnComments.map(c => c.orNull).toArray, newProperties.asJava)
  case _ =>
    if (catalog.viewExists(ident)) catalog.dropView(ident)
    catalog.createView(ident, queryText, currentCatalog, currentNamespace, viewSchema,
      queryColumnNames.toArray, columnAliases.toArray,
      columnComments.map(c => c.orNull).toArray, newProperties.asJava)
}
```

`SupportsReplaceView` 是 Iceberg 接口，只有 Iceberg 的 `SparkCatalog` 会匹配到，其他 catalog 走原 drop+create 兼容路径。这样既解决了 Iceberg 视图丢失历史的问题，又不破坏对第三方 catalog 的通用支持。原来那句 `// FIXME: replaceView API doesn't exist in Spark 3.5` 也随之移除。

### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkView.java

**修改目的**：把 `QUERY_COLUMN_NAMES` 常量从 private 提为 public，供 `SparkCatalog` 复用。

**工作逻辑**：单行改动 `private static final String QUERY_COLUMN_NAMES = "spark.query-column-names";` → `public static final String QUERY_COLUMN_NAMES = "spark.query-column-names";`。`RESERVED_PROPERTIES` 集合原本就引用了这个常量，现在外部也能引用，保证 create/replace 写入的属性键与 `SparkView` 自身解析时读取的键一致，避免魔法字符串散落。

### spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java

**修改目的**：新增针对 `CREATE OR REPLACE VIEW` 保留历史的回归测试。

**工作逻辑**：新增 `createOrReplaceViewKeepsViewHistory`：
- 先 `CREATE VIEW viewWithHistoryAfterReplace (new_id COMMENT 'some ID', new_data COMMENT 'some data') AS SELECT id, data FROM <table> WHERE id <= 3`。
- 加载视图，断言 `history().size() == 1`、`sqlFor("spark")` 等于原 sql、`currentVersion().versionId() == 1`、`schemaId == 0`、`schemas().size() == 1`、schema 结构为两列 `new_id`/`new_data` 带注释。
- 再 `CREATE OR REPLACE VIEW viewWithHistoryAfterReplace (updated_id COMMENT 'updated ID') AS SELECT id FROM <table> WHERE id > 3`。
- 重新加载视图，断言 `history().size() == 2`（关键：旧实现会是 1）、`sqlFor("spark")` 等于新 sql、`currentVersion().versionId() == 2`、`schemaId == 1`、`schemas().size() == 2`、schema 结构变为单列 `updated_id`。这一组断言精确刻画了"replace 应作为新版本叠加，而非 drop+create 重置"的语义。

## 小结

这是一个针对 Spark 3.5 API 缺口的 workaround：Spark 3.5 的 `ViewCatalog` 没有 `replace`，Iceberg 此前用 drop+create 兜底，导致视图历史丢失。本提交通过自定义 `SupportsReplaceView` 接口、让 `SparkCatalog` 实现它、并在 `CreateV2ViewExec` 里按 catalog 类型分流，把 Iceberg 原生的 `View.replace()`（保历史）能力接了进来。同时顺手统一了 `QUERY_COLUMN_NAMES` 常量与 `Joiner` 的使用，新增测试精确验证了 version/history/schema 的演进语义。
