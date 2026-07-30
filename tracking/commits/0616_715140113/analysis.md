# 提交 0616：添加 Iceberg 提案的 Issue 模板与文档

## 提交信息

- **序号**：0616 / 4088
- **哈希**：715140113a1d30dc213e677a7d65a5dbe51dde90
- **短哈希**：715140113
- **日期**：2024-03-20 12:45:58 -0700
- **作者**：Daniel Weeks <dweeks@apache.org>
- **提交说明**：Add issue template and docs for iceberg proposals (#9932)
- **PR/Issue**：#9932

## 总体目的

本提交旨在为 Apache Iceberg 项目引入一套正式的"Iceberg Improvement Proposal（Iceberg 改进提案）"流程，并通过 GitHub Issue 模板和文档说明来规范提案的提交、讨论与采纳机制。

背景动机：随着 Iceberg 项目规模扩张，涉及表格式规范、View 规范、REST、Puffin、加密等多个规范层面的重大变更越来越多，仅靠 Pull Request 已经难以充分讨论这些大范围改动（涉及规范修改、新增规范或对现有实现进行重大改造）。社区需要一种正式的治理机制，让提案在落地前获得多方利益相关者的反馈与共识，并保留可追溯的讨论记录。本提交通过两个层面的工作来满足这一需求：

1. 提供一个结构化的 GitHub Issue 模板，让提案者按统一格式提交信息（提案内容、文档链接、影响的规范范围）。
2. 在贡献文档中明确"提案是什么、应该包含什么、谁能提交、如何被采纳"等流程要求。

## 如何达成设计目的

设计思路是"模板 + 文档"双管齐下：

- **模板层**：通过 `.github/ISSUE_TEMPLATE/iceberg_proposal.yml`（GitHub 表单式 Issue 模板）强制结构化收集提案信息。GitHub 的 form schema 机制会自动给 issue 打上 `proposal` 标签，并提供指引链接。
- **文档层**：在 `site/docs/contribute.md` 中新增"Apache Iceberg Improvement Proposals"章节，定义提案的定义、构成、提交人要求、查找方式与采纳流程（含 dev 邮件列表 [DISCUSS] 讨论以及 ASF 投票流程）。

这种设计让提案流程既能利用 GitHub 工具链进行追踪（issue + label），又能符合 Apache 软件基金会的治理规范（dev list 讨论 + PMC 投票），保证流程公开透明。

## 修改详情

### `.github/ISSUE_TEMPLATE/iceberg_proposal.yml`

**修改目的**：新增一个 GitHub 表单式 Issue 模板，专门用于提交 Iceberg 改进提案。

**工作逻辑**：

- 头部 14 行为 Apache 许可证声明，符合 ASF 合规要求。
- YAML front matter 指定模板的 `name`（Apache Iceberg Improvement Proposal）、`description`（Propose a Spec change or major feature）以及默认 `labels: ["proposal"]`——只要使用此模板创建 issue，GitHub 会自动给该 issue 打上 `proposal` 标签，便于后续筛选。
- `body` 部分包含 4 个字段：
  1. `markdown` 类型：一段说明性文字，提供贡献文档中提案章节的链接 `https://iceberg.apache.org/contribute/#apache-iceberg-improvement-proposals`，引导用户先阅读流程。
  2. `textarea` 类型 `Proposed Change`：必填（`required: true`），让提案者描述提案内容、用例与动机。
  3. `input` 类型 `Proposal document`：可选，让提案者粘贴提案文档链接，推荐使用 Google Docs 以便公开评论与分享。
  4. `checkboxes` 类型 `Specifications`：列出 Table / View / REST / Puffin / Encryption / Other 六个选项，让提案者勾选本提案影响哪些规范，方便分类治理。

模板整体使用 GitHub 表单 schema（GitHub Issue forms），相比纯 markdown 模板能提供更友好的输入体验，且字段标识固定，便于自动化处理。

### `site/docs/contribute.md`

**修改目的**：在贡献指南中新增"Apache Iceberg Improvement Proposals"一节，正式说明提案流程。

**工作逻辑**：在已有的 PR 流程章节（"Pull Requests"）与"Building the Project Locally"之间插入该新章节，包含以下小节：

- **What is an improvement proposal?**：定义提案的范围——可能修改现有规范、创建新规范、或对现有实现做重大改动；强调大范围变更需要社区多方利益相关者参与。
- **What should a proposal include?**：列出提案必须包含的三项：
  1. 使用 `Apache Iceberg Improvement Proposal` 模板创建的 GitHub issue
  2. 一份文档，包含 Motivation、Implementation proposal、Breaking changes/incompatibilities、Alternatives considered 四个部分（这是典型的设计文档结构）
  3. 在 dev 邮件列表发起主题为 `[DISCUSS] <proposal title>` 的讨论线程
- **Who can submit a proposal?**：任何人都可以提交，但要求"打算参与实现"才提交，避免空想型提案。
- **Where can I find current proposals?**：通过 GitHub issue 标签 `proposal` 检索当前所有提案，链接为 `https://github.com/apache/iceberg/issues?q=is%3Aissue+is%3Aopen+label%3Aproposal+`。
- **How are proposals adopted?**：明确提案采纳流程——达成普遍共识后在 dev list 发起投票，遵循 ASF 的 [code modification][apache-vote] 投票模式，需要至少 3 个正面的 PMC 投票且不能使用 lazy consensus modifier；投票是为了确认已有的共识，而非用于解决分歧或强行决策。

文末定义了两个参考链接引用：`[iceberg-proposals]` 指向 GitHub issues 过滤页面，`[apache-vote]` 指向 Apache 投票流程官方文档。

## 小结

本提交属于文档与治理流程类改动，不涉及任何代码逻辑。引入的提案流程对规范型变更（如 Table / View / REST / Puffin / Encryption 规范）有约束作用，但对普通 bug 修复或小范围特性开发无影响。

回迁到 1.4.x 的注意事项：

- 该改动纯粹是文档和 Issue 模板，不涉及代码或构建逻辑，回迁风险极低，无 API/兼容性影响。
- 回迁意义有限——提案流程主要是面向 main 分支未来的规范演进，1.4.x 作为维护分支通常不引入新的规范变更。但若希望分支治理流程与社区保持一致，回迁模板与文档并无副作用。
- 由于文件位于 `.github/` 和 `site/docs/`，回迁时只需复制两个文件即可，不与分支已有改动冲突。
