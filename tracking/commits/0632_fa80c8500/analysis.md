# 提交 0632：Build: disable link-check for existing medium blog posts

## 提交信息

- **序号**：0632 / 4088
- **哈希**：fa80c85007ebf77f59f3bc4f015c1b4bed1b8f76
- **短哈希**：fa80c8500
- **日期**：2024-03-27 15:27:51 +0800
- **作者**：Manu Zhang
- **提交说明**：Build: disable link-check for existing medium blog posts (#10042)
- **PR/Issue**：#10042

## 总体目的

本提交解决了 Iceberg 项目网站文档 `site/docs/blogs.md` 在 CI 链接检查（link-check）持续失败的问题，让 CI 不再对 8 篇已发布但当前不可被自动链接检查器访问的 Medium 博客文章做可达性校验。

背景动机：Apache Iceberg 维护着一份"公司/社区博客列表" `site/docs/blogs.md`，按时间倒序列出与 Iceberg 相关的外部博客文章，每条目都是一个 Markdown 链接。项目的 CI 流水线会运行 markdown 链接检查器（依据 `<!-- markdown-link-check-disable-next-line -->` 这个标准 HTML 注释指令在 markdown-link-check 等工具中关闭"下一行链接"的检查），对文档中所有外链做 HTTP 可达性校验。

问题在于 Medium 平台会拦截自动化爬虫请求（通常返回 403 或非 200 状态），且这些文章已经发布并稳定存在，但链接检查器无法验证其可达性，导致 CI 频繁失败。本提交前的文件中，已经有针对 substack.com 等同类站点的 disable 注释先例；本提交把同样的处理方式扩展到 8 个 Medium 链接上。

## 如何达成设计目的

设计思路是直接复用 markdown-link-check 工具标准的"行级跳过"指令 `<!-- markdown-link-check-disable-next-line -->`，将其插入到 8 个 Medium 链接所在标题行的上一行，从而让链接检查器在扫描这些行时跳过对它们做 HTTP 校验。

之所以选择"逐行 disable"而非"整段 disable"或"全局 disable"，是因为：

1. 文档中的其他链接（dremio.com、linkedin.com 等）仍需要被链接检查器覆盖，以避免它们后续失效无人察觉。
2. 仅对已知不可达的 Medium 域名做精确豁免，最大化保留文档其余部分的链接健康监控能力。
3. 与已有 substack 域名的处理方式保持一致，文档风格统一，便于后续维护者一眼看出哪些链接被豁免以及为何豁免。

## 修改详情

### `site/docs/blogs.md`

**修改目的**：在 8 个 Medium 博客链接标题前插入 `markdown-link-check-disable-next-line` HTML 注释，豁免它们的链接检查。

**工作逻辑**：

文件原本就是"作者/日期/公司"块 + Markdown 链接标题的列表结构。修改方式是在 8 个特定链接的标题行（`### [标题](URL)` 形式）正上方插入一行 HTML 注释。被豁免的 8 个 URL 全部位于 `medium.com` 域名下，分两类：

- **个人 Medium 账号下的文章（3 篇）**：
  - `https://medium.com/@ajanthabhat/how-not-to-use-apache-iceberg-046ae7e7c884`（Dremio，2024-01-23）
  - `https://medium.com/@ayushtkn/apache-hive-4-x-with-iceberg-branches-tags-3d52293ac0bf/`（Cloudera，2023-10-12）
  - `https://medium.com/@ayushtkn/apache-hive-4-x-with-apache-iceberg-part-i-355e7a380725/`（Cloudera，2023-10-12）

- **Snowflake / Bilibili 公司 Medium 账号下的文章（5 篇）**：
  - `https://medium.com/@lirui.fudan/how-bilibili-builds-olap-data-lakehouse-with-apache-iceberg-9f3408e53f9`（Bilibili，2023-06-14）
  - `https://medium.com/snowflake/understanding-iceberg-table-metadata-b1209fbcc7c3`（Snowflake，2023-01-30）
  - `https://medium.com/snowflake/creating-and-managing-apache-iceberg-tables-using-serverless-features-and-without-coding-14d2198cf5b5`（Snowflake，2023-01-27）
  - `https://medium.com/snowflake/getting-started-with-apache-iceberg-80f338921a31`（Snowflake，2023-01-27）
  - `https://medium.com/snowflake/how-apache-iceberg-enables-acid-compliance-for-data-lakes-9069ae783b60/`（Snowflake，2023-01-13）

每次插入都是单纯加一行注释，注释本身对最终渲染的 HTML 没有可见影响（HTML 注释不会在 GitHub Pages 等渲染器中显示）。这 8 处改动合计 8 行新增，0 行删除，与 `--stat` 报告的 `8 insertions(+)` 一致。

## 小结

本提交通过 8 行精确的行级 disable 注释，修复了 CI 中由 Medium 链接不可达导致的链接检查失败，同时保留了对文档其余外链的健康监控。改动零业务影响，只触及文档文件。

回迁到 1.4.x 的注意事项：
- 文档类改动，无任何代码或运行期影响，可直接 cherry-pick。
- 文件路径 `site/docs/blogs.md` 在 1.4.x 与 main 之间应当一致；若 1.4.x 的 blogs.md 已与 main 有差异，需以 1.4.x 的版本为准应用相同的 8 处豁免，避免上下文行错位。
- 若 1.4.x 的 CI 不运行 markdown-link-check（或使用不同工具），该改动也不会产生副作用，至多是冗余注释。
