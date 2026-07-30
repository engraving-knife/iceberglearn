# 提交 1022：Build: Bump mkdocs-material from 9.5.30 to 9.5.31 (#10863)

## 提交信息

- **序号**：1022 / 4088
- **哈希**：74a9adbc0b6c0f8bdb1dbee78c333d64fb52b41f
- **短哈希**：74a9adbc0
- **日期**：2024-08-05 09:08:35 +0200
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump mkdocs-material from 9.5.30 to 9.5.31 (#10863)
- **PR/Issue**：#10863

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交。Iceberg 项目使用 mkdocs-material 作为文档站点生成器的主题，本次将其从 9.5.30 升级到 9.5.31（一个 patch 版本升级）。

mkdocs-material 是一个活跃维护的开源项目，会定期发布 patch 版本修复 bug 和安全漏洞。Iceberg 仓库通过 Dependabot 监控依赖更新并自动提交 PR，以保持依赖的及时更新。这种小版本（semver-patch）升级通常只包含 bug 修复，不引入破坏性变更。

## 如何达成设计目的

通过修改 `site/requirements.txt` 中 mkdocs-material 的版本号约束，将其固定为 9.5.31。Dependabot 自动生成了升级说明，包含发布说明、更新日志和版本对比链接，便于人工审查变更内容。

## 修改详情

### `site/requirements.txt`

**修改目的**：将 mkdocs-material 文档主题依赖从 9.5.30 升级到 9.5.31。

**工作逻辑**：将 `mkdocs-material==9.5.30` 改为 `mkdocs-material==9.5.31`，采用精确版本固定策略，确保 CI 和本地构建使用一致的依赖版本。

## 小结

- **成效**：将文档站点的 mkdocs-material 主题升级到最新 patch 版本，获取上游 bug 修复。
- **影响范围**：仅涉及 `site/requirements.txt` 一个文件，影响文档构建链路，不影响 Iceberg 运行时代码。
- **回迁到 1.4.x 的注意事项**：纯文档依赖升级，回迁风险极低。1.4.x 分支若需同步文档构建环境可考虑回迁，但对运行时功能无任何影响，优先级最低。
