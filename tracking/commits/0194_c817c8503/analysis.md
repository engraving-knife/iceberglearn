# 提交 0194：Hive: Refactor HiveTableOperations with common code for View. (#9011)

## 提交信息

- **序号**：0194 / 4088
- **哈希**：c817c8503d8570ed5b8a187471e02a0895f34598
- **短哈希**：c817c8503
- **日期**：2023-11-24 06:46:39 +0100
- **作者**：Naveen Kumar
- **提交说明**：Hive: Refactor HiveTableOperations with common code for View. (#9011)
- **PR/Issue**：#9011

## 总体目的

这个提交是对 Hive Metastore 集成层的一次重构，目的是为后续支持 Iceberg View（视图）在 Hive Catalog 中的存储铺路。Iceberg 正在推进 SQL View 支持（`View` API、`ViewCatalog`），而 Hive Metastore 中 table、view、materialized_view 都以 `Table` 对象存储，只是 `TableType` 不同。原先所有与 HMS 交互的通用逻辑（建 HMS Table、设置 StorageDescriptor、设置 schema、persistTable、validateTableIsIceberg、cleanupMetadata 等）都内联在 `HiveTableOperations` 中，且大量方法/字段是 `private` 或与 table 语义绑定，无法被未来的 `HiveViewOperations` 复用。

本提交把这些通用逻辑抽到一个新的接口 `HiveOperationsBase` 中，`HiveTableOperations` 实现该接口。`HiveOperationsBase` 以 `default` 方法提供共用实现，子类只需通过几个抽象方法（`tableType()`、`metaClients()`、`maxHiveTablePropertySize()`、`database()`、`table()`）提供具体上下文即可。这样后续 `HiveViewOperations` 只需实现同一接口、返回 `TableType.VIRTUAL_VIEW` 等即可复用全部 HMS 持久化逻辑，避免代码重复。

这是 Iceberg 向多对象类型（table/view/materialized-view）统一 HMS 操作演进的关键重构，对代码可维护性与后续 View 落地意义重大。

## 如何达成设计目的

设计思路是"提取公共接口 + default 方法实现 + 抽象钩子"。新建 `HiveOperationsBase` 接口，把原 `HiveTableOperations` 中的实例方法（`persistTable`、`setSchema`、`setField`、`exposeInHmsProperties`、`newHmsTable`、`storageDescriptor`、`cleanupMetadata`、`validateTableIsIceberg`、`hmsEnvContext`）迁移为接口的 `default` 或 `static` 方法；把原 `private` 常量提升为接口常量；把原来直接访问的实例字段（`database`、`tableName`、`metaClients`、`maxHiveTablePropertySize`）替换为接口抽象方法。`HiveTableOperations` 改为 `implements HiveOperationsBase`，并实现这些抽象方法返回自身字段，原来内联的代码删除。`HiveCatalog` 中对 `validateTableIsIceberg` 的静态调用从 `HiveTableOperations` 改指 `HiveOperationsBase`。同时把 `newHmsTable` 的 owner 来源从 `TableMetadata` 改为接口方法传入参数，因为 View 也需要建 HMS Table 但语义不同。

## 修改详情

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveOperationsBase.java`（新增）

**修改目的**：定义 table/view/materialized-view 共用的 HMS 操作接口，承载所有通用 HMS 持久化逻辑。

**工作逻辑**：接口声明：
- 常量：`HIVE_TABLE_PROPERTY_MAX_SIZE`、`HIVE_TABLE_PROPERTY_MAX_SIZE_DEFAULT`（32672）、`NO_LOCK_EXPECTED_KEY`、`NO_LOCK_EXPECTED_VALUE`、`LOG`。
- 抽象方法（由实现类提供上下文）：`TableType tableType()`、`ClientPool<IMetaStoreClient, TException> metaClients()`、`long maxHiveTablePropertySize()`、`String database()`、`String table()`。
- `default` 方法：
  - `hmsEnvContext(String metadataLocation)`：构造 HMS alterTable 的环境上下文，含 `expected_parameter_key/value` 用于乐观锁（无 location 时返回空 map）。
  - `exposeInHmsProperties()`：当 `maxHiveTablePropertySize > 0` 时才把 Iceberg 元数据写入 HMS 表参数。
  - `setSchema(TableMetadata, Map<String,String>)`：移除旧 `CURRENT_SCHEMA`，若暴露则写入 schema JSON（受 `setField` 长度限制）。
  - `setField(Map, key, value)`：值长度不超过 `maxHiveTablePropertySize` 才放入，否则 warn。
  - `persistTable(Table hmsTable, boolean updateHiveTable, String metadataLocation)`：`updateHiveTable=true` 走 `MetastoreUtil.alterTable`（带 envContext 乐观锁），否则走 `client.createTable`。
  - `newHmsTable(String hmsTableOwner)`：构造 HMS `Table` 对象，owner 由参数传入（不再从 metadata 取），`tableType()` 决定 `TableType.name()`；若为 `EXTERNAL_TABLE` 额外设 `EXTERNAL=TRUE` 参数。`Preconditions.checkNotNull(hmsTableOwner, ...)`。
- `static` 方法：
  - `validateTableIsIceberg(Table table, String fullName)`：校验 HMS 表参数中 `TABLE_TYPE_PROP` 为 `ICEBERG`，否则抛 `NoSuchIcebergTableException`。
  - `storageDescriptor(TableMetadata, boolean hiveEngineEnabled)`：根据 schema、location、是否启用 hive engine 构造 `StorageDescriptor`（InputFormat/OutputFormat/SerDe）。
  - `cleanupMetadata(FileIO io, String commitStatus, String metadataLocation)`：当 `commitStatus` 为 "FAILURE" 时删除未提交的 metadata 文件（注意参数由枚举 `CommitStatus` 改为字符串，以接口解耦）。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveTableOperations.java`

**修改目的**：实现 `HiveOperationsBase`，删除已上提的代码，改为调用接口方法。

**工作逻辑**：
- 类声明改为 `public class HiveTableOperations extends BaseMetastoreTableOperations implements HiveOperationsBase`。
- 删除原 `private` 常量 `HIVE_TABLE_PROPERTY_MAX_SIZE`、`NO_LOCK_EXPECTED_*`、`HIVE_TABLE_PROPERTY_MAX_SIZE_DEFAULT`（上提到接口）。
- 删除原 `persistTable`、`newHmsTable`、`setSchema`、`setField`、`exposeInHmsProperties`、`storageDescriptor`、`validateTableIsIceberg`、`cleanupMetadata`（部分逻辑改为调用 `HiveOperationsBase` 的 static/default 方法）。
- 新增 `@Override` 实现抽象方法：`maxHiveTablePropertySize()` 返回字段、`database()` 返回 `database`、`table()` 返回 `tableName`、`tableType()` 返回 `TableType.EXTERNAL_TABLE`、`metaClients()` 返回 `metaClients`。
- `doRefresh()` 中 `validateTableIsIceberg` 改为 `HiveOperationsBase.validateTableIsIceberg`。
- `doCommit()` 中：新建表时由原 `newHmsTable(metadata)` 改为 `newHmsTable(metadata.property(HiveCatalog.HMS_TABLE_OWNER, HiveHadoopUtil.currentUser()))`——owner 计算从 `newHmsTable` 内部移到调用点（因为接口方法不持有 metadata）。`storageDescriptor` 改为 `HiveOperationsBase.storageDescriptor(metadata, hiveEngineEnabled)`。
- `cleanupMetadataAndUnlock` 中原内联的 `if (commitStatus == FAILURE) io().deleteFile(...)` 改为调用 `HiveOperationsBase.cleanupMetadata(io(), commitStatus.name(), metadataLocation)`（注意枚举转字符串）。
- 移除多个不再需要的 import（`SerDeInfo`、`StorageDescriptor`、`SchemaParser`、`NoSuchIcebergTableException`、`Preconditions` 等）。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveCatalog.java`

**修改目的**：把对 `HiveTableOperations.validateTableIsIceberg` 的静态调用改为 `HiveOperationsBase.validateTableIsIceberg`。

**工作逻辑**：`renameTable` 中 `HiveTableOperations.validateTableIsIceberg(table, fullTableName(name, from))` 改为 `HiveOperationsBase.validateTableIsIceberg(table, fullTableName(name, from))`，使该校验逻辑的归属与可复用性更清晰，也避免在 rename 非 table 对象时误耦合到 `HiveTableOperations`。

## 小结

把 `HiveTableOperations` 中与 HMS 交互的通用逻辑提取到 `HiveOperationsBase` 接口，为后续 Hive Catalog 支持 Iceberg View（及 materialized view）复用同一套 HMS 持久化代码奠定基础，是 Iceberg 多对象类型统一管理的重构性一步。
