# 提交 3214：AWS: Prefer custom credential provider if specified (#15249)

## 提交信息

- **序号**：3214 / 4088
- **哈希**：48da548979099d0c33c8f39062f728175f786538
- **短哈希**：48da54897
- **日期**：2026-02-06
- **作者**：Bryan Keller
- **提交说明**：AWS: Prefer custom credential provider if specified (#15249)
- **PR/Issue**：#15249

## 总体目的

Iceberg 的 AWS 模块通过 `AwsClientProperties.credentialsProvider(accessKeyId, secretAccessKey, sessionToken)` 决定为 AWS 客户端使用哪一种凭证提供者。该方法原按以下优先级依次判断：①若启用凭证刷新且设置了刷新端点（`refreshCredentialsEndpoint`）→ 使用 `VendedCredentialsProvider`；②若有 accessKeyId/secretAccessKey → 使用静态凭证提供者；③若设置了自定义凭证提供者类名（`clientCredentialsProvider`）→ 动态加载并实例化该自定义类；④否则回退到 `DefaultCredentialsProvider`。

问题在于：当用户显式指定了自定义凭证提供者（`clientCredentialsProvider`），同时又配置了凭证刷新端点（或刷新被默认启用）时，第①步的 `VendedCredentialsProvider` 会先命中，用户精心指定的自定义提供者被静默忽略。这违背了用户意图——显式指定自定义提供者通常是最有意的选择，理应优先于其它自动机制。

本提交把自定义凭证提供者的判断**前置到方法最开头**，使其优先级高于凭证刷新、静态凭证与默认提供者：若设置了 `clientCredentialsProvider`，立即把 `VendedCredentialsProvider.URI`（即刷新端点）写入自定义提供者的属性 map，再返回由该类构造的提供者实例，从而让自定义提供者既取得最高优先级、又能选择性地消费刷新端点。同时将 `S3FileIOProperties.SESSION_TOKEN_EXPIRES_AT_MS` 由包级私有改为 `public`，使位于 `s3` 包之外的自定义凭证提供者实现也能引用该标准属性键、与 `VendedCredentialsProvider` 的会话令牌过期处理互通。

## 如何达成设计目的

整体思路是调整 `credentialsProvider(...)` 内部分支顺序，让"显式自定义"优先于"自动刷新/静态/默认"。涉及三个文件：`AwsClientProperties.java` 为核心优先级修复，`S3FileIOProperties.java` 放开一个常量可见性以支持自定义提供者互操作，`TestAwsClientProperties.java` 新增自定义提供者与一个验证优先级的测试。设计上既保证用户自定义提供者胜出，又把刷新端点以属性形式透传给自定义提供者，保留其按需利用 vended 凭证的能力。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/AwsClientProperties.java` (+6/-4)

**修改目的**：使自定义凭证提供者在指定时优先于凭证刷新、静态凭证与默认提供者。

**工作逻辑**：
在 `credentialsProvider(String accessKeyId, String secretAccessKey, String sessionToken)` 方法开头新增自定义提供者优先分支：

```
if (!Strings.isNullOrEmpty(this.clientCredentialsProvider)) {
  clientCredentialsProviderProperties.put(
      VendedCredentialsProvider.URI, refreshCredentialsEndpoint);
  return credentialsProvider(this.clientCredentialsProvider);
}
```

即先检查 `clientCredentialsProvider` 是否设置；若是，将 `VendedCredentialsProvider.URI` 映射为 `refreshCredentialsEndpoint` 写入 `clientCredentialsProviderProperties`（该 map 会传给自定义类的静态 `create(Map<String, String>)` 方法，使自定义提供者可选地消费刷新端点），再调用 `credentialsProvider(String)` 重载动态加载并实例化该类。原方法中部相同的三行 `clientCredentialsProvider` 分支被删除，使该判断只保留在方法开头一处。后续的刷新端点（`VendedCredentialsProvider`）、静态凭证（`StaticCredentialsProvider`）、默认（`DefaultCredentialsProvider`）分支仅在未指定自定义提供者时才被触及。

`credentialsProvider(String)` 重载（未改动）会动态加载类名，优先调用其 `create(Map<String, String>)` 静态方法、失败则回退 `create()`，传入的正是 `clientCredentialsProviderProperties`，因此把刷新端点写入该 map 即可被自定义提供者使用。

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIOProperties.java` (+1/-1)

**修改目的**：放开会话令牌过期时间属性键的可见性，供包外自定义凭证提供者使用。

**工作逻辑**：
将 `SESSION_TOKEN_EXPIRES_AT_MS = "s3.session-token-expires-at-ms"` 由 `static final`（包级私有）改为 `public static final`。该常量原注释说明"目前仅在 `VendedCredentialsProvider` 刷新 vended 凭证时使用"。改为 public 后，位于 `org.apache.iceberg.aws.s3` 包之外的自定义凭证提供者实现也能引用同一标准属性键读写会话令牌过期时间，从而与 `VendedCredentialsProvider` 的刷新逻辑互通，便于自定义提供者实现自己的凭证刷新机制。

### `aws/src/test/java/org/apache/iceberg/aws/TestAwsClientProperties.java` (+26/-0)

**修改目的**：验证自定义凭证提供者在与刷新端点同时配置时取得优先。

**工作逻辑**：
新增内部静态类 `CustomCredentialProvider implements AwsCredentialsProvider`，提供静态工厂 `create(Map<String, String>)`（返回新实例）与 `resolveCredentials()`（返回空 `AwsBasicCredentials`）。新增测试 `customCredentialsProviderTakesPrecedence()`：以同时设置 `REFRESH_CREDENTIALS_ENDPOINT="http://localhost:1234/v1/credentials"` 与 `CLIENT_CREDENTIALS_PROVIDER=CustomCredentialProvider.class.getName()` 的属性构造 `AwsClientProperties`，调用 `credentialsProvider("key","secret","token")`，断言返回的提供者 `isInstanceOf(CustomCredentialProvider.class)`——即验证在刷新端点存在的情况下，自定义提供者仍胜出（修复前会返回 `VendedCredentialsProvider`）。这正是本次优先级修复的直接回归保护。

## 总结

本提交修正了 AWS 凭证提供者选择的一个优先级缺陷：当用户同时指定自定义凭证提供者与凭证刷新端点时，原实现因 vended 凭证分支靠前而忽略用户显式指定的自定义提供者。通过将自定义提供者判断前置为最高优先级、并把刷新端点以属性透传给自定义类，使用户的自定义凭证方案始终生效且可选择性利用 vended 凭证；同时放开 `SESSION_TOKEN_EXPIRES_AT_MS` 的可见性以支持包外自定义提供者与会话令牌刷新互操作，提升了 AWS 凭证定制能力的可预期性与互操作性。
