# 提交 1971：Hive: Refactor HMS table parameter setting to be able to reuse (#12461)

## 提交信息

- **序号**：1971 / 4088
- **哈希**：369fe89138ca52ef9437fe657324c659889a8d88
- **短哈希**：369fe8913
- **日期**：2025-04-07 12:34:53 +0200
- **作者**：Zoltan Ratkai
- **提交说明**：Hive: Refactor HMS table parameter setting to be able to reuse (#12461)
- **PR/Issue**：#12461

## 总体目的

本提交对 Hive Metastore（HMS）表参数设置逻辑进行重构，将原本散落在 `HiveTableOperations` 和 `HiveViewOperations` 中的、存在大量重复的 HMS 参数设置代码抽取到一个新的工具类 `HMSTablePropertyHelper` 中，以便在表（Table）和视图（View）操作之间复用。

在重构前，`HiveTableOperations.setHmsTableParameters()` 和 `HiveViewOperations.setHmsTableParameters()` 两个方法各自维护一份高度相似的逻辑：把 Iceberg 属性推送到 HMS、设置 table type、metadata location、previous metadata location、schema、过期属性清理等。两者主要区别在于表使用 `TableMetadata` 并额外设置 storage handler、统计信息、快照统计、分区 spec、排序顺序；而视图使用 `ViewMetadata` 且只设置 schema。这种重复导致维护负担：任何对公共逻辑的修改都需要在两处同步，容易遗漏。

通过抽取公共逻辑到 `HMSTablePropertyHelper`（提供 `updateHmsTableForIcebergTable` 和 `updateHmsTableForIcebergView` 两个入口，以及共享的 `setCommonParameters` 等私有/包级方法），代码更加内聚，后续修改只需在一处进行。同时原来放在 `HiveOperationsBase` 接口中的 `setSchema`、`setField`、`exposeInHmsProperties` 默认方法也被移入工具类，使接口更专注于元数据操作契约本身。

## 如何达成设计目的

整体设计是引入一个无状态的工具类 `HMSTablePropertyHelper`，其所有方法均为静态方法，按职责拆分：

1. **两个公开入口**：
   - `updateHmsTableForIcebergTable(...)`：处理 Iceberg 表的 HMS 参数设置，包含 storage handler、统计信息、快照统计、分区 spec、排序顺序等表特有逻辑。
   - `updateHmsTableForIcebergView(...)`：处理 Iceberg 视图的 HMS 参数设置，逻辑较简单。
2. **共享私有方法 `setCommonParameters(...)`**：封装表与视图都需要的公共逻辑（uuid、过期属性清理、table type、metadata location、previous metadata location、schema 设置）。
3. **细分的 setter 方法**：`setStorageHandler`、`setSnapshotStats`、`setSnapshotSummary`、`setPartitionSpec`、`setSortOrder`、`setSchema`、`setField`、`exposeInHmsProperties`，这些方法从 `HiveTableOperations`/`HiveOperationsBase` 迁移而来，并把原先依赖实例字段 `maxHiveTablePropertySize()` 的地方改为通过参数传入 `maxHiveTablePropertySize`，从而实现无状态化。
4. 调用方（`HiveTableOperations`、`HiveViewOperations`）改为调用工具类的静态方法，并删除各自的私有 `setHmsTableParameters` 及相关 setter。
5. `HiveOperationsBase` 接口中删除 `setSchema`、`setField`、`exposeInHmsProperties` 默认方法（已迁移到工具类）。
6. 测试 `TestHiveCatalog` 中原本通过 `HiveTableOperations` 实例调用的 setter 改为直接调用 `HMSTablePropertyHelper` 的静态方法，并把 `maxHiveTablePropertySize` 作为参数显式传入。

## 修改详情

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HMSTablePropertyHelper.java` (新增, +272/-0 lines)

**修改目的**：新建工具类承载所有 HMS 表/视图参数设置逻辑。

**工作逻辑**：
- `ICEBERG_TO_HMS_TRANSLATION` 映射（由原 `BiMap` 改为普通 `Map`）用于 Iceberg 属性名到 HMS 属性名的翻译，例如 `gc.enabled` → `external.table.purge`。
- `updateHmsTableForIcebergTable`：将 Iceberg 表属性推送到 HMS parameters（带属性名翻译、过滤 HMS_TABLE_OWNER），调用 `setCommonParameters` 设置公共属性，`setStorageHandler` 根据 `hiveEngineEnabled` 设置/移除 storage handler，写入基本统计（NUM_FILES/ROW_COUNT/TOTAL_SIZE），再调用 `setSnapshotStats`/`setPartitionSpec`/`setSortOrder`。
- `updateHmsTableForIcebergView`：将视图属性推送到 HMS parameters，调用 `setCommonParameters`（table type 使用 `ICEBERG_VIEW_TYPE_VALUE`）。
- `setCommonParameters`：设置 uuid、移除过期属性、设置 table type、metadata location、previous metadata location，并调用 `setSchema`。
- 各 setter 方法（`setSnapshotStats`、`setSnapshotSummary`、`setPartitionSpec`、`setSortOrder`、`setSchema`、`setField`）从原 `HiveTableOperations` 迁移，`maxHiveTablePropertySize` 通过参数传入；带 `@VisibleForTesting` 注解便于测试直接调用。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveOperationsBase.java` (修改, +0/-23 lines)

**修改目的**：移除已迁移到工具类的默认方法。

**工作逻辑**：删除 `exposeInHmsProperties()`、`setSchema(Schema, Map)`、`setField(Map, String, String)` 三个 default 方法及相关 import（`SchemaParser`、`TableProperties`）。这些逻辑现在由 `HMSTablePropertyHelper` 提供。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveTableOperations.java` (修改, +7/-168 lines)

**修改目的**：移除本地参数设置逻辑，改为调用工具类。

**工作逻辑**：
- 删除 `ICEBERG_TO_HMS_TRANSLATION` BiMap、`translateToIcebergProp(String)` 静态方法及其文档注释。
- 删除私有 `setHmsTableParameters` 方法及 `setSnapshotStats`/`setSnapshotSummary`/`setPartitionSpec`/`setSortOrder` 等实例方法（约 120+ 行）。
- 在 `persistFields` 提交逻辑中，将原 `setHmsTableParameters(newMetadataLocation, tbl, metadata, removedProps, hiveEngineEnabled, summary)` 调用替换为 `HMSTablePropertyHelper.updateHmsTableForIcebergTable(newMetadataLocation, tbl, metadata, removedProps, hiveEngineEnabled, maxHiveTablePropertySize, currentMetadataLocation())`。
- 清理不再需要的 import（`GC_ENABLED`、`JsonProcessingException`、`Locale`、`Map`、`Optional`、`PartitionSpecParser`、`Snapshot`、`SnapshotSummary`、`SortOrderParser`、`BiMap`、`ImmutableBiMap`、`ImmutableMap`、`Maps`、`JsonUtil`、`hive_metastoreConstants` 等）。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveViewOperations.java` (修改, +7/-37 lines)

**修改目的**：视图操作同样改为调用工具类。

**工作逻辑**：
- 删除私有 `setHmsTableParameters(String, Table, ViewMetadata, Set)` 方法（约 30 行）。
- 在 `persistView` 提交逻辑中改为调用 `HMSTablePropertyHelper.updateHmsTableForIcebergView(newMetadataLocation, tbl, metadata, removedProps, maxHiveTablePropertySize, currentMetadataLocation())`。
- 清理不再需要的 import（`Locale`、`Map`、`Optional` 等）。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveCatalog.java` (修改, +12/-10 lines)

**修改目的**：测试改为直接调用工具类静态方法。

**工作逻辑**：
- `testSetSnapshotSummary` 和 `testNotExposeTableProperties` 不再构造 `HiveTableOperations` 实例并设置 conf，而是定义局部变量 `maxHiveTablePropertySize`，直接调用 `HMSTablePropertyHelper.setSnapshotSummary(...)`、`HMSTablePropertyHelper.setSnapshotStats(...)`、`HMSTablePropertyHelper.setSchema(...)`、`HMSTablePropertyHelper.setPartitionSpec(...)`、`HMSTablePropertyHelper.setSortOrder(...)`，将 `maxHiveTablePropertySize` 作为参数传入。

## 总结

本提交将 Hive 表与视图操作中重复的 HMS 参数设置逻辑抽取为独立的 `HMSTablePropertyHelper` 工具类，消除代码重复、提升可维护性。工具类采用无状态静态方法设计，将原本依赖实例配置（`maxHiveTablePropertySize`）的参数显式化传入。表与视图的差异化逻辑（storage handler、统计、快照、分区、排序）保留在各自的入口方法中，公共逻辑由 `setCommonParameters` 共享。需要注意 `HiveTableOperations.translateToIcebergProp` 静态方法被移除，`ICEBERG_TO_HMS_TRANSLATION` 由 `BiMap` 改为单向 `Map`。
