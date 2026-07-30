# 提交 0280：Core: Make sqlFor case insensitive for dialect check (#9311)

## 提交信息

- **序号**：0280 / 4088
- **哈希**：395e01e65d88953ced40a24bc4f53094f1a29a77
- **短哈希**：395e01e65
- **日期**：2023-12-16 10:03:53 -0800
- **作者**：Amogh Jahagirdar <amogh@tabular.io>
- **提交说明**：Core: Make sqlFor case insensitive for dialect check (#9311)
- **PR/Issue**：#9311

## 总体目的

本提交是对前一个提交 0278（PR #9247，引入 `View.sqlFor(String dialect)`）的快速健壮性补丁：把 `BaseView.sqlFor` 中方言匹配从大小写敏感的 `equals` 改为大小写不敏感的 `equalsIgnoreCase`，使 `sqlFor` 在调用方传入的 dialect 大小写与视图存储的 dialect 大小写不一致时仍能命中。

提交 0278 落地后，社区发现 `sqlFor` 的精确匹配用 `sqlViewRepresentation.dialect().equals(dialect)`，这意味着 `view.sqlFor("spark")` 能命中存储为 "spark" 的表示，但 `view.sqlFor("SPARK")` 或 `view.sqlFor("Spark")` 却无法命中——会回退到"第一个 SQL 表示"。这在大写、小写、混合大小写混用的实际场景下是显著的可用性问题：SQL 方言名本质上只是一个不区分大小写的标签（"spark"、"SPARK" 在语义上指同一个方言），用户/引擎很难保证与存储时的大小写完全一致。

动机来源于实际使用反馈：不同引擎、不同用户、不同工具在指定方言时的大小写习惯并不统一。例如有的客户端传 `"SPARK"`，有的传 `"spark"`；而视图在创建时写入的 dialect 字符串大小写又取决于创建方。如果 `sqlFor` 严格区分大小写，那么"对方言名大小写不一致就取不到精确表示"会成为反复触发的体验问题，且与"方言名应作为不区分大小写的标识符"的常识相悖。本提交通过一行改动把匹配改为 `equalsIgnoreCase`，消除这一类因大小写导致的误回退，使 `sqlFor` 的行为更符合用户对"方言名"这一概念的本能预期。

由于这是对刚引入 API 的及时修正（提交 0278 与 0280 相隔仅约一天，且同一作者），属于"趁接口尚未被广泛依赖前调整语义"的低风险改进，避免了未来存量调用方形成大小写敏感的依赖后再改的迁移成本。

## 如何达成设计目的

提交通过两处改动达成目的：

1. **实现侧**：在 `BaseView.sqlFor` 的精确匹配分支，把 `sqlViewRepresentation.dialect().equals(dialect)` 改为 `sqlViewRepresentation.dialect().equalsIgnoreCase(dialect)`。`equalsIgnoreCase` 是 `String` 提供的大小写不敏感比较方法，在比较前对两个字符串做大小写折叠（基于 Unicode 规则，对 ASCII 字母做 A-Z/a-z 折叠），从而 "spark"、"SPARK"、"Spark" 互相判等。其余逻辑（参数校验、遍历、首个回退）保持不变。

2. **测试侧**：在 `ViewCatalogTests` 新增 `testSqlForCaseInsensitive`，注册 "spark" 与 "trino" 两个方言的表示，然后断言 `view.sqlFor("SPARK")` 与 `view.sqlFor("TRINO")`（全大写）仍能精确命中各自的 SQL 文本。这样所有继承该抽象测试基类的 catalog 实现自动获得大小写不敏感的回归覆盖。

值得注意的是，本提交只放宽了**精确匹配**的比较口径，未改动"回退到首个 SQL 表示"的逻辑——即当连大小写不敏感也匹配不上时，仍返回遍历中遇到的第一个 SQL 表示（提交 0278 的 closest 回退策略不变）。这是一个有节制的改动：只把"大小写差异"从"不命中"提升为"命中"，其余语义保持稳定，对已有调用方友好。

## 修改详情

### `core/src/main/java/org/apache/iceberg/view/BaseView.java`

**修改目的**：把 `sqlFor` 的方言精确匹配改为大小写不敏感，避免因 dialect 字符串大小写不一致导致误回退。

**工作逻辑**：

改动只有一行：

```java
-        if (sqlViewRepresentation.dialect().equals(dialect)) {
+        if (sqlViewRepresentation.dialect().equalsIgnoreCase(dialect)) {
```

这一行位于 `sqlFor` 遍历表示的循环内。原逻辑（提交 0278 引入）是：对每个 `SQLViewRepresentation`，用 `dialect().equals(dialect)` 严格比较；命中立即返回。改为 `equalsIgnoreCase` 后，比较时忽略大小写：存储的 "spark" 与传入的 "SPARK"、"Spark" 均判等并命中。

为何这种改法是安全的：

- `dialect()` 返回 `String`，`equalsIgnoreCase` 是 `String` 的标准方法，对 null 会抛 NPE。但此处 `sqlViewRepresentation.dialect()` 来自已构建的不可变视图表示（由 Immutables 生成），正常情况下不会为 null；且即便为 null，循环内会抛 NPE——这与原 `equals`（若 `dialect()` 为 null，`null.equals(...)` 也会 NPE）的行为一致，未引入新风险。调用方传入的 `dialect` 已在方法入口由 `Preconditions.checkArgument(dialect != null, ...)` 拦截，故 `equalsIgnoreCase` 的接收端不会因入参 null 而出问题。
- `equalsIgnoreCase` 对 ASCII 字母做大小写折叠，对数字、符号等无影响，与方言名的实际字符集（通常为小写英文单词）完全契合，不会产生误判。
- 回退逻辑（`else if (closest == null) closest = sqlViewRepresentation;`）与循环结束返回 `closest` 的行为未变，仍保留"找不到时返回首个 SQL 表示"的兜底。

整体看，这是典型"小改动、大收益"的健壮性补丁：一行代码把方言匹配从严格字符相等提升为语义相等，符合方言名作为标识符的常规预期。

### `core/src/test/java/org/apache/iceberg/view/ViewCatalogTests.java`

**修改目的**：为大小写不敏感匹配补充回归测试，确保所有 catalog 实现自动覆盖该行为。

**工作逻辑**：

在 `testSqlForMultipleDialects` 之后新增 `testSqlForCaseInsensitive`：

- 创建命名空间（若 `requiresNamespaceCreate()`）。
- 通过 `catalog().buildView(identifier)` 构建视图，注册两个方言查询：`withQuery("spark", "select * from ns.tbl")` 与 `withQuery("trino", "select * from ns.tbl using X")`，注意存储的 dialect 是全小写。
- `create()` 后断言：
  - `view.sqlFor("SPARK").sql()` 等于 `"select * from ns.tbl"`——验证大写 "SPARK" 能命中存储的小写 "spark" 表示。
  - `view.sqlFor("TRINO").sql()` 等于 `"select * from ns.tbl using X"`——验证大写 "TRINO" 能命中存储的小写 "trino" 表示，且取到的是 trino 那一份而非回退到 spark，证明是真正的精确命中（只是大小写不敏感），而非 closest 回退。

测试插在 `testSqlForMultipleDialects` 与 `testSqlForInvalidArguments` 之间，与已有的 `sqlFor` 测试形成完整的用例矩阵：多方言正常解析、大小写不敏感解析、非法参数边界。放在抽象基类 `ViewCatalogTests` 中，意味着每个 catalog 实现的测试子类都会自动跑这个用例，保证大小写不敏感行为在所有 catalog 间一致。

## 小结

本提交是对 `View.sqlFor` 的及时健壮性补丁：把方言精确匹配从 `equals` 改为 `equalsIgnoreCase`，使方言名的大小写差异不再导致误回退。改动虽仅一行，但直击"方言名应作为不区分大小写标识符"的语义本质，显著提升了 `sqlFor` 在真实多引擎/多客户端场景下的可用性。配套测试通过全大写 dialect 命中小写存储方言，验证了精确命中而非回退，并借由抽象测试基类让所有 catalog 实现自动获得覆盖。作为提交 0278 的"打补丁"，它体现了 Iceberg 社区在新 API 落地后快速响应使用反馈、在接口被广泛依赖前及时校准语义的工程节奏。
