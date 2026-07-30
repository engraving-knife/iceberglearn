# 提交 2964：Core: Send Idempotency-Key on mutation requests when advertised (#14740)

## 提交信息

- **序号**：2964 / 4088
- **哈希**：7bac8650f65279c470d7d2c005c40a858933134a
- **短哈希**：7bac8650f
- **日期**：2025-12-05
- **作者**：Huaxin Gao
- **提交说明**：Core: Send Idempotency-Key on mutation requests when advertised (#14740)
- **PR/Issue**：#14740

## 总体目的

Iceberg REST Catalog 规范定义了 `idempotency-key-lifetime` 配置属性，服务端可通过 `/v1/config` 响应通告是否支持幂等键。当服务端通告支持时，客户端应在变更请求（POST/DELETE）上发送唯一的 `Idempotency-Key` HTTP 头，使服务端能在请求因网络超时等原因重试时识别重复请求并返回原始响应，而非重复执行变更。这对提交事务（commit）等操作尤为重要——可防止因重试导致的双提交问题。

然而此前的 Java REST 客户端实现并未利用这一机制：所有变更请求都以空请求头（`Map.of()`）发送，即使服务端通告了幂等键支持也不会发送 `Idempotency-Key` 头。本提交填补这一缺口，使客户端在检测到服务端通告 `idempotency-key-lifetime` 后，自动为所有变更请求生成并附加 UUIDv7 格式的幂等键。

实现上的关键设计决策是将原先统一的 `headers` 供应商拆分为 `readHeaders`（用于 GET 等读请求）和 `mutationHeaders`（用于 POST/DELETE 等变更请求），因为读请求是天然幂等的不需要幂等键，而变更请求才需要。

## 如何达成设计目的

在 `RESTUtil` 中新增 `IDEMPOTENCY_KEY_HEADER` 常量和 `idempotencyHeaders()` 方法（生成 UUIDv7 幂等键的头映射）。在 `RESTSessionCatalog.initialize()` 中检测 `config.idempotencyKeyLifetime()` 是否非空，若是则将 `mutationHeaders` 字段从默认的 `Map::of`（空）切换为 `RESTUtil::idempotencyHeaders`。随后将该供应商传递给所有变更请求调用及 `newTableOps`/`newViewOps` 工厂方法。`RESTTableOperations` 和 `RESTViewOperations` 同步拆分为 `readHeaders`（GET 用）和 `mutationHeaders`（POST 用）两个字段。测试通过拦截适配器验证变更请求携带幂等键、读请求不携带。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTUtil.java` (+11/-0 lines)

**修改目的**：新增幂等键头常量和生成方法。

**工作逻辑**：
新增常量 `IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"`。新增 `idempotencyHeaders()` 方法，调用 `UUIDUtil.generateUuidV7().toString()` 生成一个 UUIDv7 字符串，返回 `ImmutableMap.of(IDEMPOTENCY_KEY_HEADER, uuid)`。选择 UUIDv7 是因为它是时间有序的（前 48 位为毫秒时间戳），有利于服务端按时间索引和过期清理；同时保证全局唯一性。每次调用生成新键，确保每个变更请求获得独立幂等键。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (+59/-26 lines)

**修改目的**：在服务端通告支持时为所有变更请求附加幂等键头，并拆分读/写头供应商。

**工作逻辑**：
1. **新增 `mutationHeaders` 字段**：`private Supplier<Map<String, String>> mutationHeaders = Map::of`，默认为空映射（不发送幂等键）。
2. **初始化时检测通告**：在 `initialize()` 中，`if (config.idempotencyKeyLifetime() != null)` 时将 `mutationHeaders` 设为 `RESTUtil::idempotencyHeaders`。`idempotencyKeyLifetime()` 来自 `ConfigResponse`，对应服务端 `/v1/config` 响应中的 `idempotency-key-lifetime` 字段。
3. **替换所有变更请求的空头**：将 `dropTable`（DELETE）、`purgeTable`（DELETE）、`renameTable`（POST）、`createTable`/`registerTable`（POST）、`createNamespace`（POST）、`dropNamespace`（DELETE）、`updateNamespaceProperties`（POST）、`commitTransaction`（POST）、`dropView`（DELETE）、`renameView`（POST）、`createView`/`replaceView`（POST）等所有变更调用中的 `Map.of()` 替换为 `mutationHeaders`。
4. **拆分 `newTableOps`/`newViewOps` 签名**：原方法接受单一 `Supplier<Map<String, String>> headers` 参数，现改为 `readHeaders`（读请求头，仍为 `Map::of`）和 `mutationHeaderSupplier`（变更请求头）两个参数。Javadoc 同步更新，明确分别用于 GET/HEAD 和 POST/DELETE。

### `core/src/main/java/org/apache/iceberg/rest/RESTTableOperations.java` (+40/-6 lines)

**修改目的**：拆分读/写头供应商，变更请求使用幂等键头。

**工作逻辑**：
将 `private final Supplier<Map<String, String>> headers` 拆为 `readHeaders` 和 `mutationHeaders` 两个字段。新增两个接受分别供应商的构造器；原有两个构造器保持兼容（将同一 `headers` 同时传给 `readHeaders` 和 `mutationHeaders`）。`refresh()` 方法（GET 请求）使用 `readHeaders`；`commit()` 方法（POST 请求）使用 `mutationHeaders`。在 `commit()` 的 `CommitStateUnknownException` 重试路径中，重试也会通过 `mutationHeaders` 供应商获取新的幂等键。

### `core/src/main/java/org/apache/iceberg/rest/RESTViewOperations.java` (+18/-3 lines)

**修改目的**：与 `RESTTableOperations` 同步拆分读/写头供应商。

**工作逻辑**：
同样将 `headers` 拆为 `readHeaders` 和 `mutationHeaders`。新增接受分别供应商的构造器；原构造器委托（两者均传 `headers`）。`refresh()`（GET）用 `readHeaders`，`commit()`（POST）用 `mutationHeaders`。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (+82/-4 lines)

**修改目的**：验证客户端在服务端通告时自动发送幂等键、未通告时不发送。

**工作逻辑**：
1. **更新 `newTableOps` 重写**：两个重写方法签名适配新的 `readHeaders`/`mutationHeaders` 双参数，`CustomRESTTableOperations` 构造传入 `mutationHeaders`。
2. **`testClientAutoSendsIdempotencyWhenServerAdvertises`**：构造带 `idempotency-key-lifetime=PT30M` 的 `ConfigResponse`，创建 catalog 后执行 createNamespace → createTable → tableExists → dropTable 全流程。`createCatalogWithIdempAdapter` 中的 spy 适配器拦截每个请求，断言：POST/DELETE 请求的头中包含 `Idempotency-Key`，GET 请求不包含。
3. **`testClientDoesNotSendIdempotencyWhenServerNotAdvertising`**：构造不带 `idempotency-key-lifetime` 的 `ConfigResponse`，执行相同流程，断言所有请求（包括变更请求）都不包含 `Idempotency-Key`。
4. **`createCatalogWithIdempAdapter` 辅助方法**：创建 spy `RESTCatalogAdapter`，在 `execute` 方法中拦截请求：若路径为 config 则返回预设的 `ConfigResponse`；否则根据 `request.method()` 判断是否为变更（POST/DELETE），并断言 `Idempotency-Key` 头的存在与否符合预期。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTViewCatalog.java` (+4/-2 lines)

**修改目的**：适配 `newViewOps` 签名变更。

**工作逻辑**：`newViewOps` 重写方法签名适配新的 `readHeaders`/`mutationHeaders` 双参数，`CustomRESTViewOperations` 构造传入 `mutationHeaders`。

## 总结

本提交为 Iceberg REST 客户端实现了规范要求的幂等键机制：当服务端通过 `idempotency-key-lifetime` 通告支持时，客户端自动为所有变更请求（POST/DELETE）生成 UUIDv7 格式的 `Idempotency-Key` 头，使服务端能在请求重试时识别并安全处理重复提交。通过将原先统一的 headers 供应商拆分为读请求和变更请求两路，确保读请求不受影响。测试全面覆盖了通告/未通告两种场景下幂等键的存在性验证，保证了实现的正确性与规范合规性。
