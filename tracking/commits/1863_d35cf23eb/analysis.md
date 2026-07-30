# 提交 1863：Core: Add missing table-override property to REST catalog (#12548)

## 提交信息

- **序号**：1863 / 4088
- **哈希**：d35cf23eb63cc88d4fdb6ae1db8e63630b073d13
- **短哈希**：d35cf23eb
- **日期**：2025-03-17 10:55:10 +0100
- **作者**：Yuya Ebihara
- **提交说明**：Core: Add missing table-override property to REST catalog (#12548)
- **PR/Issue**：#12548

## 总体目的

Iceberg catalog 属性支持两类表级属性前缀：
- `table-default.`（`CatalogProperties.TABLE_DEFAULT_PREFIX`）：表级**默认**属性，建表时注入，用户可在 builder 上覆盖。已在 1852/#11646 为 REST catalog 修复。
- `table-override.`（`CatalogProperties.TABLE_OVERRIDE_PREFIX`）：表级**强制覆盖**属性，建表时注入且**强制覆盖**用户设置的值（用户无法覆盖）。用于运维侧强制策略（如强制 `commit.retry.num-retries=10`）。

`RESTSessionCatalog` 此前只处理了 `table-default.`（1852 修复），完全忽略了 `table-override.`——catalog 配置的 `table-override.xxx` 不会出现在新表属性里。这与 HiveCatalog、JdbcCatalog 等其他实现不一致，且违背了 `table-override` 的"强制覆盖"语义。

本提交补齐 REST catalog 对 `table-override.` 的支持：在 `TableBuilder` 的三条建表路径（`create`、`createTransaction`/`replaceTable`、`stageCreate`）中，在 `propertiesBuilder.buildKeepingLast()` 之前 `putAll(tableOverrideProperties())`。由于 override 在 default 与用户属性之后放入，`buildKeepingLast()` 保留最后值，所以 override 强制覆盖 default 与用户值。

## 如何达成设计目的

1. 新增私有方法 `tableOverrideProperties()`：`PropertyUtil.propertiesWithPrefix(properties(), CatalogProperties.TABLE_OVERRIDE_PREFIX)` 提取 `table-override.` 前缀属性（去前缀），并 `LOG.info(...)` 记录。与 1852 的 `tableDefaultProperties()` 结构对称。
2. 在 `create()`、`createTransaction`（`buildReplacement` 前）、`stageCreate()` 三处，于 `propertiesBuilder.buildKeepingLast()` 之前调用 `propertiesBuilder.putAll(tableOverrideProperties())`。注意 override 必须在 default（TableBuilder 构造时已 putAll）与用户 `withProperty` 之后放入，才能让 `buildKeepingLast()` 保留 override 值——顺序：default → 用户属性 → override，最终 override 胜出。
3. 在 `CatalogTests` 新增三个测试 `testOverrideTableProperties` / `testOverrideTablePropertiesCreateTransaction` / `testOverrideTablePropertiesReplaceTransaction`，分别覆盖 create / createTransaction / replaceTransaction 三条路径，强制所有 catalog 实现验证 override 语义。
4. 在各 catalog 测试初始化里注入 override 测试属性：`table-default.override-key3=catalog-default-key3`、`table-override.override-key3=catalog-override-key3`、`table-override.override-key4=catalog-override-key4`。其中 `override-key3` 同时有 default 和 override 值，用于验证 override 覆盖 default；`override-key4` 只有 override 值，用户在测试中又 `withProperty("override-key4", "catalog-overridden-key4")`，用于验证 override 覆盖用户值。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (修改, +15/-1 lines)

**修改目的**：让 REST catalog 建表时注入 catalog 级 `table-override.` 属性并强制覆盖 default 与用户值。

**工作逻辑**：
- 新增 `private Map<String, String> tableOverrideProperties()`：`PropertyUtil.propertiesWithPrefix(properties(), CatalogProperties.TABLE_OVERRIDE_PREFIX)` + `LOG.info(...)`。
- `create()` 中 `Endpoint.check(...)` 后、构造 `CreateTableRequest` 前 `propertiesBuilder.putAll(tableOverrideProperties())`。
- `createTransaction` 路径中 `base.buildReplacement(...)` 前 `propertiesBuilder.putAll(tableOverrideProperties())`，然后 `propertiesBuilder.buildKeepingLast()`。
- `stageCreate()` 中 `propertiesBuilder.buildKeepingLast()` 前 `propertiesBuilder.putAll(tableOverrideProperties())`。

由于 override 最后放入，`buildKeepingLast()` 保留 override 值，实现强制覆盖。

### `core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java` (修改, +91 lines)

**修改目的**：新增三个测试覆盖 override 在三条建表路径上的强制覆盖语义。

**工作逻辑**：三个测试都 `withProperty("override-key4", "catalog-overridden-key4").withProperty("prop1", "val1")`，断言表属性：
- `default-key1=catalog-default-key1`、`default-key2=catalog-default-key2`（default 注入）；
- `override-key3=catalog-override-key3`（override 覆盖 default，default 值 `catalog-default-key3` 被覆盖）；
- `override-key4=catalog-override-key4`（override 覆盖用户值 `catalog-overridden-key4`）；
- `prop1=val1`（用户自定义，未被 override 触及）。

### 各 catalog 测试初始化文件（7 个文件，每文件 +6~+9 lines）

**修改目的**：为各 catalog 测试注入 override 测试属性。

**工作逻辑**：在 InMemoryCatalog、JdbcCatalog、JdbcCatalogWithV1Schema、RESTCatalog、HiveCatalog、NessieCatalog、RCKUtils 的 catalog 配置中新增三项：
- `table-default.override-key3=catalog-default-key3`；
- `table-override.override-key3=catalog-override-key3`；
- `table-override.override-key4=catalog-override-key4`。

## 小结

- **成效**：REST catalog 建表时正确注入 catalog 级 `table-override.` 属性并强制覆盖 default 与用户值，与其他 catalog 实现行为一致，运维侧的强制策略（如 `commit.retry.num-retries`）能生效。新增三个通用测试覆盖所有 catalog 实现的三条建表路径。
- **影响范围**：core 主代码 1 文件（+15/-1），测试代码 8 个文件（+141/-1）。仅影响 REST catalog 建表路径的属性合并，且只在 catalog 配置了 `table-override.` 前缀属性时有行为变化。
- **回迁到 1.4.x 的注意事项**：建议与 1852、1857 一并回迁（三者构成 REST catalog table-default/override 完整支持）。本提交依赖 `buildKeepingLast()`（1852/1857 引入）与 `PropertyUtil.propertiesWithPrefix`。回迁时需确认 1.4.x 的 `CatalogProperties.TABLE_OVERRIDE_PREFIX` 常量已存在。测试改动机械（各 catalog 加几行配置 + 继承通用测试），可按需回迁。
