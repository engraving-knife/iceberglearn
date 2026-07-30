# 提交 1155：Hive: Add View support for HIVE catalog (#9852)

## 提交信息

- **序号**：1155 / 4088
- **哈希**：e449d3405cfdb304c94835845bd8f34a73b4a517
- **短哈希**：e449d3405
- **日期**：2024-09-13（Fri Sep 13 21:45:18 2024 +0530）
- **作者**：Naveen Kumar <nk1506@gmail.com>
- **提交说明**：Hive: Add View support for HIVE catalog (#9852)
- **PR/Issue**：#9852

## 总体目的

Iceberg 此前已为 REST Catalog、JDBC Catalog 等提供了 View 支持，但 `HiveCatalog` 还只能管理 table，无法创建 / 加载 / 列出 / 删除 / 重命名 Iceberg view。Hive Metastore 本身原生支持 `VIRTUAL_VIEW` 类型的表对象，可以承载视图元数据，社区希望复用 HMS 作为 view 的存储后端，让 Hive 用户也能直接使用 Iceberg view。

本提交让 `HiveCatalog` 实现 `ViewCatalog` 接口（通过继承 `BaseMetastoreViewCatalog`），新增 `HiveViewOperations` 负责将 Iceberg view 元数据写入 HMS 的 `VIRTUAL_VIEW` 表对象中，并区分 table 与 view 的同名冲突、提供正确的异常类型（`NoSuchIcebergViewException`、`NoSuchViewException`、`AlreadyExistsException`）。同时配套新增 `CatalogUtil.dropViewMetadata` 用于在 dropView 时清理 metadata 文件，并新增 `NoSuchIcebergViewException` 异常类。

## 如何达成设计目的

1. `HiveCatalog` 改为继承 `BaseMetastoreViewCatalog`（不再直接继承 `BaseMetastoreCatalog`），从而自动获得 `ViewCatalog` 接口与默认 view builder 实现；并实现 `newViewOps(identifier)` 返回 `HiveViewOperations`。
2. 新增 `HiveViewOperations` 类：仿照 `HiveTableOperations` 的结构，实现 `BaseViewOperations` 与 `HiveOperationsBase` 接口。`doRefresh` 从 HMS 读取 `VIRTUAL_VIEW` 表并校验是 Iceberg view；`doCommit` 处理新建 / 更新 / 并发冲突 / 锁 / 异常分类。把 view 元数据写到 HMS 表的 `METADATA_LOCATION_PROP` 参数里，与 table 共用同一套 metadata 文件存储约定。
3. 在 `HiveOperationsBase` 中新增常量 `ICEBERG_VIEW_TYPE_VALUE = "iceberg-view"` 与静态方法 `validateTableIsIcebergView`，与现有 `validateTableIsIceberg` 平行；同时把 `BaseMetastoreCatalog.isTableOrViewException` 中 VIEW 分支由"抛 UnsupportedOperationException"改为调用 `validateTableIsIcebergView`。
4. 在 `api` 模块新增 `NoSuchIcebergViewException`（继承 `NoSuchViewException`），对应已有的 `NoSuchIcebergTableException` 模式。
5. 在 `CatalogUtil` 中新增 `dropViewMetadata(io, metadata)` 工具方法，根据 `gc-enabled` 属性决定是否删除 metadata 文件，与 `dropTableMetadata` 对称。
6. 在 `HiveCatalog` 中实现 `listViews` / `dropView` / `renameView`：
   - `listViews` 通过 `client.getTables(database, "*", TableType.VIRTUAL_VIEW)` 拿到所有 view 名，再批量调用 `listIcebergTables(..., ICEBERG_VIEW_TYPE_VALUE)` 过滤出 Iceberg view。
   - `dropView` 先加载 `ViewMetadata`（用于 GC），再调 HMS `dropTable` 删除，最后调 `CatalogUtil.dropViewMetadata` 清理元数据文件。
   - `renameView` 复用 `renameTableOrView`（新增 `ContentType.VIEW` 参数），并补强为：rename 前先检查目标 table / view 是否已存在，避免覆盖。
7. 把 `renameTableOrView` 中"from 不存在"的 `NoSuchObjectException` 处理按 `contentType` 分别抛 `NoSuchTableException` / `NoSuchViewException`，让语义更准确。
8. 提供 `ViewAwareTableBuilder` / `TableAwareViewBuilder` 两个内部 builder：在 create table 前先检查同名 view 是否存在，在 create view 前先检查同名 table 是否存在，把同名冲突在最早阶段挡掉。这两个 builder 分别继承 `BaseMetastoreViewCatalogTableBuilder` 与 `BaseViewBuilder`。
9. `HiveTableOperations.doCommit` 中补充：当目标名是 `VIRTUAL_VIEW` 时抛 `AlreadyExistsException("View with same name already exists")`，让"用 table 名撞了已有 view"的报错语义清晰。
10. 测试侧：新增 `TestHiveViewCatalog`（继承 `ViewCatalogTests<HiveCatalog>`，覆盖标准 view 行为契约 + Hive 特有的同名冲突场景）与 `TestHiveViewCommits`（覆盖 commit 路径上的锁、并发、异常分类等）；并在 `TestHiveCatalog` 中新增 `testInvalidIdentifiersWithRename` 验证 rename 时 from/to 标识符非法的报错。

## 修改详情

### `api/src/main/java/org/apache/iceberg/exceptions/NoSuchIcebergViewException.java`（新增）

**修改目的**：定义"对象存在但不是 Iceberg view"对应的异常。

**工作逻辑**：继承 `NoSuchViewException`，提供 `(String message, Object... args)` 构造与静态 `check(boolean test, String message, Object... args)` 工具方法。`@FormatMethod` 标注保证格式化字符串的静态检查。

### `core/src/main/java/org/apache/iceberg/CatalogUtil.java`

**修改目的**：为 `HiveCatalog.dropView` 提供 metadata 清理工具。

**工作逻辑**：新增重载 `dropViewMetadata(FileIO io, ViewMetadata metadata)`：读取 `ViewMetadata` 的 `gc-enabled` 属性（默认 `true`），若启用则调 `deleteFile(io, metadata.metadataFileLocation(), "metadata")` 删除 metadata JSON。逻辑与 `dropTableMetadata` 完全对称。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveOperationsBase.java`

**修改目的**：把"是否为 Iceberg view"的校验逻辑集中到接口默认方法里，供 `HiveCatalog` 与 `HiveViewOperations` 共用。

**工作逻辑**：
- 新增常量 `String ICEBERG_VIEW_TYPE_VALUE = "iceberg-view";`（与现有 `table_type` 属性值 `iceberg-table` 平行）。
- 新增静态方法 `validateTableIsIcebergView(Table table, String fullName)`：用 `NoSuchIcebergViewException.check(...)` 校验 `table.getTableType()` 是 `VIRTUAL_VIEW` 且 `TABLE_TYPE_PROP` 参数为 `iceberg-view`，否则抛 `NoSuchIcebergViewException`，错误消息附带 `type` 与 `tableType` 两个值便于排查。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveCatalog.java`

**修改目的**：让 `HiveCatalog` 成为 view-capable 的 catalog。

**工作逻辑**：
- 改为 `extends BaseMetastoreViewCatalog implements SupportsNamespaces, Configurable`（原为 `extends BaseMetastoreCatalog`）；`BaseMetastoreViewCatalog` 自身已实现 `ViewCatalog` 接口并提供 `buildView` 等默认实现。
- 新增 `import` 大量 view 相关类型（`View`、`ViewBuilder`、`ViewMetadata`、`ViewOperations`、`BaseMetastoreViewCatalog`、`Schema`、`Transaction`、`NoSuchViewException`、`Iterables`、`Lists`、`TableType`）。
- 重写 `buildTable(identifier, schema)` 返回 `new ViewAwareTableBuilder(identifier, schema)`，重写 `buildView(identifier)` 返回 `new TableAwareViewBuilder(identifier)`。
- 新增 `listViews(Namespace)`：用 `client.getTables(database, "*", TableType.VIRTUAL_VIEW)` 取 view 名，再按 100 个一批调 `listIcebergTables(..., ICEBERG_VIEW_TYPE_VALUE)` 过滤。`UnknownDBException` → `NoSuchNamespaceException`。
- 新增 `dropView(TableIdentifier)`：先 `newViewOps(identifier)` 拿到 ops，加载 `lastViewMetadata`（NotFound 时仅 warn 不抛），再调 HMS `dropTable(database, viewName, false, false)` 删除，最后调 `CatalogUtil.dropViewMetadata(ops.io(), lastViewMetadata)` 清理 metadata。`NoSuchObjectException` 返回 `false`，其他 `TException` 抛 `RuntimeException`。
- 新增 `renameView(from, to)`：直接委托 `renameTableOrView(from, to, ContentType.VIEW)`。
- 改造 `renameTableOrView`：
  - from 校验从 `if (!isValidIdentifier(from)) throw NoSuchTableException(...)` 改为 `Preconditions.checkArgument(isValidIdentifier(from), ...)`（统一为 `IllegalArgumentException`，并加上 `@SuppressWarnings("checkstyle:CyclomaticComplexity")`）。
  - 在 namespace 存在性检查后追加 `if (tableExists(to)) throw AlreadyExistsException("... Table already exists")` 与 `if (viewExists(to)) throw AlreadyExistsException("... View already exists")`。
  - `NoSuchObjectException` 分支按 `contentType` 分别抛 `NoSuchTableException` / `NoSuchViewException`。
- 改造 `isTableOrViewException`：原 VIEW 分支 `throw new UnsupportedOperationException("View is not supported.")` 改为 `HiveOperationsBase.validateTableIsIcebergView(table, fullName)`。
- 新增 `protected ViewOperations newViewOps(TableIdentifier identifier)` 返回 `new HiveViewOperations(conf, clients, fileIO, name, identifier)`。
- 新增两个内部 builder 类（`ViewAwareTableBuilder` / `TableAwareViewBuilder`），均在 `create()` / `createOrReplace*()` 入口检查同名 view / table 是否存在，存在则抛 `AlreadyExistsException`。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveTableOperations.java`

**修改目的**：让 table commit 路径在撞名时识别 view 并抛语义清晰的异常。

**工作逻辑**：
- `@SuppressWarnings` 中追加 `"MethodLength"`。
- `doCommit` 中，原本当目标 table 已存在且 `METADATA_LOCATION_PROP != null` 时抛 `AlreadyExistsException("Table already exists")`；新增前置判断：若 `tbl.getTableType()` 是 `VIRTUAL_VIEW`，则抛 `AlreadyExistsException("View with same name already exists: %s.%s", database, tableName)`，避免把 view 误报为 table。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveViewOperations.java`（新增）

**修改目的**：实现 Hive 后端的 view 元数据读写与 commit。

**工作逻辑**（重点方法）：
- 字段：`conf` / `metaClients` / `fileIO` / `catalogName` / `fullName` / `database` / `viewName` / `maxHiveTablePropertySize`。构造函数从 `TableIdentifier` 拆出 database / viewName。
- `doRefresh()`：从 HMS `getTable(database, viewName)` 取 `Table`，调 `validateTableIsIcebergView` 校验；从 `METADATA_LOCATION_PROP` 参数取 metadata 位置；`NoSuchObjectException` 时若 `currentMetadataLocation() != null` 抛 `NoSuchViewException`，否则视为新建场景（metadataLocation 保持 null）。最后调 `refreshFromMetadataLocation(metadataLocation)`。
- `doCommit(ViewMetadata base, ViewMetadata metadata)`：复杂的状态机。
  - 计算 `newMetadataLocation = writeNewMetadataIfRequired(metadata)`。
  - 取 `HiveLock lock = lockObject()` 并 `lock.lock()`。
  - 加载 `tbl = loadHmsTable()`：若 tbl 非空，检查 `newView && METADATA_LOCATION_PROP != null` → 抛 `AlreadyExistsException`（区分 view/table）；否则 `updateHiveView = true`。若 tbl 为空，则 `tbl = newHMSView(metadata)` 构造新的 `VIRTUAL_VIEW` 表对象。
  - 用 `HiveOperationsBase.storageDescriptor(...)` 设置 `Sd`（与 table 共用）。
  - 比较 `base.metadataFileLocation()` 与 HMS 当前 `METADATA_LOCATION_PROP`，不一致抛 `CommitFailedException`。
  - 计算 `removedProps`（base 有而 metadata 没有的属性），调 `setHmsTableParameters` 把 Iceberg view properties 全部塞入 HMS 表 parameters，设置 `TABLE_TYPE_PROP=ICEBERG_VIEW_TYPE_VALUE.toUpperCase()`、`METADATA_LOCATION_PROP=newMetadataLocation`、必要时设 `PREVIOUS_METADATA_LOCATION_PROP`，并把 schema 序列化进 parameters。
  - `lock.ensureActive()` → `persistTable(tbl, updateHiveView, hiveLockEnabled ? null : baseMetadataLocation)`（与 table commit 一致，第三个参数用于无锁时的乐观并发控制）。
  - 异常分类：`org.apache.hadoop.hive.metastore.api.AlreadyExistsException` → Iceberg `AlreadyExistsException`；`InvalidObjectException` → `ValidationException`；`LockException` → `CommitStateUnknownException`；其他 `Throwable` 中若消息含 `The table has been modified` → `CommitFailedException`，含 `HIVE_LOCKS does not exist` → `RuntimeException`（提示用嵌入式 metastore 不支持事务）；其余走 `checkCommitStatus` 决定 `SUCCESS` / `FAILURE` / `UNKNOWN`。
  - `finally` 块调 `HiveOperationsBase.cleanupMetadataAndUnlock(io(), commitStatus, newMetadataLocation, lock)`：commit 失败时删除新写入的 metadata 文件并释放锁。
- `setHmsTableParameters`：把 metadata.properties 写入 HMS parameters（跳过 `HMS_TABLE_OWNER`），写入 `uuid`，移除 `obsoleteProps`，设置 `TABLE_TYPE_PROP` / `METADATA_LOCATION_PROP` / `PREVIOUS_METADATA_LOCATION_PROP`，并调 `setSchema` 把 schema 也放进 parameters。
- `newHMSView(metadata)`：构造 HMS `Table` 对象，`tableType` 设为 `VIRTUAL_VIEW`，`viewOriginalText` / `viewExpandedText` 设为 SQL（通过 `sqlFor(metadata)` 取，优先 `dialect=hive` 的 SQL，否则取第一个 SQL 表示）。
- `lockObject()`：若 `hive.lock-enabled`（默认 true）返回 `MetastoreLock`，否则返回 `NoLock`。
- 实现 `HiveOperationsBase` 的访问方法：`database()` / `table()` / `viewName()` / `tableType()` / `metaClients()` / `maxHiveTablePropertySize()` / `io()`。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveCatalog.java`

**修改目的**：覆盖 rename 时非法 identifier 的报错。

**工作逻辑**：新增 `testInvalidIdentifiersWithRename`：分别测试 from 非法（多 level namespace）和 to 非法两种场景，断言抛 `IllegalArgumentException` 且消息包含非法 identifier。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveViewCatalog.java`（新增）

**修改目的**：覆盖 HiveCatalog 的 view 行为契约。

**工作逻辑**：继承 `ViewCatalogTests<HiveCatalog>`，自动跑社区统一的 view 测试套件。Hive 特有用例：
- `testHiveViewAndIcebergViewWithSameName`：先创建一个原生 Hive view（非 Iceberg），再用 `loadView` 加载，断言抛 `NoSuchIcebergViewException`。
- 其他用例覆盖：list、create、replace、rename、drop、同名冲突等。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveViewCommits.java`（新增）

**修改目的**：覆盖 `HiveViewOperations.doCommit` 的并发与异常路径。

**工作逻辑**：约 516 行，主要用 Mockito spy `HiveViewOperations` 模拟各种失败场景：
- `CommitFailedException` 当 base metadata location 与 HMS 中不一致。
- `CommitStateUnknownException` 当 lock heartbeat 失败。
- `AlreadyExistsException` 当并发创建同一 view。
- `ValidationException` 当 HMS 返回 `InvalidObjectException`。
- `RuntimeException` 当消息含 `HIVE_LOCKS does not exist`。
- 各场景下校验 commit 后的 metadata 文件清理行为（commit 失败应删除新 metadata，成功应保留）。

## 小结

- **成效**：`HiveCatalog` 现在完全支持 Iceberg view，与 REST Catalog、JDBC Catalog 行为一致；Hive 用户可以在 HMS 中存储与管理 Iceberg view 元数据，并享受与 table 同等的并发控制、锁、异常分类、GC 等机制。同名 table/view 冲突在最早阶段被拦截，错误语义清晰。
- **影响范围**：`hive-metastore` 模块新增 `HiveViewOperations`（约 389 行）+ 测试（约 839 行），改造 `HiveCatalog` / `HiveOperationsBase` / `HiveTableOperations`；`api` 模块新增 `NoSuchIcebergViewException`；`core` 模块在 `CatalogUtil` 新增 `dropViewMetadata` 工具方法。
- **回迁到 1.4.x 的注意事项**：
  1. 这是一个比较大的功能新增（约 1500 行），回迁到 1.4.x 需要谨慎评估。如果 1.4.x 已经支持 view API（`BaseMetastoreViewCatalog`、`BaseViewOperations`、`ViewCatalog` 等），那么回迁是可行的；否则需要先回迁依赖的 view 基础设施。
  2. 强依赖：`BaseMetastoreViewCatalog`、`BaseMetastoreViewCatalogTableBuilder`、`BaseViewOperations`、`BaseViewBuilder`、`ViewCatalogTests`、`ViewMetadata`、`ViewRepresentation` / `SQLViewRepresentation` 等 view 框架类必须存在于 1.4.x。1.4.x 早期版本可能尚未引入这些类（view 支持是 1.4.0 才基本完整的）。
  3. 必须同时回迁 `NoSuchIcebergViewException`（api 模块）、`CatalogUtil.dropViewMetadata`（core 模块）、`HiveOperationsBase.validateTableIsIcebergView`、`HiveCatalog` 改造、`HiveTableOperations` 改造、`HiveViewOperations` 全量，缺一不可。
  4. 注意 `HiveCatalog.renameTableOrView` 的行为变化：原 from 非法抛 `NoSuchTableException` 改为抛 `IllegalArgumentException`；新增了 to 已存在时的 `AlreadyExistsException` 检查。1.4.x 若已有相关测试需要同步调整断言。
  5. 该改动**不改变**已有 table 的元数据格式，与 1.4.x 已有 HMS 表完全兼容；只新增对 `VIRTUAL_VIEW` 类型表对象的处理，不影响现有 table 读写。
  6. `METADATA_LOCATION_PROP` / `PREVIOUS_METADATA_LOCATION_PROP` / `TABLE_TYPE_PROP` 等 HMS 表参数与 table 共用同一约定，view 的 `TABLE_TYPE_PROP` 值为 `ICEBERG_VIEW_TYPE_VALUE`（即 `ICEBERG-VIEW`），与 table 的 `iceberg-table` 区分。回迁时需保证该值与 main 一致，避免客户端识别错误。
  7. Hive Metastore 的 `VIRTUAL_VIEW` 表对象有 `viewOriginalText` / `viewExpandedText` 字段，本提交把 Iceberg view 的 SQL（优先 hive dialect）写入这两个字段，便于 Hive 自身或其他工具直接读取 view SQL。回迁时需注意若 1.4.x 已有其他工具依赖这两个字段的语义。
