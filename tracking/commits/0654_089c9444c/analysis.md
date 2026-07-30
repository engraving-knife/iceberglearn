# 提交 0654：Build: Ignore link-checking for Blogs / https://search.maven.org/

## 提交信息
- **序号**：0654 / 4088
- **哈希**：089c9444cedf2ee189f722123075ae0748238e17
- **短哈希**：089c9444c
- **日期**：2024-04-03（Wed Apr 3 09:59:13 2024 +0200）
- **作者**：Eduard Tudenhoefner
- **提交说明**：Build: Ignore link-checking for Blogs / https://search.maven.org/ (#10081)
- **PR/Issue**：#10081

## 总体目的

本提交旨在解决文档站点构建流水线中"链接检查"步骤持续失败/不稳定的问题。

Iceberg 项目在 `site/` 目录下维护文档网站（基于 MkDocs/Hugo 等静态站点生成器），其中 `site/docs/blogs.md` 汇总了大量第三方公司博客链接（Dremio、Cloudera、Tabular、Starburst、Netflix、Alibaba、LinkedIn 等），用于介绍 Iceberg 相关的实践文章。此外文档中也会引用 `https://search.maven.org/`（Maven Central 搜索页）来指向制品坐标。

文档构建流程中包含一个"链接检查"（markdown-link-check）环节，会遍历 Markdown 中的外链并逐一发起 HTTP 请求验证其可访问性。问题在于：这些博客链接与 Maven 搜索页经常因为目标站点临时不可达、反爬限制、重定向、TLS 变化或地域封锁等原因而检查失败，导致 CI 流水线误报。这类失败与文档内容本身无关，纯属外部站点可达性问题，却会阻断构建或制造噪音。

本提交的目标是：将 `blogs.md` 中所有博客链接逐条标记为"跳过链接检查"，并将 `https://search.maven.org/` 整体加入链接检查器的全局忽略名单，从而使文档构建的链接检查环节不再因这些不可控的外链而失败，恢复 CI 的绿色状态。

## 如何达成设计目的

提交采用两种互补的手段：

1. **逐条行级禁用**：在 `blogs.md` 中每一条博客链接所在行之前插入 HTML 注释 `<!-- markdown-link-check-disable-next-line -->`。该注释是 `markdown-link-check` 工具支持的内联指令，作用是"仅禁用下一行的链接检查"。这样既保留了链接本身（用户仍可点击浏览），又让检查器跳过它。之所以逐条而非整文件禁用，是因为文件内还可能有需要检查的其它链接，逐条精确控制更安全。

2. **全局模式忽略**：在 `site/link-checker-config.json` 的 `ignorePatterns` 数组中新增一条正则 `"^https://search.maven.org/"`，使所有指向 Maven Central 搜索页的链接在检查时被全局忽略。这是配置层面的批量豁免，适用于整个站点所有文档，无需逐处加注释。

两种手段结合：博客链接用行级注释精确豁免，Maven 搜索页用全局配置批量豁免，覆盖了当前已知的两类不稳定外链来源。

## 修改详情

### `site/docs/blogs.md`
**修改目的**：将所有博客条目的链接排除在链接检查之外。
**工作逻辑**：该文件按时间倒序列出约 60+ 篇第三方博客。本提交在每一条 `### [博客标题](URL)` 标题行之前插入一行 `<!-- markdown-link-check-disable-next-line -->`。该 HTML 注释对 Markdown 渲染不可见（不影响页面展示），但 `markdown-link-check` 解析时会识别该指令并跳过紧随其后的那一行中的链接检查。因此所有博客 URL 仍保留在页面中供读者访问，只是不再被 CI 校验可达性。改动遍布整个文件，每个博客条目各加一行注释。

### `site/link-checker-config.json`
**修改目的**：将 `https://search.maven.org/` 加入全局链接检查忽略名单。
**工作逻辑**：该 JSON 是 `markdown-link-check` 的配置文件，其中 `ignorePatterns` 数组定义了一组正则，匹配到的 URL 会被跳过。原数组已包含 `^../../javadoc` 等模式（用于忽略本地 Javadoc 相对路径）。本提交在数组末尾新增一项 `{ "pattern": "^https://search.maven.org/" }`，使任何以 `https://search.maven.org/` 开头的链接（如指向特定制品搜索结果的链接）在整个站点范围内被跳过检查。

## 小结
- **成效**：成功达成目的。通过行级 `markdown-link-check-disable-next-line` 注释与全局 `ignorePatterns` 配置相结合，有效豁免了博客链接与 Maven 搜索页的可达性检查，消除 CI 链接检查环节因外链不可控导致的误报。
- **影响范围**：仅文档站点构建（`site/`）。`blogs.md` 的页面渲染内容不变（注释不可见），`link-checker-config.json` 影响全站链接检查行为。不影响任何生产代码或测试。
- **回迁到 1.4.x 的注意事项**：无特殊注意点。该变更纯文档/配置层面，回迁风险极低。回迁时直接应用即可；若 1.4.x 的 `blogs.md` 内容与 main 有差异，注意逐条注释应落在对应的博客标题行前。需确认 1.4.x 使用的 `markdown-link-check` 版本支持 `disable-next-line` 指令与 `ignorePatterns` 配置项（二者均为该工具标准特性）。
