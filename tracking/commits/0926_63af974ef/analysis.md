# 提交 0926：OpenAPI: Deprecate `oauth/tokens` endpoint (#10603)

## 提交信息

- **序号**：0926 / 4088
- **哈希**：63af974efe51486c89bff8df5416781ab3181976
- **短哈希**：63af974ef
- **日期**：2024-07-12 11:05:10 +0200
- **作者**：Robert Stupp <snazy@snazy.de>
- **提交说明**：OpenAPI: Deprecate `oauth/tokens` endpoint (#10603)
- **PR/Issue**：#10603（关联 #10537）

## 总体目的

Iceberg REST Catalog 规范中内置了一个 `POST /oauth/tokens` 端点，用于 OAuth2 客户端凭证流和 token 交换流。然而把 OAuth2 token 端点直接"寄生"在 catalog 的 REST 接口上存在安全隐患：catalog 服务和身份提供者（IdP）职责混淆，且默认行为下客户端会把 credential 发送到 catalog URI 拼接出的 `/oauth/tokens`，这与生产环境应使用独立、专用 OAuth2 服务器的最佳实践相悖。

本提交是 issue #10537（"Security improvements in the Iceberg REST specification"）的"M1"阶段，目的是正式在 REST 规范中将 `oauth/tokens` 端点及其相关 schema 标记为**已废弃、计划移除**（DEPRECATED for REMOVAL），并在 Java 客户端 `RESTSessionCatalog` 中针对"配置了 token/credential 但未显式设置 `oauth2-server-uri`"的场景打印警告日志，引导用户显式配置独立的 OAuth2 端点。规范层面明确：自 Iceberg (Java) 1.6.0 起废弃，将在 Iceberg (Java) 2.0 中从规范移除。

## 如何达成设计目的

分两部分：

1. **规范侧**：在 `rest-catalog-open-api.yaml` 和对应的 Python 模型 `rest-catalog-open-api.py` 中，给 `/v1/oauth/tokens` POST 操作以及 `OAuthClientCredentialsRequest`、`OAuthTokenExchangeRequest`、`OAuthTokenRequest`、`OAuthError`、`OAuthTokenResponse` 等 schema 添加 `deprecated: true` 标记，并在 description 中明确"DEPRECATED for REMOVAL"、废弃起始版本（1.6.0）、移除版本（2.0）以及引导链接。
2. **客户端侧**：在 `RESTSessionCatalog` 初始化时，若用户提供了 init token 或 credential，但未显式设置 `oauth2-server-uri`，且未启用 sigv4，则 `LOG.warn` 提示当前正回退到 `<catalog-uri>/oauth/tokens`、该回退将在未来移除、建议显式配置 `oauth2-server-uri`。随后仍保留原有回退逻辑（`props.getOrDefault(OAUTH2_SERVER_URI, ResourcePaths.tokens())`）以保持兼容。

## 修改详情

### `open-api/rest-catalog-open-api.yaml`

**修改目的**：在 OpenAPI 规范中标记 `oauth/tokens` 端点及相关 schema 为已废弃。

**工作逻辑**：
- `/v1/oauth/tokens` 的 POST 操作 `summary` 改为 `Get a token using an OAuth2 flow (DEPRECATED for REMOVAL)`，增加 `deprecated: true`，并在 description 前加入说明：不推荐实现该端点（除非了解安全影响），鼓励客户端显式配置 `oauth2-server-uri`，废弃自 1.6.0、2.0 移除，并引用 issue #10537。
- 给 `OAuthClientCredentialsRequest`、`OAuthTokenExchangeRequest`、`OAuthTokenRequest`、`OAuthError`、`OAuthTokenResponse` 五个 schema 均加上 `deprecated: true` 及"DEPRECATED for REMOVAL"描述。

### `open-api/rest-catalog-open-api.py`

**修改目的**：同步 Python pydantic 模型中的废弃标记。

**工作逻辑**：在 `OAuthClientCredentialsRequest`、`OAuthTokenExchangeRequest`（docstring）、`OAuthError`、`OAuthTokenResponse` 的 docstring 中加入"DEPRECATED for REMOVAL"说明；并为 `OAuthTokenRequest.__root__` 字段添加 `description` 显式标注废弃。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java`

**修改目的**：在客户端初始化时对"隐式回退到 catalog 自带 oauth/tokens"的行为发出警告。

**工作逻辑**：在 `initialize` 中提前计算 `hasInitToken`、`hasCredential`；若 `props` 未包含 `OAuth2Properties.OAUTH2_SERVER_URI`，且（`hasInitToken || hasCredential`），且未启用 `rest.sigv4-enabled`，则 `LOG.warn` 提示：客户端缺少 OAuth2 server URI 配置、默认回退到 `<uri>/oauth/tokens`、该自动回退将在未来版本移除、建议用 `oauth2-server-uri` 显式配置、引用 issue #10537。后续逻辑用 `hasCredential` 替代原先的 `credential != null && !credential.isEmpty()` 内联判断，行为等价。回退默认值仍为 `ResourcePaths.tokens()`，保持兼容。

## 小结

- **成效**：在 REST Catalog 规范中正式废弃 `oauth/tokens` 端点及相关 schema（标记 `deprecated`、说明 1.6.0 起废弃、2.0 移除），并在 Java 客户端对隐式回退行为发出警告，推动用户显式配置独立的 OAuth2 server URI，提升安全性。
- **影响范围**：`open-api/rest-catalog-open-api.yaml`、`open-api/rest-catalog-open-api.py`（规范/文档层）、`core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java`（客户端初始化日志），3 个文件 +69/-4，不改变运行时行为（仅新增警告日志与规范标记）。
- **回迁到 1.4.x 的注意事项**：属于规范标记与警告日志，回迁风险低。但需注意：1.4.x 早于 1.6.0，若回迁到 1.4.x 会与"自 1.6.0 起废弃"的版本声明产生语义冲突（1.4.x 上尚未"废弃"却打印废弃警告可能引起用户困惑）。建议谨慎回迁：规范侧的 `deprecated` 标记可回迁以提前告知用户；客户端警告日志若回迁，需调整文案中提到的版本号或明确为"前瞻性警告"。sigv4 相关属性 `rest.sigv4-enabled` 需确认 1.4.x 已支持。
