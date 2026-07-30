# 提交 0082：Infra: Update slack invite link (#8889)

## 提交信息

- **序号**：0082 / 4088
- **哈希**：1e5fcbf15ae8e5a66d102b9c0815f22130d7b8df
- **短哈希**：1e5fcbf15
- **日期**：2023-10-20
- **作者**：Ajantha Bhat
- **提交说明**：Infra: Update slack invite link (#8889)
- **PR/Issue**：#8889

## 总体目的

这个提交是一个纯基础设施维护变更，目的是更新 Apache Iceberg 社区在 GitHub Issue 模板中引用的 Slack 邀请链接。Slack 邀请链接通常具有时效性（会过期或被轮换），当旧链接失效后，新用户通过 GitHub Issue 提问模板里的链接将无法加入社区 Slack，影响社区支持渠道的可达性。

具体地，GitHub Issue 模板 `iceberg_question.yml` 中有一段 markdown 提示语，引导提问者"也可以在 Slack 上提问"。原链接 `zt-1znkcg5zm-7_FE~pcox347XwZE3GNfPg` 被替换为新的 `zt-2561tq9qr-UtISlHgsdY3Virs3Z2_btQ`。这类变更不涉及任何代码逻辑，但对社区运营的连续性至关重要——确保外部贡献者和用户能顺畅地进入社区沟通渠道。

## 如何达成设计目的

设计思路就是直接替换过期/失效的邀请 token。Slack 的 `join.slack.com/t/<workspace>/shared_invite/zt-<token>` 链接由 Slack 自动生成，token 失效后需由社区管理员重新生成并更新到所有引用位置。本提交更新了 GitHub Issue 模板这一处引用。

## 修改详情

### `.github/ISSUE_TEMPLATE/iceberg_question.yml`

**修改目的**：将问题模板中过期的 Slack 邀请链接替换为新的有效链接。

**工作逻辑**：在该 YAML 模板的 markdown 属性 `value` 字段中，把 Slack 共享邀请 URL 的 token 部分从 `zt-1znkcg5zm-7_FE~pcox347XwZE3GNfPg` 改为 `zt-2561tq9qr-UtISlHgsdY3Virs3Z2_btQ`。修改后，用户在 GitHub 上创建 Iceberg 问题时看到的提示语中的 Slack 链接将指向有效的邀请。

## 小结

这个提交维护了社区 Slack 邀请链接的有效性，保证用户通过 GitHub Issue 模板能正常加入社区沟通渠道。
