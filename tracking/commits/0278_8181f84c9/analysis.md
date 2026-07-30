# 提交 0278：API, Core: Add sqlFor API to views to handle resolving a representation for a dialect(#9247)

## 提交信息

- **序号**：0278 / 4088
- **哈希**：8181f84c96e6ebc66910e3c74c1d1b91fe28f01c
- **短哈希**：8181f84c9
- **日期**：2023-12-15 12:58:35 -0800
- **作者**：Amogh Jahagirdar <amogh@tabular.io>
- **提交说明**：API, Core: Add sqlFor API to views to handle resolving a representation for a dialect(#9247)
- **PR/Issue**：#9247

## 总体目的

本提交为 Iceberg 视图（View）引入了按 SQL 方言解析视图表示（representation）的公共 API：`View.sqlFor(String dialect)`。这是 Iceberg 多方言视图支持走向"可被引擎直接消费"的关键一步，使引擎能够根据自身方言（spark、trino 等）从视图版本中取回最合适的 SQL 文本，而不必由各引擎各自实现一遍解析逻辑。

背景是 Iceberg 视图模型支持"一个视图、多个方言表示"：同一个 `ViewVersion` 可携带多份 `ViewRepresentation`，每份是一个带 `dialect`（如 "spark"、"trino"）和 `sql` 文本的 `SQLViewRepresentation`。这是为了解决不同查询引擎 SQL 语法差异的问题——同一逻辑在不同引擎里可能需要不同的 SQL 写法（比如 Trino 的 `USING X` 语法 Spark 不支持）。提交之前，视图虽然能存储多份方言表示，但 api 层没有标准方法让调用方"按方言取出"对应的那一份；引擎或用户要拿到合适的 SQL，只能自己遍历 `currentVersion().representations()` 做匹配，逻辑分散且容易出错。

`sqlFor` 的设计目的是把这套"按方言解析"的逻辑统一收口到 Iceberg 核心：调用方只需 `view.sqlFor("spark")`，Iceberg 内部负责遍历表示、按 dialect 匹配、并在找不到精确匹配时回退到一个合理的默认（"最接近"的方言）。这既降低了引擎集成成本，也保证了解析语义在所有 catalog 实现间一致。

值得注意的是，本提交的可行性直接依赖于前置提交 0276（PR #9302）——正是该提交把 `SQLViewRepresentation` 从 core 上移到 api，才使本提交能在 api 模块的 `View` 接口上声明返回 `SQLViewRepresentation` 的方法。两提交同属一个视图方言解析能力增强的工作链。

## 如何达成设计目的

提交分三层落地，遵循 Iceberg"接口在 api、实现在 core、测试在 core"的分层约定：

1. **api 层声明契约**：在 `View` 接口新增 `default SQLViewRepresentation sqlFor(String dialect)` 方法，默认抛 `UnsupportedOperationException`。这是 Iceberg 接口的惯用模式——default 实现拒绝执行，强制具体 `View` 实现覆写以提供真正能力，同时保证旧实现不会因新增接口方法而编译失败。

2. **core 层提供实现**：在 `BaseView`（core 模块中 `View` 的基类实现，所有具体视图如 `SQLView`、各 catalog 的视图实现都继承它）中覆写 `sqlFor`，实现"精确匹配优先、否则回退首个 SQL 表示、都没有则返回 null"的解析逻辑，并对入参做非空、非空串校验。

3. **测试层验证行为**：在 `ViewCatalogTests`（core 模块的视图 catalog 抽象测试基类，所有 catalog 实现的测试都继承它）中新增两个测试方法，分别覆盖多方言解析的正常路径与非法参数的边界路径，确保所有 catalog 实现自动获得这两组用例的覆盖。

"closest dialect"（最接近方言）的回退策略是设计的核心：当请求的 dialect 没有精确匹配时，返回遍历中遇到的第一个 SQL 表示而非 null。这是一种务实的选择——保证调用方总能拿到一份可用的 SQL 文本（前提是视图至少有一份 SQL 表示），避免因方言名拼写不一致等原因导致调用方拿到 null 而无法继续。Javadoc 中明确把这一行为记录下来，便于使用方理解语义。

## 修改详情

### `api/src/main/java/org/apache/iceberg/view/View.java`

**修改目的**：在公共 `View` 接口上声明 `sqlFor` 方法，确立按方言解析视图表示的 API 契约。

**工作逻辑**：

在 `View` 接口末尾（紧跟已有的 `uuid()` 默认方法之后）新增：

```java
/**
 * Returns the view representation for the given SQL dialect
 *
 * @return the view representation for the given SQL dialect, or null if no representation could
 *     be resolved
 */
default SQLViewRepresentation sqlFor(String dialect) {
  throw new UnsupportedOperationException(
      "Resolving a sql with a given dialect is not supported");
}
```

- 方法返回类型为 `SQLViewRepresentation`（由前置提交 0276 已迁入 api 模块，因此 api 可直接引用）。
- `default` 实现抛 `UnsupportedOperationException`，符合 Iceberg 接口扩展惯例：新增可选能力时用 default 方法保证二进制兼容性，同时以异常显式表明"未实现"，强制有能力的子类覆写。
- Javadoc 明确说明返回值语义：返回匹配方言的表示，若无法解析则返回 null。这指导了 core 实现的行为契约（虽然 default 抛异常，但实现类应遵循"返回 null 而非抛异常"的契约，由 `BaseView` 兑现）。

### `core/src/main/java/org/apache/iceberg/view/BaseView.java`

**修改目的**：为 `sqlFor` 提供具体的解析实现，统一所有继承 `BaseView` 的视图实现的方言解析行为。

**工作逻辑**：

新增 `import org.apache.iceberg.relocated.com.google.common.base.Preconditions;`，并在类末尾新增方法：

```java
/**
 * This implementation of sqlFor will resolve what is considered the "closest" dialect. If an
 * exact match is found, then that is returned. Otherwise, the first representation would be
 * returned. If no SQL representation is found, null is returned.
 */
@Override
public SQLViewRepresentation sqlFor(String dialect) {
  Preconditions.checkArgument(dialect != null, "Invalid dialect: null");
  Preconditions.checkArgument(!dialect.isEmpty(), "Invalid dialect: (empty string)");
  SQLViewRepresentation closest = null;
  for (ViewRepresentation representation : currentVersion().representations()) {
    if (representation instanceof SQLViewRepresentation) {
      SQLViewRepresentation sqlViewRepresentation = (SQLViewRepresentation) representation;
      if (sqlViewRepresentation.dialect().equals(dialect)) {
        return sqlViewRepresentation;
      } else if (closest == null) {
        closest = sqlViewRepresentation;
      }
    }
  }

  return closest;
}
```

逐段解析：

- **参数校验**：`Preconditions.checkArgument(dialect != null, "Invalid dialect: null")` 与 `!dialect.isEmpty()` 两道检查，分别拦截 null 与空串，抛 `IllegalArgumentException` 并给出明确消息。注意两个分支的消息不同（一个写 "null"，一个写 "(empty string)"），测试中对这两条消息都做了断言。这避免了空串静默走到回退逻辑导致返回"任意"表示的诡异行为。
- **遍历表示**：通过 `currentVersion().representations()` 获取当前视图版本的全部表示。`currentVersion()` 返回 `ViewVersion`，其 `representations()` 返回 `List<ViewRepresentation>`。
- **类型筛选**：用 `instanceof SQLViewRepresentation` 筛选 SQL 类型的表示，跳过未来可能出现的其他表示类型（如 `UnknownViewRepresentation`）。这是面向扩展的稳健写法。
- **精确匹配优先**：一旦发现 `sqlViewRepresentation.dialect().equals(dialect)` 立即返回——这是请求的方言的精确命中，是最优解。注意此处用的是大小写敏感的 `equals`（紧随其后的提交 0280 会把它改成 `equalsIgnoreCase`）。
- **回退记录首个**：未精确命中时，把"遍历中遇到的第一个 SQL 表示"记入 `closest`（仅当 `closest` 仍为 null 时赋值，保证只记第一个）。这样即便最终没找到精确匹配，也能回退到一个可用表示。
- **返回 closest**：循环结束后返回 `closest`。若整个版本里没有任何 SQL 表示，`closest` 仍为 null，按 Javadoc 契约返回 null。

整体策略即 Javadoc 所述"closest dialect"：精确优先，否则首个 SQL 表示兜底，都没有则 null。这是一种"尽力满足"的策略，保证调用方在多数情况下都能拿到一份可用 SQL。

### `core/src/test/java/org/apache/iceberg/view/ViewCatalogTests.java`

**修改目的**：为 `sqlFor` 新增正常路径与边界路径测试，并让所有继承该基类的 catalog 实现测试自动获得覆盖。

**工作逻辑**：

新增两个 `@Test` 方法：

1. **`testSqlForMultipleDialects`**：覆盖多方言的正常解析路径。
   - 创建命名空间（若 `requiresNamespaceCreate()`，不同 catalog 实现按需创建）。
   - 通过 `catalog().buildView(identifier)` 构建视图，schema 为 `SCHEMA`，并 `withQuery("spark", "select * from ns.tbl")` 与 `withQuery("trino", "select * from ns.tbl using X")` 注册两个方言的 SQL，然后 `create()`。
   - 断言 `view.sqlFor("spark").sql()` 等于 `"select * from ns.tbl"`——验证精确匹配 spark 方言。
   - 断言 `view.sqlFor("trino").sql()` 等于 `"select * from ns.tbl using X"`——验证精确匹配 trino 方言，且两种方言的 SQL 文本互不相同。
   - 断言 `view.sqlFor("unknown-dialect").sql()` 等于 `"select * from ns.tbl"`——验证回退逻辑：未知方言时返回"第一个" SQL 表示（spark 那一份），证明 closest 兜底生效。

2. **`testSqlForInvalidArguments`**：覆盖非法参数边界。
   - 同样构建一个带 spark 方言查询的视图。
   - `assertThatThrownBy(() -> view.sqlFor(null)).isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid dialect: null")`——验证 null 入参被拒绝且消息匹配。
   - `assertThatThrownBy(() -> view.sqlFor("")).isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid dialect: (empty string)")`——验证空串入参被拒绝且消息匹配（注意消息与 null 分支不同）。

这两个测试放在抽象基类 `ViewCatalogTests` 中意义重大：该类是所有 `ViewCatalog` 实现的共享测试契约，每个具体 catalog（如 REST catalog、JDBC catalog、Hadoop catalog 等）的测试类都继承它并自动跑这些用例。这意味着本提交一次编写，所有 catalog 实现的 `sqlFor` 行为都被验证，无需各 catalog 单独补测试，是 Iceberg 保证跨实现一致性的标准手法。

## 小结

本提交为 Iceberg 视图引入了按方言解析 SQL 表示的公共 API `View.sqlFor`，把原先分散在各引擎侧的方言匹配逻辑统一收口到核心。设计上层次分明：api 声明 default 拒绝式契约、core 的 `BaseView` 提供"精确优先 + 首个回退"实现、core 的抽象测试基类让所有 catalog 自动获得覆盖。回退策略（返回首个 SQL 表示而非 null）体现了"尽力满足调用方"的实用取向。该 API 是多方言视图能力走向完整消费链路的关键，前置提交 0276 为其铺路，后续提交 0280 会进一步把匹配改为大小写不敏感以增强健壮性。整体上这是 Iceberg 视图方言支持从"存储"迈向"易用消费"的标志性一步。
