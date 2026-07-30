# 提交 3555：Build: Bump mkdocs-rss-plugin from 1.17.9 to 1.18.1 (#16036)

## 提交信息

- **序号**：3555 / 4088
- **哈希**：54cdbcddee30610ce4e4c9b7f685d3b90a90bfea
- **短哈希**：54cdbcdd
- **日期**：2026-04-18 22:30:49 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump mkdocs-rss-plugin from 1.17.9 to 1.18.1 (#16036)
- **PR/Issue**：#16036

## 总体目的

Dependabot 自动生成的依赖升级 PR，将 `mkdocs-rss-plugin` 从 1.17.9 升级到 1.18.1。这是一个 semver minor 版本升级，主要包含新功能和 bug 修复。

`mkdocs-rss-plugin` 是 mkdocs 的 RSS 订阅插件，为 Iceberg 官方网站生成 RSS feed，让用户可以订阅文档更新。该依赖列在 `site/requirements.txt` 中，用于网站构建。

## 如何达成设计目的

Dependabot 自动检测到 PyPI 上 `mkdocs-rss-plugin` 的新版本，更新 `site/requirements.txt` 中固定版本号的依赖声明。

## 修改详情

### `site/requirements.txt` (+1/-1 lines)

**修改目的**：将 `mkdocs-rss-plugin` 版本从 1.17.9 升级到 1.18.1。

**工作逻辑**：
```
-mkdocs-rss-plugin==1.17.9
+mkdocs-rss-plugin==1.18.1
```
使用 `==` 精确锁定版本，确保网站构建基于确定的插件版本。

## 总结

Dependabot 自动升级 `mkdocs-rss-plugin` 至 1.18.1 minor 版本，属于网站构建依赖的例行维护。该插件用于为 Iceberg 官方网站生成 RSS feed，升级以获取上游的新功能与修复，保持依赖新鲜度。
