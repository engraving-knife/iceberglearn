# 提交 0928：Core: Exclude unexpected namespaces JdbcCatalog.listNamespaces (#10498)

## 提交信息

- **序号**：0928 / 4088
- **哈希**：d9dbb75ed057c4f6673626763f257c0a9049e249
- **短哈希**：d9dbb75ed
- **日期**：2024-07-12 19:15:24 +0900
- **作者**：Yuya Ebihara <ebyhry@gmail.com>
- **提交说明**：Core: Exclude unexpected namespaces JdbcCatalog.listNamespaces (#10498)
- **PR/Issue**：#10498

## 总体目的

`JdbcCatalog` 在 `listNamespaces(Namespace)` 时使用 SQL `LIKE` 来匹配给定命名空间下的子命名空间，把查询参数中的命名空间层级拼到 LIKE 模式中。但 SQL 的 `LIKE` 把 `%` 和 `_` 当作通配符：`%` 匹配任意长度字符串、`_` 匹配单字符。当用户的命名空间层级本身包含 `%` 或 `_` 字符时（例如 `d_`、`d%`），LIKE 会把它们当作通配符进行"模糊匹配"，从而把不该返回的命名空间也查出来，导致 `listNamespaces` 返回不期望的结果。

例如：调用 `listNamespaces(Namespace.of("d_"))` 期望只返回 `d_.ns5`，但因为 `_` 是 LIKE 通配符，可能匹配到 `db.ns5`、`db2.ns5` 等其他命名空间。本提交的目的就是在 Java 侧对返回结果做精确过滤，排除这种因 LIKE 通配符语义造成的"误匹配"，保证 `listNamespaces` 只返回与给定命名空间前缀逐级精确相等的子命名空间。

## 如何达成设计目的

在 `JdbcCatalog.listNamespaces` 的结果流处理中，紧跟在 `.distinct()` 之后新增一个 `.filter(...)`：对每个候选命名空间 `n`，逐级比较 `n.levels()[i]` 与 `namespace.levels()[i]`（给定参数的各层级），只要有一级不相等就过滤掉，从而剔除 LIKE 模糊匹配带来的多余结果。这种做法不改动 SQL 层（不引入 `ESCAPE` 之类的 SQL 方言差异），而是在客户端做精确过滤，兼容所有 JDBC 后端。

## 修改详情

### `core/src/main/java/org/apache/iceberg/jdbc/JdbcCatalog.java`

**修改目的**：在 `listNamespaces` 结果中过滤掉因 LIKE 通配符语义误匹配的命名空间。

**工作逻辑**：在原有 `.distinct()` 之后追加 `.filter(...)`，对每个候选 `n`，遍历 `namespace.levels()` 的每一级 `i`，若 `n.levels()[i]` 与 `namespace.levels()[i]` 不相等则返回 `false`（剔除），全部相等才保留。这样即使 SQL `LIKE` 把 `d_` 匹配到了 `db`，也会在过滤阶段被剔除，只留下真正以 `d_` 为前缀的命名空间。

### `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcCatalog.java`

**修改目的**：补充回归测试，覆盖命名空间含 `_` 和 `%` 的场景。

**工作逻辑**：新增三个表标识 `tbl7 = db2.ns4.tbl5`、`tbl8 = d_.ns5.tbl6`、`tbl9 = d%.ns6.tbl7`，一并建表；调整 `listNamespaces()`（无参）的断言由原来的 3 个（`db`、`db2`、``）改为 5 个（新增 `d_`、`d%`）；新增 `listNamespaces(Namespace.of("d_"))` 断言只返回 `d_.ns5`、`listNamespaces(Namespace.of("d%"))` 断言只返回 `d%.ns6`，验证带通配字符的命名空间不再被误匹配。

## 小结

- **成效**：修复了 `JdbcCatalog.listNamespaces` 在命名空间含 SQL LIKE 通配字符（`%`、`_`）时返回多余结果的问题，通过客户端逐级精确过滤保证结果正确性，并补充了回归测试。
- **影响范围**：`core` 模块的 `JdbcCatalog.java`（+10）与 `TestJdbcCatalog.java`（+17/-3），仅影响 JDBC Catalog 的 `listNamespaces` 行为。
- **回迁到 1.4.x 的注意事项**：回迁风险低，是一个明显的 bug 修复，且不改变 SQL 层、对 JDBC 后端无方言依赖。前提是 1.4.x 的 `JdbcCatalog.listNamespaces` 实现结构与 main 分支一致（同样在流式处理中包含 `.distinct()` 等步骤）。建议连同测试一起回迁以防止回归。需注意 1.4.x 上若该处代码结构有差异，需手动对齐 filter 插入位置。
