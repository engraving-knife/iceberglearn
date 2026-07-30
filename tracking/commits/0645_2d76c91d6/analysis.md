# 提交 0645：Build: disable link-check for all medium blog posts

## 提交信息

- **序号**：0645 / 4088
- **哈希**：2d76c91d6a27378fe753eb7f912bf3031368e6b0
- **短哈希**：2d76c91d6
- **日期**：2024-03-28（Thu Mar 28 23:17:10 2024 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Build: disable link-check for all medium blog posts (#10057)
- **PR/Issue**：#10057

## 总体目的

本提交为 `site/docs/blogs.md` 中剩余的 5 个 Medium 博客链接逐一添加 `<!-- markdown-link-check-disable-next-line -->` 注释，使 Markdown 链接检查 CI 跳过这些链接，避免因 Medium 链接不可达而导致 CI 失败。

背景动机：
- Iceberg 的 `site/docs/blogs.md` 汇总了社区各公司撰写的 Iceberg 相关博客，其中大量托管在 Medium（`medium.com/adobetech`、`medium.com/snowflake`、`medium.com/expedia-group-tech` 等）。
- 仓库有 `docs-check-links.yml` CI 工作流，使用 `gaurav-nelson/github-action-markdown-link-check` 配合 `site/link-checker-config.json` 检查文档链接有效性。
- Medium 对自动化请求常返回非 200（反爬、付费墙、文章迁移等），导致链接检查频繁失败，阻塞 PR。
- 此前已有部分 Medium 链接通过 `<!-- markdown-link-check-disable-next-line -->` 行内注释禁用检查（如 Snowflake、Bilibili、Hive 等帖子），但仍有 5 个 Adobe/Expedia 的 Medium 链接未禁用，持续触发 CI 报错。
- `link-checker-config.json` 的 `ignorePatterns` 仅全局忽略 linkedin、mvnrepository、javadoc 相对路径，并未全局忽略 Medium（因为并非所有 Medium 链接都失效，逐个禁用更精确）。本提交补齐剩余 5 个 Medium 链接的禁用注释，实现"所有 Medium 博客链接均不参与链接检查"。

## 如何达成设计目的

利用 `markdown-link-check` 工具支持的 HTML 注释指令：

1. 在每个需要跳过的 Markdown 链接所在行的**上一行**插入 `<!-- markdown-link-check-disable-next-line -->`。
2. 工具解析时识别该注释，跳过紧随其后的那一行中的链接检查。
3. 这种行内禁用方式比修改全局 `ignorePatterns` 更精细，不影响其他正常 Medium 链接的检查（如果将来有新的 Medium 链接可达，仍可检查）。
4. 本次共为 5 个链接添加注释，分布在 `blogs.md` 的 412、443、449、460、471 行附近。

## 修改详情

### `site/docs/blogs.md`

**修改目的**：为 5 个 Medium 博客链接添加链接检查禁用注释，消除 CI 误报。

**工作逻辑**：
在以下 5 个博客标题行的上一行各插入一行 `<!-- markdown-link-check-disable-next-line -->`：
1. `### [Migrating to Apache Iceberg at Adobe Experience Platform](https://medium.com/adobetech/...)`（Adobe，2021-06-17）
2. `### [A Short Introduction to Apache Iceberg](https://medium.com/expedia-group-tech/...)`（Expedia，2021-01-26）
3. `### [Taking Query Optimizations to the Next Level with Iceberg](https://medium.com/adobetech/...)`（Adobe，2021-01-14）
4. `### [High Throughput Ingestion with Iceberg](https://medium.com/adobetech/...)`（Adobe，2020-12-22）
5. `### [Iceberg at Adobe](https://medium.com/adobetech/...)`（Adobe，2020-12-03）

这 5 个链接均为 `medium.com` 域名。修改后，`blogs.md` 中所有 Medium 链接均带有禁用注释（此前已禁用的包括 Snowflake、Bilibili、Hive、ajanthabhat 等作者的 Medium 帖子），CI 链接检查不再因 Medium 不可达而失败。

## 小结

本提交是文档 CI 稳定性维护的小修复，通过逐链接禁用检查解决 Medium 链接误报。

成效：
- `docs-check-links` CI 不再因 Adobe/Expedia 的 5 个 Medium 博客链接不可达而失败，PR 流转更顺畅。
- 至此 `blogs.md` 中所有 Medium 链接均已禁用检查，CI 在该文件上趋于稳定。
- 采用行内 `disable-next-line` 注释而非全局 `ignorePatterns`，保留了对未来新增非 Medium 链接的检查能力。

影响范围：
- 仅改 `site/docs/blogs.md`，新增 5 行注释，不删除任何内容，不影响博客列表展示（HTML 注释在渲染时不可见）。

回迁到 1.4.x 注意事项：
- 风险极低，可直接回迁。
- 需确认 1.4.x 的 `blogs.md` 是否已包含这 5 个链接；若 1.4.x 文档结构不同，需对照行号定位。
- 与 0643 号提交（CI 链接检查器路径过滤）相关但独立：0643 减少 CI 触发频率，0645 减少 CI 失败率，两者可分别回迁。
- 若 1.4.x 的 `link-checker-config.json` 已通过 `ignorePatterns` 全局忽略 Medium，则本提交可跳过（重复处理）。
