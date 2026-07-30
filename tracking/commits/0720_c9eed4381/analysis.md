# 提交 0720：REST: fix incorrect token refresh thread name

## 提交信息
- **序号**：0720 / 4088
- **哈希**：c9eed438165ff0b399c953a00d65507d02d3971f
- **短哈希**：c9eed4381
- **日期**：2024-04-26 15:46:34 +0200
- **作者**：Alexandre Dutra <adutra@users.noreply.github.com>
- **提交说明**：REST: fix incorrect token refresh thread name (#10223)
- **PR/Issue**：#10223

## 总体目的

本提交修复 REST Session Catalog 中 token 刷新线程命名错误的问题。在修复前，token 刷新线程始终被命名为 `null-token-refresh`，而不是预期的 `<catalogName>-token-refresh`。

这是一个初始化顺序（initialization order）导致的 bug。`RESTSessionCatalog` 在初始化时需要创建 token 刷新的 `ScheduledExecutorService`，该执行器的线程名通过 `ThreadPools.newScheduledPool(name() + "-token-refresh", 1)` 生成，其中 `name()` 返回 catalog 的名称。然而，`tokenRefreshExecutor()` 在 `initialize()` 方法中被调用时，catalog 的 name 尚未被设置（`name()` 返回 null），导致线程名拼接出 `null-token-refresh`。

这个 bug 虽然不影响功能正确性（token 刷新仍然能正常工作），但会带来以下问题：

1. **可观测性下降**：在 thread dump、日志、监控中看到的所有 token 刷新线程都叫 `null-token-refresh`，无法区分是哪个 catalog 实例的线程，对排查问题造成困难。
2. **多实例场景混乱**：当应用同时使用多个 REST Session Catalog 实例时，所有实例的 token 刷新线程都同名，完全无法区分。
3. **诊断误导**：`null` 出现在线程名中容易让人误以为存在配置错误或空指针风险。

## 如何达成设计目的

修复策略是将 catalog 名称作为参数显式传递给 `tokenRefreshExecutor()` 方法，而不是在方法内部通过 `name()` 动态获取。这样可以在调用时确保传入的是已经设置好的 catalog 名称，绕开初始化顺序问题。

具体做法：

1. 修改 `tokenRefreshExecutor()` 方法签名，增加 `String catalogName` 参数，方法内部使用该参数而非 `name()` 来构造线程名。
2. 在 `initialize()` 方法中的两处调用点，将 `tokenRefreshExecutor()` 改为 `tokenRefreshExecutor(name)`。由于 `initialize()` 中调用 `tokenRefreshExecutor()` 的位置在 `initialize()` 内部，此时 `name()` 可能尚未被设置，因此需要确认 `name` 是否已经可用。从 diff 来看，调用点传入的是 `name`（字段引用），而 `name` 字段在 `initialize()` 方法中被赋值。这里的关键是：`initialize()` 方法接收 `name` 作为参数，在方法体中先设置 `this.name = name`，然后再调用 `tokenRefreshExecutor(name)`。但实际 bug 表明，在某些调用路径下 `name()` 返回 null，说明调用顺序有问题。修复后通过显式传参，即使 `name()` 还未就绪，只要 `name` 局部变量（方法参数）有值即可。

实际上，更准确地说，从 diff 中可以看到 `initialize()` 方法里直接使用 `name`（这是方法的参数变量），而 `name()` 是返回 `this.name` 字段的 getter。如果在调用 `tokenRefreshExecutor()` 时 `this.name` 尚未被赋值，`name()` 就会返回 null。但方法参数 `name` 本身是有值的，所以传入 `name` 而非 `name()` 可以解决问题。

3. 在 `loadAuthSession()` 方法中的三处调用点（对应 token、credential、token exchange 三种认证方式），将 `tokenRefreshExecutor()` 改为 `tokenRefreshExecutor(name())`。由于 `loadAuthSession` 是在运行时被调用（而非初始化时），此时 `this.name` 已经被设置，所以 `name()` 可以正确返回。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java`
**修改目的**：修复 token 刷新线程命名错误，使其使用正确的 catalog 名称而非 `null`。

**工作逻辑**：

1. **`initialize()` 方法中的两处调用**（第 221、225 行附近）：
   - `tokenRefreshExecutor()` → `tokenRefreshExecutor(name)`
   - 这两处分别对应 `authResponse != null`（从 auth response 创建 catalogAuth）和 `token != null`（从 access token 创建 catalogAuth）两种初始化路径。传入 `name`（方法参数）而非 `name()`（字段 getter），确保即使 `this.name` 字段尚未赋值也能拿到正确的 catalog 名称。

2. **`tokenRefreshExecutor()` 方法签名修改**（第 558 行附近）：
   - 修改前：`private ScheduledExecutorService tokenRefreshExecutor()`
   - 修改后：`private ScheduledExecutorService tokenRefreshExecutor(String catalogName)`
   - 方法体中将 `name() + "-token-refresh"` 改为 `catalogName + "-token-refresh"`。

3. **`loadAuthSession()` 方法中的三处调用**（第 930、943、956 行附近）：
   - `tokenRefreshExecutor()` → `tokenRefreshExecutor(name())`
   - 这三处分别对应 token、credential、token exchange 三种会话加载路径。由于 `loadAuthSession` 在运行时调用，`this.name` 已经设置完毕，所以使用 `name()` 是安全的。

## 小结

- **成效**：成功达成目的。修复后，token 刷新线程将被正确命名为 `<catalogName>-token-refresh`，可在日志和 thread dump 中清晰识别。
- **影响范围**：仅影响 `core` 模块的 `RESTSessionCatalog` 类，涉及 token 刷新执行器的初始化和会话加载路径。对功能行为无影响，仅改善可观测性。
- **回迁到 1.4.x 的注意事项**：这是一个低风险的 bug 修复，不改变任何 API 或功能行为，可以安全回迁到 1.4.x 分支。需要确认 1.4.x 分支的 `RESTSessionCatalog` 代码结构是否与 main 分支一致（方法签名、调用点位置等）。如果 1.4.x 分支的代码结构有差异，需要相应调整调用点。
