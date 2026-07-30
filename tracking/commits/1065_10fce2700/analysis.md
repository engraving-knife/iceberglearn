# 提交 1065：Spec: Clarify in REST spec that server implementations of commit endpoints must fail with 400 if any unknown updates or requirements are received (#10848)

## 提交信息

- **序号**：1065 / 4088
- **哈希**：10fce2700e235c9a1f505fcbe14ba435b20283b9
- **短哈希**：10fce2700
- **日期**：2024-08-16 17:25:34 -0600
- **作者**：Amogh Jahagirdar <amoghj@apache.org>
- **提交说明**：Spec: Clarify in REST spec that server implementations of commit endpoints must fail with 400 if any unknown updates or requirements are received (#10848)
- **PR/Issue**：#10848

## 总体目的

Iceberg 的 REST Catalog 规范（`open-api/rest-catalog-open-api.yaml`）定义了两种"提交"类型的端点：单表提交 `POST /v1/{prefix}/namespaces/{namespace}/tables/{table}`（`operationId: updateTable`）和事务提交 `POST /v1/{prefix}/transactions/commit`。它们的请求体由两部分组成：`requirements`（提交前需要校验的断言，例如 `assert-ref-snapshot-id`）和 `updates`（对表元数据要做的变更，例如 `AppendSnapshotUpdate`、`AddSchemaUpdate`）。

随着规范演进，`requirements` 与 `updates` 的可枚举类型会不断扩展，不同服务端实现可能对"未识别的 update/requirement 类型"采取不同的处理策略（有的会忽略、有的会 500、有的会 400）。这种歧义会让客户端在跨实现时行为不一致，也违背 Iceberg REST 规范追求的强一致性契约。

本提交的目的是在 OpenAPI 规范中显式澄清：当服务端在提交请求中收到任何未知的 update 或 requirement 时，必须以 HTTP 400 失败，而不是静默忽略或抛出其他错误码。这样客户端可以可靠地依赖"提交要么完全被理解并执行、要么被明确拒绝"的语义，避免出现"客户端以为发送了某种 requirement/update，服务端实际跳过校验或变更"的隐患。

## 如何达成设计目的

实现方式是在 `updateTable` 和 `commitTransaction` 两个端点的 description 文本中，紧跟在 "Requirements are assertions ... will be validated before attempting to make and commit changes" 这段之后，统一追加一句明确的要求：

> Server implementations are required to fail with a 400 status code if any unknown updates or requirements are received.

这是纯文档/规范层面的澄清，不引入任何代码、schema 或行为变更。两处文案措辞一致，保持规范风格统一。由于 OpenAPI 中 `responses` 段落早已声明了 `400: $ref: '#/components/responses/BadRequestErrorResponse'`，该澄清只是把"什么情况下必须返回 400"这一行为契约写进了 description，与已有的响应定义相呼应，不冲突。

## 修改详情

### `open-api/rest-catalog-open-api.yaml`

**修改目的**：在 `updateTable` 与 `commitTransaction` 两个端点的描述文本中显式声明，当收到未知的 updates 或 requirements 时，服务端必须返回 400。

**工作逻辑**：

- 在 `updateTable` 端点（`POST /v1/{prefix}/namespaces/{namespace}/tables/{table}`，约 line 686 附近）的 description 中，紧跟在 "Requirements are assertions ... named ref's snapshot ID has a certain value." 之后，新增两行：
  ```
  Server implementations are required to fail with a 400 status code
  if any unknown updates or requirements are received.
  ```
- 在 `commitTransaction` 端点（`POST /v1/{prefix}/transactions/commit`，约 line 988 附近）的 description 中，紧跟在同一句关于 requirements 的描述之后，新增同样的两行（注意此处原文中两个段落之间原本是空行，本次提交把那行空行替换为新增的两行说明）。

两处新增内容完全相同，措辞与缩进与各自上下文一致（前者使用 8 空格缩进，后者使用 10 空格缩进，匹配各自 YAML 层级）。

## 小结

- **成效**：在 Iceberg REST Catalog OpenAPI 规范中明确了两条提交端点的服务端契约——遇到未知的 updates 或 requirements 时必须返回 400，而不是静默忽略或返回其他错误码。这统一了多服务端实现之间的行为，降低了客户端跨实现的兼容性风险。
- **影响范围**：仅修改 `open-api/rest-catalog-open-api.yaml` 一个文件，新增 4 行（每端点 2 行），删除 1 行（commitTransaction 处的空行被替换）。无代码、配置、构建或测试改动，纯规范文档变更。
- **回迁到 1.4.x 的注意事项**：纯规范澄清，回迁到 1.4.x 风险极低，可以直接 cherry-pick；需注意 1.4.x 上 `rest-catalog-open-api.yaml` 的对应位置文案是否与 main 分支该提交前的状态一致，若 1.4.x 已对该段落做过其他文案调整，cherry-pick 时可能需要手工解决冲突。该澄清对 1.4.x 既有的服务端实现行为是一种"约束声明"，并不会改变已正确实现的服务端行为，反而可以作为 1.4.x 服务端实现者校验自身行为的依据。
