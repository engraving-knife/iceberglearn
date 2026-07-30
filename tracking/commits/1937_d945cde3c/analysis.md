# 提交 1937：Flink: Backport support create table like in flink catalog to Flink v1.18 and v1.19

## 提交信息

- **序号**：1937 / 4088
- **哈希**：d945cde3c289bfdf61cf4352a7089d1d6462f41b
- **短哈希**：d945cde3c
- **日期**：2025-03-30 23:22:55 +0200
- **作者**：Swapna Marru
- **提交说明**：Flink: Backport support create table like in flink catalog to Flink v1.18 and v1.19（backports #12199）
- **PR/Issue**：#12199（被回溯的原始 PR）

## 总体目的

此提交将此前已合入主分支（针对更高 Flink 版本）的 PR #12199 回溯（backport）到 Iceberg 的 Flink v1.18 与 v1.19 两个模块，使这两个版本同样支持 Flink 的 `CREATE TABLE LIKE` 语法在 Iceberg Catalog 中的使用。

Flink 的 `CREATE TABLE LIKE` 语义通过 `Catalog.getTable()` 读取源表的属性（包括 schema、分区、属性），然后应用到新表创建流程。问题在于：当源表位于 Iceberg catalog，而目标表要创建在另一个 Iceberg catalog（或 Flink 内置 catalog）时，新表创建需要源 Iceberg catalog 的连接配置（如 catalog 类型、warehouse 路径、认证信息等），但这些信息并不在 Iceberg table 的 `properties` 中（它们是 catalog 级配置而非 table 级）。因此 `CREATE TABLE LIKE` 跨 catalog 时无法重建源 catalog 来读取/创建表。

本提交的解决思路是：在 `FlinkCatalog.getTable()` 返回 `CatalogTable` 时，额外注入两个保留属性——`connector`（值为 `iceberg`，标识这是一个 Iceberg connector 表）和 `src-catalog`（源 catalog 名称、库名、表名与全部 catalog 属性序列化为 JSON 字符串）。当目标表通过 `FlinkDynamicTableFactory` 创建时，`mergeSrcCatalogProps` 解析 `src-catalog` JSON，将源 catalog 的配置项（catalog-name、catalog-database、catalog-table、catalog-props）扁平化合并到表属性中，从而能正确重建源 Iceberg catalog 并创建新表。同时对保留属性做校验，防止用户在普通建表时手动设置这些保留键。

## 如何达成设计目的

整体设计围绕"源 catalog 信息序列化传递 + 目标 catalog 反序列化重建"展开，关键组件协作：

1. **`FlinkCreateTableOptions`（新增）**：集中定义 `CREATE TABLE LIKE` 相关的配置选项与保留属性键。提供 `toJson`/`fromJson` 将源 catalog 元信息（catalogName、catalogDb、catalogTable、catalogProps）序列化为 JSON 字符串以便嵌入 table properties（Flink API 只接受 `Map<String,String>`，无法直接传嵌套结构），并在目标侧反序列化还原。同时定义保留属性键常量：`SRC_CATALOG_PROPS_KEY = "src-catalog"`、`CONNECTOR_PROPS_KEY = "connector"`、`LOCATION_KEY = "location"`。

2. **`FlinkCatalog.getTable()`**：除返回 table 自身属性外，额外注入 `connector=iceberg` 与 `src-catalog=<JSON>`。先校验源表自身不含这两个保留键（防止恶意/误用），再合并属性返回 `CatalogTableImpl`。

3. **`FlinkCatalog.createTable()`**：调整校验逻辑——原本禁止在 Iceberg catalog 内用 `connector=iceberg` 建表，现在允许当 `connector=iceberg` 且无 `src-catalog` 时（即 LIKE 流程）通过；纯手写 `connector=iceberg` 仍禁止。

4. **`FlinkCatalog.createIcebergTable()`**：过滤保留属性（`location`、`connector`、`src-catalog`），不将它们持久化到 Iceberg table properties（`location` 单独处理为表 location）。

5. **`FlinkDynamicTableFactory`**：将原本内部定义的 catalog 配置选项（`CATALOG_NAME` 等）改为引用 `FlinkCreateTableOptions` 中的公共定义；新增 `mergeSrcCatalogProps`，在创建 catalog 前从 `src-catalog` JSON 解析源 catalog 属性并合并到 tableProps，使 `FlinkCatalogFactory.createCatalog` 能拿到完整配置重建源 catalog。

6. **`FlinkCatalogFactory`**：将 catalog properties 透传给 `FlinkCatalog` 构造器（新增 `catalogProps` 字段），供 `getTable` 序列化时使用。

## 修改详情

