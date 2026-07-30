# 提交 3121：Add idempotency adapter and E2E coverage (#14773)

## 提交信息

- **序号**：3121 / 4088
- **哈希**：4f5768738e79487058fd65fba3faa19612a53391
- **短哈希**：4f5768738
- **日期**：2026-01-16 09:52:55 -0800
- **作者**：Huaxin Gao
- **提交说明**：Add idempotency adapter and E2E coverage (#14773)
- **PR/Issue**：#14773

## 总体目的

REST Catalog 客户端在发起写操作（建表、删表、提交、改名、建视图等）时，已经会通过 `RESTUtil` 自动生成并附带 `Idempotency-Key` 请求头（一个 UUID v7），用于在网络抖动/超时后重试时让服务端识别"这是同一个请求"并返回首次结果，避免重复执行产生副作用（例如重复建表、重复提交）。然而服务端此前并没有真正消费这个头：测试用的 `RESTCatalogAdapter`（充当 REST 服务端）直接把每个请求转发给 `CatalogHandlers` 执行，对相同 key 的重复请求会重复执行，无法验证幂等语义。

本提交补齐了服务端的幂等处理能力，并在测试适配器中接入，提供端到端（E2E）覆盖。核心是在 `CatalogHandlers` 中引入一个带 TTL 与在途合并（in-flight coalescing）的幂等存储：第一个携带某 key 的请求执行动作并把结果缓存，相同 key 的并发或后续请求直接复用缓存结果（或等待首个请求完成后回放）。这覆盖了几种关键场景：(1) 客户端因 503 等瞬态故障重试时，服务端已成功完成但响应丢失，重试应回放首次成功结果而非报错；(2) 相同 key 的重复请求返回缓存结果；(3) key 超过 TTL 后视为新请求；(4) drop 等无返回值操作的重复请求为 no-op。该实现明确标注为内存版、面向测试与轻量使用，生产服务应提供持久化存储。

## 如何达成设计目的

分两部分：(1) 在 `CatalogHandlers` 中新增静态幂等基础设施——`IDEMPOTENCY_STORE`（`ConcurrentMap`）、`IdempotencyEntry`（含 `CountDownLatch` 的在途条目）、`withIdempotency` 包装方法，以及 TTL 配置入口 `setIdempotencyLifetimeFromIso`；(2) 在测试适配器 `RESTCatalogAdapter` 中把所有变更型 handler 调用（建/删/改名表、注册表、更新表、提交事务、命名空间增删改、视图增删改更新）用 `CatalogHandlers.withIdempotency(httpRequest, ...)` 包裹，使其遵守 `Idempotency-Key` 头。同时新增一个 `HeaderValidatingAdapter` 测试子类，能在首次成功后注入瞬态 503 以验证重放，并新增 4 个 E2E 测试覆盖缓存复用、TTL 过期、503 后重放、drop 幂等。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/CatalogHandlers.java` (+151/-0 lines)

**修改目的**：为 REST 服务端提供基于 `Idempotency-Key` 的幂等执行能力。

**工作逻辑**：
新增 `IDEMPOTENCY_STORE = Maps.newConcurrentMap()` 与 `idempotencyLifetimeMillis`（默认 30 分钟，`volatile`）。核心方法 `withIdempotency(HTTPRequest, Supplier<T>)` 与无返回值重载 `withIdempotency(HTTPRequest, Runnable)`：

- 从请求头取 `Idempotency-Key`（`RESTUtil.IDEMPOTENCY_KEY_HEADER`）；没有则直接执行动作。
- 有 key 时，用 `IDEMPOTENCY_STORE.compute(...)` 原子地决定：若当前无条目或条目已过期（仅 FINALIZED 状态判断过期），则标记 `isFirst=true` 并写入一个 `IN_PROGRESS` 条目；否则保留原条目。
- 若条目已是 `FINALIZED`：有错误则抛出，否则返回缓存的 `responseBody`（快路径）。
- 若不是首个请求（在途合并）：调用 `entry.awaitFinalization()` 在 `CountDownLatch` 上等待首个请求完成，再回放其结果或错误。
- 若是首个请求：执行 `action.get()`，成功则 `entry.finalizeSuccess(res)`，抛 `RuntimeException` 则 `entry.finalizeError(e)` 并重新抛出。

`IdempotencyEntry` 内部类持有 `CountDownLatch latch`、`firstSeenMillis`、`status`（IN_PROGRESS/FINALIZED）、`responseBody`、`error`。`finalizeSuccess/finalizeError` 设置结果后 `latch.countDown()` 唤醒等待者。`isExpired()` 仅对 FINALIZED 条目按 `firstSeenMillis + idempotencyLifetimeMillis` 判断，避免在途条目被误判过期。`setIdempotencyLifetimeFromIso` 用 `Duration.parse` 解析 ISO-8601 时长（如 `PT0S`、`PT30M`），标注 `@VisibleForTesting`。注释明确这是面向测试/轻量使用的内存实现，生产应提供持久化存储。

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogAdapter.java` (+58/-31 lines)

**修改目的**：让测试用 REST 服务端适配器遵守 `Idempotency-Key`，并支持注入瞬态故障。

**工作逻辑**：
该适配器是测试中充当 REST 服务端的 `BaseHTTPClient` 子类。改动把原先直接调用 `CatalogHandlers.xxx` 的各变更分支改为用 `CatalogHandlers.withIdempotency(httpRequest, () -> ...)` 包裹，覆盖：`CREATE_NAMESPACE`、`DROP_NAMESPACE`、`UPDATE_NAMESPACE_PROPERTIES`、`CREATE_TABLE`（非 staged 分支，且保留 ETag 响应头设置）、`DROP_TABLE`（purge 与非 purge）、`REGISTER_TABLE`、`UPDATE_TABLE`、`RENAME_TABLE`、`COMMIT_TRANSACTION`、`CREATE_VIEW`、`UPDATE_VIEW`、`RENAME_VIEW`、`DROP_VIEW`。即所有可能产生副作用的写操作都纳入幂等保护。`handleRequest` 末尾把直接 `return handleRequest(...)` 改为先存局部变量 `T resp` 再返回，便于后续（测试子类）在返回前注入故障。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (+296/-38 lines)

**修改目的**：提供幂等行为的端到端测试覆盖与测试基础设施。

**工作逻辑**：
新增内部类 `IdempotentEnv`（封装 ident/http/headers）、`HeaderValidatingAdapter`（继承 `RESTCatalogAdapter`，保留原有请求/响应往返序列化与 header 校验，并新增 `simulateFailureOnFirstSuccessForKey`/`simulate503OnFirstSuccessForKey`：在首次成功执行后按 key 抛出指定异常，用于模拟"服务端已成功但响应丢失"）。`HeaderValidatingAdapter` 重写 `execute(..., parserContext)`，在 `handleRequest` 成功返回后检查是否为变更请求且带 key，若该 key 注册了故障则抛出，从而模拟瞬态 503。测试字段 `adapterForRESTServer` 类型由 `RESTCatalogAdapter` 改为 `HeaderValidatingAdapter`，构造改用 `Mockito.spy(new HeaderValidatingAdapter(...))`。

新增 4 个测试：
- `testIdempotentDuplicateCreateReturnsCached`：同一 key 连续两次 POST 建表，第二次应返回缓存结果而非报 `AlreadyExists`。
- `testIdempotencyKeyLifetimeExpiredTreatsAsNew`：设 TTL 为 `PT0S`，首次建表成功后第二次同 key 请求因过期被视为新请求，抛 `AlreadyExistsException`；`finally` 恢复 `PT30M`。
- `testIdempotentCreateReplayAfterSimulated503`：注册首次成功后抛 503（`CommitStateUnknownException`），首次请求抛出含 "simulated transient 503"；用同 key 重试，服务端回放首次成功结果，返回非空 `LoadTableResponse`。
- `testIdempotentDropDuplicateNoop`：建表后用同 key 两次 DELETE，首次删除成功，第二次不抛异常（no-op）。

辅助方法 `httpAndHeaders` 构造带 `Idempotency-Key`、Authorization、test-header 的 headers 与 `RESTClient`；`idempotentEnv`/`prepareIdempotentEnv` 准备命名空间与标识；`createReq` 构造建表请求；`verifyCreatePost` 与 `reqMatcherContainsHeaders` 用于校验请求 shape（方法、路径、headers）。

## 总结

本提交为 REST Catalog 补齐了服务端幂等处理：在 `CatalogHandlers` 实现带 TTL 与在途合并的内存幂等存储，并在测试适配器中接入所有写操作，使其真正消费客户端已发送的 `Idempotency-Key` 头。配合 4 个 E2E 测试覆盖缓存复用、TTL 过期、503 后重放与 drop 幂等等关键场景，验证了客户端重试时服务端能正确回放首次结果，避免重复副作用。该内存实现明确面向测试，为未来生产级持久化实现提供了语义参考。
