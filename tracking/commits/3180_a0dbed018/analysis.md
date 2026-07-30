# 提交 3180：GCP: Add gcp.auth.credentials-key property (#14713)

## 提交信息

- **序号**：3180 / 4088
- **哈希**：a0dbed01841c50e8127538dcd9e542351a7f3f6e
- **短哈希**：a0dbed018
- **日期**：2026-01-29
- **作者**：Yuya Ebihara
- **提交说明**：GCP: Add gcp.auth.credentials-key property (#14713)
- **PR/Issue**：#14713

## 总体目的

Iceberg 的 GCP 模块通过 `GoogleAuthManager` 管理认证，此前支持两种凭据获取方式：通过 `gcp.auth.credentials-path` 指定服务账号 JSON 密钥文件路径，或不设置该属性时回退到 Application Default Credentials（ADC）。但在容器化部署、serverless 环境（如 Kubernetes Secret 以环境变量注入）或 REST Catalog 场景下，将凭据以文件形式落盘并不总是可行或安全的——用户可能更希望直接以 JSON 字符串形式传递凭据，避免临时文件管理开销与安全风险。

本提交新增 `gcp.auth.credentials-json` 属性，允许用户直接传入服务账号凭据的 JSON 字符串。这与 AWS 模块中已有的 `gcp.auth.credentials-json`（注：AWS 侧为类似设计理念）以及其他云平台的内联凭据支持保持一致。同时，为避免歧义，当用户同时设置了 `credentials-path` 和 `credentials-json` 时会抛出 `IllegalArgumentException`。文档也同步更新了配置参考表。

注：PR 标题写作 "credentials-key"，但实际实现的属性名为 `gcp.auth.credentials-json`，更准确地反映了其接受 JSON 字符串的语义。

## 如何达成设计目的

改动集中在 `GoogleAuthManager.java`：新增 `GCP_CREDENTIALS_JSON_PROPERTY` 常量，在 `catalogSession` 初始化逻辑中读取该属性，通过 `ByteArrayInputStream` 将 JSON 字符串转为 `InputStream` 后调用 `GoogleCredentials.fromStream()` 加载，与文件路径方式的处理逻辑对称。同时增加互斥校验。测试文件补充了 JSON 凭据构建会话的测试和互斥校验测试，文档配置表新增一行说明。

## 修改详情

### `docs/docs/configuration.md` (+1/-0 lines)

**修改目的**：在 GCP 认证配置表中记录新属性。

**工作逻辑**：
在 `gcp.auth.credentials-path` 行之后新增 `gcp.auth.credentials-json` 行，描述为 "JSON string of a service account credential."，默认值为 Application Default Credentials (ADC)，与 `credentials-path` 格式保持一致。

### `gcp/src/main/java/org/apache/iceberg/gcp/auth/GoogleAuthManager.java` (+26/-2 lines)

**修改目的**：支持通过 JSON 字符串加载 GCP 服务账号凭据。

**工作逻辑**：
1. 新增常量 `GCP_CREDENTIALS_JSON_PROPERTY = "gcp.auth.credentials-json"`。
2. 新增导入 `ByteArrayInputStream`、`InputStream`、`StandardCharsets`。
3. 类注释更新，说明 `credentials-path` 与 `credentials-json` 的关系：两者均未设置时回退到 ADC。
4. 在 `catalogSession` 方法中，读取 `credentialsJson` 后计算两个布尔标志 `useCredentialsPath` 和 `useCredentialsJson`。若两者同时为 true，抛出 `IllegalArgumentException("Cannot specify both %s and %s", ...)`。
5. 凭据加载分支从原来的二选一（path 或 ADC）改为三选一：
   - `useCredentialsPath`：使用 `FileInputStream` 读取文件（逻辑不变）。
   - `useCredentialsJson`：使用 `new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8))` 将 JSON 字符串转为输入流，再调用 `GoogleCredentials.fromStream(credentialsStream).createScoped(scopes)`，与文件方式对称。
   - 否则：使用 ADC。
6. 日志方面，JSON 方式仅记录 "Using Google credentials from json"（不记录凭据内容本身，避免敏感信息泄露）。

### `gcp/src/test/java/org/apache/iceberg/gcp/auth/TestGoogleAuthManager.java` (+42/-1 lines)

**修改目的**：验证 JSON 凭据加载与互斥校验。

**工作逻辑**：
1. 新增 `@Mock GoogleCredentials credentialsFromJson` 和字段 `credentialJson`（值为 `{"type": "service_account"}`），`beforeEach` 中将同一 JSON 写入临时文件供 path 测试使用。
2. 新增测试 `buildsCatalogSessionFromCredentialsJson`：设置 `GCP_CREDENTIALS_JSON_PROPERTY` 和自定义 scopes，mock `GoogleCredentials.fromStream(any(InputStream.class))` 返回 `credentialsFromJson`，断言会话类型正确且 `createScoped` 被以正确 scopes 调用。
3. 新增测试 `throwsIllegalArgumentExceptionOnMultipleCredentials`：同时设置 `credentials-path`（文件路径）和 `credentials-json`，断言抛出 `IllegalArgumentException`，消息为 "Cannot specify both gcp.auth.credentials-path and gcp.auth.credentials-json"。

## 总结

本提交为 GCP 认证新增了 `gcp.auth.credentials-json` 属性，允许用户以 JSON 字符串内联传递服务账号凭据，无需落盘文件，适配容器化与 serverless 部署场景。实现与既有文件路径方式对称，并增加了互斥校验防止配置冲突。测试与文档同步完善，整体改动简洁且安全（日志不记录凭据内容）。
