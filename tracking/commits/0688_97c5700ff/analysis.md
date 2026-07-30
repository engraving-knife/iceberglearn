# 提交 0688：Core: Fix JDBC Catalog table commit when migrating from schema V0 to V1

## 提交信息
- **序号**：0688 / 4088
- **哈希**：97c5700ff53a4ae0a991231add7e80ab2cae3978
- **短哈希**：97c5700ff
- **日期**：2024-04-16 07:42:50 +0200
- **作者**：JB Onofré <jbonofre@apache.org>
- **提交说明**：Core: Fix JDBC Catalog table commit when migrating from schema V0 to V1 (#10111)
- **PR/Issue**：#10111

## 总体目的

本提交修复了 JDBC Catalog 在 V0 → V1 schema 迁移过程中，旧表（V0 时代创建的表）无法成功 commit 的严重 bug。

**Bug 成因**：

Iceberg 的 JDBC Catalog 在 1.4.x 之后引入了 V1 schema 升级，主要新增了 `RECORD_TYPE` 列，用于在 `iceberg_tables` 表中区分记录是 `TABLE` 还是 `VIEW`。原本 V0 时代只有 `iceberg_tables` 表（只放表），升级后引入了 `iceberg_tables_view`（即 `CATALOG_TABLE_VIEW_NAME`）作为统一存储视图与表的表，并通过 `RECORD_TYPE` 列区分类型。

V1 时代的 commit SQL 模板（`V1_DO_COMMIT_SQL`）原本长这样：

```sql
UPDATE iceberg_tables_view
SET metadata_location = ?, previous_metadata_location = ?
WHERE catalog_name = ?
  AND table_namespace = ?
  AND table_name = ?
  AND metadata_location = ?
  AND record_type = ?
```

最后一个条件 `record_type = ?`（参数通过 `setString(7, isTable ? 'TABLE' : 'VIEW')` 绑定）。

**关键问题**：从 V0 升级到 V1 schema 时，`iceberg_tables_view` 表中的 V0 旧记录（在升级前已存在的表记录）的 `RECORD_TYPE` 列值为 **NULL**（数据库迁移脚本 `V1_UPDATE_CATALOG_SQL` 只新增列，不会回填已有行的值）。在标准 SQL 语义中，`NULL = 'TABLE'` 的求值结果是 `NULL`（被当作 false），因此 WHERE 子句不匹配这些旧记录。

结果：用户在升级后对 V0 时代创建的表执行 commit（如 append 数据文件），`executeUpdate()` 返回 0 行受影响，JdbcTableOperations 会认为 commit 失败，抛出 `CommitStateUnknownException` 或类似异常，导致无法正常提交数据。

**修复目标**：让 V1 的 commit SQL 同时匹配 `RECORD_TYPE = 'TABLE'` 与 `RECORD_TYPE IS NULL` 两种情况，使迁移后的旧表也能正常 commit；同时为 View 单独保留严格匹配（View 是 V1 才引入的概念，因此 View 行必然有 `RECORD_TYPE='VIEW'`）。

## 如何达成设计目的

修复策略：

1. **拆分 SQL 模板**：将一个 `V1_DO_COMMIT_SQL` 拆为两个：
   - `V1_DO_COMMIT_TABLE_SQL`：WHERE 子句使用 `record_type = 'TABLE' OR record_type IS NULL`，兼容旧表（NULL）和新表（'TABLE'）。
   - `V1_DO_COMMIT_VIEW_SQL`：WHERE 子句使用 `record_type = 'VIEW'`，严格匹配视图记录。
2. **根据 `isTable` 选择 SQL**：在 `updateTable` 方法中通过三元运算符选择使用哪个 SQL：
   ```java
   (schemaVersion == SchemaVersion.V1)
       ? (isTable ? V1_DO_COMMIT_TABLE_SQL : V1_DO_COMMIT_VIEW_SQL)
       : V0_DO_COMMIT_SQL
   ```
3. **去掉 `setString(7, ...)` 调用**：因为 RECORD_TYPE 的值已经"内联"进 SQL 字符串常量，不再需要作为参数绑定。这避免了 SQL 拼接的参数序号管理错误。
4. **测试覆盖**：
   - `TestJdbcUtil.testV0toV1SqlStatements` 端到端验证：用 V0 schema 创建表，升级到 V1，再 commit 旧表（验证 commit 成功，updated==1）。
   - `TestJdbcCatalog.initLegacySchema` 改为真正通过 JdbcCatalog 创建表写入 metadata_location，再手动构造"V0 旧记录"以验证迁移后表能正常 append 数据文件。

设计上的合理性：把 RECORD_TYPE 的可空性处理内联到 SQL 字符串里，而不是依赖运行时参数，更直观、更安全；同时也避免了"NULL 不等于任何值"这种 SQL 经典坑。

## 修改详情

### `core/src/main/java/org/apache/iceberg/jdbc/JdbcUtil.java`

**修改目的**：拆分 V1 commit SQL 模板，使表 commit 能同时匹配 RECORD_TYPE='TABLE' 和 RECORD_TYPE IS NULL（兼容 V0 旧记录），同时去掉 RECORD_TYPE 的参数绑定。

**工作逻辑**：

1. 将 `V1_DO_COMMIT_SQL` 重命名为 `V1_DO_COMMIT_TABLE_SQL`，并把 WHERE 子句中的 `record_type = ?` 改为：
   ```sql
   record_type = 'TABLE' OR record_type IS NULL
   ```
   注意 RECORD_TYPE 的值 'TABLE' 直接以字符串字面量嵌入 SQL（用单引号包围）。

2. 新增 `V1_DO_COMMIT_VIEW_SQL`，结构与 TABLE 版相同，但 WHERE 子句使用 `record_type = 'VIEW'`（视图在 V1 才引入，不可能有 NULL，因此严格匹配）。

3. 在 `updateTable` 方法（diff 中约 531-546 行）中：
   - SQL 选择改为基于 `isTable` 标志：
     ```java
     (schemaVersion == SchemaVersion.V1)
         ? (isTable ? V1_DO_COMMIT_TABLE_SQL : V1_DO_COMMIT_VIEW_SQL)
         : V0_DO_COMMIT_SQL
     ```
   - 删除原 `if (schemaVersion == SchemaVersion.V1) { sql.setString(7, isTable ? TABLE_RECORD_TYPE : VIEW_RECORD_TYPE); }` 这段参数绑定代码。
   - 现在 PreparedStatement 只需 6 个参数（newMetadataLocation、oldMetadataLocation、catalogName、namespace、tableName、oldMetadataLocation），与 SQL 模板中的 `?` 占位符数量严格一致。

修改前后 SQL 对比（V1 表 commit）：

```sql
-- 修改前
UPDATE iceberg_tables_view
SET metadata_location = ?, previous_metadata_location = ?
WHERE catalog_name = ?
  AND table_namespace = ?
  AND table_name = ?
  AND metadata_location = ?
  AND record_type = ?        -- NULL 旧表无法匹配，commit 返回 0 行 -> 失败

-- 修改后
UPDATE iceberg_tables_view
SET metadata_location = ?, previous_metadata_location = ?
WHERE catalog_name = ?
  AND table_namespace = ?
  AND table_name = ?
  AND metadata_location = ?
  AND (record_type = 'TABLE' OR record_type IS NULL)  -- 旧表（NULL）也能匹配
```

### `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcCatalog.java`

**修改目的**：增强 `initLegacySchema` 测试夹具，使其真正写入非空 metadata_location；并新增"迁移后能 append 数据文件"的断言，回归验证 commit 不再抛异常。

**工作逻辑**：

1. 在已有"V0 → V1 schema 迁移后表存在"的测试中，追加：
   - 创建 `namespace2/table3`；
   - 对迁移后的旧表 `namespace1/table1`、`namespace1/table2` 调用 `loadTable(...).newAppend().appendFile(FILE_A).commit()`，断言不会抛异常，且表依然存在。
2. 新增辅助方法 `createMetadataLocationViaJdbcCatalog`：
   - 创建一个临时 SQLite 数据库；
   - 用 `CatalogUtil.buildIcebergCatalog(...)` 构造一个独立的 JdbcCatalog，在其中通过 `buildTable(...).create()` 真正创建表，从而获得一条真实可用的 `metadata_location`；
   - 通过原生 SQL 查询 `iceberg_tables` 表读出该 metadata_location 返回给调用方。
3. 修改 `initLegacySchema` 方法：
   - 调用 `createMetadataLocationViaJdbcCatalog` 为 `table1` 和 `table2` 获取真实的 metadata_location；
   - 在向"V0 旧记录表"插入数据时，把 `null` 替换为该 metadata_location 字符串，使迁移后的旧表记录具有有效的 metadata 文件路径（这样后续 `loadTable().newAppend()` 才能正确加载并 commit）。

这一改动让测试更接近真实场景（旧表必须有有效的 metadata_location 才能被 load），也使得"迁移后 append commit"的回归测试有意义。

### `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcUtil.java`

**修改目的**：新增 `testV0toV1SqlStatements` 测试，端到端验证 V0→V1 schema 升级路径下，旧表和新表都能成功 commit。

**工作逻辑**：
1. 用 `V0_CREATE_CATALOG_SQL` 创建"老式" catalog 表。
2. 通过 `JdbcUtil.doCommitCreateTable(SchemaVersion.V0, ...)` 在 V0 schema 下创建 `table1` 和 `table2`。
3. 用 `V0_LIST_TABLE_SQL` 验证两个表存在。
4. 执行 `V1_UPDATE_CATALOG_SQL` 把 schema 升级到 V1（新增 RECORD_TYPE 列）。
5. 用 `doCommitCreateTable(SchemaVersion.V1, ...)` 在 V1 schema 下创建 `table3`，新表应该有 RECORD_TYPE='TABLE'。
6. 用 `V0_LIST_TABLE_SQL` 读取所有表，断言：
   - `table1`：RECORD_TYPE IS NULL（V0 旧记录）；
   - `table2`：RECORD_TYPE IS NULL（V0 旧记录）；
   - `table3`：RECORD_TYPE = 'TABLE'（V1 新记录）。
7. 调用 `JdbcUtil.updateTable(SchemaVersion.V1, ..., "table3", "newLocation", "testLocation")` 验证 V1 新表 commit 成功（updated=1）。
8. **关键回归**：调用 `JdbcUtil.updateTable(SchemaVersion.V1, ..., "table1", "newLocation", "testLocation")` 验证 V0 迁移过来的旧表也能 commit 成功（updated=1），这正是修复要解决的核心 bug。

## 小结
- **成效**：成功修复了 V0 → V1 schema 迁移后旧表无法 commit 的严重 bug。修复采用 SQL WHERE 子句兼容 NULL 的方式，最小化对现有逻辑的侵入；同时通过拆分 TABLE/VIEW SQL 模板让意图更清晰。
- **影响范围**：
  - `core/src/main/java/org/apache/iceberg/jdbc/JdbcUtil.java` 的 V1 commit SQL 模板和 `updateTable` 方法。
  - 所有使用 JDBC Catalog 的用户在升级到 V1 schema 后会受益；尤其是有大量 V0 旧表需要保留使用的用户。
- **回迁到 1.4.x 的注意事项**：
  1. **依赖前置特性**：该修复依赖 V0/V1 schema 双轨机制（`SchemaVersion`、`V0_DO_COMMIT_SQL`、`V1_UPDATE_CATALOG_SQL`、`CATALOG_TABLE_VIEW_NAME`、`RECORD_TYPE` 等）。如果 1.4.x 还没有引入 V1 schema 的整套机制，本补丁无法独立回迁——需要先把 V1 schema 升级特性整体回迁到 1.4.x。
  2. 若 1.4.x 已经引入 V1 schema 但未应用本修复，则存在"升级后旧表无法 append/commit"的严重回归，强烈建议优先回迁。
  3. 回迁测试时需要确保 `createMetadataLocationViaJdbcCatalog` 和 `testV0toV1SqlStatements` 这两个新测试能在 1.4.x 的测试环境下正常运行（依赖 SQLite、`CatalogUtil.buildIcebergCatalog`）。
  4. 注意 SQL 字符串中 `'TABLE'` 的引号转义在 1.4.x 中是否与 main 一致，避免拼接错误。
