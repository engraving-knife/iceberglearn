# 提交 2570：Docs: OLake added to Vendors (#13931)

## 提交信息

- **序号**：2570 / 4088
- **哈希**：4294e9746dd3de05bfe6f52e4a055e59353eb2f0
- **短哈希**：4294e9746
- **日期**：2025-08-28 18:53:52 -0500
- **作者**：Badal Prasad Singh
- **提交说明**：Docs: OLake added to Vendors (#13931)
- **PR/Issue**：#13931

## 总体目的

此次提交将 OLake 这个开源 ELT 工具添加到 Iceberg 官方网站的厂商（Vendors）列表文档中。Iceberg 的 vendors 页面汇集了围绕 Apache Iceberg 生态的商业产品与开源工具，方便用户了解可用的集成方案。OLake 是一个专注于将数据库复制到 Apache Iceberg 数据湖仓的开源 ELT 工具，原生支持 PostgreSQL、MySQL、MongoDB、Oracle 和 Kafka，能够进行全量加载、持续 CDC（变更数据捕获）和增量同步。

通过将该条目加入 vendors 文档，社区用户可以更方便地发现 OLake 这一将外部数据源接入 Iceberg 湖仓的选项，丰富了 Iceberg 生态的可见性。

## 如何达成设计目的

- 在 `site/docs/vendors.md` 文件末尾追加一个新章节，采用与已有厂商条目一致的 Markdown 格式（三级标题 + 描述 + 文档链接）。
- 章节标题使用产品名并附带官网链接，正文描述 OLake 的核心能力，最后提供文档链接和 GitHub 仓库链接。

## 修改详情

### `site/docs/vendors.md` (+6)

**修改目的**：在厂商列表中新增 OLake 条目。

**工作逻辑**：在 VeloDB 条目之后追加 OLake 章节。正文说明 OLake 是开源 ELT 工具，支持 PostgreSQL/MySQL/MongoDB/Oracle/Kafka 到 Iceberg 的实时数据接入，无需 Debezium/Kafka/Spark 中间层；具备全量加载、CDC、增量同步（基于书签/游标列）、可恢复同步与 schema 演进处理能力；采用并行分块策略加速初始同步，并通过 CDC 游标保留确保增量更新不丢事件。末尾给出 OLake 文档与 GitHub 仓库链接。

## 总结

一次纯文档新增提交，将开源 ELT 工具 OLake 添加到 Iceberg 网站 vendors 列表，丰富了生态工具的可发现性，无代码逻辑变更。
