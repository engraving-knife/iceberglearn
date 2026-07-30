# 提交 1557 f12958846 分析

## 提交信息
- 哈希：f129588461ad02c2fa2021af30b4e9bca70eee93
- 日期：2025-01-07（Tue Jan 7 19:19:22 2025 +0900）
- 作者：Yuya Ebihara <ebyhry@gmail.com>
- 消息：Core: Add support for view-default property in catalog (#11064)

## 总体目的

Iceberg 的 catalog 属性中早已存在 `table-default.<key>` 前缀机制，允许在 catalog 级别为该 catalog 下创建的所有表设置默认表属性（用户在建表时仍可覆盖）。同时还有 `table-override.<key>` 用于强制覆盖（不可被用户覆盖）。这两个机制由 `CatalogProperties.TABLE_DEFAULT_PREFIX` / `TABLE_OVERRIDE_PREFIX` 定义，并在 `BaseMetastoreCatalog` 的 TableBuilder 中通过 `PropertyUtil.propertiesWithPrefix` 提取并应用。

然而对 view 而言，此前没有对应的"catalog 级别默认 view 属性"机制。建 view 时只能由调用方逐个指定属性，无法在 catalog 配置中统一设定所有新建 view 的默认属性。这在需要为所有 view 统一注入某些默认属性（例如历史标签、归属信息、治理属性等）的场景下不够便利，也与 table 的能力不对等。

本提交新增 `view-default.<key>` 前缀机制（常量 `CatalogProperties.VIEW_DEFAULT_PREFIX = "view-default."`），让 catalog 在创建 view 时自动把 catalog 属性中以 `view-default.` 为前缀的所有键值对作为该 view 的默认属性注入。该默认属性可被用户在 `buildView(...).withProperty(k, v)` 时覆盖（因为是先注入默认、再应用用户属性）。本次同时在 `BaseMetastoreViewCatalog` 与 `RESTSessionCatalog` 两个 view builder 路径上落地该能力（REST 的 ViewBuilder 不继承 BaseMetastoreViewCatalog，故需分别修改），并为各 catalog 测试基类统一注入默认属性、新增 `defaultViewProperties` 测试用例，同时在 Spark 配置文档中补充对应说明。

## 如何达成设计目的

核心思路：仿照 `table-default.` 的实现，在 view builder 构造时（拿到 identifier 之后、用户调用 `withProperty` 之前）用 `PropertyUtil.propertiesWithPrefix(catalogProperties, VIEW_DEFAULT_PREFIX)` 提取所有 `view-default.` 前缀的属性并 `putAll` 进 builder 的 properties。由于用户的 `withProperty` 在其后才调用并写入同一 properties map，默认属性可被用户属性覆盖；用户未指定的则保留 catalog 默认值。

### 修改详情

#### `core/src/main/java/org/apache/iceberg/CatalogProperties.java`

**修改目的**：定义 view 默认属性的前缀常量。

**工作逻辑**：新增 `public static final String VIEW_DEFAULT_PREFIX = "view-default.";`，紧邻已有的 `TABLE_DEFAULT_PREFIX` 与 `TABLE_OVERRIDE_PREFIX`，命名风格一致。所有以 `view-default.` 开头的 catalog 属性将被视为 view 默认属性。

#### `core/src/main/java/org/apache/iceberg/view/BaseMetastoreViewCatalog.java`

**修改目的**：在 `BaseMetastoreViewCatalog` 的 ViewBuilder 构造时注入 catalog 级 view 默认属性，覆盖 HiveCatalog、JdbcCatalog、InMemoryCatalog、NessieCatalog 等继承该基类的 catalog。

**工作逻辑**：
- 新增 import `CatalogProperties`、`PropertyUtil`、`Logger`/`LoggerFactory`，并声明类级 `LOG`。
- 在 ViewBuilder 构造器中（`this.identifier = identifier;` 之后）追加 `this.properties.putAll(viewDefaultProperties());`。
- 新增私有方法 `viewDefaultProperties()`：用 `PropertyUtil.propertiesWithPrefix(properties(), CatalogProperties.VIEW_DEFAULT_PREFIX)` 取出所有 `view-default.` 前缀属性（去掉前缀后的键值对），并以 INFO 级别记录日志便于排查注入了哪些默认属性。

由于用户后续 `withProperty(k, v)` 直接操作同一 `properties` map（`put`），会覆盖构造阶段注入的默认值，从而实现"默认可被用户覆盖"的语义。

#### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java`

**修改目的**：在 REST catalog 的 ViewBuilder 中同样注入 catalog 级 view 默认属性。`RESTSessionCatalog` 继承自 `BaseViewSessionCatalog`（不继承 `BaseMetastoreViewCatalog`），其 ViewBuilder 是独立实现，因此需要单独修改。

**工作逻辑**：在 REST ViewBuilder 构造器（`this.context = context;` 之后）追加 `this.properties.putAll(viewDefaultProperties());`，并新增与基类一致的私有方法 `viewDefaultProperties()`（同样用 `PropertyUtil.propertiesWithPrefix(properties(), CatalogProperties.VIEW_DEFAULT_PREFIX)` 并打 INFO 日志）。两处实现保持逻辑一致。

#### `core/src/test/java/org/apache/iceberg/view/ViewCatalogTests.java`

**修改目的**：为该能力新增通用测试用例，所有继承 `ViewCatalogTests` 的 catalog 测试（InMemory、Jdbc、REST、REST with assumed view、Hive、Nessie 等）自动获得覆盖。

**工作逻辑**：新增 `defaultViewProperties()` 测试：
- 创建 view 时通过 `withProperty("key2", "catalog-overridden-key2")` 覆盖 key2，并 `withProperty("prop1", "val1")` 新增一个属性；不指定 key1。
- 断言结果 view 的 properties 同时满足：
  - `key1 = catalog-default-key1`（来自 catalog 默认，用户未覆盖，保留默认）；
  - `key2 = catalog-overridden-key2`（用户覆盖了 catalog 默认）；
  - `prop1 = val1`（用户新增）。
- 这精确验证了"默认注入 + 用户覆盖"的优先级语义。

#### 各 catalog 测试初始化（InMemory / Jdbc / REST / RESTWithAssumedView / Hive / Nessie / RCKUtils）

**修改目的**：在每个 catalog 测试的初始化配置中注入两个 catalog 级 view 默认属性 `view-default.key1=catalog-default-key1` 与 `view-default.key2=catalog-default-key2`，使上述通用测试 `defaultViewProperties` 在各 catalog 上都能复用同一断言。

**工作逻辑**：在各自 `catalog.initialize(...)` 的属性 map（或 `RCKUtils` 的 catalogProperties）中追加这两个键值对。其中：
- `TestInMemoryViewCatalog`：把原 `ImmutableMap.of()` 改为 `ImmutableMap.builder()` 形式追加两个默认属性。
- `TestJdbcViewCatalog`：在 properties 中追加两个默认属性。
- `TestRESTViewCatalog`：后端 InMemoryCatalog 初始化时追加两个默认属性。
- `TestRESTViewCatalogWithAssumedViewSupport`：在 catalog 配置中追加两个默认属性（覆盖父类已注入的，保证一致）。
- `TestHiveViewCatalog`：在 Hive catalog 初始化配置中追加两个默认属性。
- `TestNessieViewCatalog`：在 Nessie catalog 初始化 options 中追加两个默认属性。
- `RCKUtils`（open-api testFixtures）：用 `putIfAbsent` 追加两个默认属性，供 REST Catalog 兼容性测试套件使用。

#### `docs/docs/spark-configuration.md`

**修改目的**：在 Spark catalog 配置表中补充 `view-default.` 说明，与已有 `table-default.` / `table-override.` 行对齐。

**工作逻辑**：新增一行：

```
| spark.sql.catalog.<catalog-name>.view-default.<propertyKey>  |   | Default Iceberg view property value for property key <propertyKey>, which will be set on views created by this catalog if not overridden |
```

说明该配置为 view 默认属性，建 view 时若未被覆盖则生效。

## 小结

- **成效**：新增 `view-default.<key>` catalog 属性前缀机制，使 catalog 可为所有新建 view 统一注入默认属性，且用户属性可覆盖默认值；能力与 table 的 `table-default.` 对等。能力覆盖 `BaseMetastoreViewCatalog`（Hive/Jdbc/InMemory/Nessie 等）与 `RESTSessionCatalog` 两条 view builder 路径。
- **影响范围**：core 模块（常量 + 两个 ViewBuilder 注入逻辑）、各 catalog 测试初始化、通用测试 `ViewCatalogTests.defaultViewProperties`、Spark 配置文档；共 12 个文件、新增约 102 行。无元数据格式变更，对已存在 view 无影响（仅影响新建 view 的默认属性）。
- **回迁到 1.4.x 的注意事项**：此为 view 属性的功能增强，依赖 1.4.x 是否已具备 view 支持框架（`BaseMetastoreViewCatalog`、`ViewCatalogTests`、`RESTSessionCatalog` 的 view builder 等）。若 1.4.x 已含 view 支持，则可较干净地回迁：新增常量 + 两处 ViewBuilder 注入逻辑 + 测试即可。注意 REST ViewBuilder 的修改与 BaseMetastoreViewCatalog 是两处独立实现，回迁时两处都需带上，否则 REST catalog 下创建的 view 不会获得默认属性，行为不一致。回迁后建议同步文档说明。
