# 提交 1577 472ec6cbd 分析

## 提交信息
- 哈希：472ec6cbdcaa24773ef5c64c9444ae933805381d
- 日期：2025-01-13（Mon Jan 13 16:41:07 2025 +0100）
- 作者：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- 消息：Core: Add tests for catalogs supporting empty namespaces (#9890)

## 总体目的

本提交系统性地为 Iceberg Catalog 实现引入"空命名空间（empty namespace）"的测试覆盖，并修复 `InMemoryCatalog` 和 `JdbcCatalog` 在处理空命名空间时的若干不一致行为。

在 Iceberg 中，`Namespace.empty()` 表示一个无层级的根命名空间（即 `Namespace.of()` 无参数版本）。不同的 Catalog 实现对空命名空间的支持差异很大：有的 Catalog 不允许创建/查询空命名空间（如 REST Catalog 要求必须有命名空间），而有的 Catalog（如 `InMemoryCatalog`、`JdbcCatalog`）则可以支持在空命名空间下创建表、视图以及属性。之前测试套件缺乏对空命名空间场景的统一覆盖，导致：

1. `InMemoryCatalog.listTables(Namespace)` 与 `listViews(Namespace)` 中存在 `namespace.isEmpty() || t.namespace().equals(namespace)` 的回退逻辑，会把所有表/视图都列入空命名空间，这并不符合"列出空命名空间下表"的语义；
2. `InMemoryCatalog.listNamespaces` 在按前缀过滤时未排除空命名空间本身，导致空命名空间会出现在结果列表中；
3. `JdbcUtil.stringToNamespace("")` 会把空字符串拆分成含一个空字符串元素的数组，而非返回 `Namespace.empty()`，造成空命名空间在 JDBC 序列化/反序列化时丢失语义；
4. Spark `TestViews` 中 `SHOW VIEWS` 的断言把 Iceberg catalog 中的视图（属于 `NAMESPACE`）和 Spark session 临时视图混在一起断言，对 REST catalog 这种不支持顶层（空命名空间）视图的场景不适用。

本提交通过引入一个 `supportsEmptyNamespace()` 测试钩子（默认 false），为支持空命名空间的 Catalog（InMemory、Jdbc）添加专门的测试用例，并修复上述实现层的不一致。

## 如何达成设计目的

整体思路分为三步：(1) 在 `CatalogTests` 和 `ViewCatalogTests` 抽象测试基类中新增"空命名空间"相关测试方法，并用 `assumeThat(supportsEmptyNamespace())` 做条件跳过；(2) 在各具体 Catalog 的测试子类中按需覆盖 `supportsEmptyNamespace()` 返回 true；(3) 修复 `InMemoryCatalog` 和 `JdbcUtil` 的实现缺陷，让空命名空间的行为与测试期望一致；(4) 调整 Spark `TestViews` 的断言，区分 Iceberg catalog 视图与临时视图，并对 REST catalog 做特殊处理。

### 修改详情

#### `core/src/main/java/org/apache/iceberg/inmemory/InMemoryCatalog.java`

**修改目的**：修正 `InMemoryCatalog` 在 `listTables`、`listViews`、`listNamespaces` 中对空命名空间的处理。

**工作逻辑**：
- `listTables` 与 `listViews`：把 `.filter(t -> namespace.isEmpty() || t.namespace().equals(namespace))` 改为 `.filter(t -> t.namespace().equals(namespace))`。原先的回退逻辑"如果传入空命名空间就返回所有表"是错误的——它混淆了"列出空命名空间下的表"与"列出所有表"两个语义。修改后只有 `t.namespace()` 真正等于传入 namespace（包括两者都为空）时才返回，语义清晰。
- `listNamespaces`：在按前缀过滤之前增加 `.filter(n -> !n.isEmpty())`，避免把空命名空间本身作为结果返回（空命名空间是"根"，不应作为子命名空间列出）。这样 `listNamespaces()` 和 `listNamespaces(Namespace.empty())` 都不会返回空命名空间。

#### `core/src/main/java/org/apache/iceberg/jdbc/JdbcUtil.java`

**修改目的**：修复 `stringToNamespace(String)` 对空字符串的处理。

**工作逻辑**：
```java
   static Namespace stringToNamespace(String namespace) {
     Preconditions.checkArgument(namespace != null, "Invalid namespace %s", namespace);
+    if (namespace.isEmpty()) {
+      return Namespace.empty();
+    }
+
     return Namespace.of(Iterables.toArray(SPLITTER_DOT.split(namespace), String.class));
   }
```

原实现直接对空字符串调用 `SPLITTER_DOT.split("")`，会产生一个包含一个空字符串元素的数组，导致 `Namespace.of("")` 而非 `Namespace.empty()`。新增的早返回逻辑让空字符串正确映射到 `Namespace.empty()`，保证 JDBC 持久化的空命名空间能正确反序列化。

#### `core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java`

**修改目的**：在抽象测试基类中新增 `supportsEmptyNamespace()` 钩子和 4 个空命名空间测试用例。

**工作逻辑**：
- 新增 `supportsEmptyNamespace()` 默认返回 `false`，子类按需覆盖。
- 新增 import `Assumptions.assumeThat` 用于条件跳过。
- `listNamespacesWithEmptyNamespace()`：不依赖 `supportsEmptyNamespace()`，验证任何 Catalog（即使不支持空命名空间）都不应把空命名空间作为结果返回——`namespaceExists(Namespace.empty())` 为 false，`listNamespaces()` 与 `listNamespaces(Namespace.empty())` 都不应包含空命名空间。这是一个对所有 Catalog 都成立的通用断言。
- `createAndDropEmptyNamespace()`：用 `assumeThat(supportsEmptyNamespace())` 跳过不支持的 Catalog。验证创建空命名空间后 `namespaceExists` 返回 true，`listNamespaces()` 返回空（保留 TODO 注释讨论此处期望行为），删除后 `namespaceExists` 返回 false。
- `namespacePropertiesOnEmptyNamespace()`：验证在空命名空间上设置、加载、移除属性。
- `listTablesInEmptyNamespace()`：验证在空命名空间下创建的表能被 `listTables(Namespace.empty())` 列出，且不会与其它命名空间下的表混淆。

#### `core/src/test/java/org/apache/iceberg/view/ViewCatalogTests.java`

**修改目的**：在 View Catalog 抽象测试基类中新增 `supportsEmptyNamespace()` 钩子和 `listViewsInEmptyNamespace()` 测试。

**工作逻辑**：与 `CatalogTests` 对称，新增 `supportsEmptyNamespace()` 默认 false，以及一个验证在空命名空间下创建视图并通过 `listViews(Namespace.empty())` 列出的测试，同样用 `assumeThat` 做条件跳过。

#### `core/src/test/java/org/apache/iceberg/inmemory/TestInMemoryCatalog.java` 与 `TestInMemoryViewCatalog.java`

**修改目的**：声明 `InMemoryCatalog` 支持空命名空间。

**工作逻辑**：两个测试子类都覆盖 `supportsEmptyNamespace()` 返回 `true`，激活基类中相关的空命名空间测试。

#### `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcCatalog.java`

**修改目的**：声明 `JdbcCatalog` 支持空命名空间，并修正既有测试断言。

**工作逻辑**：
- 覆盖 `supportsEmptyNamespace()` 返回 `true`。
- 注意：原文件中 `supportsNamespaceProperties()` 与 `supportsNestedNamespaces()` 的覆盖被调整（diff 显示把 `supportsNamespaceProperties` 改名为 `supportsNestedNamespaces`，再把第二个改为 `supportsEmptyNamespace`）。实际上从 diff 看是把原本两个相邻的覆盖方法中前一个的方法名从 `supportsNamespaceProperties` 改为 `supportsNestedNamespaces`，后一个从 `supportsNestedNamespaces` 改为 `supportsEmptyNamespace`——这是一个隐患：如果原文件中 `supportsNamespaceProperties` 已经在别处正确定义，这里相当于删除了 `supportsNamespaceProperties` 的覆盖。但更可能是 diff 上下文显示的问题，实际意图是新增 `supportsEmptyNamespace` 覆盖。需要结合 1.4.x 回迁时验证 `supportsNamespaceProperties` 是否仍被正确覆盖。
- 调整 `listNamespaces` 断言：原断言期望 5 个命名空间（含空字符串 `""`），现改为 4 个（不含空字符串），与 `InMemoryCatalog` 的修复一致——空命名空间不应出现在 `listNamespaces` 结果中。

#### `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcUtil.java`

**修改目的**：新增 `emptyNamespaceInIdentifier()` 测试验证 `JdbcUtil.stringToTableIdentifier("", "tblName")` 返回 `TableIdentifier.of(Namespace.empty(), "tblName")`，覆盖 `stringToNamespace` 空字符串修复。

#### `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcViewCatalog.java`

**修改目的**：覆盖 `supportsEmptyNamespace()` 返回 `true`，激活 View Catalog 基类的空命名空间测试。

#### `spark/v3.4/spark-extensions/.../TestViews.java` 与 `spark/v3.5/spark-extensions/.../TestViews.java`

**修改目的**：修正 `SHOW VIEWS` 相关断言，区分 Iceberg catalog 视图与 Spark session 临时视图，并处理 REST catalog 不支持顶层视图的情况。

**工作逻辑**：
- 把原本用 `contains(row(...), row(...), tempView)` 一次性断言多类视图的方式，拆分为对 `v1Row`/`v2Row`/`v3Row`（Iceberg catalog 视图）和 `tempView`（session 临时视图）的分别断言。
- 对 `SHOW VIEWS IN <catalogName>`（不带命名空间）：由于 REST catalog 要求命名空间，会跳过该断言；对非 REST catalog，断言只含 `tempView` 而**不**含 Iceberg catalog 视图（`doesNotContain(v1Row, v2Row, v3Row)`）。这反映了"在 catalog 顶层（无命名空间）列出视图时，不应返回属于具体命名空间的视图"的语义。
- 对 `SHOW VIEWS LIKE 'pref*'`：增加 `.doesNotContain(v1Row, tempView)`，确保通配只返回匹配前缀的视图。
- 对 `SHOW VIEWS IN spark_catalog.<NAMESPACE>` 与 `SHOW VIEWS IN global_temp`：增加 `.doesNotContain(v1Row, v2Row, v3Row)`，确保 Iceberg catalog 的视图不会泄露到 spark_catalog/global_temp 的视图列表中。
- v3.4 中还把原本的 `SHOW VIEWS IN spark_catalog.default` 改为先 `CREATE NAMESPACE IF NOT EXISTS spark_catalog.<NAMESPACE>` 再 `SHOW VIEWS IN spark_catalog.<NAMESPACE>`，让命名空间存在后再断言，避免因命名空间不存在导致的行为差异。

## 小结

- **成效**：为 Iceberg Catalog 实现建立了统一的"空命名空间"测试覆盖框架，修复了 `InMemoryCatalog` 与 `JdbcUtil` 在空命名空间下的行为缺陷，并修正了 Spark `TestViews` 中混淆 Iceberg catalog 视图与 session 临时视图的断言。这提升了不同 Catalog 实现在边界场景下的一致性，并为后续新增 Catalog 实现提供了清晰的测试模板。
- **影响范围**：涉及 core 模块 2 个产品文件（`InMemoryCatalog`、`JdbcUtil`）的缺陷修复，6 个测试文件的新增/调整，以及 spark v3.4/v3.5 两个 `TestViews` 的断言重构。产品代码改动小但语义重要。
- **回迁到 1.4.x 的注意事项**：本提交包含产品代码修复（`InMemoryCatalog` 的 listTables/listViews/listNamespaces 行为变更、`JdbcUtil.stringToNamespace` 空字符串处理），如果 1.4.x 中存在相同缺陷，**建议回迁产品代码修复部分**（尤其是 `JdbcUtil.stringToNamespace` 的空字符串处理，这是一个明显的 bug）。测试部分可视 1.4.x 的测试基础设施情况选择性回迁。回迁时需特别注意 `TestJdbcCatalog` 中 `supportsNamespaceProperties`/`supportsNestedNamespaces` 覆盖方法的对应关系，避免遗漏。
