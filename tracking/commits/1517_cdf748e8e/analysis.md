# 提交 1517 cdf748e8e 分析

## 提交信息
- 哈希：cdf748e8e5537f13d861aa4c617a51f3e11dc97c
- 日期：2024-12-20（Fri Dec 20 18:51:51 2024 +0100）
- 作者：Alexandre Dutra <adutra@users.noreply.github.com>
- 消息：Auth Manager API part 2: AuthManager (#11809)

## 总体目的

本提交是 REST Catalog 认证管理 API 重构的"第二部分"（part 2 of a series，对应 PR #11809）。Iceberg REST Catalog 客户端需要为发往 REST server 的每个 HTTP 请求附加认证信息（如 OAuth2 token、BASIC 凭据、Kerberos 票据等）。旧实现中认证逻辑散落在 `RESTSessionCatalog` 及若干静态工具方法里，难以扩展、难以让用户自定义认证方式。

本提交引入一套清晰的"认证管理器 + 认证会话"抽象：

1. **`AuthManager`**：有状态、有生命周期的认证管理器接口。由 catalog 持有，负责创建不同作用域的 `AuthSession`——catalog 级（长生命周期，作为父会话）、context 级（按 SessionContext 区分）、table/view 级（按表属性区分，支持表级 token）。`AutoCloseable`，随 catalog 关闭。
2. **`AuthSession`**：无状态（通常不可变）的认证会话，核心方法 `authenticate(HTTPRequest)` 把认证头注入请求。
3. **`DefaultAuthSession`**：基于固定 `HTTPHeaders` 的默认实现，大多数 AuthManager 可复用。
4. **`BasicAuthManager` / `NoopAuthManager`**：两个内置实现——BASIC 认证与无认证。
5. **`AuthManagers`**：工厂类，根据 `rest.auth.type` 属性通过反射加载 AuthManager 实现，支持 `none`/`basic` 内置类型或自定义全限定类名。
6. **`AuthProperties`**：相关配置属性常量。

这套抽象的关键设计是把"如何认证"（AuthManager 实现，可插拔、可自定义）与"何时认证"（catalog 调用 `session.authenticate(request)`）解耦，并把会话按作用域分层（catalog/context/table），为后续支持 OAuth2 token 刷新、表级 token、context 级凭据等场景打好基础。

## 如何达成设计目的

通过新增 7 个主代码类 + 4 个测试类，构建出完整的认证管理抽象。设计上接口分离（Manager 管生命周期与分层、Session 管单次请求认证），并通过反射工厂支持运行时插拔。

### 修改详情

#### `core/src/main/java/org/apache/iceberg/rest/auth/AuthManager.java`（新增）
- 接口，`extends AutoCloseable`。定义四类会话工厂方法：
  - `initSession(RESTClient initClient, Map properties)`（default）：返回一次性临时会话，用于访问 `/v1/config` 配置端点。默认委托给 `catalogSession`。注释说明返回的会话用完即关，不应缓存；传入的 RESTClient 也是短生命周期。
  - `catalogSession(RESTClient sharedClient, Map properties)`（抽象）：返回长生命周期 catalog 级会话，作为所有其他会话的父会话。注释说明传入的 sharedClient 是长生命周期共享客户端，实现可选择保留用于后续 token 刷新。
  - `contextualSession(SessionContext context, AuthSession parent)`（default）：返回 context 级会话，默认返回父会话。注释要求实现方内部缓存 context 会话（catalog 不缓存），并自行管理其生命周期。
  - `tableSession(TableIdentifier table, Map properties, AuthSession parent)`（default）：返回表/视图级会话，默认返回父会话。properties 来自 table endpoint 返回的配置（可包含表级 token）。同样要求实现方缓存并自行管理生命周期。
  - `close()`：释放资源，由 catalog 关闭时调用。
- **工作逻辑**：分层会话模型允许不同认证策略在不同作用域生效——例如 catalog 级用 OAuth2 client credentials，某些表用表级 token，某些 context 用 user token。default 方法返回 parent 保证了"不关心此作用域的实现可以直接复用父会话"。

#### `core/src/main/java/org/apache/iceberg/rest/auth/AuthSession.java`（新增）
- 接口，`extends AutoCloseable`。核心方法 `HTTPRequest authenticate(HTTPRequest request)`——把认证信息注入请求并返回新请求。
- 提供静态 `EMPTY` 单例：空会话，`authenticate` 原样返回请求，`close` 空操作。
- `close()`：释放会话可能持有的资源（如后台刷新 token 的线程）。注释说明会话可能被缓存，close 不一定在会话不再使用时立即触发。
- **目的**：将"认证单个请求"这一行为抽象为可注入对象，便于在不同作用域使用不同会话，且支持有状态会话（如持有可能过期的 token）。

#### `core/src/main/java/org/apache/iceberg/rest/auth/DefaultAuthSession.java`（新增）
- `@Value.Immutable` 不可变实现，持有 `HTTPHeaders headers()`。
- `authenticate`：用 `request.headers().putIfAbsent(headers())` 把认证头注入请求——`putIfAbsent` 保证不覆盖请求已有的同名头。若 headers 未变则原样返回请求（避免不必要的对象创建）。
- `close`：默认空操作。
- 工厂方法 `of(HTTPHeaders)`。
- `@Value.Style(redactedMask = "****")`：在 `toString` 中对敏感字段打码。
- **目的**：为大多数 AuthManager 提供开箱即用的会话实现——只需要拼好认证头即可，无需自己实现 authenticate 逻辑。

#### `core/src/main/java/org/apache/iceberg/rest/auth/BasicAuthManager.java`（新增）
- `final` 实现。构造函数 `BasicAuthManager(String ignored)`（签名要求与反射加载兼容，name 参数被忽略）。
- `catalogSession`：从 properties 读取 `rest.auth.basic.username` 和 `rest.auth.basic.password`，校验非空，拼成 `username:password`，调用 `OAuth2Util.basicAuthHeaders(credentials)` 生成 `Authorization: Basic xxx` 头，返回 `DefaultAuthSession.of(headers)`。
- `close`：空操作（无资源）。
- **目的**：内置 BASIC 认证支持。展示如何用 DefaultAuthSession 快速实现一个 AuthManager。

#### `core/src/main/java/org/apache/iceberg/rest/auth/NoopAuthManager.java`（新增）
- 实现。构造函数 `NoopAuthManager(String ignored)`。
- `catalogSession` 返回 `AuthSession.EMPTY`。
- `close` 空操作。
- **目的**：无认证场景的默认实现。

#### `core/src/main/java/org/apache/iceberg/rest/auth/AuthManagers.java`（新增）
- 工厂类。核心方法 `loadAuthManager(String name, Map properties)`：
  - 读取 `rest.auth.type` 属性，默认 `none`。
  - switch（小写化）：`none` → `NoopAuthManager` 全限定名；`basic` → `BasicAuthManager` 全限定名；其他 → 直接把 authType 当作全限定类名（支持自定义实现）。
  - 用 `DynConstructors` 反射查找接受 `String` 参数的构造函数，实例化 AuthManager。
  - 失败时抛 `IllegalArgumentException`，消息包含 impl 名称和原因。
- **目的**：根据配置动态加载 AuthManager 实现，支持内置类型 + 自定义全限定类名两种方式。`String name` 参数让实现知道自己是哪个 catalog 的 manager（多 catalog 场景下用于日志/调试）。

#### `core/src/main/java/org/apache/iceberg/rest/auth/AuthProperties.java`（新增）
- 常量类。定义：
  - `AUTH_TYPE = "rest.auth.type"`：认证类型属性键。
  - `AUTH_TYPE_NONE = "none"`、`AUTH_TYPE_BASIC = "basic"`：内置类型值。
  - `AUTH_MANAGER_IMPL_NONE`、`AUTH_MANAGER_IMPL_BASIC`：对应实现的全限定类名。
  - `BASIC_USERNAME = "rest.auth.basic.username"`、`BASIC_PASSWORD = "rest.auth.basic.password"`：BASIC 凭据属性键。
- **目的**：集中管理配置属性键名，避免散落在各处。

#### `core/src/main/java/org/apache/iceberg/rest/HTTPHeaders.java`（修改）
- 新增静态工厂 `HTTPHeaders.of(Map<String, String> headers)`：把 Map 转为 `HTTPHeader` 列表后构造 `ImmutableHTTPHeaders`。
- **目的**：方便从 Map（如 properties）构造 HTTPHeaders，BasicAuthManager 等场景需要。`HTTPHeaders` 内部本就处理头名大小写归一化（如 `header1` 与 `HEADER1` 视为同名），此工厂保持一致行为。

#### `core/src/test/java/org/apache/iceberg/rest/TestHTTPHeaders.java`（修改）
- 新增 `ofMap` 测试：验证 `HTTPHeaders.of(Map)` 能正确处理大小写不敏感的头名（`header1`/`HEADER1` 视为同名，后者覆盖前者），结果与已有的 `headers` 测试夹具相等。

#### `core/src/test/java/org/apache/iceberg/rest/auth/TestAuthManagers.java`（新增）
- 测试工厂 `loadAuthManager`：
  - `noop`：空 properties 默认加载 NoopAuthManager，且 stderr 输出含 "Loading AuthManager implementation: ...NoopAuthManager"。
  - `noopExplicit`：显式 `AUTH_TYPE=none` 同样加载 NoopAuthManager。
  - `basicExplicit`：`AUTH_TYPE=basic` 加载 BasicAuthManager。
  - `nonExistentAuthManager`：未知类型抛 `IllegalArgumentException`，消息含 "Cannot initialize AuthManager implementation unknown"。
- 通过捕获 System.err 验证日志输出。

#### `core/src/test/java/org/apache/iceberg/rest/auth/TestBasicAuthManager.java`（新增）
- `missingUsername`：缺 username 抛异常，消息指明缺失属性。
- `missingPassword`：有 username 缺 password 抛异常。
- `success`：完整凭据生成正确会话——`session` 等于 `DefaultAuthSession.of(HTTPHeaders.of(OAuth2Util.basicAuthHeaders("alice:secret")))`。

#### `core/src/test/java/org/apache/iceberg/rest/auth/TestDefaultAuthSession.java`（新增）
- `authenticate`：原请求无 Authorization 头，认证后注入 `Authorization: s3cr3t`。
- `authenticateWithConflictingHeader`：原请求已有 `Authorization: other`，认证后请求原样返回（`isSameAs`，即同一对象引用）——验证 `putIfAbsent` 不覆盖已有头。

## 小结

- **成效**：为 REST Catalog 引入了清晰、可插拔、分层作用域的认证管理抽象。AuthManager/AuthSession 接口分离关注点（生命周期 vs 单次请求），DefaultAuthSession 降低实现成本，AuthManagers 工厂支持配置驱动的实现加载，BasicAuthManager/NoopAuthManager 提供开箱即用的内置实现。这是后续 OAuth2、Kerberos、表级 token 等认证能力的基础设施。
- **影响范围**：core 模块的 `rest/auth` 包新增 7 个主类 + 4 个测试类，外加 `HTTPHeaders` 一个新工厂方法和对应测试。共 12 个文件，+669 行。本提交是 API 设计的一部分，尚未接入 `RESTSessionCatalog`（接入应在后续 part 中完成），因此本提交单独不改变运行时行为。
- **设计亮点**：（1）分层会话（init/catalog/context/table）+ default 方法返回 parent，让简单实现只需覆盖 `catalogSession`；（2）反射工厂 + 全限定类名 fallback 支持完全自定义；（3）`DefaultAuthSession` 用 `putIfAbsent` 避免覆盖请求已有头，并返回原对象引用以减少分配；（4）Immutables + `redactedMask` 保证安全日志。
- **回迁到 1.4.x 的注意事项**：这是新功能/API 引入，1.4.x 作为维护分支通常**不回迁新功能**。若 1.4.x 的 REST 认证有严重 bug 需要此抽象才能修复，则可考虑部分回迁，但需评估与 1.4.x 现有 `RESTSessionCatalog` 认证逻辑的兼容性。一般情况下建议在 1.4.x 保持现有认证实现，待下次大版本（如 1.5/2.0）再引入此 API。

__tr_native_ec=$?; pwd -P >| '/var/folders/j4/8_ygb9zx7ll_gb4jlr9jd_vw0000gn/T/trae-agent-toolhost-501/jobs/job-9de81c2c79eb492db5f2e04ce8738318/cwd.txt'; exit "$__tr_native_ec"