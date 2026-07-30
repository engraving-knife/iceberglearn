# 提交 1363：Build: Bump mkdocs-material from 9.5.43 to 9.5.44 (#11510)

## 提交信息

- **序号**：1363 / 4088
- **哈希**：aa0aeb0cfa77687312fbacee2bfd8ab0b9ba452b
- **短哈希**：aa0aeb0cf
- **日期**：2024-11-11（Mon Nov 11 09:27:40 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump mkdocs-material from 9.5.43 to 9.5.44 (#11510)
- **PR/Issue**：#11510

## 总体目的

`mkdocs-material` 是 Iceberg 文档站点（`site/` 目录）使用的主题框架，决定文档的视觉样式、搜索、导航等核心展示能力。该主题更新较频繁，9.5.43 到 9.5.44 为 patch 版本升级。

本提交由 Dependabot 自动生成，将 `mkdocs-material` 从 9.5.43 升级到 9.5.44，目的是跟进上游 patch 修复（通常含 bug 修复、小改进或安全补丁），保持文档主题依赖新鲜。这是常规的依赖维护工作，无功能变化。

## 如何达成设计目的

Dependabot 自动检测到 `site/requirements.txt` 中 `mkdocs-material==9.5.43` 上游发布了 9.5.44，自动创建 PR 将该行版本号改为 `9.5.44`。合并后下次构建文档站点时拉取新版本主题。

## 修改详情

### `site/requirements.txt`

**修改目的**：升级 `mkdocs-material` 主题版本。

**工作逻辑**：将 `mkdocs-material==9.5.43` 改为 `mkdocs-material==9.5.44`，其他依赖保持不变。注意此时 `mkdocs-redirects` 已经是 1.2.2（前一个提交 1362 刚升级），说明这两个 Dependabot PR 是独立依次合并的。

```diff
-mkdocs-material==9.5.43
+mkdocs-material==9.5.44
```

## 小结

- **成效**：文档主题 `mkdocs-material` 升级至 9.5.44，跟进上游 patch 修复。
- **影响范围**：仅 `site/requirements.txt` 一个文件，1 行变更，仅作用于文档站点构建与展示，不影响 Iceberg 运行时代码、API 或发布产物。
- **回迁到 1.4.x 的注意事项**：与 1362 类似，文档构建依赖由 main 分支统一维护，1.4.x 分支无需同步该依赖升级。对 1.4.x 运行时和发布产物无影响，**无需回迁**。
