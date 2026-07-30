# 提交 3164：site: add slug to be explicit about blog url (#15149)

## 提交信息

- **序号**：3164 / 4088
- **哈希**：3f749736193cb6d4ae844873d98ad47ff3f640d9
- **短哈希**：3f7497361
- **日期**：2026-01-26 18:12:23 -0800
- **作者**：Kevin Liu
- **提交说明**：site: add slug to be explicit about blog url
- **PR/Issue**：#15149

## 总体目的

Iceberg 官网使用 MkDocs Material 的博客插件生成博客页面，每篇文章的最终 URL（slug）默认由插件根据文章标题自动推导。该自动推导采用了较激进的 slugify 方案：会剥离特殊字符。在 #15141 新增的 C++ 0.2.0 发布博客中，标题 "Apache Iceberg C++ 0.2.0 Release" 被自动转成 `apache-iceberg-c-020-release/`——`++` 被删除（C++ 变成了 c）、版本号中的点 `.` 也被删除（0.2.0 变成了 020）。这导致博客 URL 既不准确也不可读，丢失了"C++"与版本号的语义，影响链接的可分享性与可读性。

本提交通过在博客文章的 front matter 中显式指定 `slug` 字段来覆盖插件的自动推导，使 URL 可控且语义清晰。除修正 C++ 发布博客的 slug 外，作者还顺手给另一篇已有博客（Iceberg Summit 2026）补上显式 slug，统一所有博客的 URL 显式化约定，避免日后再次出现自动 slugify 误伤特殊字符的问题。作者在提交说明中附带了本地验证过的目标 URL。

## 如何达成设计目的

在两篇博客 Markdown 文件的 front matter 中各增加一行 `slug: <期望的-url>`，MkDocs Material 博客插件支持 `slug` 元数据字段优先于自动 slugify，从而固定文章 URL。改动极小但效果明确。

## 修改详情

### `site/docs/blog/posts/2026-01-26-iceberg-cpp-0.2.0-release.md` (+1/-0 lines)

**修改目的**：为 C++ 0.2.0 发布博客显式指定 slug，修正自动 slugify 误删 `++` 与 `.` 的问题。

**工作逻辑**：在 front matter 的 `title` 行后新增 `slug: apache-iceberg-cpp-0.2.0-release  # this is the blog url`。这样博客 URL 固定为 `/blog/apache-iceberg-cpp-0.2.0-release/`，正确保留了 `cpp`（对应 C++）与 `0.2.0` 版本号，替代了插件自动生成的错误 slug `apache-iceberg-c-020-release/`。行尾注释说明该字段即博客 URL，便于后续维护者理解。

### `site/docs/blog/posts/2026-01-10-iceberg-summit.md` (+1/-0 lines)

**修改目的**：为 Iceberg Summit 2026 博客补上显式 slug，统一显式 URL 约定。

**工作逻辑**：在 front matter 的 `title` 行后新增 `slug: announcing-iceberg-summit-2026  # this is the url`，将博客 URL 固定为 `/blog/announcing-iceberg-summit-2026/`。虽该标题不含特殊字符，自动 slugify 结果尚可，但显式指定可避免日后标题调整导致 URL 漂移，并统一全站博客的 URL 管理方式。

## 总结

本提交通过为博客文章显式指定 `slug` front matter 字段，修正了 MkDocs Material 博客插件自动 slugify 将 "C++" 误转为 "c"、将 "0.2.0" 误转为 "020" 导致的 URL 失真问题，使 C++ 0.2.0 发布博客 URL 准确可读，并顺手为 Iceberg Summit 博客补上显式 slug 以统一约定，提升了官网博客链接的稳定性与可维护性。
