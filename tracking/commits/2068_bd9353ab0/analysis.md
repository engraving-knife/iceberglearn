# 提交 2068：OpenAPI: Use more clear language in recommending error responses (#12376)

## 提交信息

- **序号**：2068 / 4088
- **哈希**：bd9353ab0bdd88f588e4e3be5773e25ce52faf51
- **短哈希**：bd9353ab0
- **日期**：2025-04-30 21:26:31 -0400
- **作者**：Sung Yun
- **提交说明**：OpenAPI: Use more clear language in recommending error responses (#12376)
- **PR/Issue**：#12376

## 总体目的

Iceberg REST Catalog 的 OpenAPI 规范（`rest-catalog-open-api.yaml`）中对认证相关错误响应的描述较为含糊，未明确指引 REST Catalog 实现者在何种场景下应返回 401 `UnauthorizedResponse`，以及 419 `AuthenticationTimeoutResponse` 与 401 之间的关系。这导致不同实现可能在 token 过期/撤销/格式错误等场景下返回不同的状态码，影响客户端的统一处理逻辑。

本提交改写 `UnauthorizedResponse` 与 `AuthenticationTimeoutResponse` 两个响应组件的 `description`，使用 RFC 风格的关键词（SHOULD/MAY）明确：
- 401 `UnauthorizedResponse` 应在 access token 过期、被撤销、格式错误或其它无效原因时返回；客户端可以请求新 token 并重试。
- 419 `AuthenticationTimeoutResponse` 是可选响应，用于 token 过期场景；但 401 应优先于 419 用于 token 过期；客户端同样可以请求新 token 并重试。

从而引导实现者统一使用 401 作为主要的认证失败响应，419 仅作为可选，提升跨实现的互操作性。

## 如何达成设计目的

通过修改 OpenAPI YAML 中两个响应组件的 `description` 字段，使用 SHOULD/MAY 等 RFC 2119 风格关键词给出明确建议，并补充客户端重试策略说明。不修改 schema 或状态码本身，仅澄清语义。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (修改, +6/-2 lines)

**修改目的**：澄清 401 与 419 认证错误响应的使用场景与优先级。

**工作逻辑**：
- `UnauthorizedResponse.description`：由 "Unauthorized. Authentication is required and has failed or has not yet been provided." 改为 "Unauthorized. The REST Catalog SHOULD respond with the 401 UnauthorizedResponse when the access token provided is expired, revoked, malformed, or invalid for other reasons. The client MAY request a new access token and retry the request."。明确列出触发场景（过期/撤销/格式错误/其它无效）与客户端重试建议。
- `AuthenticationTimeoutResponse.description`：由 "Credentials have timed out. If possible, the client should refresh credentials and retry." 改为 "This is an optional status response type that the REST Catalog can issue when the token has expired. The client MAY request a new access token and retry the request. 401 UnauthorizedResponse SHOULD be preferred over this response type on token expiry."。明确该响应是可选的、用于 token 过期、并指出 401 应优先用于 token 过期场景。

## 总结

纯规范文档变更，改写 OpenAPI 中 `UnauthorizedResponse` 与 `AuthenticationTimeoutResponse` 的描述，使用 SHOULD/MAY 关键词明确 401 应作为主要认证失败响应（覆盖 token 过期/撤销/格式错误等场景），419 为可选且 401 应优先用于 token 过期。提升 REST Catalog 实现间的一致性与客户端重试逻辑的可预测性。
