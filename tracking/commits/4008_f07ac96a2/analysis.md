# 提交 4008：Fix catalog properties links after docs refactor of #15848 (#17153)

## 提交信息

- **序号**：4008 / 4088
- **哈希**：f07ac96a25b19c56a03c41fd460fa3dfe8e43cc6
- **短哈希**：f07ac96a2
- **日期**：2026-07-10 15:56:51 +0200
- **作者**：Adam Szita
- **提交说明**：Fix catalog properties links after docs refactor of #15848 (#17153)
- **PR/Issue**：#17153（关联 #15848）

## 总体目的

本提交修复文档重构（#15848）后遗留的两个失效链接。#15848 将 catalog properties 文档从原来的 `configuration/#catalog-properties` 位置重构为独立的 `catalog-properties` 页面，但有两处链接未同步更新，导致 REST OpenAPI 规范和 terms 文档中的链接指向已不存在的旧路径。

## 如何达成设计目的

直接修正两处链接 URL：
1. `open-api/rest-catalog-open-api.yaml` 中指向 catalog 配置文档的 URL。
2. `site/docs/terms.md` 中指向 catalog 文档的相对路径。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (+1/-1 lines)

**修改目的**：修复 REST OpenAPI 规范中的 catalog 配置文档链接。

**工作逻辑**：
将 `https://iceberg.apache.org/docs/latest/configuration/#catalog-properties` 改为 `https://iceberg.apache.org/docs/latest/catalog-properties`，匹配重构后的新页面路径。

### `site/docs/terms.md` (+1/-1 lines)

**修改目的**：修复 terms 文档中的相对链接路径。

**工作逻辑**：
将 `[Iceberg documentation](docs/latest/configuration.md#catalog-properties)` 改为 `[Iceberg documentation](../../docs/docs/catalog-properties.md)`，既修正了路径指向新的 catalog-properties 页面，也修正了相对路径的层级（从 `site/docs/` 出发需要 `../../docs/docs/`）。

## 总结

这是一次小的文档链接修复提交，修正了 #15848 文档重构后遗漏的两处链接，确保 REST OpenAPI 规范和 terms 文档指向正确的 catalog properties 页面。属于文档维护的收尾工作。
