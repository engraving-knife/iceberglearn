# 提交序号 1508 短哈希 b428fbc59 分析

## 提交信息
- 哈希：b428fbc59bd1579f4dc918a5cd48fce667d81ce1
- 日期：2024-12-18
- 作者：Ppei-Wang <ppeiwang2@gmail.com>
- 消息：Spark 3.4,3.5: Use correct identifier in view DESCRIBE cmd (#11751)

## 总体目的

本提交修复了 Spark 3.4 和 3.5 中 Iceberg 视图（view）的 `DESCRIBE EXTENDED` 命令输出"View Catalog and Namespace"信息不正确的问题。修改前，`DescribeV2ViewExec` 在计算视图所属的 catalog 和 namespace 时，使用的是 `view.currentCatalog +: view.currentNamespace.toSeq`，即从视图版本（view version）中读取"当前 catalog"和"当前 namespace"。但这些字段的语义是"创建该视图版本时所在会话的 catalog/namespace 上下文"，并不一定等于视图实际存储位置的 catalog 和 namespace。

具体来说，`view.currentVersion().defaultCatalog()` 通常为 null（创建时未显式指定 catalog），而 `defaultNamespace()` 是创建时的会话命名空间。当一个视图在不同命名空间的会话中被加载、或当视图被创建在非当前命名空间时，使用这些"创建上下文"字段会导致 DESCRIBE 输出的 catalog/namespace 与视图真实位置不符。

本提交改为使用 `view.name.split("\\.").take(2)` 来提取 catalog 和 namespace。`view.name` 是视图的全限定名（如 `catalog.namespace.viewname`），取前两段恰好对应 catalog 和 namespace，这正是视图真实存储位置的标识。此外还顺带补充了注释说明为何在 DESCRIBE 输出中省略 view text（因为它已在 SHOW CREATE TABLE 中展示，且在 DESCRIBE 中会产生奇怪的格式）。

## 如何达成设计目的

本提交通过修改两个 Spark 版本（3.4 和 3.5）中各自的 `DescribeV2ViewExec.scala`，将 viewCatalogAndNamespace 的计算方式从"创建上下文"改为"全限定名解析"，并新增测试覆盖默认命名空间和非当前命名空间两种场景。

### 修改详情

#### spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/DescribeV2ViewExec.scala
#### spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/DescribeV2ViewExec.scala

这两个文件是 Spark 各自版本的视图 DESCRIBE 执行算子，修改内容完全相同：

1. 核心修改：`viewCatalogAndNamespace` 的计算由
   ```scala
   val viewCatalogAndNamespace: Seq[String] = view.currentCatalog +: view.currentNamespace.toSeq
   ```
   改为
   ```scala
   val viewCatalogAndNamespace: Seq[String] = view.name.split("\\.").take(2)
   ```
   `view.name` 形如 `catalogName.namespace.viewName`，按 `.` 分割后取前两段即为 catalog 和 namespace。这确保了 DESCRIBE 输出的"View Catalog and Namespace"行始终反映视图的真实存储位置，而非创建时的会话上下文。

2. 补充注释：在 DESCRIBE 输出构造处新增注释"omitting view text here because it is shown as part of SHOW CREATE TABLE and can result in weird formatting in the DESCRIBE output"，说明为何不在 DESCRIBE 输出中包含视图文本。这属于文档性说明，不改变行为。

#### spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java
#### spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java

两个版本的测试类各新增两个测试用例（3.4 用 `@Test`，3.5 用 `@TestTemplate`）：

1. `createAndDescribeViewInDefaultNamespace()`：在默认命名空间创建视图，然后 DESCRIBE EXTENDED，验证输出中"View Catalog and Namespace"为 `catalogName.namespace`。同时断言 `view.currentVersion().defaultCatalog()` 为 null、`view.name()` 等于 `ViewUtil.fullViewName(catalogName, identifier)`、`defaultNamespace()` 等于当前 NAMESPACE。这覆盖了常规场景。

2. `createAndDescribeViewWithoutCurrentNamespace()`：创建一个独立的命名空间 `test_namespace`，在该命名空间下创建视图，然后 DESCRIBE EXTENDED，验证"View Catalog and Namespace"为 `catalogName.test_namespace`，而非当前会话的 NAMESPACE。这直接覆盖了"视图不在当前命名空间"这一原先会出错的场景，是本次修复的关键回归测试。

两个测试都引入了 `ViewUtil` 用于构造期望的全限定视图名，使断言更健壮。

## 小结

本提交修复了 Spark 3.4/3.5 中 Iceberg 视图 DESCRIBE 命令输出 catalog/namespace 信息不正确的问题。根因是原先使用视图版本的"创建上下文"字段（`currentCatalog`/`currentNamespace`）来推断视图位置，而这些字段可能为 null 或指向创建时的会话命名空间，并非视图真实存储位置。修复方案改为从视图全限定名 `view.name` 解析前两段作为 catalog 和 namespace，保证输出始终准确。新增的两个测试覆盖了默认命名空间和非当前命名空间两种场景，其中后者是该 bug 的直接回归测试。修改同时适用于 Spark 3.4 和 3.5 两个版本，保持行为一致。
