# 提交 0463：Label `site/` as documentation

## 提交信息

- **序号**：0463
- **完整哈希**：c516fef72d603c6122f34d0fca286e0ceaad78b9
- **短哈希**：c516fef72
- **日期**：2024-02-05 12:38:56 +0100
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：Label `site/` as documentation
- **PR**：#9652

## 总体目的

本提交是对 GitHub 自动打标签机制（labeler）的配置补全。Iceberg 仓库使用 GitHub Actions 的 `labeler` 动作，根据 PR 改动的文件路径自动给 PR 打上 `DOCS` 标签，便于维护者快速识别文档类变更并分流审阅。原配置已经覆盖了 `docs/**/*`、各模块的 `CHANGELOG.md`、`README.md`、`CONTRIBUTING.md` 等路径，但遗漏了 `site/**/*` 目录。

`site/` 目录存放的是 Iceberg 官方文档站点（基于 Jekyll/Hugo 等静态站点生成器）的源文件，包括 `site/docs/`、`site/blog/`、`site/layouts/` 等。这些文件本质上是文档，但因为没有被 labeler 规则匹配，修改 `site/` 下文件的 PR 不会被自动打上 `DOCS` 标签，造成文档类 PR 分类不完整、维护者难以通过标签筛选到这类变更。

本提交在 `DOCS` 标签的 `any-glob-to-any-file` 列表中加入 `site/**/*`，使任何触及 `site/` 目录的 PR 都能被自动识别为文档变更并打上 `DOCS` 标签，与 `docs/**/*` 的处理保持一致。

## 如何达成设计目的

在 `.github/labeler.yml` 的 `DOCS` 节点下，向 `any-glob-to-any-file` 数组中新增一项 `'site/**/*'`，使其与已有的 `'docs/**/*'` 并列。这样 labeler 在评估 PR 时，只要改动文件路径匹配 `site/**/*`，就会触发 `DOCS` 标签。

## 修改详情

### .github/labeler.yml

**修改目的**：扩展 `DOCS` 标签的文件匹配范围，覆盖 `site/` 目录下的所有文档站点源文件。

**工作逻辑**：
- 修改前 `DOCS` 节点的 `any-glob-to-any-file` 数组包含：
  - `'docs/**/*'`
  - `'**/*CHANGELOG.md'`
  - `'**/*README.md'`
  - `'**/*CONTRIBUTING.md'`
- 本提交在 `'docs/**/*'` 之后插入一行 `'site/**/*'`，使数组变为 5 项。
- `any-glob-to-any-file` 的语义是「只要 PR 中任一变更文件匹配数组中任一 glob，就打上该标签」。加入 `site/**/*` 后，任何对站点文档源文件的修改都会触发 `DOCS` 标签。
- 其余规则（如针对核心代码、各引擎模块的标签）不在本提交范围内，保持不变。

## 小结

本提交通过一行配置新增，把 `site/` 目录纳入 GitHub labeler 的 `DOCS` 标签匹配范围，修复了文档站点源文件改动不被自动归类为文档变更的遗漏。改动极小但补全了自动化标签的完整性，有助于维护者高效分流文档类 PR。
