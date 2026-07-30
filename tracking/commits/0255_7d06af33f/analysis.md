# 提交 0255：Core: Improve view/table detection when replacing a table/view (#9012)

## 提交信息

- **序号**：0255 / 4088
- **哈希**：7d06af33ff787c86e0edf14856b6873021e8ea45
- **短哈希**：7d06af33f
- **日期**：2023-12-10 22:52:07 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Improve view/table detection when replacing a table/view (#9012)
- **PR/Issue**：#9012

## 总体目的

这是一个实质性的核心逻辑改动，旨在解决 Iceberg 在「视图（View）与表（Table）同名命名空间冲突」场景下的检测缺陷。

Iceberg 在 1.4.x 阶段已经引入了 View（视图）的Catalog 级抽象（`ViewCatalog`），允许在同一个 Catalog 命名空间下同时管理表和视图。但表与视图共享同一套标识符（`TableIdentifier`），即 `ns.view` 既可能是一张表，也可能是一个视图。当用户对一个标识符执行 `replace`（替换）操作时，系统需要回答：该标识符当前到底是表还是视图？如果搞反了，行为就会很奇怪。

改动之前，当用户尝试用 `buildTable(ident).replaceTransaction()` 去替换一个实际是视图的标识符时，底层会走到 `TableOperations` 路径，发现"表不存在"，于是抛出一个语义错位的 `NoSuchTableException("Table does not exist: ns.view")`。反过来，用 `buildView(ident).replace()` 去替换一个实际是表的标识符时，会抛出 `NoSuchViewException("View does not exist: ns.table")`。这两种错误信息对用户来说具有误导性——根因并非"对象不存在"，而是"存在一个不同类型的同名对象"。测试代码里当时也明确留了两条 TODO 注释：`// TODO: replace should check whether the table exists as a view` 与 `// TODO: replace should check whether the view exists as a table`，说明这是已知的设计缺口。

本提交关闭了这两条 TODO：在执行 replace 之前先做交叉检测——替换表时若同名视图存在则抛 `AlreadyExistsException`，替换视图时若同名表存在也抛 `AlreadyExistsException`，从而把"类型冲突"这一根因以正确的异常类型和清晰的错误信息呈现给调用方。这对 Iceberg View 特性的健壮性、错误可诊断性以及与 Spark/Flink 等引擎的语义一致性都有直接意义。

## 如何达成设计目的

整体设计思路是在 replace 路径的最前端插入一道"类型冲突守卫"，在真正去加载/校验目标对象之前，先用 `viewExists`/`tableExists` 探测同名对象是否以对立类型存在。改动覆盖两类 Catalog 实现：

1. **REST Catalog（`RESTSessionCatalog`）**：在表构建器（TableBuilder）的 `replaceTransaction()` 中插入 `viewExists` 检测，在视图构建器（ViewBuilder）的 `replace()` 中插入 `tableExists` 检测。
2. **Metastore Catalog（`BaseMetastoreViewCatalog`）**：在视图的 `replace(ViewOperations)` 私有方法中插入 `tableExists` 检测；同时新增一个 `BaseMetastoreViewCatalogTableBuilder` 子类覆盖表构建器的 `replaceTransaction()`，插入 `viewExists` 检测，并通过覆写 `buildTable(...)` 让该子类生效。

测试侧则把两条对应的测试用例从期待 `NoSuchTableException`/`NoSuchViewException` 改为期待 `AlreadyExistsException`，并删除两条已实现的 TODO 注释与无用 import。改动结构清晰、对称，每条 replace 入口都补齐了对应的反向类型检测。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java`

**修改目的**：为 REST Catalog 的表 replace 与视图 replace 两类入口补上"同名对立类型存在"的冲突检测。

**工作逻辑**：

- 新增 import `org.apache.iceberg.exceptions.AlreadyExistsException`。
- 在内部表构建器的 [`replaceTransaction()`](core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java) 方法（约第 706 行）最前端加入守卫：`if (viewExists(context, ident)) { throw new AlreadyExistsException("View with same name already exists: %s", ident); }`。这样当用户对一个实为视图的标识符调用 `buildTable(ident).replaceTransaction()` 时，会立刻得到明确的冲突异常，而不是继续走到 `loadInternal` 之后才以 `NoSuchTableException` 收场。守卫之后原有的 `loadInternal(context, ident, snapshotMode)` 逻辑保持不变。
- 在内部视图构建器的 [`replace()`](core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java) 方法（约第 1178 行）最前端加入对称守卫：`if (tableExists(context, identifier)) { throw new AlreadyExistsException("Table with same name already exists: %s", identifier); }`，之后才调用 `replace(loadView())`。这样对一个实为表的标识符调用 `buildView(ident).replace()` 时，会以 `AlreadyExistsException` 失败，而非 `NoSuchViewException`。

注意这里 `replaceTransaction()`（表的事务式替换）被覆盖，但 `createOrReplaceTransaction()` 未被覆盖——这与测试中 `createOrReplaceTableViaTransactionThatAlreadyExistsAsView` 仍单独存在、且本提交未修改其断言相一致；本提交聚焦于纯 replace 路径的语义修正。

### `core/src/main/java/org/apache/iceberg/view/BaseMetastoreViewCatalog.java`

**修改目的**：为基于 metastore 的视图 Catalog 补齐表 replace 时的视图冲突检测，并补齐视图 replace 时的表冲突检测，使行为与 REST Catalog 对齐。

**工作逻辑**：

- 新增 import `org.apache.iceberg.Transaction`。
- 在私有方法 [`private View replace(ViewOperations ops)`](core/src/main/java/org/apache/iceberg/view/BaseMetastoreViewCatalog.java)（约第 186 行）最前端加入守卫：`if (tableExists(identifier)) { throw new AlreadyExistsException("Table with same name already exists: %s", identifier); }`，位于原有的 `if (null == ops.current()) { throw new NoSuchViewException(...); }` 之前。这覆盖了 `BaseViewBuilder.replace()` 与 `createOrReplace()`（当视图已存在时走 replace 分支）两条路径，确保视图替换前先确认同名表不存在。
- 新增公共方法 [`@Override public TableBuilder buildTable(TableIdentifier identifier, Schema schema)`](core/src/main/java/org/apache/iceberg/view/BaseMetastoreViewCatalog.java)，返回新定义的 `BaseMetastoreViewCatalogTableBuilder`，而不是父类 `BaseMetastoreCatalog` 默认的 `BaseMetastoreCatalogTableBuilder`。这是本次改动的关键——通过覆写 `buildTable` 把"带视图检测的表构建器"注入到所有继承 `BaseMetastoreViewCatalog` 的 Catalog（如 HiveCatalog、JdbcCatalog 等同时支持表与视图的 metastore catalog）中。
- 新增内部类 `BaseMetastoreViewCatalogTableBuilder extends BaseMetastoreCatalogTableBuilder`，类上注释明确写道 `/** The purpose of this class is to add view detection when replacing a table */`。该类持有 `identifier` 字段，构造时调用 `super(identifier, schema)`；并覆盖 [`replaceTransaction()`](core/src/main/java/org/apache/iceberg/view/BaseMetastoreViewCatalog.java)：先 `if (viewExists(identifier)) { throw new AlreadyExistsException("View with same name already exists: %s", identifier); }`，再 `return super.replaceTransaction();`。这样所有经 `buildTable(...).replaceTransaction()` 的表替换路径都会先检查同名视图是否存在。

父类 `BaseMetastoreCatalogTableBuilder.replaceTransaction()` 原本只会在 `ops.current() == null` 时抛 `NoSuchTableException`，现在子类先于它做视图检测，把"同名视图存在"这一更具体的根因前置暴露。

### `core/src/test/java/org/apache/iceberg/view/ViewCatalogTests.java`

**修改目的**：把两条此前断言旧错误语义的测试改为断言新的 `AlreadyExistsException` 语义，并清理已实现的 TODO 注释与无用 import。

**工作逻辑**：

- 删除 import `org.apache.iceberg.exceptions.NoSuchTableException`（不再被使用）。
- 在测试方法 [`replaceTableViaTransactionThatAlreadyExistsAsView()`](core/src/test/java/org/apache/iceberg/view/ViewCatalogTests.java)（约第 381 行）中：先创建一个名为 `ns.view` 的视图，然后断言用 `tableCatalog().buildTable(viewIdentifier, SCHEMA).replaceTransaction().commitTransaction()` 会失败。原断言为 `isInstanceOf(NoSuchTableException.class).hasMessageStartingWith("Table does not exist: ns.view")`，并伴有注释 `// replace transaction requires table existence` 与 `// TODO: replace should check whether the table exists as a view`。本次将断言改为 `isInstanceOf(AlreadyExistsException.class).hasMessageStartingWith("View with same name already exists: ns.view")`，并删除两条 TODO 注释，标记该设计缺口已实现。
- 在测试方法 [`replaceViewThatAlreadyExistsAsTable()`](core/src/test/java/org/apache/iceberg/view/ViewCatalogTests.java)（约第 447 行）中：先创建一张名为 `ns.table` 的表，然后断言用 `catalog().buildView(tableIdentifier)...replace()` 会失败。原断言为 `isInstanceOf(NoSuchViewException.class).hasMessageStartingWith("View does not exist: ns.table")`，伴有注释 `// replace view requires the view to exist` 与 `// TODO: replace should check whether the view exists as a table`。本次改为 `isInstanceOf(AlreadyExistsException.class).hasMessageContaining("Table with same name already exists: ns.table")`，并删除两条 TODO 注释。

由于 `ViewCatalogTests` 是抽象测试基类，被各具体 Catalog（REST、Hive、Jdbc、Memory 等）的测试继承，这两处断言变更会自动在所有继承者上生效，从而验证本提交在 REST 与 metastore 两类实现中的一致性。

## 小结

该提交通过在表/视图的 replace 入口前置"同名对立类型存在"的冲突检测，把原本语义错位的 `NoSuchTableException`/`NoSuchViewException` 修正为准确的 `AlreadyExistsException`，显著提升了 Iceberg View 与 Table 同名场景下的错误可诊断性与语义正确性。
