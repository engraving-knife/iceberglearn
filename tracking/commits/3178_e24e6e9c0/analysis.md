# 提交 3178：Docs: Add Ryft to third-party integrations (#15179)

## 提交信息

- **序号**：3178 / 4088
- **哈希**：e24e6e9c06b14d5e26db1544047794c97d152414
- **短哈希**：e24e6e9c0
- **日期**：2026-01-29
- **作者**：yguy-ryft
- **提交说明**：Docs: Add Ryft to third-party integrations (#15179)
- **PR/Issue**：#15179

## 总体目的

Iceberg 官网维护着一份第三方集成（Third-party integrations）列表，展示支持 Iceberg 表格式的各类平台与工具，包括 Presto、Redpanda、RisingWave、Snowflake、Starburst、Starrocks 等。Ryft 是一个数据平台，本次提交将其加入 Iceberg 第三方集成列表，使访问 Iceberg 官网的用户可以发现 Ryft 平台对 Iceberg 的支持能力。

这类文档变更的实际价值在于生态可见性：第三方平台通过提交 PR 将自身加入集成列表，既表明其对 Iceberg 的支持承诺，也帮助 Iceberg 用户在技术选型时了解可用的平台选项。Ryft 的文档链接 `https://docs.ryft.io/platform` 为用户提供了进一步了解其 Iceberg 集成细节的入口。

## 如何达成设计目的

改动涉及两个 mkdocs 导航配置文件：`site/mkdocs-dev.yml` 和 `site/nav.yml`。两者均在 "Third-party" → "Engines" 列表中，按字母顺序在 RisingWave 与 Snowflake 之间插入 Ryft 条目，指向其官方文档。两个文件同步更新以保持开发与正式环境一致。

## 修改详情

### `site/mkdocs-dev.yml` (+1/-0 lines)

**修改目的**：在开发环境导航的第三方引擎列表中添加 Ryft。

**工作逻辑**：
在 `nav` 配置的 Third-party → Engines 列表中，于 `- RisingWave: integrations/risingwave.md` 之后、`- Snowflake: ...` 之前插入 `- Ryft: https://docs.ryft.io/platform`，保持列表按字母顺序排列。

### `site/nav.yml` (+1/-0 lines)

**修改目的**：在正式导航的第三方引擎列表中添加 Ryft。

**工作逻辑**：
与 `mkdocs-dev.yml` 完全相同的插入操作，在相同位置添加 Ryft 条目，指向 `https://docs.ryft.io/platform`，确保正式站点导航同步展示 Ryft 集成入口。

## 总结

本提交将 Ryft 平台添加到 Iceberg 官网的第三方集成引擎列表中，扩展了 Iceberg 生态的可见性，为用户提供了新的平台选项参考。改动为纯文档配置，两个导航文件同步更新，条目按字母顺序正确插入。
