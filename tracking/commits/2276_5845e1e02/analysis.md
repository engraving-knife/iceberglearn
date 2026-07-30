# 提交 2276：Core: Properly close resources when catalog initialization fails (#13384)

## 提交信息

- **序号**：2276 / 4088
- **哈希**：5845e1e02bf1078c485ff7fe917046e164ec64a3
- **短哈希**：5845e1e02
- **日期**：2025-06-26 09:47:30 +0200
- **作者**：Alexandre Dutra
- **提交说明**：Core: Properly close resources when catalog initialization fails (#13384)
- **PR/Issue**：#13384

## 总体目的

本提交修复了 `RESTSessionCatalog` 在初始化失败时资源泄漏的问题。当 catalog 初始化过程中抛出异常（例如获取 config 配置失败）时，已创建的 `AuthManager` 不会被正确关闭，导致资源泄漏。同样，catalog 的 `RESTClient` 或 `AuthSession` 如果在加入 `CloseableGroup` 之前抛出异常，也会泄漏。

提交说明指出："If initialization fails, for example when fetching the config, the AuthManager won't be properly closed and will leak resources. Same for the catalog's RESTClient or AuthSession, if some error is thrown before they are added to the CloseableGroup."

问题的根因在于原代码将 `CloseableGroup` 的创建和资源注册集中在初始化流程的末尾。如果在初始化早期（创建 AuthManager 之后、构建 client 之前）发生异常，已经分配的资源没有被纳入 CloseableGroup 管理，异常传播后无人负责关闭它们。本提交通过提前创建 CloseableGroup 并在资源创建后立即注册，确保无论初始化在哪个阶段失败，已分配的资源都能被正确关闭。

## 如何达成设计目的

- 将 `CloseableGroup` 的创建提前到初始化流程的最开始（在 AuthManager 创建之前）。
- 在每个可关闭资源（`authManager`、`client`、`catalogAuth`）创建后立即调用 `closeables.addCloseable()` 注册，而非在末尾集中注册。
- 这样无论初始化在哪一步失败抛出异常，`RESTSessionCatalog` 的 `close()` 方法（调用 `closeables.close()`）都能关闭所有已注册的资源，因为 CloseableGroup 始终是非 null 的且包含了所有已创建的资源。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (+6/-4 lines)

**修改目的**：提前创建 CloseableGroup 并即时注册资源，确保初始化失败时资源被正确关闭。

**工作逻辑**：

原代码（问题代码）：
```java
this.authManager = AuthManagers.loadAuthManager(name, props);
// ... 获取 config 的 try 块 ...
this.client = clientBuilder.apply(mergedProps);
this.catalogAuth = authManager.catalogSession(client, mergedProps);
// ... 其他初始化 ...
this.closeables = new CloseableGroup();
this.closeables.addCloseable(this.catalogAuth);
this.closeables.addCloseable(this.authManager);
this.closeables.addCloseable(this.io);
this.closeables.addCloseable(this.client);
this.closeables.addCloseable(fileIOTracker);
```

问题在于：`closeables` 在初始化末尾才创建。如果在获取 config（`authManager` 已创建后）或构建 client 时抛异常，`closeables` 仍为 null，`close()` 调用时要么 NPE，要么无法关闭已创建的 `authManager`。

新代码（修复后）：
```java
this.closeables = new CloseableGroup();          // 提前创建
this.authManager = AuthManagers.loadAuthManager(name, props);
this.closeables.addCloseable(this.authManager);  // 立即注册
// ... 获取 config 的 try 块 ...
this.client = clientBuilder.apply(mergedProps);
this.closeables.addCloseable(this.client);        // 立即注册
this.catalogAuth = authManager.catalogSession(client, mergedProps);
this.closeables.addCloseable(this.catalogAuth);   // 立即注册
// ... 其他初始化 ...
this.closeables.addCloseable(this.io);
this.closeables.addCloseable(fileIOTracker);
this.closeables.setSuppressCloseFailure(true);
```

修复后，`closeables` 在初始化最开始就创建，每个资源创建后立即注册。若初始化中途失败，已注册的资源会在 `close()` 时被正确关闭。`setSuppressCloseFailure(true)` 仍保留在末尾设置（控制关闭时是否抑制异常），这不影响失败场景下的资源关闭——因为即使 suppress 标志未设置，`close()` 仍会尝试关闭已注册资源。注册顺序也调整为资源创建顺序（authManager → client → catalogAuth → io → fileIOTracker），而非原先的反向集中注册。

## 总结

本提交通过调整 `CloseableGroup` 的创建时机和资源注册策略，修复了 `RESTSessionCatalog` 初始化失败时的资源泄漏问题。这是一个典型的"异常安全"修复——确保在部分初始化失败的场景下，已分配的资源仍被正确释放。改动虽小（净增 2 行），但解决了潜在的资源泄漏隐患，提升了 catalog 的健壮性。
