# 提交 0697：Core: Fix namespace SQL statement using ESCAPE character that works with MySQL/PostgreSQL

## 提交信息
- **序号**：0697 / 4088
- **哈希**：228fc9b41f99f423d500a236c68216f0203dd42c
- **短哈希**：228fc9b41
- **日期**：2024-04-17
- **作者**：JB Onofré（Co-authored-by: Chauncy）
- **提交说明**：Core: Fix namespace SQL statement using ESCAPE character that works with MySQL/PostgreSQL (#10167)
- **PR/Issue**：#10167

## 总体目的

本提交修复 Iceberg JDBC Catalog 在查询命名空间（namespace）时使用的 SQL `LIKE ... ESCAPE` 语句中转义字符的兼容性问题。

Iceberg 的 JDBC Catalog 将命名空间信息存储在关系型数据库表中。在执行诸如"检查命名空间是否存在"或"列出命名空间"等操作时，需要通过 SQL `LIKE` 语句配合 `ESCAPE` 子句来匹配具有层级关系的命名空间（例如 `catalog.db` 下的子命名空间 `catalog.db.ns1`）。由于命名空间名中可能包含 SQL `LIKE` 模式中的通配符字符（`_` 和 `%`），这些字符必须被转义以避免被解释为通配符，否则会导致查询结果错误。

**Bug 成因**：原代码使用反斜杠 `\` 作为 `ESCAPE` 字符：
- 在 MySQL 中，反斜杠 `\` 本身就是一个具有特殊含义的转义字符（MySQL 默认启用 `NO_BACKSLASH_ESCAPES` 关闭时，`\` 会被 MySQL SQL 解析器先行解释）。这会导致 SQL 语句中的 `ESCAPE '\\'` 被 MySQL 解析后变成单个 `\`，与预期不符，或在某些 MySQL 配置下产生双重转义问题。
- 在 PostgreSQL 中，反斜杠 `\` 同样在标准字符串中有特殊语义（尤其是 `standard_conforming_strings` 参数关闭时），使用 `\` 作为 ESCAPE 字符会引发兼容性问题或语法警告。

更根本的问题在于：使用 `\` 作为 ESCAPE 字符时，需要同时转义 ESCAPE 字符自身（即把 `\` 替换为 `\\`），这在不同数据库引擎下的解析行为不一致，难以保证跨数据库的可移植性。而使用一个普通字符（如 `!`）作为 ESCAPE 字符，则不会与数据库引擎自身的字符串转义机制冲突，行为在 MySQL 和 PostgreSQL 上一致且可预测。

**修复目标**：将 ESCAPE 字符从 `\` 改为 `!`，使命名空间查询 SQL 在 MySQL 和 PostgreSQL 上都能正确工作，同时更新对应的转义逻辑以使用新的 ESCAPE 字符。

## 如何达成设计目的

修复策略包含两个协同变更：

1. **修改 SQL 语句中的 ESCAPE 字符定义**：将 `JdbcUtil.java` 中三处 SQL 常量里的 `ESCAPE '\\'` 改为 `ESCAPE '!'`。`!` 是一个普通 ASCII 字符，在 MySQL 和 PostgreSQL 的字符串字面量中均无特殊含义，作为 ESCAPE 字符时行为一致。

2. **同步更新 Java 代码中构造转义模式字符串的逻辑**：在 `namespaceExists` 方法中，原本通过 `replace("\\", "\\\\").replace("_", "\\_").replace("%", "\\%")` 来转义命名空间名中的特殊字符，现在改为 `replace("!", "!!").replace("_", "!_").replace("%", "!%")`。即：
   - 首先转义 ESCAPE 字符自身：把 `!` 替换为 `!!`（这样原本的 `!` 不会被误认为转义引导符）
   - 然后转义通配符 `_` 为 `!_`
   - 然后转义通配符 `%` 为 `!%`
   
   这与 SQL 中 `ESCAPE '!'` 的定义完全对应，保证 Java 端构造的模式与 SQL 端的解释逻辑一致。

## 修改详情

### `core/src/main/java/org/apache/iceberg/jdbc/JdbcUtil.java`
**修改目的**：将 JDBC Catalog 命名空间查询 SQL 中的 ESCAPE 字符从 `\` 改为 `!`，并同步更新转义逻辑，修复 MySQL/PostgreSQL 兼容性问题。

**工作逻辑**：

该文件包含三处修改：

1. **`GET_NAMESPACE_SQL` 常量（约第 335 行）**：此 SQL 用于检查命名空间是否存在。原 `LIKE ? ESCAPE '\\'` 改为 `LIKE ? ESCAPE '!'`。该 SQL 通过参数传入命名空间名和以通配符结尾的子命名空间匹配模式（`namespaceStartsWith`），用 `OR` 连接精确匹配与 LIKE 前缀匹配。

2. **`LIST_NAMESPACES_SQL` 相关的命名空间存在性检查 SQL（约第 426 行）**：同样将 `LIKE ? ESCAPE '\\' ` 改为 `LIKE ? ESCAPE '!' `，用于列出命名空间时过滤层级。

3. **`namespaceExists` 方法中的转义逻辑（约第 783 行）**：
   原代码：
   ```java
   String namespaceStartsWith =
       namespaceEquals.replace("\\", "\\\\").replace("_", "\\_").replace("%", "\\%") + ".%";
   ```
   新代码：
   ```java
   String namespaceStartsWith =
       namespaceEquals.replace("!", "!!").replace("_", "!_").replace("%", "!%") + ".%";
   ```
   该变量构造用于 `LIKE` 匹配的模式字符串：先转义 ESCAPE 字符自身，再转义两个 LIKE 通配符，最后追加 `.%` 以匹配所有子命名空间（例如 `catalog.db.%` 匹配 `catalog.db.ns1`、`catalog.db.ns1.ns2` 等）。`.%"` 中的 `.` 是命名空间分隔符，`%` 是要保留的通配符不转义。

   转义顺序很关键：必须先转义 ESCAPE 字符自身（`!` -> `!!`），否则后续转义产生的 `!_`、`!%` 中的 `!` 会被再次转义导致错误。

## 小结
- **成效**：成功修复了 JDBC Catalog 在 MySQL 和 PostgreSQL 上的命名空间查询转义字符兼容性问题。使用 `!` 作为 ESCAPE 字符避免了数据库引擎自身对反斜杠的特殊处理，使行为跨数据库一致。
- **影响范围**：仅影响 `core` 模块中 JDBC Catalog 的命名空间相关查询（`JdbcUtil.java`），影响使用 MySQL 或 PostgreSQL 作为后端的 JDBC Catalog 用户的命名空间存在性检查与列出操作。对使用其他数据库（如 SQLite、H2）的用户行为无负面影响。
- **回迁到 1.4.x 的注意事项**：此修复为纯逻辑修复，无数据格式或协议变更，可安全回迁。回迁时需确认 1.4.x 分支的 `JdbcUtil.java` 中这三处 SQL 常量与方法结构与 main 分支一致。注意：由于修改了 ESCAPE 字符，若 1.4.x 已有依赖原 `\` 转义行为的自定义 SQL 或测试，需同步更新。该修复不影响已存储的数据，仅影响查询语句的生成与解释。
