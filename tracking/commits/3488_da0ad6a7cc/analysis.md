# 提交 3488：OpenAPI: Promote the S3 signing endpoint to the main spec (#15450)

## 提交信息

- **序号**：3488 / 4088
- **哈希**：da0ad6a7cc2632594f04cb7872bc6f5fb158fd6b
- **短哈希**：da0ad6a7cc
- **日期**：2026-03-31 14:39:04 -0700
- **作者**：Alexandre Dutra
- **提交说明**：OpenAPI: Promote the S3 signing endpoint to the main spec (#15450)
- **PR/Issue**：#15450

## 总体目的

将 S3 远程签名端点从 AWS 模块特有的实现提升为 REST Catalog 主 OpenAPI 规范中的一等公民端点。这使其他存储提供商（GCS、Azure 等）未来可以复用相同的签名端点模式，无需重复定义 API。原有的 AWS 模块中的 `s3-signer-open-api.yaml` 被标记为弃用（待移除）。

## 如何达成设计目的

1. 在主 REST Catalog OpenAPI 规范中新增 `/v1/{prefix}/namespaces/{namespace}/tables/{table}/sign` 端点（POST 方法）。
2. 定义 `RemoteSignRequest`、`RemoteSignResult`、`RemoteSignResponse` 和 `MultiValuedMap` schema。
3. 新增 `provider` 请求参数，用于区分不同存储提供商的签名请求（向后兼容：不指定时默认为 `s3`）。
4. 将 AWS 模块中的 `s3-signer-open-api.yaml` 标记为 deprecated。
5. 更新 Python 客户端代码以包含新类型。
6. 更新 `LoadTableResult` 文档，将远程签名配置说明从引用外部 spec 改为引用主 spec 内的 `RemoteSignRequest` schema，并新增 `signer.endpoint` 和 `signer.uri` 配置说明。

## 修改详情

### `aws/src/main/resources/s3-signer-open-api.yaml` (+13/-6 lines)

**修改目的**：将原 S3 Signer API 标记为 deprecated。

**工作逻辑**：
- 添加 WARNING 注释指向新 spec。
- title、description、summary 等字段添加 `[DEPRECATED]` 前缀。
- POST 操作和 `S3Headers`、`S3SignRequest` schema 添加 `deprecated: true`。

### `build.gradle` (+1 line)

**修改目的**：为 S3 signer spec 验证任务添加 TODO 注释，标记待移除。

**工作逻辑**：在 `s3SignerSpec` 定义前添加 `// TODO delete once s3-signer-open-api.yaml is removed`。

### `open-api/Makefile` (+2 lines)

**修改目的**：为 S3 signer spec 的 validate 和 lint 任务添加 TODO 注释。

### `open-api/rest-catalog-open-api.py` (+45/-1 lines)

**修改目的**：在 Python 客户端中新增远程签名相关类型。

**工作逻辑**：
- 新增 `MultiValuedMap`（dict[str, list[str]] 的 RootModel）。
- 新增 `RemoteSignRequest`：包含 region、uri、method（枚举）、headers、properties、body、provider 字段。
- 新增 `RemoteSignResult`：包含 uri、headers 字段。
- 更新 `LoadTableResult` 文档，将 `s3.remote-signing-enabled` 说明从引用 `s3-signer-open-api.yaml` 改为引用 `RemoteSignRequest` schema，并新增 Remote Signing 配置段（`signer.endpoint`、`signer.uri`）。

### `open-api/rest-catalog-open-api.yaml` (+112/-7 lines)

**修改目的**：在主 REST Catalog OpenAPI spec 中新增远程签名端点和相关 schema。

**工作逻辑**：
- 新增 `/v1/{prefix}/namespaces/{namespace}/tables/{table}/sign` POST 端点，请求体为 `RemoteSignRequest`，响应为 `RemoteSignResponse`。
- 新增 `MultiValuedMap`、`RemoteSignRequest`、`RemoteSignResult` schema 定义。`RemoteSignRequest` 包含 `provider` 字段用于区分存储提供商（向后兼容默认 s3）。
- 新增 `RemoteSignResponse` response 定义。
- 更新 `ConfigResponse` 中 `remote-signing` 的说明，从引用外部 spec 改为引用主 spec 内的 `LoadTableResult` schema。
- 更新 `LoadTableResult` 的 `s3.remote-signing-enabled` 说明和新增 Remote Signing 配置段。

## 总结

API 规范提升提交。将 S3 远程签名端点从 AWS 特有实现提升为 REST Catalog 主规范的一等公民端点，新增通用的 `/sign` 端点和 `provider` 参数以支持多存储提供商。原 AWS 模块的 spec 被标记为 deprecated。这为 GCS、Azure 等其他存储提供商复用签名端点模式奠定了基础。
