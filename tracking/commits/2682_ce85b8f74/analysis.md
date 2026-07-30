# 提交 2682：Docs: Add REST catalog authentication properties (#14065)

## 提交信息

- **序号**：2682 / 4088
- **哈希**：ce85b8f74ffd288daadcd8b3f0879a3788304e41
- **短哈希**：ce85b8f74
- **日期**：2025-09-25 08:24:27 +0200
- **作者**：Piyush Dubey
- **提交说明**：Docs: Add REST catalog authentication properties (#14065)
- **PR/Issue**：#14065

## 总体目的

本提交为 Iceberg 官方文档的配置参考页面（`configuration.md`）新增 REST Catalog 认证属性的完整文档。此前文档中缺少 REST Catalog 支持的各种认证机制的配置说明，用户需要查阅源码或散落的 wiki/issue 才能了解如何配置 Basic、OAuth2、SigV4、Google 等认证方式。

REST Catalog 是 Iceberg 的重要 catalog 实现类型，通过 HTTP REST API 与 catalog 服务通信。由于涉及网络访问，认证是安全部署的关键环节。Iceberg 的 REST Catalog 支持多种认证机制，每种机制有不同的配置属性。本提交将这些属性以表格形式系统性地 documenting，按认证类型分组，方便用户查阅。

## 如何达成设计目的

在 `docs/docs/configuration.md` 文件中，于 catalog 属性通用说明之后、Lock catalog 属性之前，新增"REST Catalog auth properties"章节，包含一个概述和四个子章节（REST 通用认证属性、OAuth2 属性、Google 属性），以 Markdown 表格列出每个属性的名称、默认值和描述。

## 修改详情

### `docs/docs/configuration.md` (+37/-0 lines)

**修改目的**：新增 REST Catalog 认证属性文档。

**工作逻辑**：新增内容分为以下几个部分：
- **概述段落**：说明这些 catalog 属性配置 REST catalog 的认证，支持 Basic、OAuth2、SigV4 和 Google 四种认证机制。
- **REST auth properties 表格**：列出通用认证属性，包括 `rest.auth.type`（认证类型，可选 none/basic/oauth2/sigv4/google）、`rest.auth.basic.username`/`rest.auth.basic.password`（Basic 认证凭据）、`rest.auth.sigv4.delegate-auth-type`（SigV4 签名后委托的认证类型，默认 oauth2）。
- **OAuth2 auth properties 表格**：列出 OAuth2 认证相关属性，包括 `token`（Bearer 令牌）、`credential`（client_id:client_secret 格式的凭据，用于客户端凭证流程）、`oauth2-server-uri`（OAuth2 令牌端点 URI）、`token-expires-in-ms`（令牌过期时间，默认 1 小时）、`token-refresh-enabled`（是否自动刷新令牌）、`token-exchange-enabled`（是否使用令牌交换流程）、`scope`（OAuth2 范围，默认 catalog）、`audience`（令牌受众）、`resource`（资源参数）。文档说明 `token` 和 `credential` 二者必须提供其一。
- **Google auth properties 表格**：列出 Google 认证属性，包括 `gcp.auth.credentials-path`（服务账号 JSON 密钥文件路径，默认使用应用默认凭据 ADC）和 `gcp.auth.scopes`（OAuth 范围列表，默认 `https://www.googleapis.com/auth/cloud-platform`）。

## 总结

这是一次纯文档提交，为 Iceberg 官方配置文档补充了 REST Catalog 认证属性的完整参考。文档按 Basic/OAuth2/SigV4/Google 四种认证机制分组，以表格形式清晰列出每个属性的名称、默认值和描述，显著降低了用户配置 REST Catalog 认证时的查阅成本。不涉及任何代码变更。
