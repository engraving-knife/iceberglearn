# 提交 1870：Core: Add `view-override` catalog property (#12534)

## 提交信息

- **序号**：1870 / 4088
- **哈希**：8f6ebb5b36a0263edfcb04e0c104b26225f95b07
- **短哈希**：8f6ebb5b3
- **日期**：2025-03-18 07:31:51 +0100
- **作者**：Yuya Ebihara
- **提交说明**：Core: Add `view-override` catalog property (#12534)
- **PR/Issue**：#12534

## 总体目的

本提交为 Iceberg Catalog 新增 `view-override` 属性前缀，使管理员能够通过 catalog 级别的属性强制覆盖 view 的属性，防止用户在创建或修改 view 时绕过这些被强制设置的属性值。

此前 Iceberg 已支持 `table-default` / `table-override` 用于 table 属性，以及 `view-default` 用于 view 属性的默认值，但缺少与 `table-override` 对应的 `view-override`。这意味着对于 view，管理员只能设置默认值（可被用户覆盖），无法强制执行某些属性策略。本提交补齐这一缺口，使 view 属性管理与 table 属性管理保持对称。

该机制对治理场景非常重要：例如强制所有 view 使用特定的 location、history.expire.max-snapshot-age 等属性，确保集群层面的策略不被单个用户绕过。

## 如何达成设计目的

整体设计思路与现有的 `view-default` / `table-override` 实现完全对称：

1. **新增常量**：在 `CatalogProperties` 中新增 `VIEW_OVERRIDE_PREFIX = "view-override."`。

2. **应用 override 属性**：在两个 view 构建路径（`BaseMetastoreViewCatalog` 的内部 ViewBuilder，以及 `RESTSessionCatalog` 的内部 ViewBuilder）中，新增 `viewOverrideProperties()` 私有方法，通过 `PropertyUtil.propertiesWithPrefix` 从 catalog 属性中提取以 `view-override.` 开头的属性。在 `create` 和 `replace` 流程中，使用 `properties.putAll(viewOverrideProperties())` 将这些属性强制写入 view 属性，由于 override 属性是最后 putAll 的，会覆盖用户提供的同名属性。

3. **文档更新**：在 spark-configuration.md 中补充 `view-override` 配置项说明。

4. **测试覆盖**：在 `ViewCatalogTests` 基类中新增 `overrideViewProperties` 测试用例，验证 override 属性会覆盖 default 属性和用户提供的属性。各 catalog 实现（InMemory、JDBC、REST、Hive、Nessie）的测试类在初始化时注入 key3（同时有 default 和 override）、key4（仅 override）属性，验证最终 view 属性中 key3 为 override 值、key4 为 override 值。

## 修改详情

### `core/src/main/java/org/apache/iceberg/CatalogProperties.java` (修改, +1/-0 lines)

**修改目的**：定义 `view-override` 属性前缀常量。

**工作逻辑**：新增 `public static final String VIEW_OVERRIDE_PREFIX = "view-override.";`，与已有的 `TABLE_OVERRIDE_PREFIX`、`VIEW_DEFAULT_PREFIX` 并列。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (修改, +18/-0 lines)

**修改目的**：在 REST catalog 的 view 构建器中应用 override 属性。

**工作逻辑**：
- 在内部 ViewBuilder 类中新增 `viewOverrideProperties()` 方法，使用 `PropertyUtil.propertiesWithPrefix(properties(), CatalogProperties.VIEW_OVERRIDE_PREFIX)` 提取 override 属性，并记录日志。
- 在 `create()` 方法构造 `properties` Map 后、构建 `CreateViewRequest` 前，调用 `properties.putAll(viewOverrideProperties())` 强制写入 override 属性。
- 在 `replace()` 方法中同样在构建 `ViewMetadata.Builder` 前调用 `properties.putAll(viewOverrideProperties())`。

### `core/src/main/java/org/apache/iceberg/view/BaseMetastoreViewCatalog.java` (修改, +18/-0 lines)

**修改目的**：在 metastore view catalog 基类的 view 构建器中应用 override 属性。

**工作逻辑**：与 RESTSessionCatalog 完全对称——新增 `viewOverrideProperties()` 方法，在 `create()` 和 `replace()` 流程的 properties 构建后 `putAll` override 属性。这覆盖了 Hive、JDBC、Nessie 等继承 BaseMetastoreViewCatalog 的实现。

### `core/src/test/java/org/apache/iceberg/view/ViewCatalogTests.java` (修改, +33/-0 lines)

**修改目的**：新增 override 属性的统一测试用例。

**工作逻辑**：新增 `overrideViewProperties()` 测试方法。创建 view 时显式设置 `key4=catalog-overridden-key4`（与 override 值相同）和 `prop1=val1`。然后断言 view 的属性：
- key1/key2 来自 view-default（catalog-default-*）
- key3 来自 view-override（catalog-override-key3），覆盖了 view-default 的 catalog-default-key3
- key4 来自 view-override（catalog-override-key4），与用户设置的值相同
- prop1 来自用户设置（val1）

### 各 catalog 测试类初始化 (修改)

- `core/src/test/java/org/apache/iceberg/inmemory/TestInMemoryViewCatalog.java` (+3)
- `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcViewCatalog.java` (+3)
- `core/src/test/java/org/apache/iceberg/rest/TestRESTViewCatalog.java` (+3)
- `core/src/test/java/org/apache/iceberg/rest/TestRESTViewCatalogWithAssumedViewSupport.java` (+6/-1)
- `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveViewCatalog.java` (+6/-1)
- `nessie/src/test/java/org/apache/iceberg/nessie/TestNessieViewCatalog.java` (+6/-1)
- `open-api/src/testFixtures/java/org/apache/iceberg/rest/RCKUtils.java` (+6)

**修改目的**：在各 catalog 测试初始化时注入 view-default.key3、view-override.key3、view-override.key4 属性，使统一测试用例 `overrideViewProperties` 能通过。

### `docs/docs/spark-configuration.md` (修改, +1/-0 lines)

**修改目的**：文档中补充 `view-override` 配置项说明，与 `view-default`、`table-override` 并列。

## 总结

本提交补齐了 view 属性管理相对于 table 属性管理的对称性，新增 `view-override` catalog 属性前缀，使管理员能强制覆盖 view 属性。实现方式与现有 `view-default` / `table-override` 完全一致，在 BaseMetastoreViewCatalog 和 RESTSessionCatalog 的 view 构建器中，于 create/replace 流程的最后阶段 putAll override 属性。各 catalog 实现的测试均同步更新并新增统一测试用例验证覆盖行为。
