# 提交 3171：site: add docs about Requesting Slack Integrations (#15170)

## 提交信息

- **序号**：3171 / 4088
- **哈希**：42a959791462859b3cb143191bf0e1696bb91807
- **短哈希**：42a959791
- **日期**：2026-01-28
- **作者**：Kevin Liu
- **提交说明**：site: add docs about Requesting Slack Integrations (#15170)
- **PR/Issue**：#15170

## 总体目的

Apache Iceberg 社区维护着一个 Slack 工作区（apache-iceberg.slack.com）用于社区交流。随着社区发展，成员可能希望为该 Slack 工作区引入第三方应用或集成（例如机器人、通知、自动化工具等），但此前项目文档中并未说明如何申请此类集成，导致流程不透明、操作随意，可能带来安全与治理风险。

本次提交由 Kevin Liu 通过 PR #15170 在社区文档 `site/docs/community.md` 的 Slack 章节下新增一个小节 "Requesting Slack Integrations"，明确告知贡献者：若要为 Apache Iceberg Slack 工作区申请新的应用或集成，需向 dev 邮件列表发送邮件，并提供三项必要信息——要添加的内容（名称与描述）、添加的理由（对社区的益处）、所需权限（访问级别与权限）。这一流程允许社区进行快速共识检查后再安装应用，既保障了开放透明，又建立了一道轻量级的安全审核门槛。

该文档改动的实际指导价值在于：为新成员提供了清晰的入口与规范，避免私下安装未审核应用；为社区管理者提供了审核依据；与 Apache 基金会崇尚"邮件列表公开讨论"的治理文化一致。

## 如何达成设计目的

在 `community.md` 文件中 Slack 工作区介绍段落之后、"Issues" 章节之前，插入一个 `#### Requesting Slack Integrations` 四级标题小节，包含一段说明文字与一个包含三项要点的无序列表，并使用加粗短语强调每项要点的性质。

## 修改详情

### `site/docs/community.md` (+10/-0 lines)

**修改目的**：新增 Slack 集成申请流程说明。

**工作逻辑**：
在 Slack 工作区链接说明段落之后插入新小节，内容为：
- 标题 `#### Requesting Slack Integrations`。
- 一段引导文字，说明需向 [dev mailing list](#mailing-lists) 发邮件申请，链接锚定到同页的邮件列表章节。
- 三项加粗要点的无序列表：**What you want to add**（应用名称与描述）、**Why you want to add it**（对社区的益处）、**What permissions does it need**（所需访问级别与权限）。
- 结尾说明"这允许社区在安装应用前进行快速共识检查"。

该改动为纯文档新增，无代码逻辑变更。

## 总结

本次提交为 Iceberg 社区文档补充了 Slack 集成申请流程说明，明确了通过 dev 邮件列表提交申请并提供应用信息、理由与权限三项要素的规范流程。该文档提升了社区治理的透明度与安全性，对新成员和社区管理者均具有实际指导价值。
