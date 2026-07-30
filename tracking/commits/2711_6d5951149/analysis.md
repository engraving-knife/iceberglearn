# 提交 2711：REST, OAuth2: Remove deprecated RefreshingAuthManager

## 提交信息

- **序号**：2711 / 4088
- **哈希**：6d5951149f5bd49d80141502ebb5b80c41cb29f7
- **短哈希**：6d5951149
- **日期**：2025-10-01 22:39:22 -0600
- **作者**：Alexandre Dutra
- **提交说明**：REST, OAuth2: Remove deprecated RefreshingAuthManager
- **PR/Issue**：#14229

## 总体目的

`RefreshingAuthManager` 是 Iceberg REST 认证体系中一个抽象基类，提供了异步刷新认证数据的后台线程池机制。该类在 1.10.0 版本中被标记为 `@Deprecated`，并计划在 1.11.0 中移除。此提交执行了这一清理工作。

在引入 `RefreshingAuthManager` 之前，每个 `AuthManager` 实现需要自行管理认证令牌的刷新逻辑。`RefreshingAuthManager` 封装了一个专用的 `ScheduledExecutorService` 来处理令牌的后台刷新。然而，这种设计存在几个问题：每个 `AuthManager` 实例都会创建自己独立的线程池，导致线程资源浪费；而且线程池的生命周期管理与 `AuthManager` 实例绑定，不够灵活。

社区在 1.10.0 中引入了全局共享的 `ThreadPools.authRefreshPool()`，替代了每个 `AuthManager` 实例独立创建线程池的做法。`OAuth2Manager` 是唯一继承 `RefreshingAuthManager` 的子类，现在需要将其改为直接实现 `AuthManager` 接口，并使用全局共享的认证刷新线程池。

## 如何达成设计目的

主要设计思路是：

1. **删除 `RefreshingAuthManager` 类**：整个类被删除，其功能（线程池管理和 `close()` 中的线程池关闭逻辑）不再需要，因为改用全局共享线程池。
2. **修改 `OAuth2Manager`**：将继承关系从 `extends RefreshingAuthManager` 改为 `implements AuthManager`，将原来调用父类的 `keepRefreshed()` 方法改为直接设置实例字段，新增 `refreshExecutor()` 方法返回全局共享线程池。
3. **简化 `close()` 方法**：不再需要关闭线程池（因为使用的是全局共享池），只保留关闭 `AuthSessionCache` 的逻辑。
4. **更新测试**：移除所有对 `refreshExecutor` 私有字段的断言，因为刷新执行器现在是全局共享的，不再是 `OAuth2Manager` 实例的内部状态。
5. **更新二进制兼容性配置**：在 `.palantir/revapi.yml` 中记录 API 变更（类被删除、继承关系变更）。

## 修改详情

### `.palantir/revapi.yml` (+11/-0 lines)

**修改目的**：记录 API 兼容性变更，接受由删除 `RefreshingAuthManager` 带来的二进制不兼容变更。

**工作逻辑**：添加了三条接受的 break 记录：`OAuth2Manager` 不再继承自 `RefreshingAuthManager`（`java.class.noLongerInheritsFromClass`）、`OAuth2Manager` 现在直接实现 `AuthManager` 接口（`java.class.nowImplementsInterface`）、`RefreshingAuthManager` 类被移除（`java.class.removed`）。所有变更的 justification 均为 "Removing deprecations for 1.11.0"。

### `aws/src/integration/java/org/apache/iceberg/aws/s3/signer/TestS3RestSigner.java` (+4/-2 lines)

**修改目的**：更新集成测试中对刷新执行器的断言，改为检查全局共享线程池。

**工作逻辑**：原来通过 `S3V4RestSignerClient.authManager` 的 `refreshExecutor` 字段来获取刷新执行器并断言其状态，现在改为直接从 `ThreadPools.authRefreshPool()` 获取全局共享线程池。由于全局线程池是 `DelegatedScheduledExecutorService` 类型，需要通过反射访问其内部字段 `e` 来获取底层的 `ScheduledThreadPoolExecutor`。

### `core/src/main/java/org/apache/iceberg/rest/auth/OAuth2Manager.java` (+20/-12 lines)

**修改目的**：将 `OAuth2Manager` 从继承 `RefreshingAuthManager` 改为实现 `AuthManager` 接口，并使用全局共享线程池。

**工作逻辑**：
- 类声明从 `extends RefreshingAuthManager` 改为 `implements AuthManager`
- 构造函数不再调用 `super(managerName + "-token-refresh")`
- 新增 `keepRefreshed` 实例字段，原来调用父类 `keepRefreshed(boolean)` 方法改为直接赋值字段 `keepRefreshed = config.keepRefreshed()`
- 新增 `refreshExecutor()` 方法：当 `keepRefreshed` 为 true 时返回 `ThreadPools.authRefreshPool()`，否则返回 null
- `close()` 方法简化：移除调用 `super.close()` 关闭线程池的逻辑，只保留关闭 `AuthSessionCache` 的代码

### `core/src/main/java/org/apache/iceberg/rest/auth/RefreshingAuthManager.java` (-93 lines)

**修改目的**：删除已废弃的 `RefreshingAuthManager` 类。

**工作逻辑**：整个文件被删除。该类提供了后台线程池刷新机制，包含 `keepRefreshed` 标志、`ScheduledExecutorService` 的懒加载创建和 `close()` 中的线程池关闭逻辑。这些功能现在被全局共享的 `ThreadPools.authRefreshPool()` 取代。

### `core/src/test/java/org/apache/iceberg/rest/auth/TestOAuth2Manager.java` (+0/-80 lines)

**修改目的**：移除所有对 `refreshExecutor` 私有字段的断言。

**工作逻辑**：由于 `refreshExecutor` 不再是 `OAuth2Manager` 实例的内部状态（改为全局共享池），所有通过 `assertThat(manager).extracting("refreshExecutor")` 来验证刷新执行器是否被创建/关闭的断言被移除。这些断言涉及约 20 处，覆盖了 initSession、catalogSession、contextualSession、tableSession 等各种场景。

## 总结

此提交是 Iceberg 1.11.0 版本废弃清理工作的一部分。通过删除 `RefreshingAuthManager` 并将 `OAuth2Manager` 改为直接实现 `AuthManager` 接口，简化了认证管理器的继承层次，并将线程池管理统一到全局共享的 `ThreadPools.authRefreshPool()`。这减少了每个 `AuthManager` 实例创建独立线程池的资源开销，提升了线程池的复用效率。该变更是二进制不兼容的，但已在 revapi 配置中记录并接受。
