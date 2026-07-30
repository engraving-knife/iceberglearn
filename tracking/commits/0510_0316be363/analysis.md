# 提交 0510：Core: Add view support on the JDBC catalog (#9487)

## 提交信息

| 字段 | 内容 |
|------|------|
| 序号 | 0510 |
| 完整哈希 | 0316be363bf76f8692da06c870719bbf5114244a |
| 短哈希 | 0316be363 |
| 日期 | 2024-02-17 |
| 作者 | JB Onofre <jbonofre@apache.org> |
| 提交说明 | Core: Add view support on the JDBC catalog (#9487) |
| PR | #9487 |

## 总体目的

本提交为 Iceberg 的 JDBC Catalog 添加完整的视图（View）支持。在此之前，JDBC Catalog 仅支持表（Table）操作，不支持视图的创建、加载、列举、重命名和删除。随着 Iceberg 视图功能的成熟，需要让 JDBC Catalog 这一流行的 catalog 实现也能管理视图。

核心设计挑战在于：JDBC Catalog 使用一张数据库表 `iceberg_tables` 来存储表元数据的位置信息。要支持视图，有两种方案——为视图新建独立的数据库表，或在现有表中增加类型列来区分表和视图。本提交选择了后者（在 `iceberg_tables` 表中新增 `iceberg_type` 列），将表和视图存储在同一张物理表中，通过 `RECORD_TYPE` 字段（值为 `TABLE` 或 `VIEW`）来区分。这种方案复用了现有表结构和索引，实现更简洁。

由于已有用户环境中存在旧版 schema（无 `iceberg_type` 列），本提交引入了 Schema 版本管理机制（`SchemaVersion.V0` 和 `V1`）：V0 表示旧版 schema（无类型列，仅支持表），V1 表示新版 schema（有类型列，支持表和视图）。Catalog 在初始化时自动检测数据库 schema 版本。对于旧版 schema，用户可通过设置 `jdbc.add-view-support=true` 属性来触发自动迁移（`ALTER TABLE ADD COLUMN`）；如果不设置该属性，Catalog 会以 V0 模式运行并发出警告日志，此时视图操作会抛出 `UnsupportedOperationException`，但表操作仍正常工作，保证向后兼容。

此外，`JdbcCatalog` 的基类从 `BaseMetastoreCatalog` 改为 `BaseMetastoreViewCatalog`，使其获得视图 catalog 的标准接口。重命名操作（`renameTable`）也得到增强，增加了表与视图之间的名称冲突检测，防止重命名后出现同名表和视图。

## 如何达成设计目的

实现路径分为以下几个方面：首先，将 `JdbcCatalog` 的基类改为 `BaseMetastoreViewCatalog`，实现 `newViewOps()`、`dropView()`、`listViews()`、`renameView()` 四个视图操作方法；其次，在 `JdbcUtil` 中引入 `SchemaVersion` 枚举和大量 V0/V1 双版本 SQL 语句，以及统一的辅助方法（`loadTable`、`loadView`、`updateTable`、`updateView`、`doCommitCreateTable`、`doCommitCreateView`、`tableExists`、`viewExists`），将原先散落在 `JdbcTableOperations` 中的 SQL 逻辑集中到 `JdbcUtil` 中复用；第三，新增 `JdbcViewOperations` 类实现 `BaseViewOperations` 接口，处理视图元数据的刷新和提交；第四，在 `JdbcCatalog` 初始化时检测 schema 版本并按需迁移；最后，在 `JdbcTableOperations` 中适配新的 schema 版本参数，并在建表时检查同名视图冲突。

## 修改详情

### core/src/main/java/org/apache/iceberg/jdbc/JdbcCatalog.java

**修改目的**：为 JDBC Catalog 添加视图支持，包括基类切换、schema 版本检测与迁移、视图 CRUD 操作。

**工作逻辑**：

1. **基类切换**：`JdbcCatalog` 从 `extends BaseMetastoreCatalog` 改为 `extends BaseMetastoreViewCatalog`，引入视图 catalog 接口。移除了对 `BaseMetastoreCatalog` 的 import，新增 `BaseMetastoreViewCatalog`、`ViewOperations`、`NoSuchViewException`、`PropertyUtil` 等 import。

2. **Schema 版本字段**：新增 `private JdbcUtil.SchemaVersion schemaVersion = JdbcUtil.SchemaVersion.V1`，默认为 V1。

3. **常量**：新增 `VIEW_WARNING_LOG_MESSAGE` 静态常量，用于在无视图支持时输出警告。

4. **初始化与 schema 检测**：在 `initializeTables()` 中，检测 `iceberg_tables` 表是否存在时改用 `CATALOG_TABLE_VIEW_NAME`。如果表已存在，调用新方法 `updateCatalogTables(conn)` 检测并按需迁移 schema。建表 SQL 改用 `CREATE_CATALOG_SQL`（包含 `iceberg_type` 列）。

5. **`updateCatalogTables(Connection)` 方法**：通过 `DatabaseMetaData.getColumns()` 检查 `iceberg_type` 列是否已存在。如果不存在，检查 `jdbc.add-view-support` 属性：为 `true` 则执行 `ALTER TABLE ADD COLUMN iceberg_type`（`UPDATE_CATALOG_SQL`）进行迁移；为 `false` 则记录警告并将 `schemaVersion` 设为 `V0`。

6. **`newTableOps`**：传入 `schemaVersion` 给 `JdbcTableOperations`。

7. **`newViewOps`**：新增方法，如果 schemaVersion 不是 V1 则抛出 `UnsupportedOperationException`，否则创建 `JdbcViewOperations`。

8. **`dropTable`/`listTables`/`renameTable`**：根据 schemaVersion 选择对应的 SQL（V1 或 V0 版本），V1 版本的 SQL 包含 `RECORD_TYPE` 过滤条件。

9. **`renameTable` 增强**：新增多重冲突检测——`from.equals(to)` 直接返回、源表不存在检查、目标命名空间不存在检查、目标名与已有视图冲突检查、目标名与已有表冲突检查。

10. **`dropView`/`listViews`/`renameView`**：新增三个视图操作方法，均先检查 schemaVersion 是否为 V1（否则抛 `UnsupportedOperationException`）。`renameView` 包含完整的冲突检测逻辑（命名空间、源视图存在性、目标与表/视图冲突），并处理 SQLite 特有的约束违反错误。

11. **命名空间列举**：`LIST_ALL_TABLE_NAMESPACES_SQL` 重命名为 `LIST_ALL_NAMESPACES_SQL`（因为现在同时包含表和视图的命名空间）。

### core/src/main/java/org/apache/iceberg/jdbc/JdbcTableOperations.java

**修改目的**：适配 schema 版本机制，复用 JdbcUtil 中的统一方法。

**工作逻辑**：

1. 构造函数新增 `JdbcUtil.SchemaVersion schemaVersion` 参数。

2. `doRefresh()`：将原来的 `getTable()` 私有方法调用改为 `JdbcUtil.loadTable(schemaVersion, connections, catalogName, tableIdentifier)`。

3. `doCommit()`：将原来的 `getTable()` 调用改为 `JdbcUtil.loadTable(...)`，将 `updateTable()` 内联 SQL 改为调用 `JdbcUtil.updateTable(schemaVersion, ...)`。

4. `createTable()`（新建表）：将内联 INSERT SQL 改为调用 `JdbcUtil.doCommitCreateTable(schemaVersion, ...)`。新增两项冲突检测：V1 模式下检查同名视图是否存在（`JdbcUtil.viewExists`），以及检查同名表是否存在（`JdbcUtil.tableExists`），存在则抛 `AlreadyExistsException`。

5. 移除了私有方法 `getTable()`（其逻辑已迁移到 `JdbcUtil.tableOrView()` / `JdbcUtil.loadTable()`）。

### core/src/main/java/org/apache/iceberg/jdbc/JdbcUtil.java

**修改目的**：集中管理所有 SQL 语句和数据库操作辅助方法，支持 V0/V1 双 schema 版本和视图操作。

**工作逻辑**：

1. **新增常量**：`ADD_VIEW_SUPPORT_PROPERTY`（属性键 `jdbc.add-view-support`）、`RECORD_TYPE`（列名 `iceberg_type`）、`TABLE_RECORD_TYPE`（`"TABLE"`）、`VIEW_RECORD_TYPE`（`"VIEW"`）。`CATALOG_TABLE_NAME` 重命名为 `CATALOG_TABLE_VIEW_NAME`。

2. **SchemaVersion 枚举**：`enum SchemaVersion { V0, V1 }`，V0 为旧版无类型列，V1 为新版有类型列。

3. **双版本 SQL 语句**：为每类操作提供 V0 和 V1 两个版本的 SQL。V1 版本在 WHERE 子句中增加 `RECORD_TYPE` 过滤（如 `(RECORD_TYPE = 'TABLE' OR RECORD_TYPE IS NULL)`，`NULL` 兼容迁移前已存在的旧行）。涉及 `DO_COMMIT_SQL`、`GET_TABLE_SQL`、`LIST_TABLE_SQL`、`RENAME_TABLE_SQL`、`DROP_TABLE_SQL`、`DO_COMMIT_CREATE_SQL`。新增视图专用 SQL：`GET_VIEW_SQL`、`LIST_VIEW_SQL`、`RENAME_VIEW_SQL`、`DROP_VIEW_SQL`。

4. **建表与迁移 SQL**：`CREATE_CATALOG_TABLE` 重命名为 `CREATE_CATALOG_SQL`（新增 `RECORD_TYPE VARCHAR(100)` 列）。新增 `UPDATE_CATALOG_SQL`（`ALTER TABLE ... ADD COLUMN iceberg_type VARCHAR(5)`）。

5. **统一辅助方法**：新增多个静态方法集中数据库操作逻辑：
   - `update(boolean isTable, ...)` / `updateTable(...)` / `updateView(...)`：统一的 UPDATE 操作，根据 isTable 和 schemaVersion 选择 SQL 和设置 RECORD_TYPE 参数。
   - `tableOrView(boolean isTable, ...)` / `loadTable(...)` / `loadView(...)`：统一的 SELECT 操作，将结果集映射为 `Map<String, String>`。
   - `doCommitCreate(boolean isTable, ...)` / `doCommitCreateTable(...)` / `doCommitCreateView(...)`：统一的 INSERT 操作。
   - `viewExists(...)` / `tableExists(...)`：存在性检查。
   - `namespaceExists(...)`：简化为直接 return，移除冗余的 if-return 结构。

6. **可见性调整**：多个方法从 `public` 改为包级私有（`static`），如 `stringToNamespace`、`namespaceToString`、`stringToTableIdentifier`、`filterAndRemovePrefix`、`updatePropertiesStatement`、`insertPropertiesStatement`、`deletePropertiesStatement`。

7. 引入 `BaseMetastoreTableOperations` 和 `Maps` import 用于辅助方法实现。

### core/src/main/java/org/apache/iceberg/jdbc/JdbcViewOperations.java（新文件）

**修改目的**：实现 JDBC 视图的元数据操作（刷新与提交）。

**工作逻辑**：新文件 206 行，继承 `BaseViewOperations`，结构与 `JdbcTableOperations` 对称：

1. **字段**：`catalogName`、`viewIdentifier`、`fileIO`、`connections`、`catalogProperties`。

2. **`doRefresh()`**：调用 `JdbcUtil.loadView(V1, ...)` 加载视图记录。如果返回为空且当前有 metadata location，抛 `NoSuchViewException`；如果返回为空且无当前 location，调用 `disableRefresh()`。否则通过 `refreshFromMetadataLocation()` 从文件加载元数据。

3. **`doCommit(ViewMetadata base, ViewMetadata metadata)`**：写入新元数据文件后，加载当前视图记录。如果 `base` 不为空（替换已有视图），调用 `validateMetadataLocation()` 验证 metadata location 未被并发修改，然后调用 `updateView()` 执行 UPDATE。如果 `base` 为空（新建视图），调用 `createView()` 执行 INSERT。异常处理覆盖 `SQLIntegrityConstraintViolationException`（视图已存在）、`SQLTimeoutException`、连接异常、`DataTruncation`、`SQLWarning`，以及 SQLite 特有的 "constraint failed" 消息。

4. **`validateMetadataLocation()`**：比较 base 的 metadata location 与数据库中记录的 location，不一致则抛 `CommitFailedException`（乐观锁机制）。

5. **`createView()`**：检查 strict-mode 下命名空间是否存在、检查同名表和同名视图冲突，然后调用 `JdbcUtil.doCommitCreateView()` 执行 INSERT。

6. **`updateView()`**：调用 `JdbcUtil.updateView()` 执行 UPDATE，验证更新行数为 1。

### core/src/test/java/org/apache/iceberg/jdbc/TestJdbcCatalog.java

**修改目的**：测试 schema 迁移和旧版 schema 向后兼容。

**工作逻辑**：

1. 新增 `testSchemaIsMigratedToAddViewSupport`：使用文件型 SQLite 数据库（非内存，因为跨连接），先通过 `initLegacySchema()` 创建旧版 schema（无 `iceberg_type` 列）并插入两条表记录。然后以 `add-view-support=true` 初始化 JdbcCatalog，验证旧表记录仍可正常列举，且可以创建和列举视图——证明 schema 迁移成功。

2. 新增 `testLegacySchemaSupport`：同样创建旧版 schema，但不设置 `add-view-support` 属性初始化 Catalog。验证旧表记录可正常列举、新表可创建，但 `listViews()` 和 `buildView().create()` 均抛出 `UnsupportedOperationException` 且消息为 `VIEW_WARNING_LOG_MESSAGE`——证明 V0 模式向后兼容。

3. 新增 `initLegacySchema(String jdbcUrl)` 私有方法：通过 `SQLiteDataSource` 直接执行旧版 DDL 和 INSERT 语句，创建不含 `iceberg_type` 列的 `iceberg_tables` 表并插入两条测试表记录。

### core/src/test/java/org/apache/iceberg/jdbc/TestJdbcUtil.java

**修改目的**：测试代码风格统一。

**工作逻辑**：将 `Assertions.assertThat` 改为 `assertThat`（添加对应的 static import），仅为代码风格一致性调整。

### core/src/test/java/org/apache/iceberg/jdbc/TestJdbcViewCatalog.java（新文件）

**修改目的**：通过 `ViewCatalogTests` 基类全面测试 JDBC Catalog 的视图功能。

**工作逻辑**：新文件 66 行，继承 `ViewCatalogTests<JdbcCatalog>`。在 `@BeforeEach` 中使用内存型 SQLite 数据库初始化 `JdbcCatalog`（使用带唯一 UUID 的 `jdbc:sqlite:file::memory:?ic...` URL 确保测试隔离）。实现 `catalog()` 和 `tableCatalog()` 返回同一个 catalog 实例，`requiresNamespaceCreate()` 返回 `true`（JDBC Catalog 需要命名空间预先存在）。通过继承 `ViewCatalogTests`，自动运行所有标准视图 catalog 测试用例（创建、加载、替换、重命名、删除、列举等）。

## 小结

本提交为 JDBC Catalog 添加了完整的视图支持，是本批提交中改动量最大的一个（1069 行新增，123 行删除）。核心设计决策包括：在 `iceberg_tables` 表中新增 `iceberg_type` 列区分表和视图（而非新建独立表），引入 V0/V1 schema 版本管理实现向后兼容，提供 `jdbc.add-view-support` 属性支持自动迁移。实现上将 SQL 逻辑集中到 `JdbcUtil` 中，通过 `isTable` 布尔参数和 `SchemaVersion` 枚举实现表/视图操作和 V0/V1 版本的代码复用。新增 `JdbcViewOperations` 与 `JdbcTableOperations` 结构对称。测试覆盖了 schema 迁移、旧版兼容、以及通过 `ViewCatalogTests` 的全套标准视图测试。
