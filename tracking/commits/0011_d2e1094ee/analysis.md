# 提交 0011：API, Core: Allow setting a View's location (#8648)

## 提交信息

- **序号**：0011 / 4088
- **哈希**：d2e1094ee0cc6239d43f63ba5114272f59d605d2
- **短哈希**：d2e1094ee
- **日期**：2023-10-04 11:17:34 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：API, Core: Allow setting a View's location (#8648)
- **PR/Issue**：#8648

## 总体目的

这个提交为 Iceberg 视图（View）引入了"设置与修改存储位置（location）"的能力。在 Iceberg 1.4 中，视图（View）作为一项较新的功能被引入，用于存储可复用的 SQL 查询定义。与表（Table）类似，视图也拥有一个 base location（基础存储位置），用于存放视图的元数据文件（view metadata files）。然而在本提交之前，视图的位置只能在创建时由 catalog 根据默认仓库路径（`defaultWarehouseLocation`）自动推断，既无法在创建/替换视图时显式指定自定义位置，也无法对已存在的视图单独修改其位置。

这一点与表的能力存在明显差距。表早就通过 `UpdateLocation` 接口支持设置 location，并提供了 `updateLocation()` 入口。而视图此前完全没有对应能力，这导致用户在需要将视图元数据存放在特定路径（例如自定义存储布局、与外部系统协作、迁移存储）时无能为力。本提交补齐了这一缺口，使视图与表在 location 管理上能力对齐，是视图功能走向成熟的必要一步。

具体而言，本提交实现了三件事：第一，在创建视图（`buildView`）或替换视图版本（`replace`）时，可以通过 `ViewBuilder.withLocation(...)` 显式指定 location，覆盖默认的仓库路径推断；第二，对已存在的视图，可以通过 `view.updateLocation().setLocation(...).commit()` 单独更新 location 而不产生新的视图版本；第三，新增 `View.location()` 读取方法，使视图的位置可被查询。这些能力使视图的存储管理具备与表一致的灵活性。

## 如何达成设计目的

整体设计思路是复用已有的 `UpdateLocation` 接口（原本只服务于表），将其语义扩展为"表或视图的 location"，并在视图侧新增对应的实现与入口。改动分布在 API 层（接口契约扩展）与 Core 层（实现与测试）两部分：API 层修改 `UpdateLocation` 的 Javadoc 以反映其现在同时适用于表和视图，并在 `View`、`ViewBuilder` 接口上新增 `location()`、`updateLocation()`、`withLocation()` 三个 default 方法（默认抛 `UnsupportedOperationException` 以保证向后兼容）；Core 层则在 `BaseView`、`BaseMetastoreViewCatalog` 中实现这些方法，并新增 `SetViewLocation` 类作为 `UpdateLocation` 的视图实现，最后在 `ViewCatalogTests` 中补充覆盖创建/替换/更新/冲突场景的测试。

## 修改详情

### `api/src/main/java/org/apache/iceberg/UpdateLocation.java`

**修改目的**：将 `UpdateLocation` 接口的语义从"仅表"扩展为"表或视图"。

**工作逻辑**：仅修改 Javadoc 文案：类注释由 "API for setting a table's base location." 改为 "API for setting a table's or view's base location."；方法 `setLocation` 的注释由 "Set the table's location." 改为 "Set the table's or view's location."。接口签名未变，这是复用既有接口而非新增接口的设计选择，避免为视图单独引入一套平行 API。

### `api/src/main/java/org/apache/iceberg/view/View.java`

**修改目的**：在 `View` 接口上新增 location 的读取与更新入口。

**工作逻辑**：
1. 新增 `import org.apache.iceberg.UpdateLocation`。
2. 新增 default 方法 `location()`，默认抛 `UnsupportedOperationException("Retrieving a view's location is not supported")`，返回视图的 base location。用 default 方法保证旧实现不受破坏。
3. 新增 default 方法 `updateLocation()`，返回 `UpdateLocation` 实例，默认抛 `UnsupportedOperationException`。这与已有的 `updateProperties()`、`replaceVersion()` 风格一致，让支持该能力的 catalog 实现可覆写提供真正实现。

### `api/src/main/java/org/apache/iceberg/view/ViewBuilder.java`

**修改目的**：在 `ViewBuilder` 上新增 `withLocation(String)` 方法，支持创建/替换视图时显式指定 location。

