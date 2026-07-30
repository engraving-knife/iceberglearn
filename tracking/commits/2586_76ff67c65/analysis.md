# 提交 2586：Update nessie version in docs to 0.104.5 (#13969)

## 提交信息

- **序号**：2586 / 4088
- **哈希**：76ff67c658066bd7d05ce4ce54a1d6340ee0a899
- **短哈希**：76ff67c65
- **日期**：2025-09-01 21:21:29 -0700
- **作者**：Peter Nguyen
- **提交说明**：Update nessie version in docs to 0.104.5 (#13969)
- **PR/Issue**：#13969

## 总体目的

此次提交将 Iceberg 文档站点（mkdocs）中配置的 Nessie 版本从 0.103.3 更新到 0.104.5。Nessie（Project Nessie）是一个提供 Git 风格版本化目录服务的开源项目，常作为 Iceberg 的 Catalog 后端（`NessieCatalog`）使用。

Iceberg 文档站点的 `site/mkdocs.yml` 配置中通过 `extra` 部分定义了多个版本变量（如 `icebergVersion`、`nessieVersion`、`flinkVersion` 等），这些变量用于在文档页面中动态渲染示例代码中的依赖版本号，使文档展示的 Nessie 版本与当前推荐使用的版本保持一致。

此次更新将 `nessieVersion` 从 `0.103.3` 提升到 `0.104.5`，使文档中展示的 Nessie 依赖版本与最新的 Nessie 发布版本同步，确保用户按照文档配置时使用的是较新的 Nessie 版本。

## 如何达成设计目的

- 在 `site/mkdocs.yml` 的 `extra` 部分将 `nessieVersion` 从 `'0.103.3'` 改为 `'0.104.5'`。

## 修改详情

### `site/mkdocs.yml` (+1/-1)

**修改目的**：更新文档站点中的 Nessie 版本变量。

**工作逻辑**：`extra.nessieVersion` 从 `'0.103.3'` 改为 `'0.104.5'`，文档页面中引用该变量的地方（如 Nessie 集成示例的依赖声明）会自动渲染为新版本。

## 总结

一次文档版本变量更新提交，将 `site/mkdocs.yml` 中的 `nessieVersion` 从 0.103.3 更新到 0.104.5，使 Iceberg 文档展示的 Nessie 依赖版本与最新发布版本同步。无功能代码变更。
