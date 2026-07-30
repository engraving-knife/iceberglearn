# 提交 0815：Spark 3.4, 3.5: SHOW VIEWS failed with AssertionError (#10442)

## 提交信息

- **序号**：0815 / 4088
- **哈希**：59e937761aac2dddf14d331268aeb0b9adec6908
- **短哈希**：59e937761
- **日期**：2024-06-05 10:31:49 -0700
- **作者**：Huaxin Gao
- **提交说明**：Spark 3.4, 3.5: SHOW VIEWS failed with AssertionError (#10442)
- **PR/Issue**：#10442

## 总体目的

修复 Iceberg Spark 3.4 / 3.5 扩展模块中 `SHOW VIEWS` 命令在当前 catalog 为 `spark_catalog`（Spark 内置 V1 SessionCatalog 包装）时抛出 `AssertionError` 的 bug。

### Bug 根因分析

`RewriteViewCommands` 是 Iceberg 注入到 Spark 分析器的规则，用于把 Spark 原生的 `ShowViews`、`CreateView`、`DropView` 等 plan 节点改写为 Iceberg 自己的 `ShowIcebergViews`、`CreateIcebergView`、`DropIcebergView`。

bug 出在 `SHOW VIEWS`（不带命名空间，对应 `UnresolvedNamespace(Seq())`，即空 namespace）的处理上。修复前的代码：

```scala
case ShowViews(UnresolvedNamespace(Seq()), pattern, output)
  if ViewUtil.isViewCatalog(catalogManager.currentCatalog) =>
  ShowIcebergViews(ResolvedNamespace(catalogManager.currentCatalog, catalogManager.currentNamespace),
    pattern, output)

case ShowViews(UnresolvedNamespace(CatalogAndNamespace(catalog, ns)), pattern, output)
  if ViewUtil.isViewCatalog(catalog) =>
  ShowIcebergViews(ResolvedNamespace(catalog, ns), pattern, output)
```

第一个 case 用了 Scala 的 guard（`if ViewUtil.isViewCatalog(...)`）。`ViewUtil.isViewCatalog` 实现是：

```scala
def isViewCatalog(catalog: CatalogPlugin): Boolean = {
  catalog.isInstanceOf[ViewCatalog]
}
```

当用户执行 `USE spark_catalog` 后再执行 `SHOW VIEWS` 时：

1. 当前 catalog 是 `spark_catalog`（Spark 的 `V2SessionCatalog`，不实现 `ViewCatalog`）。
2. 第一个 case 的模式 `ShowViews(UnresolvedNamespace(Seq()), ...)` 匹配成功，但 guard `ViewUtil.isViewCatalog(spark_catalog)` 返回 `false`。
3. Scala 模式匹配继续尝试下一个 case：`ShowViews(UnresolvedNamespace(CatalogAndNamespace(catalog, ns)), ...)`。
4. 第二个 case 需要对 `UnresolvedNamespace` 内部的 `Seq()`（空 namespace）调用 Spark 的 `CatalogAndNamespace` 提取器。
5. `CatalogAndNamespace` 是 Spark `LookupCatalog` 中设计的提取器，用于从非空的多段命名空间中分离出 catalog 名与剩余 namespace。对空 `Seq()` 调用该提取器并非其设计用途，在 Spark 3.4/3.5 的实现中触发了 `AssertionError`。

简言之：**guard 失败后，Scala 模式匹配 fall-through 到下一个 case，导致 `CatalogAndNamespace` 提取器被应用到空 namespace，触发了 Spark 内部的断言失败**。

### 修复方案

把第一个 case 的 guard 去掉，改为在 case body 内部用 `if-else` 判断：

```scala
case view @ ShowViews(UnresolvedNamespace(Seq()), pattern, output) =>
  if (ViewUtil.isViewCatalog(catalogManager.currentCatalog)) {
    ShowIcebergViews(ResolvedNamespace(catalogManager.currentCatalog, catalogManager.currentNamespace),
      pattern, output)
  } else {
    view
  }
```

- 用 `view @` 绑定整个 `ShowViews` 节点。
- 去掉 guard，case 无条件匹配所有 `ShowViews(UnresolvedNamespace(Seq()), ...)`。
- 在 body 内判断当前 catalog 是否为 view catalog：
  - 是 → 改写为 `ShowIcebergViews`（与修复前一致）。
  - 否 → 返回原始 `view` 不变，让 Spark 自己的 `ResolveSessionCatalog` 等规则按 V1 路径处理。
- 因为第一个 case 已经匹配了所有空 namespace 的 `ShowViews`，第二个 case（带 `CatalogAndNamespace`）永远不会对空 `Seq()` 求值，从而避免了 `AssertionError`。

注意第二个 case 保持不变（仍带 guard），因为它处理的是非空 namespace（如 `SHOW VIEWS IN cat.db`），`CatalogAndNamespace` 对非空 namespace 的调用是安全的。

## 如何达成设计目的

修改思路是利用 Scala 模式匹配的求值顺序：guard 失败会触发 fall-through 并对后续 case 的模式求值（包括提取器调用）。把 guard 移到 body 内部后，case 在模式匹配阶段就匹配成功，后续 case 不再被尝试，避免了对空 namespace 调用 `CatalogAndNamespace` 提取器。

`view @` 绑定让 body 内可以返回原始 plan 节点（而非构造新对象），语义上等价于"不改写"，让 Spark 原生规则接管。

修复同时作用于 Spark 3.4 和 3.5 两个版本模块，改动完全一致。

## 修改详情

### `spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/RewriteViewCommands.scala`

（`spark/v3.5/.../RewriteViewCommands.scala` 改动完全一致）

**修改目的**：消除 `SHOW VIEWS`（空 namespace）在 `spark_catalog` 下触发 `AssertionError` 的根因。

**工作逻辑**：
- 修复前：第一个 case 带 guard，guard 失败时 fall-through 到第二个 case，第二个 case 的 `CatalogAndNamespace` 提取器对空 `Seq()` 求值触发 Spark 断言。
- 修复后：第一个 case 无 guard，匹配所有空 namespace 的 `ShowViews`；body 内 `if-else` 决定改写还是放行。第二个 case 不再对空 namespace 求值。
- 其余 case（`DropView`、`CreateView`、带 `CatalogAndNamespace` 的 `ShowViews`、`UnresolvedView`）不变。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java`

（`spark/v3.5/.../TestViews.java` 改动一致，只是 `@Test` 注解不同）

**修改目的**：补充覆盖 `spark_catalog` 下 `SHOW VIEWS` 的回归测试。

**工作逻辑**：在已有的 view 列表测试末尾追加：

```java
sql("USE spark_catalog");
assertThat(sql("SHOW VIEWS")).contains(tempView);
assertThat(sql("SHOW VIEWS IN default")).contains(tempView);
```

- `USE spark_catalog` 切换当前 catalog 为内置 `spark_catalog`。
- `SHOW VIEWS` 验证空 namespace 路径不再抛 `AssertionError`，且能列出先前创建的临时 view。
- `SHOW VIEWS IN default` 验证带命名空间路径同样正常。

### `spark/v3.4/spark-runtime/src/integration/java/org/apache/iceberg/spark/SmokeTest.java`

（`spark/v3.5/.../SmokeTest.java` 改动一致）

**修改目的**：在集成烟雾测试中覆盖 `SHOW VIEWS` 基本路径。

**工作逻辑**：新增 `showView` 测试方法：

```java
@Test
public void showView() {
  sql("DROP VIEW IF EXISTS %s", "test");
  sql("CREATE VIEW %s AS SELECT 1 AS id", "test");
  assertThat(sql("SHOW VIEWS")).contains(row("default", "test", false));
}
```

该方法在默认 catalog（Iceberg catalog）下创建并列举 view，确保基本路径正常。引入 `assertj` 的 `assertThat` 静态导入。

## 小结

- **成效**：修复后 `USE spark_catalog; SHOW VIEWS;` 不再抛 `AssertionError`，能正常列出 `spark_catalog` 下的 view；`SHOW VIEWS IN default` 也正常工作。Iceberg catalog 下的 `SHOW VIEWS` 行为不变（仍改写为 `ShowIcebergViews`）。
- **影响范围**：仅 Spark 3.4 与 3.5 的 `spark-extensions` 模块（Scala 规则）与对应测试。不改变 Iceberg catalog 的 view 处理逻辑，只影响"当前 catalog 非 Iceberg view catalog 时的空 namespace SHOW VIEWS"这一边界场景。
- **回迁注意事项**：
  1. 1.4.x 分支需确认是否已引入 `RewriteViewCommands` 与 view 支持相关代码。若 1.4.x 已有该文件且存在同样的 guard 写法，直接照搬本提交即可。
  2. 改动同时覆盖 Spark 3.4 和 3.5。1.4.x 若还维护其它 Spark 版本（如 3.2、3.3），需检查这些版本是否存在同样问题；旧版本 Spark 的 `CatalogAndNamespace` 实现可能不同，需验证。
  3. 回归测试（`TestViews.java` 与 `SmokeTest.java`）建议一并回迁，确保 `spark_catalog` 下 `SHOW VIEWS` 路径被持续覆盖。
  4. 该 bug 的根因在于 Scala guard 与提取器的交互——guard 失败会触发后续 case 的提取器求值。回迁时若有类似的"guard + 后续 case 含提取器"结构，应检查是否存在同类风险。
