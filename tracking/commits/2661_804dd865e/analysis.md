# 提交 2661：Add "stop signs" for sensitive information/issues to all issue templates (#14103)

## 提交信息

- **序号**：2661 / 4088
- **哈希**：804dd865e2fb6aee7988d8c56127f236099bdb0f
- **短哈希**：804dd865e
- **日期**：2025-09-19 09:03:18 -0700
- **作者**：Robert Stupp
- **提交说明**：Add "stop signs" for sensitive information/issues to all issue templates (#14103)
- **PR/Issue**：#14103

## 总体目的

本提交为 Apache Iceberg 的所有 GitHub Issue 模板添加"停止标志"（stop signs）提示，提醒外部贡献者不要在 Issue 中分享敏感信息，并引导他们通过正确的渠道报告安全漏洞。

背景是：外部贡献者通常不熟悉 Apache 软件基金会的各项流程和规则。特别是在报告 Bug 时，贡献者可能会无意中在公开 Issue 中包含敏感信息（如密码、安全令牌、私有 URL 等），或者将安全漏洞直接作为公开 Issue 提交，这可能导致安全风险。Apache 的正确做法是将安全漏洞通过 `security@apache.org` 私密报告。

本提交在所有四个 Issue 模板（Bug 报告、改进请求、提案、问题）的顶部添加了醒目的 Markdown 提示文本，作为"停止标志"提醒用户注意信息安全。

## 如何达成设计目的

通过在四个 GitHub Issue 模板 YAML 文件的 `body` 部分开头添加 `type: markdown` 元素，显示提示文本。不同模板的提示内容略有不同——Bug 报告模板包含最完整的提示（包括安全漏洞报告渠道和敏感信息警告），其他模板包含敏感信息警告。

## 修改详情

### `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml` (+6/-0 lines)

**修改目的**：在 Bug 报告模板顶部添加安全提示。

**工作逻辑**：在 `body` 部分开头新增一个 `type: markdown` 元素，包含三条提示：
1. 提醒先搜索已有 Issue 避免重复报告
2. 可能的安全漏洞应通过 `security@apache.org` 报告，而非公开 Issue
3. 不要分享密码、安全令牌、私有 URL 等敏感信息

### `.github/ISSUE_TEMPLATE/iceberg_improvement.yml` (+4/-0 lines)

**修改目的**：在改进/功能请求模板顶部添加敏感信息提示。

**工作逻辑**：在 `body` 部分开头新增 `type: markdown` 元素，提示不要分享敏感信息（密码、安全令牌、私有 URL 等）。

### `.github/ISSUE_TEMPLATE/iceberg_proposal.yml` (+4/-1 lines)

**修改目的**：在提案模板中添加敏感信息提示。

**工作逻辑**：将原有的单行 Markdown 值改为多行格式，在保留提案贡献文档链接的同时，新增一行敏感信息警告。

### `.github/ISSUE_TEMPLATE/iceberg_question.yml` (+4/-1 lines)

**修改目的**：在问题模板中添加敏感信息提示。

**工作逻辑**：将原有的单行 Slack 邀请链接改为多行格式，新增一行敏感信息警告。

## 总结

本提交为所有 GitHub Issue 模板添加了安全信息提示"停止标志"，提醒外部贡献者不要在公开 Issue 中分享敏感信息，并引导安全漏洞通过 `security@apache.org` 报告。这是一个重要的社区治理和安全防护措施，有助于防止敏感信息泄露和安全漏洞被公开讨论。注意：本提交与提交 2661（a73a5c091）具有相同的 PR 编号和提交说明，2661 是一个空提交（无文件变更），可能是 GitHub 合并过程中的重复。
