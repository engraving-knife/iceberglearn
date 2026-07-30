# 提交 2578：Only warn if OAuth2 server URI is not set (#13741)

## 提交信息

- **序号**：2578 / 4088
- **哈希**：65dd3de4ae5fd9ee5244893ed8e05f2f5679dd71
- **短哈希**：65dd3de4a
- **日期**：2025-08-29 11:22:31 -0700
- **作者**：Raghav Mahajan
- **提交说明**：Only warn if OAuth2 server URI is not set (#13741)
- **PR/Issue**：#13741

## 总体目的

此次提交修改 Iceberg REST 客户端 OAuth2 认证的告警逻辑，使告警仅在用户完全未配置 OAuth2 server URI 时触发，而不是在使用了"已弃用的 token endpoint"时就触发。

原先的逻辑（`warnIfDeprecatedTokenEndpointUsed` + `usesDeprecatedTokenEndpoint`）会判断 OAuth2 server URI 是否为相对路径或与 catalog URI 同主机，若是则视为使用了"已弃用的 token endpoint"并发出警告。这意味着即使用户已经显式配置了 OAuth2 server URI，只要它恰好与 catalog URI 同主机或是相对路径，就会持续收到警告。这种过度告警对用户造成困扰——尤其是当用户有意将 OAuth2 endpoint 与 catalog 部署在同一主机时。

修改后的逻辑简化为：只要 properties 中不包含 `OAUTH2_SERVER_URI` 键，且用户提供了 token 或 credential，才发出警告提示用户配置 OAuth2 endpoint。一旦用户显式配置了 `OAUTH2_SERVER_URI`（无论是否同主机），警告就消失。这更符合"提醒用户显式配置"的初衷。

## 如何达成设计目的

- 将 `warnIfDeprecatedTokenEndpointUsed` 重命名为 `warnIfOAuthServerUriNotSet`，判定条件从 `usesDeprecatedTokenEndpoint(properties)` 改为 `!properties.containsKey(OAuth2Properties.OAUTH2_SERVER_URI)`。
- 删除 `usesDeprecatedTokenEndpoint` 方法（该方法包含相对路径和同主机判断逻辑）。
- 在警告消息中追加一个空格以修正文案拼接（"release. " -> "release. " + "It is recommended..."）。
- `initSession` 调用处同步改为新方法名。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/auth/OAuth2Manager.java` (+4/-14)

**修改目的**：简化 OAuth2 server URI 缺失告警的触发条件。

**工作逻辑**：
- `initSession` 中将 `warnIfDeprecatedTokenEndpointUsed(properties)` 改为 `warnIfOAuthServerUriNotSet(properties)`。
- `warnIfOAuthServerUriNotSet` 方法：当 `!properties.containsKey(OAUTH2_SERVER_URI)` 且存在 token 或 credential 时，发出警告，提示缺少 OAuth2 server URI 配置并默认使用回退 endpoint，建议通过 `OAUTH2_SERVER_URI` 属性显式配置，并指向 issue #10537。
- 删除 `usesDeprecatedTokenEndpoint` 方法，不再判断相对路径或同主机情况。

## 总结

此次提交简化了 OAuth2 认证的告警逻辑：从"使用了已弃用 token endpoint（相对路径或同主机）时告警"改为"仅当完全未设置 OAuth2 server URI 时告警"。删除了 `usesDeprecatedTokenEndpoint` 方法和相关判定逻辑，避免对已显式配置但与 catalog 同主机的 OAuth2 endpoint 产生过度告警，提升用户体验。
