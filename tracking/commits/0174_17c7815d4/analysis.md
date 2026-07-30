# 提交 0174：GCS: Allow no-auth for testing purposes (#9061)

## 提交信息

- **序号**：0174 / 4088
- **哈希**：17c7815d433ae075e16351f264ecf17598169dc9
- **短哈希**：17c7815d4
- **日期**：2023-11-17 14:52:17 +0100
- **作者**：Robert Stupp
- **提交说明**：GCS: Allow no-auth for testing purposes (#9061)
- **PR/Issue**：#9061

## 总体目的

这个提交为 Iceberg 的 GCS（Google Cloud Storage）FileIO 增加一种"显式无认证"配置，目的是让用户能够对接 GCS 模拟器（emulator）进行本地或 CI 测试。

提交说明里解释了动机：Google 官方至今没有提供正式的 GCS 模拟器，但社区有 [gcp-storage-emulator](https://github.com/oittaa/gcp-storage-emulator) 这类第三方实现可作基础测试。问题在于：Google Cloud 客户端 SDK 默认会走"自动凭证检测"（参见 `GoogleCredentials.getApplicationDefault()`），在没有任何显式 credentials 配置时，它会尝试从环境变量、metadata server、用户主目录等多处自动发现凭据。这种行为在真实 GCP 环境下很方便，但对模拟器场景非常碍事——模拟器不要求认证，但 SDK 仍会去尝试自动检测，可能因发现本地其他凭据而发送错误的 Authorization header，或者因检测失败而抛错。

解决方式是引入新配置项 `gcs.no-auth`，当显式设为 `true` 时，在构造 `Storage` 客户端时主动调用 `builder.setCredentials(NoCredentials.getInstance())` 注入"无凭据"实例，绕过自动检测路径。同时通过校验逻辑保证 `gcs.no-auth=true` 与 `gcs.oauth2.token` 不会同时被配置（两者语义互斥）。

这对 Iceberg 演进的意义在于：补齐了 GCS FileIO 在测试基础设施上的可对接性，使社区可以基于模拟器跑 GCP 模块的端到端集成测试，而此前这类测试基本只能在真实 GCP 项目上进行，门槛高、费用高、CI 难以承载。

## 如何达成设计目的

在 [`GCPProperties`](../../../gcp/src/main/java/org/apache/iceberg/gcp/GCPProperties.java) 新增 `GCS_NO_AUTH` 常量与 `gcsNoAuth` 字段，在构造时解析并校验与 `gcs.oauth2.token` 的互斥关系，对外暴露 `noAuth()` 方法；在 [`GCSFileIO`](../../../gcp/src/main/java/org/apache/iceberg/gcp/gcs/GCSFileIO.java) 构建 `Storage` 时根据 `noAuth()` 选择是否注入 `NoCredentials.getInstance()`。新增 `GCPPropertiesTest` 覆盖互斥校验与解析行为。

## 修改详情

### `gcp/src/main/java/org/apache/iceberg/gcp/GCPProperties.java`

**修改目的**：新增 `gcs.no-auth` 配置项及其解析、校验、访问接口。

**工作逻辑**：

1. 新增常量与字段：

   ```java
   // Boolean to explicitly configure "no authentication" for testing purposes using a GCS emulator
   public static final String GCS_NO_AUTH = "gcs.no-auth";
   // ...
   private boolean gcsNoAuth;
   ```

2. 在构造函数（解析 properties 的位置）中解析并校验：

   ```java
   gcsNoAuth = Boolean.parseBoolean(properties.getOrDefault(GCS_NO_AUTH, "false"));
   Preconditions.checkState(
       !(gcsOAuth2Token != null && gcsNoAuth),
       "Invalid auth settings: must not configure %s and %s",
       GCS_NO_AUTH,
       GCS_OAUTH2_TOKEN);
   ```

   默认 `false`；与 `gcs.oauth2.token` 互斥，同时配置则抛 `IllegalStateException`。校验放在 `gcsOAuth2Token` 解析之后，确保 `Preconditions.checkState` 引用的是已解析的最终值。

3. 暴露访问器：

   ```java
   public boolean noAuth() {
     return gcsNoAuth;
   }
   ```

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/GCSFileIO.java`

**修改目的**：在构造 `Storage` 客户端时根据 `noAuth()` 注入无凭据实例，绕过 SDK 的自动凭据检测。

**工作逻辑**：在原本只设置 `host`、`clientLibToken`、`oauth2Token` 的位置新增分支：

```java
// Google Cloud APIs default to automatically detect the credentials to use, which is
// in most cases the convenient way, especially in GCP.
// See javadoc of com.google.auth.oauth2.GoogleCredentials.getApplicationDefault().
if (gcpProperties.noAuth()) {
  // Explicitly allow "no credentials" for testing purposes.
  builder.setCredentials(NoCredentials.getInstance());
}
gcpProperties
    .oauth2Token()
    .ifPresent(
        token -> {
          // Explicitly configure an OAuth token.
          AccessToken accessToken =
              new AccessToken(token, gcpProperties.oauth2TokenExpiresAt().orElse(null));
          builder.setCredentials(OAuth2Credentials.create(accessToken));
        });
```

关键点：`NoCredentials` 来自 `com.google.cloud.NoCredentials`（新增 import），是 Google Cloud SDK 提供的"显式无凭据"实例。`builder.setCredentials(...)` 一旦被调用，`Storage` 客户端就不再走自动检测路径。由于 `GCPProperties` 已校验 `noAuth=true` 与 `oauth2Token` 互斥，运行到这里时这两个分支不会同时触发，避免冲突。注释里也明确说明了"Google Cloud APIs 默认自动检测"这一行为，方便后人理解为什么需要显式注入。

### `gcp/src/test/java/org/apache/iceberg/gcp/GCPPropertiesTest.java`

**修改目的**：覆盖 `gcs.no-auth` 与 `gcs.oauth2.token` 的互斥校验及单独配置时的解析。

**工作逻辑**：新增 `testOAuthWithNoAuth` 单测，三个断言：

1. 同时配置 `GCS_OAUTH2_TOKEN=oauth` 与 `GCS_NO_AUTH=true` 时，期望抛 `IllegalStateException` 且消息匹配 `"Invalid auth settings: must not configure gcs.no-auth and gcs.oauth2.token"`。
2. 配置 `GCS_OAUTH2_TOKEN=oauth` 且 `GCS_NO_AUTH=false`（默认行为，显式 false）时，`noAuth()` 为 false、`oauth2Token()` 为 `Optional.of("oauth")`。
3. 仅配置 `GCS_NO_AUTH=true` 时，`noAuth()` 为 true、`oauth2Token()` 为空。

## 小结

通过新增 `gcs.no-auth` 配置显式注入 `NoCredentials`，让 GCS FileIO 能够对接第三方模拟器进行无认证测试，并辅以互斥校验防止与 OAuth token 配置冲突，补齐了 GCP 模块的测试可对接性。
