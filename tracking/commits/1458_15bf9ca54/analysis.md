# 提交 1458：Core: Fix warning message for deprecated OAuth2 server URI (#11694)

## 提交信息

- **序号**：1458 / 4088
- **哈希**：15bf9ca54f4fe3ef665e2be641e6fe6f28a995d3
- **短哈希**：15bf9ca54
- **日期**：2024-12-04（Wed Dec 4 20:09:22 2024 +0900）
- **作者**：Yuya Ebihara <ebyhry@gmail.com>
- **提交说明**：Core: Fix warning message for deprecated OAuth2 server URI (#11694)
- **PR/Issue**：#11694

## 总体目的

Iceberg REST Catalog 在初始化时，如果用户配置了 `credential` 或初始 `token`（即需要 OAuth2 认证），但没有显式配置 OAuth2 server URI（`OAuth2Properties.OAUTH2_SERVER_URI`），会自动回退到以 catalog 的 `URI` 为基础拼接 `v1/oauth/tokens` 端点作为 OAuth2 token 端点。这是一个 deprecated 的兼容行为，未来会移除（参见 #10537），因此在回退时打印一条 `WARN` 日志提醒用户显式配置。

该警告消息中拼接 URL 的方式有 bug：

- 原格式串为 `"defaults to {}{}."`，两个占位符分别是 `props.get(CatalogProperties.URI)`（用户配置的 catalog URI）和 `ResourcePaths.tokens()`（固定返回 `"v1/oauth/tokens"`）；
- 如果用户配置的 URI **不带尾部斜杠**（如 `https://rest.example.com/api`），拼接结果为 `https://rest.example.com/apiv1/oauth/tokens`——缺少 `/` 分隔符，看起来像一个错误的 URL；
- 如果 URI **带尾部斜杠**（如 `https://rest.example.com/api/`），结果为 `https://rest.example.com/api/v1/oauth/tokens`，恰好正确——但这是"碰巧"对的情况。

本提交修正格式串为 `"defaults to {}/{}."`（显式加 `/` 分隔符），并对 URI 参数先调用 `RESTUtil.stripTrailingSlash()` 去掉尾部斜杠，保证无论用户配置的 URI 是否带尾部 `/`，警告消息中的 URL 都格式正确（恰好一个 `/` 分隔）。

## 如何达成设计目的

修改 `RESTSessionCatalog` 中 `LOG.warn` 调用的格式串与第一个参数：

1. 格式串 `{}{}` → `{}/{}`：在 URI 与 tokens 路径之间显式插入 `/`；
2. URI 参数从 `props.get(CatalogProperties.URI)` 改为 `RESTUtil.stripTrailingSlash(props.get(CatalogProperties.URI))`：先去掉 URI 末尾的所有 `/`，避免与新增的 `/` 分隔符叠加产生双斜杠。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java`（修改，2 行变更）

**修改目的**：修正 OAuth2 server URI 回退警告消息中的 URL 拼接，使其在任意 URI 配置下都格式正确。

**工作逻辑**：

修改前：
```java
LOG.warn(
    "Iceberg REST client is missing the OAuth2 server URI configuration and defaults to {}{}. "
        + "This automatic fallback will be removed in a future Iceberg release."
        + "It is recommended to configure the OAuth2 endpoint using the '{}' property to be prepared. "
        + "This warning will disappear if the OAuth2 endpoint is explicitly configured. "
        + "See https://github.com/apache/iceberg/issues/10537",
    props.get(CatalogProperties.URI),
    ResourcePaths.tokens(),
    OAuth2Properties.OAUTH2_SERVER_URI);
```

修改后：
```java
LOG.warn(
    "Iceberg REST client is missing the OAuth2 server URI configuration and defaults to {}/{}. "
        + "This automatic fallback will be removed in a future Iceberg release."
        + "It is recommended to configure the OAuth2 endpoint using the '{}' property to be prepared. "
        + "This warning will disappear if the OAuth2 endpoint is explicitly configured. "
        + "See https://github.com/apache/iceberg/issues/10537",
    RESTUtil.stripTrailingSlash(props.get(CatalogProperties.URI)),
    ResourcePaths.tokens(),
    OAuth2Properties.OAUTH2_SERVER_URI);
```

关键点：
- `ResourcePaths.tokens()` 固定返回 `"v1/oauth/tokens"`（不以 `/` 开头）；
- `RESTUtil.stripTrailingSlash` 会循环去掉 URI 末尾的所有 `/`（如 `https://host/api///` → `https://host/api`）；
- 修改后，无论 URI 是否带尾部斜杠，消息中的 URL 都是 `https://host/api/v1/oauth/tokens`（恰好一个 `/` 分隔）。

该警告触发的条件（参见上下文）：用户同时配置了 `credential` 或初始 `token`、且未配置 `OAuth2Properties.OAUTH2_SERVER_URI`、且未启用 SigV4（`rest.sigv4-enabled = false`）时，REST Catalog 会回退到 `<catalog-uri>/v1/oauth/tokens` 作为 token 端点，并打印此警告。

## 小结

- **成效**：修复了 OAuth2 回退警告消息中 URL 拼接的格式 bug，使消息在 URI 不带尾部斜杠时也能显示正确的 URL，避免误导用户。这是一个纯日志文本修复，不影响任何功能逻辑。
- **影响范围**：仅 `core` 模块的 `RESTSessionCatalog` 一处 `LOG.warn` 调用，2 行变更。
- **回迁到 1.4.x 的注意事项**：可安全 cherry-pick，无风险。需确认 1.4.x 的 `RESTUtil.stripTrailingSlash` 方法签名与 main 一致（该方法已存在且稳定）。此修复仅影响日志输出文本，不影响 OAuth2 token 获取的实际行为（实际 token 端点拼接逻辑在 `ResourcePaths` 中，不由此警告代码决定）。
