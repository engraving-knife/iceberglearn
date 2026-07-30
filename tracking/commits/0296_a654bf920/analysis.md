# 提交 0296：Add Description on Using a Separate Authorization Server (#8998)

## 提交信息

- **序号**：0296 / 4088
- **哈希**：a654bf920a24ece5026309d4e26f9c71b5136bc5
- **短哈希**：a654bf920
- **日期**：2023-12-21 08:07:37 -0800
- **作者**：Sung Yun <107272191+syun64@users.noreply.github.com>
- **提交说明**：Add Description on Using a Separate Authorization Server (#8998)
- **PR/Issue**：#8998

## 总体目的

Iceberg 的 REST Catalog 规范通过 `open-api/rest-catalog-open-api.yaml` 这一 OpenAPI 文档对外定义了完整的接口契约，其中安全模型（`securitySchemes`）部分使用 OAuth2 的 `clientCredentials` 流程来进行鉴权。在该 YAML 的原始描述中，`tokenUrl` 被固定写为 `/v1/oauth/tokens`，即假定 REST Catalog Server 自身同时承担了 Authorization Server（授权服务器）的职责，可以在该路径上颁发 token。

然而在实际生产部署中，鉴权与资源服务往往是要解耦的：很多组织已经拥有独立的 Authorization Server（例如 Keycloak、Auth0、Okta、企业内部的 IAM 等），并由这些外部系统负责颁发 OAuth2 token，REST Catalog Server 只作为 Resource Server 来校验这些 token。原有的 OpenAPI 描述对此场景没有任何说明，使用者很难从规范中直接判断"是否可以把 `tokenUrl` 改成外部授权服务器地址"这件事是被允许的，以及改了之后整个调用链路应当如何工作。

本提交的目的就是在 OAuth2 安全方案的描述中补充一段明确的说明，告诉实现方和使用方：当采用独立授权服务器时，应把 `tokenUrl` 替换为外部授权服务器的完整 token 路径，再用该服务器颁发的 token 去访问规范中定义的资源。这是典型的"文档澄清"类改动，本身不改变任何代码行为，但显著降低了规范使用时的歧义。

从动机上看，这种补充与 Iceberg REST Catalog 在多租户、企业级场景下的推广相吻合：用户经常需要在已有 IAM 体系下接入 Iceberg，规范如果不写清楚就会被反复询问或被错误实现，因此在 OpenAPI 中预先给出指引是一种低成本、高收益的合规性改进。

## 如何达成设计目的

整体思路非常直接：在 `OAuth2` 安全方案的 `description` 字段末尾追加一段说明文字，明确"使用独立授权服务器"的处置方式——把 `tokenUrl` 替换为外部授权服务器的完整 token 路径，再用得到的 token 访问本规范中定义的资源。文字放在已有的 401/403 错误处理说明之后，与原有"未授权请求如何响应"的语义连贯衔接，构成一段完整的安全方案行为契约。这种方式既不动 `tokenUrl` 的默认值，也不动 `flows` 的结构，仅通过文档说明就把"可替换为外部授权服务器"这一约定纳入规范，实现成本极低。

## 修改详情

### `open-api/rest-catalog-open-api.yaml`

**修改目的**：在 OAuth2 安全方案的描述中补充"使用独立授权服务器"的指引，消除部署歧义。

**工作逻辑**：

- 该文件是 Iceberg REST Catalog 的 OpenAPI 规范定义文件。`components.securitySchemes.OAuth2` 这一项定义了整个 REST API 使用的 OAuth2 鉴权方案：`type: oauth2`，`flows.clientCredentials.tokenUrl: /v1/oauth/tokens`，并配了一段 `description` 说明该方案的用途与未授权时的响应行为。
- 本次改动在该 `description` 已有的"For unauthorized requests, services should return an appropriate 401 or 403 response. Implementations must not return altered success (200) responses when a request is unauthenticated or unauthorized."段落之后，新增一段（中间以一个空行分隔）："If a separate authorization server is used, substitute the tokenUrl with the full token path of the external authorization server, and use the resulting token to access the resources defined in the spec."
- 这段文字明确了两点：(1) 允许使用独立授权服务器；(2) 实际操作是把 `tokenUrl` 替换为外部授权服务器的完整 token 路径（例如 `https://iam.example.com/oauth2/token`），客户端从这个外部地址获取 token 之后，再携带该 token 调用本规范定义的 REST Catalog 资源接口。
- 通过把这段说明放进 `description`，OpenAPI 工具链生成的客户端/服务端文档（Swagger UI 等）会自动渲染出这段说明，让任何查阅规范的人都能看到该约定，无需阅读额外的 wiki 或 README，提升了规范的自解释能力。
- 改动只新增 4 行，未删除任何内容，对现有实现完全向后兼容：仍把 `/v1/oauth/tokens` 作为默认 tokenUrl，是否替换由部署方自行决定。

## 小结

这是一个小而精准的文档改进提交：通过在 OpenAPI 规范的 OAuth2 安全方案描述中追加"使用独立授权服务器时如何替换 `tokenUrl`"的说明，把此前隐含的部署模式显式写入规范，降低了企业级部署场景下的实现歧义，并与 Iceberg REST Catalog 在外部 IAM 集成方向上的演进保持一致。改动本身不引入任何代码行为变化，风险极低，但显著提升了规范的可读性与可落地性。
