# 提交 1857：Core: Use `buildKeepingLast` for table properties in REST table builder (#12526)

## 提交信息

- **序号**：1857 / 4088
- **哈希**：7195cb2b624cd8546113ce2f918602fa38c93fef
- **短哈希**：7195cb2b6
- **日期**：2025-03-14 19:24:47 +0100
- **作者**：smaheshwar-pltr
- **提交说明**：Core: Use `buildKeepingLast` for table properties in REST table builder (#12526)
- **PR/Issue**：#12526

## 总体目的

这是提交 1852（#11646，让 REST catalog 建表时注入 catalog 级 `table-default` 属性）的后续修复。1852 已经把 `createTable` 路径的 `propertiesBuilder.build()` 改为 `buildKeepingLast()`，并在 `TableBuilder` 构造时预填了 catalog 默认属性。但 `RESTSessionCatalog.TableBuilder` 中还有**另外两条路径**仍使用 `propertiesBuilder.build()`：

1. `createTransaction()` / `replaceTransaction()` 走的 `stageCreate()` 方法（用于事务性建表/替换表）；
2. `replaceTable` 路径（`buildReplacement` 调用处）。

这两条路径同样会用 `propertiesBuilder` 收集属性，而当用户通过 `withProperty(...)` 显式设置的属性与 catalog 级 `table-default` 注入的属性同键时，`build()` 会抛 `IllegalArgumentException: Duplicate key`，导致事务性建表/替换表失败。也就是说，1852 只修了普通 `create()` 路径，事务路径仍有同样的 bug。

本提交把剩余两处 `propertiesBuilder.build()` 也改为 `propertiesBuilder.buildKeepingLast()`，使事务路径与普通 `create` 路径行为一致——用户显式属性覆盖 catalog 默认值，而非抛异常。

## 如何达成设计目的

定位 `RESTSessionCatalog.TableBuilder` 中所有 `propertiesBuilder.build()` 调用点，把剩余两处（`createTransaction` 内的 `buildReplacement` 调用、`stageCreate` 方法）统一改为 `buildKeepingLast()`。同时新增两个通用测试 `testDefaultTablePropertiesCreateTransaction` 和 `testDefaultTablePropertiesReplaceTransaction`，分别覆盖 `createTransaction().commitTransaction()` 与 `replaceTransaction().commitTransaction()` 路径，断言 catalog 默认属性被注入、用户属性覆盖默认、用户自定义属性保留，与 1852 的 `testDefaultTableProperties` 形成完整的三路径覆盖。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (修改, +2/-2 lines)

**修改目的**：把事务路径的属性构建也改为保留最后值（用户值覆盖默认）。

**工作逻辑**：
- `createTransaction` 路径中 `Map<String, String> tableProperties = propertiesBuilder.build();` → `propertiesBuilder.buildKeepingLast();`，传入 `base.buildReplacement(...)`。
- `stageCreate()` 方法中同样 `propertiesBuilder.build();` → `propertiesBuilder.buildKeepingLast();`，传入 `CreateTableRequest`。

`buildKeepingLast()` 在 `ImmutableMap.Builder` 上遇到重复键时保留后放入的值（即用户 `withProperty` 的值），而非抛异常。由于 `TableBuilder` 构造时已先 `putAll` 了 catalog 默认属性（1852 引入），用户的 `withProperty` 后调用，所以用户值胜出。

### `core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java` (修改, +59 lines)

**修改目的**：新增两个测试覆盖事务路径的默认属性注入与覆盖。

**工作逻辑**：
- `testDefaultTablePropertiesCreateTransaction`：`buildTable(ident, SCHEMA).withProperty("default-key2", "catalog-overridden-key2").withProperty("prop1", "val1").createTransaction().commitTransaction()`，断言表属性含 `default-key1=catalog-default-key1`（默认）、`default-key2=catalog-overridden-key2`（覆盖）、`prop1=val1`（自定义）。
- `testDefaultTablePropertiesReplaceTransaction`：先 `createTable(ident, SCHEMA)`，再 `buildTable(ident, OTHER_SCHEMA).withProperty(...).replaceTransaction().commitTransaction()`，断言同上。

两个测试与 1852 的 `testDefaultTableProperties`（普通 create 路径）一起，覆盖了 create / createTransaction / replaceTransaction 三条建表/替换表路径。

## 小结

- **成效**：补齐 1852 遗漏的两处 `build()` 调用，使事务性建表/替换表路径也能正确处理"用户属性与 catalog 默认属性同键"的场景，不再抛 `IllegalArgumentException`。三条路径行为统一。
- **影响范围**：core 模块 1 主代码文件（+2/-2）、1 测试文件（+59）。仅影响 REST catalog 的事务路径，且只在"用户属性与 catalog 默认属性同键"时有行为变化（之前抛异常，现在用户值胜出）。
- **回迁到 1.4.x 的注意事项**：建议与 1852 一并回迁。本提交依赖 1852 在 `TableBuilder` 构造时预填 catalog 默认属性的前提；若单独回迁本提交而缺 1852，则 `propertiesBuilder` 中不会有默认属性，`buildKeepingLast` 与 `build` 行为无差异（无重复键），修复无意义。回迁时需确认 1.4.x 的 `RESTSessionCatalog.TableBuilder` 是否仍有这两处 `build()` 调用。
