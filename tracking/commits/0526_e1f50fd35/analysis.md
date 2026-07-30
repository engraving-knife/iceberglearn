# 提交 0526：Core: Use V0 SQL schema as default / rename jdbc.add-view-support to jdbc.schema-version

## 提交信息

- **序号**：0526 / 4088
- **哈希**：e1f50fd3505b48e37f98049f8ea243aa9f1e08dd
- **短哈希**：e1f50fd35
- **日期**：2024-02-21 12:15:55 +0100
- **作者**：JB Onofré <jbonofre@apache.org>
- **提交说明**：Core: Use V0 SQL schema as default / rename jdbc.add-view-support to jdbc.schema-version
- **PR/Issue**：#9765

## 总体目的

本提交对 JDBC Catalog 的 schema 版本管理机制做了两件相互关联的事：

1. **将默认 SQL schema 版本回退为 V0**：在此之前，`JdbcCatalog` 默认使用 `SchemaVersion.V1`（即包含 `iceberg_type` 字段、原生支持 View 的 schema），仅在用户显式未启用 `jdbc.add-view-support` 时回退为 V0。这导致 catalog 默认就尝试创建带 view 字段的表，对老用户/老数据库（已存在的 V0 表）造成兼容性压力。本次改动将默认值改为 V0，让新创建的 catalog 表默认不包含 `iceberg_type` 列，保持向前兼容。
2. **将布尔型属性 `jdbc.add-view-support` 重命名为枚举型属性 `jdbc.schema-version`**：原来的属性只是“是否启用 view 支持”的开关，扩展性不足。新属性可以接受 `V0` 或 `V1` 字符串，明确表达所期望的 schema 版本，为后续可能的 V2 schema 留出扩展空间。

通过这两项变更，提交实现了：
- 默认行为向后兼容：默认不会主动迁移已有数据库 schema，避免对生产环境的隐式破坏。
- 控制语义更清晰：用户通过 `jdbc.schema-version=V1` 显式声明期望升级到 V1 schema（即带 view 支持），不再用模糊的布尔开关。

## 如何达成设计目的

整体设计思路是**将“建表”与“schema 升级”拆分为两个独立阶段**，并显式根据用户配置与数据库现有列情况决定 schema 版本。

### 关键设计

1. **`schemaVersion` 字段默认值从 V1 改为 V0**：`JdbcCatalog` 中的 `schemaVersion` 字段初始化为 `JdbcUtil.SchemaVersion.V0`。
2. **重构初始化流程**：原 `initializeCatalogTables()` 方法被拆为两步：
   - `initializeCatalogTables()`：仅负责建表（如果不存在）。建表时使用 `V0_CREATE_CATALOG_SQL`，即创建不带 `iceberg_type` 字段的 V0 schema。
   - `updateSchemaIfRequired()`：单独负责检测并按需升级 schema。
3. **检测与升级策略**：`updateSchemaIfRequired()` 通过 `DatabaseMetaData.getColumns(...)` 检查 `iceberg_type` 列是否存在：
   - 若已存在，将 `schemaVersion` 标记为 V1（无需任何操作）。
   - 若不存在，检查用户配置的 `jdbc.schema-version` 属性（默认 V0）：
     - 若值为 `V1`，执行 `V1_UPDATE_CATALOG_SQL`（`ALTER TABLE ... ADD COLUMN iceberg_type VARCHAR(5)`）并设置 schemaVersion 为 V1。
     - 否则（V0 或未设置），输出告警日志 `VIEW_WARNING_LOG_MESSAGE`，保持 V0 schema。
4. **属性重命名**：`JdbcUtil.ADD_VIEW_SUPPORT_PROPERTY` → `JdbcUtil.SCHEMA_VERSION_PROPERTY`；对应的 SQL 常量也改为语义化命名：`CREATE_CATALOG_SQL` → `V0_CREATE_CATALOG_SQL`、`UPDATE_CATALOG_SQL` → `V1_UPDATE_CATALOG_SQL`。

### 配置属性消费链路

