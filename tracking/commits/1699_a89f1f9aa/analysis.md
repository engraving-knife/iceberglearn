# 提交 1699 a89f1f9aa 分析

## 提交信息
- 哈希：a89f1f9aa75628658cfbd421e26e032d7091d323
- 日期：2025-02-07 17:23:18 +0100
- 作者：Alexandre Dutra
- 消息：AWS, Core: SigV4 Auth Manager (#11995)

## 总体目的

本提交在前面 "Auth Manager API" 系列（提交 1692 是第 4 部分）建立的 AuthManager/AuthSession 框架之上，新增一个面向 AWS 的 SigV4 认证管理器实现，让 REST Catalog 客户端能够用 AWS SigV4 协议对发往 REST 服务的请求进行签名。

SigV4 是 AWS API 请求的标准签名协议，用于身份验证与请求完整性保护。此前的 Iceberg 通过 `rest.sigv4-enabled` 属性 + `S3V4RestSignerClient` 这条老的、与 OAuth2 强耦合的代码路径来支持 SigV4。本提交把 SigV4 重新实现为一个独立的 AuthManager，可以与任意其他 AuthManager（默认 OAuth2）组合使用，支持"双重认证"场景：例如 REST 服务既要求 OAuth2 Bearer token 又要求 SigV4 签名时，`RESTSigV4AuthManager` 会先让 delegate（OAuth2）给请求加上 Authorization: Bearer ...，再用 SigV4 覆盖签名，并把原来的 Bearer Authorization 头重命名（relocate）为 `Original-Authorization` 纳入 SigV4 的 canonical headers，从而让签名同时覆盖 Bearer token。

同时，本提交保留了对老属性 `rest.sigv4-enabled` 的兼容（标记为 deprecated，发出告警后映射到新的 `rest.auth.type=sigv4`），避免现有用户配置失效。

## 如何达成设计目的

设计上把 SigV4 实现为"装饰器/委托"模式：
- `RESTSigV4AuthManager` 实现 `AuthManager` 接口，持有一个 `delegate: AuthManager` 字段，所有 `initSession`/`catalogSession`/`contextualSession`/`tableSession` 方法都先调用 delegate 的对应方法得到一个 delegate AuthSession，再包装成 `RESTSigV4AuthSession`。
- `RESTSigV4AuthSession` 实现 `AuthSession`，其 `authenticate(request)` 先调用 `delegate.authenticate(request)` 让 delegate 注入它自己的认证头（如 OAuth2 Bearer），再调用 `sign(...)` 用 `Aws4Signer` 对整个请求做 SigV4 签名。
- `AuthManagers.loadAuthManager` 在检测到 `authType=sigv4` 时，先递归加载 delegate（默认 oauth2，可通过 `rest.auth.sigv4.delegate-auth-type` 配置），再把 delegate 传给 `RESTSigV4AuthManager` 的双参构造函数。

`AuthManagers` 还增加了对老属性 `rest.sigv4-enabled=true` 的兼容处理：若该属性为 true，则强制 authType=sigv4 并发出 deprecation 告警。

### 修改详情

#### aws/src/main/java/org/apache/iceberg/aws/RESTSigV4AuthManager.java（新增，84 行）
新增 AuthManager 实现，关键点：
- 字段：`Aws4Signer signer`（单例，`Aws4Signer.create()`）、`AuthManager delegate`、`Map<String, String> catalogProperties`（在 `catalogSession` 时缓存，供后续 `contextualSession`/`tableSession` 合并上下文属性用）。
- 构造函数 `RESTSigV4AuthManager(String name, AuthManager delegate)`：校验 delegate 非空。`@SuppressWarnings("unused")` 标注 name 参数（仅为符合 AuthManager 反射加载的双参构造签名）。
- `initSession(RESTClient initClient, Map properties)`：返回 `new RESTSigV4AuthSession(signer, delegate.initSession(initClient, properties), new AwsProperties(properties))`。
- `catalogSession(RESTClient sharedClient, Map properties)`：缓存 `catalogProperties`，构造 `AwsProperties`，返回包装 delegate.catalogSession 的 SigV4 session。
- `contextualSession(SessionContext context, AuthSession parent)`：用 `RESTUtil.merge(catalogProperties, context.properties())` 合并上下文属性，构造 `AwsProperties`，返回包装 delegate.contextualSession 的 SigV4 session。
- `tableSession(TableIdentifier, Map properties, AuthSession parent)`：类似 contextualSession，合并 catalogProperties 与 table properties。
- `close()`：仅关闭 delegate（signer 无需关闭）。

#### aws/src/main/java/org/apache/iceberg/aws/RESTSigV4AuthSession.java（新增，156 行）
新增 AuthSession 实现，关键点：
- 常量：`EMPTY_BODY_SHA256`（空 body 的 SHA256 哈希，"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"，用于绕过 signer 对空 body 生成错误 checksum 的问题）；`RELOCATED_HEADER_PREFIX = "Original-"`（用于重命名冲突的 Authorization 头）。
- 字段：`Aws4Signer signer`、`AuthSession delegate`、`Region signingRegion`、`String signingName`、`AwsCredentialsProvider credentialsProvider`（均来自 `AwsProperties` 的 `restSigningRegion()`/`restSigningName()`/`restCredentialsProvider()`）。
- `authenticate(HTTPRequest request)`：先 `delegate.authenticate(request)` 再 `sign(...)`。
- `close()`：仅关闭 delegate。
- `sign(HTTPRequest request)` 工作逻辑：
  1. 构造 `Aws4SignerParams`，设置 signingName、signingRegion、awsCredentials（从 credentialsProvider 解析）、checksumParams（SHA256 算法、非流式、checksumHeaderName=X-Amz-Content-SHA256）。
  2. 把 `HTTPRequest` 转换为 `SdkHttpFullRequest.Builder`：method、protocol（从 URI scheme 取）、uri、headers（通过 `convertHeaders` 转换为 `Map<String, List<String>>`）。
  3. body 处理：若 `request.encodedBody()` 为 null，直接设置 `X-Amz-Content-SHA256: EMPTY_BODY_SHA256`（绕过 signer bug）；否则用 `contentStreamProvider` 提供 body 输入流。
  4. 调用 `signer.sign(sdkRequest, params)` 得到签名后的 `SdkHttpFullRequest`。
  5. 用 `updateRequestHeaders` 把签名后的 headers 合并回 `HTTPHeaders`，构造新的 `ImmutableHTTPRequest`（用 `builder().from(request).headers(newHeaders).build()` 保留原 request 的其他字段）。
- `convertHeaders(HTTPHeaders headers)`：把 HTTPHeaders 按 name 分组为 `Map<String, List<String>>`，其中 "Authorization" 头（忽略大小写）被重命名为 `Original-Authorization`，让 SigV4 签名覆盖原 Authorization，同时把原值纳入签名范围。
- `updateRequestHeaders(originalHeaders, signedHeaders)`：遍历签名后的 headers，若与原 header 同名且原值不在签名值列表中，则把原值以 `Original-` 前缀重命名保留（relocate），再把签名值加入。最终返回合并后的 `ImmutableHTTPHeaders`。

#### core/src/main/java/org/apache/iceberg/rest/auth/AuthManagers.java（71 行变更）
关键改动：
- 新增 import：`Preconditions`、`Maps`、`PropertyUtil`。
- 新增常量 `SIGV4_ENABLED_LEGACY = "rest.sigv4-enabled"`（老属性名）。
- `loadAuthManager` 重写：
  - 先检查 `SIGV4_ENABLED_LEGACY` 是否存在，若存在发 deprecation 告警。
  - 若 `PropertyUtil.propertyAsBoolean(properties, SIGV4_ENABLED_LEGACY, false)` 为 true，则 authType 强制为 `AUTH_TYPE_SIGV4`；否则走原有的 AUTH_TYPE 推断逻辑（显式配置优先，否则按 CREDENTIAL/TOKEN 推断 oauth2，否则 none）。
  - 若 authType 为 SIGV4：读取 `rest.auth.sigv4.delegate-auth-type`（默认 oauth2），校验不能为 sigv4 自身（防无限递归），构造 newProperties（把 AUTH_TYPE 改为 delegate 类型、移除 SIGV4_ENABLED_LEGACY），递归调用 `loadAuthManager(name, newProperties)` 得到 delegate。
  - switch 新增 `case AUTH_TYPE_SIGV4: impl = AUTH_MANAGER_IMPL_SIGV4;`。
  - `DynConstructors.builder` 新增 `.impl(impl, String.class, AuthManager.class)` 双参构造签名，优先尝试双参（带 delegate），若失败回退到单参。
  - `ctor.newInstance(name)` 改为 `ctor.newInstance(name, delegate)`（delegate 可能为 null，对应非 SigV4 类型）。
  - 注：异常处理中 `ClassCastException` 捕获未变，但 `newInstance` 抛出的其他异常通过 `Throwable` 的 `NoSuchMethodException` 等处理（具体见上下文）。

#### core/src/main/java/org/apache/iceberg/rest/auth/AuthProperties.java（6 行变更）
新增常量：
- `AUTH_TYPE_SIGV4 = "sigv4"`
- `AUTH_MANAGER_IMPL_SIGV4 = "org.apache.iceberg.aws.RESTSigV4AuthManager"`（注意：这个类位于 aws 模块，通过反射加载，core 模块不直接依赖 aws）
- `SIGV4_DELEGATE_AUTH_TYPE = "rest.auth.sigv4.delegate-auth-type"`
- `SIGV4_DELEGATE_AUTH_TYPE_DEFAULT = AUTH_TYPE_OAUTH2`

#### aws/src/test/java/org/apache/iceberg/aws/TestRESTSigV4AuthManager.java（新增，176 行）
测试 `AuthManagers.loadAuthManager` 对 SigV4 的加载逻辑：
- `create`：显式 `AUTH_TYPE=sigv4`，断言返回 `RESTSigV4AuthManager` 且 delegate 是 `OAuth2Manager`。
- `createLegacy`：用老属性 `rest.sigv4-enabled=true`，断言同样加载出 `RESTSigV4AuthManager` + OAuth2 delegate。
- `createCustomDelegate`：`SIGV4_DELEGATE_AUTH_TYPE=none`，断言 delegate 是 `NoopAuthManager`。
- `createInvalidCustomDelegate`：`SIGV4_DELEGATE_AUTH_TYPE=sigv4`，断言抛 `IllegalArgumentException`，消息 "Cannot delegate a SigV4 auth manager to another SigV4 auth manager"。
- `initSession`：mock 一个 delegate AuthManager，断言 `RESTSigV4AuthManager.initSession` 返回 `RESTSigV4AuthSession`，且 delegate 的 initSession 被调用。
- 其余测试覆盖 `catalogSession`、`contextualSession`、`tableSession`、`close` 等方法。

#### aws/src/test/java/org/apache/iceberg/aws/TestRESTSigV4AuthSession.java（新增，309 行）
测试 `RESTSigV4AuthSession` 的签名行为，重点覆盖：
- 基本签名：构造 HTTPRequest，调用 authenticate，断言结果含 SigV4 的 Authorization 头、X-Amz-Date、X-Amz-Content-SHA256。
- Authorization 头重命名：当 delegate 已设置 Authorization 时，签名后原值以 `Original-Authorization` 保留。
- 空 body 处理：断言空 body 请求的 X-Amz-Content-SHA256 等于 EMPTY_BODY_SHA256。
- 非 empty body 的 content stream 提供与签名。
- delegate 的 close 被调用。

## 小结

本提交把 AWS SigV4 签名能力以 AuthManager 装饰器的形式集成到新的 Auth 框架，支持与 OAuth2/None/Basic 等任意 delegate 组合，实现双重认证。同时保留对老属性 `rest.sigv4-enabled` 的兼容，迁移路径平滑。Authorization 头重命名机制巧妙地让 SigV4 签名覆盖原 Authorization 同时不丢失原值（纳入签名 canonical headers）。

回迁到 1.4.x 的注意事项：
1. 强依赖 Auth Manager API 系列（parts 1-4，至少包括 1692 引入的 AuthManager/AuthSession/BaseHTTPClient/withAuthSession 等）。1.4.x 若未回迁整套 Auth 框架，本提交无法独立回迁。
2. `AwsProperties` 需要提供 `restSigningRegion()`、`restSigningName()`、`restCredentialsProvider()` 三个方法，回迁时确认 1.4.x 的 `AwsProperties` 已有（或一并回迁）。
3. `RESTSigV4AuthManager` 位于 aws 模块但通过 core 的 `AuthProperties.AUTH_MANAGER_IMPL_SIGV4` 全限定名反射加载，core 模块编译期不依赖 aws；运行时若未引入 aws 模块又配置了 sigv4，会抛 ClassNotFoundException。回迁时需保证 aws 模块同步发布。
4. 老属性 `rest.sigv4-enabled` 的兼容是过渡措施，1.4.x 若回迁应同时保留，避免破坏现有用户配置。
5. `AuthManagers.loadAuthManager` 的递归调用（SigV4 加载 delegate）有防递归保护（delegate 不能是 sigv4），回迁时不能漏掉该校验。
6. `DynConstructors` 的双参构造尝试逻辑：若 SigV4 实现类没有 (String, AuthManager) 构造函数会回退失败，回迁时确认 `RESTSigV4AuthManager` 的构造签名与反射查找一致。
