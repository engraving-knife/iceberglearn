# 提交 0628：Hive: Extract common code to be re-used for View support

## 提交信息

- **序号**：0628 / 4088
- **哈希**：817a5e1be1616af77329965ac3742c14ca3ae116
- **短哈希**：817a5e1be
- **日期**：2024-03-26 12:19:22 +0530
- **作者**：Naveen Kumar <nk1506@gmail.com>
- **提交说明**：Hive: Extract common code to be re-used for View support (#10001)
- **PR/Issue**：#10001

## 总体目的

本提交是一次**面向 View（视图）支持的前置重构**。其目标是从现有的表（Table）操作体系中抽取与"表/视图"无关的公共逻辑，提升到更上层的基类或工具类中，使得后续实现 Hive 视图（Hive View）支持时可以复用这些代码，避免重复实现。

**背景动机**：

1. Iceberg 正在推进 View（视图）能力（`ViewOperations`、`BaseViewOperations` 等接口和基类已存在）。表和视图在元数据存储（metastore）层面有很多相似的提交语义：都需要检查提交状态（commit status check）、都需要处理元数据位置、都需要与 HMS（Hive Metastore）交互。
2. 此前，提交状态检查逻辑（`checkCommitStatus`）和 `CommitStatus` 枚举仅存在于 `BaseMetastoreTableOperations` 中，是表专属的。视图的 `BaseViewOperations` 无法复用，会导致代码重复。
3. Hive 模块中，`HiveTableOperations` 的 `loadHmsTable`、`setSchema`、`storageDescriptor`、`cleanupMetadataAndUnlock` 等方法都强依赖 `TableMetadata`，但视图使用 `ViewMetadata`，无法直接复用。需要将这些方法改造为接受更基础的参数（如 `Schema`、`location`），使表和视图都能调用。
4. `BaseMetastoreCatalog.fullTableName` 方法是 protected static 的，无法在非 Catalog 上下文（如视图操作）中复用，需要提升到 `CatalogUtil` 公共工具类。

本提交本身**不实现 View 功能**（`validateTableIsIcebergTableOrView` 中 VIEW 分支明确抛出 `UnsupportedOperationException("View is not supported.")`），而是为后续 View 支持铺平道路。

## 如何达成设计目的

整体设计思路是**自顶向下的层次抽取**，将公共能力从表专属层上移到通用层：

```
重构前:
  BaseMetastoreTableOperations (含 CommitStatus + checkCommitStatus)
        ^
        |--- HiveTableOperations
  BaseViewOperations (无 commit status 能力)
  BaseMetastoreCatalog.fullTableName (protected static, 仅 catalog 内可用)

重构后:
  BaseMetastoreOperations (新增: CommitStatus + checkCommitStatus, 表/视图通用)
        ^
        |--- BaseMetastoreTableOperations (继承, 旧 CommitStatus 标记 @Deprecated)
        |--- BaseViewOperations (继承, 获得 commit status 能力)
  CatalogUtil.fullTableName (public static, 全局可用)
```

在 Hive 模块中，通过引入 `ContentType` 枚举（TABLE/VIEW）和新增接受 `Schema`/`location` 等基础参数的方法重载，使 `HiveOperationsBase` 接口同时服务于表和视图，而 `HiveCatalog.renameTable` 被重构为调用通用的 `renameTableOrView`，为后续 `renameView` 留出扩展点。

关键设计决策：
- **保持向后兼容**：旧的 `CommitStatus` 枚举和旧签名方法均标记 `@Deprecated`（注明 since 1.6.0, will be removed in 1.7.0），保留原有行为，不破坏现有调用方。
- **委托而非复制**：`BaseMetastoreTableOperations.checkCommitStatus` 委托给父类的新方法，通过 `Supplier<Boolean>` 传入表特有的元数据位置校验逻辑。
- **枚举桥接**：由于旧 `CommitStatus`（表专属）和新 `CommitStatus`（基类）是两个不同的枚举类型，通过 `CommitStatus.valueOf(...name())` 做字符串桥接转换。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseMetastoreOperations.java`（新增文件）

**修改目的**：新建表/视图通用的基类，承载提交状态检查的公共逻辑。

**工作逻辑**：新增抽象类 `BaseMetastoreOperations`，包含：
- `CommitStatus` 枚举：`FAILURE`、`SUCCESS`、`UNKNOWN`，表示提交的最终状态。
- `checkCommitStatus(String tableOrViewName, String newMetadataLocation, Map<String,String> properties, Supplier<Boolean> commitStatusSupplier)` 方法：这是核心的提交状态检查逻辑，从原 `BaseMetastoreTableOperations` 提取而来，关键改造是：
  - 参数名从 `tableName` 泛化为 `tableOrViewName`，适用于表和视图。
  - 新增 `Supplier<Boolean> commitStatusSupplier` 参数：将"如何判断提交是否成功"这一表/视图差异化的逻辑抽象为回调。调用方通过 supplier 提供具体的判断逻辑（表是检查元数据位置是否在当前或历史记录中，视图未来会有自己的实现）。
  - 从 `properties`（而非 `TableMetadata`）读取重试参数（`COMMIT_NUM_STATUS_CHECKS` 等），使方法不依赖 `TableMetadata` 类型。
  - 内部使用 `Tasks.foreach(...).retry(maxAttempts).exponentialBackoff(...)` 做指数退避重试，调用 `commitStatusSupplier.get()` 判断提交是否成功，成功则设置 `CommitStatus.SUCCESS`，否则保持 `UNKNOWN`。
  - 若重试后仍为 `UNKNOWN`，记录错误日志并返回 `UNKNOWN`。

这一抽象使得表和视图可以共享完全相同的重试与状态判断框架，只需提供各自的"成功判定"supplier。

### `core/src/main/java/org/apache/iceberg/BaseMetastoreTableOperations.java`

**修改目的**：改为继承 `BaseMetastoreOperations`，复用父类的提交状态检查逻辑，同时保持向后兼容。

**工作逻辑**：
- 类声明从 `public abstract class BaseMetastoreTableOperations implements TableOperations` 改为 `extends BaseMetastoreOperations implements TableOperations`。
- 删除了大量原本内联的 import（`COMMIT_*` 相关常量、`PropertyUtil`），因为这些逻辑已上移到父类。
- 原有的 `CommitStatus` 枚举标记 `@Deprecated`（since 1.6.0, will be removed in 1.7.0），保留以兼容现有子类引用。
- `checkCommitStatus(String newMetadataLocation, TableMetadata config)` 方法体重写为委托调用：
  ```java
  return CommitStatus.valueOf(
      checkCommitStatus(
          tableName(),
          newMetadataLocation,
          config.properties(),
          () -> checkCurrentMetadataLocation(newMetadataLocation))
          .name());
  ```
  即调用父类的通用 `checkCommitStatus`，传入表特有的 `checkCurrentMetadataLocation` 作为 supplier，再将父类返回的 `BaseMetastoreOperations.CommitStatus` 通过 `valueOf(...name())` 转换回旧的 `CommitStatus` 类型返回。
- 新增 private 方法 `checkCurrentMetadataLocation(String newMetadataLocation)`：执行 `refresh()` 获取最新元数据，判断 `newMetadataLocation` 是否等于当前元数据位置或存在于历史元数据文件列表中。这正是原内联逻辑的核心，现在被抽为 supplier 供父类调用。

### `core/src/main/java/org/apache/iceberg/BaseViewOperations.java`

**修改目的**：让视图操作基类继承 `BaseMetastoreOperations`，使其具备提交状态检查能力。

**工作逻辑**：类声明从 `public abstract class BaseViewOperations implements ViewOperations` 改为 `extends BaseMetastoreOperations implements ViewOperations`。新增 import `BaseMetastoreOperations`。本提交未在 `BaseViewOperations` 中实际调用 `checkCommitStatus`，但继承关系已建立，后续 View 提交实现可直接复用父类方法。

### `core/src/main/java/org/apache/iceberg/CatalogUtil.java`

**修改目的**：将 `fullTableName` 方法从 `BaseMetastoreCatalog` 提升为 `CatalogUtil` 的 public static 方法，使其可在任意上下文（包括视图操作、Hive 操作）中复用。

**工作逻辑**：新增 `public static String fullTableName(String catalogName, TableIdentifier identifier)` 方法，逻辑与原 `BaseMetastoreCatalog.fullTableName` 完全一致：
- 若 catalogName 含 `/` 或 `:`（URI 风格，如 `thrift://host:port`），用 `/` 分隔：`thrift://host:port/db.table`。
- 否则（普通名称），用 `.` 分隔：`prod.db.table`。
- 遍历 namespace 各层级用 `.` 拼接，最后追加表名。

新增 import `org.apache.iceberg.catalog.TableIdentifier`。

### `core/src/main/java/org/apache/iceberg/BaseMetastoreCatalog.java`

**修改目的**：`fullTableName` 方法改为委托 `CatalogUtil.fullTableName`，消除重复实现。

**工作逻辑**：原 20 行的 `fullTableName` 方法体替换为一行 `return CatalogUtil.fullTableName(catalogName, identifier);`，保持方法签名和返回值不变。

### `core/src/test/java/org/apache/iceberg/TestCatalogUtil.java`

**修改目的**：为提升到 `CatalogUtil` 的 `fullTableName` 新增单元测试，覆盖各种 catalog 名称格式。

**工作逻辑**：新增 `fullTableNameWithDifferentValues` 测试方法，验证：
- URI 风格 catalog 名（`thrift://host:port/db.table`）+ 单层 namespace：用 `/` 分隔。
- URI 风格 catalog 名 + 两层 namespace：用 `/` 分隔 catalog 与 namespace。
- URI 风格 catalog 名末尾带 `/`：不重复添加 `/`。
- 非 URI catalog 名（`test.db.catalog`）：用 `.` 分隔。
- 路径风格 catalog 名（`/test/db`，含 `/` 但不含 `:`）：用 `/` 分隔。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveOperationsBase.java`

**修改目的**：将 Hive 表操作的公共方法抽取到接口默认方法中，并新增接受基础参数的重载，使视图也能复用。

**工作逻辑**：多项改动：
1. **新增 `ContentType` 枚举**：`TABLE("Table")` 和 `VIEW("View")`，用于区分操作对象类型，在日志和校验中区分表与视图。
2. **新增 `loadHmsTable()` 默认方法**：从 `HiveTableOperations` 上移而来，通过 `metaClients().run(client -> client.getTable(database(), table()))` 加载 HMS 表对象，捕获 `NoSuchObjectException` 返回 null。使用接口的 `database()` 和 `table()` 方法（已存在），使表和视图操作都能加载对应的 HMS 对象。
3. **`setSchema` 拆分**：原 `setSchema(TableMetadata, Map)` 标记 `@Deprecated`，新增 `setSchema(Schema, Map)` 重载。旧方法委托新方法（`setSchema(metadata.schema(), parameters)`）。这样视图可以直接传入 `ViewMetadata.schema()` 而无需 `TableMetadata`。
4. **`storageDescriptor` 拆分**：原 `storageDescriptor(TableMetadata, boolean)` 标记 `@Deprecated`，新增 `storageDescriptor(Schema, String location, boolean hiveEngineEnabled)` 重载。旧方法委托新方法。新方法直接接受 schema 和 location，构建 HMS `StorageDescriptor`（设置列、location、SerDeInfo、InputFormat/OutputFormat），不再依赖 `TableMetadata`。
5. **新增 `cleanupMetadataAndUnlock` 静态方法**：从 `HiveTableOperations` 上移而来，签名改为接受 `BaseMetastoreOperations.CommitStatus`（而非表专属的 `CommitStatus`）。在 finally 中解锁，确保锁释放。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveCatalog.java`

**修改目的**：重构 `renameTable` 为通用的 `renameTableOrView`，为视图重命名留出扩展点；提取 `listIcebergTables` 辅助方法。

**工作逻辑**：
1. **`listTables` 简化**：原内联的"通过 `getTableObjectsByName` 获取表对象并过滤 Iceberg 表"逻辑提取为私有方法 `listIcebergTables(List<String> tableNames, Namespace namespace, String tableTypeProp)`，返回过滤后的 `TableIdentifier` 列表。
2. **`renameTable` 委托**：`renameTable` 方法体改为调用 `renameTableOrView(from, originalTo, HiveOperationsBase.ContentType.TABLE)`。
3. **新增 `renameTableOrView` 私有方法**：承载原 `renameTable` 的全部逻辑，新增 `ContentType` 参数。关键改动：
   - 调用 `validateTableIsIcebergTableOrView(contentType, table, CatalogUtil.fullTableName(name, from))` 替代原 `HiveOperationsBase.validateTableIsIceberg(table, fullTableName(name, from))`（注意改用 `CatalogUtil.fullTableName`）。
   - 日志改为 `LOG.info("Renamed {} from {}, to {}", contentType.value(), from, to)`，日志中体现操作对象类型。
4. **新增 `validateTableIsIcebergTableOrView` 私有方法**：根据 `ContentType` 分发：
   - `TABLE`：调用 `HiveOperationsBase.validateTableIsIceberg(table, fullName)`（原有逻辑）。
   - `VIEW`：抛出 `UnsupportedOperationException("View is not supported.")`——占位实现，等待后续 View 支持补全。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveTableOperations.java`

**修改目的**：适配基类重构，更新类型引用和方法调用。

**工作逻辑**：
1. `CommitStatus` 引用全部改为 `BaseMetastoreOperations.CommitStatus`（如 `BaseMetastoreOperations.CommitStatus.FAILURE`、`.SUCCESS`、`.UNKNOWN`）。
2. `storageDescriptor` 调用改为新签名：`HiveOperationsBase.storageDescriptor(metadata.schema(), metadata.location(), hiveEngineEnabled)`。
3. `setSchema` 调用改为新签名：`setSchema(metadata.schema(), parameters)`。
4. `checkCommitStatus` 返回值通过 `BaseMetastoreOperations.CommitStatus.valueOf(checkCommitStatus(...).name())` 桥接转换（因为父类返回基类 `CommitStatus`，而 switch 需要基类类型）。
5. `cleanupMetadataAndUnlock` 调用改为 `HiveOperationsBase.cleanupMetadataAndUnlock(io(), commitStatus, newMetadataLocation, lock)`（调用上移到接口的静态方法）。
6. 删除了原 `loadHmsTable` 方法（已上移到 `HiveOperationsBase`）和原 `cleanupMetadataAndUnlock` 私有方法（已上移）。

## 小结

**成效**：本提交通过系统性的层次重构，成功将表操作中的公共逻辑（提交状态检查、HMS 表加载、schema 设置、storage descriptor 构建、元数据清理）抽取到通用基类和接口中，为 Hive View 支持奠定了代码基础。重构保持了完全的向后兼容（旧 API 标记 `@Deprecated` 但保留可用），不改变任何现有行为。

**影响范围**：
- `core` 模块：新增 `BaseMetastoreOperations` 类，`BaseMetastoreTableOperations` 和 `BaseViewOperations` 的继承关系变化，`CatalogUtil` 新增公共方法。
- `hive-metastore` 模块：`HiveOperationsBase`、`HiveCatalog`、`HiveTableOperations` 的方法结构和调用关系调整。
- 不涉及行为变更，所有现有测试应继续通过。

**回迁到 1.4.x 的注意事项**：
- 此重构是后续 View 支持的前置铺垫，1.4.x 是否需要回迁取决于是否计划在 1.4.x 上实现 View 支持。若不计划，可暂缓回迁。
- 回迁时需整体回迁所有 9 个文件的改动，不能部分回迁（类继承关系和方法签名有联动依赖）。
- 注意 `@Deprecated` 注解标注的版本号（since 1.6.0, will be removed in 1.7.0）——若 1.4.x 回迁，需评估这些 deprecation 版本号是否需要调整为 1.4.x 对应版本，以免误导用户。
- `BaseViewOperations` 在 1.4.x 上的现有实现需确认与新基类 `BaseMetastoreOperations` 无冲突（如无重复的字段/方法定义）。
- `HiveOperationsBase` 是 interface，新增的 default 方法和 static 方法需要 1.4.x 的 Java 编译版本支持（Java 8+ 即可）。
- 回迁后需运行 Hive 模块和 core 模块的完整测试套件，确保重构未引入回归。
