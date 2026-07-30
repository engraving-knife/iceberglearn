# 提交 2442：Core: introduce shared authentication refresh executor (#12563)

## 提交信息

- **序号**：2442 / 4088
- **哈希**：76b2f875ff14f0ddeb80bfa63e819eeec3047975
- **短哈希**：76b2f875f
- **日期**：2025-08-04 10:34:48 +0200
- **作者**：Alexandre Dutra
- **提交说明**：Core: introduce shared authentication refresh executor (#12563)
- **PR/Issue**：#12563

## 总体目的

本提交为 REST Catalog 的认证管理引入了一个共享的认证刷新线程池（auth refresh pool），替代此前每个 `AuthManager` 实例各自创建独立线程池的做法。

在 catalog session 的生命周期中，会创建多个 `AuthManager` 实例：一个用于 catalog 本身，另外的用于 S3 signer client、credential refreshers 等组件。此前每个 `AuthManager` 实例都会创建自己的线程池来异步刷新认证数据，这造成了线程资源的浪费——尤其是在有多个 auth manager 同时存在的场景下，线程数量会不必要地膨胀。

本提交引入一个全局共享的 `ScheduledExecutorService`（通过 `ThreadPools.authRefreshPool()` 获取），所有 `AuthManager` 可以复用该池。共享池默认核心线程数为 1（可通过系统配置调整），并通过 shutdown hook 在 JVM 退出时自动清理。同时将原有的 `RefreshingAuthManager` 标记为 `@Deprecated`（自 1.10.0 起），引导用户迁移到共享池方案。

## 如何达成设计目的

1. 在 `SystemConfigs` 中新增 `AUTH_REFRESH_THREAD_POOL_SIZE` 配置项（默认 1，可通过 `iceberg.rest.auth.refresh.num-threads` 系统属性或 `ICEBERG_AUTH_REFRESH_NUM_THREADS` 环境变量配置）。
2. 在 `ThreadPools` 中新增 `authRefreshPool()` 静态方法，通过 holder 模式实现懒加载的单例 `ScheduledExecutorService`，使用 `newExitingScheduledPool` 创建（注册 shutdown hook）。
3. 新增 `newExitingScheduledPool` 工具方法，创建带 shutdown hook 的定时线程池。
4. 将 `RefreshingAuthManager` 标记为 `@Deprecated`，Javadoc 引导使用 `ThreadPools.authRefreshPool()`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SystemConfigs.java` (+8/-0 lines)

**修改目的**：新增认证刷新线程池大小的配置项。

**工作逻辑**：新增 `AUTH_REFRESH_THREAD_POOL_SIZE` 配置项，key 为 `iceberg.rest.auth.refresh.num-threads`，环境变量为 `ICEBERG_AUTH_REFRESH_NUM_THREADS`，默认值为 1，解析器为 `Integer::parseUnsignedInt`。默认 1 个线程足以满足认证刷新的低频需求，同时最小化线程开销。

### `core/src/main/java/org/apache/iceberg/rest/auth/RefreshingAuthManager.java` (+3/-0 lines)

**修改目的**：标记旧的认证刷新管理器为废弃。

**工作逻辑**：在类上添加 `@Deprecated` 注解，并在 Javadoc 中说明"since 1.10.0, will be removed in 1.11.0; use `ThreadPools.authRefreshPool()`"。这是引导迁移的信号，后续版本将移除该类自身的线程池管理逻辑。

### `core/src/main/java/org/apache/iceberg/util/ThreadPools.java` (+36/-0 lines)

**修改目的**：提供共享的认证刷新线程池。

**工作逻辑**：
- 新增 `AUTH_REFRESH_THREAD_POOL_SIZE` 常量，从 `SystemConfigs` 读取配置值。
- 新增 `authRefreshPool()` 公共静态方法，返回 `AuthRefreshPoolHolder.INSTANCE`（holder 模式实现的懒加载单例）。
- `AuthRefreshPoolHolder` 内部类：持有 `INSTANCE`，通过 `newExitingScheduledPool("auth-session-refresh", AUTH_REFRESH_THREAD_POOL_SIZE, Duration.ZERO)` 创建。使用 `Duration.ZERO` 作为终止超时，表示 JVM 退出时不等待。
- 新增 `newExitingScheduledPool(String namePrefix, int poolSize, Duration terminationTimeout)` 公共静态方法：先通过 `newScheduledPool` 创建 `ScheduledThreadPoolExecutor`（使用 daemon 线程），再用 `MoreExecutors.getExitingScheduledExecutorService` 包装，注册 shutdown hook 确保在 JVM 退出时自动终止。新增 `java.time.Duration` 和 `java.util.concurrent.TimeUnit` 的 import。

## 总结

本提交通过引入共享的认证刷新线程池，优化了 REST Catalog 中多个 `AuthManager` 实例的线程资源使用。此前每个 AuthManager 各自创建线程池造成浪费，现在统一复用一个全局共享池（默认 1 线程，可配置）。同时将旧的 `RefreshingAuthManager` 标记为废弃，引导迁移。这是一个资源优化提交，对减少认证相关线程数量、降低系统开销有实际意义。
