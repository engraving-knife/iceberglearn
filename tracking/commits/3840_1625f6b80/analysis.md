# 提交 3840：Docs: Add Dataddo to the vendor list (#16443)

## 提交信息

- **序号**：3840 / 4088
- **哈希**：1625f6b800a6f00cf074c453e25e139d455a5e5f
- **短哈希**：1625f6b80
- **日期**：2026-06-08 13:34:54 +0200
- **作者**：cenotee
- **提交说明**：Docs: Add Dataddo to the vendor list (#16443)
- **PR/Issue**：#16443

## 总体目的

本提交将 Dataddo 公司添加到 Iceberg 官方网站的厂商列表（vendor list）中。Apache Iceberg 项目维护一个厂商列表页面，列出了所有支持或集成 Apache Iceberg 的厂商和产品，方便用户了解生态系统中可用的商业解决方案。

Dataddo 是一个全托管的数据集成平台，支持将企业数据在云、本地和混合环境之间迁移，对包括 Apache Iceberg 在内的开放表格式提供一等支持。本次提交将其加入厂商列表，使 Iceberg 社区用户可以了解到 Dataddo 这一集成选项。

## 如何达成设计目的

通过在 `site/docs/vendors.md` 文件中新增一个 Dataddo 的章节，按照已有的厂商条目格式（标题、公司简介、特性列表、链接）添加内容，插入在 Databricks 和 dltHub 之间（按字母顺序）。

## 修改详情

### `site/docs/vendors.md` (+8/-0 lines)

**修改目的**：添加 Dataddo 厂商条目。

**工作逻辑**：
在 Databricks 条目之后、dltHub 条目之前新增了 Dataddo 的介绍，包含公司链接、简介以及三个特性要点：
- **Architecture**（架构）：描述 Dataddo 的控制平面/数据平面分离架构，支持 AWS、Azure、GCP、主权云或本地 Kubernetes/OpenShift，敏感数据不离开网络。
- **Transport patterns**（传输模式）：支持 ETL/ELT、CDC、流式、反向 ETL 和批量交付。
- **Iceberg support**（Iceberg 支持）：通过 AWS Glue、REST 等目录写入 Apache Iceberg，并自动检测源 schema 漂移。

## 总结

这是一次纯文档更新，将 Dataddo 数据集成平台添加到 Iceberg 厂商列表。不影响任何代码功能，属于生态系统文档维护工作，帮助用户发现更多支持 Apache Iceberg 的商业解决方案。
