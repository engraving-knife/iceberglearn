# 提交 2003：Docs: Update the docs for working with Flink and REST catalog

## 提交信息

- **序号**：2003 / 4088
- **哈希**：31f1fca74609fdf2fb3009b0544a95b9a7a24d24
- **短哈希**：31f1fca74
- **日期**：2025-04-16 10:26:47 +0200
- **作者**：Dao Thanh Tung
- **提交说明**：Docs: Update the docs for working with Flink and REST catalog (#12726)
- **PR/Issue**：#12726

## 总体目的

本提交更新了 Flink 连接器文档，补充了 REST 目录（REST catalog）的使用说明。此前文档中仅描述了 Hive 目录和 Hadoop 目录的配置方式，缺少 REST 目录的创建示例和配置说明，用户难以了解如何在 Flink 中使用 REST 目录与 Iceberg 交互。

同时，本提交还优化了 Flink 连接器文档的结构：将原先内联在 `flink-connector.md` 中的目录配置属性列表移除，改为引用 `flink-configuration.md` 页面，避免属性描述的重复维护。

## 如何达成设计目的

通过修改两个文档文件完成更新：
1. 在 `flink-connector.md` 中新增 REST 目录创建表的 SQL 示例，并精简目录配置属性的描述（改为引用配置页面）。
2. 在 `flink.md` 中新增 REST 目录的 `CREATE CATALOG` 示例。

## 修改详情

### `docs/docs/flink-connector.md` (修改, +29/-14 lines)

**修改目的**：新增 REST 目录建表示例，精简目录配置属性描述。

**工作逻辑**：
1. **精简属性列表**：将原先内联列出的 6 项目录配置属性（`connector`、`catalog-name`、`catalog-type`、`catalog-impl`、`catalog-database`、`catalog-table`）替换为一段说明文字，引用 `flink-configuration.md#catalog-configuration` 页面获取详细属性值。

2. **新增 REST 目录建表示例**：在"Table managed in Hive catalog"和"Table managed in custom catalog"之间新增"Table managed in REST catalog"小节，提供完整的 SQL 建表示例，包含 `catalog-type='rest'`、`uri`、`credential`（可选）、`token`（可选）、`scope`（可选）等配置项。

### `docs/docs/flink.md` (修改, +16/-6 lines)

**修改目的**：新增 REST 目录的 CREATE CATALOG 示例。

**工作逻辑**：
将原先 Hive 目录属性列表（`uri`、`clients`、`warehouse`、`hive-conf-dir`、`hadoop-conf-dir`）的描述移除，替换为新增的"REST catalog"小节，提供 `CREATE CATALOG rest_catalog` 的 SQL 示例，展示如何使用 `'catalog-type'='rest'` 和 `uri` 配置创建 REST 目录。

## 总结

本提交更新了 Flink 文档，补充了 REST 目录的使用说明和 SQL 示例，涵盖在 Flink SQL 中通过 REST 目录创建表和创建目录两种场景。同时精简了 `flink-connector.md` 中的目录属性描述，改为引用配置页面，避免重复维护。
