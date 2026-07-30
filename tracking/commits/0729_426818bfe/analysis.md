# 提交分析：Core: Add property to disable table initialization for JdbcCatalog (#10124)

## 提交信息

| 项目 | 内容 |
| --- | --- |
| 哈希 | `426818bfe7fa93e8c677ebf886638d5c50db597b` |
| 短哈希 | `426818bfe` |
| 作者 | Marc Cenac |
| 提交时间 | 2024-04-29 05:20:15 -0500 |
| 提交标题 | Core: Add property to disable table initialization for JdbcCatalog (#10124) |
| 提交正文 | （无附加正文） |
| 变更范围 | 3 个文件，80 行新增，1 行删除 |

涉及文件：
- `core/src/main/java/org/apache/iceberg/jdbc/JdbcCatalog.java`
- `core/src/main/java/org/apache/iceberg/jdbc/JdbcUtil.java`
- `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcCatalog.java`

## 总体目的

为 `JdbcCatalog` 增加一个可在 catalog 属性中配置的开关 `jdbc.init-catalog-tables`，允许使用者在初始化 catalog 时**跳过**自动创建 Iceberg 所需的数据库表（`iceberg_tables`、`iceberg_namespace_properties` 等）。此前是否初始化这些表只能通过 `JdbcCatalog` 构造函数的 `initializeCatalogTables` 布尔参数决定，且该值在构造时固化、无法通过 catalog properties 覆盖；本提交把这一行为暴露为运行时可配置的属性，并使属性值优先于构造函数默认值。

## 如何达成设计目的

### 背景：原有机制

`JdbcCatalog` 原本通过构造函数注入 `initializeCatalogTables`：

```java
public JdbcCatalog(
    Function<Map<String, String>, FileIO> ioBuilder,
    Function<Map<String, String>, JdbcClientPool> clientPoolBuilder,
    boolean initializeCatalogTables) {
  ...
  this.initializeCatalogTables = initializeCatalogTables;  // 构造期固化
}
```

无参构造 `new JdbcCatalog()` 默认 `initializeCatalogTables = true`。`initialize(String, Map)` 方法在建立 JDBC 连接池后，若该字段为 true 就调用 `initializeCatalogTables()` 建表。

问题在于：catalog 在运行时是通过 `CatalogUtil.loadCatalog(...)` 反射加载的，使用的是无参构造，因此 `initializeCatalogTables` 恒为 true，使用者**无法通过 properties 关闭建表**。对于已经由 DBA 预先建好表、或权限受限（catalog 账号没有 DDL 权限）的场景，自动建表会失败或造成非预期的 DDL 执行。

### 设计思路

1. **把字段从 `final` 改为可变**：`private final boolean initializeCatalogTables` → `private boolean initializeCatalogTables`，去掉 `final`，使其可在 `initialize()` 阶段被属性值覆盖。
2. **新增属性键**：在 `JdbcUtil` 中定义 `INIT_CATALOG_TABLES_PROPERTY = "jdbc.init-catalog-tables"`，与其它 `jdbc.*` 属性（`strict-mode`、`schema-version`）保持同一前缀风格。
3. **在 `initialize()` 中读取属性并覆盖构造默认值**：用 `PropertyUtil.propertyAsBoolean(properties, INIT_CATALOG_TABLES_PROPERTY, initializeCatalogTables)`，第三参数以当前字段值（即构造函数传入的默认）作为 fallback。这样：
   - 若 properties 中显式设置了 `jdbc.init-catalog-tables`，则按属性值决定是否建表（属性优先）。
   - 若未设置，则保持原有构造函数行为（向后兼容）。
4. **保留构造函数默认值语义**：对于用 `new JdbcCatalog(null, null, false)` 显式传入 `false` 的调用方，若 properties 未设置属性，仍为 false，不破坏既有行为。

这一设计的核心是“属性覆盖构造默认值”，既向后兼容，又赋予运维侧通过配置控制 DDL 行为的能力，契合 Iceberg catalog 通过 `properties` 配置行为的统一约定。

## 修改详情

### `core/src/main/java/org/apache/iceberg/jdbc/JdbcCatalog.java`

**(1) 字段去掉 `final`**

```java
-  private final boolean initializeCatalogTables;
+  private boolean initializeCatalogTables;
```

去掉 `final` 是为了让 `initialize()` 阶段能够用属性值重新赋值该字段。注意：`ioBuilder` 与 `clientPoolBuilder` 仍是 `final`，本次只放宽了需要被覆盖的字段。

**(2) 在 `initialize()` 中读取属性**

在创建 `JdbcClientPool`（连接就绪）之后、调用 `initializeCatalogTables()` 之前，插入属性读取逻辑：

```java
this.initializeCatalogTables =
    PropertyUtil.propertyAsBoolean(
        properties, JdbcUtil.INIT_CATALOG_TABLES_PROPERTY, initializeCatalogTables);
if (initializeCatalogTables) {
  initializeCatalogTables();
}
```

- 读取时机选在连接池建立之后，确保后续无论是否建表，连接都已可用。
- `PropertyUtil.propertyAsBoolean` 以第三参数为默认值，实现了“属性优先、构造默认兜底”的语义。
- 之后的 `if (initializeCatalogTables)` 分支判断保持不变，确保原有建表流程不变。

### `core/src/main/java/org/apache/iceberg/jdbc/JdbcUtil.java`

新增属性键常量定义：

```java
// property to control if catalog tables are created during initialization
static final String INIT_CATALOG_TABLES_PROPERTY =
    JdbcCatalog.PROPERTY_PREFIX + "init-catalog-tables";
```

- 复用 `JdbcCatalog.PROPERTY_PREFIX`（即 `"jdbc."`），与同文件中 `STRICT_MODE_PROPERTY`、`SCHEMA_VERSION_PROPERTY` 风格一致。
- 包级可见（`static final`，无修饰符），供 `JdbcCatalog` 与测试类引用。

### `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcCatalog.java`

新增两个测试与两个辅助方法，验证属性可在两个方向上覆盖默认值。

**(1) `testDisableInitCatalogTablesOverridesDefault`**：验证设置 `jdbc.init-catalog-tables=false` 时**不建表**
- 使用文件型 SQLite（`jdbc:sqlite:<temp>`）而非内存库。注释解释了原因：内存库连接是 per-connection 的，测试需要用不同连接去校验表是否存在，必须用文件库保持状态。
- 构造 properties，包含 `warehouse`、`uri`，并显式设置 `JdbcUtil.INIT_CATALOG_TABLES_PROPERTY = "false"`。
- 用无参 `new JdbcCatalog()`（构造默认 true）初始化，验证属性覆盖生效。
- 断言 `catalogTablesExist(jdbcUrl)` 为 false（`iceberg_tables` 与 `iceberg_namespace_properties` 都不存在）。
- 进一步断言此时调用 `listNamespaces()` 抛出 `UncheckedSQLException`，且消息为 `Failed to execute query: <LIST_ALL_NAMESPACES_SQL>`——即表确实未建，查询自然失败。这同时验证了“关闭建表”的副作用：调用方需自行保证表已存在，否则运行期查询会报错。

**(2) `testEnableInitCatalogTablesOverridesDefault`**：验证设置 `jdbc.init-catalog-tables=true` 时**建表**，即便构造函数传 false
- 同样使用文件型 SQLite。
- 用 `new JdbcCatalog(null, null, false)`（构造默认 false）初始化，但属性设为 `true`。
- 断言 `catalogTablesExist(jdbcUrl)` 为 true，证明属性值覆盖了构造函数的 false。

**(3) 辅助方法 `catalogTablesExist(String jdbcUrl)` 与 `tableExists(DatabaseMetaData, String)`**
- 通过 `SQLiteDataSource` 单独建一个连接，用 `DatabaseMetaData.getTables(...)` 查询 `iceberg_tables`（`JdbcUtil.CATALOG_TABLE_VIEW_NAME`）与 `iceberg_namespace_properties`（`JdbcUtil.NAMESPACE_PROPERTIES_TABLE_NAME`）是否存在，二者都存在才返回 true。
- 这种“用独立连接从元数据侧校验”的方式，正是上面必须用文件库的原因。
- 新增 `import java.sql.DatabaseMetaData;` 以支持元数据查询。

这两个测试形成对称覆盖：一个验证 true→false 的覆盖，一个验证 false→true 的覆盖，完整证明了“属性优先于构造默认值”的双向语义。

## 小结

### 成效
- 为 `JdbcCatalog` 提供了运行时可配置的建表开关 `jdbc.init-catalog-tables`，解决了此前只能由构造函数决定、且反射加载场景下恒为 true 的局限。
- 严格保持向后兼容：不设置属性时行为与升级前完全一致。
- 满足了“表已由 DBA 预建”或“catalog 账号无 DDL 权限”等生产场景的需求，避免非预期的自动 DDL。

### 影响范围
- 仅影响 `JdbcCatalog` 的初始化路径，不影响其它 catalog 实现。
- 字段由 `final` 变可变，理论上降低了“不可变性”保证，但由于该字段只在 `initialize()` 内被一次性赋值，并发可见性语义未受实质影响（`initialize` 在 catalog 使用前完成）。
- 新增的属性成为 `JdbcCatalog` 公开契约的一部分，文档与配置示例需同步补充。

### 回迁注意事项（1.4.x ← main）
- 该改动是纯增量且向后兼容，回迁风险低。回迁时需同时携带三个文件的改动：`JdbcCatalog.java`（字段去 final + 属性读取）、`JdbcUtil.java`（新常量）、`TestJdbcCatalog.java`（两个新测试 + 辅助方法 + 新 import）。
- 注意 1.4.x 分支上 `JdbcCatalog` 的字段定义与 `initialize()` 顺序是否与本提交一致；若 1.4.x 已有对该字段的其它改动，需在合并时保留属性读取逻辑插入在“连接池建立之后、建表调用之前”这一正确位置。
- `PropertyUtil.propertyAsBoolean` 与 `JdbcCatalog.PROPERTY_PREFIX` 均为既有 API，1.4.x 应已具备，无需额外回迁依赖。
- 回迁后建议运行新增的两个测试，确认文件型 SQLite 校验逻辑在 1.4.x 测试环境下可用（依赖 sqlite 测试依赖存在）。
