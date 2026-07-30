# 提交 3564：Core: Expose HostnameVerificationPolicy in TLSConfigurer (#15500)

## 提交信息

- **序号**：3564 / 4088
- **哈希**：a8b7ba6d5ffb47f3e6d4ba937e191c7648c0fe0f
- **短哈希**：a8b7ba6d5
- **日期**：2026-04-20 08:21:38 -0700
- **作者**：Alexandre Dutra
- **提交说明**：Core: Expose HostnameVerificationPolicy in TLSConfigurer (#15500)
- **PR/Issue**：#15500

## 总体目的

该提交旨在利用 Apache HttpClient 5.4 引入的新组件 `HostnameVerificationPolicy`，改进 Iceberg REST 客户端的 TLS 主机名验证机制。`HostnameVerificationPolicy` 决定主机名验证由谁执行：JSSE 提供者（在 socket 层、TLS 握手期间）、HttpClient（TLS 握手后）、或两者都执行。

之前 `TLSConfigurer.hostnameVerifier()` 默认返回 `HttpsSupport.getDefaultHostnameVerifier()`，这导致无法正确区分"使用内置 JSSE 验证"和"使用自定义验证器"的场景。特别是当用户想通过 `NoopHostnameVerifier` 绕过主机名验证时，由于 JSSE 层的内置验证仍然会执行，绕过操作不会生效。该提交将默认返回值改为 `null`，表示使用 JSSE 内置验证器；当返回非 null 的自定义验证器时，仅执行自定义验证器，跳过 JSSE 内置验证。

## 如何达成设计目的

设计方案通过以下方式实现：

1. `TLSConfigurer.hostnameVerifier()` 的默认返回值从 `HttpsSupport.getDefaultHostnameVerifier()` 改为 `null`，`null` 表示使用 JSSE 内置验证器。
2. 在 `HTTPClient` 构建 TLS 策略时，根据 `hostnameVerifier()` 是否为 null 选择 `HostnameVerificationPolicy`：非 null 时使用 `CLIENT`（仅 HttpClient 层验证），null 时使用 `BUILTIN`（仅 JSSE 内置验证）。
3. 将选定的 policy 和验证器一起传给 `DefaultClientTlsStrategy`。
4. 添加 BouncyCastle 依赖用于 TLS 测试中的 MockServer 证书操作。

## 修改详情

### `build.gradle` (+7/-0 lines)

**修改目的**：为 iceberg-core 测试添加 BouncyCastle 依赖。

**工作逻辑**：
新增三个 BouncyCastle 测试依赖（`bcpkix-jdk18on`、`bcutil-jdk18on`、`bcprov-jdk18on`），用于锁定版本避免传递依赖版本不匹配，TLS 测试中 MockServer 需要这些库进行证书操作。

### `core/src/main/java/org/apache/iceberg/rest/HTTPClient.java` (+10/-3 lines)

**修改目的**：根据自定义验证器的存在与否选择 HostnameVerificationPolicy。

**工作逻辑**：
```java
HostnameVerifier customVerifier = tlsConfigurer.hostnameVerifier();
HostnameVerificationPolicy verificationPolicy =
    customVerifier != null
        ? HostnameVerificationPolicy.CLIENT
        : HostnameVerificationPolicy.BUILTIN;
connectionManagerBuilder.setTlsSocketStrategy(
    new DefaultClientTlsStrategy(
        tlsConfigurer.sslContext(),
        tlsConfigurer.supportedProtocols(),
        tlsConfigurer.supportedCipherSuites(),
        SSLBufferMode.STATIC,
        verificationPolicy,
        customVerifier));
```
当自定义验证器非 null 时使用 `CLIENT` policy（仅 HttpClient 层验证），为 null 时使用 `BUILTIN`（JSSE 内置验证）。`DefaultClientTlsStrategy` 接收 policy 和验证器两个参数。

### `core/src/main/java/org/apache/iceberg/rest/auth/TLSConfigurer.java` (+12/-4 lines)

**修改目的**：修改 hostnameVerifier() 默认行为。

**工作逻辑**：
- 移除 `HttpsSupport` 导入，新增 `@Nullable` 导入。
- `hostnameVerifier()` 默认返回值从 `HttpsSupport.getDefaultHostnameVerifier()` 改为 `null`。
- 添加 Javadoc 说明：返回 null 时使用默认 JSSE 内置验证器；返回非 null 验证器时仅执行自定义验证器，JSSE 内置验证器不执行。

### `core/src/test/java/org/apache/iceberg/rest/TestHTTPClient.java` (+109/-0 lines)

**修改目的**：添加主机名验证策略的端到端测试。

**工作逻辑**：
- 新增 `BuiltInHostnameVerifierTLSConfigurer`（不覆盖 `hostnameVerifier()`，使用默认 null/JSSE 内置）和 `CustomHostnameVerifierTLSConfigurer`（返回 `NoopHostnameVerifier.INSTANCE`）。
- 新增 `mockServerSSLContext()` 方法，使用 MockServer 的 `KeyStoreFactory` 创建信任 MockServer 证书的 SSLContext。
- `testTLSConfigurerHostnameVerifier` 测试：启动一个证书 SAN 不包含 127.0.0.1 的 MockServer。验证使用内置验证器时连接被拒绝（抛出 `CertificateException`），使用 NoopHostnameVerifier 时连接成功。

### `gradle/libs.versions.toml` (+4/-0 lines)

**修改目的**：声明 BouncyCastle 版本和库别名。

**工作逻辑**：
新增 `bouncycastle = "1.82"` 版本变量，以及 `bouncycastle-bcpkix`、`bouncycastle-bcprov`、`bouncycastle-bcutil` 三个库别名（`org.bouncycastle:bcpkix-jdk18on`、`bcprov-jdk18on`、`bcutil-jdk18on`）。

## 总结

该提交利用 Apache HttpClient 5.4 的 `HostnameVerificationPolicy` 改进了 TLS 主机名验证机制，使 `TLSConfigurer` 能够正确区分内置验证和自定义验证场景。这解决了用户无法通过 `NoopHostnameVerifier` 有效绕过主机名验证的问题（因为之前 JSSE 层验证仍会执行）。默认行为保持向后兼容（`BUILTIN` policy 产生与之前相同的结果），同时为需要绕过验证的场景提供了正确的支持。
