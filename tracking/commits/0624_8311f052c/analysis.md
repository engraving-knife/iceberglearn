# 提交 0624：修复博客列表中一篇博客的链接

## 提交信息

- **序号**：0624 / 4088
- **哈希**：8311f052caaf1bcf003c81976e227dee94012950
- **短哈希**：8311f052c
- **日期**：2024-03-25（Mon Mar 25 04:41:45 2024 -0400）
- **作者**：Alex Merced <alex@alexmerced.dev>（与 Fokko Driesprong <fokko@apache.org> 共同作者）
- **提交说明**：Docs: Fix link to blog post (#10028)
- **PR/Issue**：#10028

## 总体目的

修复 `site/docs/blogs.md` 中第二篇博客条目的链接错误。该条目标题为 "What is the Data Lakehouse and the Role of Apache Iceberg, Nessie and Dremio?"，但原本指向的 URL 是 `https://amdatalakehouse.substack.com/p/the-apache-iceberg-lakehouse-the`——这恰好是**第一篇**博客 "The Apache Iceberg Lakehouse: The Great Data Equalizer" 的 URL。两篇是不同的文章，却共享同一个链接，显然是编辑博客列表时复制粘贴上一条后忘记改 URL 导致的笔误。

本提交把第二篇的 URL 改为正确的 `https://amdatalakehouse.substack.com/p/what-is-the-data-lakehouse-and-the`，让用户点击标题时能跳到对应的文章。

## 如何达成设计目的

修复方式极简：直接替换该行 Markdown 链接的 URL 部分，标题文本与日期/作者元数据不变。

值得注意的是，这条链接在 0622 号提交（PR #9965，引入链接检查器）中已被 `<!-- markdown-link-check-disable-next-line -->` 注释豁免检查——因为 substack 链接 CI 探测不稳定。这意味着链接检查器即便已就位，也无法自动发现这个错误（被显式跳过了），只能靠人工 review 发现。事实上本提交的作者 Alex Merced 正是这两篇博客的作者本人，由内容作者主动发现并修正了自己文章的链接，Fokko（Iceberg PMC）做 co-author 评审合并。

## 修改详情

### `site/docs/blogs.md`

**修改目的**：修正第二篇 Dremio 博客条目的 URL。

**工作逻辑**：

- 第 47 行（diff 中的 `-`/`+` 行）：
  - `-### [What is the Data Lakehouse and the Role of Apache Iceberg, Nessie and Dremio?](https://amdatalakehouse.substack.com/p/the-apache-iceberg-lakehouse-the)`
  - `+### [What is the Data Lakehouse and the Role of Apache Iceberg, Nessie and Dremio?](https://amdatalakehouse.substack.com/p/what-is-the-data-lakehouse-and-the)`
- 仅 URL 的 path 部分变化：`/p/the-apache-iceberg-lakehouse-the` → `/p/what-is-the-data-lakehouse-and-the`。新 URL 的 slug `what-is-the-data-lakehouse-and-the` 与文章标题 "What is the Data Lakehouse..." 一致，符合 substack 自动生成 slug 的规则，印证这是正确的目标文章。
- 上方一行的 `<!-- markdown-link-check-disable-next-line -->` 注释保留不动——substack 链接探测不稳定的状况未变，仍需豁免。

## 小结

本提交是一处单行 URL 修正，修复了 `blogs.md` 中第二篇 Dremio 博客条目因复制粘贴导致的链接错误。

**影响范围**：

- 仅影响 `site/docs/blogs.md` 一行，不动任何其他文件、不动站点构建逻辑。
- 修复后用户点击该博客标题会跳到正确的 substack 文章，而非被误导向第一篇文章。

**回迁到 1.4.x 的注意事项**：

- 回迁非常安全，单行 URL 修正，无副作用。
- 若 1.4.x 已回迁 0622（引入链接检查器），则本提交是必要的配套修复——0622 在该行加了豁免注释，CI 不会自动发现此错误，必须靠本提交人工修正。
- 若 1.4.x 完全未回迁 0622，则本提交可独立回迁：只要 1.4.x 的 `blogs.md` 中存在同样的错误 URL（`/p/the-apache-iceberg-lakehouse-the`），就应回迁本提交替换为正确 URL。
- 注意检查 1.4.x 的 `blogs.md` 中该条目是否存在——若 1.4.x 文档版本较旧，可能尚未收录这篇 2024 年 2 月的博客，此时无需回迁。
