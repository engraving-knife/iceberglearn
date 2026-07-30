# 提交 0269：JDBC Catalog: Fix namespaceExists check with special characters (#8340)

## 提交信息

- **序号**：0269 / 4088
- **哈希**：11608e10245875ca2972c1dba1c92182c16ec887
- **短哈希**：11608e102
- **日期**：2023-12-13 19:42:24 +0100
- **作者**：ismail simsek <6005685+ismailsimsek@users.noreply.github.com>
- **提交说明**：JDBC Catalog: Fix namespaceExists check with special characters (#8340)
- **PR/Issue**：#8340

## 总体目的

本提交修复 JDBC Catalog 中 `namespaceExists` 方法在命名空间名称包含 SQL LIKE 特殊字符时产生误判的 bug。在 JDBC Catalog 实现中，判断一个命名空间是否存在是通过 SQL 查询 `iceberg_tables` 表的 `table_namespace` 列和 `iceberg_namespace_properties` 表的 `namespace` 列来实现的。修复前的代码使用 `LIKE` 操作符配合 `namespaceToString(namespace) + "%"` 作为匹配模式来查询。

这种实现存在两个层面的问题。第一，SQL 的 `LIKE` 操作符中 `%` 匹配零个或多个任意字符、`_` 匹配单个任意字符，这些是通配符。当用户创建的命名空间名称本身包含 `%` 或 `_` 字符时（例如 `testDb%` 或 `test_Db`），这些字符在 `LIKE` 模式中会被解释为通配符而非字面量，导致误匹配。例如，查询 `test_Db` 是否存在时，`LIKE 'test_Db%'` 会匹配到 `testXdb`、`testadb` 等不相关的命名空间，返回错误的"存在"结果。同理，反斜杠 `\` 在某些 SQL 方言中也是转义字符，未处理也会产生问题。

第二，原代码仅使用 `namespace + "%"` 做前缀匹配，这意味着 `db%` 会匹配 `dbsomething`（无点分隔符的前缀），而非仅匹配 `db` 本身或 `db.ns1` 这样的子命名空间。在 JDBC Catalog 的语义中，一个命名空间 `db` 被认为"存在"的条件是：它自身在表中有记录，或者它有子命名空间（如 `db.ns1`），因为父命名空间在子命名空间/表存在时是隐式存在的。正确的匹配应该是精确匹配 `db` 或前缀匹配 `db.%`（注意有点分隔符），而不是 `db%`。

## 如何达成设计目的

修复方案在 SQL 层面和方法层面同时着手：

1. **SQL 语句改造**：将 `GET_NAMESPACE_SQL` 和 `GET_NAMESPACE_PROPERTIES_SQL` 两个 SQL 常量从单一的 `LIKE ?` 改为 `( column = ? OR column LIKE ? ESCAPE '\\' )` 的组合查询。`=` 做精确匹配，`LIKE ... ESCAPE '\\'` 做带转义的前缀匹配，两者用 `OR` 连接。

2. **参数构造改造**：在 `namespaceExists` 方法中构造两个参数——`namespaceEquals`（命名空间的精确字符串表示）和 `namespaceStartsWith`（转义特殊字符后追加 `.%` 的前缀匹配模式）。前者用于 `=` 精确匹配，后者用于 `LIKE` 匹配子命名空间。

3. **测试覆盖**：新增三个专门针对特殊字符的测试方法，分别覆盖反斜杠 `\`、百分号 `%`、下划线 `_` 三种 SQL LIKE 特殊字符场景，并增强原有的 `testCreateNamespace` 测试以验证父子命名空间的存在性判断。

## 修改详情

### `core/src/main/java/org/apache/iceberg/jdbc/JdbcUtil.java`

**修改目的**：修复 `namespaceExists` 中的 SQL 查询逻辑，正确处理特殊字符并区分精确匹配与子命名空间前缀匹配。

**工作逻辑**：

1. **`GET_NAMESPACE_SQL` 常量改造**：
   原 SQL：
   ```sql
   SELECT table_namespace FROM iceberg_tables
   WHERE catalog_name = ? AND table_namespace LIKE ? LIMIT 1
   ```
   改为：
   ```sql
   SELECT table_namespace FROM iceberg_tables
   WHERE catalog_name = ? AND ( table_namespace = ? OR table_namespace LIKE ? ESCAPE '\\' ) LIMIT 1
   ```
   新增了 `table_namespace = ?` 精确匹配分支，并将 `LIKE` 分支加上 `ESCAPE '\\'` 声明，使反斜杠成为转义字符。两个条件用括号和 `OR` 组合。SQL 现在接收两个参数（原来一个）。

2. **`GET_NAMESPACE_PROPERTIES_SQL` 常量改造**：对 `iceberg_namespace_properties` 表的 `namespace` 列做完全相同的改造，从 `namespace LIKE ?` 改为 `( namespace = ? OR namespace LIKE ? ESCAPE '\\' )`。

3. **`namespaceExists` 方法改造**：
   原代码：
   ```java
   if (exists(connections, JdbcUtil.GET_NAMESPACE_SQL, catalogName,
       JdbcUtil.namespaceToString(namespace) + "%")) { return true; }
   ```
   改为：
   ```java
   String namespaceEquals = JdbcUtil.namespaceToString(namespace);
   String namespaceStartsWith =
       namespaceEquals.replace("\\", "\\\\").replace("_", "\\_").replace("%", "\\%") + ".%";
   if (exists(connections, JdbcUtil.GET_NAMESPACE_SQL, catalogName,
       namespaceEquals, namespaceStartsWith)) { return true; }
   ```
   
   关键逻辑：
   - `namespaceEquals`：命名空间的精确字符串（如 `testDb.ns1`），用于 `= ?` 精确匹配。
   - `namespaceStartsWith`：先对 `namespaceEquals` 中的特殊字符做转义——`\` 替换为 `\\`（转义反斜杠本身）、`_` 替换为 `\_`（转义下划线通配符）、`%` 替换为 `\%`（转义百分号通配符）——然后追加 `.%`。这个模式配合 `ESCAPE '\\'` 使用，`.%` 末尾的 `%` 仍然作为通配符（因为它没有被转义），匹配 `namespace.` 后面的任意子命名空间。
   
   例如命名空间 `test\Db%`，转义后变为 `test\\Db\%.%`，在 SQL `LIKE` 中：
   - `test` 匹配字面量 `test`
   - `\\` 匹配字面量 `\`
   - `Db` 匹配字面量 `Db`
   - `\%` 匹配字面量 `%`（被转义，不再是通配符）
   - `.` 匹配字面量 `.`
   - `%` 匹配任意字符（未转义，是通配符）
   
   这就精确地匹配了 `test\Db%.任意子命名空间`。
   
   对 `GET_NAMESPACE_PROPERTIES_SQL` 的第二次 `exists` 调用也做了同样的参数改造。

4. 注释说明了设计意图：`// when namespace has sub-namespace then additionally checking it with LIKE statement. // catalog.db can exists as: catalog.db.ns1 or catalog.db.ns1.ns2`，即一个命名空间可以因为自身存在或其子命名空间存在而被判定为存在。

### `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcCatalog.java`

**修改目的**：增强现有测试并新增针对 SQL 特殊字符的专项测试，验证修复的正确性。

**工作逻辑**：

1. **增强 `testCreateNamespace`**：原来只测试创建命名空间后 `namespaceExists` 返回 true。增强后额外验证：父命名空间也存在（`testDb`、`testDb.ns1` 在创建 `testDb.ns1.ns2` 后都应存在），不相关的命名空间不存在，以及包含通配符的命名空间名（`testDb.ns%`、`testDb.ns_`）不会误匹配到已存在的 `testDb.ns1`。最后还验证了更深层级的命名空间（`testDb.ns1.ns2.ns3`）不存在。

2. **新增 `testCreateNamespaceWithBackslashCharacter`**：测试命名空间包含反斜杠 `\` 的场景。创建 `test\Db.ns\1.ns3`，验证其存在性判断正确。然后验证用 `%`、`.`、`_` 等特殊字符搜索时不会误匹配。特别测试了 `test\%Db2`（反斜杠后跟 `%`）这种边界情况，确保反斜杠与 `%` 的组合被正确处理。

3. **新增 `testCreateNamespaceWithPercentCharacter`**：测试命名空间包含 `%` 的场景。创建 `testDb%.ns%1`，验证其存在，然后验证 `testDb\%`（转义的 `%`）、`testDb`（去掉 `%`）、`tes%Db%`（用 `%` 通配）等不会误匹配。

4. **新增 `testCreateNamespaceWithUnderscoreCharacter`**：测试命名空间包含 `_` 的场景。创建 `test_Db.ns_1.ns_`，验证 `test_D_`（用 `_` 通配 `b`）、`test_D%`（用 `%` 通配）等不会误匹配。同时也验证了精确匹配 `test_Db` 和 `test_Db.ns_1` 正确返回 true。

5. **修改 `testCreateTableInNonExistingNamespace`**：将测试中使用的命名空间从简单的 `testDb.ns1.ns2` 改为包含特殊字符的 `test\D_b%.ns1.ns2`，确保特殊字符场景下"命名空间不存在时创建表会失败"的行为也被覆盖。

## 小结

本提交修复了一个影响数据正确性的 bug：当 JDBC Catalog 中命名空间名称包含 SQL LIKE 特殊字符（`%`、`_`、`\`）时，`namespaceExists` 可能返回错误的 true，进而导致 `createNamespace` 误报"已存在"、或 `listNamespaces`/`loadNamespaceMetadata` 等操作行为异常。修复通过精确匹配 + 带转义的前缀匹配双管齐下，既保证了特殊字符的字面量语义，又正确区分了命名空间本身与其子命名空间。配套的四个测试方法系统性地覆盖了三种特殊字符及父子命名空间的存在性判断，为修复提供了坚实的回归保护。
