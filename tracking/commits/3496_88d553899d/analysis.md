# 提交 3496：Docs: Fix stale version label and missing integrations in mkdocs-dev.yml (#15810)

## 提交信息

- **序号**：3496 / 4088
- **哈希**：88d553899d0a6109a0c55cb5f6c2208106d3c268
- **短哈希**：88d553899d
- **日期**：2026-04-01 14:54:27 -0700
- **作者**：Eunbin Son
- **提交说明**：Docs: Fix stale version label and missing integrations in mkdocs-dev.yml (#15810)
- **PR/Issue**：#15810

## 总体目的

修复 mkdocs 开发版配置文件中的过时版本标签和缺失的集成项目链接。具体包括：将 "Latest (1.10.0)" 更新为 "Latest (1.10.1)"，并新增 Apache Fluss 和 Microsoft OneLake 两个集成项目到导航中。

## 如何达成设计目的

直接修改 `site/mkdocs-dev.yml` 中的版本标签和导航项。

## 修改详情

### `site/mkdocs-dev.yml` (+3/-1 line)

**修改目的**：更新版本标签和新增集成项目链接。

**工作逻辑**：
- 版本标签：`Latest (1.10.0)` → `Latest (1.10.1)`
- 新增 `Apache Fluss: https://fluss.apache.org/docs/next/streaming-lakehouse/integrate-data-lakes/iceberg/` 到集成导航
- 新增 `Microsoft OneLake: https://aka.ms/onelakeircdocs` 到集成导航

## 总结

文档维护提交，更新 mkdocs 开发版配置中的版本标签从 1.10.0 到 1.10.1，并新增 Apache Fluss 和 Microsoft OneLake 两个集成项目链接。