以下修改对 Flink v1.18 与 v1.19 两个模块完全相同（成对出现），以 v1.18 为例描述。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/FlinkCatalog.java` (修改, +68/-9 lines)

**修改目的**：在 `getTable` 中注入源 catalog 信息，调整 `createTable` 校验与属性过滤。

**工作逻辑**：
- 新增字段 `catalogProps`（catalog 级属性），构造器新增参数接收。
- `getTable`：用 `FlinkCreateTableOptions.toJson(name, db, table, catalogProps)` 序列化源 catalog 信息；校验源表 properties 不含 `connector`/`src-catalog` 保留键；构建合并属性（`connector=iceberg` + `src-catalog=<json>` + table 原属性），调用新的 `toCatalogTableWithProps` 返回。
- `createTable`：校验改为"仅当 `connector=iceberg` 且 `src-catalog` 为 null 时禁止"（即允许 LIKE 流程的 connector，禁止手写 connector）。
- `createIcebergTable`：用 `isReservedProperty` 过滤 `location`/`connector`/`src-catalog`，`location` 单独提取，其余保留属性不持久化。
- 新增 `isReservedProperty(prop)` 辅助方法。
- 原 `toCatalogTable(table)` 重构为委托 `toCatalogTableWithProps(table, table.properties())`，保留旧方法签名兼容。
- 多处 `"location"` 字符串字面量替换为 `FlinkCreateTableOptions.LOCATION_KEY` 常量。
- 类上加 `@Internal` 注解。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/FlinkCatalogFactory.java` (修改, +1/-1 lines)

**修改目的**：将 catalog properties 透传给 `FlinkCatalog`。

**工作逻辑**：`createCatalog` 中构造 `FlinkCatalog` 时新增传入 `properties` 参数（即完整的 catalog 配置），使 `getTable` 能序列化这些配置。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/FlinkCreateTableOptions.java` (新增, +116 lines)

**修改目的**：集中定义 LIKE 相关配置选项、保留属性键与 JSON 序列化逻辑。

**工作逻辑**：
- 定义 `ConfigOption`：`CATALOG_NAME`、`CATALOG_TYPE`、`CATALOG_DATABASE`（默认 default）、`CATALOG_TABLE`、`CATALOG_PROPS`（map 类型）。
- 定义保留属性键常量：`SRC_CATALOG_PROPS_KEY = "src-catalog"`、`CONNECTOR_PROPS_KEY = "connector"`、`LOCATION_KEY = "location"`。
- `toJson(catalogName, catalogDb, catalogTable, catalogProps)`：用 `JsonUtil.generate` 序列化为 JSON 字符串（含 catalog-name/database/table/props 字段）。
- `fromJson(json)`：用 `JsonUtil.parse` 反序列化还原 `FlinkCreateTableOptions` 实例。
- 提供 `catalogName()`/`catalogDb()`/`catalogTable()`/`catalogProps()` 访问器。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/FlinkDynamicTableFactory.java` (修改, +89/-46 lines)

**修改目的**：消费 `src-catalog` 属性重建源 catalog。

**工作逻辑**：
- 移除内部定义的 `CATALOG_NAME`/`CATALOG_TYPE`/`CATALOG_DATABASE`/`CATALOG_TABLE` 配置选项，改为引用 `FlinkCreateTableOptions` 中的公共定义（`requiredOptions`/`optionalOptions` 同步更新）。
- 新增 `mergeSrcCatalogProps(tableProps)`：若 `src-catalog` 存在，解析 JSON 得到源 catalog 的 name/db/table/props，扁平化合并到属性 map（源 catalog 属性优先于 table 属性），返回不可变 map；否则原样返回。在 `createDynamicTableSource`/`Sink` 路径中调用此方法处理 tableProps，使后续 `FlinkCatalogFactory.createCatalog` 能拿到完整配置。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/TestFlinkCatalogTable.java` (修改, +44/-1 lines)

**修改目的**：新增 `CREATE TABLE LIKE` 测试。

**工作逻辑**：
- `testCreateTableLikeInDiffIcebergCatalog`：在 Iceberg catalog1 建表，在另一个 Iceberg catalog2 的 testdb 中 `CREATE TABLE ... LIKE` 源表，验证 schema 正确复制。
- `testCreateTableLikeInFlinkCatalog`：在 Iceberg catalog 建表，在 Flink 内置 `default_catalog` 中 `CREATE TABLE ... LIKE`，验证 schema 复制且目标表 options 中包含 `connector=iceberg` 与正确的 `src-catalog` JSON。
- `catalogTable` 辅助方法重载支持指定 catalog/database/table。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceSql.java` (修改, +16 lines)

**修改目的**：补充 LIKE 场景的 SQL 测试用例。

**工作逻辑**：新增测试覆盖通过 LIKE 创建表后用 Iceberg source 读取的端到端流程。

### Flink v1.19 模块（同上 6 个文件）

**修改目的**：对 Flink v1.19 模块应用完全相同的修改。

**工作逻辑**：v1.19 与 v1.18 的代码改动一致（成对），保证两个版本行为同步。

## 总结

本提交将 PR #12199 回溯到 Flink v1.18 与 v1.19 模块，使这两个版本支持 Flink 的 `CREATE TABLE LIKE` 在 Iceberg Catalog 中跨 catalog 使用。核心机制是：在 `FlinkCatalog.getTable()` 返回时将源 catalog 的连接配置序列化为 JSON 嵌入保留属性 `src-catalog`，目标侧 `FlinkDynamicTableFactory.mergeSrcCatalogProps` 反序列化并合并这些配置以重建源 Iceberg catalog。新增 `FlinkCreateTableOptions` 集中管理配置选项与序列化逻辑，并对保留属性做校验防止误用。两个 Flink 版本模块改动完全一致。