- `JdbcCatalog.PROPERTY_PREFIX` = `"jdbc."`，故 `jdbc.schema-version` 是用户配置入口。
- 该属性被 `updateSchemaIfRequired()` 通过 `PropertyUtil.propertyAsString(catalogProperties, SCHEMA_VERSION_PROPERTY, SchemaVersion.V0.name())` 读取，并使用 `equalsIgnoreCase(V1.name())` 比较以兼容大小写。
- `schemaVersion` 字段决定后续 catalog 操作（如 `listViews`、`buildView`、`doCommit` 等）走 V0 还是 V1 SQL 路径——V0 SQL 不涉及 `iceberg_type` 列。

## 修改详情

### `core/src/main/java/org/apache/iceberg/jdbc/JdbcCatalog.java`

**修改目的**：将默认 schema 版本从 V1 改为 V0，重构初始化逻辑将“建表”与“schema 升级”分离，并改用新的属性名 `jdbc.schema-version`。

**工作逻辑**：

1. **常量与字段调整**：
   - 删除 `import java.sql.Connection;`（不再单独需要 `Connection` 参数）。
   - `VIEW_WARNING_LOG_MESSAGE` 中的提示语由 `set jdbc.add-view-support=true` 改为 `set jdbc.schema-version=V1`。
   - `schemaVersion` 字段默认值：`JdbcUtil.SchemaVersion.V1` → `JdbcUtil.SchemaVersion.V0`。

2. **`initialize()` 调整**：原本 `if (initializeCatalogTables) { initializeCatalogTables(); }` 包在 try-catch 中，现在改为先调用 `initializeCatalogTables()`，再调用 `updateSchemaIfRequired()`，两者各自独立处理异常。

3. **新的 `initializeCatalogTables()`**：
   - 不再抛出 `throws InterruptedException, SQLException`，异常在内部捕获。
   - 第一步：通过 `dbMeta.getTables(...)` 检查 `iceberg_tables` 表是否存在，若不存在则使用 `JdbcUtil.V0_CREATE_CATALOG_SQL` 创建（**V0 schema，无 `iceberg_type` 列**）。若已存在，直接返回 true（不再触发迁移）。
   - 第二步：同样检查 `iceberg_namespace_properties` 表是否存在，不存在则用 `CREATE_NAMESPACE_PROPERTIES_TABLE_SQL` 创建。
   - 异常处理统一抛出 `UncheckedSQLException`/`UncheckedInterruptedException`，错误消息形如 `"Cannot initialize JDBC catalog: ..."`。

4. **新的 `updateSchemaIfRequired()`**（取代旧 `updateCatalogTables(Connection)`）：
   - 通过 `dbMeta.getColumns(null, null, CATALOG_TABLE_VIEW_NAME, RECORD_TYPE)` 检查 `iceberg_type` 列是否已存在：
     - **存在**：`schemaVersion = V1`，记录 debug 日志 "already supports views"。
     - **不存在**：读取 `jdbc.schema-version` 属性（默认 `V0`），用 `equalsIgnoreCase(V1.name())` 比较：
       - 若为 V1：执行 `V1_UPDATE_CATALOG_SQL` 给 `iceberg_tables` 添加 `iceberg_type VARCHAR(5)` 列，并设置 `schemaVersion = V1`。
       - 否则：记录 `VIEW_WARNING_LOG_MESSAGE` 告警，schemaVersion 保持 V0。
   - 异常处理与 `initializeCatalogTables()` 类似，错误消息形如 `"Cannot update JDBC catalog: ..."`。

### `core/src/main/java/org/apache/iceberg/jdbc/JdbcUtil.java`

**修改目的**：重命名属性常量与 SQL 常量，并修改 V0 建表 SQL 去掉 `iceberg_type` 列定义。

**工作逻辑**：

- `ADD_VIEW_SUPPORT_PROPERTY` → `SCHEMA_VERSION_PROPERTY`（值为 `"jdbc.schema-version"`）。
- `CREATE_CATALOG_SQL` → `V0_CREATE_CATALOG_SQL`，**且去掉 `RECORD_TYPE + " VARCHAR(100),"` 这一行**，即 V0 schema 建表语句不再包含 `iceberg_type` 列。这保证新建的 catalog 表与 V0 语义一致。
- `UPDATE_CATALOG_SQL` → `V1_UPDATE_CATALOG_SQL`，内容不变（`ALTER TABLE iceberg_tables ADD COLUMN iceberg_type VARCHAR(5)`）。
- `SchemaVersion` 枚举（V0、V1）本身未变。

