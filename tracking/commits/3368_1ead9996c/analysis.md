# 提交 3368：docs: reformat mailing list (#15527)

## 提交信息

- **序号**：3368 / 4088
- **哈希**：1ead9996cf2e335fc6c53baf3ff826878d52a868
- **短哈希**：1ead9996c
- **日期**：2026-03-09
- **作者**：Kevin Liu
- **提交说明**：docs: reformat mailing list (#15527)
- **PR/Issue**：#15527

## 总体目的

Iceberg 社区文档中的邮件列表（mailing list）页面需要更新和重新排版。此次改动主要有三个目的：一是将邮箱地址的格式从尖括号 `<...>` 改为反引号 `` `...` ``，使其在 Markdown 渲染中更清晰且不易被爬虫抓取；二是调整链接顺序，将 Archive 链接从最后提到最前，使读者更容易先查看历史归档；三是新增了 `ci-jobs` 邮件列表，这是之前文档中缺失的用于接收 GitHub Actions CI 通知的邮件列表。同时对 `private` 列表的描述补充了"PMC-only access"的标注。

## 如何达成设计目的

改动仅涉及一个文档文件 `site/docs/community.md`，通过 Markdown 格式调整和内容增补完成。整体思路是统一邮件列表的呈现风格，并补全社区实际存在但文档中缺失的 CI 邮件列表信息。

## 修改详情

### `site/docs/community.md` (+13/-9 lines)

**修改目的**：重新格式化邮件列表，新增 ci-jobs 列表，统一风格。

**工作逻辑**：
具体改动包括：
1. 标题从 "Iceberg has four mailing lists:" 改为 "Apache Iceberg mailing lists:"，措辞更正式。
2. 所有邮箱地址从 `<dev@iceberg.apache.org>` 格式改为 `` `dev@iceberg.apache.org` `` 反引号格式。
3. 每个邮件列表的 Archive 链接从列表末尾移到 Subscribe/Unsubscribe 之前，方便优先查看归档。
4. 各列表的描述文字微调，例如 Developers 的描述从 "used for community discussions" 改为 "Iceberg community discussions"，Commits 的描述从 "distributes commit notifications" 改为 "GitHub commit notifications"。
5. 新增 `ci-jobs` 邮件列表条目：`ci-jobs@iceberg.apache.org` -- GitHub Actions notifications，含 Archive/Subscribe/Unsubscribe 链接。
6. `private` 列表的 Archive 链接补充标注 "(PMC-only access)"，说明仅 PMC 成员可访问。

## 总结

这是一次社区文档的格式化与内容完善，统一了邮件列表的 Markdown 渲染风格，新增了实际存在的 CI 邮件列表，并调整了链接顺序以改善阅读体验。改动虽小但对社区文档的准确性和可用性有实际价值。