**工作逻辑**：新增 default 方法 `withLocation(String location)`，默认抛 `UnsupportedOperationException("Setting a view's location is not supported")`。采用 default 方法设计，确保现有的 `ViewBuilder` 实现不会被破坏，由具体 catalog 实现选择是否支持。

### `core/src/main/java/org/apache/iceberg/view/BaseMetastoreViewCatalog.java`

**修改目的**：在 `BaseViewBuilder`（`BaseMetastoreViewCatalog` 的内部 Builder）中实现 `withLocation`，并在 create/replace 流程中应用自定义 location。

**工作逻辑**：
1. 新增字段 `private String location = null;`。
2. 实现 `withLocation(String newLocation)`：赋值并返回 `this`。
3. 在 `create()` 中构建 `ViewMetadata` 时，将原来的 `.setLocation(defaultWarehouseLocation(identifier))` 改为 `.setLocation(null != location ? location : defaultWarehouseLocation(identifier))`，即优先使用用户指定的 location，否则回退到默认仓库路径推断。
4. 在 `replace()` 中将原本一次性 `build()` 改为先构造 `ViewMetadata.Builder`，再在 `null != location` 时调用 `builder.setLocation(location)`，最后 `build()`。这样替换视图时若指定了新 location 也会被应用；未指定则保留原 location（因为 `buildFrom(metadata)` 已继承原 location）。注意 create 时一定会有 location（默认或自定义），而 replace 时只有显式指定才会变更。

### `core/src/main/java/org/apache/iceberg/view/BaseView.java`

**修改目的**：在 `BaseView` 中实现 `View` 接口新增的 `location()` 与 `updateLocation()`。

**工作逻辑**：
1. `location()` 直接委托 `operations().current().location()`，即从当前视图元数据读取 location。
2. `updateLocation()` 返回 `new SetViewLocation(ops)`，把视图操作句柄传给新的实现类。

### `core/src/main/java/org/apache/iceberg/view/SetViewLocation.java`（新增文件）

**修改目的**：提供 `UpdateLocation` 接口的视图实现，支持对已存在视图单独修改 location。

**工作逻辑**：新增 `SetViewLocation implements UpdateLocation` 类（包级可见），核心逻辑：
1. 持有 `ViewOperations ops` 与待设置的位置 `newLocation`。
2. `apply()` 校验 `newLocation` 非空（否则抛 `IllegalStateException("Invalid view location: null")`），返回该位置。
3. `setLocation(String)` 赋值并返回 `this`（方法链）。
4. `commit()` 先 `ops.refresh()` 获取最新元数据基线，然后用 `Tasks.foreach(ops)` 配合指数退避重试机制（复用 `TableProperties` 中的 `COMMIT_NUM_RETRIES`、`COMMIT_MIN/MAX_RETRY_WAIT_MS`、`COMMIT_TOTAL_RETRY_TIME_MS` 等配置，与表提交重试策略一致），仅在 `CommitFailedException` 时重试，最终通过 `ViewMetadata.buildFrom(base).setLocation(apply()).build()` 构造新元数据并提交。重试机制保证并发冲突下的正确性，与表的 `SetLocation` 实现风格对齐。

### `core/src/test/java/org/apache/iceberg/view/ViewCatalogTests.java`

**修改目的**：补充覆盖视图 location 设置与更新能力的测试。

**工作逻辑**：
1. 在既有 `createAndReplaceViewVersion` 测试中追加 `.withLocation("file://tmp/ns/view")` 并断言 `view.location()` 返回该值，验证创建时指定 location 生效。
2. 新增 `createAndReplaceViewWithLocation`：先以 location `file://tmp/ns/view` 创建视图，再以 `file://updated_tmp/ns/view` replace，断言 replace 后 location 已更新。
3. 新增 `updateViewLocation`：创建视图后通过 `view.updateLocation().setLocation(...).commit()` 单独更新 location，重新 load 后验证 location 已变更，且 history/versions 不变（即更新 location 不产生新版本），验证隔离性。
4. 新增 `updateViewLocationConflict`：验证两种失败场景——`setLocation(null)` 提交时抛 `IllegalStateException("Invalid view location: null")`；在并发 drop 视图后再 commit 更新，抛 `CommitFailedException("Cannot commit")`，验证冲突处理。

## 小结

本提交通过复用 `UpdateLocation` 接口并在视图 API/Core 层补齐 `withLocation`、`location()`、`updateLocation()` 及 `SetViewLocation` 实现，使 Iceberg 视图具备与表一致的 location 显式设置与运行时修改能力，补全了视图存储管理的关键一环。