### `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcCatalog.java`

**修改目的**：测试用例适配新属性名 `jdbc.schema-version`，并新增 V0 schema 行为测试、参数化已有 V0 兼容性测试。

**工作逻辑**：

- 在多个测试方法的 properties 中添加 `properties.put(JdbcUtil.SCHEMA_VERSION_PROPERTY, JdbcUtil.SchemaVersion.V1.name());`，使现有测试默认使用 V1 schema（保持原有覆盖范围）。
- 新增 `testInitSchemaV0()`：显式设置 `jdbc.schema-version=V0`，验证：
  - 表创建/列表正常工作。
  - `listViews(...)` 抛出 `UnsupportedOperationException`，消息为 `VIEW_WARNING_LOG_MESSAGE`。
  - `buildView(...).create()` 同样抛出 `UnsupportedOperationException`。
- `testSchemaIsMigratedToAddViewSupport()` 中将 `properties.put(JdbcUtil.ADD_VIEW_SUPPORT_PROPERTY, "true")` 改为 `properties.put(JdbcUtil.SCHEMA_VERSION_PROPERTY, JdbcUtil.SchemaVersion.V1.name())`，验证 V0→V1 迁移路径仍工作。
- 原 `testLegacySchemaSupport()` 改为参数化测试 `testExistingV0SchemaSupport(boolean initializeCatalogTables)`，使用 `@ParameterizedTest` + `@ValueSource(booleans = {true, false})`，同时验证 `initializeCatalogTables=true/false` 两种构造方式都能正确识别已有 V0 schema。同时构造 catalog 时显式传入 `new JdbcCatalog(null, null, initializeCatalogTables)`。
- 原 "old style" SQL schema 的硬编码 CREATE TABLE 字符串改为复用 `JdbcUtil.V0_CREATE_CATALOG_SQL`，减少重复。

### `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcViewCatalog.java`

**修改目的**：在 View Catalog 测试基类的初始化中显式启用 V1 schema。

**工作逻辑**：在 `init()` 中加入 `properties.put(JdbcUtil.SCHEMA_VERSION_PROPERTY, JdbcUtil.SchemaVersion.V1.name());`。因为 View Catalog 测试需要 view 支持，而默认 schema 现在是 V0，必须显式声明 V1 才能让 catalog 在初始化时升级到带 `iceberg_type` 列的 schema。

## 小结

- **成效**：本提交将 JDBC Catalog 的默认行为从“激进迁移到 V1”改为“保守保持 V0”，并通过明确的 `jdbc.schema-version` 属性（V0/V1）取代布尔开关 `jdbc.add-view-support`，提升了配置语义清晰度与后续 schema 演进的扩展性。
- **影响范围**：仅影响 `JdbcCatalog` 的初始化与 schema 升级路径，不改变 V1 schema 下既有 view 操作的行为。所有需要 view 功能的用户/测试需要显式设置 `jdbc.schema-version=V1`。
- **回迁到 1.4.x 的注意事项**：
  1. 这是一个**破坏性配置变更**：1.4.x 之前若用户依赖 `jdbc.add-view-support=true` 启用 view，回迁后必须将配置改为 `jdbc.schema-version=V1`，否则 catalog 默认 V0 不会自动迁移，view 操作会抛 `UnsupportedOperationException`。
  2. 默认 schema 行为变化：升级后新建的 catalog 表默认是 V0（无 `iceberg_type` 列），需要 view 的用户必须显式声明 V1。这对老用户透明（仍能识别已有 V0 表），但新部署若依赖 view 必须多配一项。
  3. 测试侧：1.4.x 现有测试若沿用 `ADD_VIEW_SUPPORT_PROPERTY` 需同步改为 `SCHEMA_VERSION_PROPERTY`，且需要 view 的测试需显式设置 V1。
  4. 数据库迁移路径仍保留：用户可在已有 V0 数据库上设置 `jdbc.schema-version=V1`，触发 `ALTER TABLE ADD COLUMN iceberg_type`，平滑升级到 V1。
