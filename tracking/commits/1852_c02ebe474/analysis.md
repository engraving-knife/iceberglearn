# 提交 1852：Core: Set missing table-default property in RESTSessionCatalog (#11646)

## 提交信息

- **序号**：1852 / 4088
- **哈希**：c02ebe4740b22d6f5a78b636aea2d918037b2751
- **短哈希**：c02ebe474
- **日期**：2025-03-14 11:20:33 +0100
- **作者**：Yuya Ebihara
- **提交说明**：Core: Set missing table-default property in RESTSessionCatalog (#11646)
- **PR/Issue**：#11646

## 总体目的

Iceberg 的 catalog 属性支持以 `table-default.` 前缀（`CatalogProperties.TABLE_DEFAULT_PREFIX`）为前缀的"表级默认属性"——这些属性在创建表时会自动作为表的默认属性注入。例如 catalog 配置 `table-default.write.format.default=parquet` 后，所有新建表都默认带上 `write.format.default=parquet`。

`RESTSessionCatalog`（REST catalog 的客户端会话封装）在通过 `buildTable(...).create()` 创建表时，本应把这些 catalog 级 table-default 属性合并进表的 properties，但实际遗漏了：`TableBuilder` 的 `propertiesBuilder` 没有预置这些默认值。结果用户在 catalog 配置了 table-default，通过 REST catalog 建表时这些默认值不会出现在新表的属性里，行为与 HiveCatalog、JdbcCatalog 等其他 catalog 实现不一致。

本提交修复此问题：在 `RESTSessionCatalog.TableBuilder` 构造时把 catalog 的 table-default 属性预填进 `propertiesBuilder`，并改用 `buildKeepingLast()` 保证用户在 builder 上显式设置的属性能覆盖 catalog 默认值。

## 如何达成设计目的

1. 在 `TableBuilder` 构造函数中调用新增的私有方法 `tableDefaultProperties()`，把结果 `putAll` 进 `propertiesBuilder`。`tableDefaultProperties()` 通过 `PropertyUtil.propertiesWithPrefix(properties(), CatalogProperties.TABLE_DEFAULT_PREFIX)` 提取所有 `table-default.` 前缀的属性（去掉前缀后的键值对），并打 INFO 日志便于排查。
2. 把 `setProperties(propertiesBuilder.build())` 改为 `setProperties(propertiesBuilder.buildKeepingLast())`。`ImmutableMap.Builder.build()` 在遇到重复键时抛 `IllegalArgumentException`，而 `buildKeepingLast()` 在重复键时保留后放入的值。这样用户通过 `withProperty(...)` 显式设置的值会覆盖 catalog 默认值，而不是触发异常——既修复了默认值缺失，又支持了"catalog 默认 + 用户覆盖"的合理语义。
3. 在通用测试基类 `CatalogTests` 新增 `testDefaultTableProperties`，强制所有 catalog 实现都验证该行为；并在 InMemory/Jdbc/JdbcV1/REST/Hive/Nessie/RCK 等各 catalog 的测试初始化里注入两个 table-default 属性（`default-key1`、`default-key2`），让新测试能跑通。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (修改, +16/-1 lines)

**修改目的**：让 REST catalog 建表时自动注入 catalog 级 table-default 属性，并支持用户属性覆盖默认。

**工作逻辑**：
- `TableBuilder` 构造函数末尾新增 `propertiesBuilder.putAll(tableDefaultProperties());`，把 catalog 配置中 `table-default.` 前缀的属性预填进 builder。
- 新增私有方法 `tableDefaultProperties()`：`PropertyUtil.propertiesWithPrefix(properties(), CatalogProperties.TABLE_DEFAULT_PREFIX)` 提取前缀属性（去掉前缀），并 `LOG.info(...)` 记录。
- 建表调用处 `setProperties(propertiesBuilder.build())` → `setProperties(propertiesBuilder.buildKeepingLast())`，避免用户显式 `withProperty` 与 catalog 默认值冲突时抛异常，改为保留用户值。

### `core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java` (修改, +26 lines)

**修改目的**：新增通用测试，强制所有 catalog 实现验证 table-default 注入与覆盖语义。

**工作逻辑**：`testDefaultTableProperties` 用 `buildTable(ident, SCHEMA).withProperty("default-key2", "catalog-overridden-key2").withProperty("prop1", "val1").create()` 建表，断言表属性：`default-key1=catalog-default-key1`（catalog 默认，未被覆盖）、`default-key2=catalog-overridden-key2`（用户覆盖 catalog 默认）、`prop1=val1`（用户自定义）。覆盖了"默认注入"与"用户覆盖"两条路径。

### `core/src/test/java/org/apache/iceberg/inmemory/TestInMemoryCatalog.java` (修改, +9/-2 lines)

**修改目的**：为 InMemoryCatalog 测试注入 catalog 级 table-default 属性。

**工作逻辑**：`before()` 中 catalog 配置从 `ImmutableMap.of()` 改为带 `table-default.default-key1=catalog-default-key1` 和 `table-default.default-key2=catalog-default-key2` 的 map。

### `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcCatalog.java` (修改, +2 lines)

**修改目的**：为 JdbcCatalog 测试注入两个 table-default 属性。

**工作逻辑**：在 catalog properties 里 `put(CatalogProperties.TABLE_DEFAULT_PREFIX + "default-key1", "catalog-default-key1")` 和 `default-key2`。

### `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcCatalogWithV1Schema.java` (修改, +2 lines)

**修改目的**：为 JdbcCatalog V1 schema 测试注入同样的 table-default 属性。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (修改, +4 lines)

**修改目的**：为 RESTCatalog 测试注入两个 table-default 属性。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveCatalog.java` (修改, +4/-1 lines)

**修改目的**：为 HiveCatalog 测试注入两个 table-default 属性。

### `nessie/src/test/java/org/apache/iceberg/nessie/TestNessieCatalog.java` (修改, +4/-1 lines)

**修改目的**：为 NessieCatalog 测试注入两个 table-default 属性。

### `open-api/src/testFixtures/java/org/apache/iceberg/rest/RCKUtils.java` (修改, +4 lines)

**修改目的**：为 REST Catalog Compliance Kit（RCK，跨实现合规性测试工具）注入两个 table-default 属性，确保 RCK 跑 `testDefaultTableProperties` 时也有默认值。

## 小结

- **成效**：REST catalog 建表时正确注入 catalog 级 table-default 属性，与其他 catalog 实现行为一致；用户显式属性可覆盖默认值而不抛异常。新增通用测试覆盖所有 catalog 实现。
- **影响范围**：core 主代码 1 文件（+16/-1），测试代码 8 个文件（+58/-3）。仅影响 REST catalog 建表路径，且 `buildKeepingLast()` 的改动只对"用户属性与默认属性同键"的场景有行为变化（之前抛异常，现在用户值胜出）。
- **回迁到 1.4.x 的注意事项**：建议回迁。核心修复集中在 `RESTSessionCatalog.TableBuilder`，依赖 `PropertyUtil.propertiesWithPrefix` 与 `ImmutableMap.Builder.buildKeepingLast()`，二者在 1.4.x 应已存在。测试改动较多但机械（各 catalog 测试加两行配置 + 继承通用测试），可按需回迁。需注意 1.4.x 的 `CatalogTests` 是否已有同名测试以避免重复。
