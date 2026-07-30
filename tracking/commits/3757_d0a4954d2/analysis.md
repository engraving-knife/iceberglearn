# 提交 3757：Site: Add version URL alias hook for docs (#16496)

## 提交信息

- **序号**：3757 / 4088
- **哈希**：d0a4954d2022bdfb5e2639a145468dc889394e55
- **短哈希**：d0a4954d2
- **日期**：2026-05-20 18:08:37 -0700
- **作者**：Kevin Liu
- **提交说明**：Site: Add version URL alias hook for docs (#16496)
- **PR/Issue**：#16496

## 总体目的

这个提交为 Iceberg 文档站点（基于 MkDocs 构建）添加了一个版本 URL 别名机制。目的是让特定版本的 URL（例如 `/docs/1.11.0/`）能够自动解析到 `/docs/latest/`，而无需在导航中为最新版本创建重复的条目。

背景是文档站点在 `mkdocs-dev.yml` 中将最新版本从 1.10.2 更新到 1.11.0，但用户可能通过版本号 URL 访问文档。为了避免维护多份文档副本或重复的导航条目，通过创建符号链接的方式让版本号 URL 指向 `latest` 目录。

## 如何达成设计目的

通过编写一个 MkDocs 的 `on_post_build` 钩子脚本 `version_alias.py`，在站点构建完成后，根据配置中的 `icebergVersion` 值，在 `site_dir/docs/` 下创建一个指向 `latest` 的符号链接。同时在 `mkdocs.yml` 中注册该钩子，并更新 `mkdocs-dev.yml` 中的版本号显示。

## 修改详情

### `site/hooks/version_alias.py` (+51/-0 lines)

**修改目的**：新建 MkDocs 钩子脚本，在构建后创建版本别名符号链接。

**工作逻辑**：
脚本定义了 `on_post_build(config)` 函数，在 MkDocs 构建完成后执行：
1. 从 `config["extra"]` 中读取 `icebergVersion`，如果没有则跳过。
2. 计算 `site_dir/docs/latest` 和 `site_dir/docs/<version>` 两个路径。
3. 如果 `latest` 目录不存在，记录警告并跳过。
4. 如果版本符号链接已存在（旧版本残留），先删除。
5. 创建新的符号链接 `<version> -> latest`。

这样 `/docs/1.11.0/` 这样的 URL 就能解析到 `/docs/latest/` 的内容。

### `site/mkdocs-dev.yml` (+1/-1 lines)

**修改目的**：更新开发环境配置中的最新版本号显示。

**工作逻辑**：将 `Latest (1.10.2)` 改为 `Latest (1.11.0)`，反映当前最新发布版本。

### `site/mkdocs.yml` (+4/-0 lines)

**修改目的**：在生产环境配置中注册版本别名钩子。

**工作逻辑**：在 `mkdocs.yml` 中新增 `hooks` 配置段，引用 `hooks/version_alias.py`，使 MkDocs 在构建时执行该钩子。

## 总结

这个提交通过 MkDocs 钩子机制实现了文档版本 URL 的别名功能，使用符号链接让版本号 URL 自动指向最新版本文档。这简化了文档站点的版本管理，避免重复内容，同时为用户提供了通过具体版本号访问文档的便利。同时也将文档站点的最新版本标识从 1.10.2 更新到了 1.11.0。
