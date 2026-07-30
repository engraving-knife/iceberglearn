# 提交 2356：Tabular no long exist as a company (#13511)

## 提交信息

- **序号**：2356 / 4088
- **哈希**：ed98c6f249a59cda847530de27f2563ca3764e6e
- **短哈希**：ed98c6f24
- **日期**：2025-07-15 12:12:42 +0200
- **作者**：Robin Moffatt
- **提交说明**：Tabular no long exist as a company (#13511)
- **PR/Issue**：#13511

## 总体目的

这个提交从 Iceberg 官方文档的厂商列表中移除了 Tabular 的条目。Tabular 是一家提供 Iceberg 托管仓库和自动化平台的公司，由 Iceberg 创始人 Ryan Blue 等人创立。2024 年 Databricks 收购了 Tabular，Tabular 不再作为独立公司存在，因此将其从厂商列表中移除。

Iceberg 的厂商页面列出支持 Iceberg 的商业产品和服务。当某个公司被收购或不再独立运营时，需要更新该列表以保持准确性。

## 如何达成设计目的

从厂商页面文件中删除 Tabular 的条目段落。

## 修改详情

### `site/docs/vendors.md` (+0/-4 lines)

**修改目的**：移除 Tabular 厂商条目。

**工作逻辑**：删除 Tabular 的标题行和描述段落共 4 行，包括 `### [Tabular](https://tabular.io)` 标题和描述其托管仓库和自动化平台的段落。删除后列表从 Starburst 直接跳到 Tinybird。

## 总结

该提交因 Tabular 被 Databricks 收购后不再作为独立公司存在，将其从 Iceberg 厂商列表中移除。纯文档变更，保持厂商列表的准确性。
