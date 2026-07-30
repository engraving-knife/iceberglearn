# 提交 0808：Core: Introduce AuthConfig (#10161)

## 提交信息

- **序号**：0808 / 4088
- **哈希**：67e181ea9b706afc8ff9d1bcd0f9e9c240f173cd
- **短哈希**：67e181ea9
- **日期**：2024-06-03 08:58:16 -0600
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core: Introduce AuthConfig (#10161)
- **PR/Issue**：#10161

## 总体目的

本提交是对 Iceberg REST 客户端认证（OAuth2）子系统的一次结构性重构，核心目标是**引入一个不可变的 `AuthConfig` 配置对象，把原本散落在 `OAuth2Util.AuthSession` 中的多个认证配置字段收敛封装**，从而解决原有设计中存在的构造函数参数过多、配置与运行时状态混杂、可变字段线程安全难保证、子 session 创建时需逐字段复制等问题。

这是一个为后续扩展（例如在 AuthConfig 上增加新的认证配置项，如 token 刷新策略、滑动窗口等）打基础的重构。重构后的 `AuthSession` 只持有两个字段：`headers`（运行时认证头）和 `config`（不可变的 `AuthConfig`），所有认证相关的配置与当前 token 状态都封装在 `AuthConfig` 中。这一改动同时带来了一项并发安全性的实质改进：`stopRefreshing()` 与 `refresh()` 的状态更新从多步分散赋值变为对 `config` 引用的原子替换。

需要强调的是，本次重构保持了对外行为的一致性（认证、token 刷新、S3 签名等流程不变），并保留了两个旧构造函数（标记 `@Deprecated`）以维持二进制兼容性，调用方可以平滑迁移。

## 如何达成设计目的

### 设计思路概览

原有 `AuthSession` 持有 8 个字段：

| 字段 | 类型 | 性质 |
|------|------|------|
| `headers` | `Map<String,String>` | 运行时状态（随 token 变化） |
| `token` | `String` | 运行时状态（刷新时变化） |
| `tokenType` | `String` | 运行时状态 |
| `expiresAtMillis` | `Long` | 运行时状态（由 token 派生） |
| `credential` | `String` | 相对静态的配置 |
| `scope` | `String` | 静态配置 |
| `keepRefreshed` | `boolean` | 状态标志 |
| `oauth2ServerUri` | `String` | 静态配置 |
| `optionalOAuthParams` | `Map<String,String>` | 静态配置 |

这带来四个问题：
1. **构造函数参数过多**：原有 3 个构造函数（5/6/7 参数），调用点容易传错顺序或漏传 null。
2. **配置与状态混杂**：刷新 token 时需小心地只更新 `token/tokenType/expiresAtMillis`，不能动 `credential/scope/oauth2ServerUri/optionalOAuthParams`。
3. **可变字段多，线程安全弱**：多个 volatile 字段分散赋值，`stopRefreshing()` 非同步，`refresh()` 三步赋值期间其他线程可能读到不一致中间状态。
4. **子 session 创建冗长**：`fromAccessToken`、`fromTokenResponse` 等工厂方法创建子 session 时，需逐个从 parent 复制 4 个静态配置字段，代码冗长且易漏。

重构方案：把除 `headers` 外的所有字段收敛进一个 **Immutables 生成的不可变值对象 `AuthConfig`**。`AuthSession` 简化为持有 `headers`（volatile Map）+ `config`（volatile AuthConfig）。修改配置/状态时，通过 builder 生成新的 `AuthConfig` 实例并原子替换 `this.config` 引用。

### 关键设计决策

1. **Immutables 不可变值对象**：`AuthConfig` 用 `@Value.Immutable` 注解，由 Immutables 注解处理器生成 `ImmutableAuthConfig`。不可变对象天然线程安全，修改时生成新实例，符合"copy-on-write"思路。

2. **敏感字段脱敏**：`@Value.Style(redactedMask = "****")` 配合 `@Value.Redacted`，让 `token` 和 `credential` 在生成的 `toString()` 中显示为 `****`，避免在日志/异常信息中泄漏密钥与 token。

3. **派生字段懒加载**：`expiresAtMillis()` 用 `@Value.Lazy` 标注，从 `token()` 解析 JWT 的 `exp` claim 得到过期时间，首次调用时计算并缓存。由于 token 变化时整个 config 会重建，缓存随之失效，语义正确。

4. **合理默认值**：`scope()` 默认 `OAuth2Properties.CATALOG_SCOPE`；`keepRefreshed()` 默认 `true`；`oauth2ServerUri()` 默认 `ResourcePaths.tokens()`。这让 `AuthConfig.builder().build()` 即可直接构造一个合法的空配置（用于 `AuthSession.empty()`）。

5. **`stopRefreshing()` 改为 `synchronized`**：原 `keepRefreshed` 是 volatile boolean 直接赋值；新实现为 `ImmutableAuthConfig.copyOf(config).withKeepRefreshed(false)`，这是"读-改-写"操作，必须 synchronized 保证原子性，否则可能丢失与 `refresh()` 并发写入的 token 更新。这是一项并发安全改进。

6. **`refresh()` 原子更新**：原代码分三步赋值 `this.token = ...; this.tokenType = ...; this.expiresAtMillis = ...`，期间存在不一致窗口；新代码 `this.config = AuthConfig.builder().from(config()).token(newToken).tokenType(newType).build()` 一次性替换 config 引用，配合 volatile 写保证可见性，状态一致性更强。

7. **`from(config())` 继承父配置**：所有子 session 工厂方法用 `AuthConfig.builder().from(parent.config())...build()` 继承父 session 的全部配置，只覆盖需要变化的字段（如 token/tokenType），彻底消除逐字段复制。

8. **保留旧构造函数（二进制兼容）**：两个旧构造函数标记 `@Deprecated`（分别标注 1.5.0、1.6.0 移除），内部委托给新构造函数 `this(baseHeaders, AuthConfig.builder()...build())`，保证外部调用方不会因本次重构而破坏。

9. **新增 `config()` getter**：公开当前 `AuthConfig`，供工厂方法 `from(parent.config())` 使用，也便于外部测试与扩展。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/auth/AuthConfig.java`（新增）

**修改目的**：定义承载 `AuthSession` 全部认证配置的不可变值对象。

**工作逻辑**：

```java
@Value.Style(redactedMask = "****")
@SuppressWarnings("ImmutablesStyle")
@Value.Immutable
public interface AuthConfig {
  @Nullable @Value.Redacted String token();
  @Nullable String tokenType();
  @Nullable @Value.Redacted String credential();
  @Value.Default default String scope() { return OAuth2Properties.CATALOG_SCOPE; }
  @Value.Lazy @Nullable default Long expiresAtMillis() { return OAuth2Util.expiresAtMillis(token()); }
  @Value.Default default boolean keepRefreshed() { return true; }
  @Nullable @Value.Default default String oauth2ServerUri() { return ResourcePaths.tokens(); }
  Map<String, String> optionalOAuthParams();
  static ImmutableAuthConfig.Builder builder() { return ImmutableAuthConfig.builder(); }
}
```

- `token` / `credential` 标记 `@Value.Redacted`：生成的 `toString()` 中以 `****` 替代真实值，防止敏感信息泄漏到日志。
- `expiresAtMillis()` 标记 `@Value.Lazy`：基于 `token()` 派生（解析 JWT 的 `exp`），首次访问时计算并缓存；token 变化时整个 config 重建，缓存自动失效。
- `scope` / `keepRefreshed` / `oauth2ServerUri` 提供 `@Value.Default` 默认值，使 `AuthConfig.builder().build()` 可构造出合法的"空"配置。
- `optionalOAuthParams()` 为 Map 类型，Immutables 对未显式设置的集合属性默认为空集合，因此 `empty()` 中 `.build()` 不设置它也合法。
- `@Value.Style(redactedMask = "****")` 指定脱敏掩码字符串。

### `core/src/main/java/org/apache/iceberg/rest/auth/OAuth2Util.java`

**修改目的**：重构 `AuthSession` 内部类，将 8 个字段收敛为 `headers` + `config`，并改造所有构造、刷新、工厂方法。

**工作逻辑**：

- **字段精简**：
  ```java
  // 旧：8 个字段
  private volatile Map<String, String> headers;
  private volatile String token;
  private volatile String tokenType;
  private volatile Long expiresAtMillis;
  private final String credential;
  private final String scope;
  private volatile boolean keepRefreshed = true;
  private final String oauth2ServerUri;
  private Map<String, String> optionalOAuthParams = ImmutableMap.of();

  // 新：2 个字段
  private volatile Map<String, String> headers;
  private volatile AuthConfig config;
  ```

- **新主构造函数**：
  ```java
  public AuthSession(Map<String, String> baseHeaders, AuthConfig config) {
    this.headers = RESTUtil.merge(baseHeaders, authHeaders(config.token()));
    this.config = config;
  }
  ```
  合并 baseHeaders 与 token 认证头，存储 config。

- **旧构造函数（@Deprecated）**：两个旧构造函数保留以维持二进制兼容，内部委托：
  ```java
  this(baseHeaders,
      AuthConfig.builder()
          .token(token).tokenType(tokenType).credential(credential)
          .scope(scope).oauth2ServerUri(oauth2ServerUri).build());
  ```
  分别标注 1.5.0、1.6.0 移除，提供平滑迁移窗口。

- **getter 全部委托 config**：`token()`→`config.token()`，`scope()`→`config.scope()`，`credential()`→`config.credential()`，`expiresAtMillis()`→`config.expiresAtMillis()`，`oauth2ServerUri()`→`config.oauth2ServerUri()`，`optionalOAuthParams()`→`config.optionalOAuthParams()`。

- **`stopRefreshing()` 改为 synchronized**：
  ```java
  public synchronized void stopRefreshing() {
    this.config = ImmutableAuthConfig.copyOf(config).withKeepRefreshed(false);
  }
  ```
  读-改-写操作需要原子性，synchronized 防止与 `refresh()` 并发时丢失 token 更新。

- **`refresh()` 原子更新 config**：
  ```java
  this.config = AuthConfig.builder()
      .from(config())                       // 继承全部现有配置
      .token(response.token())              // 只覆盖 token
      .tokenType(response.issuedTokenType())// 覆盖 tokenType
      .build();
  this.headers = RESTUtil.merge(headers, authHeaders(config.token()));
  ```
  `from(config())` 保留 credential/scope/oauth2ServerUri/optionalOAuthParams/keepRefreshed，只更新 token 与 tokenType，避免逐字段复制。一次性替换 config 引用，保证状态一致性。

- **`refreshCurrentToken` / `refreshExpiredToken`**：所有字段访问从直接读字段改为调用 getter（`token()`、`expiresAtMillis()`、`credential()` 等），逻辑不变。

- **`empty()` 简化**：
  ```java
  return new AuthSession(ImmutableMap.of(), AuthConfig.builder().build());
  ```
  利用 AuthConfig 的默认值，无需显式传 null/默认 scope。

- **`fromAccessToken` / `fromTokenResponse` / `fromCredential` / `fromTokenExchange`**：统一用 `AuthConfig.builder().from(parent.config())...build()` 继承父配置。例如：
  ```java
  new AuthSession(parent.headers(),
      AuthConfig.builder()
          .from(parent.config())
          .token(token)
          .tokenType(OAuth2Properties.ACCESS_TOKEN_TYPE)
          .build());
  ```
  `fromTokenResponse` 私有重载额外覆盖 `credential`。`fromCredential` 在 `fromTokenResponse` 之上传入 credential。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java`

**修改目的**：将 catalog 级别认证 session 的构造改为使用 `AuthConfig`。

**工作逻辑**：

`catalogAuth` 初始化从：
```java
new AuthSession(baseHeaders, null, null, credential, scope, oauth2ServerUri, optionalOAuthParams);
```
改为：
```java
new AuthSession(baseHeaders,
    AuthConfig.builder()
        .credential(credential)
        .scope(scope)
        .oauth2ServerUri(oauth2ServerUri)
        .optionalOAuthParams(optionalOAuthParams)
        .build());
```
注意此处未设置 `token`（因为 token 来自后续的 `fromTokenResponse` 或 `fromAccessToken`）。语义与原代码完全一致，仅改用 builder 表达。

### `aws/src/main/java/org/apache/iceberg/aws/s3/signer/S3V4RestSignerClient.java`

**修改目的**：将 S3 REST 签名客户端中构造 `AuthSession` 的两处改为使用 `AuthConfig`。

**工作逻辑**：

`authSession()` 方法两处改动：

1. **有 token 时**：构造 parent session 供 `AuthSession.fromAccessToken` 使用：
   ```java
   new AuthSession(ImmutableMap.of(),
       AuthConfig.builder()
           .token(token)
           .credential(credential())
           .scope(SCOPE)
           .oauth2ServerUri(oauth2ServerUri())
           .optionalOAuthParams(optionalOAuthParams())
           .build());
   ```
   原来是 `new AuthSession(ImmutableMap.of(), token, null, credential(), SCOPE, oauth2ServerUri(), optionalOAuthParams())`，tokenType 显式传 null；新代码不设置 tokenType（默认为 null），语义一致。

2. **仅有 credential 时**：构造 session 后用 `fetchToken` 换取 token：
   ```java
   new AuthSession(ImmutableMap.of(),
       AuthConfig.builder()
           .credential(credential())
           .scope(SCOPE)
           .oauth2ServerUri(oauth2ServerUri())
           .optionalOAuthParams(optionalOAuthParams())
           .build());
   ```
   未设置 token（默认 null），与原代码语义一致。

## 小结

- **成效**：
  - 将 `AuthSession` 的 8 个字段收敛为 2 个（`headers` + `config`），大幅降低字段管理复杂度。
  - 引入不可变 `AuthConfig`，配置变更走 copy-on-write，提升了线程安全性（`stopRefreshing()` 加 synchronized、`refresh()` 原子替换 config）。
  - 子 session 工厂方法用 `from(parent.config())` 统一继承配置，消除逐字段复制的冗长与易错。
  - 敏感字段（token/credential）在 `toString()` 中脱敏，降低密钥泄漏风险。
  - 保留旧构造函数（@Deprecated）维持二进制兼容，调用方可平滑迁移。
  - 为后续在 `AuthConfig` 上扩展认证配置项（如更细粒度的刷新策略）打下基础。
- **影响范围**：
  - 涉及 `core` 模块（`AuthConfig` 新增、`OAuth2Util.AuthSession` 重构、`RESTSessionCatalog` 调整）和 `aws` 模块（`S3V4RestSignerClient` 调整）。
  - 仅影响 REST 客户端与 S3 REST 签名的认证初始化与 token 刷新路径，对外行为（认证流程、刷新逻辑、签名生成）保持不变。
  - 旧的 5 参数、6 参数构造函数被 `@Deprecated` 但仍可用，外部依赖二进制兼容。
- **回迁注意事项**：
  - **本提交是 1.4.x 落后于 main 的功能性重构，回迁到 1.4.x 需谨慎评估**。
  - (1) **依赖 Immutables 注解处理器**：1.4.x 的构建配置需确认 Immutables annotation processor 已启用（Iceberg 项目已使用 Immutables，通常无问题，但需确认 `core` 模块的 Immutables 配置与 main 一致）。
  - (2) **二进制兼容性**：本提交保留了旧构造函数，但回迁后若 1.4.x 后续又引入新的 `AuthSession` 改动（如移除旧构造函数），需注意 1.4.x 生态中下游引擎（Spark/Flink 集成）是否调用了旧构造函数。
  - (3) **并发语义变化**：`stopRefreshing()` 从非同步变为 synchronized，`refresh()` 从多步赋值变为原子替换 config。这改善了正确性，但若 1.4.x 有代码依赖旧的非原子行为（极不可能），需重新验证。建议回迁后跑 REST 认证与 token 刷新相关测试。
  - (4) **`expiresAtMillis` 语义**：从"构造时计算并存储字段"变为"`@Value.Lazy` 按需计算并缓存"。对同一个 AuthConfig 实例，多次调用返回缓存的同一值；token 变化时 config 重建、缓存失效。语义等价，但若 1.4.x 有反射或测试直接访问 `expiresAtMillis` 字段（而非 getter），会失效——应通过 getter 访问。
  - (5) **后续依赖**：main 上后续提交可能基于 `AuthConfig` 进一步扩展（如新增配置项、调整刷新逻辑）。回迁本提交后，若还需回迁这些后续提交会更顺畅；若不回迁，则 1.4.x 与 main 在认证模块的分歧会持续扩大。
  - (6) **测试覆盖**：回迁后应重点验证 REST catalog 初始化、token 刷新、S3V4RestSignerClient 签名、`stopRefreshing` 在并发关闭场景下的行为。
