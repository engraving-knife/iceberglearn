# 提交 2809：site: use jinja comment in header.html override (#14459)

## 提交信息

- **序号**：2809 / 4088
- **哈希**：b211c8b82d0f52207497e7e4db1004db9f18abfa
- **短哈希**：b211c8b82
- **日期**：2025-10-31 19:27:20 -0700
- **作者**：Kevin Liu
- **提交说明**：site: use jinja comment in header.html override (#14459)
- **PR/Issue**：#14459

## 总体目的

本提交将 Iceberg 文档站点中 `header.html` 模板覆盖文件里的 HTML 注释替换为 Jinja 模板注释。

Iceberg 文档站点使用 MkDocs Material 主题，通过 `site/overrides/partials/header.html` 文件覆盖主题的默认 header 模板。该文件中包含来自 MkDocs Material 主题的 MIT 许可证声明，之前使用 HTML 注释 `<!-- ... -->` 包裹。

MkDocs 使用 Jinja2 模板引擎渲染页面。HTML 注释会原样输出到最终 HTML 中，而 Jinja 注释 `{#- ... -#}` 在渲染时会被移除，不会出现在最终 HTML 中。将许可证声明从 HTML 注释改为 Jinja 注释可以避免在最终页面的 HTML 源码中暴露这段注释内容，保持输出 HTML 的简洁。

这可能也与提交 2799（Docs: lint markdown files in site build）中引入的 markdownlint 检查有关，HTML 注释中的内容可能触发 lint 规则问题。

## 如何达成设计目的

将 `header.html` 文件开头的 HTML 注释 `<!--` 替换为 Jinja 注释 `{#-`，结尾的 `-->` 替换为 `-#}`。注释内容（MIT 许可证声明）保持不变。

## 修改详情

### `site/overrides/partials/header.html` (+2/-2 lines)

**修改目的**：将 HTML 注释改为 Jinja 注释。

**工作逻辑**：文件第 1 行的 `<!--` 改为 `{#-`，第 21 行的 `-->` 改为 `-#}`。`{#-` 和 `-#}` 中的 `-` 表示去除注释前后的空白字符，确保渲染输出中不残留多余空行。注释内容本身（MkDocs Material 主题的 MIT 许可证声明）不变。

## 总结

本提交将文档站点 `header.html` 模板覆盖文件中的 HTML 注释替换为 Jinja 模板注释，使许可证声明不会出现在最终渲染的 HTML 输出中，保持页面 HTML 的整洁。这是一个小的模板格式修复。
