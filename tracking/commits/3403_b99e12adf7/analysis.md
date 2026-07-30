# 提交 3403：site: add back privacy plugin (#15657)

## 提交信息

- **序号**：3403 / 4088
- **哈希**：b99e12adf70dea02ebb9664198dd716332f847f6
- **短哈希**：b99e12adf7
- **日期**：2026-03-16 18:24:54 -0700
- **作者**：Kevin Liu
- **提交说明**：site: add back privacy plugin (#15657)
- **PR/Issue**：#15657

## 总体目的

在 mkdocs 站点配置中重新添加 privacy 插件。该插件之前可能被移除或禁用，现在需要恢复它以在构建文档站点时自动处理外部资源隐私问题。同时配置了排除规则，避免处理来自 bladepipe.com 的资源。

## 如何达成设计目的

- 在 `site/mkdocs.yml` 的 plugins 配置中添加 privacy 插件
- 配置 `assets_exclude` 规则排除 `*bladepipe.com/*` 路径

## 修改详情

### `site/mkdocs.yml` (+3 lines)

**修改目的**：重新启用 privacy 插件并配置排除规则。

**工作逻辑**：
```yaml
- privacy:
    assets_exclude:
      - '*bladepipe.com/*'
```
privacy 插件会自动将外部资源（如图片、脚本）下载到本地以保护用户隐私。`assets_exclude` 配置排除了来自 bladepipe.com 的资源，使其不被 privacy 插件处理。

## 总结

本提交在 mkdocs 配置中重新启用了 privacy 插件，用于文档站点构建时自动处理外部资源的隐私保护，同时排除了 bladepipe.com 域名的资源。
