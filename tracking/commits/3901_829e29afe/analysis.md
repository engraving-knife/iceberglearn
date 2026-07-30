# 提交 3901：docs: add a "AI-Generated PR Disclosure" section to AGENTS.md (#15758)

## 提交信息

- **序号**：3901 / 4088
- **哈希**：829e29afe54cb43b5b315d0d13e27b5a9f40f524
- **短哈希**：829e29afe
- **日期**：2026-06-17 20:10:57 -0700
- **作者**：Kevin Liu
- **提交说明**：docs: add a "AI-Generated PR Disclosure" section to AGENTS.md (#15758)
- **PR/Issue**：#15758

## 总体目的

为 Iceberg 项目的 `AGENTS.md` 文件新增 "AI-Generated PR Disclosure"（AI 生成 PR 披露）章节。随着 AI 辅助开发工具（如 GitHub Copilot、Cursor、Claude Code 等）的普及，越来越多的 PR 由 AI 生成或辅助生成。Apache 软件基金会有关于生成式工具的政策要求，需要明确披露 AI 参与的内容。

该章节的目的是建立明确的规范：要求所有由 AI 生成或大幅辅助的 PR 必须在描述中披露，包括使用的模型、平台/工具、人工审查程度和提示摘要。这有助于维护项目的透明度、合规性和代码质量。

## 如何达成设计目的

在 `AGENTS.md` 中新增一个完整的章节，包含政策说明、必填字段定义和 PR 描述模板。同时引用 ASF 的生成式工具政策指南，确保与基金会要求一致。

## 修改详情

### `AGENTS.md` (+28 lines)

**修改目的**：新增 AI 生成 PR 披露规范。

**工作逻辑**：
新增章节包含以下内容：

1. **政策说明**：
   - AI 生成或大幅辅助的 PR 必须在描述中披露
   - 引用 [ASF 生成式工具指南](https://www.apache.org/legal/generative-tooling.html)
   - 生成文件仍须遵守 Apache License header 要求
   - AI 生成的提交消息须包含 `Generated-by: <tool>` 标记
   - AI 生成的 review 评论须人工审查后才能发布

2. **必填字段**：
   - **Model**：模型标识符和版本（如 `Claude Opus 4.6`、`GPT-4o`、`Gemini 2.5 Pro`）
   - **Platform/Tool**：编排工具或平台（如 `GitHub Copilot`、`Cursor`、`Aider`、`Claude Code`、`custom script`）
   - **Human Oversight**：人工审查程度（`fully reviewed`、`partially reviewed`、`unreviewed`）
   - **Prompt Summary**：给予 AI 的提示或任务简述

3. **PR 描述模板**：
```
---
**AI Disclosure**
- Model: [model name and version]
- Platform/Tool: [tool or platform name]
- Human Oversight: [fully reviewed | partially reviewed | unreviewed]
- Prompt Summary: [brief description of the task]
```

## 总结

为 Iceberg 项目建立了 AI 生成 PR 的披露规范，要求在 PR 描述中明确标注 AI 参与情况，包括模型、工具、审查程度和提示摘要。这响应了 ASF 生成式工具政策的要求，有助于项目维护者了解 PR 中 AI 参与的程度，进行适当的审查。该规范的建立恰逢其时，从本批次提交中可以看到多个 PR 已标注 AI co-author（如 Claude、Codex 等）。
