# 提交 1362：Build: Bump mkdocs-redirects from 1.2.1 to 1.2.2 (#11511)

## 提交信息

- **序号**：1362 / 4088
- **哈希**：ea21a533bc42a11c92d02d7441786bce6838b0b6
- **短哈希**：ea21a533b
- **日期**：2024-11-11（Mon Nov 11 09:10:33 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump mkdocs-redirects from 1.2.1 to 1.2.2 (#11511)
- **PR/Issue**：#11511

## 总体目的

Iceberg 官方文档站点（位于 `site/` 目录）使用 MkDocs Material 构建，依赖一组 Python 包，记录在 `site/requirements.txt` 中。`mkdocs-redirects` 插件用于在文档结构变化时配置旧 URL 到新 URL 的重定向，避免外链失效。

本提交由 Dependabot 自动生成，将 `mkdocs-redirects` 从 1.2.1 升级到 1.2.2（patch 版本升级），目的是跟进上游的小版本修复，保持文档构建链路的依赖新鲜度，避免累积陈旧依赖带来的潜在兼容性或安全问题。这是 Apache 项目常规的依赖维护工作，无功能变化。

## 如何达成设计目的

Dependabot 自动扫描 `site/requirements.txt` 中锁定的依赖版本，发现 `mkdocs-redirects` 上游发布了 1.2.2 新版本后，自动创建 PR 将该行版本号从 `1.2.1` 改为 `1.2.2`。提交通过 GitHub 合并后即生效，下次构建文档站点时会拉取新版本。

## 修改详情

### `site/requirements.txt`

**修改目的**：升级 `mkdocs-redirects` 依赖版本。

**工作逻辑**：将文件末尾一行的版本号由 `mkdocs-redirects==1.2.1` 改为 `mkdocs-redirects==1.2.2`，其余依赖（mkdocs-material、mkdocs-macros-plugin 等）保持不变。这是单行单字符级别的版本号变更，无任何配置或逻辑改动。

```diff
-mkdocs-redirects==1.2.1
+mkdocs-redirects==1.2.2
```

## 小结

- **成效**：文档构建依赖 `mkdocs-redirects` 升级至 1.2.2，跟进上游 patch 修复。
- **影响范围**：仅 `site/requirements.txt` 一个文件，1 行变更，仅作用于文档站点构建，不影响 Iceberg 运行时代码、API 或发布产物。
- **回迁到 1.4.x 的注意事项**：1.4.x 是已发布的维护分支，其文档构建依赖通常不随 main 分支滚动更新（文档站点以最新 main 分支为准构建）。该改动对 1.4.x 的运行时和发布产物无任何影响，**无需回迁**。若 1.4.x 分支需要重新构建文档，可按需同步该依赖版本，但非必需。
