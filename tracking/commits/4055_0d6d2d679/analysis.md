# 提交 4055：Site: Add stackable to vendors list (#17136)

## 提交信息

- **序号**：4055 / 4088
- **哈希**：0d6d2d67920e785c536ba642c2b39aa69c5f1d4d
- **短哈希**：0d6d2d679
- **日期**：2026-07-16 16:41:03 -0500
- **作者**：Jim Halfpenny
- **提交说明**：Site: Add stackable to vendors list (#17136)
- **PR/Issue**：#17136

## 总体目的

这个提交在 Iceberg 官方网站的导航配置中将 Stackable 添加到厂商列表的导航菜单中。Stackable Data Platform 是一个完全开源的数据平台，此前其介绍内容已经存在于 `vendors.md` 页面中（从 4054 提交的 diff 上下文可见「The Stackable Data Platform is completely open source...」），但未出现在网站导航的厂商快捷链接列表中。

本提交将 Stackable 的链接添加到 `mkdocs-dev.yml`（开发环境 mkdocs 配置）和 `nav.yml`（导航配置）的厂商导航列表中，使网站访问者能从导航菜单直接跳转到 Stackable 的数据湖仓页面。条目按字母顺序插入在 Snowflake 和 Starburst 之间。

## 如何达成设计目的

在两个导航配置文件中，于厂商链接列表的 Snowflake 之后、Starburst 之前，插入 Stackable 条目及其官网数据湖仓页面链接 `https://stackable.tech/en/data-lakehouse/`。

## 修改详情

### `site/mkdocs-dev.yml` (+1/-0 lines)

**修改目的**：在开发环境 mkdocs 导航中添加 Stackable 链接。

**工作逻辑**：
```yaml
- Snowflake: https://docs.snowflake.com/en/user-guide/tables-iceberg
- Stackable: https://stackable.tech/en/data-lakehouse/
- Starburst: https://docs.starburst.io/latest/connector/iceberg.html
```
在厂商链接列表中按字母顺序插入 Stackable 条目，指向其数据湖仓页面。

### `site/nav.yml` (+1/-0 lines)

**修改目的**：在主导航配置中添加 Stackable 链接。

**工作逻辑**：与 `mkdocs-dev.yml` 相同的插入，保持两个导航配置一致：
```yaml
- Snowflake: https://docs.snowflake.com/en/user-guide/tables-iceberg
- Stackable: https://stackable.tech/en/data-lakehouse/
- Starburst: https://docs.starburst.io/latest/connector/iceberg.html
```

## 总结

纯网站导航配置提交，将 Stackable 添加到 Iceberg 官网厂商导航列表（mkdocs-dev.yml 和 nav.yml），使已有 vendors.md 中的 Stackable 内容能通过导航菜单访问。按字母顺序插入在 Snowflake 和 Starburst 之间。无代码改动。
