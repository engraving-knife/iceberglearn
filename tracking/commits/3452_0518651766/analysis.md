# 提交 3452：Site: Update Snowflake vendor description (#15745)

## 提交信息

- **序号**：3452 / 4088
- **哈希**：05186517662ef6fbd8ae6bd60b6e3e311bf16609
- **短哈希**：0518651766
- **日期**：2026-03-23 17:58:43 -0700
- **作者**：Maninder
- **提交说明**：Site: Update Snowflake vendor description (#15745)
- **PR/Issue**：#15745

## 总体目的

更新 Iceberg 网站上 Snowflake 供应商的描述信息。重写描述以突出 Horizon Catalog 功能，添加 DDL 支持、面向外部引擎的 Iceberg REST 端点、catalog 链接数据库和外部管理表的写入访问等内容。同时使用正确的 Apache 前缀引用引擎名称（如 Apache Spark、Apache Flink）。

## 如何达成设计目的

- 重写 `vendors.md` 中 Snowflake 段落的描述文字
- 添加 Horizon Catalog 的说明和链接
- 更新功能描述，反映 Snowflake 对 Iceberg 的最新支持

## 修改详情

### `site/docs/vendors.md` (+1/-1 lines)

**修改目的**：更新 Snowflake 供应商描述。

**工作逻辑**：
- 旧描述：仅提及 Snowflake-managed Iceberg Tables（支持 DML）和 externally managed Iceberg Tables（只读）
- 新描述：
  - 首先介绍 Snowflake Horizon Catalog 作为内建的通用目录
  - Snowflake-managed Iceberg Tables 现在支持完整 DDL 和 DML
  - 新增 Iceberg REST API 端点，允许 Apache Spark、Trino、Apache Flink 等外部引擎直接读写
  - 新增 catalog-linked databases 用于自动表发现和同步
  - 通过 catalog integrations 支持外部管理表的读写访问

## 总结

该提交更新了 Iceberg 网站上 Snowflake 供应商的描述，反映了 Snowflake 对 Iceberg 的最新支持能力，包括 Horizon Catalog、DDL 支持、REST API 端点、catalog 链接数据库和外部管理表写入访问等功能。
