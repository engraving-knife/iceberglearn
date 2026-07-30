# 提交 3595：Build: Bump mkdocs-rss-plugin from 1.18.1 to 1.19.0 (#16113)

## 提交信息

- **序号**：3595 / 4088
- **哈希**：bd7096ee0699f6dc28f23ea0af84effdf14891de
- **短哈希**：bd7096ee0
- **日期**：2026-04-25 23:49:56 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump mkdocs-rss-plugin from 1.18.1 to 1.19.0 (#16113)
- **PR/Issue**：#16113

## 总体目的

这是一个 Dependabot 自动依赖升级提交，将 MkDocs RSS 插件 `mkdocs-rss-plugin` 从版本 1.18.1 升级到 1.19.0。MkDocs 是 Iceberg 文档网站使用的静态站点生成器，RSS 插件用于为文档站点生成 RSS 订阅源。这是一个 semver-minor（次版本）升级。

## 如何达成设计目的

Dependabot 自动检测到 `site/requirements.txt`（Python 依赖文件）中 `mkdocs-rss-plugin` 版本有新版本可用，自动创建 PR 升级版本声明。

## 修改详情

### `site/requirements.txt` (+1/-1 lines)

**修改目的**：更新 mkdocs-rss-plugin 版本声明。

**工作逻辑**：
```
-mkdocs-rss-plugin==1.18.1
+mkdocs-rss-plugin==1.19.0
```

## 总结

这是一个文档构建依赖维护提交，通过次版本升级保持 MkDocs RSS 插件的最新状态，获取 1.19.0 版本中的改进和新功能。
