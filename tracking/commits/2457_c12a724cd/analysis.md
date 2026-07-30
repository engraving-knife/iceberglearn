# 提交 2457：AWS: Fix OAuth2 additional params inclusion (#13718)

## 提交信息

- **序号**：2457 / 4088
- **哈希**：c12a724cd962c2dafc263de79a319dbd50b73548
- **短哈希**：c12a724cd
- **日期**：2025-08-05 11:51:11 +0200
- **作者**：Alexandre Dutra
- **提交说明**：AWS: Fix OAuth2 additional params inclusion (#13718)
- **PR/Issue**：#13718

## 总体目的

该提交修复了 S3 REST 签名客户端中 OAuth2 额外参数包含顺序的回归问题。PR #12197 在重构时意外改变了额外参数的包含顺序，导致当用户配置了自定义 scope 时，该自定义 scope 不再被正确包含在 OAuth2 请求参数中。

问题的根源在于：`optionalOAuthParams()` 方法的构建逻辑中，scope 参数的默认值处理存在问题。在 `S3V4RestSignerClient` 中，scope 的默认值应该是 `SCOPE`（即 `s3.signer`），但 `OAuth2Util.buildOptionalParam` 方法默认使用 `OAuth2Properties.CATALOG_SCOPE` 作为默认 scope。此外，属性构建顺序的改变导致 `optionalOAuthParams()`（其中包含 scope）在 `SCOPE` 属性被设置之前就被放入了 properties map，但由于 `optionalOAuthParams()` 使用了自己的默认 scope 而非 `SCOPE` 常量，自定义 scope 的处理出现了问题。

## 如何达成设计目的

修复方案包含三个关键改动：

1. **为 `buildOptionalParam` 添加 defaultScope 参数**：在 `OAuth2Util` 中新增 `buildOptionalParam(Map<String, String> properties, String defaultScope)` 方法重载，允许调用方指定默认 scope。原有方法委托给新方法，使用 `CATALOG_SCOPE` 作为默认值。S3 签名客户端调用时传入 `SCOPE` 常量作为默认 scope。

2. **修正属性构建顺序**：在 `S3V4RestSignerClient` 的属性构建中，先将 `SCOPE` 放入 properties map，再调用 `optionalOAuthParams()`，确保 `optionalOAuthParams()` 能正确读取到已设置的 scope 值。

3. **增强测试覆盖**：在测试中添加了自定义 scope 的测试用例，并验证 `optionalOAuthParams()` 中包含正确的 scope 值。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/signer/S3V4RestSignerClient.java` (+3/-3 lines)

**修改目的**：修复 S3 签名客户端中 OAuth2 额外参数的构建顺序和 scope 默认值。

**工作逻辑**：

1. `optionalOAuthParams()` 方法改为传入 `SCOPE` 作为默认 scope：
```java
// 修改前
return OAuth2Util.buildOptionalParam(properties());
// 修改后
return OAuth2Util.buildOptionalParam(properties(), SCOPE);
```

2. 属性构建顺序调整，先设置 SCOPE 再合并 optionalOAuthParams：
```java
// 修改前
ImmutableMap.<String, String>builder()
    .putAll(properties())
    .putAll(optionalOAuthParams())       // optionalOAuthParams 在 SCOPE 之前
    .put(OAuth2Properties.OAUTH2_SERVER_URI, oauth2ServerUri())
    .put(OAuth2Properties.TOKEN_REFRESH_ENABLED, String.valueOf(keepTokenRefreshed()))
    .put(OAuth2Properties.SCOPE, SCOPE);

// 修改后
ImmutableMap.<String, String>builder()
    .putAll(properties())
    .put(OAuth2Properties.OAUTH2_SERVER_URI, oauth2ServerUri())
    .put(OAuth2Properties.TOKEN_REFRESH_ENABLED, String.valueOf(keepTokenRefreshed()))
    .put(OAuth2Properties.SCOPE, SCOPE)  // 先设置 SCOPE
    .putAll(optionalOAuthParams());       // 再合并 optionalOAuthParams（包含 scope）
```

### `core/src/main/java/org/apache/iceberg/rest/auth/OAuth2Util.java` (+7/-2 lines)

**修改目的**：为 buildOptionalParam 方法添加可自定义的 defaultScope 参数。

**工作逻辑**：
```java
// 原有方法委托给新方法
public static Map<String, String> buildOptionalParam(Map<String, String> properties) {
    return buildOptionalParam(properties, OAuth2Properties.CATALOG_SCOPE);
}

// 新增方法重载
public static Map<String, String> buildOptionalParam(
    Map<String, String> properties, String defaultScope) {
    // ...
    optionalParamBuilder.put(
        OAuth2Properties.SCOPE,
        properties.getOrDefault(OAuth2Properties.SCOPE, defaultScope));  // 使用传入的 defaultScope
    // ...
}
```

### `aws/src/test/java/org/apache/iceberg/aws/s3/signer/TestS3V4RestSignerClient.java` (+24/-3 lines)

**修改目的**：增强测试以覆盖自定义 scope 场景。

**工作逻辑**：
- 为 `validOAuth2Properties` 测试参数源添加 `expectedScope` 参数
- 新增自定义 scope 的测试用例（scope = "custom"）
- 添加 mock 以验证带 scope 的 token 请求
- 在测试方法中断言 `optionalOAuthParams()` 包含正确的 scope 值

## 总结

该提交修复了 PR #12197 引入的回归问题，该问题导致 S3 REST 签名客户端的自定义 OAuth2 scope 不被正确包含在请求中。修复方案通过为 `OAuth2Util.buildOptionalParam` 添加可配置的 defaultScope 参数，并调整属性构建顺序，确保自定义 scope 被正确传递。同时增强了测试覆盖，添加了自定义 scope 的测试用例以防止回归。
