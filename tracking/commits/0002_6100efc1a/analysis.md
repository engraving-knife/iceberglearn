# 提交 0002：Core: Add remaining View APIs and support for InMemoryCatalog (#7880)

## 提交信息

- **序号**：0002 / 4088
- **哈希**：6100efc1a764b204e5a879e0ae6d535a71301249
- **短哈希**：6100efc1a
- **日期**：2023-09-28 15:24:07 -0700
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Add remaining View APIs and support for InMemoryCatalog (#7880)
- **PR/Issue**：#7880

## 总体目的

这个提交是 Iceberg 视图（View）功能的核心补全提交。在此之前，Iceberg 已经引入了视图元数据模型（`ViewMetadata`、`ViewVersion`、`ViewRepresentation` 等）以及 `ViewCatalog`/`View` 等接口定义，但缺少这些接口的实现类与 SPI 抽象，也缺少一个开箱即用的内存 Catalog 实现，使得视图功能只能停留在元数据层，无法真正被使用、测试或被下游引擎对接。本提交通过补全"剩余的 View API"以及让 `InMemoryCatalog` 支持 View，把视图能力从"模型层"推进到"可操作的 Catalog 层"。

具体来说，本提交引入了三组能力。第一组是视图操作的 SPI 与基类：新增 [ViewOperations.java](file:///Users/fengxiaohang/trae/iceberglearn/core/src/main/java/org/apache/iceberg/view/ViewOperations.java) 接口（抽象视图元数据的访问与提交），以及 [BaseViewOperations.java](file:///Users/fengxiaohang/trae/iceberglearn/core/src/main/java/org/apache/iceberg/view/BaseViewOperations.java) 抽象类，提供与 `TableOperations`/`BaseMetastoreTableOperations` 类似的 refresh/commit/写元数据文件/解析版本号等通用逻辑，供具体 Catalog 复用。第二组是视图实例与更新操作：新增 [BaseView.java](file:///Users/fengxiaohang/trae/iceberglearn/core/src/main/java/org/apache/iceberg/view/BaseView.java) 作为 `View` 接口的基类实现，新增 [PropertiesUpdate.java](file:///Users/fengxiaohang/trae/iceberglearn/core/src/main/java/org/apache/iceberg/view/PropertiesUpdate.java) 实现 `UpdateViewProperties`，新增 [ViewVersionReplace.java](file:///Users/fengxiaohang/trae/iceberglearn/core/src/main/java/org/apache/iceberg/view/ViewVersionReplace.java) 实现 `ReplaceViewVersion`。第三组是 Catalog 适配：新增 [BaseMetastoreViewCatalog.java](file:///Users/fengxiaohang/trae/iceberglearn/core/src/main/java/org/apache/iceberg/view/BaseMetastoreViewCatalog.java) 提供 `loadView`/`buildView`/`ViewBuilder` 实现，让基于 metastore 风格的 Catalog 只需实现 `newViewOps` 即可获得完整视图创建/替换能力；同时让 [InMemoryCatalog.java](file:///Users/fengxiaohang/trae/iceberglearn/core/src/main/java/org/apache/iceberg/inmemory/InMemoryCatalog.java) 改为继承 `BaseMetastoreViewCatalog`，新增 `views` 表、`listViews`/`dropView`/`renameView` 与 `InMemoryViewOperations`，从而具备完整的视图存取能力。

此外，本提交还对 `ViewMetadata.Builder` 增加了一个保护性校验：禁止同一视图版本内出现同 dialect 的多条 SQL 表示（dialect 唯一性）。这是为了在 Builder 层把"一个版本内同一 dialect 只能有一条 SQL"这一语义约束固化下来。配套测试新增了 [ViewCatalogTests.java](file:///Users/fengxiaohang/trae/iceberglearn/core/src/test/java/org/apache/iceberg/view/ViewCatalogTests.java)（1340 行抽象测试基类，覆盖创建/替换/重命名/列举/冲突/属性更新等场景）与 [TestInMemoryViewCatalog.java](file:///Users/fengxiaohang/trae/iceberglearn/core/src/test/java/org/apache/iceberg/inmemory/TestInMemoryViewCatalog.java)，使任何 `ViewCatalog` 实现都能复用同一套测试套件。

总体上，这个提交使 Iceberg 的视图功能从"元数据模型"演进为"可创建、可查询、可更新、可在 Catalog 间迁移"的完整能力，是视图功能走向生产可用的关键一步。

## 如何达成设计目的

整体设计思路是"对称复用表（Table）已有的 Catalog/Operations/View 基类结构"。表的 SPI 是 `TableOperations` + `BaseMetastoreTableOperations` + `BaseMetastoreCatalog` 中的 `TableBuilder`；本提交为视图对称地引入 `ViewOperations` + `BaseViewOperations` + `BaseMetastoreViewCatalog` 中的 `BaseViewBuilder`。`BaseViewOperations` 复用了表那一套的元数据文件命名（`00000-uuid.metadata.json`）、版本号解析、基于 `Tasks` 重试的 metadata 加载与 commit 的"base == current"乐观并发检查；`BaseViewBuilder`/`ViewVersionReplace`/`PropertiesUpdate` 通过 `ViewMetadata.buildFrom(base)` + `ops.commit(base, new)` 的模式实现变更。`InMemoryCatalog` 通过继承 `BaseMetastoreViewCatalog` 并实现 `newViewOps` 返回内部类 `InMemoryViewOperations`，复用全部通用逻辑，仅需在 `doCommit` 里用 `views.compute(...)` 做并发安全的位置更新。dialect 唯一性校验则插在 `ViewMetadata.Builder` 的 `addVersionInternal` 中，作为最后一道防线。

## 修改详情

### `core/src/main/java/org/apache/iceberg/view/ViewOperations.java`（新增）

**修改目的**：定义视图元数据访问与提交的 SPI 接口。

**工作逻辑**：接口声明三个方法：`current()`（返回当前已加载元数据，不主动 refresh）、`refresh()`（检查更新后返回最新元数据）、`commit(ViewMetadata base, ViewMetadata metadata)`（用新元数据替换基础元数据）。`commit` 的 Javadoc 明确要求实现做 base == current 的乐观检查、提供原子性保证，并在不确定提交是否成功时抛 `CommitStateUnknownException`，与 `TableOperations.commit` 语义完全对齐，便于复用既有的提交冲突处理与清理逻辑。

### `core/src/main/java/org/apache/iceberg/view/BaseViewOperations.java`（新增）

**修改目的**：提供 `ViewOperations` 的通用基类，封装元数据文件 IO、版本解析、refresh/commit 流程。

**工作逻辑**：
- 持有 `currentMetadata`、`currentMetadataLocation`、`shouldRefresh`、`version` 四个状态字段，由 `requestRefresh`/`disableRefresh` 控制。
- `current()` 在 `shouldRefresh` 为 true 时调用 `refresh()`；`refresh()` 调用抽象 `doRefresh()`，捕获 `NoSuchViewException` 时清空状态并重新抛出（如果之前有元数据则记 warn 日志）。
- `commit(base, metadata)`：先做 `base != current()` 的乐观检查，base 为 null 而当前非 null 时抛 `AlreadyExistsException`（视图已存在），base 与 metadata 是同一对象时直接 return（nothing to commit）；否则调用抽象 `doCommit(base, metadata)` 并 `requestRefresh()`。
- `writeNewMetadata`/`writeNewMetadataIfRequired`：若 metadata 已带 `metadataFileLocation` 则直接复用（用于保留原位置），否则按 `00000-uuid.metadata.json` 命名写到 `<location>/metadata/` 下，用 `ViewMetadataParser.overwrite` 覆写以规避 S3 负缓存。
- `refreshFromMetadataLocation`：通过 `Tasks` 做指数退避重试加载 metadata 文件（默认 20 次），可传入自定义 `shouldRetry` 与 `metadataLoader`，加载成功后更新 `currentMetadata`/`currentMetadataLocation`/`version`，并 `disableRefresh()`。
- `parseVersion`：从 metadata 文件名解析版本号（`00001-uuid.metadata.json` → 1），无法解析返回 -1。

### `core/src/main/java/org/apache/iceberg/view/BaseView.java`（新增）

**修改目的**：提供 `View` 接口的基类实现，把对元数据的访问委托给 `ViewOperations`。

**工作逻辑**：持有 `ViewOperations ops` 与 `String name`；`schema()`/`schemas()`/`currentVersion()`/`versions()`/`version(int)`/`history()`/`properties()` 全部通过 `operations().current().xxx()` 转发；`updateProperties()` 返回 `new PropertiesUpdate(ops)`，`replaceVersion()` 返回 `new ViewVersionReplace(ops)`。

### `core/src/main/java/org/apache/iceberg/view/PropertiesUpdate.java`（新增）

**修改目的**：实现 `UpdateViewProperties`，提供视图属性的 set/remove/commit 能力。

**工作逻辑**：维护 `updates` Map 与 `removals` Set，构造时缓存 `base = ops.current()`。`set`/`remove` 互斥检查（同一 key 不能同时出现在两个集合中）。`internalApply()` 先 `ops.refresh()` 取最新 base，再 `ViewMetadata.buildFrom(base).setProperties(updates).removeProperties(removals).build()` 生成新元数据。`commit()` 用 `Tasks.foreach(ops).retry(...).exponentialBackoff(...).onlyRetryOn(CommitFailedException.class).run(...)` 做乐观重试，重试参数从 base 的 properties 读取（复用 `TableProperties.COMMIT_*` 配置）。

### `core/src/main/java/org/apache/iceberg/view/ViewVersionReplace.java`（新增）

**修改目的**：实现 `ReplaceViewVersion`，提供视图版本替换（replace version）能力。

**工作逻辑**：维护新的 `representations`/`schema`/`defaultNamespace`/`defaultCatalog`。`internalApply()` 校验 representations/schema/defaultNamespace 非空，`ops.refresh()` 取最新 base，计算 `maxVersionId + 1` 作为新版本 ID，构造 `ViewVersion`（summary 标 `operation=replace`），通过 `ViewMetadata.buildFrom(base).setCurrentVersion(newVersion, schema).build()` 生成新元数据。`commit()` 与 `PropertiesUpdate` 一样的乐观重试。`withQuery`/`withSchema`/`withDefaultCatalog`/`withDefaultNamespace` 为链式配置方法。

### `core/src/main/java/org/apache/iceberg/view/ViewUtil.java`（新增）

**修改目的**：提供视图相关工具方法。

**工作逻辑**：仅一个静态方法 `fullViewName(String catalog, TableIdentifier ident)`，返回 `catalog + "." + ident`，用于构造视图的全限定名（如 `my-catalog.ns.view`），供日志、异常信息与 `BaseView` 的 `name()` 使用。

### `core/src/main/java/org/apache/iceberg/view/BaseMetastoreViewCatalog.java`（新增）

**修改目的**：为基于 metastore 的 Catalog 提供视图能力的基类，对称于 `BaseMetastoreCatalog` 对表的能力。

**工作逻辑**：
- 继承 `BaseMetastoreCatalog` 并实现 `ViewCatalog`，抽象方法 `newViewOps(TableIdentifier)` 由子类提供。
- `loadView`：校验 identifier，取 `newViewOps(identifier).current()`，为 null 抛 `NoSuchViewException`，否则返回 `new BaseView(newViewOps(identifier), ViewUtil.fullViewName(name(), identifier))`（注意重新 new 一次 ops，保证返回的 View 持有独立 ops 实例）。
- `buildView` 返回内部类 `BaseViewBuilder`。`BaseViewBuilder` 持有 identifier/properties/representations/defaultNamespace/defaultCatalog/schema，提供 `withSchema`/`withQuery`/`withDefaultCatalog`/`withDefaultNamespace`/`withProperties`/`withProperty` 链式方法。
- `create()`/`replace()`/`createOrReplace()` 分别走 `create(ops)`/`replace(ops)`/根据 `ops.current() == null` 二选一。
- `create(ops)`：若 `ops.current()` 非 null 抛 `AlreadyExistsException`；校验 representations/schema/defaultNamespace 非空；构造 `ViewVersion`（versionId=1，summary operation=create），通过 `ViewMetadata.builder().setProperties(properties).setLocation(defaultWarehouseLocation(identifier)).setCurrentVersion(viewVersion, schema).build()` 创建元数据；`ops.commit(null, viewMetadata)` 失败时把 `CommitFailedException` 转成 `AlreadyExistsException`（并发创建）。
- `replace(ops)`：若 `ops.current()` 为 null 抛 `NoSuchViewException`；从 metadata 取 `maxVersionId + 1` 作为新版本 ID；通过 `ViewMetadata.buildFrom(metadata).setProperties(properties).setCurrentVersion(viewVersion, schema).build()` 生成新元数据；`ops.commit(metadata, replacement)`。

### `core/src/main/java/org/apache/iceberg/view/ViewMetadata.java`（修改）

**修改目的**：在 `Builder.addVersionInternal` 中增加 dialect 唯一性校验。

**工作逻辑**：在已有的"版本 schemaId 必须已知"校验之后，新增一段循环：遍历版本的 `representations()`，对每个 `SQLViewRepresentation` 用 `dialects.add(sql.dialect())` 检查是否重复（`HashSet.add` 返回 false 即重复），重复则抛 `IllegalArgumentException("Invalid view version: Cannot add multiple queries for dialect %s")`。这把"一个版本内同一 dialect 只能有一条 SQL"的约束在元数据构建时强制执行。

### `core/src/main/java/org/apache/iceberg/inmemory/InMemoryCatalog.java`（修改）

**修改目的**：让 `InMemoryCatalog` 支持视图存取，并修复并发安全性问题。

**工作逻辑**：
- 改为继承 `BaseMetastoreViewCatalog`（原继承 `BaseMetastoreCatalog`），新增 `views` ConcurrentMap（TableIdentifier → metadata location）。
- 新增 `listViews(namespace)`：过滤 `views.keySet()`，按字符串排序返回。
- 新增 `dropView(identifier)`：`synchronized(this)` 后 `views.remove(identifier) != null`。
- 新增 `renameView(from, to)`：在 `synchronized(this)` 块中校验目标 namespace 存在、源存在、目标非表非视图，然后 `views.put(to, fromLocation)` + `views.remove(from)`。
- 新增内部类 `InMemoryViewOperations extends BaseViewOperations`：`doRefresh()` 从 `views.get(identifier)` 取最新 location 调 `refreshFromMetadataLocation`，缺失则 `disableRefresh()`；`doCommit` 用 `writeNewMetadataIfRequired(metadata)` 写新文件，在 `synchronized(InMemoryCatalog.this)` 块中校验 namespace 存在、与同名表冲突，再用 `views.compute(...)` 做 base/oldLocation 一致性检查并更新为新 location（CAS 风格），冲突时抛 `AlreadyExistsException` 或 `CommitFailedException`。
- `InMemoryTableOperations.doCommit`：改用 `writeNewMetadataIfRequired(base == null, metadata)`（替代原直接 `writeNewMetadata(metadata, currentVersion() + 1)`），并在 synchronized 块中额外检查 `views.containsKey(tableIdentifier)` 抛 `AlreadyExistsException`（视图同名冲突）。`tableName()` 由 `tableIdentifier.toString()` 改为 `fullTableName(catalogName, tableIdentifier)`，与视图命名风格统一。
- 多处方法（`renameTable`/`createNamespace`/`dropNamespace`/`setProperties`/`removeProperties`/`dropTable`）把原方法级 `synchronized` 改为方法内 `synchronized(this)` 块，以便 `InMemoryViewOperations.doCommit` 能用 `synchronized(InMemoryCatalog.this)` 与这些方法互斥，避免表/视图/namespace 操作之间的并发竞争。

### `core/src/test/java/org/apache/iceberg/view/ViewCatalogTests.java`（新增）

**修改目的**：为 `ViewCatalog` 提供一套抽象测试基类，覆盖视图 CRUD、重命名、列举、属性更新、版本替换、冲突等场景。

**工作逻辑**：抽象类 `ViewCatalogTests<C extends ViewCatalog & SupportsNamespaces>` 声明 `catalog()`/`tableCatalog()`/`requiresNamespaceCreate()` 抽象方法。包含 30+ 个测试用例，主要包括：`basicCreateView`/`completeCreateView` 验证创建的视图属性、版本、schema；`createViewErrorCases`/`createViewThatAlreadyExists`/`createViewThatAlreadyExistsAsTable`/`createTableThatAlreadyExistsAsView` 验证表/视图同名冲突；`renameView*` 系列覆盖重命名的各种边界；`listViews`/`listViewsAndTables` 验证列举；`createOrReplaceView`（参数化）验证 createOrReplace 行为；`replaceViewErrorCases`/`replaceViewVersion*`/`updateViewProperties*` 验证更新操作；`*Conflict` 系列验证并发冲突场景（模拟 base 失效）。

### `core/src/test/java/org/apache/iceberg/inmemory/TestInMemoryViewCatalog.java`（新增）

**修改目的**：让 `InMemoryCatalog` 接入 `ViewCatalogTests` 套件。

**工作逻辑**：`@BeforeEach` 创建并初始化 `InMemoryCatalog`，重写 `catalog()`/`tableCatalog()` 返回同一实例，`requiresNamespaceCreate()` 返回 true（InMemoryCatalog 要求 namespace 先创建）。

### `core/src/test/java/org/apache/iceberg/view/TestViewMetadata.java`（修改）

**修改目的**：为 dialect 唯一性校验新增测试。

**工作逻辑**：新增 `viewMetadataWithMultipleSQLForSameDialect`，构造一个版本内含两条 dialect=spark 的 SQL 表示，断言 `ViewMetadata.builder().build()` 抛 `IllegalArgumentException` 且消息为 "Invalid view version: Cannot add multiple queries for dialect spark"。

### `core/src/test/java/org/apache/iceberg/view/TestViewMetadataParser.java`（修改）

**修改目的**：验证 dialect 重复的视图元数据可被读取（向后兼容），并可通过 replace 修复。

**工作逻辑**：
- `viewMetadataWithMultipleSQLsForDialectShouldBeReadable`：读取 `ViewMetadataMultipleSQLsForDialect.json`（含两条 spark-sql SQL），断言 `ViewMetadataParser.fromJson` 不抛异常，且解析结果与用 `ImmutableViewMetadata.of(...)` 直接构造的期望对象一致（递归比较、忽略 Schema 类型差异）。说明解析器对历史"脏"数据宽容，不强制 dialect 唯一。
- `replaceViewMetadataWithMultipleSQLsForDialect`：读取脏 JSON 后，用 `ViewMetadata.buildFrom(invalid).addVersion(viewVersion).setCurrentVersionId(2).build()` 替换为新版本（单条 SQL），断言 `replaced.currentVersion()` 等于新版本。说明即使历史版本 dialect 重复，仍可通过 replace 走出困境。

### `core/src/test/resources/org/apache/iceberg/view/ViewMetadataMultipleSQLsForDialect.json`（新增）

**修改目的**：提供含 dialect 重复 SQL 的视图元数据 JSON 测试资源。

**工作逻辑**：标准视图元数据 JSON，唯一特殊之处是 versions[0].representations 数组中有两条 `dialect=spark-sql` 的 SQL 表示，用于测试解析器对 dialect 重复的兼容性。

## 小结

本提交通过对称复用表侧的 Operations/Catalog/Builder 体系，补全了视图的 SPI、基类与更新操作实现，并让 `InMemoryCatalog` 完整支持视图存取，使 Iceberg 视图功能从元数据模型升级为可创建、可更新、可被引擎对接的完整 Catalog 能力。
