# 提交 3394：Build: Bump mkdocs-material from 9.6.23 to 9.7.5 (#15643)

## 提交信息

- **序号**：3394 / 4088
- **哈希**：17bdb32cf1f06b13c9e8bc2c639dce76ed910799
- **短哈希**：17bdb32c
- **日期**：2026-03-16 12:40:21 +0100
- **作者**：Manu Zhang
- **提交说明**：Build: Bump mkdocs-material from 9.6.23 to 9.7.5 (#15643)
- **PR/Issue**：#15643

## 总体目的

升级 mkdocs-material 主题从 9.6.23 到 9.7.5（minor 级别升级）。mkdocs-material 是 Iceberg 文档站点（`site/`）使用的 MkDocs 主题。9.7.x 是一个新的 minor 系列，升级同时移除了 `privacy` 插件（该插件在 9.7.x 中可能存在兼容性问题，后续提交 #15657 会将其加回）。

## 如何达成设计目的

1. **升级版本**：修改 `site/requirements.txt` 中 `mkdocs-material` 的版本号。
2. **移除 privacy 插件**：从 `site/mkdocs.yml` 的 plugins 列表中移除 `privacy` 条目，以适配 9.7.x 升级带来的变化。

## 修改详情

### `site/mkdocs.yml` (+0/-1 lines)

**修改目的**：移除 privacy 插件配置。

**工作逻辑**：
- 从 `plugins` 列表中删除 `- privacy` 一行。privacy 插件用于在构建时将外部资源（如字体、脚本）本地化以增强隐私。移除可能是由于 9.7.x 升级后该插件行为变化或导致构建问题。后续提交（#15657）会重新加回该插件。

### `site/requirements.txt` (+1/-1 lines)

**修改目的**：升级 mkdocs-material 版本。

**工作逻辑**：
- 将 `mkdocs-material==9.6.23` 改为 `mkdocs-material==9.7.5`。

## 总结

这是一次文档站点主题的 minor 级升级，将 mkdocs-material 从 9.6.23 提升到 9.7.5。升级同时临时移除了 privacy 插件以适配新版本（后续 #15657 会加回）。改动仅影响文档构建，不涉及 Iceberg 核心代码。
