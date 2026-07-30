# 提交 2371：[docs] Tidy up left-hand navigation (#13491)

## 提交信息

- **序号**：2371 / 4088
- **哈希**：29a87e7a0e1171a6437a464505cbaa1cd4f1c022
- **短哈希**：29a87e7a0
- **日期**：2025-07-18 09:33:27 -0700
- **作者**：Robin Moffatt
- **提交说明**：[docs] Tidy up left-hand navigation (#13491)
- **PR/Issue**：#13491

## 总体目的

本提交对 Iceberg 项目文档网站的左侧导航栏进行了全面的整理和重组。原有的导航结构较为扁平，各种集成（Spark、Flink、Hive、Trino 等）、API 文档、库链接等混在一起，缺乏清晰的分类层次，用户难以快速定位所需内容。

本次重组将导航项按照逻辑关系重新分组，形成了更清晰的层级结构：将基础概念（Tables、Views）归入"Concepts"分组；将各语言库（Java、Python、Rust、Go）归入"Libraries"分组；将各种引擎和工具集成归入"Integrations"分组，并在其下进一步区分"Iceberg 维护的集成"（Spark、Flink、Kafka Connect、Hive）和"第三方集成"（Athena、BigQuery、Snowflake 等）；将目录服务（Catalogs）和存储（Storage）独立分组。同时对文档版本选择器的结构也做了优化，将旧版本归入"Previous"子菜单。

## 如何达成设计目的

设计思路是通过重组 mkdocs.yml 和 site/nav.yml 两个导航配置文件来实现更合理的文档结构。关键设计点如下：

1. **概念分组**：将 Tables 和 Views 等基础概念文档归入"Concepts"顶层分组。
2. **库分组**：新增"Libraries"分组，将 Java（含 Quickstart、API、Javadoc）、Python、Rust、Go 等各语言库文档统一管理。
3. **集成分组重组**：将所有集成项归入"Integrations"，并区分"Iceberg 维护的集成"（Spark、Flink、Kafka Connect、Hive）和"第三方"集成，第三方集成按字母顺序排列。
4. **目录与存储独立**：将 Catalogs（AWS Glue、DynamoDB、JDBC、Nessie 等）和 Storage（S3、Dell ECS）从原位置提取出来作为独立分组。
5. **版本选择器优化**：在 site/nav.yml 中将文档版本列表重组为 Nightly、Latest (1.9.2) 和 Previous（包含所有旧版本）的三层结构，减少导航栏的视觉 clutter。

## 修改详情

### `docs/mkdocs.yml` (+79/-70 lines)

**修改目的**：重组文档主体内容的导航结构。

**工作逻辑**：将原来扁平的导航列表重构为分层次的导航树。主要变化包括：
- 新增 "Concepts" 分组，包含 Tables（branching、configuration、evolution 等 9 个页面）和 Views。
- 新增 "Libraries" 分组，包含 Java（Quickstart、API、Javadoc 链接）、Python、Rust、Go。
- 新增 "Integrations" 分组，首先列出 Iceberg 维护的集成（Spark、Flink、Kafka Connect、Hive），然后是"Third-party"子分组，按字母顺序列出所有第三方集成（Amoro、Athena、BigQuery、ClickHouse 等）。
- 新增 "Catalogs" 分组，包含 AWS Glue、AWS DynamoDB、custom-catalog、jdbc、nessie。
- 新增 "Storage" 分组，包含 AWS S3 和 Dell ECS。

### `site/nav.yml` (+24/-20 lines)

**修改目的**：优化文档版本选择器的导航结构。

**工作逻辑**：将原来平铺的所有版本列表（nightly、latest、1.9.2 到 1.4.0 共 16 个版本）重组为三层结构：顶层为 "Nightly" 和 "Latest (1.9.2)"，所有旧版本（1.9.1 到 1.4.0）归入 "Previous" 子菜单。这样大大减少了版本选择器在导航栏中占用的空间，使导航更加清爽。注意版本值从原来的小写（如 "nightly"、"latest"）改为首字母大写的显示形式（如 "Nightly"、"Latest (1.9.2)"）。

## 总结

本提交是纯文档导航结构的优化，不涉及任何代码逻辑修改。通过将扁平的导航列表重组为逻辑清晰的层次结构，显著提升了文档的可用性和可导航性。特别是将集成区分为"Iceberg 维护"和"第三方"两类，以及将旧版本归入子菜单，都是实用的 UX 改进。该修改影响范围较大（181 行变更），但风险极低，属于纯粹的文档配置调整。
