# 提交 2478：Docs: Meetup Guidelines (#13520)

## 提交信息

- **序号**：2478 / 4088
- **哈希**：c8c61a3f6b60499f09e5047c5c9d8a3c02d9fb72
- **短哈希**：c8c61a3f6
- **日期**：2025-08-09 09:50:00 -0700
- **作者**：Danica Fine
- **提交说明**：Docs: Meetup Guidelines (#13520)
- **PR/Issue**：#13520

## 总体目的

该提交重新组织了 Iceberg 社区文档（community.md），新增了 Apache Iceberg Meetup 举办指南，并重新编排了社区页面结构，使社区参与信息更加清晰完整。

随着 Apache Iceberg 社区在全球范围内的增长，越来越多社区志愿者希望举办 Iceberg Meetup。此前社区文档缺少关于如何举办 Meetup 的指导，也没有明确的命名规范和行为准则。该提交补充了 Meetup 举办指南，明确了命名要求、内容规范和社区准则，同时将社区页面重新组织为"Join the Discussion"（邮件列表、Slack、Issues）和"Connect with Community Events"（Meetup 指南、社区日历）等清晰板块。

## 如何达成设计目的

通过对 `site/docs/community.md` 的大幅重写来实现：

1. **重新组织页面结构**：将原有内容重组为更清晰的层次，新增"Join the Discussion"和"Connect with Community Events"两个主要板块。

2. **新增 Meetup 举办指南**：明确命名规范（Apache Iceberg Meetup <地理位置>）、品牌合规要求（遵循 ASF 商标指南）、内容要求（vendor-neutral、至少两家公司演讲）、以及社区准则遵守要求。

3. **更新社区日历描述**：丰富了两个日历的描述内容，明确 Dev Events 日历包含 triweekly sync、Python/Rust/Go 子项目 sync、Catalog Community Sync 等。

4. **补充开头欢迎语**：增加引导性文字，帮助新社区成员了解如何参与。

## 修改详情

### `site/docs/community.md` (+47/-30 lines)

**修改目的**：重组社区文档结构并新增 Meetup 举办指南。

**工作逻辑**：

1. **开头欢迎语**（第 22-24 行）：新增引导文字"We're glad that you're interested in joining the Apache Iceberg community!"，介绍文档内容。

2. **Join the Discussion 板块**（第 26-57 行）：
   - 新增 "Mailing Lists" 子标题，保留原有四个邮件列表（Developers、Commits、Issues、Private）的内容。
   - 新增 "Slack" 子标题，包含 Slack 工作区邀请链接和说明。
   - 新增 "Issues" 子标题，包含 GitHub issue 链接和贡献指引。

3. **Connect with Community Events 板块**（第 59-75 行）：
   - 新增 "Hosting an Apache Iceberg Meetup" 子板块：明确命名规范（Apache Iceberg Meetup <geographical location>）、需遵循 ASF 品牌指南、无需 PMC 显式批准，并列出 5 条举办要求（强调 Apache Iceberg 生态、vendor-neutral、至少两家公司演讲、通知 dev list、遵守社区准则）。
   - 更新 "Apache Iceberg Community Calendar"：两个日历（Dev Events 和 Community Events）描述更详细，Dev Events 日历新增 Python/Rust/Go sync、Catalog Community Sync 等内容。

4. **格式修正**：修复末尾缺失句号（PMC member 后加句号）、移除文件末尾多余空行。

## 总结

该提交是对社区文档的重要更新，新增了 Apache Iceberg Meetup 举办指南，为社区志愿者提供了清晰的举办规范和行为准则。同时重新组织了社区页面结构，使邮件列表、Slack、Issues、Meetup 指南和社区日历等信息层次更加清晰。该提交不涉及任何代码修改，仅完善社区文档以促进社区健康发展。
