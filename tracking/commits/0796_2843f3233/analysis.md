# 提交 0796：docs: Add archive for documentations older than 1.4.0 (#10374)

## 提交信息

- **序号**：0796 / 4088
- **哈希**：2843f3233246118cbc963c7c6612da1ec2f60b8a
- **短哈希**：2843f3233
- **日期**：2024-05-30 10:27:11 +0200
- **作者**：Manu Zhang
- **提交说明**：docs: Add archive for documentations older than 1.4.0 (#10374)
- **PR/Issue**：#10374

## 总体目的

Iceberg 官网从 1.4.0 版本起采用多版本文档共存的管理方式（在 `site/docs/docs/` 下保留 1.4.0、1.4.1、1.4.2 等版本的独立 mkdocs 子站点）。对于 1.4.0 之前的旧版本文档，项目不再在官网中维护，而是统一通过 Web Archive（web.archive.org）的快照链接提供访问。

本提交的总体目的是：在官网新增一个"Archive（归档）"页面，集中列出 1.4.0 之前各版本文档的 Web Archive 链接，并将该页面加入站点导航栏，方便用户查找历史版本文档。

## 如何达成设计目的

提交通过两处修改达成目的：

1. **新建归档页面 `site/docs/archive.md`**：在该 Markdown 文件中列出 0.12.1 到 1.3.1 共 11 个旧版本对应的 Web Archive 快照链接。每个链接指向对应版本文档在 web.archive.org 上的存档快照（带有具体的时间戳），用户点击即可查看该版本当时完整的文档内容。文件头部使用 YAML front matter 声明页面标题为 "Archive"，并附带 Apache 2.0 许可证头。

2. **更新站点导航配置 `site/nav.yml`**：在版本列表（1.4.2、1.4.1、1.4.0）之后新增一行 `- archive: archive.md`，将归档页面注册到网站主导航的版本下拉区域，使用户可以在版本列表末尾看到并访问 "archive" 入口。

## 修改详情

### `site/docs/archive.md`

**修改目的**：新建文档归档页面，集中提供 1.4.0 之前各版本文档的访问入口。

**工作逻辑**：文件采用 mkdocs 支持的 Markdown 格式，包含以下结构：
- YAML front matter（`title: "Archive"`）声明页面标题。
- Apache 许可证注释头。
- 一段说明文字："Documentations of Iceberg versions older than 1.4.0 are no longer maintained. Here are the links to web archive."（1.4.0 之前的 Iceberg 版本文档不再维护，此处为 web archive 链接）。
- 一个无序列表，按版本从新到旧排列，共 11 个条目：
  - 1.3.1、1.3.0、1.2.1、1.2.0、1.1.0、1.0.0、0.14.1、0.14.0、0.13.2、0.13.1、0.13.0、0.12.1
- 每个条目是一个指向 `https://web.archive.org/web/<时间戳>/https://iceberg.apache.org/docs/<版本>/` 的超链接。时间戳代表 Web Archive 抓取该页面快照的具体时刻，确保链接指向一个稳定的存档版本而非随时可能变化的实时页面。

### `site/nav.yml`

**修改目的**：将新建的归档页面注册到站点导航结构中，使其在网站菜单可见可访问。

**工作逻辑**：在 `nav.yml` 的版本列表 section 中，原先依次列出 1.4.2、1.4.1、1.4.0 三个版本（均通过 `!include` 指令引入对应版本的 mkdocs 子配置）。本次在 1.4.0 之后新增 `- archive: archive.md`，直接引用 `site/docs/archive.md` 作为导航项。这样渲染后的网站导航会在版本列表末尾出现 "archive" 链接，点击后展示归档页面。

## 小结

- **成效**：为 Iceberg 1.4.0 之前的 11 个历史版本文档提供了统一的归档访问入口，同时保持了 1.4.0 及以后版本在官网中的独立维护能力，兼顾了历史可追溯性与维护成本控制。
- **影响范围**：仅影响官方网站（site 模块）的文档展示与导航，不涉及任何代码逻辑或运行时行为。
- **回迁注意事项**：本提交为纯文档变更，回迁到 1.4.x 分支无技术风险。需注意 `site/nav.yml` 的上下文应与目标分支一致（即目标分支已采用多版本文档结构，存在 1.4.0/1.4.1/1.4.2 等条目），否则插入位置需要调整。归档链接中的 Web Archive 快照地址为外部资源，回迁后可直接使用，无需修改。
