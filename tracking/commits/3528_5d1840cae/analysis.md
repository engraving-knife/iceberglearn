# 提交 3528：Docs: Add Sail to integration and vendor (#15920)

## 提交信息

- **序号**：3528 / 4088
- **哈希**：5d1840caed05ec8914fb8866378c36616d73debb
- **短哈希**：5d1840cae
- **日期**：2026-04-13 18:35:39 -0700
- **作者**：XL Liang
- **提交说明**：Docs: Add Sail to integration and vendor (#15920)
- **PR/Issue**：#15920

## 总体目的

Sail 是一个开源的多模态分布式计算框架（用 Rust 编写），统一批处理、流处理和 AI 工作负载，并提供 Spark SQL/DataFrame API 的 drop-in 替换。Sail 支持 Iceberg 作为数据源。

本提交将 Sail 添加到 Iceberg 官方网站的厂商/集成列表中，让用户了解 Sail 这个支持 Iceberg 的计算框架选项。这属于生态文档维护，帮助 Iceberg 社区记录不断增长的集成生态。

## 如何达成设计目的

在三个地方添加 Sail 条目：
1. `site/docs/vendors.md`：在厂商介绍文档中新增 Sail 段落（位于 Ryft 和 SingleStore 之间，按字母顺序）
2. `site/mkdocs-dev.yml`：在开发环境 mkdocs 导航中添加 Sail 链接
3. `site/nav.yml`：在主导航配置中添加 Sail 链接

两处导航都按字母顺序插入 Sail（在 Ryft 之后、Snowflake 之前），链接指向 Sail 的 Iceberg 指南文档。

## 修改详情

### `site/docs/vendors.md` (+4/-0 lines)

**修改目的**：在厂商文档中添加 Sail 介绍段落。

**工作逻辑**：
```markdown
### [Sail](https://lakesail.com/)

[Sail](https://github.com/lakehq/sail) is an open-source multimodal distributed compute framework, built in Rust, unifying batch, streaming, and AI workloads. For seamless adoption, Sail offers a drop-in replacement for the Spark SQL and DataFrame APIs in both single-host and distributed settings. Learn more about using Sail with Iceberg in the [Sail Iceberg guide](https://docs.lakesail.com/sail/latest/guide/sources/iceberg).
```
段落描述了 Sail 的定位（Rust 编写的开源多模态分布式计算框架）、能力（统一批/流/AI，Spark API drop-in 替换）以及与 Iceberg 集成的文档链接。

### `site/mkdocs-dev.yml` (+1/-0 lines)

**修改目的**：在开发环境 mkdocs 导航中添加 Sail 链接。

**工作逻辑**：
```yaml
        - Sail: https://docs.lakesail.com/sail/latest/guide/sources/iceberg
```
按字母顺序插入在 Ryft 和 Snowflake 之间。

### `site/nav.yml` (+1/-0 lines)

**修改目的**：在主站点导航中添加 Sail 链接。

**工作逻辑**：
```yaml
        - Sail: https://docs.lakesail.com/sail/latest/guide/sources/iceberg
```
与 mkdocs-dev.yml 相同的插入位置和链接。

## 总结

本提交将 Sail（一个 Rust 编写的开源多模态分布式计算框架，支持 Iceberg）添加到 Iceberg 官方网站的厂商/集成列表中，包括文档介绍和两处导航配置。属于生态文档维护，让用户了解 Sail 这个 Iceberg 集成选项。
