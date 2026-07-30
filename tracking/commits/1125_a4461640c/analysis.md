# 提交 1125：Docs: Initial committer guidelines and requirements for merging (#10780)

## 提交信息

- **序号**：1125 / 4088
- **哈希**：a4461640c80db67c4fa7eca64f9cd4fa19c81805
- **短哈希**：a4461640c
- **日期**：2024-09-03（Tue Sep 3 11:49:45 2024 -0700）
- **作者**：emkornfield <emkornfield@gmail.com>
- **提交说明**：Docs: Initial committer guidelines and requirements for merging (#10780)
- **PR/Issue**：#10780

## 总体目的

Iceberg 贡献文档 `site/docs/contribute.md` 此前只说明了"如何提 PR"，但没有明确写出"committer 何时可以合并 PR、哪些 PR 需要更高门槛"。这导致新晋 committer 对合并权限边界不清晰，社区也缺乏统一的合并规范。

本提交在 contribute.md 中新增 "Merging Pull Requests" 小节，首次明确以下治理规则：

1. **默认门槛**：除一名非作者的 committer 满意即可合并；公共 API 改动需等待至少 24 小时以收集反馈。
2. **review 语义**：request changes 表示"有 merits 但还需修改"；若认为根本不该合并，reviewer 应明确表态；分歧无法解决时上升到开发者邮件列表。
3. **例外清单**：spec 改动必须先走 IIP（Iceberg Improvement Proposal）；`format/` 目录和 `open-api/rest-catalog*` 视为 spec 改动，除非已被 IIP 覆盖，否则需走 ASF "code modification" 投票（无 lazy consensus）；拼写/语法/小格式修正可豁免。

## 如何达成设计目的

直接在 `site/docs/contribute.md` 的 "Pull Requests" 小节之后、"Apache Iceberg Improvement Proposals" 小节之前，插入一段纯文字说明。无代码逻辑，无构建改动，只是把社区治理约定固化为文档。

## 修改详情

### `site/docs/contribute.md`

**修改目的**：补充 committer 合并 PR 的初始指南与门槛要求。

**工作逻辑**：在原 PR 流程说明后追加 `### Merging Pull Requests` 子标题及其下三段文字：

- 第一段：说明一般 PR 一名非作者 committer 满意即可合并，链接到 Apache committer 信任模型；要求 review 意见被解决（采纳或判定超范围）；公共 API 改动需等满 24 小时。
- 第二段：定义 request changes 的含义（认为有 merits 但仍需修改）；若 reviewer 认为不应合并需明确说明；作者与 reviewer 无法达成一致时上升到开发者邮件列表。明确 reviewer 包括 contributor/committer/PMC 任何留言者。
- 第三段（列表）：列出需要超出"单 committer 合并"门槛的例外：
  - spec 的行为/功能改动必须先走 IIP。
  - `format/` 与 `open-api/rest-catalog*` 下的文件视为 spec 改动；除非已被 IIP 覆盖，否则需单独投票（遵循 ASF code modification 模型，不使用 lazy consensus）；语法/拼写/小格式修正可豁免；处于 IIP 流程中的草案 spec 不需投票，但实质性改动需在 dev 邮件列表通知。

## 小结

- **成效**：贡献文档首次写明合并门槛与 spec 改动的投票要求，给 committer 提供了清晰可执行的合并规范，并保护 spec（format/ 与 open-api）不被未经投票的改动破坏。
- **影响范围**：仅 `site/docs/contribute.md`，新增 12 行文档，无代码、构建或运行时影响。
- **回迁到 1.4.x 的注意事项**：这是仓库治理文档改进，与版本功能无关，对 1.4.x 运行时无影响。contribute.md 由 main 统一维护并发布到站点，1.4.x **无需回迁**。即便 1.4.x 分支的该文件与 main 不同，也不影响其发布产物。
